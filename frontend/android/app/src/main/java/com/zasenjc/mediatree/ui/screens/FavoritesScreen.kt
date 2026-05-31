package com.zasenjc.mediatree.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmarks
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.smbLibrarySourceId
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.ui.components.EpisodeLandscapeCard
import com.zasenjc.mediatree.ui.components.DesignFilterChip
import com.zasenjc.mediatree.ui.components.DesignTopAppBar
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.MoviePosterCard
import com.zasenjc.mediatree.ui.components.SyncChromeWithGridScroll
import com.zasenjc.mediatree.ui.components.topChromeEnterTransition
import com.zasenjc.mediatree.ui.components.topChromeExitTransition
import com.zasenjc.mediatree.ui.shouldLoadRemoteContent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

private val favoriteFilters = listOf("全部", "单集", "剧集", "电影")

class FavoritesViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val page: Int = 0,
        val loading: Boolean = false,
        val movies: List<MovieDto> = emptyList(),
        val total: Int = 0,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refresh(providerType: ProviderType, activeLibrary: String = "") {
        loadPage(providerType, activeLibrary, page = 0, replace = true)
    }

    fun loadMore(providerType: ProviderType, activeLibrary: String = "") {
        val s = _state.value
        if (s.loading || s.movies.size >= s.total && s.total > 0) return
        if (activeLibrary.smbLibrarySourceId() != null) return
        loadPage(providerType, activeLibrary, page = s.page + 1, replace = false)
    }

    private fun loadPage(providerType: ProviderType, activeLibrary: String, page: Int, replace: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val smbSourceId = activeLibrary.smbLibrarySourceId()
                if (smbSourceId != null) {
                    val movies = loadSmbMovies(smbSourceId)
                    _state.update { it.copy(page = 0, loading = false, movies = movies, total = movies.size) }
                    return@launch
                }
                val response = container.mediaProviderFor(providerType).favorites(limit = 48, offset = page * 48, sort = "release_date_desc")
                _state.update {
                    it.copy(
                        page = page,
                        loading = false,
                        movies = if (replace) response.movies else it.movies + response.movies,
                        total = response.total,
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    private suspend fun loadSmbMovies(sourceId: String): List<MovieDto> {
        val source = container.clientStorageRepository.load()
            .firstOrNull { it.id == sourceId && it.type == com.zasenjc.mediatree.data.ClientStorageType.SMB && it.enabled }
            ?: throw IllegalArgumentException("SMB 存储源不可用")
        return container.smbClient.list(source)
            .filter { it.isPlayableVideo }
            .map { entry ->
                MovieDto(
                    id = (source.id + ":" + entry.path).hashCode(),
                    path = entry.path,
                    code = entry.name,
                    title = entry.name,
                    displayTitle = entry.name,
                    mediaRoot = "smb/$sourceId",
                    fileSize = entry.sizeBytes,
                    size = entry.sizeBytes,
                )
            }
            .sortedBy { it.title.orEmpty() }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(
    container: AppContainer,
    session: Session,
    onNavigate: (String) -> Unit,
    onError: (Throwable) -> Unit,
    chromeVisible: Boolean = true,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val vm: FavoritesViewModel = viewModel(factory = viewModelFactory { FavoritesViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var filter by remember { mutableStateOf("全部") }
    var query by remember { mutableStateOf("") }
    var searchVisible by remember { mutableStateOf(false) }
    var moreVisible by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    SyncChromeWithGridScroll(gridState, onChromeVisibleChange)

    LaunchedEffect(Unit) {
        onChromeVisibleChange(true)
    }

    LaunchedEffect(session.serverUrl, session.activeProviderType, session.activeLibrary) {
        if (shouldLoadRemoteContent(session)) {
            vm.refresh(session.activeProviderType, session.activeLibrary)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let(onError)
    }

    val movies = remember(state.movies, filter, query) {
        state.movies
            .filterFavorites(filter)
            .filterFavoritesQuery(query)
    }
    val provider = remember(session.activeProviderType, container) {
        container.mediaProviderFor(session.activeProviderType)
    }

    Scaffold(containerColor = Color.Transparent) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (!shouldLoadRemoteContent(session)) {
                FavoriteEmptyState("请先在设置页连接 MediaTree 服务器")
            } else if (state.loading && state.movies.isEmpty()) {
                LoadingPane(Modifier.fillMaxSize())
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(142.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, top = 86.dp, end = 20.dp, bottom = 116.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(favoriteFilters) { item ->
                            DesignFilterChip(
                                selected = filter == item,
                                onClick = { filter = item },
                                label = item,
                            )
                        }
                    }
                }
                if (searchVisible) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        OutlinedTextField(
                            value = query,
                            onValueChange = { query = it },
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                            placeholder = { Text("搜索收藏") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                if (movies.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        FavoriteEmptyState()
                    }
                } else {
                    items(
                        items = movies,
                        key = { it.id },
                        span = { movie -> GridItemSpan(if (movie.isEpisodeFavorite()) maxLineSpan else 1) },
                    ) { movie ->
                        if (movie.isEpisodeFavorite()) {
                            EpisodeLandscapeCard(
                                movie = movie,
                                imageUrl = movie.mediaRoot?.smbLibrarySourceId()?.let { null } ?: provider.episodeStillUrl(session.serverUrl, movie.id),
                                onClick = { onNavigate(movie.openRoute()) },
                                showFavorite = true,
                            )
                        } else {
                            MoviePosterCard(
                                movie = movie,
                                imageUrl = movie.mediaRoot?.smbLibrarySourceId()?.let { null } ?: provider.coverUrl(session.serverUrl, movie.id),
                                onClick = { onNavigate(movie.openRoute()) },
                            )
                        }
                    }
                    if (state.movies.size < state.total) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Button(
                                onClick = {
                                    if (shouldLoadRemoteContent(session)) {
                                        vm.loadMore(session.activeProviderType, session.activeLibrary)
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                if (state.loading) {
                                    CircularProgressIndicator(modifier = Modifier.height(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                }
            }
            }
            AnimatedVisibility(
                visible = chromeVisible,
                enter = topChromeEnterTransition(),
                exit = topChromeExitTransition(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                DesignTopAppBar(
                    title = "收藏",
                    actions = {
                        IconButton(onClick = { searchVisible = !searchVisible }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = {}, enabled = false) {
                            Icon(Icons.Default.Tune, contentDescription = "筛选未实现")
                        }
                        Box {
                            IconButton(onClick = { moreVisible = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(expanded = moreVisible, onDismissRequest = { moreVisible = false }) {
                                DropdownMenuItem(
                                    text = { Text("刷新") },
                                    leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                    onClick = {
                                        moreVisible = false
                                        if (shouldLoadRemoteContent(session)) {
                                            vm.refresh(session.activeProviderType, session.activeLibrary)
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun FavoriteEmptyState(text: String = "还没有收藏") {
    Box(
        modifier = Modifier.fillMaxWidth().height(220.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                Icons.Default.Bookmarks,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.height(42.dp),
            )
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun List<MovieDto>.filterFavorites(filter: String): List<MovieDto> = when (filter) {
    "单集" -> filter { it.isEpisodeFavorite() }
    "剧集" -> filter { it.tmdbType == "tv" && !it.isEpisodeFavorite() }
    "电影" -> filter { it.tmdbType != "tv" && !it.isEpisodeFavorite() }
    else -> this
}

private fun List<MovieDto>.filterFavoritesQuery(query: String): List<MovieDto> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return this
    return filter {
        it.code.lowercase().contains(q) ||
            (it.title ?: "").lowercase().contains(q) ||
            (it.displayTitle ?: "").lowercase().contains(q)
    }
}

private fun MovieDto.routeId(): Int = id

private fun MovieDto.detailRoute(): String =
    "detail/${routeId()}" + path.takeIf { it.isNotBlank() }?.let { "?providerItemId=${android.net.Uri.encode(it)}" }.orEmpty()

private fun MovieDto.openRoute(): String =
    mediaRoot?.smbLibrarySourceId()?.let { sourceId -> "smbPlayer/$sourceId?path=${android.net.Uri.encode(path)}" } ?: detailRoute()

private fun String.toMovieRouteId(): Int =
    takeLast(8).toUIntOrNull(16)?.toInt() ?: hashCode()

private fun MovieDto.isEpisodeFavorite(): Boolean = tmdbEpisode != null || !episodeTitle.isNullOrBlank()

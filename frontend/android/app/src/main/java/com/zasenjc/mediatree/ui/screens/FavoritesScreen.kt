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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.zasenjc.mediatree.data.ApiException
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.ui.components.EpisodeLandscapeCard
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.MoviePosterCard
import com.zasenjc.mediatree.ui.components.SyncChromeWithGridScroll
import com.zasenjc.mediatree.ui.components.topChromeEnterTransition
import com.zasenjc.mediatree.ui.components.topChromeExitTransition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val favoriteFilters = listOf("全部", "单集", "剧集", "电影")

class FavoritesViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val page: Int = 0,
        val loading: Boolean = false,
        val movies: List<MovieDto> = emptyList(),
        val total: Int = 0,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun refresh() {
        loadPage(page = 0, replace = true)
    }

    fun loadMore() {
        val s = _state.value
        if (s.loading || s.movies.size >= s.total && s.total > 0) return
        loadPage(page = s.page + 1, replace = false)
    }

    private fun loadPage(page: Int, replace: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val response = container.api.favorites(limit = 48, offset = page * 48, sort = "release_date_desc")
                _state.update {
                    it.copy(
                        page = page,
                        loading = false,
                        movies = if (replace) response.movies else it.movies + response.movies,
                        total = response.total,
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
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
        vm.refresh()
    }

    LaunchedEffect(state.error) {
        state.error?.let { onError(ApiException(0, it)) }
    }

    val movies = remember(state.movies, filter, query) {
        state.movies
            .filterFavorites(filter)
            .filterFavoritesQuery(query)
    }

    Scaffold { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            if (state.loading && state.movies.isEmpty()) {
                LoadingPane(Modifier.fillMaxSize())
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(142.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 82.dp, end = 16.dp, bottom = 112.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(favoriteFilters) { item ->
                            FilterChip(
                                selected = filter == item,
                                onClick = { filter = item },
                                label = { Text(item) },
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
                                imageUrl = container.api.episodeStillUrl(session.serverUrl, movie.id),
                                onClick = { onNavigate("detail/${movie.id}") },
                                showFavorite = true,
                            )
                        } else {
                            MoviePosterCard(
                                movie = movie,
                                imageUrl = container.api.coverUrl(session.serverUrl, movie.id),
                                onClick = { onNavigate("detail/${movie.id}") },
                            )
                        }
                    }
                    if (state.movies.size < state.total) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Button(onClick = vm::loadMore, modifier = Modifier.fillMaxWidth()) {
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
                TopAppBar(
                    title = {
                        Text(
                            "mediatree",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    },
                    actions = {
                        IconButton(onClick = { searchVisible = !searchVisible }) {
                            Icon(Icons.Default.Search, contentDescription = "搜索")
                        }
                        IconButton(onClick = { }) {
                            Icon(Icons.Default.Tune, contentDescription = "筛选")
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
                                        vm.refresh()
                                    },
                                )
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                    ),
                )
            }
        }
    }
}

@Composable
private fun FavoriteEmptyState() {
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
            Text("还没有收藏", color = MaterialTheme.colorScheme.onSurfaceVariant)
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

private fun MovieDto.isEpisodeFavorite(): Boolean = tmdbEpisode != null || !episodeTitle.isNullOrBlank()

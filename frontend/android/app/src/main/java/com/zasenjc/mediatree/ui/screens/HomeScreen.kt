package com.zasenjc.mediatree.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zasenjc.mediatree.data.ApiException
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.MediaRootDto
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.ui.components.EpisodeLandscapeCard
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.MoviePosterCard
import com.zasenjc.mediatree.ui.components.SectionHeader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val sortOptions = listOf(
    "release_date_desc" to "上映日期",
    "created_desc" to "最新添加",
    "created_asc" to "最早添加",
    "title_asc" to "标题 A-Z",
)

private val mediaFilters = listOf("全部", "电影", "剧集", "新上架")

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val roots: List<MediaRootDto> = emptyList(),
        val recent: List<MovieDto> = emptyList(),
        val libraryMovies: List<MovieDto> = emptyList(),
        val sortMode: String = "release_date_desc",
        val selectedFilter: String = "全部",
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(activeLibrary: String, sort: String = _state.value.sortMode) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, sortMode = sort) }
            try {
                val roots = container.api.mediaRoots().items
                if (activeLibrary.isBlank()) {
                    roots.firstOrNull { !it.locked }?.let { container.sessionStore.setActiveLibrary(it.path) }
                }
                val lib = activeLibrary.ifBlank { roots.firstOrNull { !it.locked }?.path.orEmpty() }
                val recent = container.api.recentWatched(limit = 20, mediaRoot = lib).movies
                val movies = container.api.movies(
                    sort = sort,
                    limit = 60,
                    mediaRoot = lib,
                ).movies
                _state.update {
                    it.copy(
                        loading = false,
                        roots = roots,
                        recent = recent,
                        libraryMovies = movies,
                        sortMode = sort,
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun setFilter(filter: String) {
        _state.update { it.copy(selectedFilter = filter) }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    session: Session,
    onNavigate: (String) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }

    LaunchedEffect(session.serverUrl, session.activeLibrary) {
        vm.load(session.activeLibrary)
    }

    LaunchedEffect(state.error) {
        state.error?.let { onError(ApiException(0, it)) }
    }

    Scaffold(
        topBar = {
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
                    IconButton(onClick = { showSearch = true }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    Box {
                        IconButton(onClick = { showSort = true }) {
                            Icon(Icons.Default.Tune, contentDescription = "排序")
                        }
                        DropdownMenu(expanded = showSort, onDismissRequest = { showSort = false }) {
                            sortOptions.forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        showSort = false
                                        vm.load(session.activeLibrary, key)
                                    },
                                )
                            }
                        }
                    }
                    Box {
                        IconButton(onClick = { showMore = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                            DropdownMenuItem(
                                text = { Text("刷新") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showMore = false
                                    vm.load(session.activeLibrary)
                                },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (state.loading) {
            LoadingPane(Modifier.padding(padding))
        } else {
            val movies = remember(state.libraryMovies, state.selectedFilter) {
                state.libraryMovies.filterForHome(state.selectedFilter)
            }
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                if (state.recent.isNotEmpty()) {
                    item { SectionHeader("继续观看") }
                    item {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(state.recent, key = { it.id }) { movie ->
                                EpisodeLandscapeCard(
                                    movie = movie,
                                    imageUrl = container.api.episodeStillUrl(session.serverUrl, movie.id),
                                    onClick = { onNavigate("detail/${movie.id}") },
                                    modifier = Modifier.width(214.dp),
                                )
                            }
                        }
                    }
                }
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        SectionHeader("媒体库")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(mediaFilters) { filter ->
                                FilterChip(
                                    selected = state.selectedFilter == filter,
                                    onClick = { vm.setFilter(filter) },
                                    label = { Text(filter) },
                                )
                            }
                        }
                    }
                }
                if (movies.isEmpty()) {
                    item { EmptyMediaState("暂无媒体") }
                } else {
                    items(movies.chunked(3)) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { movie ->
                                MoviePosterCard(
                                    movie = movie,
                                    imageUrl = container.api.coverUrl(session.serverUrl, movie.id),
                                    onClick = { onNavigate("detail/${movie.id}") },
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            repeat(3 - row.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }

    if (showSearch) {
        SearchDialog(
            container = container,
            session = session,
            onDismiss = { showSearch = false },
            onNavigate = { path ->
                showSearch = false
                onNavigate(path)
            },
        )
    }
}

@Composable
private fun EmptyMediaState(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun SearchDialog(
    container: AppContainer,
    session: Session,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MovieDto>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(16.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { q ->
                        query = q
                        searchJob?.cancel()
                        if (q.isBlank()) {
                            results = emptyList()
                            searching = false
                        } else {
                            searching = true
                            searchJob = scope.launch {
                                delay(350)
                                try {
                                    val resp = container.api.movies(
                                        code = q,
                                        limit = 20,
                                        mediaRoot = session.activeLibrary,
                                    )
                                    results = resp.movies
                                } catch (_: Throwable) {
                                    results = emptyList()
                                }
                                searching = false
                            }
                        }
                    },
                    placeholder = { Text("搜索番号或标题") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                if (searching) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (query.isNotBlank() && results.isEmpty()) {
                    Text("无结果", modifier = Modifier.padding(16.dp))
                } else if (results.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.height(400.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(results, key = { it.id }) { movie ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onNavigate("detail/${movie.id}") }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                AsyncImage(
                                    model = container.api.coverUrl(session.serverUrl, movie.id),
                                    contentDescription = null,
                                    modifier = Modifier.width(60.dp).height(84.dp),
                                )
                                Spacer(Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = movie.displayTitle ?: movie.title ?: movie.code,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Text(
                                        text = movie.code,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun List<MovieDto>.filterForHome(filter: String): List<MovieDto> = when (filter) {
    "电影" -> filterNot { it.isEpisodeLike() || it.tmdbType == "tv" }
    "剧集" -> filter { it.isEpisodeLike() || it.tmdbType == "tv" }
    "新上架" -> take(24)
    else -> this
}

private fun MovieDto.isEpisodeLike(): Boolean = tmdbEpisode != null || !episodeTitle.isNullOrBlank()

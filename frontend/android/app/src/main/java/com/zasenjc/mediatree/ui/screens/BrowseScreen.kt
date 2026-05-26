package com.zasenjc.mediatree.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zasenjc.mediatree.data.ApiException
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.FolderNodeDto
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.ui.components.FolderCard
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.MoviePosterCard
import com.zasenjc.mediatree.ui.components.SyncChromeWithListScroll
import com.zasenjc.mediatree.ui.components.topChromeEnterTransition
import com.zasenjc.mediatree.ui.components.topChromeExitTransition
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val browseSortOptions = listOf(
    "created_desc" to "最新添加",
    "release_date_desc" to "上映日期",
    "title_asc" to "标题",
)

class BrowseViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val folders: List<FolderNodeDto> = emptyList(),
        val movies: List<MovieDto> = emptyList(),
        val total: Int = 0,
        val page: Int = 0,
        val currentFolder: String = "",
        val sortMode: String = "created_desc",
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(folder: String, mediaRoot: String, sort: String = _state.value.sortMode) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, sortMode = sort) }
            try {
                if (folder.isBlank()) {
                    val tree = container.api.folders(mediaRoot).tree.filter { it.movieCount > 0 }
                    _state.update {
                        it.copy(
                            loading = false,
                            folders = tree,
                            movies = emptyList(),
                            total = 0,
                            currentFolder = "",
                            page = 0,
                            sortMode = sort,
                        )
                    }
                } else {
                    val response = container.api.movies(
                        folder = folder,
                        sort = sort,
                        limit = 48,
                        offset = 0,
                        mediaRoot = mediaRoot,
                    )
                    _state.update {
                        it.copy(
                            loading = false,
                            movies = response.movies,
                            total = response.total,
                            currentFolder = folder,
                            folders = emptyList(),
                            page = 0,
                            sortMode = sort,
                        )
                    }
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun loadMore(folder: String, mediaRoot: String) {
        val s = _state.value
        val next = s.page + 1
        _state.update { it.copy(page = next) }
        viewModelScope.launch {
            try {
                val response = container.api.movies(
                    folder = folder,
                    sort = s.sortMode,
                    limit = 48,
                    offset = next * 48,
                    mediaRoot = mediaRoot,
                )
                _state.update { it.copy(movies = it.movies + response.movies, total = response.total) }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    container: AppContainer,
    session: Session,
    onNavigate: (String) -> Unit,
    onError: (Throwable) -> Unit,
    initialFolder: String,
    chromeVisible: Boolean = true,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val vm: BrowseViewModel = viewModel(factory = viewModelFactory { BrowseViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    SyncChromeWithListScroll(listState, onChromeVisibleChange)

    LaunchedEffect(Unit) {
        onChromeVisibleChange(true)
    }

    LaunchedEffect(session.activeLibrary, initialFolder) {
        vm.load(initialFolder, session.activeLibrary)
    }

    LaunchedEffect(state.error) {
        state.error?.let { onError(ApiException(0, it)) }
    }

    val title = state.currentFolder.substringAfterLast("/").ifBlank { "浏览" }
    val filteredFolders = remember(state.folders, query) { state.folders.filterFoldersByQuery(query) }
    val filteredMovies = remember(state.movies, query) { state.movies.filterMoviesByQuery(query) }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = { vm.load(initialFolder, session.activeLibrary, state.sortMode) }) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingPane(Modifier.fillMaxSize())
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, top = 82.dp, end = 16.dp, bottom = 112.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            BreadcrumbLine(activeLibrary = session.activeLibrary, folder = state.currentFolder)
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                placeholder = { Text(if (state.currentFolder.isBlank()) "搜索文件夹" else "搜索影片") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(browseSortOptions) { (key, label) ->
                                    FilterChip(
                                        selected = state.sortMode == key,
                                        onClick = { vm.load(initialFolder, session.activeLibrary, key) },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }
                    if (state.currentFolder.isBlank()) {
                        items(filteredFolders, key = { it.path }) { folder ->
                            FolderCard(
                                folder = folder,
                                imageUrl = null,
                                onClick = { onNavigate("browse?folder=${Uri.encode(folder.path)}") },
                            )
                        }
                        if (filteredFolders.isEmpty()) {
                            item { EmptyBrowseState("没有匹配的文件夹") }
                        }
                    } else {
                        item {
                            Text("共 ${state.total} 部", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        items(filteredMovies.chunked(2)) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { movie ->
                                    MoviePosterCard(
                                        movie = movie,
                                        imageUrl = container.api.coverUrl(session.serverUrl, movie.id),
                                        onClick = { onNavigate("detail/${movie.id}") },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                if (row.size == 1) Spacer(Modifier.weight(1f))
                            }
                        }
                        if (filteredMovies.isEmpty()) {
                            item { EmptyBrowseState("没有匹配的影片") }
                        }
                        if (state.movies.size < state.total) {
                            item {
                                Button(
                                    onClick = { vm.loadMore(state.currentFolder, session.activeLibrary) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("加载更多")
                                }
                            }
                        }
                        item { Spacer(Modifier.height(16.dp)) }
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
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        if (initialFolder.isNotBlank()) {
                            IconButton(onClick = {
                                val parent = initialFolder.trimEnd('/').substringBeforeLast("/", missingDelimiterValue = "")
                                if (parent.isNotBlank()) {
                                    onNavigate("browse?folder=${Uri.encode(parent)}")
                                } else {
                                    onNavigate("browse")
                                }
                            }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回上级")
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
private fun BreadcrumbLine(activeLibrary: String, folder: String) {
    val source = activeLibrary.substringAfterLast("/").ifBlank { "来源" }
    val leaf = folder.substringAfterLast("/").ifBlank { "根目录" }
    Text(
        text = "$source  >  $leaf",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

@Composable
private fun EmptyBrowseState(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
    )
}

private fun List<FolderNodeDto>.filterFoldersByQuery(query: String): List<FolderNodeDto> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return this
    return filter {
        it.name.lowercase().contains(q) ||
            it.path.lowercase().contains(q) ||
            (it.displayTitle ?: "").lowercase().contains(q)
    }
}

private fun List<MovieDto>.filterMoviesByQuery(query: String): List<MovieDto> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return this
    return filter {
        it.code.lowercase().contains(q) ||
            (it.title ?: "").lowercase().contains(q) ||
            (it.displayTitle ?: "").lowercase().contains(q)
    }
}

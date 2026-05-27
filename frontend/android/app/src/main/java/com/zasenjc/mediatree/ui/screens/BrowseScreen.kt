package com.zasenjc.mediatree.ui.screens

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.ViewAgenda
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.FolderNodeDto
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.MoviePosterCard
import com.zasenjc.mediatree.ui.components.SyncChromeWithListScroll
import com.zasenjc.mediatree.ui.components.topChromeEnterTransition
import com.zasenjc.mediatree.ui.components.topChromeExitTransition
import com.zasenjc.mediatree.ui.shouldLoadRemoteContent
import com.zasenjc.mediatree.util.UrlUtils
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val browseSortOptions = listOf(
    "name" to "名称",
    "modified" to "修改时间",
    "size" to "大小",
)

private val browseViewModes = listOf(
    BrowseViewMode("icon", "图标", Icons.Default.GridView),
    BrowseViewMode("list", "列表", Icons.Default.ViewList),
    BrowseViewMode("compact", "紧凑", Icons.Default.ViewAgenda),
    BrowseViewMode("poster", "封面图", Icons.Default.ViewModule),
)

private data class BrowseViewMode(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

class BrowseViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val folders: List<FolderNodeDto> = emptyList(),
        val movies: List<MovieDto> = emptyList(),
        val total: Int = 0,
        val page: Int = 0,
        val currentFolder: String = "",
        val sortMode: String = "name",
        val error: Throwable? = null,
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
                        sort = sort.toApiMovieSort(),
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
                _state.update { it.copy(loading = false, error = e) }
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
                    sort = s.sortMode.toApiMovieSort(),
                    limit = 48,
                    offset = next * 48,
                    mediaRoot = mediaRoot,
                )
                _state.update { it.copy(movies = it.movies + response.movies, total = response.total) }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e) }
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
    var viewMode by remember { mutableStateOf("list") }
    val listState = rememberLazyListState()

    SyncChromeWithListScroll(listState, onChromeVisibleChange)

    LaunchedEffect(Unit) {
        onChromeVisibleChange(true)
    }

    LaunchedEffect(session.serverUrl, session.activeLibrary, initialFolder) {
        if (shouldLoadRemoteContent(session)) {
            vm.load(initialFolder, session.activeLibrary)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let(onError)
    }

    val title = state.currentFolder.substringAfterLast("/").ifBlank { "浏览" }
    val filteredFolders = remember(state.folders, query, state.sortMode) {
        state.folders.filterFoldersByQuery(query).sortedFoldersForBrowse(state.sortMode)
    }
    val filteredMovies = remember(state.movies, query, state.sortMode) {
        state.movies.filterMoviesByQuery(query).sortedMoviesForBrowse(state.sortMode)
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (shouldLoadRemoteContent(session)) {
                        vm.load(initialFolder, session.activeLibrary, state.sortMode)
                    }
                },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !shouldLoadRemoteContent(session) -> EmptyBrowseState("请先在设置页连接 MediaTree 服务器")
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
                                items(browseViewModes) { mode ->
                                    FilterChip(
                                        selected = viewMode == mode.key,
                                        onClick = { viewMode = mode.key },
                                        label = { Text(mode.label) },
                                        leadingIcon = {
                                            Icon(
                                                mode.icon,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                            )
                                        },
                                    )
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(browseSortOptions) { (key, label) ->
                                    FilterChip(
                                        selected = state.sortMode == key,
                                        onClick = {
                                            if (shouldLoadRemoteContent(session)) {
                                                vm.load(initialFolder, session.activeLibrary, key)
                                            }
                                        },
                                        label = { Text(label) },
                                    )
                                }
                            }
                        }
                    }
                    if (state.currentFolder.isBlank()) {
                        when (viewMode) {
                            "poster" -> {
                                items(filteredFolders.chunked(3)) { row ->
                                    PosterFolderRow(
                                        row = row,
                                        serverUrl = session.serverUrl,
                                        onOpen = { folder -> onNavigate("browse?folder=${Uri.encode(folder.path)}") },
                                    )
                                }
                            }
                            "icon" -> {
                                items(filteredFolders.chunked(3)) { row ->
                                    IconFolderRow(
                                        row = row,
                                        onOpen = { folder -> onNavigate("browse?folder=${Uri.encode(folder.path)}") },
                                    )
                                }
                            }
                            "compact" -> {
                                items(filteredFolders, key = { it.path }) { folder ->
                                    CompactFolderRow(folder = folder, onClick = { onNavigate("browse?folder=${Uri.encode(folder.path)}") })
                                }
                            }
                            else -> {
                                items(filteredFolders, key = { it.path }) { folder ->
                                    FolderListRow(folder = folder, onClick = { onNavigate("browse?folder=${Uri.encode(folder.path)}") })
                                }
                            }
                        }
                        if (filteredFolders.isEmpty()) {
                            item { EmptyBrowseState("没有匹配的文件夹") }
                        }
                    } else {
                        item {
                            Text("共 ${state.total} 部", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        when (viewMode) {
                            "poster" -> {
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
                            }
                            "icon" -> {
                                items(filteredMovies.chunked(3)) { row ->
                                    IconMovieRow(
                                        row = row,
                                        onOpen = { movie -> onNavigate("detail/${movie.id}") },
                                    )
                                }
                            }
                            "compact" -> {
                                items(filteredMovies, key = { it.id }) { movie ->
                                    CompactMovieRow(movie = movie, onClick = { onNavigate("detail/${movie.id}") })
                                }
                            }
                            else -> {
                                items(filteredMovies, key = { it.id }) { movie ->
                                    MovieListRow(
                                        movie = movie,
                                        imageUrl = container.api.coverUrl(session.serverUrl, movie.id),
                                        onClick = { onNavigate("detail/${movie.id}") },
                                    )
                                }
                            }
                        }
                        if (filteredMovies.isEmpty()) {
                            item { EmptyBrowseState("没有匹配的影片") }
                        }
                        if (state.movies.size < state.total) {
                            item {
                                Button(
                                    onClick = {
                                        if (shouldLoadRemoteContent(session)) {
                                            vm.loadMore(state.currentFolder, session.activeLibrary)
                                        }
                                    },
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
private fun PosterFolderRow(
    row: List<FolderNodeDto>,
    serverUrl: String,
    onOpen: (FolderNodeDto) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { folder ->
            FolderPosterCard(
                folder = folder,
                imageUrl = UrlUtils.resolveApiUrl(serverUrl, folder.randomCover ?: folder.cover),
                onClick = { onOpen(folder) },
                modifier = Modifier.weight(1f),
            )
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun FolderPosterCard(
    folder: FolderNodeDto,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = folder.browseTitle()
    ElevatedCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (imageUrl == null) {
                    Icon(
                        Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(36.dp),
                    )
                } else {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            Text(
                folder.releaseDateMax?.take(4).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
    }
}

@Composable
private fun IconFolderRow(row: List<FolderNodeDto>, onOpen: (FolderNodeDto) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { folder ->
            IconTile(
                title = folder.browseTitle(),
                subtitle = "${folder.movieCount} 项",
                icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(34.dp)) },
                onClick = { onOpen(folder) },
                modifier = Modifier.weight(1f),
            )
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun IconMovieRow(row: List<MovieDto>, onOpen: (MovieDto) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { movie ->
            IconTile(
                title = movie.browseTitle(),
                subtitle = movie.releaseDate?.take(4).orEmpty(),
                icon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(34.dp)) },
                onClick = { onOpen(movie) },
                modifier = Modifier.weight(1f),
            )
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun IconTile(
    title: String,
    subtitle: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ElevatedCard(
        modifier = modifier.defaultMinSize(minHeight = 112.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(Modifier.padding(12.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Text(title, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun FolderListRow(folder: FolderNodeDto, onClick: () -> Unit) {
    BrowserListRow(
        title = folder.browseTitle(),
        subtitle = folder.folderMeta(),
        trailing = folder.createdMax?.take(10).orEmpty(),
        onClick = onClick,
        leading = {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(10.dp).size(28.dp))
            }
        },
    )
}

@Composable
private fun MovieListRow(movie: MovieDto, imageUrl: String?, onClick: () -> Unit) {
    BrowserListRow(
        title = movie.browseTitle(),
        subtitle = movie.movieMeta(),
        trailing = movie.updatedAt?.take(10) ?: movie.createdAt?.take(10).orEmpty(),
        onClick = onClick,
        leading = {
            AsyncImage(
                model = imageUrl,
                contentDescription = movie.browseTitle(),
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 46.dp, height = 62.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
            )
        },
    )
}

@Composable
private fun BrowserListRow(
    title: String,
    subtitle: String,
    trailing: String,
    onClick: () -> Unit,
    leading: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Text(trailing, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CompactFolderRow(folder: FolderNodeDto, onClick: () -> Unit) {
    CompactBrowserRow(
        title = folder.browseTitle(),
        meta = "${folder.movieCount} 项",
        onClick = onClick,
        icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp)) },
    )
}

@Composable
private fun CompactMovieRow(movie: MovieDto, onClick: () -> Unit) {
    CompactBrowserRow(
        title = movie.browseTitle(),
        meta = movie.releaseDate?.take(4) ?: movie.duration?.let { "${it} 分钟" }.orEmpty(),
        onClick = onClick,
        icon = { Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(20.dp)) },
    )
}

@Composable
private fun CompactBrowserRow(
    title: String,
    meta: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.primary,
            ) {
                Box(Modifier.padding(7.dp), contentAlignment = Alignment.Center) { icon() }
            }
            Text(title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Text(meta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
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

private fun String.toApiMovieSort(): String = when (this) {
    "name" -> "name"
    else -> "created_desc"
}

private fun List<FolderNodeDto>.sortedFoldersForBrowse(sort: String): List<FolderNodeDto> = when (sort) {
    "modified" -> sortedWith(compareByDescending<FolderNodeDto> { it.createdMax.orEmpty() }.thenBy { it.browseTitle() })
    "size" -> sortedWith(compareByDescending<FolderNodeDto> { it.movieCount }.thenBy { it.browseTitle() })
    else -> sortedBy { it.browseTitle() }
}

private fun List<MovieDto>.sortedMoviesForBrowse(sort: String): List<MovieDto> = when (sort) {
    "modified" -> sortedWith(compareByDescending<MovieDto> { it.updatedAt ?: it.createdAt.orEmpty() }.thenBy { it.browseTitle() })
    "size" -> sortedWith(compareByDescending<MovieDto> { it.fileSize ?: it.size ?: (it.duration?.toLong() ?: 0L) }.thenBy { it.browseTitle() })
    else -> sortedWith(compareBy<MovieDto> { it.browseTitle() }.thenBy { it.code })
}

private fun FolderNodeDto.browseTitle(): String = displayTitle ?: name.ifBlank { path.substringAfterLast("/") }

private fun FolderNodeDto.folderMeta(): String =
    listOf(
        "${movieCount} 项",
        createdMax?.take(10),
    ).filter { !it.isNullOrBlank() }.joinToString(" · ")

private fun MovieDto.browseTitle(): String = displayTitle ?: title ?: code

private fun MovieDto.movieMeta(): String =
    listOf(
        code.takeIf { it.isNotBlank() },
        releaseDate?.take(4),
        readableSize(),
    ).filter { !it.isNullOrBlank() }.joinToString(" · ")

private fun MovieDto.readableSize(): String? {
    val bytes = fileSize ?: size
    if (bytes != null && bytes > 0) {
        val gb = bytes / 1024.0 / 1024.0 / 1024.0
        return if (gb >= 1) {
            String.format("%.1f GB", gb)
        } else {
            String.format("%.0f MB", bytes / 1024.0 / 1024.0)
        }
    }
    return duration?.let { "${it} 分钟" }
}

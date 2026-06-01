package com.zasenjc.mediatree.ui.screens

import android.graphics.Bitmap
import android.os.Build
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.SystemClock
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.ClientStorageSource
import com.zasenjc.mediatree.data.ClientStorageType
import com.zasenjc.mediatree.data.FolderNodeDto
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.webDavLibraryPath
import com.zasenjc.mediatree.data.webDavLibrarySourceId
import com.zasenjc.mediatree.data.smbLibrarySourceId
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.playback.PlaybackSource
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.MediaAsyncImage
import com.zasenjc.mediatree.ui.components.MoviePosterCard
import com.zasenjc.mediatree.ui.components.SyncChromeWithListScroll
import com.zasenjc.mediatree.ui.components.DesignFilterChip
import com.zasenjc.mediatree.ui.components.DesignTopAppBar
import com.zasenjc.mediatree.ui.components.topChromeEnterTransition
import com.zasenjc.mediatree.ui.components.topChromeExitTransition
import com.zasenjc.mediatree.ui.shouldLoadRemoteContent
import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.graphics.Color

private val browseSortOptions = listOf(
    "name" to "名称",
    "modified" to "修改时间",
    "size" to "大小",
)

private val browseViewModes = listOf(
    BrowseViewMode("icon", "图标", Icons.Default.GridView),
    BrowseViewMode("compact", "紧凑", Icons.Default.ViewAgenda),
    BrowseViewMode("poster", "封面图", Icons.Default.ViewModule),
)

private data class BrowseViewMode(
    val key: String,
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
)

private typealias MountedVideoThumbnailLoader = suspend (ClientStorageSource?, MovieDto) -> Bitmap?

class BrowseViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val folders: List<FolderNodeDto> = emptyList(),
        val movies: List<MovieDto> = emptyList(),
        val total: Int = 0,
        val page: Int = 0,
        val currentFolder: String = "",
        val sortMode: String = "name",
        val mountedSource: ClientStorageSource? = null,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()
    private val mountedVideoFrameCache = MountedVideoFrameMemoryCache()
    private val mountedVideoFrameRequests = mutableMapOf<String, Deferred<Bitmap?>>()

    fun load(
        providerType: ProviderType,
        folder: String,
        mediaRoot: String,
        sort: String = _state.value.sortMode,
        recursiveVideosOnly: Boolean = false,
    ) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, sortMode = sort) }
            try {
                val smbSourceId = mediaRoot.smbLibrarySourceId()
                if (smbSourceId != null) {
                    loadSmb(smbSourceId, folder, sort, recursiveVideosOnly)
                    return@launch
                }
                val webDavSourceId = mediaRoot.webDavLibrarySourceId()
                if (webDavSourceId != null) {
                    loadWebDav(webDavSourceId, folder, sort, recursiveVideosOnly)
                    return@launch
                }
                val provider = container.mediaProviderFor(providerType)
                val folders = if (providerType == ProviderType.MediaTree) {
                    provider.folders(mediaRoot).tree.childrenForBrowse(folder)
                } else {
                    provider.folders(folder.ifBlank { mediaRoot }).tree
                }.sortedFoldersForBrowse(sort)
                val response = provider.movies(
                    folder = folder,
                    sort = sort.toApiMovieSort(),
                    limit = 48,
                    offset = 0,
                    mediaRoot = mediaRoot,
                )
                _state.update {
                    it.copy(
                        loading = false,
                        folders = folders,
                        movies = response?.movies.orEmpty(),
                        total = response?.total ?: 0,
                        currentFolder = folder,
                        page = 0,
                        sortMode = sort,
                        mountedSource = null,
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    private suspend fun loadSmb(sourceId: String, folder: String, sort: String, recursiveVideosOnly: Boolean) {
        val source = container.clientStorageRepository.load()
            .firstOrNull { it.id == sourceId && it.type == com.zasenjc.mediatree.data.ClientStorageType.SMB && it.enabled }
            ?: throw IllegalArgumentException("SMB 存储源不可用")
        val entries = if (recursiveVideosOnly) {
            collectSmbVideoEntries(source, folder)
        } else {
            container.smbClient.list(source, folder)
        }
        val folders = if (recursiveVideosOnly) emptyList() else entries.filter { it.isDirectory }
            .map { entry ->
                FolderNodeDto(
                    name = entry.name,
                    path = entry.path,
                    isLeaf = false,
                    displayTitle = entry.name,
                    mediaRoot = mediaRootPath(sourceId),
                )
            }
            .sortedFoldersForBrowse(sort)
        val movies = entries.filter { it.isPlayableVideo }
            .map { entry ->
                MovieDto(
                    id = (source.id + ":" + entry.path).hashCode(),
                    path = entry.path,
                    code = entry.name,
                    title = entry.name,
                    displayTitle = entry.name,
                    mediaRoot = mediaRootPath(sourceId),
                    fileSize = entry.sizeBytes,
                    size = entry.sizeBytes,
                )
            }
            .sortedMoviesForBrowse(sort)
        _state.update {
            it.copy(
                loading = false,
                folders = folders,
                movies = movies,
                total = movies.size,
                currentFolder = folder,
                page = 0,
                sortMode = sort,
                mountedSource = source,
            )
        }
    }

    private suspend fun collectSmbVideoEntries(source: ClientStorageSource, folder: String): List<com.zasenjc.mediatree.data.SmbEntry> {
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        val videos = mutableListOf<com.zasenjc.mediatree.data.SmbEntry>()
        pending.add(folder)
        while (pending.isNotEmpty()) {
            val currentFolder = pending.removeFirst()
            if (!visited.add(currentFolder)) continue
            container.smbClient.list(source, currentFolder).forEach { entry ->
                when {
                    entry.isDirectory -> pending.add(entry.path)
                    entry.isPlayableVideo -> videos.add(entry)
                }
            }
        }
        return videos
    }

    private suspend fun loadWebDav(sourceId: String, folder: String, sort: String, recursiveVideosOnly: Boolean) {
        val source = container.clientStorageRepository.load()
            .firstOrNull { it.id == sourceId && it.type == com.zasenjc.mediatree.data.ClientStorageType.WebDAV && it.enabled }
            ?: throw IllegalArgumentException("WebDAV 存储源不可用")
        val entries = if (recursiveVideosOnly) {
            collectWebDavVideoEntries(source, folder)
        } else {
            container.webDavClient.list(source, folder)
        }
        val folders = if (recursiveVideosOnly) emptyList() else entries.filter { it.isDirectory }
            .map { entry ->
                FolderNodeDto(
                    name = entry.name,
                    path = entry.path,
                    isLeaf = false,
                    displayTitle = entry.name,
                    mediaRoot = webDavLibraryPath(sourceId),
                )
            }
            .sortedFoldersForBrowse(sort)
        val movies = entries.filter { it.isPlayableVideo }
            .map { entry ->
                MovieDto(
                    id = (source.id + ":" + entry.path).hashCode(),
                    path = entry.path,
                    code = entry.name,
                    title = entry.name,
                    displayTitle = entry.name,
                    mediaRoot = webDavLibraryPath(sourceId),
                    fileSize = entry.sizeBytes,
                    size = entry.sizeBytes,
                )
            }
            .sortedMoviesForBrowse(sort)
        _state.update {
            it.copy(
                loading = false,
                folders = folders,
                movies = movies,
                total = movies.size,
                currentFolder = folder,
                page = 0,
                sortMode = sort,
                mountedSource = source,
            )
        }
    }

    fun loadMore(providerType: ProviderType, folder: String, mediaRoot: String) {
        if (mediaRoot.smbLibrarySourceId() != null || mediaRoot.webDavLibrarySourceId() != null) return
        val s = _state.value
        val next = s.page + 1
        _state.update { it.copy(page = next) }
        viewModelScope.launch {
            try {
                val response = container.mediaProviderFor(providerType).movies(
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

    private suspend fun collectWebDavVideoEntries(source: ClientStorageSource, folder: String): List<com.zasenjc.mediatree.data.WebDavEntry> {
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        val videos = mutableListOf<com.zasenjc.mediatree.data.WebDavEntry>()
        pending.add(folder)
        while (pending.isNotEmpty()) {
            val currentFolder = pending.removeFirst()
            if (!visited.add(currentFolder)) continue
            container.webDavClient.list(source, currentFolder).forEach { entry ->
                when {
                    entry.isDirectory -> pending.add(entry.path)
                    entry.isPlayableVideo -> videos.add(entry)
                }
            }
        }
        return videos
    }

    suspend fun loadMountedVideoFrame(source: ClientStorageSource?, movie: MovieDto): Bitmap? {
        val cacheKey = mountedVideoFrameCacheKey(source, movie) ?: return null
        mountedVideoFrameCache.getCached(cacheKey)?.let { return it }
        val request = mountedVideoFrameRequests[cacheKey] ?: viewModelScope.async {
            val sourceInfo = mountedVideoThumbnailSource(container, source, movie) ?: return@async null
            try {
                extractMountedVideoFrame(sourceInfo)
                    ?.also { frame -> mountedVideoFrameCache.putCached(cacheKey, frame) }
            } finally {
                sourceInfo.onClose?.invoke()
            }
        }.also { newRequest ->
            mountedVideoFrameRequests[cacheKey] = newRequest
            newRequest.invokeOnCompletion {
                mountedVideoFrameRequests.remove(cacheKey)
            }
        }
        return request.await()
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
    recursiveVideosOnly: Boolean = false,
    viewMode: String,
    onViewModeChange: (String) -> Unit,
    chromeVisible: Boolean = true,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val vm: BrowseViewModel = viewModel(factory = viewModelFactory { BrowseViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val mountedVideoThumbnailLoader: MountedVideoThumbnailLoader = remember(vm) { vm::loadMountedVideoFrame }
    val listState = rememberLazyListState()

    SyncChromeWithListScroll(listState, onChromeVisibleChange)

    LaunchedEffect(Unit) {
        onChromeVisibleChange(true)
    }

    LaunchedEffect(session.serverUrl, session.activeProviderType, session.activeLibrary, initialFolder, recursiveVideosOnly) {
        val smbSourceId = session.activeLibrary.smbLibrarySourceId()
        val webDavSourceId = session.activeLibrary.webDavLibrarySourceId()
        if (shouldLoadRemoteContent(session) || smbSourceId != null || webDavSourceId != null) {
            vm.load(
                providerType = session.activeProviderType,
                folder = initialFolder,
                mediaRoot = session.activeLibrary,
                recursiveVideosOnly = recursiveVideosOnly,
            )
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let(onError)
    }

    val title = state.currentFolder.substringAfterLast("/").ifBlank { "浏览" }
    fun openFolderNode(folder: FolderNodeDto) {
        if (folder.isLeaf && session.activeProviderType != ProviderType.MediaTree) {
            onNavigate(folder.detailRoute())
        } else {
            onNavigate("browse?folder=${Uri.encode(folder.path)}")
        }
    }
    val filteredFolders = remember(state.folders, query, state.sortMode) {
        state.folders.filterFoldersByQuery(query).sortedFoldersForBrowse(state.sortMode)
    }
    val filteredMovies = remember(state.movies, query, state.sortMode) {
        state.movies.filterMoviesByQuery(query).sortedMoviesForBrowse(state.sortMode)
    }
    val provider = remember(session.activeProviderType, container) {
        container.mediaProviderFor(session.activeProviderType)
    }
    val posterFolderRows = remember(filteredFolders) { filteredFolders.chunked(3) }
    val iconFolderRows = posterFolderRows
    val posterMovieRows = remember(filteredMovies) { filteredMovies.chunked(2) }
    val iconMovieRows = remember(filteredMovies) { filteredMovies.chunked(3) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val smbSourceId = session.activeLibrary.smbLibrarySourceId()
                    val webDavSourceId = session.activeLibrary.webDavLibrarySourceId()
                    if (shouldLoadRemoteContent(session) || smbSourceId != null || webDavSourceId != null) {
                        vm.load(
                            providerType = session.activeProviderType,
                            folder = initialFolder,
                            mediaRoot = session.activeLibrary,
                            sort = state.sortMode,
                            recursiveVideosOnly = recursiveVideosOnly,
                        )
                    }
                },
            ) {
                Icon(Icons.Default.Refresh, contentDescription = "刷新")
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                !shouldLoadRemoteContent(session) &&
                    session.activeLibrary.smbLibrarySourceId() == null &&
                    session.activeLibrary.webDavLibrarySourceId() == null -> EmptyBrowseState("请先在设置页连接 MediaTree 服务器")
                state.loading -> LoadingPane(Modifier.fillMaxSize())
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, top = 86.dp, end = 20.dp, bottom = 116.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                    item {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = query,
                                onValueChange = { query = it },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                placeholder = { Text("搜索项目") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(browseViewModes) { mode ->
                                    DesignFilterChip(
                                        selected = viewMode == mode.key,
                                        onClick = { onViewModeChange(mode.key) },
                                        label = mode.label,
                                        icon = mode.icon,
                                    )
                                }
                            }
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(browseSortOptions) { (key, label) ->
                                    DesignFilterChip(
                                        selected = state.sortMode == key,
                                        onClick = {
                                            val smbSourceId = session.activeLibrary.smbLibrarySourceId()
                                            val webDavSourceId = session.activeLibrary.webDavLibrarySourceId()
                                            if (shouldLoadRemoteContent(session) || smbSourceId != null || webDavSourceId != null) {
                                                vm.load(
                                                    providerType = session.activeProviderType,
                                                    folder = initialFolder,
                                                    mediaRoot = session.activeLibrary,
                                                    sort = key,
                                                    recursiveVideosOnly = recursiveVideosOnly,
                                                )
                                            }
                                        },
                                        label = label,
                                    )
                                }
                            }
                        }
                    }
                    if (filteredFolders.isNotEmpty()) {
                        when (viewMode) {
                            "poster" -> {
                                items(posterFolderRows, key = { row -> row.joinToString("|") { it.path } }) { row ->
                                    PosterFolderRow(
                                        row = row,
                                        serverUrl = session.serverUrl,
                                        onOpen = ::openFolderNode,
                                    )
                                }
                            }
                            "icon" -> {
                                items(iconFolderRows, key = { row -> row.joinToString("|") { it.path } }) { row ->
                                    IconFolderRow(
                                        row = row,
                                        onOpen = ::openFolderNode,
                                    )
                                }
                            }
                            "compact" -> {
                                items(filteredFolders, key = { it.path }) { folder ->
                                    CompactFolderRow(folder = folder, onClick = { openFolderNode(folder) })
                                }
                            }
                            else -> {
                                items(filteredFolders, key = { it.path }) { folder ->
                                    CompactFolderRow(folder = folder, onClick = { openFolderNode(folder) })
                                }
                            }
                        }
                    }
                    if (filteredMovies.isNotEmpty() || state.currentFolder.isNotBlank()) {
                        when (viewMode) {
                            "poster" -> {
                                items(posterMovieRows, key = { row -> row.joinToString("|") { it.id.toString() } }) { row ->
                                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                        row.forEach { movie ->
                                            if (movie.isMountedLibraryItem()) {
                                                MountedVideoPosterCard(
                                                    thumbnailLoader = mountedVideoThumbnailLoader,
                                                    source = state.mountedSource,
                                                    movie = movie,
                                                    onClick = { onNavigate(movie.openRoute()) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            } else {
                                                MoviePosterCard(
                                                    movie = movie,
                                                    imageUrl = provider.coverUrl(session.serverUrl, movie.id),
                                                    onClick = { onNavigate(movie.openRoute()) },
                                                    modifier = Modifier.weight(1f),
                                                )
                                            }
                                        }
                                        if (row.size == 1) Spacer(Modifier.weight(1f))
                                    }
                                }
                            }
                            "icon" -> {
                                items(iconMovieRows, key = { row -> row.joinToString("|") { it.id.toString() } }) { row ->
                                    IconMovieRow(
                                        thumbnailLoader = mountedVideoThumbnailLoader,
                                        source = state.mountedSource,
                                        row = row,
                                        onOpen = { movie -> onNavigate(movie.openRoute()) },
                                    )
                                }
                            }
                            "compact" -> {
                                items(filteredMovies, key = { it.id }) { movie ->
                                    CompactMovieRow(
                                        thumbnailLoader = mountedVideoThumbnailLoader,
                                        source = state.mountedSource,
                                        movie = movie,
                                        onClick = { onNavigate(movie.openRoute()) },
                                    )
                                }
                            }
                            else -> {
                                items(filteredMovies, key = { it.id }) { movie ->
                                    CompactMovieRow(
                                        thumbnailLoader = mountedVideoThumbnailLoader,
                                        source = state.mountedSource,
                                        movie = movie,
                                        onClick = { onNavigate(movie.openRoute()) },
                                    )
                                }
                            }
                        }
                        if (state.movies.size < state.total) {
                            item {
                                Button(
                                    onClick = {
                                        if (shouldLoadRemoteContent(session)) {
                                            vm.loadMore(session.activeProviderType, state.currentFolder, session.activeLibrary)
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    Text("加载更多")
                                }
                            }
                        }
                    }
                    if (filteredFolders.isEmpty() && filteredMovies.isEmpty()) {
                        item { EmptyBrowseState(if (state.currentFolder.isBlank()) "没有匹配的项目" else "此目录没有可显示项目") }
                    }
                    item { Spacer(Modifier.height(16.dp)) }
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
                    title = title,
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
                )
            }
        }
    }
}

@Composable
private fun DesignFolderRow(folder: FolderNodeDto, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 1.dp,
        shadowElevation = 2.dp,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.padding(8.dp).size(24.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    folder.browseTitle(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(folder.folderMeta(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
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
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
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
                    MediaAsyncImage(
                        imageUrl = imageUrl,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        cornerRadius = 12.dp,
                    )
                }
            }
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
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
                subtitle = folder.folderMeta(),
                icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(34.dp)) },
                onClick = { onOpen(folder) },
                modifier = Modifier.weight(1f),
            )
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun IconMovieRow(
    thumbnailLoader: MountedVideoThumbnailLoader,
    source: ClientStorageSource?,
    row: List<MovieDto>,
    onOpen: (MovieDto) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { movie ->
            if (movie.isMountedLibraryItem()) {
                MountedVideoIconTile(
                    thumbnailLoader = thumbnailLoader,
                    source = source,
                    movie = movie,
                    onClick = { onOpen(movie) },
                    modifier = Modifier.weight(1f),
                )
            } else {
                IconTile(
                    title = movie.browseTitle(),
                    subtitle = movie.iconMovieMeta(),
                    icon = {
                        Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(34.dp))
                    },
                    onClick = { onOpen(movie) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
    }
}

@Composable
private fun MountedVideoIconTile(
    thumbnailLoader: MountedVideoThumbnailLoader,
    source: ClientStorageSource?,
    movie: MovieDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MountedVideoThumbnail(
            thumbnailLoader = thumbnailLoader,
            source = source,
            movie = movie,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f),
            showPlayIcon = false,
        )
        Text(
            text = movie.browseTitle(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
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
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun MountedVideoPosterCard(
    thumbnailLoader: MountedVideoThumbnailLoader,
    source: ClientStorageSource?,
    movie: MovieDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        MountedVideoThumbnail(
            thumbnailLoader = thumbnailLoader,
            source = source,
            movie = movie,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            cornerRadius = 16.dp,
        )
        Text(
            text = movie.browseTitle(),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = movie.iconMovieMeta(),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun MountedVideoThumbnail(
    thumbnailLoader: MountedVideoThumbnailLoader,
    source: ClientStorageSource?,
    movie: MovieDto,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 12.dp,
    showPlayIcon: Boolean = true,
) {
    var bitmap by remember(source?.id, source?.type, movie.path, movie.fileSize, movie.size) {
        mutableStateOf<Bitmap?>(null)
    }
    LaunchedEffect(thumbnailLoader, source?.id, movie.mediaRoot, movie.path, movie.fileSize, movie.size) {
        if (bitmap == null) {
            bitmap = thumbnailLoader(source, movie)
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { frame ->
            Image(
                bitmap = frame.asImageBitmap(),
                contentDescription = movie.browseTitle(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (bitmap == null) {
            Icon(
                imageVector = Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                modifier = Modifier.fillMaxSize(0.34f),
            )
        }
        if (showPlayIcon) {
            Surface(
                modifier = Modifier.align(Alignment.Center),
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.primary.copy(alpha = if (bitmap == null) 0.92f else 0.78f),
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.padding(7.dp).size(20.dp),
                )
            }
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
            MediaAsyncImage(
                imageUrl = imageUrl,
                contentDescription = movie.browseTitle(),
                modifier = Modifier.size(width = 46.dp, height = 62.dp),
                cornerRadius = 10.dp,
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
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            leading()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
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
        meta = folder.folderMeta(),
        onClick = onClick,
        icon = { Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(20.dp)) },
    )
}

@Composable
private fun CompactMovieRow(
    thumbnailLoader: MountedVideoThumbnailLoader,
    source: ClientStorageSource?,
    movie: MovieDto,
    onClick: () -> Unit,
) {
    CompactBrowserRow(
        title = movie.browseTitle(),
        meta = movie.releaseDate?.take(4) ?: movie.duration?.let { "${it} 分钟" }.orEmpty(),
        onClick = onClick,
        icon = {
            if (movie.isMountedLibraryItem()) {
                MountedVideoThumbnail(
                    thumbnailLoader = thumbnailLoader,
                    source = source,
                    movie = movie,
                    modifier = Modifier.size(width = 52.dp, height = 32.dp),
                    cornerRadius = 8.dp,
                    showPlayIcon = false,
                )
            } else {
                Icon(Icons.Default.InsertDriveFile, contentDescription = null, modifier = Modifier.size(20.dp))
            }
        },
        framedIcon = !movie.isMountedLibraryItem(),
    )
}

@Composable
private fun CompactBrowserRow(
    title: String,
    meta: String,
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    framedIcon: Boolean = true,
) {
    Surface(
        modifier = Modifier.fillMaxWidth().height(46.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (framedIcon) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(Modifier.padding(7.dp), contentAlignment = Alignment.Center) { icon() }
                }
            } else {
                icon()
            }
            Text(
                title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
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

private fun List<FolderNodeDto>.childrenForBrowse(folder: String): List<FolderNodeDto> {
    if (folder.isBlank()) return this
    val target = normalizedFolderPath(folder)
    return firstNotNullOfOrNull { node -> node.childrenForBrowsePath(target) }.orEmpty()
}

private fun FolderNodeDto.childrenForBrowsePath(target: String): List<FolderNodeDto>? {
    if (normalizedFolderPath(path) == target) return children
    return children.firstNotNullOfOrNull { it.childrenForBrowsePath(target) }
}

private fun normalizedFolderPath(path: String): String = path.trim('/', '\\')

private fun List<FolderNodeDto>.filterFoldersByQuery(query: String): List<FolderNodeDto> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return this
    return filter {
        it.name.lowercase().contains(q) ||
            it.path.lowercase().contains(q)
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

private fun FolderNodeDto.browseTitle(): String = name

private fun FolderNodeDto.detailRoute(): String =
    "detail/${path.toMovieRouteId()}?providerItemId=${Uri.encode(path)}"

private fun MovieDto.routeId(): Int = id

private fun MovieDto.detailRoute(): String =
    "detail/${routeId()}" + path.takeIf { it.isNotBlank() }?.let { "?providerItemId=${Uri.encode(it)}" }.orEmpty()

private fun MovieDto.openRoute(): String =
    mediaRoot?.smbLibrarySourceId()?.let { sourceId -> "smbPlayer/$sourceId?path=${Uri.encode(path)}" }
        ?: mediaRoot?.webDavLibrarySourceId()?.let { sourceId -> "webdavPlayer/$sourceId?path=${Uri.encode(path)}" }
        ?: detailRoute()

private fun MovieDto.isMountedLibraryItem(): Boolean =
    mediaRoot?.mountedLibrarySourceId() != null

private data class MountedVideoThumbnailSource(
    val uri: String,
    val headers: Map<String, String>,
    val onClose: (() -> Unit)? = null,
)

private val mountedVideoFrameDispatcher = Dispatchers.IO.limitedParallelism(4)
private const val MountedVideoFrameWidth = 240
private const val MountedVideoFrameHeight = 360
private const val MountedVideoFrameCacheTtlMillis = 2 * 60 * 1000L
private const val MountedVideoFrameCacheMaxBytes = 32 * 1024 * 1024L

private class MountedVideoFrameMemoryCache(
    private val ttlMillis: Long = MountedVideoFrameCacheTtlMillis,
    private val maxBytes: Long = MountedVideoFrameCacheMaxBytes,
) {
    private data class Entry(
        val bitmap: Bitmap,
        val createdAtMillis: Long,
        val bytes: Long,
    )

    private val entries = LinkedHashMap<String, Entry>(0, 0.75f, true)
    private var totalBytes = 0L

    @Synchronized
    fun getCached(key: String): Bitmap? {
        val entry = entries[key] ?: return null
        if (SystemClock.elapsedRealtime() - entry.createdAtMillis > ttlMillis) {
            removeEntry(key)
            return null
        }
        return entry.bitmap
    }

    @Synchronized
    fun putCached(key: String, bitmap: Bitmap) {
        removeEntry(key)
        val bytes = bitmap.allocationByteCount.toLong()
        entries[key] = Entry(
            bitmap = bitmap,
            createdAtMillis = SystemClock.elapsedRealtime(),
            bytes = bytes,
        )
        totalBytes += bytes
        while (totalBytes > maxBytes && entries.isNotEmpty()) {
            removeOldestCacheEntry()
        }
    }

    @Synchronized
    fun removeOldestCacheEntry() {
        val oldestKey = entries.entries.firstOrNull()?.key ?: return
        removeEntry(oldestKey)
    }

    private fun removeEntry(key: String) {
        entries.remove(key)?.let { removed -> totalBytes -= removed.bytes }
    }
}

private suspend fun extractMountedVideoFrame(source: MountedVideoThumbnailSource): Bitmap? =
    withContext(mountedVideoFrameDispatcher) {
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(source.uri, source.headers)
                retriever.scaledFrameAtStart()
            }
        }.getOrNull()
    }

private fun MediaMetadataRetriever.scaledFrameAtStart(): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        getScaledFrameAtTime(
            0L,
            MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
            MountedVideoFrameWidth,
            MountedVideoFrameHeight,
        )
    } else {
        getFrameAtTime(0L, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
            ?.let { Bitmap.createScaledBitmap(it, MountedVideoFrameWidth, MountedVideoFrameHeight, true) }
    }

private fun mountedVideoFrameCacheKey(source: ClientStorageSource?, movie: MovieDto): String? {
    val resolvedSource = source ?: return null
    if (!movie.isMountedLibraryItem()) return null
    val version = movie.fileSize ?: movie.size ?: 0L
    return listOf(
        resolvedSource.type.name,
        resolvedSource.id,
        movie.path,
        version.toString(),
    ).joinToString("|")
}

private fun mountedVideoThumbnailSource(
    container: AppContainer,
    source: ClientStorageSource?,
    movie: MovieDto,
): MountedVideoThumbnailSource? {
    if (!movie.isMountedLibraryItem()) return null
    val resolvedSource = source ?: return null
    return when (resolvedSource.type) {
        ClientStorageType.SMB -> {
            val playbackSource = container.smbRangeProxy.playbackSource(source = resolvedSource, path = movie.path)
            MountedVideoThumbnailSource(
                uri = playbackSource.uri,
                headers = playbackSource.headers,
                onClose = playbackSource.onClose,
            )
        }
        ClientStorageType.WebDAV -> {
            val playbackSource = PlaybackSource.webDav(source = resolvedSource, path = movie.path)
            MountedVideoThumbnailSource(
                uri = playbackSource.uri,
                headers = playbackSource.headers,
            )
        }
    }
}

private fun mediaRootPath(sourceId: String): String = "smb/$sourceId"

private fun String.mountedLibrarySourceId(): String? =
    smbLibrarySourceId() ?: webDavLibrarySourceId()

private fun String.toMovieRouteId(): Int =
    takeLast(8).toUIntOrNull(16)?.toInt() ?: hashCode()

private fun FolderNodeDto.folderMeta(): String =
    if (mediaRoot?.mountedLibrarySourceId() != null) {
        "文件夹"
    } else {
        listOf(
            "${movieCount} 项",
            createdMax?.take(10),
        ).filter { !it.isNullOrBlank() }.joinToString(" · ")
    }

private fun MovieDto.browseTitle(): String = displayTitle ?: title ?: code

private fun MovieDto.iconMovieMeta(): String =
    if (isMountedLibraryItem()) {
        listOf(
            fileExtensionLabel().ifBlank { "视频" },
            readableSize(),
        ).filter { !it.isNullOrBlank() }.joinToString(" · ")
    } else {
        releaseDate?.take(4).orEmpty()
    }

private fun MovieDto.fileExtensionLabel(): String {
    val name = path.ifBlank { code }
        .substringAfterLast('/')
        .substringAfterLast('\\')
    return name.substringAfterLast('.', "")
        .takeIf { it.isNotBlank() && it.length <= 8 }
        ?.uppercase()
        .orEmpty()
}

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

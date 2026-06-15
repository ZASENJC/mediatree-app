package com.zasenjc.mediatree.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.Image as ComposeImage
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.ClientStorageSource
import com.zasenjc.mediatree.data.ClientStorageType
import com.zasenjc.mediatree.data.FolderNodeDto
import com.zasenjc.mediatree.data.MountedVideoThumbnailRequest
import com.zasenjc.mediatree.data.MountedVideoThumbnailSpec
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.WebDavClient
import com.zasenjc.mediatree.data.mountedThumbnailKey
import com.zasenjc.mediatree.data.webDavLibraryPath
import com.zasenjc.mediatree.data.webDavLibrarySourceId
import com.zasenjc.mediatree.data.smbLibrarySourceId
import com.zasenjc.mediatree.data.isViewableImageFileName
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.ui.components.BackendSetupRequiredMessage
import com.zasenjc.mediatree.ui.components.BackendSetupRequiredState
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.MediaAsyncImage
import com.zasenjc.mediatree.ui.components.MoviePosterCard
import com.zasenjc.mediatree.ui.components.SyncChromeWithListScroll
import com.zasenjc.mediatree.ui.components.DesignFilterChip
import com.zasenjc.mediatree.ui.components.DesignTopAppBar
import com.zasenjc.mediatree.ui.components.shapeAwareClickable
import com.zasenjc.mediatree.ui.components.topChromeEnterTransition
import com.zasenjc.mediatree.ui.components.topChromeExitTransition
import com.zasenjc.mediatree.ui.motion.md3DefaultContentTransform
import com.zasenjc.mediatree.ui.shouldLoadRemoteContent
import com.zasenjc.mediatree.util.UrlUtils
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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

private typealias MountedVideoThumbnailLoader = suspend (ClientStorageSource?, MovieDto, MountedVideoThumbnailSpec) -> Bitmap?
private typealias MountedImageThumbnailSourceLoader = (ClientStorageSource?, MovieDto) -> MountedImageThumbnailSource?

private data class MountedImageThumbnailSource(
    val uri: String,
    val headers: Map<String, String>,
    val onClose: (() -> Unit)? = null,
)

private val MountedPosterVideoThumbnailSpec = MountedVideoThumbnailSpec(
    width = MountedPosterVideoFrameWidth,
    height = MountedPosterVideoFrameHeight,
)
private val MountedLandscapeVideoThumbnailSpec = MountedVideoThumbnailSpec(
    width = MountedLandscapeVideoFrameWidth,
    height = MountedLandscapeVideoFrameHeight,
)
private const val MountedThumbnailVisibleDebounceMillis = 120L

data class BrowseScrollPosition(
    val firstVisibleItemIndex: Int = 0,
    val firstVisibleItemScrollOffset: Int = 0,
)

private class MountedThumbnailViewportScheduler {
    private val visibleKeys = MutableStateFlow<List<String>>(emptyList())

    fun updateVisibleKeys(keys: List<String>) {
        visibleKeys.value = keys
    }

    suspend fun awaitVisible(key: String) {
        visibleKeys.first { visibleKeys -> visibleKeys.indexOf(key) >= 0 }
    }
}

@Composable
private fun rememberMountedThumbnailViewportScheduler(listState: LazyListState): MountedThumbnailViewportScheduler {
    val scheduler = remember { MountedThumbnailViewportScheduler() }
    LaunchedEffect(listState, scheduler) {
        snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull { it.key as? String } }
            .distinctUntilChanged()
            .collect { keys -> scheduler.updateVisibleKeys(keys) }
    }
    return scheduler
}

private data class BrowseContentSnapshot(
    val folders: List<FolderNodeDto> = emptyList(),
    val movies: List<MovieDto> = emptyList(),
    val total: Int = 0,
    val currentFolder: String = "",
    val sortMode: String = "name",
    val mountedSource: ClientStorageSource? = null,
) {
    companion object {
        fun from(state: BrowseViewModel.UiState): BrowseContentSnapshot =
            BrowseContentSnapshot(
                folders = state.folders,
                movies = state.movies,
                total = state.total,
                currentFolder = state.currentFolder,
                sortMode = state.sortMode,
                mountedSource = state.mountedSource,
            )
    }
}

private class BrowseViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val folders: List<FolderNodeDto> = emptyList(),
        val movies: List<MovieDto> = emptyList(),
        val total: Int = 0,
        val page: Int = 0,
        val currentFolder: String = "",
        val sortMode: String = "name",
        val searchQuery: String = "",
        val mountedSource: ClientStorageSource? = null,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(
        providerType: ProviderType,
        folder: String,
        mediaRoot: String,
        sort: String = _state.value.sortMode,
        recursiveVideosOnly: Boolean = false,
        searchQuery: String = "",
    ) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, sortMode = sort, searchQuery = searchQuery.trim()) }
            try {
                val smbSourceId = mediaRoot.smbLibrarySourceId()
                if (smbSourceId != null) {
                    loadSmb(smbSourceId, folder, sort, recursiveVideosOnly, searchQuery)
                    return@launch
                }
                val webDavSourceId = mediaRoot.webDavLibrarySourceId()
                if (webDavSourceId != null) {
                    loadWebDav(webDavSourceId, folder, sort, recursiveVideosOnly, searchQuery)
                    return@launch
                }
                val provider = container.mediaProviderFor(providerType)
                val folders = if (providerType == ProviderType.MediaTree) {
                    provider.folders(mediaRoot).tree.childrenForBrowse(folder)
                } else {
                    provider.folders(folder.ifBlank { mediaRoot }).tree
                }.filterFoldersForRemoteSearch(searchQuery).sortedFoldersForBrowse(sort)
                val response = provider.movies(
                    folder = folder,
                    code = searchQuery.trim(),
                    sort = sort.toProviderBrowseMovieSort(providerType),
                    limit = 48,
                    offset = 0,
                    mediaRoot = mediaRoot,
                )
                _state.update {
                    it.copy(
                        loading = false,
                        folders = folders,
                        movies = response?.movies.orEmpty().sortedMoviesForBrowse(sort),
                        total = response?.total ?: 0,
                        currentFolder = folder,
                        page = 0,
                        sortMode = sort,
                        searchQuery = searchQuery.trim(),
                        mountedSource = null,
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    private suspend fun loadSmb(
        sourceId: String,
        folder: String,
        sort: String,
        recursiveVideosOnly: Boolean,
        searchQuery: String,
    ) {
        val source = container.clientStorageRepository.load()
            .firstOrNull { it.id == sourceId && it.type == com.zasenjc.mediatree.data.ClientStorageType.SMB && it.enabled }
            ?: throw IllegalArgumentException("SMB 存储源不可用")
        val searching = searchQuery.trim().isNotBlank()
        val entries = when {
            recursiveVideosOnly -> collectSmbVideoEntries(source, folder)
            searching -> collectSmbMediaEntries(source, folder)
            else -> container.smbClient.list(source, folder)
        }
        val folders = if (recursiveVideosOnly || searching) emptyList() else entries.filter { it.isDirectory }
            .map { entry ->
                FolderNodeDto(
                    name = entry.name,
                    path = entry.path,
                    isLeaf = false,
                    displayTitle = entry.name,
                    mediaRoot = mediaRootPath(sourceId),
                    createdMax = entry.modified.takeIf { it > 0L }?.toString(),
                )
            }
            .sortedFoldersForBrowse(sort)
        val movies = entries.filter { it.isPlayableVideo || it.isViewableImage }
            .map { entry -> entry.toMountedMovieDto(source) }
            .filterMoviesByQuery(searchQuery)
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
                searchQuery = searchQuery.trim(),
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

    private suspend fun collectSmbMediaEntries(source: ClientStorageSource, folder: String): List<com.zasenjc.mediatree.data.SmbEntry> {
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        val media = mutableListOf<com.zasenjc.mediatree.data.SmbEntry>()
        pending.add(folder)
        while (pending.isNotEmpty()) {
            val currentFolder = pending.removeFirst()
            if (!visited.add(currentFolder)) continue
            container.smbClient.list(source, currentFolder).forEach { entry ->
                when {
                    entry.isDirectory -> pending.add(entry.path)
                    entry.isPlayableVideo || entry.isViewableImage -> media.add(entry)
                }
            }
        }
        return media
    }

    private suspend fun loadWebDav(
        sourceId: String,
        folder: String,
        sort: String,
        recursiveVideosOnly: Boolean,
        searchQuery: String,
    ) {
        val source = container.clientStorageRepository.load()
            .firstOrNull { it.id == sourceId && it.type == com.zasenjc.mediatree.data.ClientStorageType.WebDAV && it.enabled }
            ?: throw IllegalArgumentException("WebDAV 存储源不可用")
        val searching = searchQuery.trim().isNotBlank()
        val entries = when {
            recursiveVideosOnly -> collectWebDavVideoEntries(source, folder)
            searching -> collectWebDavMediaEntries(source, folder)
            else -> container.webDavClient.list(source, folder)
        }
        val folders = if (recursiveVideosOnly || searching) emptyList() else entries.filter { it.isDirectory }
            .map { entry ->
                FolderNodeDto(
                    name = entry.name,
                    path = entry.path,
                    isLeaf = false,
                    displayTitle = entry.name,
                    mediaRoot = webDavLibraryPath(sourceId),
                    createdMax = entry.modified.ifBlank { null },
                )
            }
            .sortedFoldersForBrowse(sort)
        val movies = entries.filter { it.isPlayableVideo || it.isViewableImage }
            .map { entry -> entry.toMountedMovieDto(source) }
            .filterMoviesByQuery(searchQuery)
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
                searchQuery = searchQuery.trim(),
                mountedSource = source,
            )
        }
    }

    fun loadMore(providerType: ProviderType, folder: String, mediaRoot: String) {
        if (mediaRoot.smbLibrarySourceId() != null || mediaRoot.webDavLibrarySourceId() != null) return
        val s = _state.value
        val next = s.page + 1
        viewModelScope.launch {
            try {
                val response = container.mediaProviderFor(providerType).movies(
                    folder = folder,
                    code = s.searchQuery,
                    sort = s.sortMode.toProviderBrowseMovieSort(providerType),
                    limit = 48,
                    offset = next * 48,
                    mediaRoot = mediaRoot,
                )
                _state.update {
                    val mergedMovies = (it.movies + response.movies).sortedMoviesForBrowse(s.sortMode)
                    it.copy(page = next, movies = mergedMovies, total = response.total)
                }
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

    private suspend fun collectWebDavMediaEntries(source: ClientStorageSource, folder: String): List<com.zasenjc.mediatree.data.WebDavEntry> {
        val pending = ArrayDeque<String>()
        val visited = mutableSetOf<String>()
        val media = mutableListOf<com.zasenjc.mediatree.data.WebDavEntry>()
        pending.add(folder)
        while (pending.isNotEmpty()) {
            val currentFolder = pending.removeFirst()
            if (!visited.add(currentFolder)) continue
            container.webDavClient.list(source, currentFolder).forEach { entry ->
                when {
                    entry.isDirectory -> pending.add(entry.path)
                    entry.isPlayableVideo || entry.isViewableImage -> media.add(entry)
                }
            }
        }
        return media
    }

    suspend fun loadMountedVideoFrame(source: ClientStorageSource?, movie: MovieDto, spec: MountedVideoThumbnailSpec): Bitmap? {
        return container.mountedVideoThumbnailCache.getOrCreate(
            MountedVideoThumbnailRequest(source = source, movie = movie, spec = spec),
        )
    }

    fun mountedImageThumbnailSource(source: ClientStorageSource?, movie: MovieDto): MountedImageThumbnailSource? {
        val resolvedSource = source ?: return null
        if (!movie.isMountedImageItem()) return null
        return when (resolvedSource.type) {
            ClientStorageType.SMB -> {
                val playbackSource = container.smbRangeProxy.playbackSource(source = resolvedSource, path = movie.path)
                MountedImageThumbnailSource(
                    uri = playbackSource.uri,
                    headers = playbackSource.headers,
                    onClose = playbackSource.onClose,
                )
            }
            ClientStorageType.WebDAV -> MountedImageThumbnailSource(
                uri = WebDavClient.buildResourceUrl(resolvedSource, movie.path),
                headers = WebDavClient.authorizationHeaders(resolvedSource),
            )
        }
    }
}

@OptIn(ExperimentalAnimationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    container: AppContainer,
    session: Session,
    onNavigate: (String) -> Unit,
    onError: (Throwable) -> Unit,
    active: Boolean = true,
    initialFolder: String,
    recursiveVideosOnly: Boolean = false,
    sourceFileNameMode: Boolean = false,
    viewMode: String,
    onViewModeChange: (String) -> Unit,
    onExitToHome: () -> Unit = {},
    browseScrollPositions: MutableMap<String, BrowseScrollPosition>,
    chromeVisible: Boolean = true,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val vm: BrowseViewModel = viewModel(factory = viewModelFactory { BrowseViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val mountedVideoThumbnailLoader: MountedVideoThumbnailLoader = remember(vm) { vm::loadMountedVideoFrame }
    val mountedImageThumbnailSourceLoader: MountedImageThumbnailSourceLoader = remember(vm) { vm::mountedImageThumbnailSource }
    var contentSnapshot by remember { mutableStateOf(BrowseContentSnapshot()) }
    var hasContentSnapshot by remember { mutableStateOf(false) }

    LaunchedEffect(active) {
        if (!active) return@LaunchedEffect
        onChromeVisibleChange(true)
    }

    fun reloadBrowse(
        sort: String = state.sortMode,
        request: String = query,
    ) {
        val smbSourceId = session.activeLibrary.smbLibrarySourceId()
        val webDavSourceId = session.activeLibrary.webDavLibrarySourceId()
        if (shouldLoadRemoteContent(session) || smbSourceId != null || webDavSourceId != null) {
            vm.load(
                providerType = session.activeProviderType,
                folder = initialFolder,
                mediaRoot = session.activeLibrary,
                sort = sort,
                recursiveVideosOnly = recursiveVideosOnly,
                searchQuery = request,
            )
        }
    }

    LaunchedEffect(active, session.serverUrl, session.activeProviderType, session.activeLibrary, initialFolder, recursiveVideosOnly, sourceFileNameMode) {
        if (!active) return@LaunchedEffect
        searchJob?.cancel()
        query = ""
        reloadBrowse(request = "")
    }

    LaunchedEffect(active, state.error) {
        if (!active) return@LaunchedEffect
        state.error?.let(onError)
    }

    LaunchedEffect(
        state.loading,
        state.error,
        state.currentFolder,
        state.folders,
        state.movies,
        state.total,
        state.sortMode,
        state.mountedSource,
    ) {
        if (!state.loading && state.error == null) {
            contentSnapshot = BrowseContentSnapshot.from(state)
            hasContentSnapshot = true
        }
    }

    val title = state.currentFolder.substringAfterLast("/").ifBlank { "浏览" }
    fun openFolderNode(folder: FolderNodeDto) {
        if (folder.isLeaf && session.activeProviderType != ProviderType.MediaTree) {
            onNavigate(folder.detailRoute())
        } else if (sourceFileNameMode) {
            onNavigate("browse?folder=${Uri.encode(folder.path)}&sourceFileName=true")
        } else {
            onNavigate("browse?folder=${Uri.encode(folder.path)}")
        }
    }
    val provider = remember(session.activeProviderType, container) {
        container.mediaProviderFor(session.activeProviderType)
    }
    val filteredFolders = state.folders
    val filteredMovies = state.movies

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    reloadBrowse()
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
                    session.activeLibrary.webDavLibrarySourceId() == null -> BackendSetupRequiredState(
                        icon = Icons.Filled.Folder,
                        message = BackendSetupRequiredMessage,
                    )
                state.loading && !hasContentSnapshot -> LoadingPane(Modifier.fillMaxSize())
                else -> {
                    AnimatedContent(
                        targetState = contentSnapshot,
                        transitionSpec = { md3DefaultContentTransform() },
                        label = "browseFolderContent",
                    ) { snapshot ->
                        val filteredFolders = snapshot.folders
                        val filteredMovies = snapshot.movies
                        val posterFolderRows = remember(filteredFolders) { filteredFolders.chunked(3) }
                        val iconFolderRows = posterFolderRows
                        val posterMovieRows = remember(filteredMovies) { filteredMovies.chunked(2) }
                        val iconMovieRows = remember(filteredMovies) { filteredMovies.chunked(3) }
                        val resolvedViewMode = if (sourceFileNameMode) "poster" else viewMode
                        val scrollKey = snapshot.scrollMemoryKey(
                            providerType = session.activeProviderType,
                            activeProfileId = session.activeProfileId,
                            activeLibrary = session.activeLibrary,
                            viewMode = resolvedViewMode,
                            query = query,
                            recursiveVideosOnly = recursiveVideosOnly,
                            sourceFileNameMode = sourceFileNameMode,
                        )
                        val rememberedScroll = browseScrollPositions[scrollKey]
                        val snapshotListState = rememberLazyListState(
                            initialFirstVisibleItemIndex = rememberedScroll?.firstVisibleItemIndex ?: 0,
                            initialFirstVisibleItemScrollOffset = rememberedScroll?.firstVisibleItemScrollOffset ?: 0,
                        )
                        DisposableEffect(scrollKey, snapshotListState) {
                            onDispose {
                                browseScrollPositions[scrollKey] = snapshotListState.toBrowseScrollPosition()
                            }
                        }
                        LaunchedEffect(scrollKey, snapshotListState) {
                            snapshotFlow { snapshotListState.toBrowseScrollPosition() }
                                .distinctUntilChanged()
                                .collect { position ->
                                    browseScrollPositions[scrollKey] = position
                                }
                        }
                        val thumbnailViewportScheduler = rememberMountedThumbnailViewportScheduler(snapshotListState)
                        if (active) {
                            SyncChromeWithListScroll(snapshotListState, onChromeVisibleChange)
                        }
                        LazyColumn(
                            state = snapshotListState,
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(start = 20.dp, top = 86.dp, end = 20.dp, bottom = 116.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            item {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    OutlinedTextField(
                                        value = query,
                                        onValueChange = { value ->
                                            query = value
                                            searchJob?.cancel()
                                            val request = value.trim()
                                            searchJob = scope.launch {
                                                delay(260)
                                                reloadBrowse(request = request)
                                            }
                                        },
                                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                        placeholder = { Text("搜索项目") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (!sourceFileNameMode) {
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
                                    }
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        items(browseSortOptions) { (key, label) ->
                                            DesignFilterChip(
                                                selected = snapshot.sortMode == key,
                                                onClick = {
                                                    reloadBrowse(sort = key)
                                                },
                                                label = label,
                                            )
                                        }
                                    }
                                }
                            }
                            if (filteredFolders.isNotEmpty()) {
                                when (resolvedViewMode) {
                                    "poster" -> {
                                        items(
                                            posterFolderRows,
                                            key = { row -> row.joinToString("|") { it.path } },
                                            contentType = { "folder-poster-row" },
                                        ) { row ->
                                            PosterFolderRow(
                                                row = row,
                                                serverUrl = session.serverUrl,
                                                sourceFileNameMode = sourceFileNameMode,
                                                onOpen = ::openFolderNode,
                                            )
                                        }
                                    }
                                    "icon" -> {
                                        items(
                                            iconFolderRows,
                                            key = { row -> row.joinToString("|") { it.path } },
                                            contentType = { "folder-icon-row" },
                                        ) { row ->
                                            IconFolderRow(
                                                row = row,
                                                onOpen = ::openFolderNode,
                                            )
                                        }
                                    }
                                    "compact" -> {
                                        items(filteredFolders, key = { it.path }, contentType = { "folder-compact" }) { folder ->
                                            CompactFolderRow(folder = folder, onClick = { openFolderNode(folder) })
                                        }
                                    }
                                    else -> {
                                        items(filteredFolders, key = { it.path }, contentType = { "folder-compact" }) { folder ->
                                            CompactFolderRow(folder = folder, onClick = { openFolderNode(folder) })
                                        }
                                    }
                                }
                            }
                            if (filteredMovies.isNotEmpty() || snapshot.currentFolder.isNotBlank()) {
                                when (resolvedViewMode) {
                                    "poster" -> {
                                        items(
                                            posterMovieRows,
                                            key = { row -> row.mountedThumbnailRowKey(snapshot.mountedSource, MountedPosterVideoThumbnailSpec) },
                                            contentType = { "movie-poster-row" },
                                        ) { row ->
                                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                                                row.forEach { movie ->
                                                    if (movie.isMountedLibraryItem()) {
                                                        MountedVideoPosterCard(
                                                            imageThumbnailSourceLoader = mountedImageThumbnailSourceLoader,
                                                            thumbnailLoader = mountedVideoThumbnailLoader,
                                                            thumbnailViewportScheduler = thumbnailViewportScheduler,
                                                            viewportKey = row.mountedThumbnailRowKey(snapshot.mountedSource, MountedPosterVideoThumbnailSpec),
                                                            source = snapshot.mountedSource,
                                                            movie = movie,
                                                            sourceFileNameMode = sourceFileNameMode,
                                                            onClick = { onNavigate(movie.openRoute()) },
                                                            modifier = Modifier.weight(1f),
                                                        )
                                                    } else {
                                                        MoviePosterCard(
                                                            movie = movie,
                                                            imageUrl = provider.coverUrl(session.serverUrl, movie.id),
                                                            titleOverride = movie.sourceFileNameTitle().takeIf { sourceFileNameMode },
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
                                        items(
                                            iconMovieRows,
                                            key = { row -> row.mountedThumbnailRowKey(snapshot.mountedSource, MountedLandscapeVideoThumbnailSpec) },
                                            contentType = { "movie-icon-row" },
                                        ) { row ->
                                            IconMovieRow(
                                                imageThumbnailSourceLoader = mountedImageThumbnailSourceLoader,
                                                thumbnailLoader = mountedVideoThumbnailLoader,
                                                thumbnailViewportScheduler = thumbnailViewportScheduler,
                                                source = snapshot.mountedSource,
                                                row = row,
                                                onOpen = { movie -> onNavigate(movie.openRoute()) },
                                            )
                                        }
                                    }
                                    "compact" -> {
                                        items(
                                            filteredMovies,
                                            key = { movie -> movie.mountedThumbnailItemKey(snapshot.mountedSource, MountedLandscapeVideoThumbnailSpec) },
                                            contentType = { "movie-compact" },
                                        ) { movie ->
                                            CompactMovieRow(
                                                imageThumbnailSourceLoader = mountedImageThumbnailSourceLoader,
                                                thumbnailLoader = mountedVideoThumbnailLoader,
                                                thumbnailViewportScheduler = thumbnailViewportScheduler,
                                                source = snapshot.mountedSource,
                                                movie = movie,
                                                onClick = { onNavigate(movie.openRoute()) },
                                            )
                                        }
                                    }
                                    else -> {
                                        items(
                                            filteredMovies,
                                            key = { movie -> movie.mountedThumbnailItemKey(snapshot.mountedSource, MountedLandscapeVideoThumbnailSpec) },
                                            contentType = { "movie-compact" },
                                        ) { movie ->
                                            CompactMovieRow(
                                                imageThumbnailSourceLoader = mountedImageThumbnailSourceLoader,
                                                thumbnailLoader = mountedVideoThumbnailLoader,
                                                thumbnailViewportScheduler = thumbnailViewportScheduler,
                                                source = snapshot.mountedSource,
                                                movie = movie,
                                                onClick = { onNavigate(movie.openRoute()) },
                                            )
                                        }
                                    }
                                }
                                if (snapshot.movies.size < snapshot.total) {
                                    item {
                                        Button(
                                            onClick = {
                                                if (shouldLoadRemoteContent(session)) {
                                                    vm.loadMore(session.activeProviderType, snapshot.currentFolder, session.activeLibrary)
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
                                item { EmptyBrowseState(if (snapshot.currentFolder.isBlank()) "没有匹配的项目" else "此目录没有可显示项目") }
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
                DesignTopAppBar(
                    title = title,
                    navigationIcon = {
                        if (initialFolder.isNotBlank()) {
                            IconButton(onClick = {
                                if (sourceFileNameMode) {
                                    onExitToHome()
                                } else {
                                    val parent = initialFolder.trimEnd('/').substringBeforeLast("/", missingDelimiterValue = "")
                                    if (parent.isNotBlank()) {
                                        onNavigate("browse?folder=${Uri.encode(parent)}")
                                    } else {
                                        onNavigate("browse")
                                    }
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
    val shape = RoundedCornerShape(16.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().shapeAwareClickable(shape = shape, onClick = onClick),
        shape = shape,
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
    sourceFileNameMode: Boolean,
    onOpen: (FolderNodeDto) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { folder ->
            FolderPosterCard(
                folder = folder,
                imageUrl = UrlUtils.resolveApiUrl(serverUrl, folder.randomCover ?: folder.cover),
                titleOverride = folder.sourceFileNameTitle().takeIf { sourceFileNameMode },
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
    titleOverride: String? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = titleOverride ?: folder.browseTitle()
    val shape = RoundedCornerShape(14.dp)
    ElevatedCard(
        modifier = modifier.shapeAwareClickable(shape = shape, onClick = onClick),
        shape = shape,
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
                if (folder.folderWatched == true) {
                    WatchFlag(Modifier.align(Alignment.TopEnd).padding(7.dp))
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
private fun WatchFlag(modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        tonalElevation = 3.dp,
    ) {
        Icon(
            Icons.Default.Flag,
            contentDescription = "已观看",
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp).size(16.dp),
        )
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
    imageThumbnailSourceLoader: MountedImageThumbnailSourceLoader,
    thumbnailLoader: MountedVideoThumbnailLoader,
    thumbnailViewportScheduler: MountedThumbnailViewportScheduler,
    source: ClientStorageSource?,
    row: List<MovieDto>,
    onOpen: (MovieDto) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        row.forEach { movie ->
            if (movie.isMountedLibraryItem()) {
                MountedVideoIconTile(
                    imageThumbnailSourceLoader = imageThumbnailSourceLoader,
                    thumbnailLoader = thumbnailLoader,
                    thumbnailViewportScheduler = thumbnailViewportScheduler,
                    viewportKey = row.mountedThumbnailRowKey(source, MountedLandscapeVideoThumbnailSpec),
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
                        Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(34.dp))
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
    imageThumbnailSourceLoader: MountedImageThumbnailSourceLoader,
    thumbnailLoader: MountedVideoThumbnailLoader,
    thumbnailViewportScheduler: MountedThumbnailViewportScheduler,
    viewportKey: String,
    source: ClientStorageSource?,
    movie: MovieDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = modifier.shapeAwareClickable(shape = shape, onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        MountedMediaThumbnail(
            imageThumbnailSourceLoader = imageThumbnailSourceLoader,
            thumbnailLoader = thumbnailLoader,
            thumbnailViewportScheduler = thumbnailViewportScheduler,
            viewportKey = viewportKey,
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
    val shape = RoundedCornerShape(14.dp)
    ElevatedCard(
        modifier = modifier.defaultMinSize(minHeight = 112.dp).shapeAwareClickable(shape = shape, onClick = onClick),
        shape = shape,
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
    imageThumbnailSourceLoader: MountedImageThumbnailSourceLoader,
    thumbnailLoader: MountedVideoThumbnailLoader,
    thumbnailViewportScheduler: MountedThumbnailViewportScheduler,
    viewportKey: String,
    source: ClientStorageSource?,
    movie: MovieDto,
    sourceFileNameMode: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(modifier = modifier.shapeAwareClickable(shape = shape, onClick = onClick), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        MountedMediaThumbnail(
            imageThumbnailSourceLoader = imageThumbnailSourceLoader,
            thumbnailLoader = thumbnailLoader,
            thumbnailViewportScheduler = thumbnailViewportScheduler,
            viewportKey = viewportKey,
            source = source,
            movie = movie,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f),
            cornerRadius = 16.dp,
            spec = MountedPosterVideoThumbnailSpec,
            showPlayIcon = !movie.isMountedImageItem(),
        )
        Text(
            text = if (sourceFileNameMode) movie.sourceFileNameTitle() else movie.browseTitle(),
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
private fun MountedMediaThumbnail(
    imageThumbnailSourceLoader: MountedImageThumbnailSourceLoader,
    thumbnailLoader: MountedVideoThumbnailLoader,
    thumbnailViewportScheduler: MountedThumbnailViewportScheduler,
    viewportKey: String? = null,
    source: ClientStorageSource?,
    movie: MovieDto,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    spec: MountedVideoThumbnailSpec = MountedLandscapeVideoThumbnailSpec,
    showPlayIcon: Boolean = true,
) {
    if (movie.isMountedImageItem()) {
        MountedImageThumbnail(
            thumbnailSourceLoader = imageThumbnailSourceLoader,
            thumbnailViewportScheduler = thumbnailViewportScheduler,
            viewportKey = viewportKey,
            source = source,
            movie = movie,
            modifier = modifier,
            cornerRadius = cornerRadius,
            spec = spec,
        )
    } else {
        MountedVideoThumbnail(
            thumbnailLoader = thumbnailLoader,
            thumbnailViewportScheduler = thumbnailViewportScheduler,
            viewportKey = viewportKey,
            source = source,
            movie = movie,
            modifier = modifier,
            cornerRadius = cornerRadius,
            spec = spec,
            showPlayIcon = showPlayIcon,
        )
    }
}

@Composable
private fun MountedImageThumbnail(
    thumbnailSourceLoader: MountedImageThumbnailSourceLoader,
    thumbnailViewportScheduler: MountedThumbnailViewportScheduler,
    viewportKey: String? = null,
    source: ClientStorageSource?,
    movie: MovieDto,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    spec: MountedVideoThumbnailSpec = MountedLandscapeVideoThumbnailSpec,
) {
    var shouldLoad by remember(source?.id, source?.type, movie.path, movie.fileSize, movie.size, spec) {
        mutableStateOf(false)
    }
    val thumbnailKey = remember(source?.id, source?.type, movie.path, movie.fileSize, movie.size, spec) {
        mountedThumbnailKey(source, movie, spec)?.let { key -> "image|$key" } ?: movie.id.toString()
    }
    LaunchedEffect(thumbnailViewportScheduler, viewportKey, thumbnailKey) {
        shouldLoad = false
        val keyToWait = viewportKey ?: thumbnailKey
        thumbnailViewportScheduler.awaitVisible(keyToWait)
        delay(MountedThumbnailVisibleDebounceMillis)
        thumbnailViewportScheduler.awaitVisible(keyToWait)
        shouldLoad = true
    }
    val imageSource = remember(shouldLoad, source, movie.path, movie.fileSize, movie.size) {
        if (shouldLoad) thumbnailSourceLoader(source, movie) else null
    }
    DisposableEffect(imageSource) {
        onDispose { imageSource?.onClose?.invoke() }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Image,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
            modifier = Modifier.fillMaxSize(0.34f),
        )
        imageSource?.let { sourceInfo ->
            val context = LocalContext.current
            val imageRequest = remember(context, sourceInfo.uri, sourceInfo.headers, thumbnailKey, spec) {
                ImageRequest.Builder(context)
                    .data(sourceInfo.uri)
                    .apply {
                        sourceInfo.headers.forEach { (name, value) -> addHeader(name, value) }
                    }
                    .size(spec.width, spec.height)
                    .crossfade(false)
                    .memoryCacheKey(thumbnailKey)
                    .diskCacheKey(thumbnailKey)
                    .build()
            }
            AsyncImage(
                model = imageRequest,
                contentDescription = movie.browseTitle(),
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun MountedVideoThumbnail(
    thumbnailLoader: MountedVideoThumbnailLoader,
    thumbnailViewportScheduler: MountedThumbnailViewportScheduler,
    viewportKey: String? = null,
    source: ClientStorageSource?,
    movie: MovieDto,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 12.dp,
    spec: MountedVideoThumbnailSpec = MountedLandscapeVideoThumbnailSpec,
    showPlayIcon: Boolean = true,
) {
    var bitmap by remember(source?.id, source?.type, movie.path, movie.fileSize, movie.size, spec) {
        mutableStateOf<Bitmap?>(null)
    }
    val thumbnailKey = remember(source?.id, source?.type, movie.path, movie.fileSize, movie.size, spec) {
        mountedThumbnailKey(source, movie, spec)
    }
    LaunchedEffect(thumbnailLoader, source?.id, movie.mediaRoot, movie.path, movie.fileSize, movie.size, spec) {
        if (movie.isMountedImageItem()) return@LaunchedEffect
        if (bitmap == null) {
            thumbnailKey?.let { key ->
                val keyToWait = viewportKey ?: key
                thumbnailViewportScheduler.awaitVisible(keyToWait)
                delay(MountedThumbnailVisibleDebounceMillis)
                thumbnailViewportScheduler.awaitVisible(keyToWait)
            }
            bitmap = thumbnailLoader(source, movie, spec)
        }
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        bitmap?.let { frame ->
            ComposeImage(
                bitmap = frame.asImageBitmap(),
                contentDescription = movie.browseTitle(),
                contentScale = ContentScale.Crop,
                filterQuality = FilterQuality.Low,
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (bitmap == null) {
            Icon(
                imageVector = if (movie.isMountedImageItem()) Icons.Default.Image else Icons.AutoMirrored.Filled.InsertDriveFile,
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
    val shape = RoundedCornerShape(14.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().shapeAwareClickable(shape = shape, onClick = onClick),
        shape = shape,
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
    imageThumbnailSourceLoader: MountedImageThumbnailSourceLoader,
    thumbnailLoader: MountedVideoThumbnailLoader,
    thumbnailViewportScheduler: MountedThumbnailViewportScheduler,
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
                MountedMediaThumbnail(
                    imageThumbnailSourceLoader = imageThumbnailSourceLoader,
                    thumbnailLoader = thumbnailLoader,
                    thumbnailViewportScheduler = thumbnailViewportScheduler,
                    viewportKey = movie.mountedThumbnailItemKey(source, MountedLandscapeVideoThumbnailSpec),
                    source = source,
                    movie = movie,
                    modifier = Modifier.size(width = 52.dp, height = 32.dp),
                    cornerRadius = 8.dp,
                    showPlayIcon = false,
                )
            } else {
                Icon(Icons.AutoMirrored.Filled.InsertDriveFile, contentDescription = null, modifier = Modifier.size(20.dp))
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
    val shape = RoundedCornerShape(12.dp)
    Surface(
        modifier = Modifier.fillMaxWidth().height(46.dp).shapeAwareClickable(shape = shape, onClick = onClick),
        shape = shape,
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

private fun List<FolderNodeDto>.filterFoldersForRemoteSearch(query: String): List<FolderNodeDto> =
    if (query.trim().isBlank()) this else emptyList()

private fun List<MovieDto>.filterMoviesByQuery(query: String): List<MovieDto> {
    val q = query.trim().lowercase()
    if (q.isBlank()) return this
    return filter {
        it.code.lowercase().contains(q) ||
            (it.title ?: "").lowercase().contains(q) ||
            (it.displayTitle ?: "").lowercase().contains(q) ||
            it.path.lowercase().contains(q)
    }
}

private fun BrowseContentSnapshot.scrollMemoryKey(
    providerType: ProviderType,
    activeProfileId: String,
    activeLibrary: String,
    viewMode: String,
    query: String,
    recursiveVideosOnly: Boolean,
    sourceFileNameMode: Boolean,
): String = listOf(
    providerType.name,
    activeProfileId,
    activeLibrary,
    currentFolder,
    viewMode,
    sortMode,
    query.trim(),
    recursiveVideosOnly.toString(),
    sourceFileNameMode.toString(),
).joinToString("|")

private fun androidx.compose.foundation.lazy.LazyListState.toBrowseScrollPosition(): BrowseScrollPosition =
    BrowseScrollPosition(
        firstVisibleItemIndex = firstVisibleItemIndex,
        firstVisibleItemScrollOffset = firstVisibleItemScrollOffset,
    )

private fun String.toProviderBrowseMovieSort(providerType: ProviderType): String = when (providerType) {
    ProviderType.MediaTree -> toMediaTreeBrowseMovieSort()
    ProviderType.Jellyfin, ProviderType.Emby -> toJellyfinBrowseMovieSort()
    ProviderType.M3U,
    ProviderType.SMB,
    ProviderType.WebDAV,
    -> this
}

private fun String.toMediaTreeBrowseMovieSort(): String = when (this) {
    "name" -> "name"
    "modified" -> "created_desc"
    "size" -> "created_desc"
    else -> this
}

private fun String.toJellyfinBrowseMovieSort(): String = when (this) {
    "name" -> "title_asc"
    "modified" -> "created_desc"
    "size" -> "size_desc"
    else -> this
}

private fun List<FolderNodeDto>.sortedFoldersForBrowse(sort: String): List<FolderNodeDto> = when (sort) {
    "modified" -> sortedWith(compareByDescending<FolderNodeDto> { it.createdMax.orEmpty() }.thenBy { it.browseTitle() })
    "size" -> sortedWith(compareByDescending<FolderNodeDto> { it.movieCount }.thenBy { it.browseTitle() })
    else -> sortedBy { it.browseTitle() }
}

private fun com.zasenjc.mediatree.data.SmbEntry.toMountedMovieDto(source: ClientStorageSource): MovieDto =
    MovieDto(
        id = (source.id + ":" + path).hashCode(),
        path = path,
        code = name,
        title = name,
        displayTitle = name,
        mediaRoot = mediaRootPath(source.id),
        scraperSource = MountedImageItemMarker.takeIf { isViewableImage },
        fileSize = sizeBytes,
        size = sizeBytes,
        updatedAt = modified.takeIf { it > 0L }?.toString(),
        createdAt = modified.takeIf { it > 0L }?.toString(),
    )

private fun com.zasenjc.mediatree.data.WebDavEntry.toMountedMovieDto(source: ClientStorageSource): MovieDto =
    MovieDto(
        id = (source.id + ":" + path).hashCode(),
        path = path,
        code = name,
        title = name,
        displayTitle = name,
        mediaRoot = webDavLibraryPath(source.id),
        scraperSource = MountedImageItemMarker.takeIf { isViewableImage },
        fileSize = sizeBytes,
        size = sizeBytes,
        updatedAt = modified.ifBlank { null },
        createdAt = modified.ifBlank { null },
    )

private fun List<MovieDto>.sortedMoviesForBrowse(sort: String): List<MovieDto> = when (sort) {
    "modified" -> sortedWith(compareByDescending<MovieDto> { it.updatedAt ?: it.createdAt.orEmpty() }.thenBy { it.browseTitle() })
    "size" -> sortedWith(compareByDescending<MovieDto> { it.fileSize ?: it.size ?: (it.duration?.toLong() ?: 0L) }.thenBy { it.browseTitle() })
    else -> sortedWith(compareBy<MovieDto> { it.browseTitle() }.thenBy { it.code })
}

private fun List<MovieDto>.mountedThumbnailRowKey(source: ClientStorageSource?, spec: MountedVideoThumbnailSpec): String =
    joinToString("|") { movie -> movie.mountedThumbnailItemKey(source, spec) }

private fun MovieDto.mountedThumbnailItemKey(source: ClientStorageSource?, spec: MountedVideoThumbnailSpec): String =
    mountedThumbnailKey(source, this, spec) ?: id.toString()

private fun FolderNodeDto.browseTitle(): String = name

private fun FolderNodeDto.sourceFileNameTitle(): String =
    storageFileNameOrFallback(path, name.ifBlank { displayTitle.orEmpty() })

private fun FolderNodeDto.detailRoute(): String =
    "detail/${path.toMovieRouteId()}?providerItemId=${Uri.encode(path)}"

private fun MovieDto.routeId(): Int = id

private fun MovieDto.detailRoute(): String =
    "detail/${routeId()}" + providerRouteItemId().takeIf { it.isNotBlank() }?.let { "?providerItemId=${Uri.encode(it)}" }.orEmpty()

private fun MovieDto.providerRouteItemId(): String = providerItemId?.takeIf { it.isNotBlank() } ?: path

private fun MovieDto.openRoute(): String =
    mediaRoot?.smbLibrarySourceId()?.let { sourceId ->
        "${if (isMountedImageItem()) "smbImage" else "smbPlayer"}/$sourceId?path=${Uri.encode(path)}"
    }
        ?: mediaRoot?.webDavLibrarySourceId()?.let { sourceId ->
            "${if (isMountedImageItem()) "webdavImage" else "webdavPlayer"}/$sourceId?path=${Uri.encode(path)}"
        }
        ?: detailRoute()

private fun MovieDto.isMountedLibraryItem(): Boolean =
    mediaRoot?.mountedLibrarySourceId() != null

private fun MovieDto.isMountedImageItem(): Boolean =
    isMountedLibraryItem() && (scraperSource == MountedImageItemMarker || isViewableImageFileName(path.ifBlank { code }))

private const val MountedPosterVideoFrameWidth = 120
private const val MountedPosterVideoFrameHeight = 180
private const val MountedLandscapeVideoFrameWidth = 128
private const val MountedLandscapeVideoFrameHeight = 72
private const val MountedImageItemMarker = "mounted-image"

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

private fun MovieDto.sourceFileNameTitle(): String =
    storageFileNameOrFallback(path, code.ifBlank { displayTitle ?: title.orEmpty() })

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

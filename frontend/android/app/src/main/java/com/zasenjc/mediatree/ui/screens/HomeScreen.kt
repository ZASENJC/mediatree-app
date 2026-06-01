package com.zasenjc.mediatree.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.ClientStorageSource
import com.zasenjc.mediatree.data.ClientStorageType
import com.zasenjc.mediatree.data.HomeLayoutPreference
import com.zasenjc.mediatree.data.FolderNodeDto
import com.zasenjc.mediatree.data.MediaRootDto
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.MoviesResponseDto
import com.zasenjc.mediatree.data.SmbEntry
import com.zasenjc.mediatree.data.WebDavEntry
import com.zasenjc.mediatree.data.smbLibraryPath
import com.zasenjc.mediatree.data.smbLibrarySourceId
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.data.webDavLibraryPath
import com.zasenjc.mediatree.data.webDavLibrarySourceId
import com.zasenjc.mediatree.ui.components.DesignTopAppBar
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.MediaAsyncImage
import com.zasenjc.mediatree.ui.components.SyncChromeWithGridScroll
import com.zasenjc.mediatree.ui.components.topChromeEnterTransition
import com.zasenjc.mediatree.ui.components.topChromeExitTransition
import com.zasenjc.mediatree.ui.shouldLoadRemoteContent
import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

private val sortOptions = listOf(
    "release_date_desc" to "发行时间",
    "created_desc" to "最新添加",
    "created_asc" to "最早添加",
    "title_asc" to "标题 A-Z",
)

class HomeViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val roots: List<MediaRootDto> = emptyList(),
        val recent: List<MovieDto> = emptyList(),
        val feedMovies: List<MovieDto> = emptyList(),
        val libraryItems: List<FolderNodeDto> = emptyList(),
        val sortMode: String = "release_date_desc",
        val openingPath: String? = null,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(providerType: ProviderType, activeLibrary: String, sort: String = _state.value.sortMode) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, sortMode = sort) }
            try {
                val provider = container.mediaProviderFor(providerType)
                val smbSourceId = activeLibrary.smbLibrarySourceId()
                if (smbSourceId != null) {
                    loadSmbLibrary(smbSourceId, sort)
                    return@launch
                }
                val webDavSourceId = activeLibrary.webDavLibrarySourceId()
                if (webDavSourceId != null) {
                    loadWebDavLibrary(webDavSourceId, sort)
                    return@launch
                }
                val roots = provider.mediaRoots().items
                if (activeLibrary.isBlank()) {
                    roots.firstOrNull { !it.locked }?.let { container.sessionStore.setActiveLibrary(it.path) }
                }
                val lib = activeLibrary.ifBlank { roots.firstOrNull { !it.locked }?.path.orEmpty() }
                val providerSort = sort.toProviderHomeMovieSort(providerType)
                val items = provider.folders(mediaRoot = lib)
                    .tree
                    .filter { it.movieCount > 0 }
                    .sortedForHome(sort)
                val recent = provider.recentWatched(limit = 20, mediaRoot = lib).movies
                val feedMovies = provider.movies(sort = providerSort, limit = 60, mediaRoot = lib).movies
                _state.update {
                    it.copy(
                        loading = false,
                        roots = roots,
                        recent = recent,
                        feedMovies = feedMovies,
                        libraryItems = items,
                        sortMode = sort,
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    private suspend fun loadSmbLibrary(sourceId: String, sort: String) {
        val source = container.clientStorageRepository.load()
            .firstOrNull { it.id == sourceId && it.type == ClientStorageType.SMB && it.enabled }
            ?: throw IllegalArgumentException("SMB 存储源不可用")
        val entries = container.smbClient.list(source)
        val folders = entries.filter { it.isDirectory }
            .map { entry -> entry.toFolderNode(source.id) }
            .sortedForHome(sort)
        val recent = entries.filter { it.isPlayableVideo }
            .map { entry -> entry.toMovieDto(source) }
            .sortedMoviesForHome(sort)
            .take(20)
        _state.update {
            it.copy(
                loading = false,
                roots = emptyList(),
                recent = recent,
                feedMovies = recent,
                libraryItems = folders,
                sortMode = sort,
            )
        }
    }

    private suspend fun loadWebDavLibrary(sourceId: String, sort: String) {
        val source = container.clientStorageRepository.load()
            .firstOrNull { it.id == sourceId && it.type == ClientStorageType.WebDAV && it.enabled }
            ?: throw IllegalArgumentException("WebDAV 存储源不可用")
        val entries = container.webDavClient.list(source)
        val folders = entries.filter { it.isDirectory }
            .map { entry -> entry.toFolderNode(source.id) }
            .sortedForHome(sort)
        val recent = entries.filter { it.isPlayableVideo }
            .map { entry -> entry.toMovieDto(source) }
            .sortedMoviesForHome(sort)
            .take(20)
        _state.update {
            it.copy(
                loading = false,
                roots = emptyList(),
                recent = recent,
                feedMovies = recent,
                libraryItems = folders,
                sortMode = sort,
            )
        }
    }

    fun openLibraryItem(
        providerType: ProviderType,
        item: FolderNodeDto,
        fallbackMediaRoot: String,
        onNavigate: (String) -> Unit,
    ) {
        if (_state.value.openingPath == item.path) return
        viewModelScope.launch {
            _state.update { it.copy(openingPath = item.path, error = null) }
            try {
                if (item.mediaRoot?.smbLibrarySourceId() != null || item.mediaRoot?.webDavLibrarySourceId() != null) {
                    onNavigate("browse?folder=${Uri.encode(item.path)}&recursiveVideos=true")
                } else if (item.isLeaf) {
                    onNavigate(item.detailRoute())
                } else {
                    val response = container.mediaProviderFor(providerType).movies(
                        folder = item.path,
                        sort = "created_desc",
                        limit = 1,
                        mediaRoot = item.mediaRoot?.takeIf { it.isNotBlank() } ?: fallbackMediaRoot,
                    )
                    val movie = response.movies.firstOrNull()
                    if (movie == null) {
                        _state.update { it.copy(error = IllegalStateException("未找到可播放影片")) }
                    } else {
                        onNavigate(movie.detailRoute())
                    }
                }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e) }
            } finally {
                _state.update { it.copy(openingPath = null) }
            }
        }
    }

}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    session: Session,
    onNavigate: (String) -> Unit,
    onError: (Throwable) -> Unit,
    browseViewMode: String,
    onBrowseViewModeChange: (String) -> Unit,
    chromeVisible: Boolean = true,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val homeLayout by container.uiPreferencesStore.homeLayoutFlow.collectAsStateWithLifecycle(initialValue = HomeLayoutPreference.MediaFeed)
    if (homeLayout == HomeLayoutPreference.DirectoryFirst) {
        BrowseScreen(
            container = container,
            session = session,
            onNavigate = onNavigate,
            onError = onError,
            initialFolder = "",
            viewMode = browseViewMode,
            onViewModeChange = onBrowseViewModeChange,
            chromeVisible = chromeVisible,
            onChromeVisibleChange = onChromeVisibleChange,
        )
        return
    }

    val vm: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val gridState = rememberLazyGridState()

    SyncChromeWithGridScroll(gridState, onChromeVisibleChange)

    LaunchedEffect(Unit) {
        onChromeVisibleChange(true)
    }

    LaunchedEffect(session.serverUrl, session.activeProviderType, session.activeLibrary) {
        if (session.canLoadHomeContent()) {
            vm.load(session.activeProviderType, session.activeLibrary)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let(onError)
    }

    val provider = remember(session.activeProviderType, container) {
        container.mediaProviderFor(session.activeProviderType)
    }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (
                !session.canLoadHomeContent()
            ) {
                EmptyMediaState("请先在设置页连接 MediaTree 服务器")
            } else if (state.loading) {
                LoadingPane(Modifier.fillMaxSize())
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(104.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, top = 86.dp, end = 20.dp, bottom = 116.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (state.recent.isNotEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, contentType = "section") {
                            HomeSectionHeader("继续观看 >")
                        }
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, contentType = "recent") {
                            HomeHeroRail(
                                movies = state.recent,
                                imageUrl = { movie -> provider.episodeStillUrl(session.serverUrl, movie.id) },
                                onOpen = { movie -> onNavigate(movie.openRoute()) },
                            )
                        }
                    }
                    item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, contentType = "section") {
                        HomeSectionHeader("媒体库")
                    }
                    if (state.libraryItems.isEmpty()) {
                        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }, contentType = "empty") {
                            EmptyMediaState("暂无媒体")
                        }
                    } else {
                        items(state.libraryItems, key = { it.path }, contentType = { "media-poster" }) { item ->
                            HomeMediaPosterCard(
                                item = item,
                                imageUrl = UrlUtils.resolveApiUrl(
                                    session.serverUrl,
                                    item.randomCover ?: item.cover,
                                ),
                                opening = state.openingPath == item.path,
                                onClick = {
                                    vm.openLibraryItem(
                                        providerType = session.activeProviderType,
                                        item = item,
                                        fallbackMediaRoot = session.activeLibrary,
                                        onNavigate = onNavigate,
                                    )
                                },
                            )
                        }
                    }
                }
            }
            AnimatedVisibility(
                visible = chromeVisible && !showSearch,
                enter = topChromeEnterTransition(),
                exit = topChromeExitTransition(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                DesignTopAppBar(
                    title = "mediatree",
                    brand = true,
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
                                            if (session.canLoadHomeContent()) {
                                                vm.load(session.activeProviderType, session.activeLibrary, key)
                                            }
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
                                        if (session.canLoadHomeContent()) {
                                            vm.load(session.activeProviderType, session.activeLibrary)
                                        }
                                    },
                                )
                            }
                        }
                    },
                )
            }
            HomeSearchOverlay(
                visible = showSearch,
                container = container,
                session = session,
                onDismiss = { showSearch = false },
                onNavigate = { path ->
                    showSearch = false
                    onNavigate(path)
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
    }
}

@Composable
private fun HomeSectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onBackground,
    )
}

@Composable
private fun HomeHeroRail(
    movies: List<MovieDto>,
    imageUrl: (MovieDto) -> String?,
    onOpen: (MovieDto) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(movies, key = { it.id }) { movie ->
            RecentWatchingCard(
                movie = movie,
                imageUrl = imageUrl(movie),
                onClick = { onOpen(movie) },
                modifier = Modifier.width(214.dp),
            )
        }
    }
}

@Composable
private fun HomeMoviePosterCard(
    movie: MovieDto,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = movie.displayTitle ?: movie.title ?: movie.code
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Card(
            modifier = Modifier.clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            MediaAsyncImage(
                imageUrl = imageUrl,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f),
                cornerRadius = 16.dp,
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(movie.releaseDate?.take(4), movieCardType(movie)).joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun HomeMediaPosterCard(
    item: FolderNodeDto,
    imageUrl: String?,
    opening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = item.displayTitle ?: item.name.ifBlank { item.path.substringAfterLast("/") }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            modifier = Modifier.clickable(enabled = !opening, onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box {
                MediaAsyncImage(
                    imageUrl = imageUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f),
                    cornerRadius = 16.dp,
                )
                if (item.folderWatched == true) {
                    WatchFlag(Modifier.align(Alignment.TopEnd).padding(7.dp))
                }
                if (opening) {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.28f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.releaseDateMax?.take(4).orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
    }
}

@Composable
private fun RecentWatchingCard(
    movie: MovieDto,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = movie.displayTitle ?: movie.title ?: movie.code
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Card(
            modifier = Modifier.clickable(onClick = onClick),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box {
                MediaAsyncImage(
                    imageUrl = imageUrl,
                    contentDescription = title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                    cornerRadius = 16.dp,
                )
                if (movie.tags.contains("watched")) {
                    WatchFlag(Modifier.align(Alignment.TopEnd).padding(7.dp))
                }
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = listOfNotNull(movie.releaseDate?.take(4), episodeText(movie).takeIf { it.isNotBlank() })
                .joinToString(" · "),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
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
private fun EmptyMediaState(text: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun HomeSearchOverlay(
    visible: Boolean,
    container: AppContainer,
    session: Session,
    onDismiss: () -> Unit,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MovieDto>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val provider = remember(session.activeProviderType, container) {
        container.mediaProviderFor(session.activeProviderType)
    }

    fun dismissSearch() {
        focusManager.clearFocus()
        onDismiss()
    }

    fun updateQuery(value: String) {
        query = value
        searchJob?.cancel()
        val request = value.trim()
        if (request.isBlank()) {
            results = emptyList()
            searching = false
            searchJob = null
            return
        }
        searching = true
        searchJob = scope.launch {
            delay(280)
            if (!session.canLoadHomeContent()) {
                results = emptyList()
                searching = false
                return@launch
            }
            try {
                val mountedSearchResults = session.activeLibrary.smbLibrarySourceId()?.let { sourceId ->
                    searchMountedLibrary(
                        sourceId = sourceId,
                        sourceType = ClientStorageType.SMB,
                        request = request,
                        container = container,
                    )
                } ?: session.activeLibrary.webDavLibrarySourceId()?.let { sourceId ->
                    searchMountedLibrary(
                        sourceId = sourceId,
                        sourceType = ClientStorageType.WebDAV,
                        request = request,
                        container = container,
                    )
                }
                val resp = mountedSearchResults ?: provider.search(
                    query = request,
                    sort = session.activeProviderType.defaultHomeSearchSort(),
                    limit = 20,
                    mediaRoot = session.activeLibrary,
                )
                if (query.trim() == request) {
                    results = resp.movies
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Throwable) {
                if (query.trim() == request) {
                    results = emptyList()
                }
            } finally {
                if (query.trim() == request) {
                    searching = false
                }
            }
        }
    }

    BackHandler(enabled = visible) {
        dismissSearch()
    }

    LaunchedEffect(visible) {
        if (visible) {
            delay(90)
            runCatching { focusRequester.requestFocus() }
        } else {
            searchJob?.cancel()
            searching = false
            delay(180)
            query = ""
            results = emptyList()
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = tween(90)) +
            scaleIn(
                initialScale = 0.9f,
                transformOrigin = TransformOrigin(0.86f, 0f),
                animationSpec = tween(210),
            ) +
            expandVertically(expandFrom = Alignment.Top, animationSpec = tween(240)),
        exit = fadeOut(animationSpec = tween(90)) +
            scaleOut(
                targetScale = 0.96f,
                transformOrigin = TransformOrigin(0.86f, 0f),
                animationSpec = tween(150),
            ) +
            shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(170)),
        modifier = modifier,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            tonalElevation = 6.dp,
        ) {
            Column(Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = ::updateQuery,
                    placeholder = { Text("搜索番号或标题") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { dismissSearch() }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭搜索")
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester),
                )

                AnimatedVisibility(
                    visible = query.isNotBlank() || searching || results.isNotEmpty(),
                    enter = fadeIn(animationSpec = tween(120)) +
                        expandVertically(expandFrom = Alignment.Top, animationSpec = tween(220)),
                    exit = fadeOut(animationSpec = tween(80)) +
                        shrinkVertically(shrinkTowards = Alignment.Top, animationSpec = tween(140)),
                ) {
                    Column {
                        Spacer(Modifier.height(10.dp))
                        when {
                            searching -> {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(96.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator()
                                }
                            }
                            query.isNotBlank() && results.isEmpty() -> {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .height(96.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "无结果",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            else -> {
                                LazyColumn(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 420.dp),
                                    contentPadding = PaddingValues(bottom = 4.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    items(results, key = { it.id }) { movie ->
                                        HomeSearchResultRow(
                                            movie = movie,
                                            imageUrl = if (movie.isMountedLibraryItem()) null else provider.coverUrl(session.serverUrl, movie.id),
                                            onClick = {
                                                focusManager.clearFocus()
                                                onNavigate(movie.openRoute())
                                            },
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
}

@Composable
private fun HomeSearchResultRow(
    movie: MovieDto,
    imageUrl: String?,
    onClick: () -> Unit,
) {
    val title = movie.displayTitle ?: movie.title ?: movie.code
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.38f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MediaAsyncImage(
                imageUrl = imageUrl,
                contentDescription = title,
                modifier = Modifier
                    .width(54.dp)
                    .height(76.dp),
                cornerRadius = 10.dp,
            )
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = listOfNotNull(movie.releaseDate?.take(4), movie.code.takeIf { it.isNotBlank() })
                        .joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun List<FolderNodeDto>.sortedForHome(sort: String): List<FolderNodeDto> = when (sort) {
    "created_desc" -> sortedWith(compareByDescending<FolderNodeDto> { it.createdMax.orEmpty() }.thenBy { it.homeTitle() })
    "created_asc" -> sortedWith(compareBy<FolderNodeDto> { it.createdMax.orEmpty() }.thenBy { it.homeTitle() })
    "title_asc" -> sortedBy { it.homeTitle() }
    else -> sortedWith(
        compareByDescending<FolderNodeDto> { it.releaseDateMax.orEmpty() }
            .thenByDescending { it.createdMax.orEmpty() }
            .thenBy { it.homeTitle() },
    )
}

private suspend fun searchMountedLibrary(
    sourceId: String,
    sourceType: ClientStorageType,
    request: String,
    container: AppContainer,
): MoviesResponseDto {
    val query = request.trim().lowercase()
    val source = container.clientStorageRepository.load()
        .firstOrNull { it.id == sourceId && it.type == sourceType && it.enabled }
        ?: return MoviesResponseDto()
    val movies = when (sourceType) {
        ClientStorageType.SMB -> collectSmbMountedLibraryVideos(source, container)
            .filter { it.isPlayableVideo && it.matchesMountedQuery(query) }
            .map { it.toMovieDto(source) }
        ClientStorageType.WebDAV -> collectWebDavMountedLibraryVideos(source, container)
            .filter { it.isPlayableVideo && it.matchesMountedQuery(query) }
            .map { it.toMovieDto(source) }
    }.sortedForHomeSearch().take(20)
    return MoviesResponseDto(movies = movies, total = movies.size)
}

private suspend fun collectSmbMountedLibraryVideos(
    source: ClientStorageSource,
    container: AppContainer,
): List<SmbEntry> {
    val pending = ArrayDeque<String>()
    val visited = mutableSetOf<String>()
    val videos = mutableListOf<SmbEntry>()
    pending.add("")
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

private suspend fun collectWebDavMountedLibraryVideos(
    source: ClientStorageSource,
    container: AppContainer,
): List<WebDavEntry> {
    val pending = ArrayDeque<String>()
    val visited = mutableSetOf<String>()
    val videos = mutableListOf<WebDavEntry>()
    pending.add("")
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

private fun SmbEntry.matchesMountedQuery(query: String): Boolean =
    name.lowercase().contains(query) || path.lowercase().contains(query)

private fun WebDavEntry.matchesMountedQuery(query: String): Boolean =
    name.lowercase().contains(query) || path.lowercase().contains(query)

private fun SmbEntry.toFolderNode(sourceId: String): FolderNodeDto = FolderNodeDto(
    name = name,
    path = path,
    isLeaf = false,
    movieCount = 1,
    displayTitle = name,
    mediaRoot = smbLibraryPath(sourceId),
    createdMax = modified.takeIf { it > 0L }?.toString(),
)

private fun SmbEntry.toMovieDto(source: ClientStorageSource): MovieDto = MovieDto(
    id = (source.id + ":" + path).hashCode(),
    path = path,
    code = name,
    title = name,
    displayTitle = name,
    mediaRoot = smbLibraryPath(source.id),
    fileSize = sizeBytes,
    size = sizeBytes,
    updatedAt = modified.takeIf { it > 0L }?.toString(),
    createdAt = modified.takeIf { it > 0L }?.toString(),
)

private fun WebDavEntry.toFolderNode(sourceId: String): FolderNodeDto = FolderNodeDto(
    name = name,
    path = path,
    isLeaf = false,
    movieCount = 1,
    displayTitle = name,
    mediaRoot = webDavLibraryPath(sourceId),
    createdMax = modified.ifBlank { null },
)

private fun WebDavEntry.toMovieDto(source: ClientStorageSource): MovieDto = MovieDto(
    id = (source.id + ":" + path).hashCode(),
    path = path,
    code = name,
    title = name,
    displayTitle = name,
    mediaRoot = webDavLibraryPath(source.id),
    fileSize = sizeBytes,
    size = sizeBytes,
    updatedAt = modified.ifBlank { null },
    createdAt = modified.ifBlank { null },
)

private fun FolderNodeDto.homeTitle(): String = displayTitle ?: name.ifBlank { path }

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
    mediaRoot?.smbLibrarySourceId() != null || mediaRoot?.webDavLibrarySourceId() != null

private fun List<MovieDto>.sortedMoviesForHome(sort: String): List<MovieDto> = when (sort) {
    "created_desc" -> sortedWith(compareByDescending<MovieDto> { it.updatedAt ?: it.createdAt.orEmpty() }.thenBy { it.homeTitle() })
    "created_asc" -> sortedWith(compareBy<MovieDto> { it.updatedAt ?: it.createdAt.orEmpty() }.thenBy { it.homeTitle() })
    "title_asc" -> sortedBy { it.homeTitle() }
    else -> sortedWith(
        compareByDescending<MovieDto> { it.releaseDate.orEmpty() }
            .thenByDescending { it.updatedAt ?: it.createdAt.orEmpty() }
            .thenBy { it.homeTitle() },
    )
}

private fun List<MovieDto>.sortedForHomeSearch(): List<MovieDto> =
    sortedWith(compareBy<MovieDto> { it.homeTitle() }.thenBy { it.path })

private fun MovieDto.homeTitle(): String = displayTitle ?: title ?: code

private fun String.toProviderHomeMovieSort(providerType: ProviderType): String = when (providerType) {
    ProviderType.MediaTree -> toMediaTreeHomeMovieSort()
    ProviderType.Jellyfin, ProviderType.Emby -> toJellyfinHomeMovieSort()
    ProviderType.SMB, ProviderType.WebDAV -> this
}

private fun String.toMediaTreeHomeMovieSort(): String = when (this) {
    "title_asc" -> "name"
    else -> this
}

private fun String.toJellyfinHomeMovieSort(): String = when (this) {
    "title_asc" -> "title_asc"
    "created_asc" -> "created_asc"
    else -> this
}

private fun ProviderType.defaultHomeSearchSort(): String = when (this) {
    ProviderType.MediaTree -> "created_desc"
    ProviderType.Jellyfin, ProviderType.Emby -> "created_desc"
    ProviderType.SMB, ProviderType.WebDAV -> "created_desc"
}

private fun Session.canLoadHomeContent(): Boolean =
    shouldLoadRemoteContent(this) ||
        activeLibrary.smbLibrarySourceId() != null ||
        activeLibrary.webDavLibrarySourceId() != null

private fun String.toMovieRouteId(): Int =
    takeLast(8).toUIntOrNull(16)?.toInt() ?: hashCode()

private fun movieCardType(movie: MovieDto): String = when {
    movie.tmdbEpisode != null || movie.episodeTitle != null -> "剧集"
    movie.tmdbType == "tv" -> "剧集"
    else -> "电影"
}

private fun episodeText(movie: MovieDto): String = when {
    movie.tmdbSeason != null || movie.tmdbEpisode != null ->
        "S${(movie.tmdbSeason ?: 0).toString().padStart(2, '0')}E${(movie.tmdbEpisode ?: 0).toString().padStart(2, '0')}"
    !movie.episodeLabel.isNullOrBlank() -> movie.episodeLabel
    movie.episodeNumber != null -> "EP${movie.episodeNumber.toString().padStart(2, '0')}"
    else -> ""
}

package com.zasenjc.mediatree.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.FolderNodeDto
import com.zasenjc.mediatree.data.MediaRootDto
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.SectionHeader
import com.zasenjc.mediatree.ui.components.SyncChromeWithListScroll
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
        val libraryItems: List<FolderNodeDto> = emptyList(),
        val sortMode: String = "release_date_desc",
        val openingPath: String? = null,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(activeLibrary: String, sort: String = _state.value.sortMode) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, sortMode = sort) }
            try {
                val roots = container.mediaProvider.mediaRoots().items
                if (activeLibrary.isBlank()) {
                    roots.firstOrNull { !it.locked }?.let { container.sessionStore.setActiveLibrary(it.path) }
                }
                val lib = activeLibrary.ifBlank { roots.firstOrNull { !it.locked }?.path.orEmpty() }
                val items = container.mediaProvider.folders(mediaRoot = lib)
                    .tree
                    .filter { it.movieCount > 0 }
                    .sortedForHome(sort)
                val recent = container.mediaProvider.recentWatched(limit = 20, mediaRoot = lib).movies
                _state.update {
                    it.copy(
                        loading = false,
                        roots = roots,
                        recent = recent,
                        libraryItems = items,
                        sortMode = sort,
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    fun openLibraryItem(
        item: FolderNodeDto,
        fallbackMediaRoot: String,
        onNavigate: (String) -> Unit,
    ) {
        if (_state.value.openingPath == item.path) return
        viewModelScope.launch {
            _state.update { it.copy(openingPath = item.path, error = null) }
            try {
                val response = container.mediaProvider.movies(
                    folder = item.path,
                    sort = "created_desc",
                    limit = 1,
                    mediaRoot = item.mediaRoot?.takeIf { it.isNotBlank() } ?: fallbackMediaRoot,
                )
                val movie = response.movies.firstOrNull()
                if (movie == null) {
                    _state.update { it.copy(error = IllegalStateException("未找到可播放影片")) }
                } else {
                    onNavigate("detail/${movie.id}")
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
    chromeVisible: Boolean = true,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val vm: HomeViewModel = viewModel(factory = viewModelFactory { HomeViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var showSearch by remember { mutableStateOf(false) }
    var showSort by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    SyncChromeWithListScroll(listState, onChromeVisibleChange)

    LaunchedEffect(Unit) {
        onChromeVisibleChange(true)
    }

    LaunchedEffect(session.serverUrl, session.activeLibrary) {
        if (shouldLoadRemoteContent(session)) {
            vm.load(session.activeLibrary)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let(onError)
    }

    Scaffold { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (!shouldLoadRemoteContent(session)) {
                EmptyMediaState("请先在设置页连接 MediaTree 服务器")
            } else if (state.loading) {
                LoadingPane(Modifier.fillMaxSize())
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, top = 82.dp, end = 16.dp, bottom = 112.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    if (state.recent.isNotEmpty()) {
                        item { SectionHeader("最近观看") }
                        item {
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                items(state.recent, key = { it.id }) { movie ->
                                    RecentWatchingCard(
                                        movie = movie,
                                        imageUrl = container.mediaProvider.episodeStillUrl(session.serverUrl, movie.id),
                                        onClick = { onNavigate("detail/${movie.id}") },
                                        modifier = Modifier.width(214.dp),
                                    )
                                }
                            }
                        }
                    }
                    item { SectionHeader("媒体库") }
                    if (state.libraryItems.isEmpty()) {
                        item { EmptyMediaState("暂无媒体") }
                    } else {
                        items(state.libraryItems.chunked(3)) { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                                row.forEach { item ->
                                    HomeLibraryPosterCard(
                                        item = item,
                                        imageUrl = UrlUtils.resolveApiUrl(
                                            session.serverUrl,
                                            item.randomCover ?: item.cover,
                                        ),
                                        opening = state.openingPath == item.path,
                                        onClick = {
                                            vm.openLibraryItem(
                                                item = item,
                                                fallbackMediaRoot = session.activeLibrary,
                                                onNavigate = onNavigate,
                                            )
                                        },
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
            AnimatedVisibility(
                visible = chromeVisible && !showSearch,
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
                                            if (shouldLoadRemoteContent(session)) {
                                                vm.load(session.activeLibrary, key)
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
                                        if (shouldLoadRemoteContent(session)) {
                                            vm.load(session.activeLibrary)
                                        }
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
private fun HomeLibraryPosterCard(
    item: FolderNodeDto,
    imageUrl: String?,
    opening: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val title = item.displayTitle ?: item.name.ifBlank { item.path.substringAfterLast("/") }
    Card(
        modifier = modifier
            .clickable(enabled = !opening, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.clip(RoundedCornerShape(12.dp))) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            Text(
                text = item.releaseDateMax?.take(4).orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
        }
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
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.clip(RoundedCornerShape(12.dp))) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                )
                if (movie.tags.contains("watched")) {
                    WatchFlag(Modifier.align(Alignment.TopEnd).padding(7.dp))
                }
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 2.dp),
            )
            Text(
                text = listOfNotNull(movie.releaseDate?.take(4), episodeText(movie).takeIf { it.isNotBlank() })
                    .joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
            if (!shouldLoadRemoteContent(session)) {
                results = emptyList()
                searching = false
                return@launch
            }
            try {
                val resp = container.mediaProvider.movies(
                    code = request,
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
                                            imageUrl = container.mediaProvider.coverUrl(session.serverUrl, movie.id),
                                            onClick = {
                                                focusManager.clearFocus()
                                                onNavigate("detail/${movie.id}")
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
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(54.dp)
                    .height(76.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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

private fun FolderNodeDto.homeTitle(): String = displayTitle ?: name.ifBlank { path }

private fun episodeText(movie: MovieDto): String = when {
    movie.tmdbSeason != null || movie.tmdbEpisode != null ->
        "S${(movie.tmdbSeason ?: 0).toString().padStart(2, '0')}E${(movie.tmdbEpisode ?: 0).toString().padStart(2, '0')}"
    !movie.episodeLabel.isNullOrBlank() -> movie.episodeLabel
    movie.episodeNumber != null -> "EP${movie.episodeNumber.toString().padStart(2, '0')}"
    else -> ""
}

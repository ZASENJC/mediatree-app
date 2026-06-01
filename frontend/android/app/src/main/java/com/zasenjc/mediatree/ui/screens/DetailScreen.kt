package com.zasenjc.mediatree.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.CrewCreditDto
import com.zasenjc.mediatree.data.MediaInfoDto
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.PersonCreditDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.SubtitleTrackDto
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.player.MediaTreePlayer
import com.zasenjc.mediatree.player.PlaybackPositionSnapshot
import com.zasenjc.mediatree.playback.PlaybackSource
import com.zasenjc.mediatree.ui.components.ErrorPane
import com.zasenjc.mediatree.ui.components.FullscreenSystemBarsEffect
import com.zasenjc.mediatree.ui.components.InfoBlock
import com.zasenjc.mediatree.ui.components.InfoLine
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.DesignFilterChip
import com.zasenjc.mediatree.ui.components.DesignTopAppBar
import com.zasenjc.mediatree.ui.components.SectionHeader
import com.zasenjc.mediatree.ui.shouldLoadRemoteContent
import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

private const val ExitPlayerReleaseDelayMillis = 120L

class DetailViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val movie: MovieDto? = null,
        val mediaInfo: MediaInfoDto? = null,
        val seriesItems: List<MovieDto> = emptyList(),
        val resume: Double = 0.0,
        val subtitleTracks: List<SubtitleTrackDto> = emptyList(),
        val selectedSubtitle: Int = -1,
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(providerType: ProviderType, movieId: Int, mediaRoot: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val provider = container.mediaProviderFor(providerType)
                val movie = provider.detail(movieId)
                val resume = provider.progress(movieId).position
                val subs = runCatching { provider.subtitleTracks(movieId) }.getOrDefault(emptyList())
                val mediaInfo = runCatching { provider.mediaInfo(movieId) }.getOrNull()
                val seriesFolder = seriesFolderFor(movie)
                val seriesItems = if (seriesFolder.isBlank()) {
                    emptyList()
                } else {
                    runCatching {
                        provider.movies(
                            folder = seriesFolder,
                            sort = "created_desc",
                            limit = 500,
                            mediaRoot = movie.mediaRoot?.takeIf { it.isNotBlank() } ?: mediaRoot,
                        ).movies.sortedForEpisodes()
                    }.getOrDefault(emptyList())
                }
                _state.update {
                    it.copy(
                        loading = false,
                        movie = movie,
                        mediaInfo = mediaInfo,
                        seriesItems = seriesItems,
                        resume = resume,
                        subtitleTracks = subs,
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }

    fun toggleFavorite(providerType: ProviderType) {
        val movie = _state.value.movie ?: return
        viewModelScope.launch {
            val provider = container.mediaProviderFor(providerType)
            if (movie.tags.contains("favorite")) {
                provider.removeTag(movie.id, "favorite")
                _state.update { it.copy(movie = it.movie?.copy(tags = it.movie!!.tags - "favorite")) }
            } else {
                provider.addTag(movie.id, "favorite")
                _state.update { it.copy(movie = it.movie?.copy(tags = it.movie!!.tags + "favorite")) }
            }
        }
    }

    fun markWatched(providerType: ProviderType) {
        val movie = _state.value.movie ?: return
        viewModelScope.launch {
            container.mediaProviderFor(providerType).addTag(movie.id, "watched")
            _state.update { it.copy(movie = it.movie?.copy(tags = it.movie!!.tags + "watched")) }
        }
    }

    fun selectSubtitle(index: Int) {
        _state.update { it.copy(selectedSubtitle = index) }
    }

    fun saveProgress(providerType: ProviderType, movieId: Int, position: Double, duration: Double) {
        viewModelScope.launch {
            runCatching { container.mediaProviderFor(providerType).saveProgress(movieId, position, duration) }
        }
    }

    fun onPlaybackComplete(providerType: ProviderType, movieId: Int, position: Double, duration: Double) {
        viewModelScope.launch {
            val provider = container.mediaProviderFor(providerType)
            runCatching { provider.saveProgress(movieId, position, duration, stopped = true) }
            runCatching { provider.addTag(movieId, "watched") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun DetailScreen(
    container: AppContainer,
    session: Session,
    movieId: Int,
    providerItemId: String = "",
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onError: (Throwable) -> Unit,
    onChromeVisibleChange: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var activeMovieId by remember(movieId) { mutableStateOf(movieId) }
    var leavingDetail by remember { mutableStateOf(false) }
    val playbackPositions = remember { mutableMapOf<Int, Double>() }
    var playbackPositionSnapshot by remember { mutableStateOf<(() -> PlaybackPositionSnapshot?)?>(null) }

    val vm: DetailViewModel = viewModel(factory = viewModelFactory { DetailViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val contentSnapshots = remember { mutableStateMapOf<Int, DetailViewModel.UiState>() }
    val activeMovie = state.movie?.takeIf { it.id == activeMovieId }

    fun leaveDetail() {
        leavingDetail = true
    }

    fun capturePlaybackPosition() {
        val snapshot = playbackPositionSnapshot?.invoke() ?: return
        playbackPositions[activeMovieId] = snapshot.positionSeconds
    }

    FullscreenSystemBarsEffect(isLandscape)
    LaunchedEffect(isLandscape) {
        onChromeVisibleChange(!isLandscape)
    }
    DisposableEffect(Unit) {
        onDispose { onChromeVisibleChange(true) }
    }
    BackHandler {
        if (isLandscape) {
            capturePlaybackPosition()
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        } else {
            leaveDetail()
        }
    }

    LaunchedEffect(leavingDetail) {
        if (leavingDetail) {
            delay(ExitPlayerReleaseDelayMillis)
            onBack()
        }
    }

    LaunchedEffect(activeMovieId, providerItemId, session.activeProviderType) {
        container.registerProviderItemId(session.activeProviderType, activeMovieId, providerItemId)
    }

    LaunchedEffect(activeMovieId, session.serverUrl, session.activeProviderType, session.activeLibrary) {
        playbackPositionSnapshot = null
        if (shouldLoadRemoteContent(session)) {
            vm.load(session.activeProviderType, activeMovieId, session.activeLibrary)
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let(onError)
    }

    LaunchedEffect(state.movie?.id, state) {
        state.movie?.let { contentSnapshots[it.id] = state }
    }

    if (!shouldLoadRemoteContent(session)) {
        LaunchedEffect(Unit) { onNavigate("settings") }
        ErrorPane(
            message = "请先在设置页连接 MediaTree 服务器",
            onRetry = { onNavigate("settings") },
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    val provider = remember(session.activeProviderType, container) {
        container.mediaProviderFor(session.activeProviderType)
    }
    val playbackSource: PlaybackSource = remember(
        session.serverUrl,
        session.token,
        session.activeUserId,
        session.activeProviderType,
        activeMovieId,
        state.subtitleTracks,
    ) {
        provider.playbackSource(
            serverUrl = session.serverUrl,
            movieId = activeMovieId,
            token = session.token,
            userId = session.activeUserId,
            subtitleTracks = state.subtitleTracks,
        )
    }

    val onSelectEpisode: (Int) -> Unit = { episodeId ->
        if (episodeId != activeMovieId) {
            activeMovieId = episodeId
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (!isLandscape) {
                DesignTopAppBar(
                    title = "",
                    navigationIcon = {
                        IconButton(onClick = ::leaveDetail) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "横屏播放")
                        }
                    },
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isLandscape) Modifier else Modifier.padding(padding))
                .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                state.loading && activeMovie == null -> LoadingPane()
                activeMovie == null -> ErrorPane(
                    message = state.error?.message ?: "影片加载失败",
                    onRetry = { vm.load(session.activeProviderType, activeMovieId, session.activeLibrary) },
                )
                !isLandscape -> PortraitPlayerCard(
                    activeMovie = activeMovie,
                    state = state,
                    contentSnapshots = contentSnapshots,
                    container = container,
                    session = session,
                    onSelectEpisode = onSelectEpisode,
                    onNavigate = onNavigate,
                    onFavorite = { vm.toggleFavorite(session.activeProviderType) },
                    onWatched = { vm.markWatched(session.activeProviderType) },
                )
            }

            if (activeMovie != null && !leavingDetail) {
                val playerModifier = if (isLandscape) {
                    Modifier
                        .fillMaxSize()
                } else {
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .align(Alignment.TopCenter)
                }
                MediaTreePlayer(
                    playbackSource = playbackSource,
                    startPosition = playbackPositions[activeMovieId] ?: state.resume,
                    selectedSubtitle = state.selectedSubtitle,
                    onPlaybackPositionChange = { pos, _ -> playbackPositions[activeMovieId] = pos },
                    onPlaybackPositionSnapshot = { playbackPositionSnapshot = it },
                    onProgressUpdate = { pos, dur -> vm.saveProgress(session.activeProviderType, activeMovieId, pos, dur) },
                    onPlaybackComplete = { pos, dur -> vm.onPlaybackComplete(session.activeProviderType, activeMovieId, pos, dur) },
                    isFullscreen = isLandscape,
                    showFullscreenButton = true,
                    showAspectRatioControls = isLandscape,
                    onFullscreenRequest = {
                        capturePlaybackPosition()
                        activity?.requestedOrientation = if (isLandscape) {
                            ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                        } else {
                            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }
                    },
                    modifier = playerModifier,
                )
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun PortraitPlayerCard(
    activeMovie: MovieDto,
    state: DetailViewModel.UiState,
    contentSnapshots: Map<Int, DetailViewModel.UiState>,
    container: AppContainer,
    session: Session,
    onSelectEpisode: (Int) -> Unit,
    onNavigate: (String) -> Unit,
    onFavorite: () -> Unit,
    onWatched: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .background(MaterialTheme.colorScheme.scrim),
        )
        Spacer(Modifier.height(12.dp))
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                AnimatedContent(
                    targetState = activeMovie.id,
                    transitionSpec = { detailEpisodeContentTransform() },
                    label = "detailMetadataContent",
                ) { contentMovieId ->
                    val targetState = contentSnapshots[contentMovieId] ?: state
                    val targetMovie = targetState.movie!!
                    DetailMetadataContent(
                        container = container,
                        session = session,
                        state = targetState,
                        movie = targetMovie,
                        onSelectEpisode = onSelectEpisode,
                        onNavigate = onNavigate,
                        onFavorite = onFavorite,
                        onWatched = onWatched,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun LandscapeDetailScaffold(
    activeMovie: MovieDto,
    state: DetailViewModel.UiState,
    contentSnapshots: Map<Int, DetailViewModel.UiState>,
    container: AppContainer,
    session: Session,
    onSelectEpisode: (Int) -> Unit,
    onNavigate: (String) -> Unit,
    onFavorite: () -> Unit,
    onWatched: () -> Unit,
) {
    Row(Modifier.fillMaxSize()) {
        Spacer(Modifier.weight(1.45f).fillMaxSize())
        Surface(
            modifier = Modifier.weight(1f).fillMaxSize().padding(12.dp),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
            tonalElevation = 4.dp,
            shadowElevation = 10.dp,
        ) {
            LazyColumn(
                contentPadding = PaddingValues(vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                item {
                    AnimatedContent(
                        targetState = activeMovie.id,
                        transitionSpec = { detailEpisodeContentTransform() },
                        label = "landscapeDetailMetadataContent",
                    ) { contentMovieId ->
                        val targetState = contentSnapshots[contentMovieId] ?: state
                        val targetMovie = targetState.movie!!
                        DetailMetadataContent(
                            container = container,
                            session = session,
                            state = targetState,
                            movie = targetMovie,
                            onSelectEpisode = onSelectEpisode,
                            onNavigate = onNavigate,
                            onFavorite = onFavorite,
                            onWatched = onWatched,
                        )
                    }
                }
            }
        }
    }
}

private fun detailEpisodeContentTransform(): ContentTransform =
    (
        fadeIn(animationSpec = tween(durationMillis = 180)) +
            slideInHorizontally(animationSpec = tween(durationMillis = 260)) { it / 8 }
        ).togetherWith(
            fadeOut(animationSpec = tween(durationMillis = 120)) +
                slideOutHorizontally(animationSpec = tween(durationMillis = 220)) { -it / 12 },
        )

@Composable
private fun DetailMetadataContent(
    container: AppContainer,
    session: Session,
    state: DetailViewModel.UiState,
    movie: MovieDto,
    onSelectEpisode: (Int) -> Unit,
    onNavigate: (String) -> Unit,
    onFavorite: () -> Unit,
    onWatched: () -> Unit,
) {
    var episodesExpanded by remember(movie.id) { mutableStateOf(false) }
    var selectedDetailTab by remember(movie.id) { mutableStateOf("信息") }
    var selectedSeasonKey by remember(movie.id, state.seriesItems) {
        mutableStateOf(seasonKey(movie))
    }
    val provider = remember(session.activeProviderType, container) {
        container.mediaProviderFor(session.activeProviderType)
    }
    val fallbackStill = remember(session.serverUrl, session.activeProviderType, movie.id) {
        provider.episodeStillUrl(session.serverUrl, movie.id)
    }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        MovieInfoHeader(
            movie = movie,
            mediaInfo = state.mediaInfo,
            seriesItems = state.seriesItems,
            currentMovieId = movie.id,
            serverUrl = session.serverUrl,
            episodesExpanded = episodesExpanded,
            selectedSeasonKey = selectedSeasonKey,
            onToggleEpisodes = { episodesExpanded = !episodesExpanded },
            onSelectSeason = { selectedSeasonKey = it },
            onSelectEpisode = onSelectEpisode,
            onNavigate = onNavigate,
            onFavorite = onFavorite,
            onWatched = onWatched,
        )
        DetailTabStrip(
            selectedTab = selectedDetailTab,
            onSelectTab = { selectedDetailTab = it },
        )
        if (selectedDetailTab == "剧照") {
            ThumbnailStrip(
                movie = movie,
                serverUrl = session.serverUrl,
                fallbackStill = fallbackStill,
            )
        } else {
            CastSection(movie = movie, serverUrl = session.serverUrl)
            Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                InfoBlock("简介", movie.episodeOverview ?: movie.overview ?: "暂无简介")
                InfoLine("导演", directorText(movie))
                InfoLine("类型", movie.genre.orEmpty())
                InfoLine("片商", movie.studio ?: movie.studios.orEmpty())
                InfoLine("目录", movie.folderLevels.orEmpty())
                CrewSection(movie.crew)
            }
        }
    }
}

@Composable
private fun DetailTabStrip(
    selectedTab: String,
    onSelectTab: (String) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(listOf("信息", "剧照")) { label ->
            DesignFilterChip(label = label, selected = label == selectedTab, onClick = { onSelectTab(label) })
        }
    }
}

@Composable
private fun MovieInfoHeader(
    movie: MovieDto,
    mediaInfo: MediaInfoDto?,
    seriesItems: List<MovieDto>,
    currentMovieId: Int,
    serverUrl: String,
    episodesExpanded: Boolean,
    selectedSeasonKey: Int,
    onToggleEpisodes: () -> Unit,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
    onNavigate: (String) -> Unit,
    onFavorite: () -> Unit,
    onWatched: () -> Unit,
) {
    val seasonGroups = remember(seriesItems) { buildSeasonGroups(seriesItems) }
    val selectedGroup = seasonGroups.firstOrNull { it.key == selectedSeasonKey } ?: seasonGroups.firstOrNull()
    val canSelectEpisodes = seriesItems.size > 1
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (canSelectEpisodes) Modifier.clickable(onClick = onToggleEpisodes) else Modifier),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = movie.title ?: movie.displayTitle ?: movie.code,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (canSelectEpisodes) {
                Icon(
                    imageVector = if (episodesExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (episodesExpanded) "收起集数" else "展开集数",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (episodesExpanded && canSelectEpisodes && selectedGroup != null) {
            EpisodeSelector(
                seasonGroups = seasonGroups,
                selectedSeasonKey = selectedGroup.key,
                currentMovieId = currentMovieId,
                serverUrl = serverUrl,
                onSelectSeason = onSelectSeason,
                onSelectEpisode = onSelectEpisode,
            )
        }
        movie.episodeTitle?.takeIf { it.isNotBlank() }?.let {
            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val chips = buildList {
                add(movie.code)
                movie.releaseDate?.takeIf { it.isNotBlank() }?.let { add(it.take(10)) }
                movie.duration?.let { add("${it} 分钟") }
                mediaInfo?.videoCodec?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                mediaInfo?.container?.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
                mediaInfo?.audioChannels?.takeIf { it > 0 }?.let { add("${it}.0") }
            }
            items(chips) { label ->
                SuggestionChip(onClick = {}, label = { Text(label) })
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            val favorite = movie.tags.contains("favorite")
            FilledTonalButton(
                onClick = onFavorite,
                modifier = Modifier.weight(1f),
                colors = if (favorite) {
                    ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    )
                } else {
                    ButtonDefaults.filledTonalButtonColors()
                },
            ) {
                Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (favorite) "已收藏" else "收藏")
            }
            FilledTonalButton(onClick = onWatched, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.CheckCircle, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(if (movie.tags.contains("watched")) "已看" else "标记已看")
            }
        }
    }
}

@Composable
private fun CastSection(movie: MovieDto, serverUrl: String) {
    val cast = castPeople(movie)
    if (cast.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("演员")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(cast.take(16)) { person ->
                PersonCard(person = person, serverUrl = serverUrl)
            }
        }
    }
}

@Composable
private fun PersonCard(person: PersonCreditDto, serverUrl: String) {
    Column(
        modifier = Modifier.width(74.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val imageUrl = UrlUtils.resolveApiUrl(serverUrl, person.profilePath)
        if (imageUrl == null) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(54.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(person.name.take(1), color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = person.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(54.dp).clip(CircleShape),
            )
        }
        Text(
            person.name,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = person.character ?: person.role.orEmpty(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun EpisodeSelector(
    seasonGroups: List<SeasonGroup>,
    selectedSeasonKey: Int,
    currentMovieId: Int,
    serverUrl: String,
    onSelectSeason: (Int) -> Unit,
    onSelectEpisode: (Int) -> Unit,
) {
    val selectedGroup = seasonGroups.firstOrNull { it.key == selectedSeasonKey } ?: return
    val selectedIndex = selectedGroup.episodes.indexOfFirst { it.id == currentMovieId }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = selectedIndex)
    LaunchedEffect(selectedSeasonKey, currentMovieId) {
        if (selectedIndex >= 0) listState.scrollToItem(selectedIndex)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SeasonPickerButton(
                groups = seasonGroups,
                selectedKey = selectedGroup.key,
                onSelect = onSelectSeason,
            )
            AssistChip(onClick = {}, label = { Text("${selectedGroup.episodes.size} 集") })
        }
        LazyRow(state = listState, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(selectedGroup.episodes, key = { it.id }) { episode ->
                EpisodeCoverCard(
                    movie = episode,
                    imageUrl = episodeStillImageUrl(serverUrl, episode),
                    selected = episode.id == currentMovieId,
                    onClick = {
                        if (episode.id != currentMovieId) onSelectEpisode(episode.id)
                    },
                )
            }
        }
    }
}

@Composable
private fun SeasonPickerButton(
    groups: List<SeasonGroup>,
    selectedKey: Int,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selected = groups.firstOrNull { it.key == selectedKey } ?: groups.first()
    Box {
        AssistChip(
            onClick = { if (groups.size > 1) expanded = true },
            label = { Text(selected.label) },
            trailingIcon = {
                if (groups.size > 1) {
                    Icon(Icons.Default.ArrowDropDown, contentDescription = null)
                }
            },
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            groups.forEach { group ->
                DropdownMenuItem(
                    text = { Text(group.label) },
                    onClick = {
                        expanded = false
                        onSelect(group.key)
                    },
                )
            }
        }
    }
}

@Composable
private fun EpisodeCoverCard(
    movie: MovieDto,
    imageUrl: String?,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier.width(142.dp).clickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        ElevatedCard(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                },
            ),
        ) {
            Box {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = movie.episodeTitle ?: movie.displayTitle ?: movie.title ?: movie.code,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f),
                )
                if (movie.tags.contains("watched")) {
                    WatchFlag(Modifier.align(Alignment.TopEnd).padding(6.dp))
                }
            }
        }
        Text(
            text = episodeCode(movie),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = movie.episodeTitle ?: movie.displayTitle ?: movie.title ?: movie.code,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
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
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 4.dp).size(15.dp),
        )
    }
}

@Composable
private fun ThumbnailStrip(movie: MovieDto, serverUrl: String, fallbackStill: String) {
    val thumbnails = remember(movie.id, movie.episodeStill, movie.javdbThumbnails, serverUrl, fallbackStill) {
        buildList {
            add(movie.episodeStill?.let { UrlUtils.resolveApiUrl(serverUrl, it) } ?: fallbackStill)
            movie.javdbThumbnails.forEach { value ->
                UrlUtils.resolveApiUrl(serverUrl, value)?.let { add(it) }
            }
        }.distinct().take(10)
    }
    if (thumbnails.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(thumbnails, key = { it }) { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(132.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp)),
                )
            }
        }
    }
}

@Composable
private fun CrewSection(crew: List<CrewCreditDto>) {
    val staff = crew.filterNot { it.job.contains("director", true) }.take(6)
    if (staff.isEmpty()) return
    InfoLine("工作人员", staff.joinToString(" / ") { "${it.name} (${it.job})" })
}

private data class SeasonGroup(
    val key: Int,
    val label: String,
    val episodes: List<MovieDto>,
)

private fun buildSeasonGroups(movies: List<MovieDto>): List<SeasonGroup> =
    movies
        .sortedForEpisodes()
        .groupBy { seasonKey(it) }
        .toSortedMap()
        .map { (key, episodes) ->
            SeasonGroup(
                key = key,
                label = if (key > 0) "第 ${key} 季" else "全部剧集",
                episodes = episodes,
            )
        }

private fun episodeStillImageUrl(serverUrl: String, movie: MovieDto): String =
    movie.episodeStill?.let { UrlUtils.resolveApiUrl(serverUrl, it) }
        ?: "${UrlUtils.apiBase(serverUrl)}/episode-still/${movie.id}"

private fun seriesFolderFor(movie: MovieDto): String {
    val folder = movie.folderLevels.orEmpty()
    if (folder.isBlank()) return ""
    val isEpisode = movie.tmdbType == "tv" ||
        movie.tmdbEpisode != null ||
        movie.episodeTitle != null ||
        movie.episodeNumber != null
    val parts = folder.split("/").filter { it.isNotBlank() }
    if (isEpisode && parts.size > 1 && isSeasonFolderName(parts.last())) {
        return parts.dropLast(1).joinToString("/")
    }
    return folder
}

private fun List<MovieDto>.sortedForEpisodes(): List<MovieDto> =
    sortedWith(
        compareBy<MovieDto> { seasonKey(it) }
            .thenBy { it.tmdbEpisode ?: it.episodeNumber ?: Int.MAX_VALUE }
            .thenBy { it.code }
            .thenBy { it.id },
    )

private fun seasonKey(movie: MovieDto): Int = movie.tmdbSeason ?: seasonNumberFromFolder(movie.folderLevels) ?: 0

private fun seasonNumberFromFolder(folderLevels: String?): Int? {
    val leaf = folderLevels
        ?.split("/")
        ?.lastOrNull { it.isNotBlank() }
        ?.trim()
        .orEmpty()
    if (leaf.isBlank()) return null
    val patterns = listOf(
        Regex("""^S\s*(\d{1,2})$""", RegexOption.IGNORE_CASE),
        Regex("""^Season\s*(\d{1,2})$""", RegexOption.IGNORE_CASE),
        Regex("""^第\s*(\d{1,2})\s*[季期部]?$"""),
    )
    return patterns.firstNotNullOfOrNull { pattern ->
        pattern.matchEntire(leaf)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}

private fun isSeasonFolderName(value: String): Boolean = seasonNumberFromFolder(value) != null

private fun castPeople(movie: MovieDto): List<PersonCreditDto> {
    if (movie.cast.isNotEmpty()) return movie.cast
    return movie.actress
        ?.split("/", "／", ",", "，")
        ?.map { it.trim() }
        ?.filter { it.isNotBlank() }
        ?.map { PersonCreditDto(name = it) }
        .orEmpty()
}

private fun directorText(movie: MovieDto): String {
    movie.director?.takeIf { it.isNotBlank() }?.let { return it }
    return movie.crew
        .filter { it.job.contains("director", true) }
        .joinToString(" / ") { it.name }
}

private fun episodeCode(movie: MovieDto): String {
    return if (movie.tmdbSeason != null || movie.tmdbEpisode != null) {
        "S${(movie.tmdbSeason ?: 0).toString().padStart(2, '0')}E${(movie.tmdbEpisode ?: 0).toString().padStart(2, '0')}"
    } else if (!movie.episodeLabel.isNullOrBlank()) {
        movie.episodeLabel
    } else if (movie.episodeNumber != null) {
        "EP${movie.episodeNumber.toString().padStart(2, '0')}"
    } else {
        movie.code
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

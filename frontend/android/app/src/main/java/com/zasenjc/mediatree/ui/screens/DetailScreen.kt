package com.zasenjc.mediatree.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.zasenjc.mediatree.data.ApiException
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.CrewCreditDto
import com.zasenjc.mediatree.data.MediaInfoDto
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.PersonCreditDto
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.SubtitleTrackDto
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.player.MediaTreePlayer
import com.zasenjc.mediatree.ui.components.ErrorPane
import com.zasenjc.mediatree.ui.components.FullscreenSystemBarsEffect
import com.zasenjc.mediatree.ui.components.InfoBlock
import com.zasenjc.mediatree.ui.components.InfoLine
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.SectionHeader
import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DetailViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val movie: MovieDto? = null,
        val mediaInfo: MediaInfoDto? = null,
        val seriesItems: List<MovieDto> = emptyList(),
        val resume: Double = 0.0,
        val subtitleTracks: List<SubtitleTrackDto> = emptyList(),
        val selectedSubtitle: Int = -1,
        val error: String? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(movieId: Int, mediaRoot: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val movie = container.api.detail(movieId)
                val resume = container.api.progress(movieId).position
                val subs = runCatching { container.api.subtitleTracks(movieId) }.getOrDefault(emptyList())
                val mediaInfo = runCatching { container.api.mediaInfo(movieId) }.getOrNull()
                val parentFolder = movie.path.substringBeforeLast("/", missingDelimiterValue = "")
                val seriesItems = if (parentFolder.isBlank()) {
                    emptyList()
                } else {
                    runCatching {
                        container.api.movies(
                            folder = parentFolder,
                            sort = "release_date_asc",
                            limit = 60,
                            mediaRoot = movie.mediaRoot ?: mediaRoot,
                        ).movies.sortedWith(compareBy<MovieDto> { it.tmdbSeason ?: 0 }.thenBy { it.tmdbEpisode ?: 0 }.thenBy { it.code })
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
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun toggleFavorite() {
        val movie = _state.value.movie ?: return
        viewModelScope.launch {
            if (movie.tags.contains("favorite")) {
                container.api.removeTag(movie.id, "favorite")
                _state.update { it.copy(movie = it.movie?.copy(tags = it.movie!!.tags - "favorite")) }
            } else {
                container.api.addTag(movie.id, "favorite")
                _state.update { it.copy(movie = it.movie?.copy(tags = it.movie!!.tags + "favorite")) }
            }
        }
    }

    fun markWatched() {
        val movie = _state.value.movie ?: return
        viewModelScope.launch {
            container.api.addTag(movie.id, "watched")
            _state.update { it.copy(movie = it.movie?.copy(tags = it.movie!!.tags + "watched")) }
        }
    }

    fun selectSubtitle(index: Int) {
        _state.update { it.copy(selectedSubtitle = index) }
    }

    fun saveProgress(movieId: Int, position: Double, duration: Double) {
        viewModelScope.launch {
            runCatching { container.api.saveProgress(movieId, position, duration) }
        }
    }

    fun onPlaybackComplete(movieId: Int, position: Double, duration: Double) {
        viewModelScope.launch {
            runCatching { container.api.saveProgress(movieId, position, duration, stopped = true) }
            runCatching { container.api.addTag(movieId, "watched") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailScreen(
    container: AppContainer,
    session: Session,
    movieId: Int,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val vm: DetailViewModel = viewModel(factory = viewModelFactory { DetailViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()

    FullscreenSystemBarsEffect(isLandscape)
    BackHandler(enabled = isLandscape) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }

    LaunchedEffect(movieId, session.activeLibrary) { vm.load(movieId, session.activeLibrary) }

    LaunchedEffect(state.error) {
        state.error?.let { onError(ApiException(0, it)) }
    }

    if (isLandscape) {
        Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            MediaTreePlayer(
                streamUrl = container.api.streamUrl(session.serverUrl, movieId),
                token = session.token,
                startPosition = state.resume,
                subtitleTracks = state.subtitleTracks,
                selectedSubtitle = state.selectedSubtitle,
                subtitleUrlProvider = { idx -> container.api.subtitleUrl(session.serverUrl, movieId, idx) },
                onProgressUpdate = { pos, dur -> vm.saveProgress(movieId, pos, dur) },
                onPlaybackComplete = { pos, dur -> vm.onPlaybackComplete(movieId, pos, dur) },
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("影片页") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回") }
                },
                actions = {
                    IconButton(onClick = {
                        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                    }) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "横屏播放")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        when {
            state.loading -> LoadingPane(Modifier.padding(padding))
            state.movie == null -> ErrorPane(
                message = state.error ?: "影片加载失败",
                onRetry = { vm.load(movieId, session.activeLibrary) },
                modifier = Modifier.padding(padding),
            )
            else -> {
                val item = state.movie!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(bottom = 28.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    item {
                        MediaTreePlayer(
                            streamUrl = container.api.streamUrl(session.serverUrl, movieId),
                            token = session.token,
                            startPosition = state.resume,
                            subtitleTracks = state.subtitleTracks,
                            selectedSubtitle = state.selectedSubtitle,
                            subtitleUrlProvider = { idx -> container.api.subtitleUrl(session.serverUrl, movieId, idx) },
                            onProgressUpdate = { pos, dur -> vm.saveProgress(movieId, pos, dur) },
                            onPlaybackComplete = { pos, dur -> vm.onPlaybackComplete(movieId, pos, dur) },
                            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                        )
                    }
                    if (state.subtitleTracks.isNotEmpty()) {
                        item {
                            SubtitleSelector(
                                tracks = state.subtitleTracks,
                                selectedSubtitle = state.selectedSubtitle,
                                onSelect = vm::selectSubtitle,
                            )
                        }
                    }
                    item {
                        MovieInfoHeader(
                            movie = item,
                            mediaInfo = state.mediaInfo,
                            onFavorite = vm::toggleFavorite,
                            onWatched = vm::markWatched,
                        )
                    }
                    item {
                        CastSection(movie = item, serverUrl = session.serverUrl)
                    }
                    if (state.seriesItems.isNotEmpty()) {
                        item {
                            SeriesSection(
                                movies = state.seriesItems,
                                currentMovieId = item.id,
                                onNavigate = onNavigate,
                            )
                        }
                    }
                    item {
                        ThumbnailStrip(
                            movie = item,
                            serverUrl = session.serverUrl,
                            fallbackStill = container.api.episodeStillUrl(session.serverUrl, item.id),
                        )
                    }
                    item {
                        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            InfoBlock("简介", item.episodeOverview ?: item.overview ?: "暂无简介")
                            InfoLine("导演", directorText(item))
                            InfoLine("类型", item.genre.orEmpty())
                            InfoLine("片商", item.studio ?: item.studios.orEmpty())
                            InfoLine("目录", item.folderLevels.orEmpty())
                            CrewSection(item.crew)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SubtitleSelector(
    tracks: List<SubtitleTrackDto>,
    selectedSubtitle: Int,
    onSelect: (Int) -> Unit,
) {
    LazyRow(
        modifier = Modifier.padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            FilterChip(
                selected = selectedSubtitle == -1,
                onClick = { onSelect(-1) },
                label = { Text("关闭字幕") },
            )
        }
        items(tracks) { track ->
            FilterChip(
                selected = selectedSubtitle == track.index,
                onClick = { onSelect(track.index) },
                label = {
                    Text(track.title.ifBlank { track.language.ifBlank { "轨道 ${track.index}" } })
                },
            )
        }
    }
}

@Composable
private fun MovieInfoHeader(
    movie: MovieDto,
    mediaInfo: MediaInfoDto?,
    onFavorite: () -> Unit,
    onWatched: () -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = movie.title ?: movie.displayTitle ?: movie.code,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
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
        Text(person.name, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
private fun SeriesSection(
    movies: List<MovieDto>,
    currentMovieId: Int,
    onNavigate: (String) -> Unit,
) {
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            SectionHeader("剧集")
            AssistChip(onClick = {}, label = { Text("${movies.size} 集") })
        }
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(movies, key = { it.id }) { movie ->
                EpisodeMiniCard(
                    movie = movie,
                    selected = movie.id == currentMovieId,
                    onClick = { onNavigate("detail/${movie.id}") },
                )
            }
        }
    }
}

@Composable
private fun EpisodeMiniCard(movie: MovieDto, selected: Boolean, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.width(112.dp).height(72.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
        ),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                episodeCode(movie),
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
            )
            Text(
                movie.episodeTitle ?: movie.title ?: movie.code,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ThumbnailStrip(movie: MovieDto, serverUrl: String, fallbackStill: String) {
    val thumbnails = buildList {
        add(movie.episodeStill?.let { UrlUtils.resolveApiUrl(serverUrl, it) } ?: fallbackStill)
        movie.javdbThumbnails.forEach { value ->
            UrlUtils.resolveApiUrl(serverUrl, value)?.let { add(it) }
        }
    }.distinct()
    if (thumbnails.isEmpty()) return
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        SectionHeader("精彩剧照")
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(thumbnails.take(10)) { url ->
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
    } else {
        movie.code
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

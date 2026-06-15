package com.zasenjc.mediatree.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.FullscreenModePreference
import com.zasenjc.mediatree.data.M3uChannel
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.player.MediaTreePlayer
import com.zasenjc.mediatree.playback.PlaybackSource
import com.zasenjc.mediatree.ui.components.ErrorPane
import com.zasenjc.mediatree.ui.components.FullscreenSystemBarsEffect
import com.zasenjc.mediatree.ui.components.LoadingPane
import com.zasenjc.mediatree.ui.components.MediaAsyncImage
import com.zasenjc.mediatree.ui.components.shapeAwareClickable
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3uPlayerScreen(
    container: AppContainer,
    session: Session,
    channelId: String,
    onBack: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreenModePreference by container.uiPreferencesStore.fullscreenModeFlow.collectAsStateWithLifecycle(
        initialValue = FullscreenModePreference.Landscape,
    )
    var fullscreenRequested by remember { mutableStateOf(false) }
    var channel by remember { mutableStateOf<M3uChannel?>(null) }
    var channels by remember { mutableStateOf<List<M3uChannel>>(emptyList()) }
    var favorite by remember { mutableStateOf(false) }
    var favoriteIds by remember { mutableStateOf<Set<String>>(emptySet()) }
    var error by remember { mutableStateOf<Throwable?>(null) }

    val profile = remember(session.activeProfileId, session.resolvedProfiles) {
        session.resolvedProfiles.firstOrNull { it.id == session.activeProfileId && it.type == ProviderType.M3U }
    }

    val playerFullscreen = fullscreenRequested || isLandscape
    FullscreenSystemBarsEffect(playerFullscreen)

    BackHandler {
        if (playerFullscreen) {
            fullscreenRequested = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        } else {
            onBack()
        }
    }

    LaunchedEffect(profile?.id, channelId) {
        channel = null
        channels = emptyList()
        favoriteIds = emptySet()
        favorite = false
        error = null
        runCatching {
            val m3uProfile = profile ?: throw IllegalArgumentException("M3U 订阅未配置")
            val loadedChannels = container.m3uSubscriptionCacheRepository.loadCached(m3uProfile)
            val loadedChannel = loadedChannels.firstOrNull { it.id == channelId }
                ?: throw IllegalArgumentException("直播频道不存在")
            val favorites = container.m3uFavoritesRepository.load(m3uProfile.id)
            channels = loadedChannels
            favoriteIds = favorites
            channel = loadedChannel
            favorite = loadedChannel.id in favorites
            error = null
        }.onFailure {
            error = it
        }
    }

    LaunchedEffect(error) {
        error?.let(onError)
    }

    Scaffold(
        topBar = {
            if (!playerFullscreen) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(
                            enabled = profile != null && channel != null,
                            onClick = {
                                val m3uProfile = profile ?: return@IconButton
                                val loadedChannel = channel ?: return@IconButton
                                val nextFavorite = loadedChannel.id !in favoriteIds
                                favoriteIds = if (nextFavorite) {
                                    favoriteIds + loadedChannel.id
                                } else {
                                    favoriteIds - loadedChannel.id
                                }
                                favorite = nextFavorite
                                container.applicationScope.launch {
                                    container.m3uFavoritesRepository.setFavorite(
                                        profileId = m3uProfile.id,
                                        channelId = loadedChannel.id,
                                        favorite = nextFavorite,
                                    )
                                }
                            },
                        ) {
                            Icon(
                                if (favorite) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                contentDescription = if (favorite) "取消收藏" else "收藏频道",
                            )
                        }
                        IconButton(onClick = {
                            fullscreenRequested = true
                            requestFullscreenOrientation(activity, fullscreenModePreference)
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "全屏播放")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (playerFullscreen) Modifier else Modifier.padding(padding))
                .background(MaterialTheme.colorScheme.background),
        ) {
            val loadedChannel = channel
            when {
                error != null -> ErrorPane(message = error?.message ?: "直播频道加载失败", modifier = Modifier.fillMaxSize())
                loadedChannel == null -> LoadingPane(Modifier.fillMaxSize())
                else -> {
                    M3uPlayerContent(
                        channel = loadedChannel,
                        channels = channels,
                        playerFullscreen = playerFullscreen,
                        onFullscreenRequest = {
                            if (playerFullscreen) {
                                fullscreenRequested = false
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                            } else {
                                fullscreenRequested = true
                                requestFullscreenOrientation(activity, fullscreenModePreference)
                            }
                        },
                        onSelectChannel = { nextChannel ->
                            channel = nextChannel
                            favorite = nextChannel.id in favoriteIds
                            error = null
                        },
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun M3uPlayerContent(
    channel: M3uChannel,
    channels: List<M3uChannel>,
    playerFullscreen: Boolean,
    onFullscreenRequest: () -> Unit,
    onSelectChannel: (M3uChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (playerFullscreen) {
        MediaTreePlayer(
            playbackSource = PlaybackSource.m3u(channel.streamUrl),
            startPosition = 0.0,
            isFullscreen = true,
            showFullscreenButton = true,
            showAspectRatioControls = true,
            onFullscreenRequest = onFullscreenRequest,
            modifier = modifier.fillMaxSize(),
        )
    } else {
        Column(
            modifier = modifier,
        ) {
            MediaTreePlayer(
                playbackSource = PlaybackSource.m3u(channel.streamUrl),
                startPosition = 0.0,
                isFullscreen = false,
                showFullscreenButton = true,
                showAspectRatioControls = false,
                onFullscreenRequest = onFullscreenRequest,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Spacer(Modifier.height(14.dp))
            M3uChannelDetails(
                channel = channel,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp),
            )
            Spacer(Modifier.height(12.dp))
            M3uChannelSwitcher(
                channels = channels,
                currentChannel = channel,
                onSelect = onSelectChannel,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 18.dp),
            )
        }
    }
}

@Composable
private fun M3uChannelSwitcher(
    channels: List<M3uChannel>,
    currentChannel: M3uChannel,
    onSelect: (M3uChannel) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex = channels.indexOfFirst { it.id == currentChannel.id }
    if (channels.isEmpty() || currentIndex < 0) return

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "频道 ${currentIndex + 1}/${channels.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(108.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentPadding = PaddingValues(vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(channels, key = { it.id }) { candidate ->
                val selected = candidate.id == currentChannel.id
                M3uChannelMiniCard(
                    channel = candidate,
                    selected = selected,
                    onClick = { if (!selected) onSelect(candidate) },
                )
            }
        }
    }
}

@Composable
private fun M3uChannelMiniCard(
    channel: M3uChannel,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Card(
            modifier = Modifier.shapeAwareClickable(shape = shape, onClick = onClick),
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = if (selected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                } else {
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.72f)
                },
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 10f)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)),
                contentAlignment = Alignment.Center,
            ) {
                if (channel.logoUrl.isNotBlank()) {
                    MediaAsyncImage(
                        imageUrl = channel.logoUrl,
                        contentDescription = channel.name,
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                    )
                } else {
                    Icon(
                        Icons.Default.LiveTv,
                        contentDescription = null,
                        tint = if (selected) {
                            MaterialTheme.colorScheme.onPrimaryContainer
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                        modifier = Modifier.size(28.dp),
                    )
                }
                if (selected) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(5.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(999.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    ) {
                        Text(
                            text = "播放中",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
        Text(
            text = channel.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = channel.group.ifBlank { "直播频道" },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun M3uChannelDetails(
    channel: M3uChannel,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = channel.name,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = channel.group.ifBlank { "直播频道" },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Text(
            text = channel.streamUrl,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

package com.zasenjc.mediatree.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SyncProblem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zasenjc.mediatree.playback.PlaybackSource
import com.zasenjc.mediatree.playback.PlaybackSubtitleTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SourceSwapCurtainFadeInMillis = 120
private const val SourceSwapCurtainFadeOutMillis = 220
private const val OverlayAutoHideMillis = 3_500L
private const val HudAutoHideMillis = 1_500L
private const val LockedButtonAutoHideMillis = 5_000L

private val PlayerSpeeds = listOf(0.75, 1.0, 1.25, 1.5, 2.0)
private val AspectRatioOptions = listOf(
    PlayerMenuOption("no", "默认"),
    PlayerMenuOption("16:9", "16:9"),
    PlayerMenuOption("4:3", "4:3"),
    PlayerMenuOption("2.35:1", "2.35:1"),
)

enum class PlayerDoubleTapAction {
    Rewind,
    TogglePlay,
    Forward,
}

fun playerDoubleTapAction(tapX: Float, width: Int): PlayerDoubleTapAction {
    if (width <= 0) return PlayerDoubleTapAction.TogglePlay
    val sideZone = width * 0.3f
    return when {
        tapX < sideZone -> PlayerDoubleTapAction.Rewind
        tapX > width - sideZone -> PlayerDoubleTapAction.Forward
        else -> PlayerDoubleTapAction.TogglePlay
    }
}

data class PlaybackPositionSnapshot(
    val positionSeconds: Double,
    val durationSeconds: Double,
)

@Composable
fun MediaTreePlayer(
    playbackSource: PlaybackSource,
    startPosition: Double,
    selectedSubtitle: Int = -1,
    onProgressUpdate: (position: Double, duration: Double) -> Unit = { _, _ -> },
    onPlaybackComplete: (position: Double, duration: Double) -> Unit = { _, _ -> },
    onPlaybackPositionChange: (position: Double, duration: Double) -> Unit = { _, _ -> },
    onPlaybackPositionSnapshot: ((() -> PlaybackPositionSnapshot?) -> Unit)? = null,
    showFullscreenButton: Boolean = false,
    isFullscreen: Boolean = false,
    showAspectRatioControls: Boolean = false,
    onFullscreenRequest: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val appContext = context.applicationContext
    val controller = remember(appContext) { MpvPlayerController(appContext) }

    var isPlaying by remember { mutableStateOf(false) }
    var positionSeconds by remember { mutableDoubleStateOf(startPosition.coerceAtLeast(0.0)) }
    var durationSeconds by remember { mutableDoubleStateOf(0.0) }
    var completedReported by remember(playbackSource) { mutableStateOf(false) }
    var hudMessage by remember { mutableStateOf("") }
    var showOverlay by remember { mutableStateOf(false) }
    var playerLocked by remember { mutableStateOf(false) }
    var showLockedButton by remember { mutableStateOf(false) }
    var playbackSpeed by remember { mutableDoubleStateOf(1.0) }
    var selectedAspectRatio by remember { mutableStateOf("no") }
    var selectedAudioTrackId by remember { mutableStateOf("") }
    var audioTracks by remember { mutableStateOf<List<MpvTrackOption>>(emptyList()) }
    var playbackError by remember { mutableStateOf<String?>(null) }
    var curtainVisible by remember { mutableStateOf(true) }
    val curtainAlpha by animateFloatAsState(
        targetValue = if (curtainVisible) 1f else 0f,
        animationSpec = tween(
            durationMillis = if (curtainVisible) {
                SourceSwapCurtainFadeInMillis
            } else {
                SourceSwapCurtainFadeOutMillis
            },
        ),
        label = "sourceSwapCurtainAlpha",
    )

    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    LaunchedEffect(playbackSource.uri, playbackSource.headers) {
        curtainVisible = true
        playbackError = null
        delay(SourceSwapCurtainFadeInMillis.toLong())
        runCatching {
            controller.loadUrl(
                url = playbackSource.uri,
                headers = playbackSource.headers,
                startPositionSeconds = startPosition,
            )
            controller.play()
            isPlaying = true
        }.onFailure {
            playbackError = it.message ?: "播放器启动失败"
            isPlaying = false
        }
        curtainVisible = false
    }

    DisposableEffect(controller, playbackSource, selectedSubtitle) {
        val subtitleUri = if (selectedSubtitle >= 0) {
            playbackSource.subtitleUri(selectedSubtitle)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        runCatching {
            if (subtitleUri == null) {
                controller.clearSubtitle()
            } else {
                controller.selectSubtitle(subtitleUri)
            }
        }.onFailure { playbackError = it.message ?: "字幕切换失败" }
        onDispose { }
    }

    LaunchedEffect(controller) {
        while (isActive) {
            delay(1_000)
            positionSeconds = controller.positionSeconds().coerceAtLeast(0.0)
            durationSeconds = controller.durationSeconds().coerceAtLeast(0.0)
            onPlaybackPositionChange(positionSeconds, durationSeconds)
            playbackError = controller.lastError() ?: playbackError
            audioTracks = controller.audioTrackOptions()
            if (!completedReported && controller.isEnded()) {
                completedReported = true
                onPlaybackComplete(positionSeconds, durationSeconds)
            }
        }
    }

    DisposableEffect(controller, onPlaybackPositionSnapshot) {
        onPlaybackPositionSnapshot?.invoke {
            PlaybackPositionSnapshot(
                positionSeconds = controller.positionSeconds().coerceAtLeast(0.0),
                durationSeconds = controller.durationSeconds().coerceAtLeast(0.0),
            )
        }
        onDispose {
            onPlaybackPositionSnapshot?.invoke { null }
        }
    }

    LaunchedEffect(controller) {
        while (isActive) {
            delay(15_000)
            if (positionSeconds > 0) {
                onProgressUpdate(positionSeconds, durationSeconds)
            }
        }
    }

    LaunchedEffect(playerLocked, showLockedButton) {
        if (playerLocked && showLockedButton) {
            delay(LockedButtonAutoHideMillis)
            showLockedButton = false
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(playerLocked) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (playerLocked) return@detectVerticalDragGestures
                    val halfWidth = size.width / 2f
                    val ratio = (dragAmount / size.height).coerceIn(-0.5f, 0.5f)
                    if (change.position.x < halfWidth) {
                        activity?.let { act ->
                            val attrs = act.window.attributes
                            attrs.screenBrightness = (attrs.screenBrightness - ratio).coerceIn(0.01f, 1f)
                            act.window.attributes = attrs
                            hudMessage = "亮度: ${(attrs.screenBrightness * 100).toInt()}%"
                        }
                    } else {
                        val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                        val maxVol = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC) ?: 15
                        val currentVol = audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 7
                        val newVol = (currentVol - (ratio * maxVol).toInt()).coerceIn(0, maxVol)
                        audio?.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                        hudMessage = "音量: ${(newVol * 100 / maxVol)}%"
                    }
                }
            }
            .pointerInput(playerLocked) {
                detectTapGestures(
                    onTap = { showOverlay = !showOverlay },
                    onDoubleTap = { offset ->
                        if (playerLocked) {
                            showLockedButton = true
                            return@detectTapGestures
                        }
                        when (playerDoubleTapAction(tapX = offset.x, width = size.width)) {
                            PlayerDoubleTapAction.Rewind -> {
                                controller.seekBy(-10.0)
                                hudMessage = "快退 10 秒"
                            }
                            PlayerDoubleTapAction.TogglePlay -> {
                                if (isPlaying) {
                                    controller.pause()
                                    isPlaying = false
                                    hudMessage = "暂停"
                                } else {
                                    controller.play()
                                    isPlaying = true
                                    hudMessage = "播放"
                                }
                            }
                            PlayerDoubleTapAction.Forward -> {
                                controller.seekBy(10.0)
                                hudMessage = "快进 10 秒"
                            }
                        }
                        showOverlay = true
                    },
                )
            },
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { MpvPlayerView(it).apply { this.controller = controller } },
            update = { view -> view.controller = controller },
        )

        if (curtainAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = curtainAlpha)),
            )
        }

        if (hudMessage.isNotBlank()) {
            LaunchedEffect(hudMessage) {
                delay(HudAutoHideMillis)
                hudMessage = ""
            }
        }

        if (showOverlay) {
            LaunchedEffect(showOverlay, playerLocked) {
                if (!playerLocked) {
                    delay(OverlayAutoHideMillis)
                    showOverlay = false
                }
            }

            PlayerControlsOverlay(
                isPlaying = isPlaying,
                playerLocked = playerLocked,
                positionSeconds = positionSeconds,
                durationSeconds = durationSeconds,
                playbackSpeed = playbackSpeed,
                selectedSubtitle = selectedSubtitle,
                subtitleTracks = playbackSource.subtitleTracks,
                audioTracks = audioTracks,
                selectedAudioTrackId = selectedAudioTrackId,
                selectedAspectRatio = selectedAspectRatio,
                showFullscreenButton = showFullscreenButton,
                isFullscreen = isFullscreen,
                showAspectRatioControls = showAspectRatioControls,
                onToggleLock = {
                    playerLocked = true
                    showLockedButton = true
                    showOverlay = true
                },
                onTogglePlay = {
                    if (isPlaying) {
                        controller.pause()
                        isPlaying = false
                    } else {
                        controller.play()
                        isPlaying = true
                    }
                },
                onSpeedChange = { speed ->
                    playbackSpeed = speed
                    controller.setPlaybackSpeed(speed)
                    hudMessage = "${speed.formatSpeed()}x"
                    showOverlay = true
                },
                onSubtitleChange = { option ->
                    if (option == null) {
                        controller.clearSubtitle()
                        hudMessage = "字幕关闭"
                    } else {
                        playbackSource.subtitleUri(option.index)?.takeIf { it.isNotBlank() }?.let(controller::selectSubtitle)
                        hudMessage = option.subtitleLabel()
                    }
                    showOverlay = true
                },
                onAudioTrackChange = { option ->
                    selectedAudioTrackId = option.id
                    controller.selectAudioTrack(option.id)
                    hudMessage = option.label
                    showOverlay = true
                },
                onAspectRatioChange = { option ->
                    selectedAspectRatio = option.id
                    controller.setAspectRatio(option.id)
                    hudMessage = "画面 ${option.label}"
                    showOverlay = true
                },
                onFullscreenRequest = onFullscreenRequest,
                modifier = Modifier.fillMaxSize(),
            )
        }

        if (playerLocked && showLockedButton && !showOverlay) {
            PlayerLock(playerLocked = playerLocked, modifier = Modifier.align(Alignment.TopEnd).padding(10.dp)) {
                playerLocked = false
                showLockedButton = false
                showOverlay = true
                hudMessage = "控制已解锁"
            }
        }

        if (hudMessage.isNotBlank()) {
            Text(
                hudMessage,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.66f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color.White,
                fontSize = 14.sp,
            )
        }

        playbackError?.let { message ->
            PlayerErrorOverlay(message = message, onDismiss = { playbackError = null })
        }
    }
}

@Composable
private fun PlayerControlsOverlay(
    isPlaying: Boolean,
    playerLocked: Boolean,
    positionSeconds: Double,
    durationSeconds: Double,
    playbackSpeed: Double,
    selectedSubtitle: Int,
    subtitleTracks: List<PlaybackSubtitleTrack>,
    audioTracks: List<MpvTrackOption>,
    selectedAudioTrackId: String,
    selectedAspectRatio: String,
    showFullscreenButton: Boolean,
    isFullscreen: Boolean,
    showAspectRatioControls: Boolean,
    onToggleLock: () -> Unit,
    onTogglePlay: () -> Unit,
    onSpeedChange: (Double) -> Unit,
    onSubtitleChange: (PlaybackSubtitleTrack?) -> Unit,
    onAudioTrackChange: (MpvTrackOption) -> Unit,
    onAspectRatioChange: (PlayerMenuOption) -> Unit,
    onFullscreenRequest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.background(Color.Black.copy(alpha = 0.22f))) {
        Row(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LockButton(playerLocked = playerLocked, onToggleLock = onToggleLock)
        }

        if (!playerLocked) {
            IconButton(
                onClick = onTogglePlay,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.36f), RoundedCornerShape(24.dp)),
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "暂停" else "播放",
                    tint = Color.White,
                    modifier = Modifier.size(36.dp),
                )
            }

            Column(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 14.dp, vertical = 12.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(formatTime(positionSeconds), color = Color.White, fontSize = 11.sp)
                    Text(formatTime(durationSeconds), color = Color.White, fontSize = 11.sp)
                }
                LinearProgressIndicator(
                    progress = {
                        playbackProgress(positionSeconds, durationSeconds)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.28f),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlaybackSpeedMenu(selectedSpeed = playbackSpeed, onSpeedChange = onSpeedChange)
                    SubtitleTrackMenu(
                        selectedSubtitle = selectedSubtitle,
                        tracks = subtitleTracks,
                        onSubtitleChange = onSubtitleChange,
                    )
                    AudioTrackMenu(
                        selectedAudioTrackId = selectedAudioTrackId,
                        tracks = audioTracks,
                        onAudioTrackChange = onAudioTrackChange,
                    )
                    if (showAspectRatioControls) {
                        AspectRatioMenu(selectedAspectRatio = selectedAspectRatio, onAspectRatioChange = onAspectRatioChange)
                    }
                    Spacer(Modifier.weight(1f))
                    if (showFullscreenButton || isFullscreen) {
                        FullscreenControl(isFullscreen = isFullscreen, onFullscreenRequest = onFullscreenRequest)
                    }
                }
            }
        }
    }
}

@Composable
private fun LockButton(playerLocked: Boolean, onToggleLock: () -> Unit) {
    IconButton(
        onClick = onToggleLock,
        modifier = Modifier
            .size(40.dp),
    ) {
        Icon(
            if (playerLocked) Icons.Default.Lock else Icons.Default.LockOpen,
            contentDescription = if (playerLocked) "解锁播放器" else "锁定播放器",
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun FullscreenControl(isFullscreen: Boolean, onFullscreenRequest: () -> Unit) {
    PlayerMenuChip(
        icon = {
            Icon(
                if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                contentDescription = null,
            )
        },
        label = if (isFullscreen) "退出全屏" else "全屏",
        expanded = false,
        onExpandedChange = { onFullscreenRequest() },
    ) {
    }
}

@Composable
private fun PlayerLock(playerLocked: Boolean, modifier: Modifier = Modifier, onToggle: () -> Unit) {
    if (playerLocked) {
        Box(modifier) {
            LockButton(
                playerLocked = true,
                onToggleLock = onToggle,
            )
        }
    }
}

@Composable
private fun PlaybackSpeedMenu(selectedSpeed: Double, onSpeedChange: (Double) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    PlayerMenuChip(
        icon = { Icon(Icons.Default.Speed, contentDescription = null) },
        label = "${selectedSpeed.formatSpeed()}x",
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        PlayerSpeeds.forEach { speed ->
            DropdownMenuItem(
                text = { Text("${speed.formatSpeed()}x") },
                onClick = {
                    expanded = false
                    onSpeedChange(speed)
                },
            )
        }
    }
}

@Composable
private fun SubtitleTrackMenu(
    selectedSubtitle: Int,
    tracks: List<PlaybackSubtitleTrack>,
    onSubtitleChange: (PlaybackSubtitleTrack?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    PlayerMenuChip(
        icon = { Icon(Icons.Default.Subtitles, contentDescription = null) },
        label = "字幕",
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        DropdownMenuItem(
            text = { Text("关闭字幕") },
            onClick = {
                expanded = false
                onSubtitleChange(null)
            },
        )
        tracks.forEach { track ->
            DropdownMenuItem(
                text = { Text(track.subtitleLabel()) },
                onClick = {
                    expanded = false
                    onSubtitleChange(track)
                },
            )
        }
    }
}

@Composable
private fun AudioTrackMenu(
    selectedAudioTrackId: String,
    tracks: List<MpvTrackOption>,
    onAudioTrackChange: (MpvTrackOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = tracks.firstOrNull { it.id == selectedAudioTrackId }?.label ?: "音轨"
    PlayerMenuChip(
        icon = { Icon(Icons.Default.Audiotrack, contentDescription = null) },
        label = label,
        expanded = expanded,
        onExpandedChange = { expanded = it },
        enabled = tracks.isNotEmpty(),
    ) {
        tracks.forEach { track ->
            DropdownMenuItem(
                text = { Text(track.label) },
                onClick = {
                    expanded = false
                    onAudioTrackChange(track)
                },
            )
        }
    }
}

@Composable
private fun AspectRatioMenu(
    selectedAspectRatio: String,
    onAspectRatioChange: (PlayerMenuOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val label = AspectRatioOptions.firstOrNull { it.id == selectedAspectRatio }?.label ?: "默认"
    PlayerMenuChip(
        icon = { Icon(Icons.Default.AspectRatio, contentDescription = null) },
        label = label,
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        AspectRatioOptions.forEach { option ->
            DropdownMenuItem(
                text = { Text(option.label) },
                onClick = {
                    expanded = false
                    onAspectRatioChange(option)
                },
            )
        }
    }
}

@Composable
private fun PlayerMenuChip(
    icon: @Composable () -> Unit,
    label: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box {
        AssistChip(
            enabled = enabled,
            onClick = { onExpandedChange(true) },
            leadingIcon = {
                Box(Modifier.size(16.dp), contentAlignment = Alignment.Center) {
                    icon()
                }
            },
            label = {
                Text(
                    label,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) },
        ) {
            content()
        }
    }
}

@Composable
private fun PlayerErrorOverlay(message: String, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.58f))
            .clickable(onClick = onDismiss),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .background(Color.Black.copy(alpha = 0.72f), RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.SyncProblem, contentDescription = null, tint = Color.White)
            Spacer(Modifier.width(10.dp))
            Column {
                Text("播放异常", color = Color.White, fontSize = 14.sp)
                Spacer(Modifier.height(2.dp))
                Text(message, color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp, maxLines = 3)
            }
        }
    }
}

private data class PlayerMenuOption(
    val id: String,
    val label: String,
)

private fun PlaybackSubtitleTrack.subtitleLabel(): String =
    title.ifBlank { language.ifBlank { "字幕 $index" } }

private fun Double.formatSpeed(): String =
    if (this % 1.0 == 0.0) toInt().toString() else toString().trimEnd('0').trimEnd('.')

fun playbackProgress(positionSeconds: Double, durationSeconds: Double): Float {
    if (!positionSeconds.isFinite() || !durationSeconds.isFinite() || durationSeconds <= 0.0) return 0f
    return (positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f)
}

private fun formatTime(seconds: Double): String {
    val totalSec = seconds.toLong().coerceAtLeast(0)
    val min = totalSec / 60
    val sec = totalSec % 60
    return "${min}:${sec.toString().padStart(2, '0')}"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

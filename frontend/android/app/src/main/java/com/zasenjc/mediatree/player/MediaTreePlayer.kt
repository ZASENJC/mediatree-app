package com.zasenjc.mediatree.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.view.WindowManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.zasenjc.mediatree.playback.PlaybackSource
import com.zasenjc.mediatree.playback.PlaybackSubtitleTrack
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SourceSwapCurtainFadeInMillis = 120
private const val SourceSwapCurtainFadeOutMillis = 220
private const val PlaybackMemorySaveIntervalMillis = 5_000L
private const val OverlayAutoHideMillis = 3_500L
private const val HudAutoHideMillis = 1_500L
private const val LockedButtonAutoHideMillis = 5_000L
private const val HorizontalSeekSecondsPerScreen = 90.0
private const val DoubleTapSideZoneFraction = 0.22f

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
    val sideZone = width * DoubleTapSideZoneFraction
    return when {
        tapX < sideZone -> PlayerDoubleTapAction.Rewind
        tapX > width - sideZone -> PlayerDoubleTapAction.Forward
        else -> PlayerDoubleTapAction.TogglePlay
    }
}

data class PlaybackPositionSnapshot(
    val positionSeconds: Double,
    val durationSeconds: Double,
    val percentPosition: Double = 0.0,
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
    val lifecycleOwner = LocalLifecycleOwner.current
    val controller = remember(appContext) { MpvPlayerController(appContext) }

    var isPlaying by remember { mutableStateOf(false) }
    var positionSeconds by remember { mutableDoubleStateOf(startPosition.coerceAtLeast(0.0)) }
    var durationSeconds by remember { mutableDoubleStateOf(0.0) }
    var percentPosition by remember { mutableDoubleStateOf(0.0) }
    var seekingPositionSeconds by remember { mutableStateOf<Double?>(null) }
    var tickCount by remember { mutableStateOf(0) }
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
    val horizontalSeekHandler by rememberUpdatedState(
        newValue = { deltaSeconds: Double ->
            if (playerLocked || deltaSeconds == 0.0) {
                Unit
            } else if (durationSeconds > 0.0) {
                val target = (positionSeconds + deltaSeconds).coerceIn(0.0, durationSeconds)
                controller.seekTo(target)
                seekingPositionSeconds = null
                positionSeconds = target
                percentPosition = playbackPercent(target, durationSeconds, percentPosition)
                hudMessage = seekHudMessage(deltaSeconds, target, durationSeconds)
                showOverlay = true
                onProgressUpdate(target, durationSeconds)
            } else {
                controller.seekBy(deltaSeconds)
                val target = (positionSeconds + deltaSeconds).coerceAtLeast(0.0)
                positionSeconds = target
                hudMessage = relativeSeekHudMessage(deltaSeconds)
                showOverlay = true
                onProgressUpdate(target, durationSeconds)
            }
        },
    )

    DisposableEffect(controller) {
        onDispose {
            controller.release()
        }
    }

    DisposableEffect(activity) {
        val window = activity?.window
        if (window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            if (window != null) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            }
        }
    }

    DisposableEffect(lifecycleOwner, controller) {
        val lifecycle = lifecycleOwner.lifecycle
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE,
                Lifecycle.Event.ON_STOP,
                -> {
                    controller.pause()
                    isPlaying = false
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(playbackSource.uri, playbackSource.headers) {
        curtainVisible = true
        playbackError = null
        positionSeconds = startPosition.coerceAtLeast(0.0)
        durationSeconds = 0.0
        percentPosition = 0.0
        seekingPositionSeconds = null
        completedReported = false
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
            delay(500)
            tickCount += 1
            val controllerPosition = controller.positionSeconds().coerceAtLeast(0.0)
            val controllerDuration = controller.durationSeconds().coerceAtLeast(0.0)
            val controllerPercent = controller.percentPosition().coerceIn(0.0, 100.0)
            val resolvedPosition = if (controllerPosition > 0.0) {
                controllerPosition
            } else if (controllerDuration > 0.0 && controllerPercent > 0.0) {
                controllerDuration * controllerPercent / 100.0
            } else {
                controllerPosition
            }
            if (seekingPositionSeconds == null) {
                positionSeconds = resolvedPosition
            }
            if (controllerDuration > 0.0) {
                durationSeconds = controllerDuration
            }
            percentPosition = playbackPercent(
                positionSeconds = seekingPositionSeconds ?: positionSeconds,
                durationSeconds = durationSeconds,
                fallbackPercent = controllerPercent,
            )
            onPlaybackPositionChange(positionSeconds, durationSeconds)
            if (tickCount % 3 == 1) {
                playbackError = controller.lastError() ?: playbackError
            }
            if (showOverlay && tickCount % 5 == 1) {
                audioTracks = controller.audioTrackOptions()
            }
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
                percentPosition = controller.percentPosition().coerceIn(0.0, 100.0),
            )
        }
        onDispose {
            onPlaybackPositionSnapshot?.invoke { null }
        }
    }

    LaunchedEffect(controller) {
        while (isActive) {
            delay(PlaybackMemorySaveIntervalMillis)
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
            .background(Color.Black),
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { MpvPlayerView(it).apply { this.controller = controller } },
            update = { view -> view.controller = controller },
        )

        PlayerGestureLayer(
            playerLocked = playerLocked,
            onHorizontalSeek = horizontalSeekHandler,
            onVerticalDrag = { x, dragAmount, width, height ->
                if (playerLocked) return@PlayerGestureLayer
                val halfWidth = width / 2f
                val ratio = (dragAmount / height).coerceIn(-0.5f, 0.5f)
                if (x < halfWidth) {
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
            },
            onTap = { showOverlay = !showOverlay },
            onDoubleTap = { tapX, width ->
                if (playerLocked) {
                    showLockedButton = true
                    return@PlayerGestureLayer
                }
                when (playerDoubleTapAction(tapX = tapX, width = width)) {
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
            modifier = Modifier.fillMaxSize(),
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
                positionSeconds = seekingPositionSeconds ?: positionSeconds,
                durationSeconds = durationSeconds,
                percentPosition = percentPosition,
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
                onSeekPreview = { target ->
                    seekingPositionSeconds = target
                    positionSeconds = target
                    percentPosition = playbackPercent(target, durationSeconds, percentPosition)
                    showOverlay = true
                },
                onSeekCommit = { target ->
                    controller.seekTo(target)
                    seekingPositionSeconds = null
                    positionSeconds = target
                    percentPosition = playbackPercent(target, durationSeconds, percentPosition)
                    hudMessage = formatTime(target)
                    showOverlay = true
                    onProgressUpdate(target, durationSeconds)
                },
                onAudioMenuOpen = {
                    audioTracks = controller.audioTrackOptions()
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
private fun PlayerGestureLayer(
    playerLocked: Boolean,
    onHorizontalSeek: (Double) -> Unit,
    onVerticalDrag: (x: Float, dragAmount: Float, width: Int, height: Int) -> Unit,
    onTap: () -> Unit,
    onDoubleTap: (tapX: Float, width: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .pointerInput(playerLocked) {
                detectHorizontalDragGestures { change, dragAmount ->
                    if (playerLocked) return@detectHorizontalDragGestures
                    change.consume()
                    onHorizontalSeek(horizontalSeekDeltaSeconds(dragAmount, size.width))
                }
            }
            .pointerInput(playerLocked) {
                detectVerticalDragGestures { change, dragAmount ->
                    if (playerLocked) return@detectVerticalDragGestures
                    onVerticalDrag(change.position.x, dragAmount, size.width, size.height)
                }
            }
            .pointerInput(playerLocked) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { offset -> onDoubleTap(offset.x, size.width) },
                )
            },
    )
}

@Composable
private fun PlayerControlsOverlay(
    isPlaying: Boolean,
    playerLocked: Boolean,
    positionSeconds: Double,
    durationSeconds: Double,
    percentPosition: Double,
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
    onSeekPreview: (Double) -> Unit,
    onSeekCommit: (Double) -> Unit,
    onAudioMenuOpen: () -> Unit,
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
                PlayerSeekBar(
                    positionSeconds = positionSeconds,
                    durationSeconds = durationSeconds,
                    percentPosition = percentPosition,
                    onSeekPreview = onSeekPreview,
                    onSeekCommit = onSeekCommit,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    PlayPauseControl(isPlaying = isPlaying, onTogglePlay = onTogglePlay)
                    PlaybackSpeedMenu(selectedSpeed = playbackSpeed, onSpeedChange = onSpeedChange)
                    SubtitleTrackMenu(
                        selectedSubtitle = selectedSubtitle,
                        tracks = subtitleTracks,
                        onSubtitleChange = onSubtitleChange,
                    )
                    AudioTrackMenu(
                        selectedAudioTrackId = selectedAudioTrackId,
                        tracks = audioTracks,
                        onAudioMenuOpen = onAudioMenuOpen,
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
@OptIn(ExperimentalMaterial3Api::class)
private fun PlayerSeekBar(
    positionSeconds: Double,
    durationSeconds: Double,
    percentPosition: Double,
    onSeekPreview: (Double) -> Unit,
    onSeekCommit: (Double) -> Unit,
    modifier: Modifier = Modifier,
) {
    var pendingSeekSeconds by remember { mutableDoubleStateOf(Double.NaN) }
    val dragging = pendingSeekSeconds.isFinite()
    val displayPosition = if (dragging) pendingSeekSeconds else positionSeconds
    Slider(
        value = playbackProgress(displayPosition, durationSeconds, percentPosition),
        onValueChange = { progress ->
            if (durationSeconds > 0.0) {
                val target = progress.coerceIn(0f, 1f) * durationSeconds
                pendingSeekSeconds = target
                onSeekPreview(target)
            }
        },
        onValueChangeFinished = {
            if (pendingSeekSeconds.isFinite()) {
                onSeekCommit(pendingSeekSeconds)
                pendingSeekSeconds = Double.NaN
            }
        },
        enabled = durationSeconds > 0.0,
        modifier = modifier.height(20.dp),
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = Color.White,
            inactiveTrackColor = Color.White.copy(alpha = 0.28f),
            disabledThumbColor = Color.White.copy(alpha = 0.48f),
            disabledActiveTrackColor = Color.White.copy(alpha = 0.48f),
            disabledInactiveTrackColor = Color.White.copy(alpha = 0.22f),
        ),
        thumb = {
            Box(Modifier.offset(y = 4.dp).offset { IntOffset(0, -3) }.size(12.dp).background(Color.White, CircleShape))
        },
        track = { sliderState ->
            ThinPlayerSliderTrack(progress = sliderState.value, enabled = durationSeconds > 0.0)
        },
    )
}

@Composable
private fun ThinPlayerSliderTrack(
    progress: Float,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val activeColor = if (enabled) Color.White else Color.White.copy(alpha = 0.48f)
    val trackColor = if (enabled) Color.White.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.22f)
    Box(
        modifier = modifier.height(12.dp).fillMaxWidth(),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .height(2.dp)
                .fillMaxWidth()
                .background(trackColor, RoundedCornerShape(50)),
        )
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(2.dp)
                .background(activeColor, RoundedCornerShape(50)),
        )
    }
}

@Composable
private fun PlayPauseControl(isPlaying: Boolean, onTogglePlay: () -> Unit) {
    IconButton(
        onClick = onTogglePlay,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "暂停" else "播放",
            tint = Color.White,
            modifier = Modifier.size(24.dp),
        )
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
    IconButton(
        onClick = onFullscreenRequest,
        modifier = Modifier.size(40.dp),
    ) {
        Icon(
            imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
            contentDescription = if (isFullscreen) "退出全屏" else "全屏",
            tint = Color.White,
            modifier = Modifier.size(21.dp),
        )
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
    PlayerIconMenuButton(
        imageVector = Icons.Default.Speed,
        contentDescription = "播放速度",
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        PlayerSpeeds.forEach { speed ->
            DropdownMenuItem(
                text = { Text(if (speed == selectedSpeed) "${speed.formatSpeed()}x · 当前" else "${speed.formatSpeed()}x") },
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
    PlayerIconMenuButton(
        imageVector = Icons.Default.Subtitles,
        contentDescription = "字幕",
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        DropdownMenuItem(
            text = { Text(if (selectedSubtitle < 0) "关闭字幕 · 当前" else "关闭字幕") },
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
    onAudioMenuOpen: () -> Unit,
    onAudioTrackChange: (MpvTrackOption) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    PlayerIconMenuButton(
        imageVector = Icons.Default.Audiotrack,
        contentDescription = "音轨",
        expanded = expanded,
        onExpandedChange = {
            if (it) onAudioMenuOpen()
            expanded = it
        },
        enabled = true,
    ) {
        tracks.forEach { track ->
            DropdownMenuItem(
                text = { Text(if (track.id == selectedAudioTrackId) "${track.label} · 当前" else track.label) },
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
    PlayerIconMenuButton(
        imageVector = Icons.Default.AspectRatio,
        contentDescription = "画面比例",
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        AspectRatioOptions.forEach { option ->
            DropdownMenuItem(
                text = { Text(if (option.id == selectedAspectRatio) "${option.label} · 当前" else option.label) },
                onClick = {
                    expanded = false
                    onAspectRatioChange(option)
                },
            )
        }
    }
}

@Composable
private fun PlayerIconMenuButton(
    imageVector: ImageVector,
    contentDescription: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit,
) {
    Box {
        IconButton(
            enabled = enabled,
            onClick = { onExpandedChange(true) },
            modifier = Modifier.size(40.dp),
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = contentDescription,
                tint = Color.White,
                modifier = Modifier.size(21.dp),
            )
        }
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

fun playbackProgress(
    positionSeconds: Double,
    durationSeconds: Double,
    percentPosition: Double = 0.0,
): Float = (playbackPercent(positionSeconds, durationSeconds, percentPosition) / 100.0).toFloat().coerceIn(0f, 1f)

fun playbackPercent(
    positionSeconds: Double,
    durationSeconds: Double,
    fallbackPercent: Double = 0.0,
): Double {
    if (positionSeconds.isFinite() && durationSeconds.isFinite() && durationSeconds > 0.0) {
        return (positionSeconds / durationSeconds * 100.0).coerceIn(0.0, 100.0)
    }
    if (!fallbackPercent.isFinite() || fallbackPercent <= 0.0) return 0.0
    return fallbackPercent.coerceIn(0.0, 100.0)
}

fun horizontalSeekDeltaSeconds(dragAmountPx: Float, widthPx: Int): Double {
    if (widthPx <= 0) return 0.0
    return dragAmountPx.toDouble() / widthPx.toDouble() * HorizontalSeekSecondsPerScreen
}

private fun seekHudMessage(deltaSeconds: Double, targetSeconds: Double, durationSeconds: Double): String {
    val sign = if (deltaSeconds >= 0.0) "+" else "-"
    return "$sign${formatTime(kotlin.math.abs(deltaSeconds))}  ${formatTime(targetSeconds)} / ${formatTime(durationSeconds)}"
}

private fun relativeSeekHudMessage(deltaSeconds: Double): String {
    val sign = if (deltaSeconds >= 0.0) "+" else "-"
    return "$sign${formatTime(kotlin.math.abs(deltaSeconds))}"
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

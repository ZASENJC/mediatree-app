package com.zasenjc.mediatree.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Forward10
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.zasenjc.mediatree.playback.PlaybackSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val SourceSwapCurtainFadeInMillis = 120
private const val SourceSwapCurtainFadeOutMillis = 220

@Composable
fun MediaTreePlayer(
    playbackSource: PlaybackSource,
    startPosition: Double,
    selectedSubtitle: Int = -1,
    onProgressUpdate: (position: Double, duration: Double) -> Unit = { _, _ -> },
    onPlaybackComplete: (position: Double, duration: Double) -> Unit = { _, _ -> },
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

    LaunchedEffect(playbackSource) {
        curtainVisible = true
        delay(SourceSwapCurtainFadeInMillis.toLong())
        controller.loadUrl(
            url = playbackSource.uri,
            headers = playbackSource.headers,
            startPositionSeconds = startPosition,
        )
        controller.play()
        isPlaying = true
        curtainVisible = false
    }

    DisposableEffect(playbackSource, selectedSubtitle) {
        val subtitleUri = if (selectedSubtitle >= 0) {
            playbackSource.subtitleUri(selectedSubtitle)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        if (subtitleUri == null) {
            controller.clearSubtitle()
        } else {
            controller.selectSubtitle(subtitleUri)
        }
        onDispose { }
    }

    LaunchedEffect(controller) {
        while (isActive) {
            delay(1_000)
            positionSeconds = controller.positionSeconds().coerceAtLeast(0.0)
            durationSeconds = controller.durationSeconds().coerceAtLeast(0.0)
            if (!completedReported && controller.isEnded()) {
                completedReported = true
                onPlaybackComplete(positionSeconds, durationSeconds)
            }
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

    Box(
        modifier = modifier
            .background(Color.Black)
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
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
            .pointerInput(Unit) {
                detectTapGestures { showOverlay = !showOverlay }
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
                delay(1500)
                hudMessage = ""
            }
        }

        if (showOverlay) {
            LaunchedEffect(showOverlay) {
                delay(3000)
                showOverlay = false
            }

            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).padding(horizontal = 24.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    Text(formatTime(positionSeconds), color = Color.White, fontSize = 12.sp)
                    Text(formatTime(durationSeconds), color = Color.White, fontSize = 12.sp)
                }
                LinearProgressIndicator(
                    progress = {
                        if (durationSeconds > 0) (positionSeconds / durationSeconds).toFloat().coerceIn(0f, 1f) else 0f
                    },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.3f),
                )
            }

            Row(
                modifier = Modifier.align(Alignment.Center),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { controller.seekBy(-10.0) }) {
                    Icon(Icons.Default.Replay10, contentDescription = "快退10秒", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = {
                    if (isPlaying) {
                        controller.pause()
                        isPlaying = false
                    } else {
                        controller.play()
                        isPlaying = true
                    }
                }) {
                    Icon(
                        if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
                IconButton(onClick = { controller.seekBy(10.0) }) {
                    Icon(Icons.Default.Forward10, contentDescription = "快进10秒", tint = Color.White, modifier = Modifier.size(36.dp))
                }
            }

            if (hudMessage.isNotBlank()) {
                Text(
                    hudMessage,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(bottom = 80.dp)
                        .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }
    }
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

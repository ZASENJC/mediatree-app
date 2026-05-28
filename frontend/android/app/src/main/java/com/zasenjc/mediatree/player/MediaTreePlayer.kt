package com.zasenjc.mediatree.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.net.Uri
import android.util.Log
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.zasenjc.mediatree.playback.PlaybackSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private const val TAG = "MediaTreePlayer"

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

    // Build http factory + player together, keyed on playback source.
    val player = remember(playbackSource) {
        val httpFactory = DefaultHttpDataSource.Factory().apply {
            setDefaultRequestProperties(playbackSource.headers)
        }
        val mediaFactory = DefaultMediaSourceFactory(httpFactory)
        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaFactory)
            .build()
    }

    var isPlaying by remember { mutableStateOf(false) }
    var hudMessage by remember { mutableStateOf("") }
    var showOverlay by remember { mutableStateOf(false) }

    // Setup media source when playback source or subtitle changes.
    DisposableEffect(playbackSource, selectedSubtitle) {
        Log.d(TAG, "Preparing media source subtitleSelected=${selectedSubtitle >= 0}")
        val subUrl = if (selectedSubtitle >= 0) {
            playbackSource.subtitleUri(selectedSubtitle)?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val mediaItem = MediaItem.Builder()
            .setUri(Uri.parse(playbackSource.uri))
            .apply {
                if (subUrl != null) {
                    setSubtitleConfigurations(listOf(
                        MediaItem.SubtitleConfiguration.Builder(Uri.parse(subUrl))
                            .setMimeType(MimeTypes.APPLICATION_SUBRIP)
                            .setLanguage("ext")
                            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                            .build()
                    ))
                }
            }
            .build()

        player.stop()
        player.setMediaItem(mediaItem)
        if (startPosition > 1.0) {
            player.seekTo((startPosition * 1000).toLong())
        }
        player.prepare()
        player.play()

        onDispose {
            // Only stop, do NOT release here (release is handled by the other DisposableEffect)
        }
    }

    // Clean up player when playback source changes or composable leaves.
    DisposableEffect(playbackSource) {
        onDispose {
            Log.d(TAG, "Releasing player")
            player.stop()
            player.release()
        }
    }

    // Player listener: playback state + errors
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) { isPlaying = playing }
            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) {
                    val pos = player.currentPosition / 1000.0
                    val dur = player.duration / 1000.0
                    onPlaybackComplete(pos, dur)
                }
            }
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "Player error code=${error.errorCodeName}")
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    // 15s progress reporting
    LaunchedEffect(Unit) {
        while (isActive) {
            delay(15_000)
            val pos = player.currentPosition / 1000.0
            val dur = player.duration / 1000.0
            if (pos > 0) onProgressUpdate(pos, dur)
        }
    }

    // Surface-level gestures (brightness, volume)
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
            }
    ) {
        // ExoPlayer PlayerView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                PlayerView(it).apply {
                    useController = false
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                }
            },
            update = { view ->
                view.player = player
            },
        )

        // HUD overlay
        if (hudMessage.isNotBlank()) {
            LaunchedEffect(hudMessage) {
                delay(1500)
                hudMessage = ""
            }
        }

        // Overlay controls (shown on tap)
        if (showOverlay) {
            LaunchedEffect(showOverlay) {
                delay(3000)
                showOverlay = false
            }

            Column(Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp).padding(horizontal = 24.dp).fillMaxWidth()) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween) {
                    Text(formatTime(player.currentPosition), color = Color.White, fontSize = 12.sp)
                    Text(formatTime(player.duration), color = Color.White, fontSize = 12.sp)
                }
                LinearProgressIndicator(
                    progress = {
                        val dur = player.duration
                        if (dur > 0) (player.currentPosition.toFloat() / dur) else 0f
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
                IconButton(onClick = { player.seekTo((player.currentPosition - 10_000).coerceAtLeast(0)) }) {
                    Icon(Icons.Default.Replay10, contentDescription = "快退10秒", tint = Color.White, modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                }) {
                    Icon(
                        if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (player.isPlaying) "暂停" else "播放",
                        tint = Color.White,
                        modifier = Modifier.size(48.dp),
                    )
                }
                IconButton(onClick = { player.seekTo(player.currentPosition + 10_000) }) {
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

private fun formatTime(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return "${min}:${sec.toString().padStart(2, '0')}"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

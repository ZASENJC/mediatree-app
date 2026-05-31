package com.zasenjc.mediatree.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayerUiCompletenessSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun mpvPlayerExposesCompletePrimaryControls() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertTrue(player.contains("onDoubleTap"))
        assertTrue(player.contains("PlayerGestureLayer"))
        assertTrue(player.contains("detectHorizontalDragGestures"))
        assertTrue(player.contains("horizontalSeekDeltaSeconds"))
        assertTrue(player.contains("HorizontalSeekSecondsPerScreen = 90.0"))
        assertTrue(player.contains("PlayerLock"))
        assertTrue(player.contains("FullscreenControl"))
        assertTrue(player.contains("PlaybackSpeedMenu"))
        assertTrue(player.contains("SubtitleTrackMenu"))
        assertTrue(player.contains("AudioTrackMenu"))
        assertTrue(player.contains("AspectRatioMenu"))
        assertTrue(player.contains("PlayerErrorOverlay"))
        assertTrue(player.contains("PlayerProgressBar"))
        assertTrue(player.contains("PlayerSeekBar"))
        assertTrue(player.contains("Slider("))
        assertTrue(player.contains("controller.seekTo"))
        assertTrue(player.contains("controller.seekBy(deltaSeconds)"))
        assertTrue(player.contains("controller.setPlaybackSpeed"))
        assertTrue(player.contains("controller.selectAudioTrack"))
        assertTrue(player.contains("controller.setAspectRatio"))
        assertTrue(player.contains("controller.percentPosition()"))
        assertTrue(player.contains("controller.lastError()"))
    }

    @Test
    fun playerUiRemovesVisibleTenSecondSeekButtons() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertFalse(player.contains("Icons.Default.Replay10"))
        assertFalse(player.contains("Icons.Default.Forward10"))
        assertFalse(player.contains("onSeekBy ="))
        assertFalse(player.contains("快退10秒"))
        assertFalse(player.contains("快进10秒"))
    }

    @Test
    fun doubleTapUsesSideZonesAndCenterPause() {
        assertEquals(PlayerDoubleTapAction.Rewind, playerDoubleTapAction(tapX = 20f, width = 300))
        assertEquals(PlayerDoubleTapAction.TogglePlay, playerDoubleTapAction(tapX = 100f, width = 300))
        assertEquals(PlayerDoubleTapAction.TogglePlay, playerDoubleTapAction(tapX = 150f, width = 300))
        assertEquals(PlayerDoubleTapAction.TogglePlay, playerDoubleTapAction(tapX = 200f, width = 300))
        assertEquals(PlayerDoubleTapAction.Forward, playerDoubleTapAction(tapX = 280f, width = 300))
    }

    @Test
    fun fullscreenAndLockBehaviorAreScoped() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()

        assertTrue(player.contains("LockedButtonAutoHideMillis = 5_000L"))
        assertTrue(player.contains("showLockedButton"))
        assertTrue(player.contains("onFullscreenRequest"))
        assertTrue(player.contains("showFullscreenButton"))
        assertTrue(player.contains("isFullscreen"))
        assertTrue(player.contains("showAspectRatioControls"))
        assertTrue(player.contains("if (showAspectRatioControls)"))
        assertTrue(player.contains("FullscreenControl("))
        assertTrue(player.contains("AspectRatioMenu("))
        assertFalse(player.contains("private fun FullscreenButton"))
        assertTrue(detailScreen.contains("playbackPositions"))
        assertTrue(detailScreen.contains("onPlaybackPositionChange"))
        assertTrue(detailScreen.contains("ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT"))
        assertTrue(detailScreen.contains("onChromeVisibleChange"))
        assertTrue(detailScreen.contains("onChromeVisibleChange(!isLandscape)"))
        assertTrue(detailScreen.contains("onDispose { onChromeVisibleChange(true) }"))
        assertTrue(detailScreen.contains("isFullscreen = isLandscape"))
        assertTrue(detailScreen.contains("showAspectRatioControls = isLandscape"))
    }

    @Test
    fun fullscreenControlStaysOnBottomRowTrailingEdge() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        val speedIndex = player.indexOf("PlaybackSpeedMenu(selectedSpeed = playbackSpeed")
        val subtitleIndex = player.indexOf("SubtitleTrackMenu(")
        val audioIndex = player.indexOf("AudioTrackMenu(")
        val aspectIndex = player.indexOf("AspectRatioMenu(selectedAspectRatio = selectedAspectRatio")
        val fullscreenIndex = player.indexOf("FullscreenControl(isFullscreen = isFullscreen")

        assertTrue(speedIndex >= 0)
        assertTrue(subtitleIndex > speedIndex)
        assertTrue(audioIndex > subtitleIndex)
        assertTrue(aspectIndex > audioIndex)
        assertTrue(fullscreenIndex > aspectIndex)
        assertTrue(player.contains("Spacer(Modifier.weight(1f))"))
    }

    @Test
    fun subtitleMenuUsesCompactTriggerLabel() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()
        val menuStart = player.indexOf("private fun SubtitleTrackMenu(")
        val menuEnd = player.indexOf("@Composable\nprivate fun AudioTrackMenu", menuStart)
        val subtitleMenu = player.substring(menuStart, menuEnd)

        assertTrue(subtitleMenu.contains("label = \"字幕\""))
        assertFalse(subtitleMenu.contains("subtitleLabel() ?: \"字幕\""))
    }

    @Test
    fun detailPageKeepsSinglePlayerInstanceAcrossFullscreenChanges() {
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()

        assertEquals(1, Regex("""MediaTreePlayer\(""").findAll(detailScreen).count())
        assertTrue(detailScreen.contains("val playerModifier = if (isLandscape)"))
        assertTrue(detailScreen.contains("modifier = playerModifier"))
        assertFalse(detailScreen.contains("SubtitleSelector("))
        assertFalse(detailScreen.contains("private fun SubtitleSelector"))
        assertFalse(detailScreen.contains("FilterChip"))
    }

    @Test
    fun playerLoadEffectIgnoresSubtitleTrackListChanges() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertTrue(player.contains("LaunchedEffect(playbackSource.uri, playbackSource.headers)"))
        assertFalse(player.contains("LaunchedEffect(playbackSource)"))
    }

    @Test
    fun playbackProgressIsFiniteAndClamped() {
        assertEquals(0f, playbackProgress(positionSeconds = Double.NaN, durationSeconds = 100.0))
        assertEquals(0f, playbackProgress(positionSeconds = 30.0, durationSeconds = Double.NaN))
        assertEquals(0f, playbackProgress(positionSeconds = 0.0, durationSeconds = 0.0))
        assertEquals(0.5f, playbackProgress(positionSeconds = 30.0, durationSeconds = 60.0))
        assertEquals(1f, playbackProgress(positionSeconds = 90.0, durationSeconds = 60.0))
        assertEquals(0.25f, playbackProgress(positionSeconds = 30.0, durationSeconds = 0.0, percentPosition = 25.0))
        assertEquals(1f, playbackProgress(positionSeconds = 30.0, durationSeconds = 0.0, percentPosition = 120.0))
        assertEquals(0f, playbackProgress(positionSeconds = 30.0, durationSeconds = 0.0, percentPosition = Double.NaN))
        assertEquals(25.0, playbackPercent(positionSeconds = 30.0, durationSeconds = 120.0), 0.001)
        assertEquals(25.0, playbackPercent(positionSeconds = 0.0, durationSeconds = 0.0, fallbackPercent = 25.0), 0.001)
    }

    @Test
    fun horizontalSeekGestureUsesLowSensitivity() {
        assertEquals(45.0, horizontalSeekDeltaSeconds(dragAmountPx = 500f, widthPx = 1000), 0.001)
        assertEquals(-45.0, horizontalSeekDeltaSeconds(dragAmountPx = -500f, widthPx = 1000), 0.001)
        assertEquals(0.0, horizontalSeekDeltaSeconds(dragAmountPx = 500f, widthPx = 0), 0.001)
    }

    @Test
    fun playerUiKeepsNativeSurfaceOutOfLayerTransforms() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertTrue(player.contains("MpvPlayerView"))
        assertFalse(player.contains("import androidx.compose.ui.graphics.graphicsLayer"))
        assertFalse(player.contains(".graphicsLayer"))
    }
}

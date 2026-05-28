package com.zasenjc.mediatree.player

import org.junit.Assert.assertFalse
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
        assertTrue(player.contains("PlayerLock"))
        assertTrue(player.contains("PlaybackSpeedMenu"))
        assertTrue(player.contains("SubtitleTrackMenu"))
        assertTrue(player.contains("AudioTrackMenu"))
        assertTrue(player.contains("AspectRatioMenu"))
        assertTrue(player.contains("PlayerErrorOverlay"))
        assertTrue(player.contains("controller.setPlaybackSpeed"))
        assertTrue(player.contains("controller.selectAudioTrack"))
        assertTrue(player.contains("controller.setAspectRatio"))
        assertTrue(player.contains("controller.lastError()"))
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

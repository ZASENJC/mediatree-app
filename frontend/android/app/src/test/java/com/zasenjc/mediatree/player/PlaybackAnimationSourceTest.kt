package com.zasenjc.mediatree.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaybackAnimationSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun mpvViewKeepsStableSurfaceViewForNativeMpv() {
        val playerView = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MpvPlayerView.kt")
            .readText()

        assertTrue(playerView.contains("import android.view.SurfaceView"))
        assertTrue(playerView.contains("SurfaceHolder.Callback"))
        assertTrue(playerView.contains("override fun surfaceChanged"))
        assertTrue(playerView.contains("setSurfaceSize(width, height)"))
        assertFalse(playerView.contains("TextureView"))
        assertFalse(playerView.contains("SurfaceTexture"))
    }

    @Test
    fun mediaTreePlayerKeepsSurfaceViewOutOfComposeLayerAnimations() {
        val mediaTreePlayer = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertTrue(mediaTreePlayer.contains("remember(appContext) { MpvPlayerController(appContext) }"))
        assertFalse(mediaTreePlayer.contains("remember(playbackSource) { MpvPlayerController"))
        assertTrue(mediaTreePlayer.contains("animateFloatAsState"))
        assertTrue(mediaTreePlayer.contains("SourceSwapCurtainFadeOutMillis"))
        assertFalse(mediaTreePlayer.contains("import androidx.compose.ui.graphics.graphicsLayer"))
        assertFalse(mediaTreePlayer.contains(".graphicsLayer"))
    }

    @Test
    fun detailRouteDoesNotAnimateSurfaceViewAndEpisodesStayInPlace() {
        val appShell = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()

        assertFalse(appShell.contains("detailEnterTransition"))
        assertFalse(appShell.contains("detailPopExitTransition"))
        assertFalse(appShell.contains("scaleIn"))
        assertFalse(appShell.contains("scaleOut"))
        assertTrue(detailScreen.contains("var activeMovieId"))
        assertTrue(detailScreen.contains("onSelectEpisode"))
        assertTrue(detailScreen.contains("MediaTreePlayer("))
        assertTrue(detailScreen.contains("AnimatedContent("))
        assertTrue(detailScreen.contains("ExitPlayerReleaseDelayMillis"))
        assertTrue(detailScreen.contains("activeMovie != null && !leavingDetail"))
        assertFalse(detailScreen.contains("onNavigate(\"detail/${'$'}{episode.id}\")"))
    }
}

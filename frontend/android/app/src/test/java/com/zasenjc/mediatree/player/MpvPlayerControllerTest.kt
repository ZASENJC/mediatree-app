package com.zasenjc.mediatree.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MpvPlayerControllerTest {
    @Test
    fun loadUrlSetsHeadersAndLoadsFile() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(backend)

        controller.loadUrl(
            url = "http://media.local/api/stream/42",
            headers = mapOf("Authorization" to "Bearer token"),
            startPositionSeconds = 12.5,
        )

        assertEquals(listOf("force-window" to "no"), backend.options)
        assertEquals(
            listOf(
                listOf("set", "http-header-fields", "Authorization: Bearer token"),
                listOf("loadfile", "http://media.local/api/stream/42", "replace"),
                listOf("seek", "12.5", "absolute", "exact"),
            ),
            backend.commands,
        )
    }

    @Test
    fun playbackControlsUseMpvPropertiesAndCommands() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(backend)

        controller.play()
        controller.pause()
        controller.seekTo(90.0)
        controller.seekBy(-10.0)

        assertEquals(
            listOf("pause" to false, "pause" to true),
            backend.booleanProperties,
        )
        assertEquals(
            listOf(
                listOf("seek", "90.0", "absolute", "exact"),
                listOf("seek", "-10.0", "relative", "exact"),
            ),
            backend.commands,
        )
    }

    @Test
    fun sourceDeclaresMpvJniAndSurfaceBridge() {
        val appRoot = File(System.getProperty("user.dir") ?: ".")
        val mpvLib = appRoot.resolve("src/main/java/is/xyz/mpv/MPVLib.kt").readText()
        val playerView = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MpvPlayerView.kt")
            .readText()

        assertTrue(mpvLib.contains("System.loadLibrary(\"mpv\")"))
        assertTrue(mpvLib.contains("System.loadLibrary(\"player\")"))
        assertTrue(mpvLib.contains("external fun attachSurface"))
        assertTrue(mpvLib.contains("external fun detachSurface"))
        assertTrue(playerView.contains("SurfaceHolder.Callback"))
        assertTrue(playerView.contains("attachSurface"))
        assertTrue(playerView.contains("detachSurface"))
    }

    private class RecordingMpvBackend : MpvBackend {
        val options = mutableListOf<Pair<String, String>>()
        val commands = mutableListOf<List<String>>()
        val booleanProperties = mutableListOf<Pair<String, Boolean>>()
        var created = false
        var initialized = false
        var destroyed = false
        var attachedSurface: Any? = null
        var detached = false

        override fun create() {
            created = true
        }

        override fun init() {
            initialized = true
        }

        override fun destroy() {
            destroyed = true
        }

        override fun command(args: Array<String>) {
            commands += args.toList()
        }

        override fun setOptionString(name: String, value: String) {
            options += name to value
        }

        override fun setPropertyBoolean(name: String, value: Boolean) {
            booleanProperties += name to value
        }

        override fun attachSurface(surface: Any) {
            attachedSurface = surface
        }

        override fun detachSurface() {
            detached = true
        }
    }
}

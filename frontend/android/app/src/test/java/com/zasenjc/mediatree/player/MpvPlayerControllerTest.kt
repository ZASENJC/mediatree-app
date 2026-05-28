package com.zasenjc.mediatree.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MpvPlayerControllerTest {
    private val appContext = Any()

    @Test
    fun loadUrlSetsHeadersAndLoadsFile() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.loadUrl(
            url = "http://media.local/api/stream/42",
            headers = mapOf("Authorization" to "Bearer token"),
            startPositionSeconds = 12.5,
        )

        assertEquals(appContext, backend.createdContext)
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
    fun loadUrlWaitsForSurfaceBeforeStartingVideoOutput() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.loadUrl(
            url = "http://media.local/api/stream/42",
            headers = mapOf("Authorization" to "Bearer token"),
            startPositionSeconds = 12.5,
        )

        assertEquals(emptyList<List<String>>(), backend.commands)

        controller.attachSurface(Any())

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
        val controller = MpvPlayerController(appContext, backend)

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
    fun detachSurfaceStopsPlaybackBeforeClearingWindow() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.loadUrl(url = "http://media.local/api/stream/42")
        backend.commands.clear()

        controller.detachSurface()

        assertEquals(listOf(listOf("stop")), backend.commands)
        assertEquals(
            listOf(
                "force-window" to "no",
                "vo" to "null",
            ),
            backend.stringProperties,
        )
        assertTrue(backend.detached)
    }

    @Test
    fun releaseStopsPlaybackBeforeDestroyingMpv() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.loadUrl(url = "http://media.local/api/stream/42")
        backend.commands.clear()

        controller.release()

        assertEquals(listOf(listOf("stop")), backend.commands)
        assertEquals(
            listOf(
                "force-window" to "no",
                "vo" to "null",
            ),
            backend.stringProperties,
        )
        assertTrue(backend.detached)
        assertTrue(backend.destroyed)
    }

    @Test
    fun repeatedDetachOnlyTearsDownVideoOutputOnce() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.loadUrl(url = "http://media.local/api/stream/42")
        backend.commands.clear()

        controller.detachSurface()
        controller.detachSurface()

        assertEquals(listOf(listOf("stop")), backend.commands)
        assertEquals(
            listOf(
                "force-window" to "no",
                "vo" to "null",
            ),
            backend.stringProperties,
        )
        assertEquals(1, backend.detachCount)
    }

    @Test
    fun playbackStateReadsPositionDurationAndEndedFlag() {
        val backend = RecordingMpvBackend().apply {
            doubleProperties["time-pos"] = 15.25
            doubleProperties["duration"] = 120.0
            booleanValues["eof-reached"] = true
        }
        val controller = MpvPlayerController(appContext, backend)

        assertEquals(15.25, controller.positionSeconds(), 0.001)
        assertEquals(120.0, controller.durationSeconds(), 0.001)
        assertTrue(controller.isEnded())
    }

    @Test
    fun subtitleSelectionAddsAndClearsExternalSubtitle() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.selectSubtitle("http://media.local/api/subtitle/42/3")
        controller.clearSubtitle()

        assertEquals(
            listOf(
                listOf("sub-add", "http://media.local/api/subtitle/42/3", "select"),
                listOf("sub-remove"),
            ),
            backend.commands,
        )
    }

    @Test
    fun mediaTreePlayerUsesMpvAndBuildRemovesMedia3() {
        val appRoot = File(System.getProperty("user.dir") ?: ".")
        val playerSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()
        val buildGradle = appRoot.resolve("build.gradle").readText()

        assertTrue(playerSource.contains("MpvPlayerView"))
        assertTrue(playerSource.contains("MpvPlayerController"))
        assertFalse(playerSource.contains("ExoPlayer"))
        assertFalse(playerSource.contains("androidx.media3.ui.PlayerView"))
        assertFalse(playerSource.contains("androidx.media3"))
        assertFalse(buildGradle.contains("androidx.media3"))
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
        assertFalse(mpvLib.contains("external fun create()"))
        assertTrue(mpvLib.contains("external fun create(context: Context)"))
        assertTrue(mpvLib.contains("external fun attachSurface"))
        assertTrue(mpvLib.contains("external fun detachSurface"))
        assertTrue(mpvLib.contains("@JvmStatic fun eventProperty(name: String)"))
        assertTrue(mpvLib.contains("@JvmStatic fun eventProperty(name: String, value: Boolean)"))
        assertTrue(mpvLib.contains("@JvmStatic fun eventProperty(name: String, value: Long)"))
        assertTrue(mpvLib.contains("@JvmStatic fun eventProperty(name: String, value: Double)"))
        assertTrue(mpvLib.contains("@JvmStatic fun eventProperty(name: String, value: String?)"))
        assertTrue(mpvLib.contains("@JvmStatic fun event(eventId: Int)"))
        assertTrue(mpvLib.contains("@JvmStatic fun logMessage(prefix: String, level: Int, text: String)"))
        assertTrue(playerView.contains("SurfaceHolder.Callback"))
        assertTrue(playerView.contains("attachSurface"))
        assertTrue(playerView.contains("detachSurface"))
    }

    private class RecordingMpvBackend : MpvBackend {
        val options = mutableListOf<Pair<String, String>>()
        val commands = mutableListOf<List<String>>()
        val booleanProperties = mutableListOf<Pair<String, Boolean>>()
        val stringProperties = mutableListOf<Pair<String, String>>()
        val doubleProperties = mutableMapOf<String, Double>()
        val booleanValues = mutableMapOf<String, Boolean>()
        var created = false
        var createdContext: Any? = null
        var initialized = false
        var destroyed = false
        var attachedSurface: Any? = null
        var detached = false
        var detachCount = 0

        override fun create(context: Any) {
            createdContext = context
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

        override fun setPropertyString(name: String, value: String) {
            stringProperties += name to value
        }

        override fun getPropertyDouble(name: String): Double = doubleProperties[name] ?: 0.0

        override fun getPropertyBoolean(name: String): Boolean = booleanValues[name] ?: false

        override fun attachSurface(surface: Any) {
            attachedSurface = surface
        }

        override fun detachSurface() {
            detachCount += 1
            detached = true
        }
    }
}

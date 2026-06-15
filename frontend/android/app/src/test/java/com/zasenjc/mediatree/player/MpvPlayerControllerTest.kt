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
        )

        assertEquals(appContext, backend.createdContext)
        assertTrue(backend.options.contains("force-window" to "no"))
        assertTrue(backend.options.contains("vd-lavc-threads" to "0"))
        assertTrue(backend.options.contains("ad-lavc-threads" to "0"))
        assertTrue(backend.options.contains("ad" to "av3a"))
        assertEquals(
            listOf(
                listOf("change-list", "http-header-fields", "clr", ""),
                listOf("change-list", "http-header-fields", "append", "Authorization: Bearer token"),
                listOf("loadfile", "http://media.local/api/stream/42", "replace"),
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
        )

        assertEquals(emptyList<List<String>>(), backend.commands)

        controller.attachSurface(Any())

        assertEquals(
            listOf(
                listOf("change-list", "http-header-fields", "clr", ""),
                listOf("change-list", "http-header-fields", "append", "Authorization: Bearer token"),
                listOf("loadfile", "http://media.local/api/stream/42", "replace"),
            ),
            backend.commands,
        )
    }

    @Test
    fun loadUrlWithoutResumeDoesNotSendStartOption() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.loadUrl(url = "http://media.local/api/stream/42")

        assertEquals(
            listOf(
                listOf("change-list", "http-header-fields", "clr", ""),
                listOf("loadfile", "http://media.local/api/stream/42", "replace"),
            ),
            backend.commands,
        )
    }

    @Test
    fun loadUrlNeverSendsResumePositionAsLoadfileStartOption() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.loadUrl(url = "http://media.local/api/stream/42")

        assertTrue(backend.commands.none { command -> command.any { it.startsWith("start=") } })
    }

    @Test
    fun loadUrlSkipsReloadWhenSourceAndHeadersAreUnchanged() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.loadUrl(
            url = "http://media.local/api/stream/42",
            headers = mapOf("Authorization" to "Bearer token"),
        )
        backend.commands.clear()

        controller.loadUrl(
            url = "http://media.local/api/stream/42",
            headers = mapOf("Authorization" to "Bearer token"),
        )

        assertEquals(emptyList<List<String>>(), backend.commands)
    }

    @Test
    fun playbackControlsUseMpvPropertiesAndCommands() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.play()
        controller.pause()
        controller.seekTo(90.0)
        controller.seekBy(-10.0)
        controller.setPlaybackSpeed(1.25)
        controller.selectAudioTrack("2")
        controller.setAspectRatio("16:9")

        assertEquals(
            listOf("pause" to false, "pause" to true),
            backend.booleanProperties,
        )
        assertEquals(listOf("speed" to 1.25), backend.doublePropertiesSet)
        assertEquals(
            listOf(
                "aid" to "2",
                "video-aspect-override" to "16:9",
            ),
            backend.stringProperties,
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
    fun playbackSpeedIsClampedForMpv() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.setPlaybackSpeed(2.0)
        controller.setPlaybackSpeed(10.0)
        controller.setPlaybackSpeed(0.1)

        assertEquals(
            listOf(
                "speed" to 2.0,
                "speed" to 3.0,
                "speed" to 0.25,
            ),
            backend.doublePropertiesSet,
        )
    }

    @Test
    fun detachSurfaceStopsVideoOutputBeforeDroppingSurface() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.loadUrl(url = "http://media.local/api/stream/42")
        backend.commands.clear()

        controller.detachSurface()

        assertEquals(emptyList<List<String>>(), backend.commands)
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
    fun reattachingSurfaceRestoresVideoOutputAfterManualPause() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.loadUrl(url = "http://media.local/api/stream/42")
        controller.pause()
        backend.commands.clear()
        backend.stringProperties.clear()
        backend.booleanProperties.clear()

        controller.detachSurface()
        controller.attachSurface(Any(), width = 1280, height = 720)
        controller.play()

        assertEquals(emptyList<List<String>>(), backend.commands)
        assertEquals(
            listOf(
                "force-window" to "no",
                "vo" to "null",
                "vo" to "gpu",
                "android-surface-size" to "1280x720",
            ),
            backend.stringProperties,
        )
        assertEquals(listOf("pause" to false), backend.booleanProperties)
        assertEquals(1, backend.detachCount)
    }

    @Test
    fun surfaceSizeUpdatesAndroidSurfaceSizeProperty() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any(), width = 1920, height = 1080)
        controller.setSurfaceSize(width = 1280, height = 720)
        controller.setSurfaceSize(width = 0, height = 720)
        controller.setSurfaceSize(width = 1280, height = -1)

        assertEquals(
            listOf(
                "android-surface-size" to "1920x1080",
                "android-surface-size" to "1280x720",
            ),
            backend.stringProperties,
        )
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

        assertEquals(emptyList<List<String>>(), backend.commands)
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
            doubleProperties["percent-pos"] = 12.7
            booleanValues["eof-reached"] = true
        }
        val controller = MpvPlayerController(appContext, backend)
        controller.attachSurface(Any())

        assertEquals(15.25, controller.positionSeconds(), 0.001)
        assertEquals(120.0, controller.durationSeconds(), 0.001)
        assertEquals(12.7, controller.percentPosition(), 0.001)
        assertTrue(controller.isEnded())
    }

    @Test
    fun playbackPositionFallsBackToPlaybackTimeWhenTimePosIsUnavailable() {
        val backend = RecordingMpvBackend().apply {
            doubleProperties["time-pos"] = 0.0
            doubleProperties["playback-time"] = 44.5
        }
        val controller = MpvPlayerController(appContext, backend)
        controller.attachSurface(Any())

        assertEquals(44.5, controller.positionSeconds(), 0.001)
    }

    @Test
    fun playbackStateIgnoresInvalidMpvDoublesAndDerivesMissingDuration() {
        val backend = RecordingMpvBackend().apply {
            doubleProperties["time-pos"] = Double.NaN
            doubleProperties["playback-time"] = 30.0
            doubleProperties["duration"] = Double.NaN
            doubleProperties["time-remaining"] = 90.0
            doubleProperties["percent-pos"] = Double.NaN
        }
        val controller = MpvPlayerController(appContext, backend)
        controller.attachSurface(Any())

        assertEquals(30.0, controller.positionSeconds(), 0.001)
        assertEquals(120.0, controller.durationSeconds(), 0.001)
        assertEquals(25.0, controller.percentPosition(), 0.001)
    }

    @Test
    fun audioTrackOptionsReadMpvTrackList() {
        val backend = RecordingMpvBackend().apply {
            intValues["track-list/count"] = 4
            stringValues["track-list/0/type"] = "video"
            intValues["track-list/0/id"] = 1
            stringValues["track-list/1/type"] = "audio"
            intValues["track-list/1/id"] = 2
            stringValues["track-list/1/title"] = "Japanese 5.1"
            stringValues["track-list/1/lang"] = "jpn"
            stringValues["track-list/2/type"] = "sub"
            intValues["track-list/2/id"] = 3
            stringValues["track-list/3/type"] = "audio"
            intValues["track-list/3/id"] = 4
            stringValues["track-list/3/lang"] = "eng"
        }
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())

        assertEquals(
            listOf(
                MpvTrackOption(id = "2", label = "Japanese 5.1 (jpn)"),
                MpvTrackOption(id = "4", label = "音轨 4 (eng)"),
            ),
            controller.audioTrackOptions(),
        )
    }

    @Test
    fun subtitleSelectionAddsAndClearsExternalSubtitle() {
        val backend = RecordingMpvBackend()
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
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
    fun playbackCallsAfterReleaseDoNotTouchNativeBackend() {
        val backend = RecordingMpvBackend().apply {
            doubleProperties["time-pos"] = 15.25
            doubleProperties["duration"] = 120.0
            booleanValues["eof-reached"] = true
        }
        val controller = MpvPlayerController(appContext, backend)

        controller.attachSurface(Any())
        controller.loadUrl(url = "http://media.local/api/stream/42")
        controller.release()
        backend.commands.clear()
        backend.booleanProperties.clear()
        backend.doublePropertiesSet.clear()
        backend.stringProperties.clear()
        backend.doublePropertyReads.clear()
        backend.booleanPropertyReads.clear()

        controller.play()
        controller.pause()
        controller.seekBy(10.0)
        controller.setPlaybackSpeed(1.5)
        controller.selectAudioTrack("1")
        controller.setAspectRatio("4:3")
        controller.selectSubtitle("http://media.local/api/subtitle/42/3")
        controller.clearSubtitle()

        assertEquals(0.0, controller.positionSeconds(), 0.001)
        assertEquals(0.0, controller.durationSeconds(), 0.001)
        assertEquals(0.0, controller.percentPosition(), 0.001)
        assertFalse(controller.isEnded())
        assertEquals(emptyList<List<String>>(), backend.commands)
        assertEquals(emptyList<Pair<String, Boolean>>(), backend.booleanProperties)
        assertEquals(emptyList<Pair<String, Double>>(), backend.doublePropertiesSet)
        assertEquals(emptyList<Pair<String, String>>(), backend.stringProperties)
        assertEquals(emptyList<String>(), backend.doublePropertyReads)
        assertEquals(emptyList<String>(), backend.booleanPropertyReads)
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
        assertTrue(mpvLib.contains("observedDouble"))
        assertTrue(mpvLib.contains("ConcurrentHashMap"))
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
        val doublePropertiesSet = mutableListOf<Pair<String, Double>>()
        val stringProperties = mutableListOf<Pair<String, String>>()
        val doubleProperties = mutableMapOf<String, Double>()
        val booleanValues = mutableMapOf<String, Boolean>()
        val intValues = mutableMapOf<String, Int>()
        val stringValues = mutableMapOf<String, String>()
        val doublePropertyReads = mutableListOf<String>()
        val booleanPropertyReads = mutableListOf<String>()
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

        override fun observeProperty(name: String, format: Int) = Unit

        override fun observedPropertyDouble(name: String): Double? = null

        override fun observedPropertyBoolean(name: String): Boolean? = null

        override fun observedPropertyString(name: String): String? = null

        override fun setPropertyBoolean(name: String, value: Boolean) {
            booleanProperties += name to value
        }

        override fun setPropertyString(name: String, value: String) {
            stringProperties += name to value
        }

        override fun setPropertyDouble(name: String, value: Double) {
            doublePropertiesSet += name to value
        }

        override fun getPropertyInt(name: String): Int = intValues[name] ?: 0

        override fun getPropertyString(name: String): String? = stringValues[name]

        override fun getPropertyDouble(name: String): Double {
            doublePropertyReads += name
            return doubleProperties[name] ?: 0.0
        }

        override fun getPropertyBoolean(name: String): Boolean {
            booleanPropertyReads += name
            return booleanValues[name] ?: false
        }

        override fun attachSurface(surface: Any) {
            attachedSurface = surface
        }

        override fun detachSurface() {
            detachCount += 1
            detached = true
        }
    }
}

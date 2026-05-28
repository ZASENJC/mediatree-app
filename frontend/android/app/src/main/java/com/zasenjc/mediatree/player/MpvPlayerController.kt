package com.zasenjc.mediatree.player

import android.content.Context
import android.view.Surface
import `is`.xyz.mpv.MPVLib

interface MpvBackend {
    fun create(context: Any)
    fun init()
    fun destroy()
    fun command(args: Array<String>)
    fun setOptionString(name: String, value: String)
    fun getPropertyInt(name: String): Int
    fun getPropertyDouble(name: String): Double
    fun getPropertyBoolean(name: String): Boolean
    fun getPropertyString(name: String): String?
    fun setPropertyDouble(name: String, value: Double)
    fun setPropertyBoolean(name: String, value: Boolean)
    fun setPropertyString(name: String, value: String)
    fun attachSurface(surface: Any)
    fun detachSurface()
}

object NativeMpvBackend : MpvBackend {
    override fun create(context: Any) = MPVLib.create(context as Context)

    override fun init() = MPVLib.init()

    override fun destroy() = MPVLib.destroy()

    override fun command(args: Array<String>) = MPVLib.command(args)

    override fun setOptionString(name: String, value: String) = MPVLib.setOptionString(name, value)

    override fun getPropertyInt(name: String): Int = MPVLib.getPropertyInt(name)

    override fun getPropertyDouble(name: String): Double = MPVLib.getPropertyDouble(name)

    override fun getPropertyBoolean(name: String): Boolean = MPVLib.getPropertyBoolean(name)

    override fun getPropertyString(name: String): String? = MPVLib.getPropertyString(name)

    override fun setPropertyDouble(name: String, value: Double) = MPVLib.setPropertyDouble(name, value)

    override fun setPropertyBoolean(name: String, value: Boolean) = MPVLib.setPropertyBoolean(name, value)

    override fun setPropertyString(name: String, value: String) = MPVLib.setPropertyString(name, value)

    override fun attachSurface(surface: Any) {
        MPVLib.attachSurface(surface as Surface)
    }

    override fun detachSurface() = MPVLib.detachSurface()
}

data class MpvTrackOption(
    val id: String,
    val label: String,
)

class MpvPlayerController(
    private val appContext: Any,
    private val backend: MpvBackend = NativeMpvBackend,
) {
    private data class PendingLoad(
        val url: String,
        val headers: Map<String, String>,
        val startPositionSeconds: Double,
    )

    private var initialized = false
    private var surfaceAttached = false
    private var fileLoaded = false
    private var pendingLoad: PendingLoad? = null

    fun initialize() {
        if (initialized) return
        backend.create(appContext)
        backend.setOptionString("force-window", "no")
        backend.init()
        initialized = true
    }

    fun attachSurface(surface: Any) {
        initialize()
        backend.attachSurface(surface)
        surfaceAttached = true
        flushPendingLoad()
    }

    fun detachSurface() {
        if (!initialized || !surfaceAttached) return
        stopLoadedFile()
        stopVideoOutput()
        backend.detachSurface()
        surfaceAttached = false
    }

    fun loadUrl(
        url: String,
        headers: Map<String, String> = emptyMap(),
        startPositionSeconds: Double = 0.0,
    ) {
        initialize()
        pendingLoad = PendingLoad(url, headers, startPositionSeconds)
        if (!surfaceAttached) return
        flushPendingLoad()
    }

    private fun flushPendingLoad() {
        val request = pendingLoad ?: return
        pendingLoad = null
        val headers = request.headers
        if (headers.isNotEmpty()) {
            backend.command(arrayOf("set", "http-header-fields", headers.toHeaderFields()))
        }
        backend.command(arrayOf("loadfile", request.url, "replace"))
        fileLoaded = true
        if (request.startPositionSeconds > 0.0) {
            seekTo(request.startPositionSeconds)
        }
    }

    fun play() {
        if (!initialized) return
        backend.setPropertyBoolean("pause", false)
    }

    fun pause() {
        if (!initialized) return
        backend.setPropertyBoolean("pause", true)
    }

    fun seekTo(seconds: Double) {
        if (!initialized) return
        backend.command(arrayOf("seek", seconds.toString(), "absolute", "exact"))
    }

    fun seekBy(deltaSeconds: Double) {
        if (!initialized) return
        backend.command(arrayOf("seek", deltaSeconds.toString(), "relative", "exact"))
    }

    fun setPlaybackSpeed(speed: Double) {
        if (!initialized) return
        backend.setPropertyDouble("speed", speed.coerceIn(0.25, 3.0))
    }

    fun selectAudioTrack(trackId: String) {
        if (!initialized) return
        if (trackId.isBlank()) return
        backend.setPropertyString("aid", trackId)
    }

    fun setAspectRatio(aspectRatio: String) {
        if (!initialized) return
        if (aspectRatio.isBlank()) return
        backend.setPropertyString("video-aspect-override", aspectRatio)
    }

    fun selectSubtitle(subtitleUri: String) {
        if (!initialized) return
        if (subtitleUri.isBlank()) return
        backend.command(arrayOf("sub-add", subtitleUri, "select"))
    }

    fun clearSubtitle() {
        if (!initialized) return
        backend.command(arrayOf("sub-remove"))
    }

    private fun stopLoadedFile() {
        if (!fileLoaded && pendingLoad == null) return
        pendingLoad = null
        if (fileLoaded) {
            backend.command(arrayOf("stop"))
            fileLoaded = false
        }
    }

    private fun stopVideoOutput() {
        backend.setPropertyString("force-window", "no")
        backend.setPropertyString("vo", "null")
    }

    fun positionSeconds(): Double = if (initialized) backend.getPropertyDouble("time-pos") else 0.0

    fun durationSeconds(): Double = if (initialized) backend.getPropertyDouble("duration") else 0.0

    fun isEnded(): Boolean = initialized && backend.getPropertyBoolean("eof-reached")

    fun lastError(): String? =
        if (initialized) backend.getPropertyString("error-string")?.takeIf { it.isNotBlank() } else null

    fun audioTrackOptions(): List<MpvTrackOption> {
        if (!initialized) return emptyList()
        val count = backend.getPropertyInt("track-list/count").coerceAtLeast(0)
        return (0 until count).mapNotNull { index ->
            if (backend.getPropertyString("track-list/$index/type") != "audio") return@mapNotNull null
            val id = backend.getPropertyInt("track-list/$index/id").takeIf { it > 0 }?.toString() ?: return@mapNotNull null
            val title = backend.getPropertyString("track-list/$index/title").orEmpty()
            val language = backend.getPropertyString("track-list/$index/lang").orEmpty()
            MpvTrackOption(id = id, label = audioTrackLabel(id, title, language))
        }
    }

    fun release() {
        if (!initialized) return
        stopLoadedFile()
        if (surfaceAttached) {
            stopVideoOutput()
            backend.detachSurface()
        }
        backend.destroy()
        initialized = false
        surfaceAttached = false
        fileLoaded = false
        pendingLoad = null
    }
}

private fun Map<String, String>.toHeaderFields(): String =
    entries.joinToString(",") { (name, value) -> "$name: $value" }

private fun audioTrackLabel(id: String, title: String, language: String): String {
    val base = title.ifBlank { "音轨 $id" }
    return if (language.isBlank()) base else "$base ($language)"
}

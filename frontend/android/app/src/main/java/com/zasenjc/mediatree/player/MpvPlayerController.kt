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
    fun getPropertyDouble(name: String): Double
    fun getPropertyBoolean(name: String): Boolean
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

    override fun getPropertyDouble(name: String): Double = MPVLib.getPropertyDouble(name)

    override fun getPropertyBoolean(name: String): Boolean = MPVLib.getPropertyBoolean(name)

    override fun setPropertyBoolean(name: String, value: Boolean) = MPVLib.setPropertyBoolean(name, value)

    override fun setPropertyString(name: String, value: String) = MPVLib.setPropertyString(name, value)

    override fun attachSurface(surface: Any) {
        MPVLib.attachSurface(surface as Surface)
    }

    override fun detachSurface() = MPVLib.detachSurface()
}

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
        backend.setPropertyBoolean("pause", false)
    }

    fun pause() {
        backend.setPropertyBoolean("pause", true)
    }

    fun seekTo(seconds: Double) {
        backend.command(arrayOf("seek", seconds.toString(), "absolute", "exact"))
    }

    fun seekBy(deltaSeconds: Double) {
        backend.command(arrayOf("seek", deltaSeconds.toString(), "relative", "exact"))
    }

    fun selectSubtitle(subtitleUri: String) {
        if (subtitleUri.isBlank()) return
        backend.command(arrayOf("sub-add", subtitleUri, "select"))
    }

    fun clearSubtitle() {
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

    fun positionSeconds(): Double = backend.getPropertyDouble("time-pos")

    fun durationSeconds(): Double = backend.getPropertyDouble("duration")

    fun isEnded(): Boolean = backend.getPropertyBoolean("eof-reached")

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

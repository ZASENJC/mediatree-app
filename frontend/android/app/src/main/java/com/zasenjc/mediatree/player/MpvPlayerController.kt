package com.zasenjc.mediatree.player

import android.view.Surface
import `is`.xyz.mpv.MPVLib

interface MpvBackend {
    fun create()
    fun init()
    fun destroy()
    fun command(args: Array<String>)
    fun setOptionString(name: String, value: String)
    fun setPropertyBoolean(name: String, value: Boolean)
    fun attachSurface(surface: Any)
    fun detachSurface()
}

object NativeMpvBackend : MpvBackend {
    override fun create() = MPVLib.create()

    override fun init() = MPVLib.init()

    override fun destroy() = MPVLib.destroy()

    override fun command(args: Array<String>) = MPVLib.command(args)

    override fun setOptionString(name: String, value: String) = MPVLib.setOptionString(name, value)

    override fun setPropertyBoolean(name: String, value: Boolean) = MPVLib.setPropertyBoolean(name, value)

    override fun attachSurface(surface: Any) {
        MPVLib.attachSurface(surface as Surface)
    }

    override fun detachSurface() = MPVLib.detachSurface()
}

class MpvPlayerController(
    private val backend: MpvBackend = NativeMpvBackend,
) {
    private var initialized = false

    fun initialize() {
        if (initialized) return
        backend.create()
        backend.setOptionString("force-window", "no")
        backend.init()
        initialized = true
    }

    fun attachSurface(surface: Surface) {
        initialize()
        backend.attachSurface(surface)
    }

    fun detachSurface() {
        backend.detachSurface()
    }

    fun loadUrl(
        url: String,
        headers: Map<String, String> = emptyMap(),
        startPositionSeconds: Double = 0.0,
    ) {
        initialize()
        if (headers.isNotEmpty()) {
            backend.command(arrayOf("set", "http-header-fields", headers.toHeaderFields()))
        }
        backend.command(arrayOf("loadfile", url, "replace"))
        if (startPositionSeconds > 0.0) {
            seekTo(startPositionSeconds)
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

    fun release() {
        if (!initialized) return
        backend.detachSurface()
        backend.destroy()
        initialized = false
    }
}

private fun Map<String, String>.toHeaderFields(): String =
    entries.joinToString(",") { (name, value) -> "$name: $value" }

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
    fun observeProperty(name: String, format: Int)
    fun getPropertyInt(name: String): Int
    fun getPropertyDouble(name: String): Double
    fun getPropertyBoolean(name: String): Boolean
    fun getPropertyString(name: String): String?
    fun observedPropertyDouble(name: String): Double?
    fun observedPropertyBoolean(name: String): Boolean?
    fun observedPropertyString(name: String): String?
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

    override fun observeProperty(name: String, format: Int) = MPVLib.observeProperty(name, format)

    override fun getPropertyInt(name: String): Int = MPVLib.getPropertyInt(name)

    override fun getPropertyDouble(name: String): Double = MPVLib.getPropertyDouble(name)

    override fun getPropertyBoolean(name: String): Boolean = MPVLib.getPropertyBoolean(name)

    override fun getPropertyString(name: String): String? = MPVLib.getPropertyString(name)

    override fun observedPropertyDouble(name: String): Double? = MPVLib.observedDouble(name)

    override fun observedPropertyBoolean(name: String): Boolean? = MPVLib.observedBoolean(name)

    override fun observedPropertyString(name: String): String? = MPVLib.observedString(name)

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
    private var loadedSource: PendingLoad? = null
    private var videoOutputStopped = false

    fun initialize() {
        if (initialized) return
        backend.create(appContext)
        backend.setOptionString("force-window", "no")
        backend.init()
        observePlaybackProperties()
        initialized = true
    }

    private fun observePlaybackProperties() {
        listOf(
            "time-pos",
            "playback-time",
            "duration",
            "time-remaining",
            "playtime-remaining",
            "percent-pos",
        ).forEach { property -> backend.observeProperty(property, MpvFormatDouble) }
        backend.observeProperty("eof-reached", MpvFormatFlag)
        backend.observeProperty("error-string", MpvFormatString)
    }

    fun attachSurface(surface: Any, width: Int = 0, height: Int = 0) {
        initialize()
        if (videoOutputStopped) restoreVideoOutput()
        backend.attachSurface(surface)
        surfaceAttached = true
        setSurfaceSize(width, height)
        flushPendingLoad()
    }

    fun detachSurface() {
        if (!initialized || !surfaceAttached) return
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
        if (loadedSource?.sameMediaRequest(url, headers) == true && pendingLoad == null) return
        pendingLoad = PendingLoad(url, headers, startPositionSeconds)
        if (!surfaceAttached) return
        flushPendingLoad()
    }

    private fun PendingLoad.sameMediaRequest(
        url: String,
        headers: Map<String, String>,
    ): Boolean = this.url == url && this.headers == headers

    private fun flushPendingLoad() {
        val request = pendingLoad ?: return
        pendingLoad = null
        clearHttpHeaders()
        setHttpHeaders(request.headers)
        backend.command(arrayOf("loadfile", request.url, "replace"))
        fileLoaded = true
        loadedSource = request
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

    fun setSurfaceSize(width: Int, height: Int) {
        if (!initialized) return
        if (width <= 0 || height <= 0) return
        backend.setPropertyString("android-surface-size", "${width}x$height")
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
            loadedSource = null
        }
    }

    private fun clearHttpHeaders() {
        backend.command(arrayOf("change-list", "http-header-fields", "clr", ""))
    }

    private fun setHttpHeaders(headers: Map<String, String>) {
        headers.forEach { (name, value) ->
            backend.command(arrayOf("change-list", "http-header-fields", "append", "$name: $value"))
        }
    }

    private fun restoreVideoOutput() {
        backend.setPropertyString("vo", "gpu")
        videoOutputStopped = false
    }

    private fun stopVideoOutput() {
        backend.setPropertyString("force-window", "no")
        backend.setPropertyString("vo", "null")
        videoOutputStopped = true
    }

    fun positionSeconds(): Double {
        if (!initialized) return 0.0
        val positivePosition = listOf("time-pos", "playback-time")
            .firstNotNullOfOrNull { name -> readPositiveDouble(name) }
        if (positivePosition != null) return positivePosition
        return listOf("time-pos", "playback-time")
            .firstNotNullOfOrNull { name -> readFiniteDouble(name)?.coerceAtLeast(0.0) }
            ?: 0.0
    }

    fun durationSeconds(): Double {
        if (!initialized) return 0.0
        readPositiveDouble("duration")?.let { return it }
        val position = positionSeconds()
        val remaining = readPositiveDouble("time-remaining") ?: readPositiveDouble("playtime-remaining")
        if (position > 0.0 && remaining != null) return position + remaining
        val percent = readPercentDouble("percent-pos")
        if (position > 0.0 && percent != null) return position * 100.0 / percent
        return 0.0
    }

    fun percentPosition(): Double {
        if (!initialized) return 0.0
        readPercentDouble("percent-pos")?.let { return it }
        val position = positionSeconds()
        val duration = readPositiveDouble("duration")
            ?: readPositiveDouble("time-remaining")?.let { position + it }
            ?: readPositiveDouble("playtime-remaining")?.let { position + it }
        if (position > 0.0 && duration != null && duration > 0.0) {
            return (position / duration * 100.0).coerceIn(0.0, 100.0)
        }
        return 0.0
    }

    private fun readFiniteDouble(name: String): Double? =
        backend.observedPropertyDouble(name)
            ?.takeIf { it.isFinite() }
            ?: runCatching { backend.getPropertyDouble(name) }
                .getOrNull()
                ?.takeIf { it.isFinite() }

    private fun readPositiveDouble(name: String): Double? =
        readFiniteDouble(name)?.takeIf { it > 0.0 }

    private fun readPercentDouble(name: String): Double? =
        readFiniteDouble(name)
            ?.takeIf { it > 0.0 }
            ?.coerceIn(0.0, 100.0)

    fun isEnded(): Boolean = initialized && (backend.observedPropertyBoolean("eof-reached") ?: backend.getPropertyBoolean("eof-reached"))

    fun lastError(): String? = if (initialized) {
        (backend.observedPropertyString("error-string") ?: backend.getPropertyString("error-string"))
            ?.takeIf { it.isNotBlank() }
    } else {
        null
    }

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
        loadedSource = null
        videoOutputStopped = false
    }
}

private const val MpvFormatString = 1
private const val MpvFormatFlag = 3
private const val MpvFormatDouble = 5

private fun audioTrackLabel(id: String, title: String, language: String): String {
    val base = title.ifBlank { "音轨 $id" }
    return if (language.isBlank()) base else "$base ($language)"
}

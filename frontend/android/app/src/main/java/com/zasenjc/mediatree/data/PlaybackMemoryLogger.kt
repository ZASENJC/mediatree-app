package com.zasenjc.mediatree.data

import android.util.Log

internal object PlaybackMemoryLogger {
    private const val Tag = "PlaybackMemory"

    fun debug(message: String) {
        runCatching { Log.d(Tag, message) }
    }

    fun warn(message: String, error: Throwable? = null) {
        runCatching {
            if (error == null) {
                Log.w(Tag, message)
            } else {
                Log.w(Tag, message, error)
            }
        }
    }
}

internal fun Double.memoryLogValue(): String =
    if (isFinite()) String.format("%.2f", this) else "invalid"

internal fun String.memorySafeHash(): Int = hashCode()

package com.zasenjc.mediatree.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.clientPlaybackProgressDataStore by preferencesDataStore("mediatree_client_playback_progress")

private val clientPlaybackProgressJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class ClientPlaybackProgress(
    val sourceId: String,
    val path: String,
    val positionSeconds: Double,
    val durationSeconds: Double,
    val updatedAtMillis: Long,
)

interface ClientPlaybackProgressStore {
    suspend fun load(sourceId: String, path: String): ClientPlaybackProgress?
    suspend fun list(sourceId: String): List<ClientPlaybackProgress>
    suspend fun save(progress: ClientPlaybackProgress)
    suspend fun delete(sourceId: String, path: String)
}

class ClientPlaybackProgressRepository(
    private val store: ClientPlaybackProgressStore,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun resumePosition(sourceId: String, path: String): Double {
        if (sourceId.isBlank() || path.isBlank()) return 0.0
        val progress = store.load(sourceId, path) ?: return 0.0
        val position = rememberablePlaybackPosition(progress.positionSeconds, progress.durationSeconds)
        if (position == 0.0) {
            store.delete(sourceId, path)
        }
        PlaybackMemoryLogger.debug(
            "client-resume sourceHash=${sourceId.memorySafeHash()} pathHash=${path.memorySafeHash()} position=${position.memoryLogValue()}",
        )
        return position
    }

    suspend fun save(
        sourceId: String,
        path: String,
        positionSeconds: Double,
        durationSeconds: Double,
    ) {
        if (sourceId.isBlank() || path.isBlank()) return
        when (val decision = playbackMemorySaveDecision(positionSeconds, durationSeconds)) {
            PlaybackMemorySaveDecision.Clear -> {
                PlaybackMemoryLogger.debug(
                    "client-save sourceHash=${sourceId.memorySafeHash()} pathHash=${path.memorySafeHash()} position=${positionSeconds.memoryLogValue()} duration=${durationSeconds.memoryLogValue()} decision=clear",
                )
                store.delete(sourceId, path)
            }
            PlaybackMemorySaveDecision.Ignore -> {
                PlaybackMemoryLogger.debug(
                    "client-save sourceHash=${sourceId.memorySafeHash()} pathHash=${path.memorySafeHash()} position=${positionSeconds.memoryLogValue()} duration=${durationSeconds.memoryLogValue()} decision=ignore",
                )
            }
            is PlaybackMemorySaveDecision.Remember -> {
                PlaybackMemoryLogger.debug(
                    "client-save sourceHash=${sourceId.memorySafeHash()} pathHash=${path.memorySafeHash()} position=${decision.positionSeconds.memoryLogValue()} duration=${durationSeconds.memoryLogValue()} decision=remember",
                )
                store.save(
                    ClientPlaybackProgress(
                        sourceId = sourceId,
                        path = path,
                        positionSeconds = decision.positionSeconds,
                        durationSeconds = durationSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0,
                        updatedAtMillis = clockMillis(),
                    ),
                )
            }
        }
    }

    suspend fun markFinished(sourceId: String, path: String) {
        if (sourceId.isBlank() || path.isBlank()) return
        PlaybackMemoryLogger.debug(
            "client-finish sourceHash=${sourceId.memorySafeHash()} pathHash=${path.memorySafeHash()}",
        )
        store.delete(sourceId, path)
    }

    suspend fun listContinueWatching(sourceId: String, limit: Int): List<ClientPlaybackProgress> {
        if (sourceId.isBlank()) return emptyList()
        val valid = mutableListOf<ClientPlaybackProgress>()
        store.list(sourceId).forEach { progress ->
            if (rememberablePlaybackPosition(progress.positionSeconds, progress.durationSeconds) == 0.0) {
                store.delete(sourceId, progress.path)
            } else {
                valid.add(progress)
            }
        }
        return valid
            .sortedByDescending { it.updatedAtMillis }
            .take(limit)
    }
}

class AndroidClientPlaybackProgressStore(context: Context) : ClientPlaybackProgressStore {
    private val appContext = context.applicationContext

    override suspend fun load(sourceId: String, path: String): ClientPlaybackProgress? =
        decode(appContext.clientPlaybackProgressDataStore.data.first()[PROGRESS_KEY])
            .firstOrNull { it.sourceId == sourceId && it.path == path }

    override suspend fun list(sourceId: String): List<ClientPlaybackProgress> =
        decode(appContext.clientPlaybackProgressDataStore.data.first()[PROGRESS_KEY])
            .filter { it.sourceId == sourceId }

    override suspend fun save(progress: ClientPlaybackProgress) {
        appContext.clientPlaybackProgressDataStore.edit { prefs ->
            val entries = decode(prefs[PROGRESS_KEY])
                .filterNot { it.sourceId == progress.sourceId && it.path == progress.path } + progress
            prefs[PROGRESS_KEY] = clientPlaybackProgressJson.encodeToString(entries)
        }
    }

    override suspend fun delete(sourceId: String, path: String) {
        appContext.clientPlaybackProgressDataStore.edit { prefs ->
            val entries = decode(prefs[PROGRESS_KEY])
                .filterNot { it.sourceId == sourceId && it.path == path }
            if (entries.isEmpty()) {
                prefs.remove(PROGRESS_KEY)
            } else {
                prefs[PROGRESS_KEY] = clientPlaybackProgressJson.encodeToString(entries)
            }
        }
    }

    private fun decode(value: String?): List<ClientPlaybackProgress> =
        value
            ?.let { runCatching { clientPlaybackProgressJson.decodeFromString<List<ClientPlaybackProgress>>(it) }.getOrNull() }
            .orEmpty()

    companion object {
        private val PROGRESS_KEY = stringPreferencesKey("client_playback_progress")
    }
}

fun rememberablePlaybackPosition(positionSeconds: Double, durationSeconds: Double): Double {
    return when (val decision = playbackMemorySaveDecision(positionSeconds, durationSeconds)) {
        is PlaybackMemorySaveDecision.Remember -> decision.positionSeconds
        PlaybackMemorySaveDecision.Clear,
        PlaybackMemorySaveDecision.Ignore,
        -> 0.0
    }
}

internal sealed interface PlaybackMemorySaveDecision {
    data object Ignore : PlaybackMemorySaveDecision
    data object Clear : PlaybackMemorySaveDecision
    data class Remember(val positionSeconds: Double) : PlaybackMemorySaveDecision
}

internal fun playbackMemorySaveDecision(positionSeconds: Double, durationSeconds: Double): PlaybackMemorySaveDecision {
    if (!positionSeconds.isFinite()) return PlaybackMemorySaveDecision.Ignore
    val position = positionSeconds.coerceAtLeast(0.0)
    if (position < PlaybackMemoryMinimumPositionSeconds) return PlaybackMemorySaveDecision.Ignore
    if (durationSeconds.isFinite() && durationSeconds > 0.0 && position / durationSeconds >= 0.95) {
        return PlaybackMemorySaveDecision.Clear
    }
    return PlaybackMemorySaveDecision.Remember(position)
}

const val PlaybackMemoryMinimumPositionSeconds = 60.0

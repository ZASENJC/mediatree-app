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
        return position
    }

    suspend fun save(
        sourceId: String,
        path: String,
        positionSeconds: Double,
        durationSeconds: Double,
    ) {
        if (sourceId.isBlank() || path.isBlank()) return
        val position = rememberablePlaybackPosition(positionSeconds, durationSeconds)
        if (position == 0.0) {
            store.delete(sourceId, path)
            return
        }
        store.save(
            ClientPlaybackProgress(
                sourceId = sourceId,
                path = path,
                positionSeconds = position,
                durationSeconds = durationSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0,
                updatedAtMillis = clockMillis(),
            ),
        )
    }

    suspend fun markFinished(sourceId: String, path: String) {
        if (sourceId.isBlank() || path.isBlank()) return
        store.delete(sourceId, path)
    }
}

class AndroidClientPlaybackProgressStore(context: Context) : ClientPlaybackProgressStore {
    private val appContext = context.applicationContext

    override suspend fun load(sourceId: String, path: String): ClientPlaybackProgress? =
        decode(appContext.clientPlaybackProgressDataStore.data.first()[PROGRESS_KEY])
            .firstOrNull { it.sourceId == sourceId && it.path == path }

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
    if (!positionSeconds.isFinite()) return 0.0
    val position = positionSeconds.coerceAtLeast(0.0)
    if (position < 5.0) return 0.0
    if (durationSeconds.isFinite() && durationSeconds > 0.0 && position / durationSeconds >= 0.95) {
        return 0.0
    }
    return position
}

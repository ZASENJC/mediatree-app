package com.zasenjc.mediatree.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.remotePlaybackMemoryDataStore by preferencesDataStore("mediatree_remote_playback_memory")

private val remotePlaybackMemoryJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class RemotePlaybackMemory(
    val providerType: ProviderType,
    val profileId: String,
    val mediaRoot: String,
    val movieId: Int,
    val providerItemId: String? = null,
    val providerSeriesId: String? = null,
    val providerSeasonId: String? = null,
    val path: String = "",
    val code: String = "",
    val title: String? = null,
    val originalTitle: String? = null,
    val releaseDate: String? = null,
    val folderLevels: String? = null,
    val episodeTitle: String? = null,
    val episodeNumber: Int? = null,
    val episodeLabel: String? = null,
    val episodeStill: String? = null,
    val displayTitle: String? = null,
    val createdAt: String? = null,
    val updatedAt: String? = null,
    val tags: List<String> = emptyList(),
    val positionSeconds: Double,
    val durationSeconds: Double,
    val progressPercent: Double? = null,
    val updatedAtMillis: Long,
)

interface RemotePlaybackMemoryStore {
    suspend fun load(providerType: ProviderType, profileId: String, movieId: Int): RemotePlaybackMemory?
    suspend fun list(providerType: ProviderType, profileId: String, mediaRoot: String): List<RemotePlaybackMemory>
    suspend fun save(memory: RemotePlaybackMemory)
    suspend fun delete(providerType: ProviderType, profileId: String, movieId: Int)
}

class RemotePlaybackMemoryRepository(
    private val store: RemotePlaybackMemoryStore,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun resumePosition(providerType: ProviderType, profileId: String, movieId: Int): Double {
        val resolvedProfileId = profileId.remoteMemoryProfileId()
        val memory = store.load(providerType, resolvedProfileId, movieId) ?: return 0.0
        val position = rememberablePlaybackPosition(memory.positionSeconds, memory.durationSeconds)
        if (position == 0.0) {
            store.delete(providerType, resolvedProfileId, movieId)
        }
        return position
    }

    suspend fun save(
        providerType: ProviderType,
        profileId: String,
        mediaRoot: String,
        movie: MovieDto,
        positionSeconds: Double,
        durationSeconds: Double,
    ) {
        val resolvedProfileId = profileId.remoteMemoryProfileId()
        when (val decision = playbackMemorySaveDecision(positionSeconds, durationSeconds)) {
            PlaybackMemorySaveDecision.Clear -> store.delete(providerType, resolvedProfileId, movie.id)
            PlaybackMemorySaveDecision.Ignore -> Unit
            is PlaybackMemorySaveDecision.Remember -> store.save(
                movie.toRemotePlaybackMemory(
                    providerType = providerType,
                    profileId = resolvedProfileId,
                    mediaRoot = mediaRoot.ifBlank { movie.mediaRoot.orEmpty() },
                    positionSeconds = decision.positionSeconds,
                    durationSeconds = durationSeconds.takeIf { it.isFinite() }?.coerceAtLeast(0.0) ?: 0.0,
                    updatedAtMillis = clockMillis(),
                ),
            )
        }
    }

    suspend fun listContinueWatching(
        providerType: ProviderType,
        profileId: String,
        mediaRoot: String,
        limit: Int,
    ): List<RemotePlaybackMemory> {
        val resolvedProfileId = profileId.remoteMemoryProfileId()
        val valid = mutableListOf<RemotePlaybackMemory>()
        store.list(providerType, resolvedProfileId, mediaRoot).forEach { memory ->
            if (rememberablePlaybackPosition(memory.positionSeconds, memory.durationSeconds) == 0.0) {
                store.delete(providerType, resolvedProfileId, memory.movieId)
            } else {
                valid.add(memory)
            }
        }
        return valid
            .sortedByDescending { it.updatedAtMillis }
            .take(limit)
    }

    suspend fun markFinished(providerType: ProviderType, profileId: String, movieId: Int) {
        store.delete(providerType, profileId.remoteMemoryProfileId(), movieId)
    }
}

class AndroidRemotePlaybackMemoryStore(context: Context) : RemotePlaybackMemoryStore {
    private val appContext = context.applicationContext

    override suspend fun load(providerType: ProviderType, profileId: String, movieId: Int): RemotePlaybackMemory? =
        decode(appContext.remotePlaybackMemoryDataStore.data.first()[MEMORY_KEY])
            .firstOrNull { it.providerType == providerType && it.profileId == profileId && it.movieId == movieId }

    override suspend fun list(providerType: ProviderType, profileId: String, mediaRoot: String): List<RemotePlaybackMemory> =
        decode(appContext.remotePlaybackMemoryDataStore.data.first()[MEMORY_KEY])
            .filter { memory ->
                memory.providerType == providerType &&
                    memory.profileId == profileId &&
                    memory.mediaRoot == mediaRoot
            }

    override suspend fun save(memory: RemotePlaybackMemory) {
        appContext.remotePlaybackMemoryDataStore.edit { prefs ->
            val entries = decode(prefs[MEMORY_KEY])
                .filterNot {
                    it.providerType == memory.providerType &&
                        it.profileId == memory.profileId &&
                        it.movieId == memory.movieId
                } + memory
            prefs[MEMORY_KEY] = remotePlaybackMemoryJson.encodeToString(entries)
        }
    }

    override suspend fun delete(providerType: ProviderType, profileId: String, movieId: Int) {
        appContext.remotePlaybackMemoryDataStore.edit { prefs ->
            val entries = decode(prefs[MEMORY_KEY])
                .filterNot { it.providerType == providerType && it.profileId == profileId && it.movieId == movieId }
            if (entries.isEmpty()) {
                prefs.remove(MEMORY_KEY)
            } else {
                prefs[MEMORY_KEY] = remotePlaybackMemoryJson.encodeToString(entries)
            }
        }
    }

    private fun decode(value: String?): List<RemotePlaybackMemory> =
        value
            ?.let { runCatching { remotePlaybackMemoryJson.decodeFromString<List<RemotePlaybackMemory>>(it) }.getOrNull() }
            .orEmpty()

    companion object {
        private val MEMORY_KEY = stringPreferencesKey("remote_playback_memory")
    }
}

fun remotePlaybackMemoryKey(providerType: ProviderType, profileId: String, movieId: Int): Pair<String, String> =
    remotePlaybackMemorySourceId(providerType, profileId) to movieId.toString()

fun remotePlaybackMemorySourceId(providerType: ProviderType, profileId: String): String =
    "remote:${providerType.name}:${profileId.remoteMemoryProfileId()}"

fun RemotePlaybackMemory.toMovieDto(): MovieDto = MovieDto(
    id = movieId,
    path = path,
    providerItemId = providerItemId,
    providerSeriesId = providerSeriesId,
    providerSeasonId = providerSeasonId,
    code = code,
    title = title,
    originalTitle = originalTitle,
    releaseDate = releaseDate,
    folderLevels = folderLevels,
    tags = tags,
    mediaRoot = mediaRoot,
    episodeTitle = episodeTitle,
    episodeNumber = episodeNumber,
    episodeLabel = episodeLabel,
    episodeStill = episodeStill,
    displayTitle = displayTitle,
    createdAt = createdAt,
    updatedAt = updatedAt ?: updatedAtMillis.toString(),
    playbackPosition = positionSeconds,
    progressPercent = progressPercent,
    scraperSource = providerType.name,
)

private fun MovieDto.toRemotePlaybackMemory(
    providerType: ProviderType,
    profileId: String,
    mediaRoot: String,
    positionSeconds: Double,
    durationSeconds: Double,
    updatedAtMillis: Long,
): RemotePlaybackMemory {
    val progressPercent = if (durationSeconds > 0.0) {
        (positionSeconds / durationSeconds * 100.0).coerceIn(0.0, 100.0)
    } else {
        progressPercent
    }
    return RemotePlaybackMemory(
        providerType = providerType,
        profileId = profileId,
        mediaRoot = mediaRoot,
        movieId = id,
        providerItemId = providerItemId,
        providerSeriesId = providerSeriesId,
        providerSeasonId = providerSeasonId,
        path = path,
        code = code,
        title = title,
        originalTitle = originalTitle,
        releaseDate = releaseDate,
        folderLevels = folderLevels,
        episodeTitle = episodeTitle,
        episodeNumber = episodeNumber,
        episodeLabel = episodeLabel,
        episodeStill = episodeStill,
        displayTitle = displayTitle,
        createdAt = createdAt,
        updatedAt = updatedAt,
        tags = tags,
        positionSeconds = positionSeconds,
        durationSeconds = durationSeconds,
        progressPercent = progressPercent,
        updatedAtMillis = updatedAtMillis,
    )
}

private fun String.remoteMemoryProfileId(): String = ifBlank { "default" }

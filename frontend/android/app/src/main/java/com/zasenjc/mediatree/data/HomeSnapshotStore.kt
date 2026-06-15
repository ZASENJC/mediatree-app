package com.zasenjc.mediatree.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.homeSnapshotDataStore by preferencesDataStore("mediatree_home_snapshot")

private val homeSnapshotJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class HomeSnapshot(
    val providerType: ProviderType,
    val profileId: String,
    val mediaRoot: String,
    val sortMode: String,
    val roots: List<MediaRootDto> = emptyList(),
    val recent: List<MovieDto> = emptyList(),
    val libraryItems: List<FolderNodeDto> = emptyList(),
    val updatedAtMillis: Long = 0L,
)

interface HomeSnapshotStore {
    suspend fun load(providerType: ProviderType, profileId: String, mediaRoot: String, sortMode: String): HomeSnapshot?
    suspend fun latest(providerType: ProviderType, profileId: String, sortMode: String): HomeSnapshot?
    suspend fun save(snapshot: HomeSnapshot)
}

class HomeSnapshotRepository(
    private val store: HomeSnapshotStore,
    private val clockMillis: () -> Long = { System.currentTimeMillis() },
) {
    suspend fun load(providerType: ProviderType, profileId: String, mediaRoot: String, sortMode: String): HomeSnapshot? {
        if (!providerType.supportsRemoteHomeSnapshot()) return null
        val resolvedProfileId = profileId.homeSnapshotProfileId()
        return if (mediaRoot.isBlank()) {
            store.latest(providerType, resolvedProfileId, sortMode)
        } else {
            store.load(providerType, resolvedProfileId, mediaRoot, sortMode)
        }
    }

    suspend fun save(
        providerType: ProviderType,
        profileId: String,
        mediaRoot: String,
        sortMode: String,
        roots: List<MediaRootDto>,
        recent: List<MovieDto>,
        libraryItems: List<FolderNodeDto>,
    ) {
        if (!providerType.supportsRemoteHomeSnapshot() || mediaRoot.isBlank()) return
        store.save(
            HomeSnapshot(
                providerType = providerType,
                profileId = profileId.homeSnapshotProfileId(),
                mediaRoot = mediaRoot,
                sortMode = sortMode,
                roots = roots,
                recent = recent,
                libraryItems = libraryItems,
                updatedAtMillis = clockMillis(),
            ),
        )
    }
}

class AndroidHomeSnapshotStore(context: Context) : HomeSnapshotStore {
    private val appContext = context.applicationContext

    override suspend fun load(
        providerType: ProviderType,
        profileId: String,
        mediaRoot: String,
        sortMode: String,
    ): HomeSnapshot? =
        decode(appContext.homeSnapshotDataStore.data.first()[SNAPSHOTS_KEY])
            .firstOrNull {
                it.providerType == providerType &&
                    it.profileId == profileId &&
                    it.mediaRoot == mediaRoot &&
                    it.sortMode == sortMode
            }

    override suspend fun latest(providerType: ProviderType, profileId: String, sortMode: String): HomeSnapshot? =
        decode(appContext.homeSnapshotDataStore.data.first()[SNAPSHOTS_KEY])
            .filter { it.providerType == providerType && it.profileId == profileId && it.sortMode == sortMode }
            .maxByOrNull { it.updatedAtMillis }

    override suspend fun save(snapshot: HomeSnapshot) {
        appContext.homeSnapshotDataStore.edit { prefs ->
            val entries = decode(prefs[SNAPSHOTS_KEY])
                .filterNot {
                    it.providerType == snapshot.providerType &&
                        it.profileId == snapshot.profileId &&
                        it.mediaRoot == snapshot.mediaRoot &&
                        it.sortMode == snapshot.sortMode
                } + snapshot
            prefs[SNAPSHOTS_KEY] = homeSnapshotJson.encodeToString(entries.takeLast(MaxHomeSnapshots))
        }
    }

    private fun decode(value: String?): List<HomeSnapshot> =
        value
            ?.let { runCatching { homeSnapshotJson.decodeFromString<List<HomeSnapshot>>(it) }.getOrNull() }
            .orEmpty()

    companion object {
        private const val MaxHomeSnapshots = 12
        private val SNAPSHOTS_KEY = stringPreferencesKey("home_snapshots")
    }
}

fun ProviderType.supportsRemoteHomeSnapshot(): Boolean = when (this) {
    ProviderType.MediaTree,
    ProviderType.Jellyfin -> true
    ProviderType.Emby,
    ProviderType.M3U,
    ProviderType.WebDAV,
    ProviderType.SMB -> false
}

private fun String.homeSnapshotProfileId(): String = ifBlank { "default" }

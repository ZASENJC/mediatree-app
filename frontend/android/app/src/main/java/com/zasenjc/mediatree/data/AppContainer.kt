package com.zasenjc.mediatree.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

class AppContainer(context: Context) {
    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val sessionStore = SessionStore(context)
    val uiPreferencesStore = UiPreferencesStore(context)
    val clientStorageStore = AndroidClientStorageStore(context)
    val clientStorageRepository = ClientStorageRepository(clientStorageStore)
    val clientPlaybackProgressStore = AndroidClientPlaybackProgressStore(context)
    val clientPlaybackProgressRepository = ClientPlaybackProgressRepository(clientPlaybackProgressStore)
    val remotePlaybackMemoryStore = AndroidRemotePlaybackMemoryStore(context)
    val remotePlaybackMemoryRepository = RemotePlaybackMemoryRepository(remotePlaybackMemoryStore)
    val homeSnapshotStore = AndroidHomeSnapshotStore(context)
    val homeSnapshotRepository = HomeSnapshotRepository(homeSnapshotStore)
    val m3uSubscriptionClient = M3uSubscriptionClient()
    val m3uSubscriptionCacheStore = AndroidM3uSubscriptionCacheStore(context)
    val m3uSubscriptionCacheRepository = M3uSubscriptionCacheRepository(m3uSubscriptionCacheStore, m3uSubscriptionClient)
    val m3uFavoritesStore = AndroidM3uFavoritesStore(context)
    val m3uFavoritesRepository = M3uFavoritesRepository(m3uFavoritesStore)
    val webDavClient = WebDavClient()
    val smbClient = SmbClient()
    val smbRangeProxy = SmbRangeProxy(smbClient)
    val mountedVideoThumbnailCache = MountedVideoThumbnailCache(context, this)
    val releaseUpdateChecker = ReleaseUpdateChecker()
    private val mediaTreeApi = MediaTreeApi(sessionStore)
    val mediaTreeProvider: MediaProvider = MediaTreeProvider(mediaTreeApi)
    val jellyfinProvider: MediaProvider = JellyfinProvider(sessionStore)
    val embyProvider: MediaProvider = EmbyProvider(sessionStore)
    val mediaProvider: MediaProvider = mediaTreeProvider
    val remotePlaybackMemoryCoordinator = RemotePlaybackMemoryCoordinator(remotePlaybackMemoryRepository) { type ->
        mediaProviderFor(type)
    }

    fun mediaProviderFor(type: ProviderType?): MediaProvider = when (type) {
        ProviderType.Jellyfin -> jellyfinProvider
        ProviderType.Emby -> embyProvider
        else -> mediaTreeProvider
    }

    fun registerProviderItemId(type: ProviderType?, movieId: Int, itemId: String) {
        when (type) {
            ProviderType.Jellyfin -> (jellyfinProvider as? JellyfinProvider)?.registerProviderItemId(movieId, itemId)
            ProviderType.Emby -> (embyProvider as? JellyfinProvider)?.registerProviderItemId(movieId, itemId)
            else -> Unit
        }
    }
}

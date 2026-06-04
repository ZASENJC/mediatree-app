package com.zasenjc.mediatree.storage

import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.RemotePlaybackMemory
import com.zasenjc.mediatree.data.RemotePlaybackMemoryRepository
import com.zasenjc.mediatree.data.RemotePlaybackMemoryStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RemotePlaybackMemoryRepositoryTest {
    @Test
    fun savesAndRestoresRemotePlaybackByProviderProfileAndMovie() = runTest {
        val repository = RemotePlaybackMemoryRepository(FakeRemotePlaybackMemoryStore(), clockMillis = { 1_000L })

        repository.save(
            providerType = ProviderType.Jellyfin,
            profileId = "jellyfin-home",
            mediaRoot = "shows",
            movie = movie(id = 42, providerItemId = "jf-42"),
            positionSeconds = 125.0,
            durationSeconds = 1_000.0,
        )

        assertEquals(
            125.0,
            repository.resumePosition(ProviderType.Jellyfin, "jellyfin-home", 42),
            0.001,
        )
        assertEquals(
            0.0,
            repository.resumePosition(ProviderType.Emby, "jellyfin-home", 42),
            0.001,
        )
    }

    @Test
    fun listContinueWatchingIsScopedSortedAndSkipsFinishedEntries() = runTest {
        var now = 1_000L
        val store = FakeRemotePlaybackMemoryStore()
        val repository = RemotePlaybackMemoryRepository(store, clockMillis = { now })

        repository.save(ProviderType.MediaTree, "main", "root-a", movie(1), 59.9, 1_000.0)
        now += 1_000L
        repository.save(ProviderType.Jellyfin, "main", "root-a", movie(2), 80.0, 1_000.0)
        now += 1_000L
        repository.save(ProviderType.MediaTree, "other", "root-a", movie(3), 90.0, 1_000.0)
        now += 1_000L
        repository.save(ProviderType.MediaTree, "main", "root-b", movie(4), 100.0, 1_000.0)
        now += 1_000L
        repository.save(ProviderType.MediaTree, "main", "root-a", movie(5), 120.0, 1_000.0)
        now += 1_000L
        repository.save(ProviderType.MediaTree, "main", "root-a", movie(6), 960.0, 1_000.0)

        val memories = repository.listContinueWatching(ProviderType.MediaTree, "main", "root-a", limit = 20)

        assertEquals(listOf(5), memories.map { it.movieId })
        assertNull(store.load(ProviderType.MediaTree, "main", 1))
        assertNull(store.load(ProviderType.MediaTree, "main", 6))
    }

    @Test
    fun remembersOneMinuteBoundaryAndRejectsAnythingEarlier() = runTest {
        val repository = RemotePlaybackMemoryRepository(FakeRemotePlaybackMemoryStore(), clockMillis = { 1_000L })

        repository.save(ProviderType.Jellyfin, "main", "root-a", movie(10), 59.9, 1_000.0)
        repository.save(ProviderType.Jellyfin, "main", "root-a", movie(11), 60.0, 1_000.0)

        assertEquals(0.0, repository.resumePosition(ProviderType.Jellyfin, "main", 10), 0.001)
        assertEquals(60.0, repository.resumePosition(ProviderType.Jellyfin, "main", 11), 0.001)
    }

    @Test
    fun transientInvalidProgressDoesNotClearExistingUsefulMemory() = runTest {
        val repository = RemotePlaybackMemoryRepository(FakeRemotePlaybackMemoryStore(), clockMillis = { 1_000L })

        repository.save(ProviderType.MediaTree, "main", "root-a", movie(12), 180.0, 1_000.0)
        repository.save(ProviderType.MediaTree, "main", "root-a", movie(12), 0.0, 0.0)
        repository.save(ProviderType.MediaTree, "main", "root-a", movie(12), 42.0, 1_000.0)

        assertEquals(180.0, repository.resumePosition(ProviderType.MediaTree, "main", 12), 0.001)
    }

    private fun movie(id: Int, providerItemId: String = "provider-$id"): MovieDto = MovieDto(
        id = id,
        path = providerItemId,
        providerItemId = providerItemId,
        code = "S01E$id",
        title = "Series",
        displayTitle = "Episode $id",
        releaseDate = "2026-01-${id.toString().padStart(2, '0')}",
        episodeNumber = id,
        episodeLabel = "E$id",
    )
}

private class FakeRemotePlaybackMemoryStore : RemotePlaybackMemoryStore {
    private val items = mutableMapOf<Triple<ProviderType, String, Int>, RemotePlaybackMemory>()

    override suspend fun load(providerType: ProviderType, profileId: String, movieId: Int): RemotePlaybackMemory? =
        items[Triple(providerType, profileId, movieId)]

    override suspend fun list(providerType: ProviderType, profileId: String, mediaRoot: String): List<RemotePlaybackMemory> =
        items.values.filter {
            it.providerType == providerType &&
                it.profileId == profileId &&
                it.mediaRoot == mediaRoot
        }

    override suspend fun save(memory: RemotePlaybackMemory) {
        items[Triple(memory.providerType, memory.profileId, memory.movieId)] = memory
    }

    override suspend fun delete(providerType: ProviderType, profileId: String, movieId: Int) {
        items.remove(Triple(providerType, profileId, movieId))
    }
}

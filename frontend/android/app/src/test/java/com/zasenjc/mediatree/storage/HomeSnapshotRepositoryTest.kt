package com.zasenjc.mediatree.storage

import com.zasenjc.mediatree.data.FolderNodeDto
import com.zasenjc.mediatree.data.HomeSnapshot
import com.zasenjc.mediatree.data.HomeSnapshotRepository
import com.zasenjc.mediatree.data.HomeSnapshotStore
import com.zasenjc.mediatree.data.MediaRootDto
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.supportsRemoteHomeSnapshot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeSnapshotRepositoryTest {
    @Test
    fun savesAndLoadsRemoteHomeSnapshotsByProviderProfileRootAndSort() = runTest {
        val repository = HomeSnapshotRepository(FakeHomeSnapshotStore(), clockMillis = { 10_000L })

        repository.save(
            providerType = ProviderType.MediaTree,
            profileId = "main",
            mediaRoot = "root-a",
            sortMode = "created_desc",
            roots = listOf(MediaRootDto(path = "root-a", label = "Root A")),
            recent = listOf(MovieDto(id = 1, code = "E01")),
            libraryItems = listOf(FolderNodeDto(path = "folder-a", name = "Folder A", movieCount = 1)),
        )

        val snapshot = repository.load(ProviderType.MediaTree, "main", "root-a", "created_desc")

        assertEquals("root-a", snapshot?.mediaRoot)
        assertEquals(listOf("folder-a"), snapshot?.libraryItems?.map { it.path })
        assertEquals(10_000L, snapshot?.updatedAtMillis)
        assertNull(repository.load(ProviderType.MediaTree, "main", "root-a", "title_asc"))
        assertNull(repository.load(ProviderType.Jellyfin, "main", "root-a", "created_desc"))
    }

    @Test
    fun emptyMediaRootLoadsLatestSnapshotForProviderProfileAndSort() = runTest {
        var now = 1_000L
        val repository = HomeSnapshotRepository(FakeHomeSnapshotStore(), clockMillis = { now })

        repository.save(ProviderType.Jellyfin, "jf", "root-old", "created_desc", emptyList(), emptyList(), emptyList())
        now = 2_000L
        repository.save(
            ProviderType.Jellyfin,
            "jf",
            "root-new",
            "created_desc",
            roots = listOf(MediaRootDto(path = "root-new", label = "Root New")),
            recent = emptyList(),
            libraryItems = listOf(FolderNodeDto(path = "new", name = "New", movieCount = 1)),
        )

        val snapshot = repository.load(ProviderType.Jellyfin, "jf", "", "created_desc")

        assertEquals("root-new", snapshot?.mediaRoot)
        assertEquals(listOf("new"), snapshot?.libraryItems?.map { it.path })
    }

    @Test
    fun cacheIsLimitedToMediaTreeAndJellyfinHomeProviders() = runTest {
        val store = FakeHomeSnapshotStore()
        val repository = HomeSnapshotRepository(store)

        repository.save(ProviderType.Emby, "emby", "root", "created_desc", emptyList(), emptyList(), emptyList())
        repository.save(ProviderType.SMB, "smb", "root", "created_desc", emptyList(), emptyList(), emptyList())

        assertTrue(ProviderType.MediaTree.supportsRemoteHomeSnapshot())
        assertTrue(ProviderType.Jellyfin.supportsRemoteHomeSnapshot())
        assertFalse(ProviderType.Emby.supportsRemoteHomeSnapshot())
        assertFalse(ProviderType.SMB.supportsRemoteHomeSnapshot())
        assertFalse(ProviderType.WebDAV.supportsRemoteHomeSnapshot())
        assertNull(repository.load(ProviderType.Emby, "emby", "root", "created_desc"))
        assertNull(repository.load(ProviderType.SMB, "smb", "root", "created_desc"))
        assertEquals(0, store.saved.size)
    }
}

private class FakeHomeSnapshotStore : HomeSnapshotStore {
    val saved = mutableListOf<HomeSnapshot>()

    override suspend fun load(
        providerType: ProviderType,
        profileId: String,
        mediaRoot: String,
        sortMode: String,
    ): HomeSnapshot? =
        saved.firstOrNull {
            it.providerType == providerType &&
                it.profileId == profileId &&
                it.mediaRoot == mediaRoot &&
                it.sortMode == sortMode
        }

    override suspend fun latest(providerType: ProviderType, profileId: String, sortMode: String): HomeSnapshot? =
        saved
            .filter { it.providerType == providerType && it.profileId == profileId && it.sortMode == sortMode }
            .maxByOrNull { it.updatedAtMillis }

    override suspend fun save(snapshot: HomeSnapshot) {
        saved.removeAll {
            it.providerType == snapshot.providerType &&
                it.profileId == snapshot.profileId &&
                it.mediaRoot == snapshot.mediaRoot &&
                it.sortMode == snapshot.sortMode
        }
        saved += snapshot
    }
}

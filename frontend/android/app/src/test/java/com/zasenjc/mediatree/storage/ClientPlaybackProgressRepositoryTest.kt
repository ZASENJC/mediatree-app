package com.zasenjc.mediatree.storage

import com.zasenjc.mediatree.data.ClientPlaybackProgress
import com.zasenjc.mediatree.data.ClientPlaybackProgressRepository
import com.zasenjc.mediatree.data.ClientPlaybackProgressStore
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class ClientPlaybackProgressRepositoryTest {
    @Test
    fun restoresSavedPositionByStorageSourceAndPath() = runTest {
        val repository = ClientPlaybackProgressRepository(FakeClientPlaybackProgressStore())

        repository.save(
            sourceId = "smb-nas",
            path = "/Movies/Film.mkv",
            positionSeconds = 123.5,
            durationSeconds = 1_000.0,
        )

        assertEquals(123.5, repository.resumePosition("smb-nas", "/Movies/Film.mkv"), 0.001)
        assertEquals(0.0, repository.resumePosition("other-source", "/Movies/Film.mkv"), 0.001)
        assertEquals(0.0, repository.resumePosition("smb-nas", "/Movies/Other.mkv"), 0.001)
    }

    @Test
    fun doesNotRememberPositionsBeforeOneMinute() = runTest {
        val repository = ClientPlaybackProgressRepository(FakeClientPlaybackProgressStore())

        repository.save(
            sourceId = "webdav-home",
            path = "/Series/E01.mp4",
            positionSeconds = 59.9,
            durationSeconds = 1_200.0,
        )

        assertEquals(0.0, repository.resumePosition("webdav-home", "/Series/E01.mp4"), 0.001)
    }

    @Test
    fun remembersPositionsAtOneMinuteBoundary() = runTest {
        val repository = ClientPlaybackProgressRepository(FakeClientPlaybackProgressStore())

        repository.save(
            sourceId = "smb-nas",
            path = "/Series/E02.mp4",
            positionSeconds = 60.0,
            durationSeconds = 1_200.0,
        )

        assertEquals(60.0, repository.resumePosition("smb-nas", "/Series/E02.mp4"), 0.001)
    }

    @Test
    fun clearsResumePositionWhenVideoWasNearlyFinished() = runTest {
        val store = FakeClientPlaybackProgressStore()
        val repository = ClientPlaybackProgressRepository(store)

        repository.save(
            sourceId = "smb-nas",
            path = "/Movies/Finished.mkv",
            positionSeconds = 950.0,
            durationSeconds = 1_000.0,
        )

        assertEquals(0.0, repository.resumePosition("smb-nas", "/Movies/Finished.mkv"), 0.001)
        assertEquals(null, store.load("smb-nas", "/Movies/Finished.mkv"))
    }

    @Test
    fun keepsUsefulPositionWhenDurationIsUnknown() = runTest {
        val repository = ClientPlaybackProgressRepository(FakeClientPlaybackProgressStore())

        repository.save(
            sourceId = "webdav-home",
            path = "/Raw/Clip.mov",
            positionSeconds = 61.0,
            durationSeconds = 0.0,
        )

        assertEquals(61.0, repository.resumePosition("webdav-home", "/Raw/Clip.mov"), 0.001)
    }
}

private class FakeClientPlaybackProgressStore : ClientPlaybackProgressStore {
    private val items = mutableMapOf<Pair<String, String>, ClientPlaybackProgress>()

    override suspend fun load(sourceId: String, path: String): ClientPlaybackProgress? =
        items[sourceId to path]

    override suspend fun list(sourceId: String): List<ClientPlaybackProgress> =
        items.values.filter { it.sourceId == sourceId }

    override suspend fun save(progress: ClientPlaybackProgress) {
        items[progress.sourceId to progress.path] = progress
    }

    override suspend fun delete(sourceId: String, path: String) {
        items.remove(sourceId to path)
    }
}

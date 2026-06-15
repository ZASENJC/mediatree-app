package com.zasenjc.mediatree.storage

import com.zasenjc.mediatree.data.AuthStatusDto
import com.zasenjc.mediatree.data.FolderTreeResponseDto
import com.zasenjc.mediatree.data.MediaInfoDto
import com.zasenjc.mediatree.data.MediaProvider
import com.zasenjc.mediatree.data.MediaRootsResponseDto
import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.MoviesResponseDto
import com.zasenjc.mediatree.data.OkResponseDto
import com.zasenjc.mediatree.data.ProgressDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.RemotePlaybackMemory
import com.zasenjc.mediatree.data.RemotePlaybackMemoryCoordinator
import com.zasenjc.mediatree.data.RemotePlaybackMemoryRepository
import com.zasenjc.mediatree.data.RemotePlaybackMemoryStore
import com.zasenjc.mediatree.data.SaveProgressResponseDto
import com.zasenjc.mediatree.data.ScanResponseDto
import com.zasenjc.mediatree.data.SubtitleTrackDto
import com.zasenjc.mediatree.data.LoginResponseDto
import com.zasenjc.mediatree.playback.PlaybackSource
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RemotePlaybackMemoryCoordinatorTest {
    @Test
    fun exitProgressOverwritesThresholdProgressAndSyncsMediaTreeBackend() = runTest {
        val store = FakeCoordinatorRemotePlaybackMemoryStore()
        val repository = RemotePlaybackMemoryRepository(store, clockMillis = { 1_000L })
        val provider = FakeMediaProvider()
        val coordinator = RemotePlaybackMemoryCoordinator(repository) { provider }

        coordinator.recordProgress(ProviderType.MediaTree, "main", "root-a", movie(42), 65.0, 1_000.0)
        coordinator.recordExit(ProviderType.MediaTree, "main", "root-a", movie(42), 186.5, 1_000.0)

        assertEquals(186.5, repository.resumePosition(ProviderType.MediaTree, "main", 42), 0.001)
        assertEquals(
            listOf(SavedProgress(movieId = 42, position = 65.0, stopped = false), SavedProgress(movieId = 42, position = 186.5, stopped = true)),
            provider.savedProgress,
        )
    }

    @Test
    fun invalidExitSnapshotDoesNotClearExistingMemoryOrSyncBackend() = runTest {
        val store = FakeCoordinatorRemotePlaybackMemoryStore()
        val repository = RemotePlaybackMemoryRepository(store, clockMillis = { 1_000L })
        val provider = FakeMediaProvider()
        val coordinator = RemotePlaybackMemoryCoordinator(repository) { provider }

        coordinator.recordProgress(ProviderType.MediaTree, "main", "root-a", movie(7), 140.0, 1_000.0)
        coordinator.recordExit(ProviderType.MediaTree, "main", "root-a", movie(7), 0.0, 0.0)

        assertEquals(140.0, repository.resumePosition(ProviderType.MediaTree, "main", 7), 0.001)
        assertEquals(listOf(SavedProgress(movieId = 7, position = 140.0, stopped = false)), provider.savedProgress)
    }

    @Test
    fun resumePrefersLocalMemoryThenFallsBackToBackendProgress() = runTest {
        val repository = RemotePlaybackMemoryRepository(FakeCoordinatorRemotePlaybackMemoryStore(), clockMillis = { 1_000L })
        val provider = FakeMediaProvider(progressByMovieId = mutableMapOf(11 to ProgressDto(position = 125.0, progressPercent = 25.0)))
        val coordinator = RemotePlaybackMemoryCoordinator(repository) { provider }

        repository.save(ProviderType.MediaTree, "main", "root-a", movie(10), 240.0, 1_000.0)

        assertEquals(240.0, coordinator.resumePosition(ProviderType.MediaTree, "main", 10), 0.001)
        assertEquals(125.0, coordinator.resumePosition(ProviderType.MediaTree, "main", 11), 0.001)
    }

    private fun movie(id: Int): MovieDto = MovieDto(
        id = id,
        path = "/Series/E$id.mp4",
        code = "S01E$id",
        title = "Series",
        displayTitle = "Episode $id",
        mediaRoot = "root-a",
    )
}

private data class SavedProgress(
    val movieId: Int,
    val position: Double,
    val stopped: Boolean,
)

private class FakeCoordinatorRemotePlaybackMemoryStore : RemotePlaybackMemoryStore {
    private val items = mutableMapOf<Triple<ProviderType, String, Int>, RemotePlaybackMemory>()

    override suspend fun load(providerType: ProviderType, profileId: String, movieId: Int): RemotePlaybackMemory? =
        items[Triple(providerType, profileId, movieId)]

    override suspend fun list(providerType: ProviderType, profileId: String, mediaRoot: String): List<RemotePlaybackMemory> =
        items.values.filter { it.providerType == providerType && it.profileId == profileId && it.mediaRoot == mediaRoot }

    override suspend fun save(memory: RemotePlaybackMemory) {
        items[Triple(memory.providerType, memory.profileId, memory.movieId)] = memory
    }

    override suspend fun delete(providerType: ProviderType, profileId: String, movieId: Int) {
        items.remove(Triple(providerType, profileId, movieId))
    }
}

private class FakeMediaProvider(
    val progressByMovieId: MutableMap<Int, ProgressDto> = mutableMapOf(),
) : MediaProvider {
    val savedProgress = mutableListOf<SavedProgress>()

    override suspend fun authStatus(serverUrl: String?): AuthStatusDto = AuthStatusDto()
    override suspend fun login(serverUrl: String, username: String, password: String): LoginResponseDto = LoginResponseDto()
    override suspend fun mediaRoots(): MediaRootsResponseDto = MediaRootsResponseDto()
    override suspend fun folders(mediaRoot: String): FolderTreeResponseDto = FolderTreeResponseDto()
    override suspend fun recentWatched(limit: Int, offset: Int, mediaRoot: String): MoviesResponseDto = MoviesResponseDto()
    override suspend fun movies(folder: String, code: String, tag: String, sort: String, limit: Int, offset: Int, mediaRoot: String): MoviesResponseDto = MoviesResponseDto()
    override suspend fun favorites(limit: Int, offset: Int, sort: String, mediaRoot: String): MoviesResponseDto = MoviesResponseDto()
    override suspend fun detail(movieId: Int): MovieDto = MovieDto(id = movieId)
    override suspend fun progress(movieId: Int): ProgressDto = progressByMovieId[movieId] ?: ProgressDto()
    override suspend fun saveProgress(movieId: Int, position: Double, duration: Double?, stopped: Boolean): SaveProgressResponseDto {
        savedProgress += SavedProgress(movieId = movieId, position = position, stopped = stopped)
        return SaveProgressResponseDto(ok = true)
    }
    override suspend fun subtitleTracks(movieId: Int): List<SubtitleTrackDto> = emptyList()
    override suspend fun mediaInfo(movieId: Int): MediaInfoDto = MediaInfoDto()
    override suspend fun addTag(movieId: Int, tag: String): OkResponseDto = OkResponseDto(ok = true)
    override suspend fun removeTag(movieId: Int, tag: String): OkResponseDto = OkResponseDto(ok = true)
    override suspend fun scan(mediaRoot: String): ScanResponseDto = ScanResponseDto()
    override fun coverUrl(serverUrl: String, movieId: Int): String = ""
    override fun episodeStillUrl(serverUrl: String, movieId: Int): String = ""
    override fun thumbnailUrl(serverUrl: String, movieId: Int, index: Int): String = ""
    override fun streamUrl(serverUrl: String, movieId: Int): String = ""
    override fun subtitleUrl(serverUrl: String, movieId: Int, trackIndex: Int): String = ""
    override fun playbackSource(serverUrl: String, movieId: Int, token: String, userId: String, mediaToken: String, subtitleTracks: List<SubtitleTrackDto>): PlaybackSource =
        PlaybackSource.mediaTree(serverUrl = serverUrl, movieId = movieId, token = token)
}

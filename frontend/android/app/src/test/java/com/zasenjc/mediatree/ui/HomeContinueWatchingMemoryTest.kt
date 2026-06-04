package com.zasenjc.mediatree.ui

import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.RemotePlaybackMemory
import com.zasenjc.mediatree.ui.screens.mergeContinueWatchingWithMemory
import org.junit.Assert.assertEquals
import org.junit.Test

class HomeContinueWatchingMemoryTest {
    @Test
    fun localRemoteMemoryIsMergedAheadOfDelayedProviderRecentAndDeduplicated() {
        val providerRecent = listOf(
            movie(id = 42, progress = 12.0, title = "Provider old"),
            movie(id = 7, progress = 33.0, title = "Provider only"),
        )
        val localMemory = listOf(
            memory(movieId = 42, updatedAtMillis = 3_000L, position = 60.0, duration = 100.0),
        )

        val merged = mergeContinueWatchingWithMemory(providerRecent, localMemory, limit = 20)

        assertEquals(listOf(42, 7), merged.map { it.id })
        assertEquals(60.0, merged.first().playbackPosition ?: 0.0, 0.001)
        assertEquals(60.0, merged.first().progressPercent ?: 0.0, 0.001)
        assertEquals("Local 42", merged.first().displayTitle)
    }

    @Test
    fun newerLocalMemoriesKeepTheirRecentOrderBeforeProviderOnlyItems() {
        val merged = mergeContinueWatchingWithMemory(
            providerRecent = listOf(movie(id = 9, progress = 20.0, title = "Provider only")),
            localMemories = listOf(
                memory(movieId = 1, updatedAtMillis = 1_000L, position = 10.0, duration = 100.0),
                memory(movieId = 2, updatedAtMillis = 2_000L, position = 20.0, duration = 100.0),
            ),
            limit = 20,
        )

        assertEquals(listOf(2, 1, 9), merged.map { it.id })
    }

    private fun movie(id: Int, progress: Double, title: String): MovieDto = MovieDto(
        id = id,
        path = "provider-$id",
        providerItemId = "provider-$id",
        code = "S01E$id",
        title = title,
        displayTitle = title,
        progressPercent = progress,
    )

    private fun memory(
        movieId: Int,
        updatedAtMillis: Long,
        position: Double,
        duration: Double,
    ): RemotePlaybackMemory = RemotePlaybackMemory(
        providerType = ProviderType.MediaTree,
        profileId = "main",
        mediaRoot = "root",
        movieId = movieId,
        providerItemId = "provider-$movieId",
        path = "provider-$movieId",
        code = "S01E$movieId",
        title = "Local series",
        displayTitle = "Local $movieId",
        episodeNumber = movieId,
        episodeLabel = "E$movieId",
        positionSeconds = position,
        durationSeconds = duration,
        progressPercent = position / duration * 100.0,
        updatedAtMillis = updatedAtMillis,
    )
}

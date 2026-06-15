package com.zasenjc.mediatree.ui

import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.ui.screens.detailStillImages
import org.junit.Assert.assertEquals
import org.junit.Test

class DetailStillsTest {
    @Test
    fun mediaTreeJavdatabaseStillsKeepEveryBackendThumbnailWithOriginalFallbacks() {
        val movie = MovieDto(
            id = 42,
            javdbThumbnails = (1..16).map { index -> "https://img.javdatabase.example/still-$index.jpg" },
        )

        val stills = detailStillImages(
            movie = movie,
            serverUrl = "http://media.local:27580",
            providerType = ProviderType.MediaTree,
            fallbackStill = "http://media.local:27580/api/episode-still/42",
            thumbnailUrl = { movieId, index -> "http://media.local:27580/api/thumbnail/$movieId/$index" },
        )

        assertEquals(17, stills.size)
        assertEquals("http://media.local:27580/api/episode-still/42", stills.first().url)
        assertEquals(
            (0..15).map { index -> "http://media.local:27580/api/thumbnail/42/$index" },
            stills.drop(1).map { it.url },
        )
        assertEquals(
            (1..16).map { index -> "https://img.javdatabase.example/still-$index.jpg" },
            stills.drop(1).map { it.fallbackUrl },
        )
    }

    @Test
    fun mediaBrowserStillsUseResolvedBackdropUrlsDirectly() {
        val movie = MovieDto(
            id = 7,
            episodeStill = "https://jellyfin.example.com/Items/7/Images/Primary",
            javdbThumbnails = listOf("https://jellyfin.example.com/Items/7/Images/Backdrop?tag=a"),
        )

        val stills = detailStillImages(
            movie = movie,
            serverUrl = "https://jellyfin.example.com",
            providerType = ProviderType.Jellyfin,
            fallbackStill = "https://jellyfin.example.com/Items/7/Images/Primary",
            thumbnailUrl = { _, _ -> error("Jellyfin stills should not use the MediaTree thumbnail proxy") },
        )

        assertEquals(
            listOf(
                "https://jellyfin.example.com/Items/7/Images/Primary",
                "https://jellyfin.example.com/Items/7/Images/Backdrop?tag=a",
            ),
            stills.map { it.url },
        )
        assertEquals(listOf(null, null), stills.map { it.fallbackUrl })
    }
}

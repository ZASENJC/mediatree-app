package com.zasenjc.mediatree.ui.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaAsyncImageAuthTest {
    @Test
    fun addsAuthorizationOnlyForCurrentMediaTreeProtectedImageRoutes() {
        val auth = MediaTreeImageAuth(serverUrl = "http://server", token = "token")

        assertEquals(
            mapOf("Authorization" to "Bearer token"),
            mediaTreeImageHeaders("http://server/api/cover/42", auth),
        )
        assertEquals(
            mapOf("Authorization" to "Bearer token"),
            mediaTreeImageHeaders("http://server/api/episode-still/42?size=poster", auth),
        )
        assertEquals(
            mapOf("Authorization" to "Bearer token"),
            mediaTreeImageHeaders("/api/media/folder/file.jpg", auth),
        )

        assertTrue(mediaTreeImageHeaders("http://server/api/cached-cover/poster.jpg", auth).isEmpty())
        assertTrue(mediaTreeImageHeaders("https://image.tmdb.org/t/p/w500/poster.jpg", auth).isEmpty())
        assertTrue(mediaTreeImageHeaders("http://other/api/cover/42", auth).isEmpty())
        assertTrue(mediaTreeImageHeaders("http://server/api/cover/42", auth.copy(token = "")).isEmpty())
    }
}

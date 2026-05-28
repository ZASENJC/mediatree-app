package com.zasenjc.mediatree.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlaybackSourceTest {
    @Test
    fun mediaTreeSourceUsesBackendStreamEndpointWithAuthorizationHeader() {
        val source: PlaybackSource = PlaybackSource.mediaTree(
            serverUrl = "http://192.168.1.10:27580/",
            movieId = 42,
            token = "secret-token",
            subtitleTracks = listOf(PlaybackSubtitleTrack(index = 3, title = "zh-CN")),
        )

        assertTrue(source is HttpPlaybackSource)
        assertEquals("http://192.168.1.10:27580/api/stream/42", source.uri)
        assertEquals(mapOf("Authorization" to "Bearer secret-token"), source.headers)
        assertEquals("http://192.168.1.10:27580/api/subtitle/42/3", source.subtitleUri(3))
        assertEquals(listOf(PlaybackSubtitleTrack(index = 3, title = "zh-CN")), source.subtitleTracks)
    }

    @Test
    fun mediaTreeSourceOmitsBlankAuthorizationHeader() {
        val source = PlaybackSource.mediaTree(
            serverUrl = "server.local",
            movieId = 7,
            token = "",
        )

        assertEquals("http://server.local/api/stream/7", source.uri)
        assertEquals(emptyMap<String, String>(), source.headers)
    }

    @Test
    fun directPlaybackSourcesExposeTypedUrisAndHeaders() {
        val webDav = WebDavPlaybackSource(
            uri = "https://dav.example.com/video.mkv",
            headers = mapOf("Authorization" to "Basic abc"),
        )
        val smb = SmbPlaybackSource(
            share = "smb://nas/media/video.mkv",
            proxyUri = "http://127.0.0.1:19191/smb/video.mkv",
        )
        val proxy = LocalProxyPlaybackSource(
            uri = "http://127.0.0.1:19191/proxy/video.mkv",
            origin = "smb://nas/media/video.mkv",
        )

        assertEquals("https://dav.example.com/video.mkv", webDav.uri)
        assertEquals(mapOf("Authorization" to "Basic abc"), webDav.headers)
        assertEquals("http://127.0.0.1:19191/smb/video.mkv", smb.uri)
        assertEquals("smb://nas/media/video.mkv", smb.share)
        assertEquals("http://127.0.0.1:19191/proxy/video.mkv", proxy.uri)
        assertEquals("smb://nas/media/video.mkv", proxy.origin)
    }

    @Test
    fun detailScreenUsesPlaybackSourceInsteadOfStreamHelpers() {
        val appRoot = File(System.getProperty("user.dir") ?: ".")
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()

        assertTrue(detailScreen.contains("PlaybackSource.mediaTree"))
        assertFalse(detailScreen.contains(".streamUrl("))
        assertFalse(detailScreen.contains(".subtitleUrl("))
    }
}

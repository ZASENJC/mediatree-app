package com.zasenjc.mediatree.data

import com.zasenjc.mediatree.playback.PlaybackSource
import com.zasenjc.mediatree.playback.WebDavPlaybackSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebDavClientTest {
    @Test
    fun parsesPropfindDirectoryListingAndSkipsCurrentDirectory() {
        val source = webDavSource()
        val entries = WebDavClient.parseDirectoryListing(
            source = source,
            currentPath = "",
            xml = """
                <?xml version="1.0" encoding="utf-8" ?>
                <d:multistatus xmlns:d="DAV:">
                  <d:response>
                    <d:href>/remote.php/dav/files/alice/</d:href>
                    <d:propstat><d:prop><d:resourcetype><d:collection/></d:resourcetype></d:prop></d:propstat>
                  </d:response>
                  <d:response>
                    <d:href>/remote.php/dav/files/alice/Movies/</d:href>
                    <d:propstat><d:prop>
                      <d:displayname>Movies</d:displayname>
                      <d:resourcetype><d:collection/></d:resourcetype>
                      <d:getlastmodified>Wed, 29 May 2026 01:20:00 GMT</d:getlastmodified>
                    </d:prop></d:propstat>
                  </d:response>
                  <d:response>
                    <d:href>/remote.php/dav/files/alice/video%20one.mkv</d:href>
                    <d:propstat><d:prop>
                      <d:getcontentlength>1234567</d:getcontentlength>
                      <d:getcontenttype>video/x-matroska</d:getcontenttype>
                    </d:prop></d:propstat>
                  </d:response>
                </d:multistatus>
            """.trimIndent(),
        )

        assertEquals(2, entries.size)
        assertEquals("Movies", entries[0].name)
        assertEquals("Movies", entries[0].path)
        assertTrue(entries[0].isDirectory)
        assertEquals("video one.mkv", entries[1].name)
        assertEquals("video one.mkv", entries[1].path)
        assertFalse(entries[1].isDirectory)
        assertTrue(entries[1].isPlayableVideo)
        assertEquals(1234567L, entries[1].sizeBytes)
    }

    @Test
    fun buildsBasicAndBearerAuthorizationHeaders() {
        val basic = webDavSource(username = "alice", secret = "secret")
        val bearer = webDavSource(
            username = "",
            secret = "token-value",
            authType = ClientStorageAuthType.Bearer,
        )

        assertEquals(
            mapOf("Authorization" to "Basic YWxpY2U6c2VjcmV0"),
            WebDavClient.authorizationHeaders(basic),
        )
        assertEquals(
            mapOf("Authorization" to "Bearer token-value"),
            WebDavClient.authorizationHeaders(bearer),
        )
    }

    @Test
    fun buildsWebDavPlaybackSourceForDirectMpvPlayback() {
        val source = webDavSource(username = "", secret = "token-value", authType = ClientStorageAuthType.Bearer)

        val playbackSource = PlaybackSource.webDav(
            source = source,
            path = "Movies/video one.mkv",
        )

        assertTrue(playbackSource is WebDavPlaybackSource)
        assertEquals("https://dav.example.com/remote.php/dav/files/alice/Movies/video%20one.mkv", playbackSource.uri)
        assertEquals(mapOf("Authorization" to "Bearer token-value"), playbackSource.headers)
    }

    @Test
    fun webDavClientUsesPropfindDepthOneWithoutBackendScan() {
        val appRoot = File(System.getProperty("user.dir") ?: ".")
        val webDavClientSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/WebDavClient.kt")
            .readText()

        assertTrue(webDavClientSource.contains("\"PROPFIND\""))
        assertTrue(webDavClientSource.contains("\"Depth\""))
        assertTrue(webDavClientSource.contains("\"1\""))
        assertFalse(webDavClientSource.contains("/scan"))
        assertFalse(webDavClientSource.contains("MediaTreeApi"))
    }

    private fun webDavSource(
        username: String = "alice",
        secret: String = "secret",
        authType: ClientStorageAuthType = ClientStorageAuthType.Basic,
    ): ClientStorageSource = ClientStorageSource(
        id = "webdav-1",
        type = ClientStorageType.WebDAV,
        name = "WebDAV",
        endpoint = "https://dav.example.com/remote.php/dav/files/alice",
        username = username,
        secret = secret,
        authType = authType,
    )
}

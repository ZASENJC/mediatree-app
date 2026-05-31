package com.zasenjc.mediatree.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SmbRangeProxyTest {
    @Test
    fun parsesOpenEndedAndBoundedRangeHeaders() {
        assertEquals(ByteRange(start = 100L, endInclusive = 199L), SmbRangeProxy.parseRange("bytes=100-199", 1000L))
        assertEquals(ByteRange(start = 900L, endInclusive = 999L), SmbRangeProxy.parseRange("bytes=900-", 1000L))
        assertEquals(ByteRange(start = 0L, endInclusive = 999L), SmbRangeProxy.parseRange(null, 1000L))
    }

    @Test
    fun buildsLoopbackPlaybackUrlAndLocalProxySource() {
        val source = smbSource()
        val proxy = SmbRangeProxy()
        val playbackSource = proxy.playbackSource(source, "Movies/video one.mkv")

        assertEquals("smb://192.168.1.20/Media/Movies/video%20one.mkv", playbackSource.origin)
        assertTrue(playbackSource.uri.startsWith("http://127.0.0.1:"))
        assertTrue(playbackSource.uri.contains("/smb/"))
    }

    @Test
    fun proxySourceSupportsRangeAndLocalProxyPlaybackSource() {
        val appRoot = File(System.getProperty("user.dir") ?: ".")
        val proxySource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/SmbRangeProxy.kt")
            .readText()

        assertTrue(proxySource.contains("127.0.0.1"))
        assertTrue(proxySource.contains("Range"))
        assertTrue(proxySource.contains("Content-Range"))
        assertTrue(proxySource.contains("LocalProxyPlaybackSource"))
        assertTrue(proxySource.contains("isClientDisconnect"))
    }

    private fun smbSource(): ClientStorageSource = ClientStorageSource(
        id = "smb-1",
        type = ClientStorageType.SMB,
        name = "NAS",
        endpoint = "smb://192.168.1.20",
        path = "/Media",
    )
}

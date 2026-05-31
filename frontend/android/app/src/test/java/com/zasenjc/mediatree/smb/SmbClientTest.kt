package com.zasenjc.mediatree.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SmbClientTest {
    @Test
    fun buildsSmbUrlFromSourceAndRelativePath() {
        val source = smbSource()

        assertEquals("smb://192.168.1.20/Media", SmbClient.buildSmbUrl(source, ""))
        assertEquals("smb://192.168.1.20/Media/Movies/video%20one.mkv", SmbClient.buildSmbUrl(source, "Movies/video one.mkv"))
    }

    @Test
    fun mapsRemoteFilesToBrowserEntries() {
        val source = smbSource()
        val entries = SmbClient.toEntries(
            source = source,
            currentPath = "Movies",
            files = listOf(
                SmbRemoteFile(name = "Season 1", isDirectory = true, sizeBytes = 0L, modified = 10L),
                SmbRemoteFile(name = "video.mkv", isDirectory = false, sizeBytes = 1024L, modified = 20L),
                SmbRemoteFile(name = "readme.txt", isDirectory = false, sizeBytes = 32L, modified = 30L),
            ),
        )

        assertEquals("Season 1", entries[0].name)
        assertEquals("Movies/Season 1", entries[0].path)
        assertTrue(entries[0].isDirectory)
        assertEquals("video.mkv", entries[1].name)
        assertTrue(entries[1].isPlayableVideo)
        assertFalse(entries[2].isPlayableVideo)
    }

    @Test
    fun smbClientUsesSmbjWithoutMediaTreeBackendCalls() {
        val appRoot = File(System.getProperty("user.dir") ?: ".")
        val clientSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/SmbClient.kt")
            .readText()

        assertTrue(clientSource.contains("com.hierynomus.smbj.SMBClient"))
        assertFalse(clientSource.contains("MediaTreeApi"))
        assertFalse(clientSource.contains("/scan"))
        assertFalse(clientSource.contains("mediaRoots"))
    }

    private fun smbSource(): ClientStorageSource = ClientStorageSource(
        id = "smb-1",
        type = ClientStorageType.SMB,
        name = "NAS",
        endpoint = "smb://192.168.1.20",
        path = "/Media",
        username = "guest",
        secret = "secret",
    )
}

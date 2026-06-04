package com.zasenjc.mediatree.release

import com.zasenjc.mediatree.data.ReleaseUpdateChecker
import com.zasenjc.mediatree.data.ReleaseUpdateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReleaseUpdateCheckerTest {
    @Test
    fun latestReleaseEndpointUsesMediatreeAppRepository() {
        assertEquals(
            "https://api.github.com/repos/ZASENJC/mediatree-app/releases/latest",
            ReleaseUpdateChecker.LATEST_RELEASE_URL,
        )
        assertEquals(
            "https://github.com/ZASENJC/mediatree-app",
            ReleaseUpdateChecker.REPOSITORY_URL,
        )
    }

    @Test
    fun parsesLatestReleaseAndPrefersApkDownloadAsset() {
        val body = """
            {
              "tag_name": "v0.1.01",
              "html_url": "https://github.com/ZASENJC/mediatree-app/releases/tag/0.1.01",
              "assets": [
                {
                  "name": "notes.txt",
                  "browser_download_url": "https://example.com/notes.txt"
                },
                {
                  "name": "mediatree-app-0.1.01.apk",
                  "browser_download_url": "https://example.com/mediatree-app-0.1.01.apk"
                }
              ]
            }
        """.trimIndent()

        val latest = ReleaseUpdateChecker.parseLatestRelease(body)

        assertEquals("0.1.01", latest.version)
        assertEquals("https://example.com/mediatree-app-0.1.01.apk", latest.downloadUrl)
    }

    @Test
    fun fallsBackToReleasePageWhenNoApkAssetExists() {
        val body = """
            {
              "tag_name": "0.1.01",
              "html_url": "https://github.com/ZASENJC/mediatree-app/releases/tag/0.1.01",
              "assets": []
            }
        """.trimIndent()

        val latest = ReleaseUpdateChecker.parseLatestRelease(body)

        assertEquals("0.1.01", latest.version)
        assertEquals("https://github.com/ZASENJC/mediatree-app/releases/tag/0.1.01", latest.downloadUrl)
    }

    @Test
    fun comparesThreeLevelVersionsWithoutVPrefix() {
        assertTrue(ReleaseUpdateChecker.isNewerVersion("0.1.01", "0.1.00"))
        assertTrue(ReleaseUpdateChecker.isNewerVersion("1.0.00", "0.9.99"))
        assertFalse(ReleaseUpdateChecker.isNewerVersion("0.1.00", "0.1.00"))
        assertFalse(ReleaseUpdateChecker.isNewerVersion("v0.0.99", "0.1.00"))
    }

    @Test
    fun mapsReleaseToAvailableOnlyWhenRemoteVersionIsNewer() {
        val available = ReleaseUpdateChecker.updateStateFor(
            currentVersion = "0.1.00",
            latest = ReleaseUpdateChecker.LatestRelease(
                version = "0.1.01",
                downloadUrl = "https://example.com/app.apk",
            ),
        )
        val current = ReleaseUpdateChecker.updateStateFor(
            currentVersion = "0.1.00",
            latest = ReleaseUpdateChecker.LatestRelease(
                version = "0.1.00",
                downloadUrl = "https://example.com/app.apk",
            ),
        )

        assertTrue(available is ReleaseUpdateState.Available)
        assertEquals("0.1.01", (available as ReleaseUpdateState.Available).latestVersion)
        assertEquals("https://example.com/app.apk", available.downloadUrl)
        assertEquals(ReleaseUpdateState.Current("0.1.00"), current)
    }
}

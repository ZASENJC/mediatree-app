package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WebDavBrowserSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun navigationIncludesWebDavBrowserAndPlayerRoutes() {
        val appSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()

        assertTrue(appSource.contains("webdav/{sourceId}"))
        assertTrue(appSource.contains("webdavPlayer/{sourceId}"))
        assertTrue(appSource.contains("WebDavBrowseScreen"))
        assertTrue(appSource.contains("WebDavPlayerScreen"))
    }

    @Test
    fun settingsLinksOnlyWebDavStorageSourcesToBrowser() {
        val settingsSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertTrue(settingsSource.contains("onOpenClientStorageSource"))
        assertTrue(settingsSource.contains("webdav/${'$'}{source.id}"))
        assertTrue(settingsSource.contains("ClientStorageType.WebDAV"))
        assertFalse(settingsSource.contains("smb/${'$'}{source.id}"))
    }

    @Test
    fun webDavBrowseScreenUsesRepositoryAndGeneratesPlaybackRoute() {
        val screenSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/WebDavBrowseScreen.kt")
            .readText()

        assertTrue(screenSource.contains("class WebDavBrowseViewModel"))
        assertTrue(screenSource.contains("container.webDavClient.list"))
        assertTrue(screenSource.contains("isPlayableVideo"))
        assertTrue(screenSource.contains("webdavPlayer/${'$'}sourceId"))
        assertFalse(screenSource.contains("Smb"))
        assertFalse(screenSource.contains("MediaTreeApi"))
    }
}

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
    fun settingsKeepsWebDavStorageSourcesAsConnectionsOnly() {
        val settingsSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertFalse(settingsSource.contains("onOpenClientStorageSource"))
        assertFalse(settingsSource.contains("webdav/${'$'}{source.id}"))
        assertTrue(settingsSource.contains("ClientStorageType.WebDAV"))
        assertTrue(settingsSource.contains("webDavLibraryPath(source.id)"))
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

    @Test
    fun webDavPlayerShowsOriginalPathAndSameFolderVideoSwitcher() {
        val screenSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/WebDavBrowseScreen.kt")
            .readText()
        val sharedPlayerSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/ClientStoragePlayerDetails.kt")
            .readText()

        assertTrue(screenSource.contains("var currentPath by remember(path)"))
        assertTrue(screenSource.contains("storageParentPath(currentPath)"))
        assertTrue(screenSource.contains("container.webDavClient.list(loadedSource, storageParentPath(currentPath))"))
        assertTrue(screenSource.contains("PlaybackSource.webDav(source = loadedSource, path = currentPath)"))
        assertTrue(screenSource.contains("WebDavClient.buildResourceUrl(loadedSource, currentPath)"))
        assertTrue(screenSource.contains("ClientStoragePlayerDetails("))
        assertTrue(screenSource.contains("onSelectVideo = { item ->"))
        assertTrue(screenSource.contains("currentPath = item.path"))
        assertTrue(sharedPlayerSource.contains("原路径"))
        assertTrue(sharedPlayerSource.contains("同文件夹"))
        assertTrue(sharedPlayerSource.contains("SkipPrevious"))
        assertTrue(sharedPlayerSource.contains("SkipNext"))
    }

    @Test
    fun webDavPlayerDetailsSitDirectlyBelowPlayerWithoutTopTitle() {
        val screenSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/WebDavBrowseScreen.kt")
            .readText()

        assertFalse(screenSource.contains("title = { Text(storageFileName(currentPath)"))
        assertTrue(screenSource.contains("title = {},"))
        assertTrue(screenSource.contains("Spacer(Modifier.height(12.dp))"))
        assertTrue(screenSource.contains("ClientStoragePlayerDetails("))
        assertFalse(screenSource.contains(".align(Alignment.BottomCenter)"))
    }
}

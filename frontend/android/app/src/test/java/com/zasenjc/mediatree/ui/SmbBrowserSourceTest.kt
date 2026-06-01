package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SmbBrowserSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun navigationIncludesSmbBrowserAndPlayerRoutes() {
        val appSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()

        assertTrue(appSource.contains("smb/{sourceId}"))
        assertTrue(appSource.contains("smbPlayer/{sourceId}"))
        assertTrue(appSource.contains("SmbBrowseScreen"))
        assertTrue(appSource.contains("SmbPlayerScreen"))
    }

    @Test
    fun settingsSelectsSmbStorageSourcesAsLibraries() {
        val settingsSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertTrue(settingsSource.contains("ClientStorageType.SMB"))
        assertTrue(settingsSource.contains("smbLibraryPath(source.id)"))
        assertFalse(settingsSource.contains("smb/${'$'}{source.id}"))
    }

    @Test
    fun smbBrowseScreenUsesSmbClientAndProxyPlaybackRouteOnly() {
        val screenSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SmbBrowseScreen.kt")
            .readText()

        assertTrue(screenSource.contains("class SmbBrowseViewModel"))
        assertTrue(screenSource.contains("container.smbClient.list"))
        assertTrue(screenSource.contains("isPlayableVideo"))
        assertTrue(screenSource.contains("smbPlayer/${'$'}sourceId"))
        assertTrue(screenSource.contains("container.smbRangeProxy.playbackSource"))
        val proxySource = appRoot.resolve("src/main/java/com/zasenjc/mediatree/data/SmbRangeProxy.kt").readText()
        assertTrue(proxySource.contains("isClientDisconnect"))
        assertTrue(appRoot.resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt").readText().contains("smbLibrarySourceId"))
        assertTrue(appRoot.resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt").readText().contains("挂载源"))
        assertFalse(screenSource.contains("WebDav"))
        assertFalse(screenSource.contains("MediaTreeApi"))
    }

    @Test
    fun smbPlayerShowsOriginalPathAndSameFolderVideoSwitcher() {
        val screenSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SmbBrowseScreen.kt")
            .readText()
        val sharedPlayerSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/ClientStoragePlayerDetails.kt")
            .readText()

        assertTrue(screenSource.contains("var currentPath by remember(path)"))
        assertTrue(screenSource.contains("storageParentPath(currentPath)"))
        assertTrue(screenSource.contains("container.smbClient.list(loadedSource, storageParentPath(currentPath))"))
        assertTrue(screenSource.contains("SmbClient.buildSmbUrl(loadedSource, currentPath)"))
        assertTrue(screenSource.contains("ClientStoragePlayerDetails("))
        assertTrue(screenSource.contains("onSelectVideo = { item ->"))
        assertTrue(screenSource.contains("currentPath = item.path"))
        assertTrue(sharedPlayerSource.contains("原路径"))
        assertTrue(sharedPlayerSource.contains("同文件夹"))
        assertTrue(sharedPlayerSource.contains("SkipPrevious"))
        assertTrue(sharedPlayerSource.contains("SkipNext"))
    }
}

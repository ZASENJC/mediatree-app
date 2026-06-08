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
        assertTrue(appSource.contains("smbImage/{sourceId}"))
        assertTrue(appSource.contains("SmbBrowseScreen"))
        assertTrue(appSource.contains("SmbPlayerScreen"))
        assertTrue(appSource.contains("SmbImageViewerScreen"))
    }

    @Test
    fun imageViewerRoutesHideBottomNavigationChrome() {
        val appSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()
        val bottomChromeBlock = appSource
            .substringAfter("val bottomChromeVisible =")
            .substringBefore("LaunchedEffect(currentRoute)")

        assertTrue(bottomChromeBlock.contains("!currentRoute.startsWith(\"detail\")"))
        assertTrue(bottomChromeBlock.contains("!currentRoute.endsWith(\"Player/{sourceId}?path={path}\")"))
        assertTrue(bottomChromeBlock.contains("!currentRoute.endsWith(\"Image/{sourceId}?path={path}\")"))
    }

    @Test
    fun imageViewerSupportsSameFolderPagingAndZoomGestures() {
        val imageViewerSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/ImageViewerScreen.kt")
            .readText()

        assertTrue(imageViewerSource.contains("container.smbClient.list(loadedSource, storageParentPath(path))"))
        assertTrue(imageViewerSource.contains("container.webDavClient.list(loadedSource, storageParentPath(path))"))
        assertTrue(imageViewerSource.contains(".filter { it.isViewableImage }"))
        assertTrue(imageViewerSource.contains("ensureCurrentImage("))
        assertTrue(imageViewerSource.contains("rememberPagerState("))
        assertTrue(imageViewerSource.contains("HorizontalPager("))
        assertTrue(imageViewerSource.contains("userScrollEnabled = currentScale <= 1.01f"))
        assertTrue(imageViewerSource.contains("awaitEachGesture"))
        assertTrue(imageViewerSource.contains("calculateZoom()"))
        assertTrue(imageViewerSource.contains("calculatePan()"))
        assertTrue(imageViewerSource.contains("graphicsLayer"))
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
        assertTrue(screenSource.contains("isViewableImage"))
        assertTrue(screenSource.contains("smbPlayer/${'$'}sourceId"))
        assertTrue(screenSource.contains("smbImage/${'$'}sourceId"))
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
        assertTrue(screenSource.contains("saveClientPlaybackProgress(sourceId, currentPath, positionSeconds, durationSeconds)"))
        assertTrue(screenSource.contains("currentPath = item.path"))
        assertTrue(sharedPlayerSource.contains("原路径"))
        assertTrue(sharedPlayerSource.contains("同文件夹"))
        assertTrue(sharedPlayerSource.contains("SkipPrevious"))
        assertTrue(sharedPlayerSource.contains("SkipNext"))
    }

    @Test
    fun smbPlayerDetailsSitDirectlyBelowPlayerWithoutTopTitle() {
        val screenSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SmbBrowseScreen.kt")
            .readText()

        assertFalse(screenSource.contains("title = { Text(storageFileName(currentPath)"))
        assertTrue(screenSource.contains("title = {},"))
        assertTrue(screenSource.contains("Spacer(Modifier.height(12.dp))"))
        assertTrue(screenSource.contains("ClientStoragePlayerDetails("))
        assertFalse(screenSource.contains(".align(Alignment.BottomCenter)"))
    }

    @Test
    fun smbPlayerRestoresAndPersistsClientPlaybackProgress() {
        val screenSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SmbBrowseScreen.kt")
            .readText()

        assertTrue(screenSource.contains("container.clientPlaybackProgressRepository.resumePosition(sourceId, currentPath)"))
        assertTrue(screenSource.contains("val playingPath = currentPath"))
        assertTrue(screenSource.contains("onProgressUpdate = { pos, dur ->"))
        assertTrue(screenSource.contains("container.clientPlaybackProgressRepository.save("))
        assertTrue(screenSource.contains("path = playingPath"))
        assertTrue(screenSource.contains("onPlaybackComplete = { _, _ ->"))
        assertTrue(screenSource.contains("container.clientPlaybackProgressRepository.markFinished(sourceId, playingPath)"))
    }
}

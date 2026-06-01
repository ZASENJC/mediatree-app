package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrowseDirectorySemanticsSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    private val browseSource: String
        get() = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/BrowseScreen.kt")
            .readText()

    private val appSource: String
        get() = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()

    @Test
    fun browseFolderTitleUsesOriginalFolderNameOnly() {
        val source = browseSource
        val titleBlock = source
            .substringAfter("private fun FolderNodeDto.browseTitle(): String =")
            .substringBefore("private fun FolderNodeDto.detailRoute")

        assertTrue(titleBlock.trim().startsWith("name"))
        assertFalse(titleBlock.contains("displayTitle"))
        assertFalse(titleBlock.contains("substringAfterLast"))
    }

    @Test
    fun browseScreenDoesNotRenderMountedRootBreadcrumbOrMediaGroupHeaders() {
        val screenBlock = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore("@Composable\nprivate fun DesignFolderRow")

        assertFalse(screenBlock.contains("BreadcrumbLine("))
        assertFalse(screenBlock.contains("媒体库组目录"))
        assertFalse(screenBlock.contains("影片 · 共"))
        assertFalse(screenBlock.contains("搜索影片"))
    }

    @Test
    fun browseScreenRemovesListModeAndDefaultsToCompact() {
        assertFalse(browseSource.contains("ViewList"))
        assertFalse(browseSource.contains("\"list\", \"列表\""))
        assertTrue(appSource.contains("var browseViewMode by rememberSaveable { mutableStateOf(\"compact\") }"))
    }

    @Test
    fun browseViewModeIsHoistedSoReturningKeepsSelection() {
        val browseSignature = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore(") {")
        val appBrowseCall = appSource
            .substringAfter("\"browse\" -> BrowseScreen(")
            .substringBefore(")")

        assertTrue(browseSignature.contains("viewMode: String"))
        assertTrue(browseSignature.contains("onViewModeChange: (String) -> Unit"))
        assertFalse(browseSource.contains("var viewMode by remember { mutableStateOf(\"compact\") }"))
        assertTrue(appBrowseCall.contains("viewMode = browseViewMode"))
        assertTrue(appBrowseCall.contains("onViewModeChange = { browseViewMode = it }"))
    }

    @Test
    fun activeLibraryChangeResetsBrowseFolderToRoot() {
        val resetBlock = appSource
            .substringAfter("LaunchedEffect(session.activeProviderType, session.activeLibrary)")
            .substringBefore("fun navigateTopDestination")

        assertTrue(resetBlock.contains("browseFolder = \"\""))
        assertFalse(resetBlock.contains("browseViewMode = \"compact\""))
    }

    @Test
    fun compactRowsKeepRoundedShape() {
        val compactBrowserRow = browseSource
            .substringAfter("private fun CompactBrowserRow")
            .substringBefore("@Composable\nprivate fun BreadcrumbLine")

        assertTrue(compactBrowserRow.contains("shape = RoundedCornerShape(12.dp)"))
    }

    @Test
    fun smbMountedBrowseDoesNotPrefetchChildFolderCounts() {
        val loadSmbBlock = browseSource
            .substringAfter("private suspend fun loadSmb")
            .substringBefore("private suspend fun loadWebDav")

        assertTrue(loadSmbBlock.contains("container.smbClient.list(source, folder)"))
        assertFalse(loadSmbBlock.contains("smbDirectoryChildCount"))
        assertFalse(loadSmbBlock.contains("container.smbClient.list(source, entry.path)"))
        assertFalse(loadSmbBlock.contains("movieCount = 1"))
        assertFalse(browseSource.contains("private suspend fun smbDirectoryChildCount"))
    }

    @Test
    fun browseSupportsWebDavMountedLibrarySource() {
        val loadWebDavBlock = browseSource
            .substringAfter("private suspend fun loadWebDav")
            .substringBefore("fun loadMore")

        assertTrue(browseSource.contains("webDavLibrarySourceId"))
        assertTrue(browseSource.contains("private suspend fun loadWebDav"))
        assertTrue(browseSource.contains("container.webDavClient.list(source, folder)"))
        assertFalse(loadWebDavBlock.contains("webDavDirectoryChildCount"))
        assertFalse(loadWebDavBlock.contains("container.webDavClient.list(source, entry.path)"))
        assertFalse(browseSource.contains("private suspend fun webDavDirectoryChildCount"))
        assertTrue(browseSource.contains("webDavLibraryPath(sourceId)"))
        assertTrue(browseSource.contains("webdavPlayer/${'$'}sourceId?path="))
    }

    @Test
    fun mountedFolderMetaAvoidsUnloadedChildCounts() {
        val folderMetaBlock = browseSource
            .substringAfter("private fun FolderNodeDto.folderMeta(): String =")
            .substringBefore("private fun MovieDto.browseTitle")

        assertTrue(folderMetaBlock.contains("mediaRoot?.mountedLibrarySourceId() != null"))
        assertTrue(folderMetaBlock.contains("\"文件夹\""))
    }

    @Test
    fun mountedVideosUseRealFrameThumbnailsInPosterAndIconModes() {
        val screenBlock = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore("@Composable\nprivate fun DesignFolderRow")
        val iconMovieRowBlock = browseSource
            .substringAfter("private fun IconMovieRow")
            .substringBefore("@Composable\nprivate fun IconTile")
        val mountedIconTileBlock = browseSource
            .substringAfter("private fun MountedVideoIconTile")
            .substringBefore("@Composable\nprivate fun IconTile")
        val compactMovieRowBlock = browseSource
            .substringAfter("private fun CompactMovieRow")
            .substringBefore("@Composable\nprivate fun CompactBrowserRow")

        assertTrue(screenBlock.contains("movie.isMountedLibraryItem()"))
        assertTrue(screenBlock.contains("MountedVideoPosterCard("))
        assertTrue(screenBlock.contains("CompactMovieRow("))
        assertTrue(iconMovieRowBlock.contains("MountedVideoThumbnail("))
        assertTrue(mountedIconTileBlock.contains("showPlayIcon = false"))
        assertTrue(mountedIconTileBlock.contains("maxLines = 1"))
        assertFalse(mountedIconTileBlock.contains("movie.iconMovieMeta()"))
        assertFalse(mountedIconTileBlock.contains("ElevatedCard("))
        assertTrue(compactMovieRowBlock.contains("MountedVideoThumbnail("))
        assertTrue(compactMovieRowBlock.contains("framedIcon = !movie.isMountedLibraryItem()"))
        assertTrue(browseSource.contains("private fun MountedVideoThumbnail"))
        assertTrue(browseSource.contains("MediaMetadataRetriever"))
        assertTrue(browseSource.contains("setDataSource(source.uri, source.headers)"))
        assertTrue(browseSource.contains("getScaledFrameAtTime"))
        assertTrue(browseSource.contains("limitedParallelism(4)"))
        assertTrue(browseSource.contains("MountedVideoFrameWidth = 240"))
        assertTrue(browseSource.contains("MountedVideoFrameHeight = 360"))
        assertTrue(browseSource.contains("mountedVideoFrameCache"))
        assertTrue(browseSource.contains("MountedVideoFrameMemoryCache"))
        assertTrue(browseSource.contains("MountedVideoFrameCacheTtlMillis"))
        assertTrue(browseSource.contains("MountedVideoFrameCacheMaxBytes"))
        assertTrue(browseSource.contains("mountedVideoFrameRequests"))
        assertTrue(browseSource.contains("Deferred<Bitmap?>"))
        assertTrue(browseSource.contains("getCached"))
        assertTrue(browseSource.contains("putCached"))
        assertTrue(browseSource.contains("removeOldestCacheEntry"))
        assertFalse(browseSource.contains("DiskCache"))
        assertTrue(browseSource.contains("sourceInfo.onClose?.invoke()"))
        assertTrue(browseSource.contains("container.smbRangeProxy.playbackSource"))
        assertTrue(browseSource.contains("PlaybackSource.webDav"))
        assertFalse(browseSource.contains("modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)"))
    }

    @Test
    fun browserRowsExposeThemeContentColorForDarkMode() {
        val source = browseSource
        val designFolderRow = source.substringAfter("private fun DesignFolderRow").substringBefore("@Composable\nprivate fun PosterFolderRow")
        val folderPosterCard = source.substringAfter("private fun FolderPosterCard").substringBefore("@Composable\nprivate fun IconFolderRow")
        val iconTile = source.substringAfter("private fun IconTile").substringBefore("@Composable\nprivate fun FolderListRow")
        val browserListRow = source.substringAfter("private fun BrowserListRow").substringBefore("@Composable\nprivate fun CompactFolderRow")
        val compactBrowserRow = source.substringAfter("private fun CompactBrowserRow").substringBefore("@Composable\nprivate fun BreadcrumbLine")

        assertTrue(designFolderRow.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(folderPosterCard.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(iconTile.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(browserListRow.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(compactBrowserRow.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(folderPosterCard.contains("color = MaterialTheme.colorScheme.onSurface"))
        assertTrue(iconTile.contains("color = MaterialTheme.colorScheme.onSurface"))
        assertTrue(browserListRow.contains("color = MaterialTheme.colorScheme.onSurface"))
        assertTrue(compactBrowserRow.contains("color = MaterialTheme.colorScheme.onSurface"))
    }
}

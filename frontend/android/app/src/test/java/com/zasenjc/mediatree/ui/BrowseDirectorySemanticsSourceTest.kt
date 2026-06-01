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
        assertTrue(browseSource.contains("mutableStateOf(\"compact\")"))
    }

    @Test
    fun compactRowsKeepRoundedShape() {
        val compactBrowserRow = browseSource
            .substringAfter("private fun CompactBrowserRow")
            .substringBefore("@Composable\nprivate fun BreadcrumbLine")

        assertTrue(compactBrowserRow.contains("shape = RoundedCornerShape(12.dp)"))
    }

    @Test
    fun smbFolderCountsVisibleChildrenInsteadOfHardcodedOne() {
        val loadSmbBlock = browseSource
            .substringAfter("private suspend fun loadSmb")
            .substringBefore("fun loadMore")

        assertTrue(loadSmbBlock.contains("smbDirectoryChildCount(source, entry.path)"))
        assertFalse(loadSmbBlock.contains("movieCount = 1"))
        assertTrue(browseSource.contains("private suspend fun smbDirectoryChildCount"))
        assertTrue(browseSource.contains("count { it.isDirectory || it.isPlayableVideo }"))
    }

    @Test
    fun browseSupportsWebDavMountedLibrarySource() {
        assertTrue(browseSource.contains("webDavLibrarySourceId"))
        assertTrue(browseSource.contains("private suspend fun loadWebDav"))
        assertTrue(browseSource.contains("container.webDavClient.list(source, folder)"))
        assertTrue(browseSource.contains("webDavDirectoryChildCount(source, entry.path)"))
        assertTrue(browseSource.contains("webDavLibraryPath(sourceId)"))
        assertTrue(browseSource.contains("webdavPlayer/${'$'}sourceId?path="))
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

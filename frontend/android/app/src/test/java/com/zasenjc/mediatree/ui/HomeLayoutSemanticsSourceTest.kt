package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeLayoutSemanticsSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun mediaFeedUsesGroupedMediaPostersAndDirectoryFirstReusesBrowseScreen() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val mediaFeedBlock = source
            .substringAfter("} else if (showMediaFeed) {")
            .substringBefore("AnimatedVisibility(")
        val directoryFirstBlock = source
            .substringAfter("if (homeLayout == HomeLayoutPreference.DirectoryFirst) {")
            .substringBefore("val vm: HomeViewModel")

        assertTrue(mediaFeedBlock.contains("state.libraryItems"))
        assertTrue(mediaFeedBlock.contains("HomeMediaPosterCard"))
        assertTrue(mediaFeedBlock.contains("vm.openLibraryItem"))
        assertFalse(mediaFeedBlock.contains("state.feedMovies.isEmpty()"))

        assertTrue(directoryFirstBlock.contains("BrowseScreen("))
        assertTrue(directoryFirstBlock.contains("initialFolder = \"\""))
        assertTrue(directoryFirstBlock.contains("viewMode = browseViewMode"))
        assertTrue(directoryFirstBlock.contains("onViewModeChange = onBrowseViewModeChange"))
        assertTrue(source.contains("webDavLibrarySourceId"))
        assertFalse(source.contains("HomeDirectoryRow("))
    }

    @Test
    fun settingsDoesNotExplainHomeLayoutModesInline() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertFalse(source.contains("媒体流按剧集/电影显示单海报"))
        assertFalse(source.contains("目录优先直接显示媒体库或 SMB 的源文件夹结构"))
    }

    @Test
    fun homeMountedLibrariesSupportSortRefreshAndSearch() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val actionsBlock = source
            .substringAfter("actions = {")
            .substringBefore("HomeSearchOverlay(")
        val searchBlock = source
            .substringAfter("private fun HomeSearchOverlay")
            .substringBefore("@Composable\nprivate fun HomeSearchResultRow")

        assertTrue(source.contains("private fun Session.canLoadHomeContent()"))
        assertTrue(actionsBlock.contains("session.canLoadHomeContent()"))
        assertTrue(actionsBlock.contains("vm.load(session.activeProviderType, session.activeLibrary, key)"))
        assertTrue(actionsBlock.contains("vm.load(session.activeProviderType, session.activeLibrary)"))
        assertFalse(actionsBlock.contains("if (shouldLoadRemoteContent(session))"))

        assertTrue(searchBlock.contains("session.activeLibrary.smbLibrarySourceId()"))
        assertTrue(searchBlock.contains("session.activeLibrary.webDavLibrarySourceId()"))
        assertTrue(searchBlock.contains("searchMountedLibrary"))
        assertTrue(source.contains("container.smbClient.list(source)"))
        assertTrue(source.contains("container.webDavClient.list(source)"))
        assertTrue(searchBlock.contains("mountedSearchResults"))
        assertTrue(searchBlock.contains("movie.openRoute()"))
    }
}

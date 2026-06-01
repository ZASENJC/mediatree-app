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
}

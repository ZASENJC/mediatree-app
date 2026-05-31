package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeLayoutSemanticsSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun mediaFeedUsesGroupedMediaPostersAndDirectoryFirstUsesFolderRows() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val mediaFeedBlock = source
            .substringAfter("} else if (showMediaFeed) {")
            .substringBefore("columns = GridCells.Fixed(1)")
        val directoryBlock = source
            .substringAfter("columns = GridCells.Fixed(1)")
            .substringBefore("AnimatedVisibility(")

        assertTrue(mediaFeedBlock.contains("state.libraryItems"))
        assertTrue(mediaFeedBlock.contains("HomeMediaPosterCard"))
        assertTrue(mediaFeedBlock.contains("vm.openLibraryItem"))
        assertFalse(mediaFeedBlock.contains("state.feedMovies.isEmpty()"))

        assertTrue(directoryBlock.contains("HomeDirectoryRow"))
        assertTrue(directoryBlock.contains("vm.openDirectoryItem"))
        assertTrue(source.contains("columns = GridCells.Fixed(1)"))
        assertFalse(directoryBlock.contains("HomeMediaPosterCard"))
    }

    @Test
    fun settingsExplainsHomeLayoutModes() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertTrue(source.contains("媒体流按剧集/电影显示单海报"))
        assertTrue(source.contains("目录优先直接显示媒体库或 SMB 的源文件夹结构"))
    }
}

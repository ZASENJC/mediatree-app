package com.zasenjc.mediatree.ui

import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.isWatched
import com.zasenjc.mediatree.data.withTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WatchedFlagSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun watchedTagMatchingIsCaseInsensitiveAndDoesNotDuplicateTags() {
        val movie = MovieDto(id = 1, tags = listOf("Watched"))

        assertTrue(movie.isWatched())
        assertEquals(listOf("Watched"), movie.withTag("watched").tags)
        assertFalse(MovieDto(id = 2, tags = listOf("favorite")).isWatched())
    }

    @Test
    fun posterCardsRenderWatchedRibbonInsteadOfOnlyHidingProgress() {
        val posterCard = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/MoviePosterCard.kt")
            .readText()
        val home = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()

        assertTrue(posterCard.contains("watched: Boolean"))
        assertTrue(posterCard.contains("if (watched)"))
        assertTrue(posterCard.contains("WatchRibbon("))
        assertTrue(posterCard.contains("contentDescription = \"已观看\""))
        assertTrue(posterCard.contains("!movie.isWatched()"))

        val homePosterBlock = home
            .substringAfter("private fun HomeMoviePosterCard(")
            .substringBefore("@Composable\nprivate fun HomeMediaPosterCard")
        assertTrue(homePosterBlock.contains("Box {"))
        assertTrue(homePosterBlock.contains("if (movie.isWatched())"))
        assertTrue(homePosterBlock.contains("WatchFlag(Modifier.align(Alignment.TopEnd).padding(7.dp))"))
    }

    @Test
    fun detailWatchedActionUpdatesCurrentMovieAndSeriesItems() {
        val detail = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()
        val updateBlock = detail
            .substringAfter("private fun updateMovieTags(")
            .substringBefore("private fun playbackMemoryMovie")

        assertTrue(detail.contains("updateMovieTags(movie.id) { it.withTag(\"watched\") }"))
        assertTrue(detail.contains(".onSuccess { updateMovieTags(movieId) { it.withTag(\"watched\") } }"))
        assertTrue(updateBlock.contains("movie = state.movie?.let"))
        assertTrue(updateBlock.contains("seriesItems = state.seriesItems.map"))
        assertTrue(updateBlock.contains("if (item.id == movieId) transform(item) else item"))
        assertFalse(detail.contains("tags = it.movie!!.tags + \"watched\""))
    }

    @Test
    fun browseSourceFileNameFolderPostersRenderWatchedFlag() {
        val browse = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/BrowseScreen.kt")
            .readText()
        val posterFolderRowBlock = browse
            .substringAfter("private fun PosterFolderRow(")
            .substringBefore("@Composable\nprivate fun FolderPosterCard")
        val folderPosterBlock = browse
            .substringAfter("private fun FolderPosterCard(")
            .substringBefore("@Composable\nprivate fun IconFolderRow")

        assertTrue(posterFolderRowBlock.contains("FolderPosterCard("))
        assertTrue(posterFolderRowBlock.contains("titleOverride = folder.sourceFileNameTitle().takeIf { sourceFileNameMode }"))
        assertTrue(folderPosterBlock.contains("Box("))
        assertTrue(folderPosterBlock.contains("if (folder.folderWatched == true)"))
        assertTrue(folderPosterBlock.contains("WatchFlag(Modifier.align(Alignment.TopEnd).padding(7.dp))"))
        assertTrue(browse.contains("contentDescription = \"已观看\""))
    }
}

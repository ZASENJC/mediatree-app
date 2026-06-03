package com.zasenjc.mediatree.ui

import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.ui.screens.latestHomePlaybackCandidate
import org.junit.Assert.assertEquals
import org.junit.Test

class HomePlaybackSelectionTest {
    @Test
    fun picksLatestUnfinishedEpisodeBySeasonAndEpisodeNumber() {
        val episodes = listOf(
            episode(id = 1, season = 1, episode = 10, progress = 100.0),
            episode(id = 2, season = 2, episode = 1, tags = listOf("watched")),
            episode(id = 3, season = 2, episode = 2, progress = 96.0),
            episode(id = 4, season = 2, episode = 3, progress = 40.0),
            episode(id = 5, season = 1, episode = 12, progress = null),
        )

        val selected = episodes.latestHomePlaybackCandidate()

        assertEquals(4, selected?.id)
    }

    @Test
    fun fallsBackToLatestEpisodeWhenEveryEpisodeIsFinished() {
        val episodes = listOf(
            episode(id = 1, season = 1, episode = 12, tags = listOf("watched")),
            episode(id = 2, season = 2, episode = 1, progress = 99.0),
            episode(id = 3, season = 2, episode = 2, tags = listOf("watched")),
        )

        val selected = episodes.latestHomePlaybackCandidate()

        assertEquals(3, selected?.id)
    }

    @Test
    fun infersSeasonFromFolderLevelsWhenTmdbSeasonIsMissing() {
        val episodes = listOf(
            episode(id = 1, season = null, episode = 12, folderLevels = "Example/Season 1"),
            episode(id = 2, season = null, episode = 1, folderLevels = "Example/Season 2"),
        )

        val selected = episodes.latestHomePlaybackCandidate()

        assertEquals(2, selected?.id)
    }

    private fun episode(
        id: Int,
        season: Int?,
        episode: Int,
        tags: List<String> = emptyList(),
        progress: Double? = null,
        folderLevels: String? = null,
    ): MovieDto = MovieDto(
        id = id,
        code = "S${season ?: 0}E$episode",
        tmdbSeason = season,
        tmdbEpisode = episode,
        episodeNumber = episode,
        folderLevels = folderLevels,
        tags = tags,
        progressPercent = progress,
    )
}

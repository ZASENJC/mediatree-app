package com.zasenjc.mediatree.provider

import com.zasenjc.mediatree.data.MediaBrowserItemDto
import com.zasenjc.mediatree.data.MediaBrowserMediaSourceDto
import com.zasenjc.mediatree.data.MediaBrowserMediaStreamDto
import com.zasenjc.mediatree.data.MediaBrowserNameDto
import com.zasenjc.mediatree.data.MediaBrowserPersonDto
import com.zasenjc.mediatree.data.MediaBrowserPlaybackInfoDto
import com.zasenjc.mediatree.data.MediaBrowserUserDataDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.mediaBrowserRouteId
import com.zasenjc.mediatree.data.toMediaInfoDto
import com.zasenjc.mediatree.data.toMediaTreeFolderNodeDto
import com.zasenjc.mediatree.data.toMediaTreeMovieDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaBrowserCompatTest {
    @Test
    fun mapsJellyfinEpisodeIntoMediaTreeFrontendFields() {
        val item = MediaBrowserItemDto(
            id = "episode-abcdef12",
            name = "Pilot",
            originalTitle = "Original Pilot",
            overview = "Episode overview",
            type = "Episode",
            parentId = "season-id",
            seriesId = "series-id",
            seriesName = "Example Show",
            seasonId = "season-id",
            seasonName = "Season 1",
            studios = listOf(MediaBrowserNameDto(name = "Studio A")),
            genres = listOf("Drama", "Mystery"),
            tags = listOf("Slow Burn"),
            premiereDate = "2024-01-02T00:00:00.000Z",
            dateCreated = "2024-02-03T04:05:06.000Z",
            officialRating = "TV-14",
            runTimeTicks = 27_000_000_000L,
            parentIndexNumber = 1,
            indexNumber = 2,
            providerIds = mapOf("Tmdb" to "12345"),
            imageTags = mapOf("Primary" to "primary-tag"),
            backdropImageTags = listOf("backdrop-tag"),
            mediaSources = listOf(
                MediaBrowserMediaSourceDto(
                    id = "source-1",
                    path = "/media/show/season 1/pilot.mkv",
                    size = 1_234_567_890L,
                    container = "mkv",
                    runTimeTicks = 27_000_000_000L,
                    mediaStreams = listOf(
                        MediaBrowserMediaStreamDto(index = 0, type = "Video", codec = "hevc"),
                        MediaBrowserMediaStreamDto(index = 1, type = "Audio", codec = "aac", channels = 6),
                        MediaBrowserMediaStreamDto(index = 2, type = "Subtitle", codec = "srt", language = "eng"),
                    ),
                ),
            ),
            userData = MediaBrowserUserDataDto(
                isFavorite = true,
                played = false,
                playbackPositionTicks = 1_200_000_000L,
                playedPercentage = 44.4,
            ),
            people = listOf(
                MediaBrowserPersonDto(id = "actor-1", name = "Actor One", type = "Actor", role = "Lead", primaryImageTag = "actor-tag"),
                MediaBrowserPersonDto(id = "director-1", name = "Director One", type = "Director", role = "Director"),
            ),
        )

        val movie = item.toMediaTreeMovieDto("https://jellyfin.example.com", ProviderType.Jellyfin)

        assertEquals(mediaBrowserRouteId("episode-abcdef12"), movie.id)
        assertEquals("episode-abcdef12", movie.path)
        assertEquals("episode-abcdef12", movie.providerItemId)
        assertEquals("series-id", movie.providerSeriesId)
        assertEquals("season-id", movie.providerSeasonId)
        assertEquals("Example Show", movie.title)
        assertEquals("Pilot", movie.episodeTitle)
        assertEquals("S01E02", movie.episodeLabel)
        assertEquals(1, movie.tmdbSeason)
        assertEquals(2, movie.tmdbEpisode)
        assertEquals(12345, movie.tmdbId)
        assertEquals("tv", movie.tmdbType)
        assertEquals("Drama, Mystery", movie.genre)
        assertEquals("Studio A", movie.studio)
        assertEquals("/media/show/season 1", movie.folderLevels)
        assertEquals(1_234_567_890L, movie.fileSize)
        assertEquals("https://jellyfin.example.com/Items/episode-abcdef12/Images/Primary?tag=primary-tag", movie.episodeStill)
        assertTrue(movie.javdbThumbnails.contains("https://jellyfin.example.com/Items/episode-abcdef12/Images/Backdrop?tag=backdrop-tag"))
        assertTrue(movie.tags.contains("favorite"))
        assertEquals(120.0, movie.playbackPosition ?: 0.0, 0.01)
        assertEquals(44.4, movie.progressPercent ?: 0.0, 0.01)
        assertEquals("Actor One", movie.cast.single().name)
        assertEquals("Director One", movie.director)
        assertEquals("Jellyfin", movie.scraperSource)
    }

    @Test
    fun mapsPlaybackInfoIntoMediaInfoUsedByDetailChips() {
        val playbackInfo = MediaBrowserPlaybackInfoDto(
            mediaSources = listOf(
                MediaBrowserMediaSourceDto(
                    id = "source-1",
                    container = "mp4",
                    runTimeTicks = 6_000_000_000L,
                    mediaStreams = listOf(
                        MediaBrowserMediaStreamDto(index = 0, type = "Video", codec = "h264"),
                        MediaBrowserMediaStreamDto(index = 1, type = "Audio", codec = "aac", channels = 2),
                    ),
                ),
            ),
        )

        val mediaInfo = playbackInfo.toMediaInfoDto()

        assertEquals(600.0, mediaInfo.duration, 0.01)
        assertEquals("h264", mediaInfo.videoCodec)
        assertEquals("aac", mediaInfo.audioCodec)
        assertEquals(2, mediaInfo.audioChannels)
        assertEquals("mp4", mediaInfo.container)
    }

    @Test
    fun mapsBrowsableItemsWithoutChildCountsAsVisibleHomeFolders() {
        val items = listOf(
            MediaBrowserItemDto(id = "series-id", name = "Series", type = "Series", isFolder = true),
            MediaBrowserItemDto(id = "season-id", name = "Season 1", type = "Season", isFolder = true),
            MediaBrowserItemDto(id = "boxset-id", name = "Collection", type = "BoxSet", isFolder = true),
            MediaBrowserItemDto(id = "folder-id", name = "Folder", type = "Folder", isFolder = true),
        )

        val folders = items.map {
            it.toMediaTreeFolderNodeDto(
                serverUrl = "https://jellyfin.example.com",
                parentMediaRoot = "root-id",
            )
        }

        assertEquals(listOf(false, false, false, false), folders.map { it.isLeaf })
        assertEquals(listOf(1, 1, 1, 1), folders.map { it.movieCount })
        assertEquals(listOf("root-id", "root-id", "root-id", "root-id"), folders.map { it.mediaRoot })
    }

    @Test
    fun mapsStandaloneVideoItemsAsPlayableLeavesForDetailNavigation() {
        val folder = MediaBrowserItemDto(
            id = "video-id",
            name = "Standalone Video",
            type = "Video",
            isFolder = false,
        ).toMediaTreeFolderNodeDto(
            serverUrl = "https://jellyfin.example.com",
            parentMediaRoot = "root-id",
        )

        assertTrue(folder.isLeaf)
        assertEquals(1, folder.movieCount)
        assertEquals("video-id", folder.path)
        assertFalse(folder.cover.isNullOrBlank())
    }
}

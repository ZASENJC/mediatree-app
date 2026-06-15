package com.zasenjc.mediatree.data

import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

private const val MediaBrowserTicksPerSecond = 10_000_000L

@Serializable
data class MediaBrowserItemsResponse(
    @SerialName("Items") val items: List<MediaBrowserItemDto> = emptyList(),
    @SerialName("TotalRecordCount") val totalRecordCount: Int = items.size,
)

@Serializable
data class MediaBrowserItemDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("OriginalTitle") val originalTitle: String? = null,
    @SerialName("Overview") val overview: String? = null,
    @SerialName("Type") val type: String = "",
    @SerialName("ParentId") val parentId: String? = null,
    @SerialName("SeriesId") val seriesId: String? = null,
    @SerialName("SeriesName") val seriesName: String? = null,
    @SerialName("SeasonId") val seasonId: String? = null,
    @SerialName("SeasonName") val seasonName: String? = null,
    @SerialName("Studios") val studios: List<MediaBrowserNameDto> = emptyList(),
    @SerialName("Genres") val genres: List<String> = emptyList(),
    @SerialName("Tags") val tags: List<String> = emptyList(),
    @SerialName("PremiereDate") val premiereDate: String? = null,
    @SerialName("DateCreated") val dateCreated: String? = null,
    @SerialName("ProductionYear") val productionYear: Int? = null,
    @SerialName("OfficialRating") val officialRating: String? = null,
    @SerialName("CommunityRating") val communityRating: Double? = null,
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("ChildCount") val childCount: Int? = null,
    @SerialName("RecursiveItemCount") val recursiveItemCount: Int? = null,
    @SerialName("CollectionType") val collectionType: String? = null,
    @SerialName("IndexNumber") val indexNumber: Int? = null,
    @SerialName("ParentIndexNumber") val parentIndexNumber: Int? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Path") val filePath: String? = null,
    @SerialName("IsFolder") val isFolder: Boolean = false,
    @SerialName("CanDelete") val canDelete: Boolean = false,
    @SerialName("UserData") val userData: MediaBrowserUserDataDto? = null,
    @SerialName("People") val people: List<MediaBrowserPersonDto> = emptyList(),
    @SerialName("MediaSources") val mediaSources: List<MediaBrowserMediaSourceDto> = emptyList(),
    @SerialName("ProviderIds") val providerIds: Map<String, String> = emptyMap(),
    @SerialName("ImageTags") val imageTags: Map<String, String> = emptyMap(),
    @SerialName("BackdropImageTags") val backdropImageTags: List<String> = emptyList(),
) {
    val isPlayable: Boolean
        get() = isPlayableMediaBrowserItem()
}

@Serializable
data class MediaBrowserNameDto(
    @SerialName("Name") val name: String = "",
)

@Serializable
data class MediaBrowserUserDataDto(
    @SerialName("IsFavorite") val isFavorite: Boolean = false,
    @SerialName("Played") val played: Boolean = false,
    @SerialName("PlaybackPositionTicks") val playbackPositionTicks: Long = 0,
    @SerialName("PlayedPercentage") val playedPercentage: Double = 0.0,
    @SerialName("LastPlayedDate") val lastPlayedDate: String? = null,
    @SerialName("PlayCount") val playCount: Int = 0,
)

@Serializable
data class MediaBrowserPersonDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Type") val type: String = "",
    @SerialName("Role") val role: String? = null,
    @SerialName("PrimaryImageTag") val primaryImageTag: String? = null,
)

@Serializable
data class MediaBrowserPlaybackInfoDto(
    @SerialName("MediaSources") val mediaSources: List<MediaBrowserMediaSourceDto> = emptyList(),
)

@Serializable
data class MediaBrowserMediaSourceDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
    @SerialName("Path") val path: String? = null,
    @SerialName("Size") val size: Long? = null,
    @SerialName("Container") val container: String = "",
    @SerialName("RunTimeTicks") val runTimeTicks: Long? = null,
    @SerialName("MediaStreams") val mediaStreams: List<MediaBrowserMediaStreamDto> = emptyList(),
)

@Serializable
data class MediaBrowserMediaStreamDto(
    @SerialName("Index") val index: Int,
    @SerialName("Type") val type: String = "",
    @SerialName("Codec") val codec: String = "",
    @SerialName("Language") val language: String? = null,
    @SerialName("Title") val title: String? = null,
    @SerialName("DisplayTitle") val displayTitle: String = "",
    @SerialName("IsExternal") val isExternal: Boolean = false,
    @SerialName("Channels") val channels: Int? = null,
)

fun mediaBrowserRouteId(itemId: String): Int =
    itemId.takeLast(8).toUIntOrNull(16)?.toInt() ?: itemId.hashCode()

fun MediaBrowserItemDto.toMediaTreeFolderNodeDto(
    serverUrl: String,
    parentMediaRoot: String,
    rememberId: (String) -> Int = { mediaBrowserRouteId(it) },
): FolderNodeDto {
    rememberId(id)
    val base = UrlUtils.normalizeServerUrl(serverUrl)
    val title = name.ifBlank { originalTitle.orEmpty() }.ifBlank { id }
    return FolderNodeDto(
        name = title,
        path = id,
        isLeaf = !isBrowsableMediaBrowserItem(),
        movieCount = mediaBrowserItemCount(),
        videoCount = childCount ?: recursiveItemCount ?: 0,
        cover = itemImageUrl(base, id, "Primary", imageTags["Primary"]),
        backdrop = backdropImageTags.firstOrNull()?.let { tag -> itemImageUrl(base, id, "Backdrop", tag) },
        displayTitle = title,
        mediaRoot = parentMediaRoot,
        createdMax = dateCreated,
        releaseDateMax = premiereDate ?: productionYear?.toString(),
        progressPercent = userData?.playedPercentage,
        folderWatched = userData?.played,
    )
}

fun MediaBrowserItemDto.toMediaTreeMovieDto(
    serverUrl: String,
    providerType: ProviderType,
): MovieDto {
    val base = UrlUtils.normalizeServerUrl(serverUrl)
    val routeId = mediaBrowserRouteId(id)
    val mediaSource = mediaSources.firstOrNull()
    val filePath = mediaSource?.path ?: filePath
    val seasonNumber = parentIndexNumber
    val episodeNumber = indexNumber
    val episodeLabel = episodeLabel(type, seasonNumber, episodeNumber)
    val isEpisode = type == "Episode"
    val itemTitle = name.ifBlank { originalTitle.orEmpty() }
    val seriesTitle = seriesName?.takeIf { it.isNotBlank() }
    val display = itemTitle.ifBlank { seriesTitle.orEmpty() }.ifBlank { id }
    val code = if (isEpisode) episodeLabel.ifBlank { display } else display
    val primaryImage = itemImageUrl(base, id, "Primary", imageTags["Primary"])
    val backdropImages = backdropImageTags.map { tag -> itemImageUrl(base, id, "Backdrop", tag) }
    val providerTmdbId = providerIds.firstMatchingInt("Tmdb", "TMDb", "TheMovieDb")
    val cast = people.filter { it.type.equals("Actor", ignoreCase = true) }.map { person ->
        PersonCreditDto(
            name = person.name,
            character = person.role,
            role = person.role,
            profilePath = person.primaryImageTag?.let {
                itemImageUrl(base, person.id, "Primary", it)
            },
            personId = person.id,
            source = providerType.name,
        )
    }
    val crew = people.filterNot { it.type.equals("Actor", ignoreCase = true) }.map { person ->
        CrewCreditDto(
            name = person.name,
            job = person.role?.takeIf { it.isNotBlank() } ?: person.type,
            profilePath = person.primaryImageTag?.let {
                itemImageUrl(base, person.id, "Primary", it)
            },
            personId = person.id,
            source = providerType.name,
        )
    }
    val director = crew
        .filter { it.job.contains("director", ignoreCase = true) }
        .joinToString(" / ") { it.name }
        .ifBlank { null }

    return MovieDto(
        id = routeId,
        path = id,
        providerItemId = id,
        providerSeriesId = seriesId,
        providerSeasonId = seasonId,
        code = code,
        title = if (isEpisode) seriesTitle ?: display else display,
        originalTitle = originalTitle,
        overview = overview,
        director = director,
        series = seriesTitle,
        studio = studios.firstOrNull()?.name,
        genre = genres.joinToString(", "),
        releaseDate = premiereDate ?: productionYear?.toString(),
        duration = (runTimeTicks ?: mediaSource?.runTimeTicks)?.ticksToMinutes(),
        keywords = tags.joinToString(", ").ifBlank { null },
        studios = studios.joinToString(", ") { it.name }.ifBlank { null },
        status = type.ifBlank { null },
        contentRating = officialRating,
        javdbScore = communityRating,
        javdbThumbnails = backdropImages,
        folderLevels = mediaBrowserFolderLevels(filePath, seriesTitle, seasonName),
        tags = buildList {
            tags.forEach { add(it) }
            if (userData?.isFavorite == true) add("favorite")
            if (userData?.played == true) add("watched")
        }.distinct(),
        mediaRoot = parentId,
        tmdbId = providerTmdbId,
        tmdbType = when {
            isEpisode || type == "Series" || seriesId != null -> "tv"
            providerTmdbId != null -> "movie"
            else -> null
        },
        tmdbSeason = seasonNumber,
        tmdbEpisode = episodeNumber,
        episodeTitle = if (isEpisode) display else null,
        episodeNumber = episodeNumber,
        episodeLabel = episodeLabel.ifBlank { null },
        episodeOverview = if (isEpisode) overview else null,
        episodeStill = primaryImage,
        displayTitle = display,
        createdAt = dateCreated,
        updatedAt = userData?.lastPlayedDate ?: dateCreated,
        fileSize = size ?: mediaSource?.size,
        size = size ?: mediaSource?.size,
        playbackPosition = userData?.playbackPositionTicks?.ticksToSeconds(),
        progressPercent = userData?.playedPercentage,
        cast = cast,
        crew = crew,
        scraperSource = providerType.name,
    )
}

fun MediaBrowserPlaybackInfoDto.toMediaInfoDto(): MediaInfoDto {
    val mediaSource = mediaSources.firstOrNull() ?: return MediaInfoDto()
    val video = mediaSource.mediaStreams.firstOrNull { it.type.equals("Video", ignoreCase = true) }
    val audio = mediaSource.mediaStreams.firstOrNull { it.type.equals("Audio", ignoreCase = true) }
    return MediaInfoDto(
        duration = mediaSource.runTimeTicks?.ticksToSeconds() ?: 0.0,
        videoCodec = video?.codec.orEmpty(),
        audioCodec = audio?.codec.orEmpty(),
        audioChannels = audio?.channels ?: 0,
        container = mediaSource.container,
    )
}

fun mediaBrowserSeriesFolder(seriesId: String): String = "mediabrowser-series:$seriesId"

fun mediaBrowserSeriesId(folder: String): String? =
    folder.removePrefix("mediabrowser-series:").takeIf { folder.startsWith("mediabrowser-series:") && it.isNotBlank() }

private fun episodeLabel(type: String, seasonNumber: Int?, episodeNumber: Int?): String {
    if (type != "Episode") return ""
    return if (seasonNumber != null || episodeNumber != null) {
        "S${(seasonNumber ?: 0).toString().padStart(2, '0')}E${(episodeNumber ?: 0).toString().padStart(2, '0')}"
    } else {
        ""
    }
}

private fun itemImageUrl(base: String, itemId: String, imageType: String, tag: String? = null): String {
    val url = "$base/Items/${itemId.encodePathSegment()}/Images/$imageType"
    return tag?.takeIf { it.isNotBlank() }?.let { "$url?tag=${it.encodeQuery()}" } ?: url
}

private fun mediaBrowserFolderLevels(
    filePath: String?,
    seriesName: String?,
    seasonName: String?,
): String? {
    val pathFolder = filePath?.parentPath()?.takeIf { it.isNotBlank() }
    if (pathFolder != null) return pathFolder
    return listOfNotNull(seriesName?.takeIf { it.isNotBlank() }, seasonName?.takeIf { it.isNotBlank() })
        .joinToString("/")
        .ifBlank { null }
}

private fun Long.ticksToSeconds(): Double = toDouble() / MediaBrowserTicksPerSecond

private fun Long.ticksToMinutes(): Int = (this / MediaBrowserTicksPerSecond / 60).toInt()

private fun Map<String, String>.firstMatchingInt(vararg keys: String): Int? =
    keys.firstNotNullOfOrNull { key ->
        entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value?.toIntOrNull()
    }

private fun String.parentPath(): String {
    val normalized = replace('\\', '/')
    return normalized.substringBeforeLast('/', "")
}

private fun String.encodeQuery(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private fun String.encodePathSegment(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private fun MediaBrowserItemDto.mediaBrowserItemCount(): Int =
    listOfNotNull(childCount, recursiveItemCount)
        .firstOrNull { it > 0 }
        ?: when {
            isBrowsableMediaBrowserItem() -> 1
            isPlayableMediaBrowserItem() -> 1
            else -> 0
        }

private fun MediaBrowserItemDto.isPlayableMediaBrowserItem(): Boolean =
    type in MediaBrowserPlayableTypes || (!isFolder && type !in MediaBrowserBrowsableTypes)

private fun MediaBrowserItemDto.isBrowsableMediaBrowserItem(): Boolean =
    isFolder || type in MediaBrowserBrowsableTypes

private val MediaBrowserBrowsableTypes = setOf(
    "AggregateFolder",
    "BoxSet",
    "CollectionFolder",
    "Folder",
    "MusicAlbum",
    "PhotoAlbum",
    "Playlist",
    "Season",
    "Series",
)

private val MediaBrowserPlayableTypes = setOf(
    "Audio",
    "Episode",
    "Movie",
    "MusicVideo",
    "Video",
)

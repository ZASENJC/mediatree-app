package com.zasenjc.mediatree.data

import com.zasenjc.mediatree.playback.PlaybackSource

interface MediaProvider {
    suspend fun authStatus(serverUrl: String? = null): AuthStatusDto

    suspend fun login(serverUrl: String, username: String, password: String): LoginResponseDto

    suspend fun setupAuth(serverUrl: String, username: String, password: String): LoginResponseDto =
        login(serverUrl, username, password)

    suspend fun mediaToken(): MediaTokenDto? = null

    suspend fun mediaRoots(): MediaRootsResponseDto

    suspend fun mediaRoots(profile: ServerProfile): MediaRootsResponseDto = mediaRoots()

    suspend fun folders(mediaRoot: String = ""): FolderTreeResponseDto

    suspend fun recentWatched(limit: Int = 30, offset: Int = 0, mediaRoot: String = ""): MoviesResponseDto

    suspend fun search(
        query: String,
        sort: String = "created_desc",
        limit: Int = 48,
        offset: Int = 0,
        mediaRoot: String = "",
    ): MoviesResponseDto =
        movies(code = query, sort = sort, limit = limit, offset = offset, mediaRoot = mediaRoot)

    suspend fun movies(
        folder: String = "",
        code: String = "",
        tag: String = "",
        sort: String = "created_desc",
        limit: Int = 48,
        offset: Int = 0,
        mediaRoot: String = "",
    ): MoviesResponseDto

    suspend fun favorites(
        limit: Int = 48,
        offset: Int = 0,
        sort: String = "created_desc",
        mediaRoot: String = "",
    ): MoviesResponseDto

    suspend fun detail(movieId: Int): MovieDto

    suspend fun progress(movieId: Int): ProgressDto

    suspend fun saveProgress(
        movieId: Int,
        position: Double,
        duration: Double? = null,
        stopped: Boolean = false,
    ): SaveProgressResponseDto

    suspend fun subtitleTracks(movieId: Int): List<SubtitleTrackDto>

    suspend fun mediaInfo(movieId: Int): MediaInfoDto

    suspend fun addTag(movieId: Int, tag: String): OkResponseDto

    suspend fun removeTag(movieId: Int, tag: String): OkResponseDto

    suspend fun scan(mediaRoot: String = ""): ScanResponseDto

    fun coverUrl(serverUrl: String, movieId: Int): String

    fun episodeStillUrl(serverUrl: String, movieId: Int): String

    fun thumbnailUrl(serverUrl: String, movieId: Int, index: Int): String

    fun streamUrl(serverUrl: String, movieId: Int): String

    fun subtitleUrl(serverUrl: String, movieId: Int, trackIndex: Int): String

    fun playbackSource(
        serverUrl: String,
        movieId: Int,
        token: String,
        userId: String = "",
        mediaToken: String = "",
        subtitleTracks: List<SubtitleTrackDto> = emptyList(),
    ): PlaybackSource
}

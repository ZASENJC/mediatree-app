package com.zasenjc.mediatree.data

import com.zasenjc.mediatree.playback.PlaybackSource
import com.zasenjc.mediatree.playback.toPlaybackSubtitleTrack

class MediaTreeProvider(
    private val api: MediaTreeApi,
) : MediaProvider {
    override suspend fun authStatus(serverUrl: String?): AuthStatusDto =
        api.authStatus(serverUrl)

    override suspend fun login(serverUrl: String, username: String, password: String): LoginResponseDto =
        api.login(serverUrl, username, password)

    override suspend fun mediaRoots(): MediaRootsResponseDto =
        api.mediaRoots()

    override suspend fun mediaRoots(profile: ServerProfile): MediaRootsResponseDto =
        api.mediaRoots(profile.serverUrl, profile.token)

    override suspend fun folders(mediaRoot: String): FolderTreeResponseDto =
        api.folders(mediaRoot)

    override suspend fun recentWatched(limit: Int, offset: Int, mediaRoot: String): MoviesResponseDto =
        api.recentWatched(limit, offset, mediaRoot)

    override suspend fun movies(
        folder: String,
        code: String,
        tag: String,
        sort: String,
        limit: Int,
        offset: Int,
        mediaRoot: String,
    ): MoviesResponseDto =
        api.movies(folder, code, tag, sort, limit, offset, mediaRoot)

    override suspend fun favorites(limit: Int, offset: Int, sort: String): MoviesResponseDto =
        api.favorites(limit, offset, sort)

    override suspend fun detail(movieId: Int): MovieDto =
        api.detail(movieId)

    override suspend fun progress(movieId: Int): ProgressDto =
        api.progress(movieId)

    override suspend fun saveProgress(
        movieId: Int,
        position: Double,
        duration: Double?,
        stopped: Boolean,
    ): SaveProgressResponseDto =
        api.saveProgress(movieId, position, duration, stopped)

    override suspend fun subtitleTracks(movieId: Int): List<SubtitleTrackDto> =
        api.subtitleTracks(movieId)

    override suspend fun mediaInfo(movieId: Int): MediaInfoDto =
        api.mediaInfo(movieId)

    override suspend fun addTag(movieId: Int, tag: String): OkResponseDto =
        api.addTag(movieId, tag)

    override suspend fun removeTag(movieId: Int, tag: String): OkResponseDto =
        api.removeTag(movieId, tag)

    override suspend fun scan(mediaRoot: String): ScanResponseDto =
        api.scan(mediaRoot)

    override fun coverUrl(serverUrl: String, movieId: Int): String =
        api.coverUrl(serverUrl, movieId)

    override fun episodeStillUrl(serverUrl: String, movieId: Int): String =
        api.episodeStillUrl(serverUrl, movieId)

    override fun streamUrl(serverUrl: String, movieId: Int): String =
        api.streamUrl(serverUrl, movieId)

    override fun subtitleUrl(serverUrl: String, movieId: Int, trackIndex: Int): String =
        api.subtitleUrl(serverUrl, movieId, trackIndex)

    override fun playbackSource(
        serverUrl: String,
        movieId: Int,
        token: String,
        userId: String,
        subtitleTracks: List<SubtitleTrackDto>,
    ): PlaybackSource =
        PlaybackSource.mediaTree(
            serverUrl = serverUrl,
            movieId = movieId,
            token = token,
            subtitleTracks = subtitleTracks.map { it.toPlaybackSubtitleTrack() },
        )
}

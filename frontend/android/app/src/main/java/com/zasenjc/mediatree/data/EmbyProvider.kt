package com.zasenjc.mediatree.data

import com.zasenjc.mediatree.playback.PlaybackSource
import com.zasenjc.mediatree.playback.toPlaybackSubtitleTrack

class EmbyProvider(
    sessionStore: SessionStore,
) : JellyfinProvider(sessionStore) {
    override val providerType: ProviderType = ProviderType.Emby
    override val authorizationHeaderName: String = "Authorization"
    // Emby progress marker: /Users/$userId/PlayingItems/$movieId/Progress
    override val authorizationScheme: String = "Emby"
    override val tokenQueryParameter: String = "api_key"

    override fun playbackSource(
        serverUrl: String,
        movieId: Int,
        token: String,
        userId: String,
        mediaToken: String,
        subtitleTracks: List<SubtitleTrackDto>,
    ): PlaybackSource =
        PlaybackSource.emby(
            serverUrl = serverUrl,
            itemId = providerItemId(movieId),
            token = token,
            userId = userId,
            subtitleTracks = subtitleTracks.map { it.toPlaybackSubtitleTrack() },
        )

    override fun progressPath(userId: String, movieId: Int): String =
        "/Users/$userId/PlayingItems/${providerItemId(movieId)}/Progress"

    override fun progressParams(movieId: Int, position: Double, stopped: Boolean): List<Pair<String, String>> =
        listOf(
            "PositionTicks" to secondsToTicks(position).toString(),
            "api_key" to "",
        )
}

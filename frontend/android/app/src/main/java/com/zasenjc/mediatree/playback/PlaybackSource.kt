package com.zasenjc.mediatree.playback

import com.zasenjc.mediatree.data.SubtitleTrackDto
import com.zasenjc.mediatree.util.UrlUtils

sealed interface PlaybackSource {
    val uri: String
    val headers: Map<String, String>
    val subtitleTracks: List<PlaybackSubtitleTrack>

    fun subtitleUri(trackIndex: Int): String? = null

    companion object {
        fun mediaTree(
            serverUrl: String,
            movieId: Int,
            token: String,
            subtitleTracks: List<PlaybackSubtitleTrack> = emptyList(),
        ): HttpPlaybackSource {
            val apiBase = UrlUtils.apiBase(serverUrl)
            return HttpPlaybackSource(
                uri = "$apiBase/stream/$movieId",
                headers = authorizationHeaders(token),
                subtitleTracks = subtitleTracks,
                subtitleUriForTrack = { trackIndex -> "$apiBase/subtitle/$movieId/$trackIndex" },
            )
        }
    }
}

data class PlaybackSubtitleTrack(
    val index: Int,
    val title: String = "",
    val language: String = "",
    val format: String? = null,
)

data class HttpPlaybackSource(
    override val uri: String,
    override val headers: Map<String, String> = emptyMap(),
    override val subtitleTracks: List<PlaybackSubtitleTrack> = emptyList(),
    private val subtitleUriForTrack: ((Int) -> String)? = null,
) : PlaybackSource {
    override fun subtitleUri(trackIndex: Int): String? = subtitleUriForTrack?.invoke(trackIndex)
}

data class WebDavPlaybackSource(
    override val uri: String,
    override val headers: Map<String, String> = emptyMap(),
    override val subtitleTracks: List<PlaybackSubtitleTrack> = emptyList(),
) : PlaybackSource

data class SmbPlaybackSource(
    val share: String,
    val proxyUri: String,
    override val subtitleTracks: List<PlaybackSubtitleTrack> = emptyList(),
) : PlaybackSource {
    override val uri: String = proxyUri
    override val headers: Map<String, String> = emptyMap()
}

data class LocalProxyPlaybackSource(
    override val uri: String,
    val origin: String,
    override val headers: Map<String, String> = emptyMap(),
    override val subtitleTracks: List<PlaybackSubtitleTrack> = emptyList(),
) : PlaybackSource

fun SubtitleTrackDto.toPlaybackSubtitleTrack(): PlaybackSubtitleTrack =
    PlaybackSubtitleTrack(
        index = index,
        title = title,
        language = language,
        format = format,
    )

private fun authorizationHeaders(token: String): Map<String, String> =
    token.takeIf { it.isNotBlank() }
        ?.let { mapOf("Authorization" to "Bearer ".plus(it)) }
        ?: emptyMap()

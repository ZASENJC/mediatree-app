package com.zasenjc.mediatree.playback

import com.zasenjc.mediatree.data.SubtitleTrackDto
import com.zasenjc.mediatree.data.ClientStorageSource
import com.zasenjc.mediatree.data.WebDavClient
import com.zasenjc.mediatree.util.UrlUtils
import java.net.URLEncoder

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

        fun webDav(
            source: ClientStorageSource,
            path: String,
            subtitleTracks: List<PlaybackSubtitleTrack> = emptyList(),
        ): WebDavPlaybackSource = WebDavPlaybackSource(
            uri = WebDavClient.buildResourceUrl(source, path),
            headers = WebDavClient.authorizationHeaders(source),
            subtitleTracks = subtitleTracks,
        )

        fun jellyfin(
            serverUrl: String,
            itemId: String,
            token: String,
            userId: String,
            subtitleTracks: List<PlaybackSubtitleTrack> = emptyList(),
        ): HttpPlaybackSource = mediaBrowserSource(
            serverUrl = serverUrl,
            itemId = itemId,
            token = token,
            userId = userId,
            subtitleTracks = subtitleTracks,
            authorizationHeaderName = "X-Emby-Authorization",
            authorizationScheme = "MediaBrowser",
            subtitleTokenParameter = "api_key",
        )

        fun emby(
            serverUrl: String,
            itemId: String,
            token: String,
            userId: String,
            subtitleTracks: List<PlaybackSubtitleTrack> = emptyList(),
        ): HttpPlaybackSource = mediaBrowserSource(
            serverUrl = serverUrl,
            itemId = itemId,
            token = token,
            userId = userId,
            subtitleTracks = subtitleTracks,
            authorizationHeaderName = "Authorization",
            authorizationScheme = "Emby",
            subtitleTokenParameter = "api_key",
        )

        private fun mediaBrowserSource(
            serverUrl: String,
            itemId: String,
            token: String,
            userId: String,
            subtitleTracks: List<PlaybackSubtitleTrack>,
            authorizationHeaderName: String,
            authorizationScheme: String,
            subtitleTokenParameter: String,
        ): HttpPlaybackSource {
            val base = UrlUtils.normalizeServerUrl(serverUrl)
            val encodedItemId = encodePathSegment(itemId)
            return HttpPlaybackSource(
                uri = "$base/Videos/$encodedItemId/stream?static=true",
                headers = mediaBrowserHeaders(
                    token = token,
                    userId = userId,
                    authorizationHeaderName = authorizationHeaderName,
                    authorizationScheme = authorizationScheme,
                ),
                subtitleTracks = subtitleTracks,
                subtitleUriForTrack = { trackIndex ->
                    val track = subtitleTracks.firstOrNull { it.index == trackIndex }
                    val mediaSourceId = track?.mediaSourceId?.takeIf { it.isNotBlank() }?.let(::encodePathSegment)
                    track?.uri?.takeIf { it.isNotBlank() }
                        ?: if (track != null && mediaSourceId != null) {
                            "$base/Videos/$encodedItemId/$mediaSourceId/Subtitles/$trackIndex/Stream.${track.format?.ifBlank { null } ?: "vtt"}?$subtitleTokenParameter=${encodeQuery(token)}"
                        } else {
                            null
                        }
                },
            )
        }
    }
}

data class PlaybackSubtitleTrack(
    val index: Int,
    val title: String = "",
    val language: String = "",
    val format: String? = null,
    val uri: String? = null,
    val mediaSourceId: String? = null,
)

data class HttpPlaybackSource(
    override val uri: String,
    override val headers: Map<String, String> = emptyMap(),
    override val subtitleTracks: List<PlaybackSubtitleTrack> = emptyList(),
    private val subtitleUriForTrack: ((Int) -> String?)? = null,
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
    val onClose: (() -> Unit)? = null,
) : PlaybackSource

fun SubtitleTrackDto.toPlaybackSubtitleTrack(): PlaybackSubtitleTrack =
    PlaybackSubtitleTrack(
        index = index,
        title = title,
        language = language,
        format = format,
        uri = url,
        mediaSourceId = mediaSourceId,
    )

private fun authorizationHeaders(token: String): Map<String, String> =
    token.takeIf { it.isNotBlank() }
        ?.let { mapOf("Authorization" to "Bearer ".plus(it)) }
        ?: emptyMap()

private fun mediaBrowserHeaders(
    token: String,
    userId: String,
    authorizationHeaderName: String,
    authorizationScheme: String,
): Map<String, String> {
    val authValue = buildString {
        append(authorizationScheme)
        append(" Client=\"MediaTree\", Device=\"Android\", DeviceId=\"mediatree-android\", Version=\"0.1.00\"")
        if (userId.isNotBlank()) append(", UserId=\"").append(userId).append("\"")
        if (token.isNotBlank()) append(", Token=\"").append(token).append("\"")
    }
    return buildMap {
        put(authorizationHeaderName, authValue)
        if (token.isNotBlank()) put("X-Emby-Token", token)
    }
}

private fun encodeQuery(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

private fun encodePathSegment(value: String): String =
    URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

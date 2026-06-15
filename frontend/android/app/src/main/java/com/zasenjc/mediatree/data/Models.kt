package com.zasenjc.mediatree.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
enum class ProviderType {
    MediaTree,
    Jellyfin,
    Emby,
    M3U,
    WebDAV,
    SMB,
}

@Serializable
data class ServerProfile(
    val id: String = DEFAULT_MEDIATREE_PROFILE_ID,
    val type: ProviderType = ProviderType.MediaTree,
    val name: String = type.name,
    val serverUrl: String = "",
    val userId: String = "",
    val token: String = "",
    val activeLibrary: String = "",
    val authenticated: Boolean = false,
) {
    val displayName: String
        get() = name.ifBlank { type.name }
}

const val DEFAULT_MEDIATREE_PROFILE_ID = "mediatree-default"

fun mediaTreeProfile(
    serverUrl: String,
    token: String = "",
    activeLibrary: String = "",
): ServerProfile = ServerProfile(
    id = DEFAULT_MEDIATREE_PROFILE_ID,
    type = ProviderType.MediaTree,
    name = ProviderType.MediaTree.name,
    serverUrl = serverUrl,
    userId = "",
    token = token,
    activeLibrary = activeLibrary,
    authenticated = token.isNotBlank(),
)

@Serializable
data class Session(
    val profiles: List<ServerProfile> = emptyList(),
    val activeProfileId: String = DEFAULT_MEDIATREE_PROFILE_ID,
    val serverUrl: String = profiles.activeProfile(activeProfileId)?.serverUrl.orEmpty(),
    val userId: String = profiles.activeProfile(activeProfileId)?.userId.orEmpty(),
    val token: String = profiles.activeProfile(activeProfileId)?.token.orEmpty(),
    val activeLibrary: String = profiles.activeProfile(activeProfileId)?.activeLibrary.orEmpty(),
) {
    @kotlinx.serialization.Transient
    val resolvedProfiles: List<ServerProfile> =
        profiles
            .ifEmpty {
                if (serverUrl.isNotBlank() || token.isNotBlank() || activeLibrary.isNotBlank()) {
                    listOf(mediaTreeProfile(serverUrl, token, activeLibrary))
                } else {
                    emptyList()
                }
            }
            .map { profile ->
                if (profile.id == activeProfileId && profile.type == ProviderType.MediaTree) {
                    profile.copy(
                        serverUrl = serverUrl.ifBlank { profile.serverUrl },
                        userId = userId.ifBlank { profile.userId },
                        token = token.ifBlank { profile.token },
                        activeLibrary = activeLibrary.ifBlank { profile.activeLibrary },
                    )
                } else {
                    profile
                }
            }

    val activeProfile: ServerProfile?
        get() = resolvedProfiles.activeProfile(activeProfileId)

    val activeProviderType: ProviderType
        get() = when {
            activeLibrary.smbLibrarySourceId() != null -> ProviderType.SMB
            activeLibrary.webDavLibrarySourceId() != null -> ProviderType.WebDAV
            else -> activeProfile?.type ?: ProviderType.MediaTree
        }

    val activeUserId: String
        get() = activeProfile?.userId.orEmpty()
}

private fun List<ServerProfile>.activeProfile(activeProfileId: String): ServerProfile? =
    firstOrNull { it.id == activeProfileId } ?: firstOrNull()

@Serializable
data class AuthStatusDto(
    @SerialName("need_auth") val needAuth: Boolean = true,
    @SerialName("auth_configured") val authConfigured: Boolean = true,
)

@Serializable
data class LoginResponseDto(
    val token: String = "",
    val ok: Boolean = false,
    val userId: String = "",
    val userName: String = "",
)

@Serializable
data class MediaTokenDto(
    val token: String = "",
    @SerialName("expires_at") val expiresAt: Long = 0L,
)

@Serializable
data class MediaRootDto(
    val path: String = "",
    val label: String = "",
    @SerialName("movie_count") val movieCount: Int = 0,
    val locked: Boolean = false,
    val scraper: String = "",
)

@Serializable
data class MediaRootsResponseDto(
    val items: List<MediaRootDto> = emptyList(),
)

@Serializable
data class FolderTreeResponseDto(
    val tree: List<FolderNodeDto> = emptyList(),
)

@Serializable
data class FolderNodeDto(
    val name: String = "",
    val path: String = "",
    @SerialName("is_leaf") val isLeaf: Boolean = false,
    @SerialName("movie_count") val movieCount: Int = 0,
    @SerialName("video_count") val videoCount: Int = 0,
    val cover: String? = null,
    @SerialName("random_cover") val randomCover: String? = null,
    val backdrop: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    val children: List<FolderNodeDto> = emptyList(),
    @SerialName("media_root") val mediaRoot: String? = null,
    @SerialName("created_max") val createdMax: String? = null,
    @SerialName("release_date_max") val releaseDateMax: String? = null,
    @SerialName("watched_count") val watchedCount: Int? = null,
    @SerialName("folder_watched") val folderWatched: Boolean? = null,
    @SerialName("progress_percent") val progressPercent: Double? = null,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    @SerialName("tmdb_type") val tmdbType: String? = null,
    @SerialName("special_count") val specialCount: Int? = null,
    @SerialName("show_specials") val showSpecials: Boolean? = null,
)

@Serializable
data class MoviesResponseDto(
    val movies: List<MovieDto> = emptyList(),
    val total: Int = 0,
)

@Serializable
data class MovieDto(
    val id: Int,
    val path: String = "",
    @SerialName("provider_item_id") val providerItemId: String? = null,
    @SerialName("provider_series_id") val providerSeriesId: String? = null,
    @SerialName("provider_season_id") val providerSeasonId: String? = null,
    val code: String = "",
    val title: String? = null,
    @SerialName("original_title") val originalTitle: String? = null,
    val overview: String? = null,
    val actress: String? = null,
    val director: String? = null,
    val series: String? = null,
    val studio: String? = null,
    val genre: String? = null,
    @SerialName("dvd_id") val dvdId: String? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    val duration: Int? = null,
    @SerialName("cover_local") val coverLocal: String? = null,
    @SerialName("cover_remote") val coverRemote: String? = null,
    @SerialName("fanart_local") val fanartLocal: String? = null,
    @SerialName("javdb_url") val javdbUrl: String? = null,
    val keywords: String? = null,
    val studios: String? = null,
    val tagline: String? = null,
    val status: String? = null,
    @SerialName("content_rating") val contentRating: String? = null,
    @SerialName("scraper_source") val scraperSource: String? = null,
    @SerialName("source_id") val sourceId: String? = null,
    @SerialName("javdb_id") val javdbId: String? = null,
    @SerialName("javdb_score") val javdbScore: Double? = null,
    @SerialName("javdb_likes") val javdbLikes: Int? = null,
    @Serializable(with = StringListSerializer::class)
    @SerialName("javdb_thumbnails") val javdbThumbnails: List<String> = emptyList(),
    @Serializable(with = StringListSerializer::class)
    @SerialName("javdb_comments") val javdbComments: List<String> = emptyList(),
    @SerialName("folder_levels") val folderLevels: String? = null,
    @Serializable(with = StringListSerializer::class)
    val tags: List<String> = emptyList(),
    @SerialName("media_root") val mediaRoot: String? = null,
    @SerialName("tmdb_id") val tmdbId: Int? = null,
    @SerialName("tmdb_type") val tmdbType: String? = null,
    @SerialName("tmdb_season") val tmdbSeason: Int? = null,
    @SerialName("tmdb_episode") val tmdbEpisode: Int? = null,
    @SerialName("episode_title") val episodeTitle: String? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("episode_label") val episodeLabel: String? = null,
    @SerialName("episode_overview") val episodeOverview: String? = null,
    @SerialName("episode_still") val episodeStill: String? = null,
    @SerialName("episode_still_local") val episodeStillLocal: String? = null,
    @SerialName("clean_title") val cleanTitle: String? = null,
    @SerialName("display_title") val displayTitle: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
    @SerialName("file_size") val fileSize: Long? = null,
    val size: Long? = null,
    @SerialName("playback_position") val playbackPosition: Double? = null,
    @SerialName("progress_percent") val progressPercent: Double? = null,
    @Serializable(with = PersonCreditListSerializer::class)
    val cast: List<PersonCreditDto> = emptyList(),
    @Serializable(with = CrewCreditListSerializer::class)
    val crew: List<CrewCreditDto> = emptyList(),
    @Serializable(with = ExternalAudioTrackListSerializer::class)
    @SerialName("external_audio_tracks") val externalAudioTracks: List<ExternalAudioTrackDto> = emptyList(),
    @SerialName("content_role") val contentRole: String? = null,
    @SerialName("special_parent_levels") val specialParentLevels: String? = null,
)

@Serializable
data class ExternalAudioTrackDto(
    val path: String = "",
    val name: String = "",
    val source: String? = null,
    val language: String? = null,
    val codec: String? = null,
    val format: String? = null,
    val title: String? = null,
    @SerialName("is_external") val isExternal: Boolean? = null,
)

@Serializable
data class PersonCreditDto(
    val name: String = "",
    val character: String? = null,
    val role: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("person_id") val personId: String? = null,
    val source: String? = null,
)

@Serializable
data class CrewCreditDto(
    val name: String = "",
    val job: String = "",
    val department: String? = null,
    @SerialName("profile_path") val profilePath: String? = null,
    @SerialName("person_id") val personId: String? = null,
    val source: String? = null,
)

@Serializable
data class ProgressDto(
    val position: Double = 0.0,
    val played: Boolean = false,
    @SerialName("progress_percent") val progressPercent: Double = 0.0,
)

@Serializable
data class SaveProgressResponseDto(
    val ok: Boolean = false,
    val played: Boolean = false,
    @SerialName("progress_percent") val progressPercent: Double = 0.0,
)

@Serializable
data class SubtitleTrackDto(
    val index: Int,
    @SerialName("stream_index") val streamIndex: Int = -1,
    val codec: String = "",
    val language: String = "",
    val title: String = "",
    val name: String? = null,
    val source: String? = null,
    val path: String? = null,
    val url: String? = null,
    val format: String? = null,
    @SerialName("media_source_id") val mediaSourceId: String? = null,
    @SerialName("is_external") val isExternal: Boolean = false,
    @SerialName("web_supported") val webSupported: Boolean? = null,
)

@Serializable
data class MediaInfoDto(
    val duration: Double = 0.0,
    @SerialName("video_codec") val videoCodec: String = "",
    @SerialName("audio_codec") val audioCodec: String = "",
    @SerialName("audio_channels") val audioChannels: Int = 0,
    val container: String = "",
    @Serializable(with = ExternalAudioTrackListSerializer::class)
    @SerialName("external_audio_tracks") val externalAudioTracks: List<ExternalAudioTrackDto> = emptyList(),
)

@Serializable
data class ConfigDto(
    @SerialName("tmdb_configured") val tmdbConfigured: Boolean = false,
    @SerialName("tmdb_api_key") val tmdbApiKey: String = "",
    @SerialName("tmdb_access_token") val tmdbAccessToken: String = "",
)

@Serializable
data class ScanResponseDto(
    val total: Int = 0,
    val codes: List<String> = emptyList(),
)

@Serializable
data class OkResponseDto(
    val ok: Boolean = false,
)

private val modelJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

object StringListSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> =
        decodeFlexibleList(decoder, delegate) { value ->
            if (value.contains(",")) value.split(",").map { it.trim() }.filter { it.isNotEmpty() } else listOf(value)
        }

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

object PersonCreditListSerializer : KSerializer<List<PersonCreditDto>> {
    private val delegate = ListSerializer(PersonCreditDto.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<PersonCreditDto> =
        decodeFlexibleList(decoder, delegate)

    override fun serialize(encoder: Encoder, value: List<PersonCreditDto>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

object CrewCreditListSerializer : KSerializer<List<CrewCreditDto>> {
    private val delegate = ListSerializer(CrewCreditDto.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<CrewCreditDto> =
        decodeFlexibleList(decoder, delegate)

    override fun serialize(encoder: Encoder, value: List<CrewCreditDto>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

object ExternalAudioTrackListSerializer : KSerializer<List<ExternalAudioTrackDto>> {
    private val delegate = ListSerializer(ExternalAudioTrackDto.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<ExternalAudioTrackDto> =
        decodeFlexibleList(decoder, delegate)

    override fun serialize(encoder: Encoder, value: List<ExternalAudioTrackDto>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

private fun <T> decodeFlexibleList(
    decoder: Decoder,
    listSerializer: KSerializer<List<T>>,
    primitiveFallback: (String) -> List<T> = { emptyList() },
): List<T> {
    val jsonDecoder = decoder as? JsonDecoder ?: return decoder.decodeSerializableValue(listSerializer)
    val element = jsonDecoder.decodeJsonElement()
    return decodeFlexibleListElement(element, listSerializer, primitiveFallback)
}

private fun <T> decodeFlexibleListElement(
    element: JsonElement,
    listSerializer: KSerializer<List<T>>,
    primitiveFallback: (String) -> List<T>,
): List<T> = when (element) {
    is JsonArray -> runCatching { modelJson.decodeFromJsonElement(listSerializer, element) }.getOrDefault(emptyList())
    JsonNull -> emptyList()
    is JsonPrimitive -> {
        val text = element.contentOrNull?.trim().orEmpty()
        when {
            text.isBlank() || text == "null" -> emptyList()
            text.startsWith("[") -> runCatching { modelJson.decodeFromString(listSerializer, text) }.getOrDefault(emptyList())
            else -> primitiveFallback(text)
        }
    }
    else -> emptyList()
}

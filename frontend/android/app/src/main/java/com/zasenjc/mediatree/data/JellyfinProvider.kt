package com.zasenjc.mediatree.data

import com.zasenjc.mediatree.BuildConfig
import com.zasenjc.mediatree.playback.PlaybackSource
import com.zasenjc.mediatree.playback.toPlaybackSubtitleTrack
import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

open class JellyfinProvider(
    @PublishedApi
    internal val sessionStore: SessionStore,
    @PublishedApi
    internal val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build(),
    @PublishedApi
    internal val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    },
) : MediaProvider {
    // Endpoint markers: /Users/$userId/Views /Users/$userId/Items /Items/$movieId/PlaybackInfo
    // URL markers: /Items/$movieId/Images/Primary /Videos/$movieId/stream
    protected open val providerType: ProviderType = ProviderType.Jellyfin
    protected open val authorizationHeaderName: String = "X-Emby-Authorization"
    protected open val authorizationScheme: String = "MediaBrowser"
    protected open val tokenQueryParameter: String = "api_key"
    private val providerIdsByMovieId = mutableMapOf<Int, String>()

    override suspend fun authStatus(serverUrl: String?): AuthStatusDto = AuthStatusDto(needAuth = true)

    override suspend fun login(serverUrl: String, username: String, password: String): LoginResponseDto {
        val result = request<JellyfinAuthResponse>(
            path = "/Users/AuthenticateByName",
            method = "POST",
            body = json.encodeToString(JellyfinLoginRequest(username, password)),
            serverOverride = serverUrl,
            tokenOverride = "",
            userIdOverride = "",
        )
        return LoginResponseDto(
            token = result.accessToken,
            ok = result.accessToken.isNotBlank(),
            userId = result.user.id,
            userName = result.user.name,
        )
    }

    override suspend fun mediaRoots(): MediaRootsResponseDto {
        val session = currentSession()
        return mediaRootsFor(
            serverUrl = session.serverUrl,
            token = session.token,
            userId = session.requireUserId(),
        )
    }

    override suspend fun mediaRoots(profile: ServerProfile): MediaRootsResponseDto =
        mediaRootsFor(
            serverUrl = profile.serverUrl,
            token = profile.token,
            userId = profile.requireUserId(),
        )

    private suspend fun mediaRootsFor(
        serverUrl: String,
        token: String,
        userId: String,
    ): MediaRootsResponseDto {
        val result = request<MediaBrowserItemsResponse>(
            path = "/Users/$userId/Views",
            params = listOf("IncludeExternalContent" to "false"),
            serverOverride = serverUrl,
            tokenOverride = token,
            userIdOverride = userId,
        )
        return MediaRootsResponseDto(
            items = result.items.map { item ->
                MediaRootDto(
                    path = item.id,
                    label = item.name,
                    movieCount = item.childCount ?: 0,
                    locked = false,
                    scraper = providerType.name,
                )
            },
        )
    }

    override suspend fun folders(mediaRoot: String): FolderTreeResponseDto {
        val session = currentSession()
        val result = items(
            userId = session.requireUserId(),
            params = listOf(
                "ParentId" to mediaRoot,
                "Recursive" to "false",
                "IncludeItemTypes" to MediaBrowserFolderItemTypes,
                "Fields" to ItemFields,
                "SortBy" to "SortName",
                "SortOrder" to "Ascending",
            ),
        )
        return FolderTreeResponseDto(
            tree = result.items.map { item ->
                item.toMediaTreeFolderNodeDto(
                    serverUrl = session.serverUrl,
                    parentMediaRoot = mediaRoot,
                    rememberId = ::rememberProviderItemId,
                )
            },
        )
    }

    override suspend fun recentWatched(limit: Int, offset: Int, mediaRoot: String): MoviesResponseDto {
        val session = currentSession()
        return moviesFromItems(
            items(
                userId = session.requireUserId(),
                params = listOf(
                    "ParentId" to mediaRoot,
                    "Recursive" to "true",
                    "IncludeItemTypes" to "Movie,Episode",
                    "Filters" to "IsResumable",
                    "SortBy" to "DatePlayed",
                    "SortOrder" to "Descending",
                    "StartIndex" to offset.toString(),
                    "Limit" to limit.toString(),
                    "Fields" to ItemFields,
                ),
            ),
            session = session,
        )
    }

    override suspend fun movies(
        folder: String,
        code: String,
        tag: String,
        sort: String,
        limit: Int,
        offset: Int,
        mediaRoot: String,
    ): MoviesResponseDto {
        val session = currentSession()
        mediaBrowserSeriesId(folder)?.let { seriesId ->
            val (sortBy, sortOrder) = sort.toJellyfinSort()
            return moviesFromItems(
                request(
                    path = "/Shows/${seriesId.encodePathSegment()}/Episodes",
                    params = buildList {
                        add("UserId" to session.requireUserId())
                        add("Fields" to ItemFields)
                        add("StartIndex" to offset.toString())
                        add("Limit" to limit.toString())
                        add("SortBy" to sortBy)
                        add("SortOrder" to sortOrder)
                        code.takeIf { it.isNotBlank() }?.let { add("SearchTerm" to it) }
                        tag.takeIf { it.isNotBlank() }?.let { add("Filters" to if (it == "favorite") "IsFavorite" else it) }
                    },
                ),
                session = session,
            )
        }
        val parentId = folder.ifBlank { mediaRoot }
        val params = buildList {
            if (parentId.isNotBlank()) add("ParentId" to parentId)
            add("Recursive" to "true")
            add("IncludeItemTypes" to "Movie,Episode")
            add("StartIndex" to offset.toString())
            add("Limit" to limit.toString())
            add("Fields" to ItemFields)
            code.takeIf { it.isNotBlank() }?.let { add("SearchTerm" to it) }
            tag.takeIf { it.isNotBlank() }?.let { add("Filters" to if (it == "favorite") "IsFavorite" else it) }
            val (sortBy, sortOrder) = sort.toJellyfinSort()
            add("SortBy" to sortBy)
            add("SortOrder" to sortOrder)
        }
        return moviesFromItems(items(session.requireUserId(), params), session)
    }

    override suspend fun favorites(limit: Int, offset: Int, sort: String): MoviesResponseDto {
        val session = currentSession()
        val (sortBy, sortOrder) = sort.toJellyfinSort()
        return moviesFromItems(
            items(
                userId = session.requireUserId(),
                params = listOf(
                    "Recursive" to "true",
                    "IncludeItemTypes" to "Movie,Episode",
                    "Filters" to "IsFavorite",
                    "StartIndex" to offset.toString(),
                    "Limit" to limit.toString(),
                    "Fields" to ItemFields,
                    "SortBy" to sortBy,
                    "SortOrder" to sortOrder,
                ),
            ),
            session = session,
        )
    }

    override suspend fun detail(movieId: Int): MovieDto {
        val session = currentSession()
        return request<MediaBrowserItemDto>(
            path = "/Users/${session.requireUserId()}/Items/${providerItemId(movieId)}",
            params = listOf("Fields" to ItemFields),
        ).toRegisteredMovieDto(session)
    }

    override suspend fun progress(movieId: Int): ProgressDto {
        val item = detail(movieId)
        val position = item.playbackPosition ?: 0.0
        return ProgressDto(
            position = position,
            played = item.tags.contains("watched"),
            progressPercent = item.progressPercent ?: 0.0,
        )
    }

    override suspend fun saveProgress(
        movieId: Int,
        position: Double,
        duration: Double?,
        stopped: Boolean,
    ): SaveProgressResponseDto {
        val session = currentSession()
        val path = progressPath(session.requireUserId(), movieId)
        request<Unit>(
            path = path,
            method = "POST",
            params = progressParams(movieId, position, stopped),
        )
        return SaveProgressResponseDto(
            ok = true,
            played = stopped,
            progressPercent = if (duration != null && duration > 0.0) position / duration * 100.0 else 0.0,
        )
    }

    override suspend fun subtitleTracks(movieId: Int): List<SubtitleTrackDto> {
        val session = currentSession()
        val playbackInfo = request<MediaBrowserPlaybackInfoDto>(
            path = "/Items/${providerItemId(movieId)}/PlaybackInfo",
            params = listOf("UserId" to session.requireUserId()),
        )
        return playbackInfo.mediaSources
            .firstOrNull()
            ?.let { mediaSource ->
                mediaSource.mediaStreams
                    .filter { it.type.equals("Subtitle", ignoreCase = true) }
                    .map { stream ->
                        SubtitleTrackDto(
                            index = stream.index,
                            streamIndex = stream.index,
                            codec = stream.codec,
                            language = stream.language.orEmpty(),
                            title = stream.displayTitle.ifBlank { stream.title.orEmpty() },
                            source = providerType.name,
                            url = subtitleStreamUrl(
                                serverUrl = session.serverUrl,
                                movieId = movieId,
                                mediaSourceId = mediaSource.id,
                                trackIndex = stream.index,
                                format = stream.codec,
                                token = session.token,
                            ),
                            format = stream.codec.ifBlank { "vtt" },
                            mediaSourceId = mediaSource.id,
                            isExternal = stream.isExternal,
                        )
                    }
            }
            .orEmpty()
    }

    override suspend fun mediaInfo(movieId: Int): MediaInfoDto {
        return request<MediaBrowserPlaybackInfoDto>(
            path = "/Items/${providerItemId(movieId)}/PlaybackInfo",
            params = listOf("UserId" to currentSession().requireUserId()),
        ).toMediaInfoDto()
    }

    override suspend fun addTag(movieId: Int, tag: String): OkResponseDto {
        val session = currentSession()
        when (tag) {
            "watched" -> request<Unit>(
                path = "/Users/${session.requireUserId()}/PlayedItems/${providerItemId(movieId)}",
                method = "POST",
            )
            "favorite" -> request<Unit>(
                path = "/Users/${session.requireUserId()}/FavoriteItems/${providerItemId(movieId)}",
                method = "POST",
            )
        }
        return OkResponseDto(ok = true)
    }

    override suspend fun removeTag(movieId: Int, tag: String): OkResponseDto {
        val session = currentSession()
        if (tag == "favorite") {
            request<Unit>(
                path = "/Users/${session.requireUserId()}/FavoriteItems/${providerItemId(movieId)}",
                method = "DELETE",
            )
        }
        return OkResponseDto(ok = true)
    }

    override suspend fun scan(mediaRoot: String): ScanResponseDto = ScanResponseDto(total = 0)

    override fun coverUrl(serverUrl: String, movieId: Int): String =
        "${UrlUtils.normalizeServerUrl(serverUrl)}/Items/${providerItemId(movieId)}/Images/Primary"

    override fun episodeStillUrl(serverUrl: String, movieId: Int): String =
        "${UrlUtils.normalizeServerUrl(serverUrl)}/Items/${providerItemId(movieId)}/Images/Backdrop"

    override fun streamUrl(serverUrl: String, movieId: Int): String =
        "${UrlUtils.normalizeServerUrl(serverUrl)}/Videos/${providerItemId(movieId)}/stream?static=true"

    override fun subtitleUrl(serverUrl: String, movieId: Int, trackIndex: Int): String =
        subtitleStreamUrl(
            serverUrl = serverUrl,
            movieId = movieId,
            mediaSourceId = null,
            trackIndex = trackIndex,
            format = "vtt",
            token = "",
        )

    override fun playbackSource(
        serverUrl: String,
        movieId: Int,
        token: String,
        userId: String,
        subtitleTracks: List<SubtitleTrackDto>,
    ): PlaybackSource =
        PlaybackSource.jellyfin(
            serverUrl = serverUrl,
            itemId = providerItemId(movieId),
            token = token,
            userId = userId,
            subtitleTracks = subtitleTracks.map { it.toPlaybackSubtitleTrack() },
        )

    protected open fun progressPath(userId: String, movieId: Int): String = "/Sessions/Playing/Progress"

    protected open fun progressParams(movieId: Int, position: Double, stopped: Boolean): List<Pair<String, String>> =
        listOf(
            "ItemId" to providerItemId(movieId),
            "PositionTicks" to secondsToTicks(position).toString(),
            "IsPaused" to "false",
            "EventName" to if (stopped) "timeupdate" else "timeupdate",
        )

    private suspend fun currentSession(): Session = sessionStore.sessionFlow.first()

    private suspend fun items(userId: String, params: List<Pair<String, String>>): MediaBrowserItemsResponse =
        request(path = "/Users/$userId/Items", params = params)

    private fun moviesFromItems(response: MediaBrowserItemsResponse, session: Session): MoviesResponseDto =
        MoviesResponseDto(
            movies = response.items.map { it.toRegisteredMovieDto(session) },
            total = response.totalRecordCount,
        )

    protected suspend inline fun <reified T> request(
        path: String,
        method: String = "GET",
        body: String? = null,
        params: List<Pair<String, String>> = emptyList(),
        serverOverride: String? = null,
        tokenOverride: String? = null,
        userIdOverride: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val session = sessionStore.sessionFlow.first()
        val server = UrlUtils.normalizeServerUrl(serverOverride ?: session.serverUrl)
        if (server.isBlank()) throw java.io.IOException("Server URL is required")
        val urlBuilder = "$server$path".toHttpUrl().newBuilder()
        params.filter { it.second.isNotBlank() }.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        val requestBuilder = Request.Builder().url(urlBuilder.build())
        val token = tokenOverride ?: session.token
        val userId = userIdOverride ?: session.activeUserId
        requestBuilder.header(
            authorizationHeaderName,
            mediaBrowserAuthorizationValue(
                scheme = authorizationScheme,
                token = token,
                userId = userId,
            ),
        )
        if (token.isNotBlank()) requestBuilder.header("X-Emby-Token", token)
        if (body != null) {
            requestBuilder.method(method, body.toRequestBody(JsonMediaType))
            requestBuilder.header("Content-Type", "application/json")
        } else {
            val requestBody = if (method.requiresRequestBody()) EmptyRequestBody else null
            requestBuilder.method(method, requestBody)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) {
                sessionStore.clearToken()
                throw ApiException(401, "Unauthorized")
            }
            if (!response.isSuccessful) {
                throw ApiException(response.code, text.ifBlank { response.message })
            }
            if (T::class == Unit::class || text.isBlank()) Unit as T else json.decodeFromString<T>(text)
        }
    }

    fun registerProviderItemId(movieId: Int, itemId: String) {
        if (itemId.isNotBlank()) providerIdsByMovieId[movieId] = itemId
    }

    private fun MediaBrowserItemDto.toRegisteredMovieDto(session: Session): MovieDto =
        toMediaTreeMovieDto(session.serverUrl, providerType).also { movie ->
            registerProviderItemId(movie.id, id)
        }

    private fun rememberProviderItemId(itemId: String): Int =
        mediaBrowserRouteId(itemId).also { movieId ->
            registerProviderItemId(movieId, itemId)
        }

    protected fun providerItemId(movieId: Int): String =
        providerIdsByMovieId[movieId] ?: movieId.toUInt().toString(16).padStart(8, '0')

    private fun subtitleStreamUrl(
        serverUrl: String,
        movieId: Int,
        mediaSourceId: String?,
        trackIndex: Int,
        format: String?,
        token: String,
    ): String {
        val base = UrlUtils.normalizeServerUrl(serverUrl)
        val itemId = providerItemId(movieId).encodePathSegment()
        val sourceId = mediaSourceId?.takeIf { it.isNotBlank() }?.encodePathSegment() ?: itemId
        val extension = format?.ifBlank { null } ?: "vtt"
        val tokenPart = token.takeIf { it.isNotBlank() }?.let { "${tokenQueryParameter}=${it.encodeQuery()}" }.orEmpty()
        return "$base/Videos/$itemId/$sourceId/Subtitles/$trackIndex/Stream.$extension?$tokenPart"
    }

}

@Serializable
private data class JellyfinLoginRequest(
    @SerialName("Username") val username: String,
    @SerialName("Pw") val password: String,
)

@Serializable
private data class JellyfinAuthResponse(
    @SerialName("AccessToken") val accessToken: String = "",
    @SerialName("User") val user: JellyfinUserDto = JellyfinUserDto(),
)

@Serializable
private data class JellyfinUserDto(
    @SerialName("Id") val id: String = "",
    @SerialName("Name") val name: String = "",
)

private const val ItemFields = "Overview,Genres,Tags,Studios,People,MediaSources,UserData,PrimaryImageAspectRatio,BasicSyncInfo,Path,DateCreated,PremiereDate,ProductionYear,OfficialRating,CommunityRating,ProviderIds,ImageTags,BackdropImageTags,SeriesId,SeriesName,SeasonId,SeasonName,ParentIndexNumber,IndexNumber,RecursiveItemCount,CollectionType"
private const val MediaBrowserFolderItemTypes = "Folder,Movie,Series,Season,Episode,BoxSet,Video,MusicVideo"
private const val TicksPerSecond = 10_000_000L
@PublishedApi
internal val JsonMediaType = "application/json; charset=utf-8".toMediaType()
@PublishedApi
internal val EmptyRequestBody = ByteArray(0).toRequestBody(JsonMediaType)

internal fun secondsToTicks(seconds: Double): Long = (seconds * TicksPerSecond).toLong().coerceAtLeast(0)

private fun Session.requireUserId(): String =
    activeUserId.ifBlank { throw java.io.IOException("User ID is required for ${activeProviderType.name}") }

private fun ServerProfile.requireUserId(): String =
    userId.ifBlank { throw java.io.IOException("User ID is required for ${type.name}") }

@PublishedApi
internal fun mediaBrowserAuthorizationValue(scheme: String, token: String, userId: String): String =
    buildString {
        append(scheme)
        append(" Client=\"MediaTree\", Device=\"Android\", DeviceId=\"mediatree-android\", Version=\"")
        append(BuildConfig.VERSION_NAME)
        append("\"")
        if (userId.isNotBlank()) append(", UserId=\"").append(userId).append("\"")
        if (token.isNotBlank()) append(", Token=\"").append(token).append("\"")
    }

private fun String.toJellyfinSort(): Pair<String, String> = when (this) {
    "release_date_desc" -> "PremiereDate" to "Descending"
    "release_date_asc" -> "PremiereDate" to "Ascending"
    "title_asc" -> "SortName" to "Ascending"
    "title_desc" -> "SortName" to "Descending"
    "created_asc" -> "DateCreated" to "Ascending"
    "created_desc" -> "DateCreated" to "Descending"
    "size_desc" -> "Size" to "Descending"
    "size_asc" -> "Size" to "Ascending"
    else -> "DateCreated" to "Descending"
}

@PublishedApi
internal fun String.requiresRequestBody(): Boolean =
    equals("POST", ignoreCase = true) ||
        equals("PUT", ignoreCase = true) ||
        equals("PATCH", ignoreCase = true) ||
        equals("PROPPATCH", ignoreCase = true) ||
        equals("REPORT", ignoreCase = true)

private fun String.encodeQuery(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

private fun String.encodePathSegment(): String =
    java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")

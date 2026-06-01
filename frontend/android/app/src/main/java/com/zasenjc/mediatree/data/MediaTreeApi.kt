package com.zasenjc.mediatree.data

import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

class ApiException(val statusCode: Int, message: String) : IOException(message)

class MediaTreeApi(private val sessionStore: SessionStore) {
    @PublishedApi
    internal val sessionStoreRef = sessionStore

    @PublishedApi
    internal val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @PublishedApi
    internal val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    suspend fun authStatus(serverUrl: String? = null): AuthStatusDto =
        request("/auth/status", serverOverride = serverUrl)

    suspend fun login(serverUrl: String, username: String, password: String): LoginResponseDto =
        request(
            path = "/auth/login",
            method = "POST",
            body = json.encodeToString(mapOf("username" to username, "password" to password)),
            serverOverride = serverUrl,
            tokenOverride = "",
        )

    suspend fun mediaRoots(): MediaRootsResponseDto = request("/media-roots")

    suspend fun mediaRoots(serverUrl: String, token: String): MediaRootsResponseDto =
        request("/media-roots", serverOverride = serverUrl, tokenOverride = token)

    suspend fun folders(mediaRoot: String = ""): FolderTreeResponseDto =
        request("/folders", params = params("media_root" to mediaRoot))

    suspend fun recentWatched(limit: Int = 30, offset: Int = 0, mediaRoot: String = ""): MoviesResponseDto =
        request(
            "/recent-watched",
            params = params("limit" to limit.toString(), "offset" to offset.toString(), "media_root" to mediaRoot),
        )

    suspend fun search(
        query: String,
        limit: Int = 48,
        offset: Int = 0,
        mediaRoot: String = "",
    ): MoviesResponseDto = request(
        "/search",
        params = params(
            "q" to query,
            "limit" to limit.toString(),
            "offset" to offset.toString(),
            "media_root" to mediaRoot,
        ),
    )

    suspend fun movies(
        folder: String = "",
        code: String = "",
        tag: String = "",
        sort: String = "created_desc",
        limit: Int = 48,
        offset: Int = 0,
        mediaRoot: String = "",
    ): MoviesResponseDto = request(
        "/movies",
        params = params(
            "folder" to folder,
            "code" to code,
            "tag" to tag,
            "sort" to sort,
            "limit" to limit.toString(),
            "offset" to offset.toString(),
            "media_root" to mediaRoot,
        ),
    )

    suspend fun favorites(limit: Int = 48, offset: Int = 0, sort: String = "created_desc"): MoviesResponseDto =
        request("/favorites", params = params("limit" to "$limit", "offset" to "$offset", "sort" to sort))

    suspend fun detail(movieId: Int): MovieDto = request("/detail/$movieId")

    suspend fun progress(movieId: Int): ProgressDto = request("/progress/$movieId")

    suspend fun saveProgress(
        movieId: Int,
        position: Double,
        duration: Double? = null,
        stopped: Boolean = false,
    ): SaveProgressResponseDto {
        val payload = buildJsonObject {
            put("position", position)
            if (duration != null) put("duration", duration)
            put("stopped", stopped)
        }
        return request(
            "/progress/$movieId",
            method = "POST",
            body = payload.toString(),
        )
    }

    suspend fun subtitleTracks(movieId: Int): List<SubtitleTrackDto> = request("/subtitle-tracks/$movieId")

    suspend fun mediaInfo(movieId: Int): MediaInfoDto = request("/media-info/$movieId")

    suspend fun addTag(movieId: Int, tag: String): OkResponseDto =
        request("/movies/$movieId/tags", method = "POST", body = json.encodeToString(mapOf("tag" to tag)))

    suspend fun removeTag(movieId: Int, tag: String): OkResponseDto =
        request("/movies/$movieId/tags/${encodePath(tag)}", method = "DELETE")

    suspend fun scan(mediaRoot: String = ""): ScanResponseDto =
        request("/scan", params = params("media_root" to mediaRoot))

    fun coverUrl(serverUrl: String, movieId: Int): String = "${UrlUtils.apiBase(serverUrl)}/cover/$movieId"

    fun episodeStillUrl(serverUrl: String, movieId: Int): String = "${UrlUtils.apiBase(serverUrl)}/episode-still/$movieId"

    fun streamUrl(serverUrl: String, movieId: Int): String = "${UrlUtils.apiBase(serverUrl)}/stream/$movieId"

    fun subtitleUrl(serverUrl: String, movieId: Int, trackIndex: Int): String =
        "${UrlUtils.apiBase(serverUrl)}/subtitle/$movieId/$trackIndex"

    @PublishedApi
    internal suspend inline fun <reified T> request(
        path: String,
        method: String = "GET",
        body: String? = null,
        params: List<Pair<String, String>> = emptyList(),
        serverOverride: String? = null,
        tokenOverride: String? = null,
    ): T = withContext(Dispatchers.IO) {
        val session = sessionStoreRef.sessionFlow.first()
        val server = UrlUtils.normalizeServerUrl(serverOverride ?: session.serverUrl)
        if (server.isBlank()) throw IOException("Server URL is required")
        val urlBuilder = "${UrlUtils.apiBase(server)}$path".toHttpUrl().newBuilder()
        params.filter { it.second.isNotBlank() }.forEach { (key, value) ->
            urlBuilder.addQueryParameter(key, value)
        }
        val requestBuilder = Request.Builder().url(urlBuilder.build())
        val token = tokenOverride ?: session.token
        if (token.isNotBlank()) requestBuilder.header("Authorization", "Bearer $token")
        if (body != null) {
            requestBuilder.method(method, body.toRequestBody(JSON_MEDIA_TYPE))
            requestBuilder.header("Content-Type", "application/json")
        } else {
            requestBuilder.method(method, null)
        }
        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (response.code == 401) {
                sessionStoreRef.clearToken()
                throw ApiException(401, "Unauthorized")
            }
            if (!response.isSuccessful) {
                throw ApiException(response.code, text.ifBlank { response.message })
            }
            if (T::class == Unit::class) Unit as T else json.decodeFromString<T>(text)
        }
    }

    private fun params(vararg pairs: Pair<String, String>): List<Pair<String, String>> = pairs.toList()

    private fun encodePath(value: String): String = java.net.URLEncoder.encode(value, Charsets.UTF_8.name())

    companion object {
        @PublishedApi
        internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

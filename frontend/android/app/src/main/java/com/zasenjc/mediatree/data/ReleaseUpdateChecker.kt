package com.zasenjc.mediatree.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit

sealed interface ReleaseUpdateState {
    val currentVersion: String

    data class Checking(override val currentVersion: String) : ReleaseUpdateState
    data class Current(
        override val currentVersion: String,
        val downloadUrl: String = "${ReleaseUpdateChecker.REPOSITORY_URL}/releases/latest",
        val releaseNotes: String = "",
    ) : ReleaseUpdateState
    data class Available(
        override val currentVersion: String,
        val latestVersion: String,
        val downloadUrl: String,
        val releaseNotes: String,
    ) : ReleaseUpdateState
    data class Failed(
        override val currentVersion: String,
        val message: String,
    ) : ReleaseUpdateState
}

class ReleaseUpdateChecker(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    data class LatestRelease(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String,
    )

    private val _state = MutableStateFlow<ReleaseUpdateState>(ReleaseUpdateState.Checking(""))
    val state: StateFlow<ReleaseUpdateState> = _state.asStateFlow()

    suspend fun checkForUpdates(currentVersion: String) {
        val normalizedCurrentVersion = normalizeVersion(currentVersion)
        _state.value = ReleaseUpdateState.Checking(normalizedCurrentVersion)
        _state.value = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url(LATEST_RELEASE_URL)
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "mediatree-app-android")
                    .build()
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        throw IOException(body.ifBlank { response.message })
                    }
                    updateStateFor(normalizedCurrentVersion, parseLatestRelease(body))
                }
            }.getOrElse { throwable ->
                ReleaseUpdateState.Failed(
                    currentVersion = normalizedCurrentVersion,
                    message = throwable.message ?: "更新检查失败",
                )
            }
        }
    }

    companion object {
        const val REPOSITORY_URL = "https://github.com/ZASENJC/mediatree-app"
        const val LATEST_RELEASE_URL = "https://api.github.com/repos/ZASENJC/mediatree-app/releases/latest"

        private val json = Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
            explicitNulls = false
        }
        private val versionPattern = Regex("""(\d+)\.(\d+)\.(\d+)""")

        fun parseLatestRelease(body: String): LatestRelease {
            val release = json.decodeFromString<GitHubReleaseDto>(body)
            val version = normalizeVersion(release.tagName)
            val apkUrl = release.assets
                .firstOrNull { asset ->
                    asset.name.endsWith(".apk", ignoreCase = true) &&
                        asset.browserDownloadUrl.isNotBlank()
                }
                ?.browserDownloadUrl
            return LatestRelease(
                version = version,
                downloadUrl = apkUrl ?: release.htmlUrl.ifBlank { "$REPOSITORY_URL/releases/latest" },
                releaseNotes = release.body.trim(),
            )
        }

        fun updateStateFor(currentVersion: String, latest: LatestRelease): ReleaseUpdateState {
            val normalizedCurrentVersion = normalizeVersion(currentVersion)
            return if (isNewerVersion(latest.version, normalizedCurrentVersion)) {
                ReleaseUpdateState.Available(
                    currentVersion = normalizedCurrentVersion,
                    latestVersion = normalizeVersion(latest.version),
                    downloadUrl = latest.downloadUrl,
                    releaseNotes = latest.releaseNotes,
                )
            } else {
                ReleaseUpdateState.Current(
                    currentVersion = normalizedCurrentVersion,
                    downloadUrl = latest.downloadUrl,
                    releaseNotes = latest.releaseNotes,
                )
            }
        }

        fun isNewerVersion(latestVersion: String, currentVersion: String): Boolean {
            val latest = versionParts(latestVersion) ?: return false
            val current = versionParts(currentVersion) ?: return false
            return latest.zip(current)
                .firstOrNull { (latestPart, currentPart) -> latestPart != currentPart }
                ?.let { (latestPart, currentPart) -> latestPart > currentPart }
                ?: false
        }

        private fun normalizeVersion(value: String): String =
            versionPattern.find(value.trim().removePrefix("v").removePrefix("V"))?.value.orEmpty()

        private fun versionParts(value: String): List<Int>? {
            val version = normalizeVersion(value)
            if (version.isBlank()) return null
            return version.split(".").map { it.toInt() }
        }
    }
}

@Serializable
private data class GitHubReleaseDto(
    @SerialName("tag_name") val tagName: String = "",
    @SerialName("html_url") val htmlUrl: String = "",
    val body: String = "",
    val assets: List<GitHubReleaseAssetDto> = emptyList(),
)

@Serializable
private data class GitHubReleaseAssetDto(
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
)

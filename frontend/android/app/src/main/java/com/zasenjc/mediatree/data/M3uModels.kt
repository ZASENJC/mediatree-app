package com.zasenjc.mediatree.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URI
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

private val Context.m3uFavoritesDataStore by preferencesDataStore("mediatree_m3u_favorites")
private val Context.m3uCacheDataStore by preferencesDataStore("mediatree_m3u_cache")

private val m3uJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
data class M3uChannel(
    val id: String,
    val name: String,
    val streamUrl: String,
    val group: String = "",
    val logoUrl: String = "",
    val tvgId: String = "",
    val tvgName: String = "",
)

@Serializable
data class M3uFavoriteSet(
    val profileId: String,
    val channelIds: Set<String> = emptySet(),
)

@Serializable
data class M3uSubscriptionCacheEntry(
    val profileId: String,
    val subscriptionUrl: String,
    val channels: List<M3uChannel> = emptyList(),
    val fetchedAtMillis: Long = 0L,
)

object M3uParser {
    fun parse(
        playlist: String,
        subscriptionUrl: String,
        profileId: String = "m3u",
    ): List<M3uChannel> {
        val baseUri = subscriptionUrl.takeIf { it.isNotBlank() }?.let { runCatching { URI(it) }.getOrNull() }
        val channels = mutableListOf<M3uChannel>()
        var pendingInfo: ExtInf? = null
        playlist.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = parseExtInf(line)
                    line.startsWith("#") -> Unit
                    else -> {
                        val info = pendingInfo ?: ExtInf(name = line)
                        pendingInfo = null
                        val streamUrl = resolveAllowedUrl(line, baseUri) ?: return@forEach
                        val logoUrl = info.logoUrl.takeIf { it.isNotBlank() }
                            ?.let { resolveAllowedUrl(it, baseUri) }
                            .orEmpty()
                        val name = info.name
                            .ifBlank { info.tvgName }
                            .ifBlank { streamUrl.substringAfterLast('/').substringBefore('?') }
                            .ifBlank { "直播频道" }
                        channels += M3uChannel(
                            id = stableM3uChannelId(profileId, streamUrl),
                            name = name,
                            streamUrl = streamUrl,
                            group = info.group,
                            logoUrl = logoUrl,
                            tvgId = info.tvgId,
                            tvgName = info.tvgName,
                        )
                    }
                }
            }
        return channels.distinctBy { it.id }
    }

    private fun parseExtInf(line: String): ExtInf {
        val payload = line.substringAfter(":", missingDelimiterValue = "")
        val title = payload.substringAfterLast(",", missingDelimiterValue = "").trim()
        val attrs = extInfAttributeRegex.findAll(payload.substringBeforeLast(",", missingDelimiterValue = payload))
            .associate { match ->
                match.groupValues[1].lowercase() to match.groupValues[2].trim()
            }
        return ExtInf(
            name = title,
            tvgId = attrs["tvg-id"].orEmpty(),
            tvgName = attrs["tvg-name"].orEmpty(),
            logoUrl = attrs["tvg-logo"].orEmpty(),
            group = attrs["group-title"].orEmpty(),
        )
    }

    private data class ExtInf(
        val name: String = "",
        val tvgId: String = "",
        val tvgName: String = "",
        val logoUrl: String = "",
        val group: String = "",
    )

    private val extInfAttributeRegex = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")
}

open class M3uSubscriptionClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build(),
) {
    open suspend fun load(profile: ServerProfile): List<M3uChannel> = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val subscriptionUrl = profile.serverUrl.trim()
        require(resolveAllowedUrl(subscriptionUrl, null) != null) { "M3U 订阅地址仅支持 http/https" }
        val request = Request.Builder()
            .url(subscriptionUrl)
            .header("User-Agent", "MediaTree Android")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw java.io.IOException("M3U 订阅加载失败：HTTP ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            M3uParser.parse(body, subscriptionUrl = subscriptionUrl, profileId = profile.id)
        }
    }
}

interface M3uSubscriptionCacheStore {
    val cacheFlow: Flow<List<M3uSubscriptionCacheEntry>>
    suspend fun load(profileId: String): M3uSubscriptionCacheEntry?
    suspend fun save(entry: M3uSubscriptionCacheEntry)
    suspend fun remove(profileId: String)
}

class M3uSubscriptionCacheRepository(
    private val store: M3uSubscriptionCacheStore,
    private val client: M3uSubscriptionClient,
    private val nowMillis: () -> Long = { System.currentTimeMillis() },
) {
    val cacheFlow: Flow<List<M3uSubscriptionCacheEntry>> = store.cacheFlow

    suspend fun hasCacheFor(profile: ServerProfile): Boolean =
        store.load(profile.id)?.subscriptionUrl == profile.serverUrl

    suspend fun loadCached(profile: ServerProfile): List<M3uChannel> {
        val cached = store.load(profile.id)
        if (cached != null && cached.subscriptionUrl == profile.serverUrl) {
            return cached.channels
        }
        return emptyList()
    }

    suspend fun refresh(profile: ServerProfile): List<M3uChannel> {
        val channels = client.load(profile)
        store.save(
            M3uSubscriptionCacheEntry(
                profileId = profile.id,
                subscriptionUrl = profile.serverUrl,
                channels = channels,
                fetchedAtMillis = nowMillis(),
            ),
        )
        return channels
    }

    suspend fun remove(profileId: String) {
        if (profileId.isBlank()) return
        store.remove(profileId)
    }

    companion object {
        const val TtlMillis: Long = 6 * 60 * 60 * 1000L
    }
}

class AndroidM3uSubscriptionCacheStore(context: Context) : M3uSubscriptionCacheStore {
    private val appContext = context.applicationContext

    override val cacheFlow: Flow<List<M3uSubscriptionCacheEntry>> =
        appContext.m3uCacheDataStore.data.map { prefs -> decode(prefs[CACHE_KEY]) }

    override suspend fun load(profileId: String): M3uSubscriptionCacheEntry? =
        decode(appContext.m3uCacheDataStore.data.first()[CACHE_KEY])
            .firstOrNull { it.profileId == profileId }

    override suspend fun save(entry: M3uSubscriptionCacheEntry) {
        appContext.m3uCacheDataStore.edit { prefs ->
            val next = decode(prefs[CACHE_KEY])
                .filterNot { it.profileId == entry.profileId } + entry
            prefs[CACHE_KEY] = m3uJson.encodeToString(next)
        }
    }

    override suspend fun remove(profileId: String) {
        appContext.m3uCacheDataStore.edit { prefs ->
            val next = decode(prefs[CACHE_KEY]).filterNot { it.profileId == profileId }
            if (next.isEmpty()) {
                prefs.remove(CACHE_KEY)
            } else {
                prefs[CACHE_KEY] = m3uJson.encodeToString(next)
            }
        }
    }

    private fun decode(value: String?): List<M3uSubscriptionCacheEntry> =
        value
            ?.let { runCatching { m3uJson.decodeFromString<List<M3uSubscriptionCacheEntry>>(it) }.getOrNull() }
            .orEmpty()

    companion object {
        private val CACHE_KEY = stringPreferencesKey("m3u_subscription_cache")
    }
}

interface M3uFavoritesStore {
    val favoritesFlow: Flow<List<M3uFavoriteSet>>
    suspend fun load(profileId: String): Set<String>
    suspend fun setFavorite(profileId: String, channelId: String, favorite: Boolean)
}

class M3uFavoritesRepository(private val store: M3uFavoritesStore) {
    val favoritesFlow: Flow<List<M3uFavoriteSet>> = store.favoritesFlow

    suspend fun load(profileId: String): Set<String> = store.load(profileId)

    suspend fun setFavorite(profileId: String, channelId: String, favorite: Boolean) {
        if (profileId.isBlank() || channelId.isBlank()) return
        store.setFavorite(profileId, channelId, favorite)
    }
}

class AndroidM3uFavoritesStore(context: Context) : M3uFavoritesStore {
    private val appContext = context.applicationContext

    override val favoritesFlow: Flow<List<M3uFavoriteSet>> =
        appContext.m3uFavoritesDataStore.data.map { prefs -> decode(prefs[FAVORITES_KEY]) }

    override suspend fun load(profileId: String): Set<String> =
        decode(appContext.m3uFavoritesDataStore.data.first()[FAVORITES_KEY])
            .firstOrNull { it.profileId == profileId }
            ?.channelIds
            .orEmpty()

    override suspend fun setFavorite(profileId: String, channelId: String, favorite: Boolean) {
        appContext.m3uFavoritesDataStore.edit { prefs ->
            val current = decode(prefs[FAVORITES_KEY])
            val existing = current.firstOrNull { it.profileId == profileId }
            val nextIds = if (favorite) {
                existing?.channelIds.orEmpty() + channelId
            } else {
                existing?.channelIds.orEmpty() - channelId
            }
            val next = current.filterNot { it.profileId == profileId } +
                M3uFavoriteSet(profileId = profileId, channelIds = nextIds)
            prefs[FAVORITES_KEY] = m3uJson.encodeToString(next.filter { it.channelIds.isNotEmpty() })
        }
    }

    private fun decode(value: String?): List<M3uFavoriteSet> =
        value
            ?.let { runCatching { m3uJson.decodeFromString<List<M3uFavoriteSet>>(it) }.getOrNull() }
            .orEmpty()

    companion object {
        private val FAVORITES_KEY = stringPreferencesKey("m3u_favorites")
    }
}

fun ServerProfile.isM3uProfile(): Boolean =
    type == ProviderType.M3U && resolveAllowedUrl(serverUrl, null) != null

fun resolveAllowedUrl(value: String, baseUri: URI?): String? {
    val trimmed = value.trim()
    if (trimmed.isBlank()) return null
    val resolved = runCatching {
        val uri = URI(trimmed)
        if (uri.isAbsolute) uri else baseUri?.resolve(uri)
    }.getOrNull() ?: return null
    val scheme = resolved.scheme?.lowercase() ?: return null
    if (scheme != "http" && scheme != "https") return null
    return resolved.toASCIIString()
}

private fun stableM3uChannelId(profileId: String, streamUrl: String): String =
    "${profileId.ifBlank { "m3u" }}-${streamUrl.sha256().take(16)}"

private fun String.sha256(): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(toByteArray(Charsets.UTF_8))
    return digest.joinToString("") { "%02x".format(it) }
}

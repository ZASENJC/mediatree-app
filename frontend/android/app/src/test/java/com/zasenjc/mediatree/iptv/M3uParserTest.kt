package com.zasenjc.mediatree.iptv

import com.zasenjc.mediatree.data.M3uParser
import com.zasenjc.mediatree.data.M3uSubscriptionCacheEntry
import com.zasenjc.mediatree.data.M3uSubscriptionCacheRepository
import com.zasenjc.mediatree.data.M3uSubscriptionCacheStore
import com.zasenjc.mediatree.data.M3uSubscriptionClient
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.ServerProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class M3uParserTest {
    @Test
    fun parsesExtinfAttributesAndChannelUrls() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-id="cctv1" tvg-name="CCTV-1" tvg-logo="https://img.example/cctv1.png" group-title="央视",CCTV-1 综合
            http://live.example.com/cctv1/index.m3u8
            #EXTINF:-1 group-title="卫视",湖南卫视
            https://live.example.com/hunan.flv
        """.trimIndent()

        val channels = M3uParser.parse(playlist, subscriptionUrl = "https://iptv.example/list.m3u")

        assertEquals(2, channels.size)
        assertEquals("cctv1", channels[0].tvgId)
        assertEquals("CCTV-1", channels[0].tvgName)
        assertEquals("CCTV-1 综合", channels[0].name)
        assertEquals("央视", channels[0].group)
        assertEquals("https://img.example/cctv1.png", channels[0].logoUrl)
        assertEquals("http://live.example.com/cctv1/index.m3u8", channels[0].streamUrl)
        assertEquals("卫视", channels[1].group)
    }

    @Test
    fun resolvesRelativeUrlsAgainstSubscriptionUrl() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="/logos/news.png",News
            channels/news.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(playlist, subscriptionUrl = "https://iptv.example/live/list.m3u")

        assertEquals("https://iptv.example/live/channels/news.m3u8", channels.single().streamUrl)
        assertEquals("https://iptv.example/logos/news.png", channels.single().logoUrl)
    }

    @Test
    fun skipsUnsupportedOrInvalidChannelUrls() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,Local file
            file:///sdcard/movie.ts
            #EXTINF:-1,JavaScript
            javascript:alert(1)
            #EXTINF:-1,HTTPS live
            https://live.example.com/channel.m3u8
        """.trimIndent()

        val channels = M3uParser.parse(playlist, subscriptionUrl = "https://iptv.example/list.m3u")

        assertEquals(1, channels.size)
        assertEquals("HTTPS live", channels.single().name)
    }

    @Test
    fun createsStableIdsFromProfileAndStreamUrl() {
        val playlist = """
            #EXTM3U
            #EXTINF:-1,One
            https://live.example.com/one.m3u8
        """.trimIndent()

        val first = M3uParser.parse(playlist, subscriptionUrl = "https://iptv.example/a.m3u", profileId = "m3u-a").single()
        val second = M3uParser.parse(playlist, subscriptionUrl = "https://iptv.example/a.m3u", profileId = "m3u-a").single()

        assertEquals(first.id, second.id)
        assertTrue(first.id.startsWith("m3u-a-"))
    }

    @Test
    fun cachedRepositoryDoesNotRefreshRemoteOnReadOnlyLoad() = runTest {
        val store = InMemoryM3uCacheStore()
        val remote = RecordingM3uSubscriptionClient()
        val repository = M3uSubscriptionCacheRepository(store, remote, nowMillis = { 10_000L })
        val profile = testProfile()

        repository.refresh(profile)
        val cached = repository.loadCached(profile)

        assertEquals(1, remote.loadCount)
        assertTrue(repository.hasCacheFor(profile))
        assertEquals("CCTV-1", cached.single().name)
    }

    @Test
    fun cachedRepositoryRefreshesOnlyWhenExplicitlyRequested() = runTest {
        val store = InMemoryM3uCacheStore()
        val remote = RecordingM3uSubscriptionClient()
        val repository = M3uSubscriptionCacheRepository(store, remote, nowMillis = { 20_000L })
        val profile = testProfile()

        repository.refresh(profile)
        repository.loadCached(profile.copy(serverUrl = "https://iptv.example/updated.m3u"))
        repository.refresh(profile.copy(serverUrl = "https://iptv.example/updated.m3u"))

        assertEquals(2, remote.loadCount)
        assertTrue(repository.hasCacheFor(profile.copy(serverUrl = "https://iptv.example/updated.m3u")))
        assertEquals(
            listOf("https://iptv.example/list.m3u", "https://iptv.example/updated.m3u"),
            remote.loadedUrls,
        )
    }

    @Test
    fun cachedRepositoryTtlExpiryAndMissingCacheDoNotAutoRefreshWithoutForce() = runTest {
        var now = 0L
        val store = InMemoryM3uCacheStore()
        val remote = RecordingM3uSubscriptionClient()
        val repository = M3uSubscriptionCacheRepository(store, remote, nowMillis = { now })
        val profile = testProfile()

        assertEquals(emptyList<M3uSubscriptionCacheEntry>(), repository.cacheFlow.first())
        assertEquals(emptyList<com.zasenjc.mediatree.data.M3uChannel>(), repository.loadCached(profile))
        assertEquals(0, remote.loadCount)

        repository.refresh(profile)
        now = M3uSubscriptionCacheRepository.TtlMillis + 1
        val cached = repository.loadCached(profile)

        assertEquals(1, remote.loadCount)
        assertEquals("CCTV-1", cached.single().name)
    }

    private fun testProfile(url: String = "https://iptv.example/list.m3u"): ServerProfile =
        ServerProfile(
            id = "m3u-profile",
            type = ProviderType.M3U,
            name = "M3U",
            serverUrl = url,
            authenticated = true,
        )

    private class RecordingM3uSubscriptionClient : M3uSubscriptionClient() {
        var loadCount = 0
        val loadedUrls = mutableListOf<String>()

        override suspend fun load(profile: ServerProfile) =
            M3uParser.parse(
                playlist = """
                    #EXTM3U
                    #EXTINF:-1,CCTV-1
                    ${profile.serverUrl}/cctv1.m3u8
                """.trimIndent(),
                subscriptionUrl = profile.serverUrl,
                profileId = profile.id,
            ).also {
                loadCount += 1
                loadedUrls += profile.serverUrl
            }
    }

    private class InMemoryM3uCacheStore : M3uSubscriptionCacheStore {
        private val state = MutableStateFlow<List<M3uSubscriptionCacheEntry>>(emptyList())

        override val cacheFlow: Flow<List<M3uSubscriptionCacheEntry>> = state

        override suspend fun load(profileId: String): M3uSubscriptionCacheEntry? =
            state.value.firstOrNull { it.profileId == profileId }

        override suspend fun save(entry: M3uSubscriptionCacheEntry) {
            state.value = state.value.filterNot { it.profileId == entry.profileId } + entry
        }

        override suspend fun remove(profileId: String) {
            state.value = state.value.filterNot { it.profileId == profileId }
        }
    }
}

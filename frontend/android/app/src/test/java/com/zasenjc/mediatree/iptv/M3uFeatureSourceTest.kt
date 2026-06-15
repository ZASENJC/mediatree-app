package com.zasenjc.mediatree.iptv

import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.ui.navigation.topDestinationsFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class M3uFeatureSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun providerTypesIncludeM3uSubscriptionMode() {
        assertTrue(ProviderType.entries.any { it.name == "M3U" })
    }

    @Test
    fun m3uModeHidesBrowseDestination() {
        val routes = topDestinationsFor(ProviderType.M3U).map { it.route }

        assertEquals(listOf("home", "favorites", "settings"), routes)
    }

    @Test
    fun settingsCanAddSwitchAndRemoveM3uSubscriptions() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertTrue(source.contains("ProviderType.M3U"))
        assertTrue(source.contains("M3uConnectionDialog"))
        assertTrue(source.contains("saveM3uProfile"))
        assertTrue(source.contains("M3U 订阅"))
        assertTrue(source.contains("selectM3uProfile"))
        assertTrue(source.contains("M3U 直播 ·"))
    }

    @Test
    fun homeAndFavoritesUseM3uChannelLists() {
        val homeSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val favoritesSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/FavoritesScreen.kt")
            .readText()

        assertTrue(homeSource.contains("loadM3uChannels"))
        assertTrue(homeSource.contains("M3uChannelCard"))
        assertTrue(homeSource.contains("m3uPlayerRoute()"))
        assertTrue(homeSource.contains("m3uSubscriptionCacheRepository.loadCached"))
        assertTrue(homeSource.contains("m3uSubscriptionCacheRepository.refresh"))
        assertTrue(homeSource.contains("homeLayout == HomeLayoutPreference.DirectoryFirst && session.activeProviderType != ProviderType.M3U"))
        assertTrue(favoritesSource.contains("loadM3uFavorites"))
        assertTrue(favoritesSource.contains("M3uChannelGrid"))
        assertTrue(favoritesSource.contains("m3uSubscriptionCacheRepository.loadCached"))
        assertFalse(homeSource.contains("m3uSubscriptionClient.load"))
        assertFalse(favoritesSource.contains("m3uSubscriptionClient.load"))
    }

    @Test
    fun playbackHasDedicatedM3uRouteAndDirectSource() {
        val appSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()
        val playerSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/M3uPlayerScreen.kt")
            .readText()
        val playbackSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/playback/PlaybackSource.kt")
            .readText()

        assertTrue(appSource.contains("m3uPlayer/{channelId}"))
        assertTrue(appSource.contains("M3uPlayerScreen"))
        assertTrue(playerSource.contains("M3uChannelSwitcher"))
        assertTrue(playerSource.contains("m3uSubscriptionCacheRepository.loadCached"))
        assertTrue(playerSource.contains("M3uChannelMiniCard"))
        assertTrue(playerSource.contains("LazyVerticalGrid"))
        assertTrue(playerSource.contains("GridCells.Adaptive"))
        assertTrue(playerSource.contains(".weight(1f)"))
        assertFalse(playerSource.contains("heightIn(max = 300.dp)"))
        assertFalse(playerSource.contains("m3uSubscriptionClient.load"))
        assertTrue(playbackSource.contains("fun m3u("))
        assertTrue(playbackSource.contains("M3uPlaybackSource"))
    }

    @Test
    fun mpvControllerEnablesAv3aAudioDecodeCompatibility() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MpvPlayerController.kt")
            .readText()

        assertTrue(source.contains("vd-lavc-threads"))
        assertTrue(source.contains("ad-lavc-threads"))
        assertTrue(source.contains("\"ad\""))
        assertTrue(source.contains("av3a"))
    }
}

package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StartupPerformanceSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun appContainerIsCreatedOnceAndPassedIntoAppShell() {
        val mainActivity = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/MainActivity.kt")
            .readText()
        val appShell = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()

        assertTrue(mainActivity.contains("val container = remember { AppContainer(this) }"))
        assertTrue(mainActivity.contains("MediaTreeApp(container = container, deepLinkData = deepLinkData)"))
        assertTrue(appShell.contains("fun MediaTreeApp(container: AppContainer, deepLinkData: Uri? = null)"))
        assertFalse(appShell.contains("remember { AppContainer(context) }"))
    }

    @Test
    fun homePagerComposesOnlyCurrentTabAndDefersInactiveWork() {
        val appShell = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()
        val homeScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val browseScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/BrowseScreen.kt")
            .readText()
        val favoritesScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/FavoritesScreen.kt")
            .readText()
        val settingsScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertTrue(appShell.contains("beyondViewportPageCount = 0"))
        assertTrue(appShell.contains("val pageActive = currentRoute == \"main\" && page == pagerState.currentPage"))
        assertTrue(appShell.contains("HomeScreen(") && appShell.contains("active = pageActive"))
        assertTrue(appShell.contains("BrowseScreen(") && appShell.contains("active = pageActive"))
        assertTrue(appShell.contains("FavoritesScreen(") && appShell.contains("active = pageActive"))
        assertTrue(appShell.contains("SettingsScreen(") && appShell.contains("active = pageActive"))

        listOf(homeScreen, browseScreen, favoritesScreen, settingsScreen).forEach { screen ->
            assertTrue(screen.contains("active: Boolean = true"))
            assertTrue(screen.contains("if (!active) return@LaunchedEffect"))
        }
        listOf(homeScreen, browseScreen, favoritesScreen).forEach { screen ->
            assertTrue(screen.contains("if (active)"))
        }
    }

    @Test
    fun systemBarsAreAppliedOnlyWhenThemeChanges() {
        val mainActivity = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/MainActivity.kt")
            .readText()

        assertTrue(mainActivity.contains("LaunchedEffect(darkTheme)"))
        assertFalse(mainActivity.contains("SideEffect"))
    }

    @Test
    fun mediaImagesAvoidPerRecompositionGradientAndRequestWork() {
        val mediaAsyncImage = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/MediaAsyncImage.kt")
            .readText()

        assertFalse(mediaAsyncImage.contains("Brush.linearGradient"))
        assertTrue(mediaAsyncImage.contains("val imageRequest = remember(context, imageUrl)"))
        assertTrue(mediaAsyncImage.contains("model = imageRequest"))
    }

    @Test
    fun browseListUsesStableContentTypesAndSerializedFrameExtraction() {
        val browseScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/BrowseScreen.kt")
            .readText()
        val chromeVisibility = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/ChromeVisibility.kt")
            .readText()

        assertTrue(browseScreen.contains("contentType = { \"folder-poster-row\" }"))
        assertTrue(browseScreen.contains("contentType = { \"folder-icon-row\" }"))
        assertTrue(browseScreen.contains("contentType = { \"folder-compact\" }"))
        assertTrue(browseScreen.contains("contentType = { \"movie-poster-row\" }"))
        assertTrue(browseScreen.contains("contentType = { \"movie-icon-row\" }"))
        assertTrue(browseScreen.contains("contentType = { \"movie-compact\" }"))
        assertTrue(browseScreen.contains("Dispatchers.IO.limitedParallelism(1)"))
        assertTrue(chromeVisibility.contains("var previousVisible: Boolean? = null"))
        assertTrue(chromeVisibility.contains("visible != previousVisible"))
    }
}

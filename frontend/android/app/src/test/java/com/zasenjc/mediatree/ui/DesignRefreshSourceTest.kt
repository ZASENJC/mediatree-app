package com.zasenjc.mediatree.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DesignRefreshSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun themeUsesGreenPlumTokensAndThemeBackgroundSurfaces() {
        val theme = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/theme/Theme.kt")
            .readText()
        val shared = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/SharedComponents.kt")
            .readText()

        assertTrue(theme.contains("GreenPlumPrimary"))
        assertTrue(theme.contains("GreenPlumAccent"))
        assertTrue(theme.contains("Color(0xFFA8C98B)"))
        assertTrue(theme.contains("Color(0xFFF8FBF1)"))
        assertTrue(shared.contains("fun MediaTreePageBackground"))
        assertTrue(shared.contains("MaterialTheme.colorScheme.background"))
    }

    @Test
    fun sharedComponentsExposeDesignReferenceChrome() {
        val shared = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/SharedComponents.kt")
            .readText()
        val appShell = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()

        assertTrue(shared.contains("fun DesignTopAppBar"))
        assertTrue(shared.contains("fun DesignFilterChip"))
        assertTrue(shared.contains("fun DesignSettingsRow"))
        assertTrue(appShell.contains("DesignBottomNavigationBar"))
        assertTrue(appShell.contains("MediaTreePageBackground"))
    }

    @Test
    fun mediaScreensUseReferencePosterAndFolderCards() {
        val posterCard = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/MoviePosterCard.kt")
            .readText()
        val home = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val browse = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/BrowseScreen.kt")
            .readText()
        val favorites = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/FavoritesScreen.kt")
            .readText()

        assertTrue(posterCard.contains("PosterImageFrame"))
        assertTrue(posterCard.contains("PosterTextBelow"))
        assertTrue(posterCard.contains("BookmarkRibbon"))
        assertTrue(home.contains("DesignTopAppBar("))
        assertTrue(home.contains("HomeHeroRail"))
        assertTrue(browse.contains("DesignFolderRow"))
        assertTrue(favorites.contains("DesignFilterChip"))
    }

    @Test
    fun settingsAndDetailMatchReferenceLayouts() {
        val settings = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()
        val detail = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()

        assertTrue(settings.contains("DesignSettingsRow"))
        assertTrue(settings.contains("ThemeModeSelector"))
        assertTrue(detail.contains("LandscapeDetailScaffold"))
        assertTrue(detail.contains("PortraitPlayerCard"))
        assertTrue(detail.contains("DetailTabStrip"))
    }
}

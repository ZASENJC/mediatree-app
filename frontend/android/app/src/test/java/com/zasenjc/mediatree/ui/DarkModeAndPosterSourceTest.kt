package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DarkModeAndPosterSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun pageBackgroundUsesThemeOnlyAndDoesNotForceLightGradient() {
        val shared = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/SharedComponents.kt")
            .readText()
        val backgroundBlock = shared
            .substringAfter("fun MediaTreePageBackground")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")

        assertTrue(backgroundBlock.contains("MaterialTheme.colorScheme.background"))
        assertFalse(backgroundBlock.contains("Color(0xFFEAF4FF)"))
        assertFalse(backgroundBlock.contains("surfaceContainerLow"))
    }

    @Test
    fun darkSchemeUsesNonWhiteBackgroundAndSurfaces() {
        val theme = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/theme/Theme.kt")
            .readText()
        val darkScheme = theme
            .substringAfter("private val MediaTreeDarkScheme")
            .substringBefore("private val MediaTreeTypography")

        assertTrue(darkScheme.contains("background = Color(0xFF10140D)"))
        assertTrue(darkScheme.contains("surface = Color(0xFF181D14)"))
        assertFalse(darkScheme.contains("Color(0xFFF4F9FF)"))
        assertFalse(darkScheme.contains("Color(0xFFFAFDFF)"))
        assertFalse(darkScheme.contains("Color(0xFFF8FBFF)"))
        assertFalse(darkScheme.contains("Color(0xFFF0F6FF)"))
        assertFalse(darkScheme.contains("Color.White"))
    }

    @Test
    fun moviePosterCardHasImageOnlyCardAndTextBelow() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/MoviePosterCard.kt")
            .readText()

        assertFalse(source.contains("ReferencePosterOverlay"))
        assertFalse(source.contains(".background(mediaScrim())"))
        assertTrue(source.contains("PosterImageFrame("))
        assertTrue(source.contains("PosterTextBelow("))
        assertTrue(source.contains("colors = CardDefaults.cardColors(containerColor = Color.Transparent)"))
    }

    @Test
    fun sharedSettingSurfacesProvideThemeContentColor() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/SharedComponents.kt")
            .readText()
        val settingsRow = source.substringAfter("fun DesignSettingsRow").substringBefore("@Composable\nfun DesignSectionCard")
        val sectionCard = source.substringAfter("fun DesignSectionCard").substringBefore("@Composable\nfun DesignIconButton")

        assertTrue(settingsRow.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(sectionCard.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
    }

    @Test
    fun settingsRowIconBackgroundUsesLightTintForContrast() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/SharedComponents.kt")
            .readText()
        val settingsRow = source.substringAfter("fun DesignSettingsRow").substringBefore("@Composable\nfun DesignSectionCard")

        assertTrue(settingsRow.contains("color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)"))
        assertFalse(settingsRow.contains("primaryContainer.copy(alpha = 0.8f)"))
    }

    @Test
    fun settingsRowsDoNotRenderInnerWhiteBackingFrames() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/SharedComponents.kt")
            .readText()
        val settingsRow = source.substringAfter("fun DesignSettingsRow").substringBefore("@Composable\nfun DesignSectionCard")

        assertTrue(settingsRow.contains("color = Color.Transparent"))
        assertFalse(settingsRow.contains("color = MaterialTheme.colorScheme.surfaceContainerLow"))
        assertFalse(settingsRow.contains("tonalElevation = 1.dp"))
        assertFalse(settingsRow.contains("shadowElevation = 2.dp"))
    }

    @Test
    fun settingsSectionCardsDoNotUseTranslucentSurfaceBacking() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/SharedComponents.kt")
            .readText()
        val sectionCard = source.substringAfter("fun DesignSectionCard").substringBefore("@Composable\nfun DesignIconButton")

        assertTrue(sectionCard.contains("containerColor = MaterialTheme.colorScheme.surface"))
        assertFalse(sectionCard.contains("MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)"))
    }
}

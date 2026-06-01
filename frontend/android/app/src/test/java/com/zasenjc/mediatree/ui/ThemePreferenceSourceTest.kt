package com.zasenjc.mediatree.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ThemePreferenceSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun uiPreferencesStorePersistsThemeModePreference() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/UiPreferencesStore.kt")
            .readText()

        assertTrue(source.contains("enum class ThemeModePreference"))
        assertTrue(source.contains("System(\"system\")"))
        assertTrue(source.contains("Light(\"light\")"))
        assertTrue(source.contains("Dark(\"dark\")"))
        assertTrue(source.contains("val themeModeFlow"))
        assertTrue(source.contains("setThemeModePreference"))
    }

    @Test
    fun uiPreferencesStorePersistsFullscreenModePreference() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/UiPreferencesStore.kt")
            .readText()

        assertTrue(source.contains("enum class FullscreenModePreference"))
        assertTrue(source.contains("Portrait(\"portrait\")"))
        assertTrue(source.contains("Landscape(\"landscape\")"))
        assertTrue(source.contains("Auto(\"auto\")"))
        assertTrue(source.contains("val fullscreenModeFlow"))
        assertTrue(source.contains("setFullscreenModePreference"))
        assertTrue(source.contains("private val FULLSCREEN_MODE = stringPreferencesKey(\"fullscreen_mode\")"))
    }

    @Test
    fun mainActivityAppliesSystemOrManualThemeMode() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/MainActivity.kt")
            .readText()

        assertTrue(source.contains("themeModeFlow.collectAsStateWithLifecycle"))
        assertTrue(source.contains("resolveDarkTheme"))
        assertTrue(source.contains("ThemeModePreference.System -> isSystemInDarkTheme()"))
        assertTrue(source.contains("MediaTreeTheme(darkTheme = darkTheme"))
    }

    @Test
    fun settingsScreenExposesThemeModeControls() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertTrue(source.contains("themeModePreference"))
        assertTrue(source.contains("setThemeModePreference"))
        assertTrue(source.contains("ThemeModeSelector"))
        assertTrue(source.contains("跟随系统"))
        assertTrue(source.contains("浅色模式"))
        assertTrue(source.contains("深色模式"))
    }

    @Test
    fun playerScreensApplyFullscreenModePreference() {
        val detail = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()
        val smb = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SmbBrowseScreen.kt")
            .readText()
        val webDav = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/WebDavBrowseScreen.kt")
            .readText()

        assertTrue(detail.contains("fullscreenModeFlow.collectAsStateWithLifecycle"))
        assertTrue(detail.contains("requestFullscreenOrientation(activity, fullscreenModePreference)"))
        assertTrue(smb.contains("fullscreenModeFlow.collectAsStateWithLifecycle"))
        assertTrue(smb.contains("requestFullscreenOrientation(activity, fullscreenModePreference)"))
        assertTrue(webDav.contains("fullscreenModeFlow.collectAsStateWithLifecycle"))
        assertTrue(webDav.contains("requestFullscreenOrientation(activity, fullscreenModePreference)"))
    }
}

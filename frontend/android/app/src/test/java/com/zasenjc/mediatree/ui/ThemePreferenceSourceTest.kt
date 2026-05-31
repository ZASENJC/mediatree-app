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
}

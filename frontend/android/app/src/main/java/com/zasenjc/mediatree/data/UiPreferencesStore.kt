package com.zasenjc.mediatree.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class HomeLayoutPreference(val value: String) {
    MediaFeed("media_feed"),
    DirectoryFirst("directory_first"),
}

enum class ThemeModePreference(val value: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
}

enum class FullscreenModePreference(val value: String) {
    Portrait("portrait"),
    Landscape("landscape"),
    Auto("auto"),
}

const val DEFAULT_THEME_COLOR = "#B7F07A"

private val Context.uiPreferencesDataStore by preferencesDataStore("mediatree_ui_preferences")

class UiPreferencesStore(context: Context) {
    private val appContext = context.applicationContext

    val homeLayoutFlow: Flow<HomeLayoutPreference> = appContext.uiPreferencesDataStore.data.map { prefs ->
        when (prefs[HOME_LAYOUT]) {
            HomeLayoutPreference.DirectoryFirst.value -> HomeLayoutPreference.DirectoryFirst
            else -> HomeLayoutPreference.MediaFeed
        }
    }

    val themeModeFlow: Flow<ThemeModePreference> = appContext.uiPreferencesDataStore.data.map { prefs ->
        when (prefs[THEME_MODE]) {
            ThemeModePreference.Light.value -> ThemeModePreference.Light
            ThemeModePreference.Dark.value -> ThemeModePreference.Dark
            else -> ThemeModePreference.System
        }
    }

    val themeColorFlow: Flow<String> = appContext.uiPreferencesDataStore.data.map { prefs ->
        sanitizeThemeColor(prefs[THEME_COLOR].orEmpty())
    }

    val fullscreenModeFlow: Flow<FullscreenModePreference> = appContext.uiPreferencesDataStore.data.map { prefs ->
        when (prefs[FULLSCREEN_MODE]) {
            FullscreenModePreference.Portrait.value -> FullscreenModePreference.Portrait
            FullscreenModePreference.Auto.value -> FullscreenModePreference.Auto
            else -> FullscreenModePreference.Landscape
        }
    }

    suspend fun setHomeLayoutPreference(preference: HomeLayoutPreference) {
        appContext.uiPreferencesDataStore.edit { prefs ->
            prefs[HOME_LAYOUT] = preference.value
        }
    }

    suspend fun setThemeModePreference(preference: ThemeModePreference) {
        appContext.uiPreferencesDataStore.edit { prefs ->
            prefs[THEME_MODE] = preference.value
        }
    }

    suspend fun setThemeColorPreference(value: String) {
        appContext.uiPreferencesDataStore.edit { prefs ->
            prefs[THEME_COLOR] = sanitizeThemeColor(value)
        }
    }

    suspend fun setFullscreenModePreference(preference: FullscreenModePreference) {
        appContext.uiPreferencesDataStore.edit { prefs ->
            prefs[FULLSCREEN_MODE] = preference.value
        }
    }

    companion object {
        private val HOME_LAYOUT = stringPreferencesKey("home_layout")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
        private val THEME_COLOR = stringPreferencesKey("theme_color")
        private val FULLSCREEN_MODE = stringPreferencesKey("fullscreen_mode")
    }
}

fun sanitizeThemeColor(value: String): String {
    val normalized = value.trim().uppercase()
    val withPrefix = if (normalized.startsWith("#")) normalized else "#$normalized"
    return if (Regex("^#[0-9A-F]{6}$").matches(withPrefix)) withPrefix else DEFAULT_THEME_COLOR
}

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

    companion object {
        private val HOME_LAYOUT = stringPreferencesKey("home_layout")
        private val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}

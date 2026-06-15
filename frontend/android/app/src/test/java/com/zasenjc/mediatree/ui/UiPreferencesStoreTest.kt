package com.zasenjc.mediatree.ui

import com.zasenjc.mediatree.data.DEFAULT_THEME_COLOR
import com.zasenjc.mediatree.data.HomeLayoutPreference
import com.zasenjc.mediatree.data.sanitizeHomeSortMode
import com.zasenjc.mediatree.data.sanitizeThemeColor
import com.zasenjc.mediatree.ui.screens.storageFileNameOrFallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UiPreferencesStoreTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun sanitizeThemeColorNormalizesValidHexColors() {
        assertEquals("#4F8EDB", sanitizeThemeColor("4f8edb"))
        assertEquals("#D16B86", sanitizeThemeColor("#d16b86"))
    }

    @Test
    fun sanitizeThemeColorFallsBackForInvalidInput() {
        assertEquals(DEFAULT_THEME_COLOR, sanitizeThemeColor(""))
        assertEquals(DEFAULT_THEME_COLOR, sanitizeThemeColor("#12"))
        assertEquals(DEFAULT_THEME_COLOR, sanitizeThemeColor("#GGGGGG"))
    }

    @Test
    fun uiPreferencesStorePersistsHomeSortModePreference() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/UiPreferencesStore.kt")
            .readText()

        assertTrue(source.contains("val homeSortModeFlow: Flow<String>"))
        assertTrue(source.contains("sanitizeHomeSortMode(prefs[HOME_SORT_MODE].orEmpty())"))
        assertTrue(source.contains("suspend fun setHomeSortModePreference(value: String)"))
        assertTrue(source.contains("prefs[HOME_SORT_MODE] = sanitizeHomeSortMode(value)"))
        assertTrue(source.contains("private val HOME_SORT_MODE = stringPreferencesKey(\"home_sort_mode\")"))
    }

    @Test
    fun homeLayoutPreferenceReplacesDirectoryFirstWithSourceFileName() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/UiPreferencesStore.kt")
            .readText()

        assertEquals("source_file_name", HomeLayoutPreference.SourceFileName.value)
        assertTrue(source.contains("SourceFileName(\"source_file_name\")"))
        assertTrue(source.contains("\"directory_first\" -> HomeLayoutPreference.SourceFileName"))
        assertTrue(source.contains("HomeLayoutPreference.SourceFileName.value -> HomeLayoutPreference.SourceFileName"))
        assertFalse(source.contains("DirectoryFirst(\"directory_first\")"))
    }

    @Test
    fun sanitizeHomeSortModeFallsBackForUnknownInput() {
        assertEquals("title_asc", sanitizeHomeSortMode("title_asc"))
        assertEquals("release_date_desc", sanitizeHomeSortMode(""))
        assertEquals("release_date_desc", sanitizeHomeSortMode("unknown"))
    }

    @Test
    fun storageFileNameOrFallbackKeepsProviderIdsReadable() {
        assertEquals("Episode 01.mkv", storageFileNameOrFallback("/shows/Season 1/Episode 01.mkv", "剧集 01"))
        assertEquals("剧集 01", storageFileNameOrFallback("f4a3b2c1d0", "剧集 01"))
    }
}

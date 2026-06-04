package com.zasenjc.mediatree.ui

import com.zasenjc.mediatree.data.DEFAULT_THEME_COLOR
import com.zasenjc.mediatree.data.sanitizeThemeColor
import org.junit.Assert.assertEquals
import org.junit.Test

class UiPreferencesStoreTest {
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
}

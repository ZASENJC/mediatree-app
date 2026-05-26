package com.zasenjc.mediatree.util

import org.junit.Assert.assertEquals
import org.junit.Test

class UrlUtilsTest {
    @Test
    fun normalizesHostWithoutScheme() {
        assertEquals("http://192.168.1.2:27580", UrlUtils.normalizeServerUrl("192.168.1.2:27580/"))
    }

    @Test
    fun preservesHttps() {
        assertEquals("https://media.example.com", UrlUtils.normalizeServerUrl("https://media.example.com///"))
    }

    @Test
    fun buildsApiBase() {
        assertEquals("http://host:80/api", UrlUtils.apiBase("host:80/"))
    }
}

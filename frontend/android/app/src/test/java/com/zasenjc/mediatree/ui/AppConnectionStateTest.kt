package com.zasenjc.mediatree.ui

import com.zasenjc.mediatree.data.ApiException
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.ui.navigation.topDestinations
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppConnectionStateTest {
    @Test
    fun startsOnSettingsWhenServerUrlIsMissing() {
        assertEquals(
            topDestinations.indexOfFirst { it.route == "settings" },
            initialTopDestinationPage(Session()),
        )
    }

    @Test
    fun startsOnHomeWhenServerUrlExists() {
        assertEquals(0, initialTopDestinationPage(Session(serverUrl = "http://server")))
    }

    @Test
    fun contentTabsDoNotLoadWithoutServerUrl() {
        assertFalse(shouldLoadRemoteContent(Session()))
        assertTrue(shouldLoadRemoteContent(Session(serverUrl = "http://server")))
    }

    @Test
    fun unauthorizedErrorRoutesToSettingsAndKeepsServerUrl() {
        val result = handleConnectionError(
            session = Session(serverUrl = "http://server", token = "expired"),
            throwable = ApiException(401, "Unauthorized"),
        )

        assertTrue(result.clearToken)
        assertEquals("settings", result.navigateRoute)
        assertEquals("http://server", result.session.serverUrl)
        assertEquals("", result.session.token)
    }

    @Test
    fun nonUnauthorizedErrorDoesNotRouteToSettings() {
        val result = handleConnectionError(
            session = Session(serverUrl = "http://server", token = "token"),
            throwable = IllegalStateException("boom"),
        )

        assertFalse(result.clearToken)
        assertEquals(null, result.navigateRoute)
        assertEquals("token", result.session.token)
    }
}

package com.zasenjc.mediatree.ui

import com.zasenjc.mediatree.data.ApiException
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.ServerProfile
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
    fun startsOnSettingsWhenMediaTreeHasOnlyServerUrl() {
        assertEquals(
            topDestinations.indexOfFirst { it.route == "settings" },
            initialTopDestinationPage(Session(serverUrl = "http://server")),
        )
    }

    @Test
    fun contentTabsDoNotLoadWithoutLoggedInBackend() {
        assertFalse(shouldLoadRemoteContent(Session()))
        assertFalse(shouldLoadRemoteContent(Session(serverUrl = "http://server")))
        assertTrue(shouldLoadRemoteContent(mediaBrowserSession(ProviderType.MediaTree, token = "", userId = "", authenticated = true)))
    }

    @Test
    fun jellyfinAndEmbyRequireTokenAndUserIdBeforeLoadingContent() {
        val jellyfinSavedOnly = mediaBrowserSession(ProviderType.Jellyfin, token = "", userId = "", authenticated = false)
        val embyTokenOnly = mediaBrowserSession(ProviderType.Emby, token = "token", userId = "", authenticated = false)
        val jellyfinLoggedIn = mediaBrowserSession(ProviderType.Jellyfin, token = "token", userId = "user", authenticated = true)

        assertFalse(shouldLoadRemoteContent(jellyfinSavedOnly))
        assertFalse(shouldLoadRemoteContent(embyTokenOnly))
        assertTrue(shouldLoadRemoteContent(jellyfinLoggedIn))
        assertEquals(topDestinations.indexOfFirst { it.route == "settings" }, initialTopDestinationPage(jellyfinSavedOnly))
        assertEquals(0, initialTopDestinationPage(jellyfinLoggedIn))
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

    private fun mediaBrowserSession(type: ProviderType, token: String, userId: String, authenticated: Boolean): Session {
        val profile = ServerProfile(
            id = type.name.lowercase(),
            type = type,
            serverUrl = "http://server",
            token = token,
            userId = userId,
            authenticated = authenticated,
        )
        return Session(profiles = listOf(profile), activeProfileId = profile.id)
    }
}

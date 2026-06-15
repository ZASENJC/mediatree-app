package com.zasenjc.mediatree.session

import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.ServerProfile
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.smbLibraryPath
import com.zasenjc.mediatree.data.webDavLibraryPath
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerProfileTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    @Test
    fun supportsPlannedProviderTypes() {
        assertEquals(
            setOf("MediaTree", "Jellyfin", "Emby", "M3U", "WebDAV", "SMB"),
            ProviderType.entries.map { it.name }.toSet(),
        )
    }

    @Test
    fun defaultProfileIsMediaTree() {
        val profile = ServerProfile(serverUrl = "http://server")

        assertEquals(ProviderType.MediaTree, profile.type)
        assertEquals("MediaTree", profile.name)
    }

    @Test
    fun backendProfileDisplayNameFallsBackToProviderType() {
        assertEquals("家庭片库", ServerProfile(name = "家庭片库").displayName)
        assertEquals("Jellyfin", ServerProfile(type = ProviderType.Jellyfin, name = "").displayName)
    }

    @Test
    fun legacySessionFieldsExposeActiveMediaTreeProfile() {
        val session = Session(
            profiles = listOf(
                ServerProfile(
                    id = "main",
                    type = ProviderType.MediaTree,
                    name = "Home",
                    serverUrl = "http://server",
                    token = "token",
                ),
            ),
            activeProfileId = "main",
            activeLibrary = "/media",
        )

        assertEquals("http://server", session.serverUrl)
        assertEquals("token", session.token)
        assertEquals("/media", session.activeLibrary)
    }

    @Test
    fun legacyServerUrlAndTokenBackfillMediaTreeProfile() {
        val session = Session(
            serverUrl = "http://server",
            token = "token",
            activeLibrary = "/media",
        )

        val profile = session.activeProfile
        assertEquals(ProviderType.MediaTree, profile?.type)
        assertEquals("http://server", profile?.serverUrl)
        assertEquals("token", profile?.token)
        assertTrue(session.resolvedProfiles.any { it.id == "mediatree-default" })
    }

    @Test
    fun profileAwareSessionSurvivesJsonRoundTrip() {
        val session = Session(
            profiles = listOf(
                ServerProfile(
                    id = "main",
                    type = ProviderType.MediaTree,
                    serverUrl = "http://server",
                    token = "token",
                ),
            ),
            activeProfileId = "main",
        )

        val decoded = json.decodeFromString<Session>(json.encodeToString(session))

        assertEquals(ProviderType.MediaTree, decoded.activeProfile?.type)
        assertEquals("http://server", decoded.serverUrl)
        assertEquals("token", decoded.token)
    }

    @Test
    fun mountedLibrariesExposeDedicatedProviderType() {
        val smbSession = Session(
            profiles = listOf(
                ServerProfile(id = "m3u", type = ProviderType.M3U, serverUrl = "https://m3u.example/playlist.m3u"),
            ),
            activeProfileId = "m3u",
            activeLibrary = smbLibraryPath("source-1"),
        )
        val webDavSession = Session(
            profiles = listOf(
                ServerProfile(id = "m3u", type = ProviderType.M3U, serverUrl = "https://m3u.example/playlist.m3u"),
            ),
            activeProfileId = "m3u",
            activeLibrary = webDavLibraryPath("source-2"),
        )

        assertEquals(ProviderType.SMB, smbSession.activeProviderType)
        assertEquals(ProviderType.WebDAV, webDavSession.activeProviderType)
    }
}

package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsConnectionsSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun settingsCombinesServerProfilesAndClientStorageSources() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertTrue(source.contains("private fun ConnectionsSection"))
        assertTrue(source.contains("serverProviderTypes"))
        assertTrue(source.contains("session.resolvedProfiles"))
        assertTrue(source.contains("state.clientStorageSources"))
        assertTrue(source.contains("DropdownMenu"))
        assertTrue(source.contains("ConnectionEditorDialog"))
        assertFalse(source.contains("SettingsSectionCard(title = \"客户端存储源\""))
    }

    @Test
    fun connectionEditorSupportsAllConfiguredConnectionTypes() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertTrue(source.contains("ConnectionEditorTarget.Server"))
        assertTrue(source.contains("ConnectionEditorTarget.WebDav"))
        assertTrue(source.contains("ConnectionEditorTarget.Smb"))
        assertTrue(source.contains("ServerConnectionDialog"))
        assertTrue(source.contains("WebDavConnectionDialog"))
        assertTrue(source.contains("SmbConnectionDialog"))
        assertTrue(source.contains("saveServerProfile"))
        assertTrue(source.contains("loginServerProfile"))
    }

    @Test
    fun serverConnectionEditorCanNameBackendProfiles() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertTrue(source.contains("profileName"))
        assertTrue(source.contains("label = { Text(\"媒体库名称\") }"))
        assertTrue(source.contains("saveServerProfile(profileId, providerType, serverUrl, profileName)"))
        assertTrue(source.contains("loginServerProfile(profileId, providerType, serverUrl, profileName, username, password)"))
        assertTrue(source.contains("title = profile.displayName()"))
        assertTrue(source.contains("library.profileName"))
    }
}

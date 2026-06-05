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
        assertTrue(source.contains("loginServerProfile"))
    }

    @Test
    fun serverConnectionEditorCanNameBackendProfiles() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertTrue(source.contains("profileName"))
        assertTrue(source.contains("label = { Text(\"媒体库名称\") }"))
        assertTrue(source.contains("loginServerProfile(profileId, providerType, serverUrl, profileName, username, password)"))
        assertTrue(source.contains("title = profile.displayName()"))
        assertTrue(source.contains("title = firstLibrary.profileName"))
    }

    @Test
    fun serverConnectionAddCreatesBlankProfileEditorAndEditPassesExistingProfile() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        val connectionsBlock = source
            .substringAfter("private fun ConnectionsSection(")
            .substringBefore("@Composable\nprivate fun ConnectionEditorDialog")

        assertTrue(connectionsBlock.contains("onAdd(ConnectionEditorTarget.Server(type = type))"))
        assertTrue(connectionsBlock.contains("onEdit(ConnectionEditorTarget.Server(type = profile.type, profile = profile))"))
    }

    @Test
    fun backendProfilesAreSavedOnlyAfterLoginSucceeds() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()
        val loginBlock = source
            .substringAfter("fun loginServerProfile(")
            .substringBefore("fun loadRoots(")

        assertFalse(source.contains("fun saveServerProfile("))
        assertFalse(loginBlock.contains("saveProfile("))
        assertTrue(loginBlock.contains("saveSession(normalized, \"\", type = providerType, name = profileName, profileId = profileId"))
        assertTrue(loginBlock.contains("saveSession(normalized, result.token, type = providerType, userId = result.userId, name = profileName, profileId = profileId"))
    }

    @Test
    fun serverConnectionDialogHasOnlyLoginPrimaryActionAndRedLogoutOnLeft() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()
        val dialogBlock = source
            .substringAfter("private fun ServerConnectionDialog(")
            .substringBefore("@Composable\nprivate fun WebDavConnectionDialog")

        assertTrue(dialogBlock.contains("confirmButton"))
        assertTrue(dialogBlock.contains("Text(\"登录\")"))
        assertFalse(dialogBlock.contains("Text(\"保存\")"))
        assertTrue(dialogBlock.contains("onLogout"))
        assertTrue(dialogBlock.contains("Text(\"登出\")"))
        assertTrue(dialogBlock.contains("MaterialTheme.colorScheme.error"))
    }

    @Test
    fun mediaTreeServerConnectionEditorShowsBackendGuideLink() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()
        val block = source
            .substringAfter("private fun ServerConnectionDialog(")
            .substringBefore("@Composable\nprivate fun WebDavConnectionDialog")

        assertTrue(source.contains("MEDIATREE_BACKEND_REPOSITORY_URL = \"https://github.com/ZASENJC/mediatree\""))
        assertTrue(block.contains("val uriHandler = LocalUriHandler.current"))
        assertTrue(block.contains("if (target.type == ProviderType.MediaTree)"))
        assertTrue(block.contains("MediaTree 配套后端，兼容性更高"))
        assertTrue(block.contains("uriHandler.openUri(MEDIATREE_BACKEND_REPOSITORY_URL)"))
    }
}

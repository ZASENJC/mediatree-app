package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsDisplaySelectionSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    private val settingsSource: String
        get() = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

    @Test
    fun displayPreferenceDoesNotRenderExplanatoryCopy() {
        val block = settingsSource
            .substringAfter("SettingsSectionCard(title = \"显示偏好\"")
            .substringBefore("ConnectionsSection(")

        assertFalse(block.contains("媒体流按剧集/电影显示单海报"))
        assertFalse(block.contains("目录优先直接显示媒体库或 SMB 的源文件夹结构"))
        assertFalse(block.contains("外观默认跟随系统"))
        assertFalse(block.contains("固定浅色或深色模式"))
    }

    @Test
    fun connectionsSectionDoesNotExposeEnableOrBrowseActions() {
        val block = settingsSource
            .substringAfter("private fun ConnectionsSection")
            .substringBefore("@Composable\nprivate fun ConnectionEditorDialog")

        assertFalse(block.contains("onActivateProfile"))
        assertFalse(block.contains("onOpenClientStorageSource"))
        assertFalse(block.contains("Text(\"启用\")"))
        assertFalse(block.contains("Text(\"浏览\")"))
        assertFalse(block.contains("Icons.Default.CheckCircle"))
        assertFalse(block.contains("已启用"))
    }

    @Test
    fun mediaLibraryDisplayUsesBackendLibrariesAndMountedSources() {
        val block = settingsSource
            .substringAfter("SettingsSectionCard(title = \"媒体库显示\"")
            .substringBefore("if (state.message")

        assertFalse(settingsSource.contains("private val libraryViews"))
        assertFalse(block.contains("全部媒体库"))
        assertFalse(block.contains("filterForLibraryView"))
        assertFalse(block.contains("profile.displayName()"))
        assertFalse(block.contains("selectServerProfile"))
        assertTrue(block.contains("state.backendLibraries"))
        assertTrue(block.contains("selectBackendLibrary"))
        assertTrue(block.contains("smbLibraryPath(source.id)"))
        assertTrue(block.contains("webDavLibraryPath(source.id)"))
        assertTrue(block.contains("session.activeProfileId == library.profileId"))
        assertTrue(block.contains("session.activeLibrary == library.root.path"))
    }

    @Test
    fun backendLibrariesAreLoadedOnlyFromProfilesWithUsableCredentials() {
        assertTrue(settingsSource.contains(".filter { it.canLoadMediaRoots() }"))
        assertTrue(settingsSource.contains("ProviderType.MediaTree -> serverUrl.isNotBlank()"))
        assertTrue(settingsSource.contains("ProviderType.Jellyfin, ProviderType.Emby ->"))
        assertTrue(settingsSource.contains("token.isNotBlank() && userId.isNotBlank()"))
    }

    @Test
    fun clientStorageConnectionsAreAlwaysEnabledAfterSave() {
        assertFalse(settingsSource.contains("EnabledSwitchRow("))
        assertFalse(settingsSource.contains("Switch("))
        assertTrue(settingsSource.contains("enabled = true"))
    }

    @Test
    fun playbackSettingsDoNotRenderThemeRow() {
        val block = settingsSource
            .substringAfter("SettingsSectionCard(title = \"播放\"")
            .substringBefore("SettingsSectionCard(title = \"关于\"")

        assertFalse(block.contains("title = \"主题\""))
        assertFalse(block.contains("Icons.Default.Palette"))
        assertFalse(block.contains("themeModePreference.labelText()"))
    }
}

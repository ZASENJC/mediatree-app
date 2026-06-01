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
    fun displayPreferenceUsesExpandableSettingRowsForThemeAndFullscreen() {
        val block = settingsSource
            .substringAfter("SettingsSectionCard(title = \"显示偏好\"")
            .substringBefore("ConnectionsSection(")

        assertTrue(block.contains("Text(\"深浅色模式\""))
        assertTrue(block.contains("PreferenceExpandableRow("))
        assertTrue(block.contains("title = \"首页布局\""))
        assertTrue(block.contains("title = \"主题模式\""))
        assertTrue(block.contains("title = \"播放全屏模式\""))
        assertTrue(block.contains("selectedLabel = state.themeModePreference.labelText()"))
        assertTrue(block.contains("selectedLabel = state.fullscreenModePreference.labelText()"))
        assertTrue(block.contains("FullscreenModePreference.Portrait"))
        assertTrue(block.contains("FullscreenModePreference.Landscape"))
        assertTrue(block.contains("FullscreenModePreference.Auto"))
        assertFalse(block.contains("Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth())"))
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
        assertTrue(block.contains("activeProfileId = session.activeProfileId"))
        assertTrue(block.contains("activeLibrary = session.activeLibrary"))
    }

    @Test
    fun backendLibraryDisplayGroupsLibrariesUnderBackendRows() {
        val block = settingsSource
            .substringAfter("SettingsSectionCard(title = \"媒体库显示\"")
            .substringBefore("state.clientStorageSources")

        assertTrue(block.contains("groupBy { it.profileId }"))
        assertTrue(block.contains("backendLibraryGroups"))
        assertTrue(block.contains("BackendLibrarySelectorRow("))
        assertTrue(settingsSource.contains("private fun BackendLibrarySelectorRow("))
        val selectorBlock = settingsSource
            .substringAfter("private fun BackendLibrarySelectorRow(")
            .substringBefore("@Composable\nprivate fun ConnectionsSection")

        assertFalse(selectorBlock.contains("DropdownMenu("))
        assertFalse(selectorBlock.contains("DropdownMenuItem("))
        assertTrue(selectorBlock.contains("AnimatedVisibility(visible = expanded)"))
        assertTrue(selectorBlock.contains("DesignSettingsRow("))
        assertTrue(selectorBlock.contains("Modifier.padding(start = 18.dp)"))
        assertTrue(settingsSource.contains("val selectedLibrary = libraries.firstOrNull"))
        assertTrue(settingsSource.contains("val backendSelected = selectedLibrary != null"))
        assertTrue(settingsSource.contains("if (backendSelected)"))
        assertTrue(settingsSource.contains("if (isSelected) Icon(Icons.Default.CheckCircle"))
        assertTrue(settingsSource.contains("onSelect(library.profileId, library.root.path)"))
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
        assertFalse(block.contains("播放全屏模式"))
    }
}

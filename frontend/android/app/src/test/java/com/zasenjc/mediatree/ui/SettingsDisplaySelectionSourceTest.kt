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

    private val appShellSource: String
        get() = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
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
    fun displayPreferenceUsesExpandableSettingRowsAndThemeColorPicker() {
        val block = settingsSource
            .substringAfter("SettingsSectionCard(title = \"显示偏好\"")
            .substringBefore("ConnectionsSection(")

        assertFalse(block.contains("Text(\"深浅色模式\""))
        assertTrue(block.contains("PreferenceExpandableRow("))
        assertTrue(block.contains("title = \"首页布局\""))
        assertTrue(block.contains("title = \"主题模式\""))
        assertTrue(block.contains("selectedLabel = state.themeModePreference.labelText()"))
        assertTrue(block.contains("ThemeColorPreferenceRow("))
        assertTrue(block.contains("title = \"主题色\""))
        assertTrue(block.contains("themeColorPreference = state.themeColorPreference"))
        assertTrue(block.contains("onThemeColorChange = vm::setThemeColorPreference"))
        assertFalse(settingsSource.contains("支持 RRGGBB"))
        assertFalse(block.contains("title = \"播放全屏模式\""))
        assertFalse(block.contains("selectedLabel = state.fullscreenModePreference.labelText()"))
        assertFalse(block.contains("Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth())"))
    }

    @Test
    fun themeColorPresetsUseMd3RecommendationsAndExcludeRemovedDefault() {
        val source = settingsSource
        val block = source
            .substringAfter("private val themeColorPresets = listOf(")
            .substringBefore(")")

        assertTrue(block.contains("DEFAULT_THEME_COLOR"))
        assertTrue(block.contains("#6750A4"))
        assertTrue(block.contains("#006C4C"))
        assertTrue(block.contains("#006A6A"))
        assertTrue(block.contains("#825500"))
        assertFalse(block.contains("#DDEFD1"))
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
            .substringBefore("SettingsSectionCard(title = \"缓存\"")

        assertFalse(settingsSource.contains("private val libraryViews"))
        assertFalse(block.contains("全部媒体库"))
        assertFalse(block.contains("filterForLibraryView"))
        assertTrue(block.contains("profile.displayName()"))
        assertFalse(block.contains("selectServerProfile"))
        assertTrue(block.contains("state.backendLibraries"))
        assertTrue(block.contains("selectBackendLibrary"))
        assertTrue(block.contains("ProviderType.M3U"))
        assertTrue(block.contains("M3U 直播"))
        assertTrue(block.contains("smbLibraryPath(source.id)"))
        assertTrue(block.contains("webDavLibraryPath(source.id)"))
        assertTrue(block.contains("activeProfileId = session.activeProfileId"))
        assertTrue(block.contains("activeLibrary = session.activeLibrary"))
        assertFalse(block.contains("立即扫描媒体库"))
        assertFalse(block.contains("vm.scan("))
        assertFalse(block.contains("state.scanning"))
        assertFalse(block.contains("Icons.Default.Refresh"))
    }

    @Test
    fun settingsDoesNotExposeManualBackendScanAction() {
        assertFalse(settingsSource.contains("fun scan(activeLibrary"))
        assertFalse(settingsSource.contains("mediaProviderFor(_state.value.providerType).scan"))
        assertFalse(settingsSource.contains("扫描任务已触发"))
    }

    @Test
    fun settingsMessagesUseSharedErrorChannelInsteadOfInlineRows() {
        assertFalse(settingsSource.contains("if (state.message.isNotBlank())"))
        assertFalse(settingsSource.contains("text = state.message"))
        assertTrue(settingsSource.contains("LaunchedEffect(active, state.message)"))
        assertTrue(settingsSource.contains("onError(ApiException(0, message))"))
        assertTrue(settingsSource.contains("vm.consumeMessage()"))
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
        assertTrue(settingsSource.contains("ProviderType.MediaTree -> serverUrl.isNotBlank() && authenticated"))
        assertTrue(settingsSource.contains("ProviderType.Jellyfin, ProviderType.Emby ->"))
        assertTrue(settingsSource.contains("authenticated && token.isNotBlank() && userId.isNotBlank()"))
    }

    @Test
    fun backendLibrarySwitchTriggersBackendScanImplicitly() {
        val block = settingsSource
            .substringAfter("fun selectBackendLibrary(")
            .substringBefore("fun login()")

        assertTrue(block.contains("container.sessionStore.activateProfile(profileId)"))
        assertTrue(block.contains("container.sessionStore.setActiveLibrary(path)"))
        assertTrue(block.contains("container.mediaProviderFor(profile.providerType).scan(path)"))
    }

    @Test
    fun clientStorageConnectionsAreAlwaysEnabledAfterSave() {
        assertFalse(settingsSource.contains("EnabledSwitchRow("))
        assertFalse(settingsSource.contains("Switch("))
        assertTrue(settingsSource.contains("enabled = true"))
    }

    @Test
    fun playbackSettingsMoveFullscreenModeAndRemoveDefaultSurface() {
        val block = settingsSource
            .substringAfter("SettingsSectionCard(title = \"播放\"")
            .substringBefore("SettingsSectionCard(title = \"关于\"")

        assertFalse(block.contains("title = \"主题\""))
        assertFalse(block.contains("Icons.Default.Palette"))
        assertFalse(block.contains("themeModePreference.labelText()"))
        assertFalse(block.contains("title = \"默认画面\""))
        assertFalse(block.contains("subtitle = \"原生系统\""))
        assertTrue(block.contains("title = \"播放全屏模式\""))
        assertTrue(block.contains("selectedLabel = state.fullscreenModePreference.labelText()"))
        assertTrue(block.contains("FullscreenModePreference.Portrait"))
        assertTrue(block.contains("FullscreenModePreference.Landscape"))
        assertTrue(block.contains("FullscreenModePreference.Auto"))
    }

    @Test
    fun aboutSectionUsesGithubUpdateStateAndRepositoryLinks() {
        val block = settingsSource
            .substringAfter("SettingsSectionCard(title = \"关于\"")
            .substringBefore("}\n        }\n    }\n    editingConnection")

        assertTrue(settingsSource.contains("ReleaseUpdateState"))
        assertTrue(settingsSource.contains("container.releaseUpdateChecker.state.collectAsStateWithLifecycle()"))
        assertTrue(settingsSource.contains("BuildConfig.VERSION_NAME"))
        assertTrue(settingsSource.contains("ReleaseUpdateChecker.REPOSITORY_URL"))
        assertTrue(block.contains("title = \"版本\""))
        assertTrue(block.contains("releaseUpdateState"))
        assertTrue(block.contains("onClick = { releaseNotesDialogVisible = true }"))
        assertTrue(block.contains("title = \"关于 mediatree\""))
        assertTrue(block.contains("openUri(ReleaseUpdateChecker.REPOSITORY_URL)"))
    }

    @Test
    fun updateAvailableShowsRedDotsInVersionRowAndSettingsBottomTab() {
        val aboutBlock = settingsSource
            .substringAfter("SettingsSectionCard(title = \"关于\"")
            .substringBefore("}\n        }\n    }\n    editingConnection")
        val bottomBarBlock = appShellSource
            .substringAfter("private fun DesignBottomNavigationBar")
            .substringBefore("private fun detailMovieIdFromUri")

        assertTrue(settingsSource.contains("fun UpdateAvailableDot("))
        assertTrue(aboutBlock.contains("val updateAvailable = releaseUpdateState is ReleaseUpdateState.Available"))
        assertTrue(aboutBlock.contains("if (updateAvailable) UpdateAvailableDot("))
        assertTrue(appShellSource.contains("val settingsBadgeVisible = releaseUpdateState is ReleaseUpdateState.Available"))
        assertTrue(appShellSource.contains("container.releaseUpdateChecker.state.collectAsStateWithLifecycle()"))
        assertTrue(appShellSource.contains("settingsBadgeVisible = settingsBadgeVisible"))
        assertTrue(bottomBarBlock.contains("showUpdateBadge = settingsBadgeVisible && item.route == \"settings\""))
        assertTrue(bottomBarBlock.contains("if (showUpdateBadge) UpdateAvailableDot("))
    }

    @Test
    fun versionRowOpensFixedHeightScrollableReleaseNotesDialog() {
        val aboutBlock = settingsSource
            .substringAfter("SettingsSectionCard(title = \"关于\"")
            .substringBefore("}\n        }\n    }\n    editingConnection")
        val dialogBlock = settingsSource
            .substringAfter("private fun ReleaseNotesDialog(")
            .substringBefore("@Composable\nfun UpdateAvailableDot")

        assertTrue(settingsSource.contains("var releaseNotesDialogVisible by remember"))
        assertTrue(aboutBlock.contains("onClick = { releaseNotesDialogVisible = true }"))
        assertFalse(aboutBlock.contains("onClick = update?.let { { uriHandler.openUri(update.downloadUrl) } }"))
        assertTrue(settingsSource.contains("ReleaseNotesDialog("))
        assertTrue(dialogBlock.contains("AlertDialog("))
        assertTrue(dialogBlock.contains(".height(320.dp)"))
        assertTrue(dialogBlock.contains("verticalScroll(rememberScrollState())"))
        assertTrue(dialogBlock.contains("releaseUpdateState.releaseNotesForDialog()"))
        assertTrue(dialogBlock.contains("Text(\"取消\")"))
        assertTrue(dialogBlock.contains("Text(\"前往下载\")"))
        assertTrue(dialogBlock.contains("uriHandler.openUri(downloadUrl)"))
        assertTrue(settingsSource.contains("private fun ReleaseUpdateState.releaseNotesForDialog()"))
        assertTrue(settingsSource.contains("is ReleaseUpdateState.Available -> releaseNotes"))
        assertTrue(settingsSource.contains("is ReleaseUpdateState.Current -> releaseNotes"))
    }
}

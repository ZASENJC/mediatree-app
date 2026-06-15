package com.zasenjc.mediatree.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zasenjc.mediatree.BuildConfig
import com.zasenjc.mediatree.data.ApiException
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.ClientStorageAuthType
import com.zasenjc.mediatree.data.ClientStorageSource
import com.zasenjc.mediatree.data.ClientStorageType
import com.zasenjc.mediatree.data.DEFAULT_THEME_COLOR
import com.zasenjc.mediatree.data.FullscreenModePreference
import com.zasenjc.mediatree.data.HomeLayoutPreference
import com.zasenjc.mediatree.data.LoginResponseDto
import com.zasenjc.mediatree.data.MediaProvider
import com.zasenjc.mediatree.data.MediaRootDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.ReleaseUpdateChecker
import com.zasenjc.mediatree.data.ReleaseUpdateState
import com.zasenjc.mediatree.data.ServerProfile
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.ThemeModePreference
import com.zasenjc.mediatree.data.sanitizeThemeColor
import com.zasenjc.mediatree.data.smbLibraryPath
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.data.webDavLibraryPath
import com.zasenjc.mediatree.ui.components.DesignFilterChip
import com.zasenjc.mediatree.ui.components.DesignSectionCard
import com.zasenjc.mediatree.ui.components.DesignSettingsRow
import com.zasenjc.mediatree.ui.components.DesignTopAppBar
import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.Color

private val serverProviderTypes = listOf(ProviderType.MediaTree, ProviderType.Jellyfin, ProviderType.Emby)
private const val MEDIATREE_BACKEND_REPOSITORY_URL = "https://github.com/ZASENJC/mediatree"

private fun ProviderType.labelText(): String = when (this) {
    ProviderType.MediaTree -> "MediaTree"
    ProviderType.Jellyfin -> "Jellyfin"
    ProviderType.Emby -> "Emby"
    ProviderType.WebDAV -> "WebDAV"
    ProviderType.SMB -> "SMB"
}

private fun ClientStorageType.labelText(): String = when (this) {
    ClientStorageType.WebDAV -> "WebDAV"
    ClientStorageType.SMB -> "SMB"
}

private fun ProviderType.connectionIcon(): ImageVector = when (this) {
    ProviderType.MediaTree -> Icons.Default.Dns
    ProviderType.Jellyfin -> Icons.Default.Movie
    ProviderType.Emby -> Icons.Default.PlayArrow
    ProviderType.WebDAV -> Icons.Default.Storage
    ProviderType.SMB -> Icons.Default.Router
}

private fun ClientStorageType.connectionIcon(): ImageVector = when (this) {
    ClientStorageType.WebDAV -> Icons.Default.Storage
    ClientStorageType.SMB -> Icons.Default.Router
}

private val themeColorPresets = listOf(
    DEFAULT_THEME_COLOR,
    "#6750A4",
    "#006C4C",
    "#006A6A",
    "#825500",
    "#B3261E",
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    data class BackendLibraryItem(
        val profileId: String,
        val profileName: String,
        val providerType: ProviderType,
        val root: MediaRootDto,
    )

    data class UiState(
        val serverInput: String = "",
        val providerType: ProviderType = ProviderType.MediaTree,
        val username: String = "",
        val password: String = "",
        val roots: List<MediaRootDto> = emptyList(),
        val backendLibraries: List<BackendLibraryItem> = emptyList(),
        val message: String = "",
        val error: String? = null,
        val clientStorageSources: List<ClientStorageSource> = emptyList(),
        val webDavName: String = "WebDAV",
        val webDavUrl: String = "",
        val webDavUsername: String = "",
        val webDavPassword: String = "",
        val webDavAuthType: ClientStorageAuthType = ClientStorageAuthType.Basic,
        val webDavEnabled: Boolean = true,
        val smbName: String = "SMB",
        val smbAddress: String = "smb://192.168.1.10",
        val smbSharePath: String = "/Media",
        val smbUsername: String = "",
        val smbPassword: String = "",
        val homeLayoutPreference: HomeLayoutPreference = HomeLayoutPreference.MediaFeed,
        val themeModePreference: ThemeModePreference = ThemeModePreference.System,
        val themeColorPreference: String = DEFAULT_THEME_COLOR,
        val fullscreenModePreference: FullscreenModePreference = FullscreenModePreference.Landscape,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.clientStorageRepository.sourcesFlow.collect { sources ->
                _state.update { it.copy(clientStorageSources = sources) }
            }
        }
        viewModelScope.launch {
            container.uiPreferencesStore.homeLayoutFlow.collect { preference ->
                _state.update { it.copy(homeLayoutPreference = preference) }
            }
        }
        viewModelScope.launch {
            container.uiPreferencesStore.themeModeFlow.collect { preference ->
                _state.update { it.copy(themeModePreference = preference) }
            }
        }
        viewModelScope.launch {
            container.uiPreferencesStore.themeColorFlow.collect { preference ->
                _state.update { it.copy(themeColorPreference = preference) }
            }
        }
        viewModelScope.launch {
            container.uiPreferencesStore.fullscreenModeFlow.collect { preference ->
                _state.update { it.copy(fullscreenModePreference = preference) }
            }
        }
    }

    fun initServerInput(serverUrl: String) {
        if (_state.value.serverInput.isBlank() || _state.value.serverInput != serverUrl) {
            _state.update { it.copy(serverInput = serverUrl) }
        }
    }

    fun initProviderType(type: ProviderType) {
        if (_state.value.providerType != type) {
            _state.update { it.copy(providerType = type) }
        }
    }

    fun onServerInputChange(value: String) = _state.update { it.copy(serverInput = value, message = "") }
    fun onProviderTypeChange(value: ProviderType) = _state.update { it.copy(providerType = value, message = "") }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value) }
    fun onWebDavNameChange(value: String) = _state.update { it.copy(webDavName = value) }
    fun onWebDavUrlChange(value: String) = _state.update { it.copy(webDavUrl = value) }
    fun onWebDavUsernameChange(value: String) = _state.update { it.copy(webDavUsername = value) }
    fun onWebDavPasswordChange(value: String) = _state.update { it.copy(webDavPassword = value) }
    fun onWebDavAuthTypeChange(value: ClientStorageAuthType) = _state.update { it.copy(webDavAuthType = value) }
    fun onSmbNameChange(value: String) = _state.update { it.copy(smbName = value) }
    fun onSmbAddressChange(value: String) = _state.update { it.copy(smbAddress = value) }
    fun onSmbSharePathChange(value: String) = _state.update { it.copy(smbSharePath = value) }
    fun onSmbUsernameChange(value: String) = _state.update { it.copy(smbUsername = value) }
    fun onSmbPasswordChange(value: String) = _state.update { it.copy(smbPassword = value) }

    fun setHomeLayoutPreference(value: HomeLayoutPreference) {
        viewModelScope.launch { container.uiPreferencesStore.setHomeLayoutPreference(value) }
    }

    fun setThemeModePreference(value: ThemeModePreference) {
        viewModelScope.launch { container.uiPreferencesStore.setThemeModePreference(value) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = "") }
    }

    fun setThemeColorPreference(value: String) {
        viewModelScope.launch { container.uiPreferencesStore.setThemeColorPreference(value) }
    }

    fun setFullscreenModePreference(value: FullscreenModePreference) {
        viewModelScope.launch { container.uiPreferencesStore.setFullscreenModePreference(value) }
    }

    fun loginServerProfile(
        profileId: String?,
        providerType: ProviderType,
        serverUrl: String,
        profileName: String,
        username: String,
        password: String,
    ) {
        viewModelScope.launch {
            val normalized = UrlUtils.normalizeServerUrl(serverUrl)
            _state.update { it.copy(message = "", error = null) }
            try {
                val provider = container.mediaProviderFor(providerType)
                val result = authenticateServer(provider, providerType, normalized, username, password)
                if (result.ok) {
                    container.sessionStore.saveSession(normalized, result.token, type = providerType, userId = result.userId, name = profileName, profileId = profileId)
                    _state.update { it.copy(message = "${providerType.labelText()} 登录成功") }
                } else {
                    _state.update { it.copy(error = "${providerType.labelText()} 登录失败") }
                }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message ?: "${providerType.labelText()} 登录失败") }
            }
        }
    }

    fun logoutServerProfile(profileId: String?) {
        if (profileId == null) return
        viewModelScope.launch {
            container.sessionStore.removeProfile(profileId)
            _state.update { it.copy(message = "后端已登出", error = null) }
        }
    }

    fun loadRoots(session: Session) {
        viewModelScope.launch {
            val libraries = mutableListOf<BackendLibraryItem>()
            var activeRoots = emptyList<MediaRootDto>()
            var firstError: Throwable? = null
            val activeProfileId = session.activeProfileId
            session.resolvedProfiles
                .filter { it.canLoadMediaRoots() }
                .forEach { profile ->
                    runCatching {
                        container.mediaProviderFor(profile.type).mediaRoots(profile).items
                    }
                        .onSuccess { roots ->
                            libraries += roots.map { root ->
                                BackendLibraryItem(
                                    profileId = profile.id,
                                    profileName = profile.displayName,
                                    providerType = profile.type,
                                    root = root,
                                )
                            }
                            if (profile.id == activeProfileId) activeRoots = roots
                        }
                        .onFailure { throwable ->
                            if (firstError == null) firstError = throwable
                        }
                }
            activeRoots = libraries
                .filter { it.profileId == activeProfileId }
                .map { it.root }
                .ifEmpty { activeRoots }
            _state.update {
                it.copy(
                    roots = activeRoots,
                    backendLibraries = libraries,
                    error = firstError?.message,
                )
            }
        }
    }

    fun clearBackendLibraries() {
        _state.update { it.copy(roots = emptyList(), backendLibraries = emptyList(), error = null) }
    }

    fun selectBackendLibrary(profileId: String, path: String) {
        viewModelScope.launch {
            container.sessionStore.activateProfile(profileId)
            container.sessionStore.setActiveLibrary(path)
            val profile = _state.value.backendLibraries.firstOrNull { it.profileId == profileId } ?: return@launch
            kotlin.runCatching { container.mediaProviderFor(profile.providerType).scan(path) }
                .onFailure { throwable -> _state.update { it.copy(error = throwable.message) } }
        }
    }

    fun login() {
        viewModelScope.launch {
            val state = _state.value
            val normalized = UrlUtils.normalizeServerUrl(state.serverInput)
            _state.update { it.copy(message = "", error = null) }
            try {
                val provider = container.mediaProviderFor(state.providerType)
                val result = authenticateServer(provider, state.providerType, normalized, state.username, state.password)
                if (result.ok) {
                    container.sessionStore.saveSession(normalized, result.token, type = state.providerType, userId = result.userId)
                    _state.update { it.copy(message = "登录成功") }
                } else {
                    _state.update { it.copy(error = "登录失败") }
                }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message ?: "登录失败") }
            }
        }
    }

    private suspend fun authenticateServer(
        provider: MediaProvider,
        providerType: ProviderType,
        normalizedServerUrl: String,
        username: String,
        password: String,
    ): LoginResponseDto {
        val status = provider.authStatus(normalizedServerUrl)
        if (!status.needAuth) return LoginResponseDto(ok = true)
        return if (providerType == ProviderType.MediaTree && !status.authConfigured) {
            provider.setupAuth(normalizedServerUrl, username, password)
        } else {
            provider.login(normalizedServerUrl, username, password)
        }
    }

    fun logout() {
        viewModelScope.launch { container.sessionStore.logout() }
    }

    fun setActiveLibrary(path: String) {
        viewModelScope.launch { container.sessionStore.setActiveLibrary(path) }
    }

    fun saveWebDavSource() {
        val state = _state.value
        saveWebDavSource(
            id = null,
            name = state.webDavName,
            url = state.webDavUrl,
            username = state.webDavUsername,
            password = state.webDavPassword,
            authType = state.webDavAuthType,
            enabled = true,
        )
    }

    fun saveWebDavSource(
        id: String?,
        name: String,
        url: String,
        username: String,
        password: String,
        authType: ClientStorageAuthType,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            kotlin.runCatching {
                container.clientStorageRepository.saveWebDav(
                    id = id ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    url = url,
                    username = username,
                    password = password,
                    authType = authType,
                    enabled = enabled,
                )
            }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            message = "WebDAV 存储源已保存",
                            error = null,
                            webDavPassword = "",
                        )
                    }
                }
                .onFailure { throwable -> _state.update { it.copy(error = throwable.message) } }
        }
    }

    fun saveSmbSource() {
        val state = _state.value
        saveSmbSource(
            id = null,
            name = state.smbName,
            server = state.smbAddress,
            sharePath = state.smbSharePath,
            username = state.smbUsername,
            password = state.smbPassword,
            enabled = true,
        )
    }

    fun saveSmbSource(
        id: String?,
        name: String,
        server: String,
        sharePath: String,
        username: String,
        password: String,
        enabled: Boolean,
    ) {
        viewModelScope.launch {
            kotlin.runCatching {
                container.clientStorageRepository.saveSmb(
                    id = id ?: java.util.UUID.randomUUID().toString(),
                    name = name,
                    server = server,
                    sharePath = sharePath,
                    username = username,
                    password = password,
                    enabled = enabled,
                )
            }
                .onSuccess {
                    _state.update { current ->
                        current.copy(
                            message = "SMB 存储源已保存",
                            error = null,
                            smbPassword = "",
                        )
                    }
                }
                .onFailure { throwable -> _state.update { it.copy(error = throwable.message) } }
        }
    }

    fun deleteClientStorageSource(sourceId: String) {
        viewModelScope.launch {
            container.clientStorageRepository.delete(sourceId)
            _state.update { it.copy(message = "存储源已删除", error = null) }
        }
    }

    fun clearMountedVideoThumbnailCache() {
        viewModelScope.launch {
            kotlin.runCatching {
                container.mountedVideoThumbnailCache.clear()
            }
                .onSuccess { _state.update { it.copy(message = "缩略图缓存已清理", error = null) } }
                .onFailure { throwable -> _state.update { it.copy(error = throwable.message) } }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    session: Session,
    onError: (Throwable) -> Unit,
    active: Boolean = true,
) {
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { SettingsViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    val releaseUpdateState by container.releaseUpdateChecker.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    var editingConnection by remember { mutableStateOf<ConnectionEditorTarget?>(null) }
    var releaseNotesDialogVisible by remember { mutableStateOf(false) }

    LaunchedEffect(active, session.serverUrl, session.activeProviderType, session.resolvedProfiles) {
        if (!active) return@LaunchedEffect
        vm.initServerInput(session.serverUrl)
        vm.initProviderType(session.activeProviderType)
        if (session.resolvedProfiles.any { it.canLoadMediaRoots() }) {
            vm.loadRoots(session)
        } else {
            vm.clearBackendLibraries()
        }
    }

    LaunchedEffect(active, state.error) {
        if (!active) return@LaunchedEffect
        state.error?.let { onError(ApiException(0, it)) }
    }

    LaunchedEffect(active, state.message) {
        if (!active) return@LaunchedEffect
        val message = state.message
        if (message.isNotBlank()) {
            onError(ApiException(0, message))
            vm.consumeMessage()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            DesignTopAppBar(title = "设置")
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsSectionCard(title = "显示偏好", icon = Icons.Default.Visibility) {
                    PreferenceExpandableRow(
                        title = "首页布局",
                        selectedLabel = state.homeLayoutPreference.labelText(),
                        icon = Icons.Default.Folder,
                        options = listOf(
                            HomeLayoutPreference.MediaFeed to "媒体流",
                            HomeLayoutPreference.DirectoryFirst to "目录优先",
                        ),
                        selected = state.homeLayoutPreference,
                        onSelect = vm::setHomeLayoutPreference,
                    )
                    PreferenceExpandableRow(
                        title = "主题模式",
                        selectedLabel = state.themeModePreference.labelText(),
                        icon = Icons.Default.Visibility,
                        options = listOf(
                            ThemeModePreference.System to "跟随系统",
                            ThemeModePreference.Light to "浅色模式",
                            ThemeModePreference.Dark to "深色模式",
                        ),
                        selected = state.themeModePreference,
                        onSelect = vm::setThemeModePreference,
                    )
                    ThemeColorPreferenceRow(
                        title = "主题色",
                        themeColorPreference = state.themeColorPreference,
                        onThemeColorChange = vm::setThemeColorPreference,
                    )
                }
            }
            item {
                ConnectionsSection(
                    session = session,
                    sources = state.clientStorageSources,
                    onAdd = { editingConnection = it },
                    onEdit = { editingConnection = it },
                    onDeleteClientStorageSource = vm::deleteClientStorageSource,
                )
            }
            item {
                SettingsSectionCard(title = "媒体库显示", icon = Icons.Default.Folder) {
                    val backendLibraryGroups = state.backendLibraries.groupBy { it.profileId }
                    backendLibraryGroups.forEach { (_, libraries) ->
                        BackendLibrarySelectorRow(
                            libraries = libraries,
                            activeProfileId = session.activeProfileId,
                            activeLibrary = session.activeLibrary,
                            onSelect = vm::selectBackendLibrary,
                        )
                    }
                    state.clientStorageSources
                        .filter { it.enabled }
                        .forEach { source ->
                            val libraryPath = when (source.type) {
                                ClientStorageType.WebDAV -> webDavLibraryPath(source.id)
                                ClientStorageType.SMB -> smbLibraryPath(source.id)
                            }
                            DesignSettingsRow(
                                title = source.name,
                                subtitle = "${source.type.labelText()} 挂载源 · ${source.storageSummary()}",
                                icon = source.type.connectionIcon(),
                                trailing = {
                                    if (session.activeLibrary == libraryPath) Icon(Icons.Default.CheckCircle, contentDescription = "当前")
                                },
                                onClick = { vm.setActiveLibrary(libraryPath) },
                            )
                        }
                }
            }
            item {
                SettingsSectionCard(title = "缓存", icon = Icons.Default.Cached) {
                    DesignSettingsRow(
                        title = "视频缩略图缓存",
                        subtitle = "清理 SMB / WebDAV 浏览页生成的缩略图",
                        icon = Icons.Default.Cached,
                        trailing = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = vm::clearMountedVideoThumbnailCache,
                    )
                }
            }
            item {
                SettingsSectionCard(title = "播放", icon = Icons.Default.PlayArrow) {
                    PreferenceExpandableRow(
                        title = "播放全屏模式",
                        selectedLabel = state.fullscreenModePreference.labelText(),
                        icon = Icons.Default.PlayArrow,
                        options = listOf(
                            FullscreenModePreference.Portrait to "竖向",
                            FullscreenModePreference.Landscape to "横向",
                            FullscreenModePreference.Auto to "自适应",
                        ),
                        selected = state.fullscreenModePreference,
                        onSelect = vm::setFullscreenModePreference,
                    )
                    DesignSettingsRow(
                        title = "字幕语言",
                        subtitle = "跟随系统",
                        icon = Icons.Default.Visibility,
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    )
                }
            }
            item {
                SettingsSectionCard(title = "关于", icon = Icons.Default.Info) {
                    val update = releaseUpdateState as? ReleaseUpdateState.Available
                    val updateAvailable = releaseUpdateState is ReleaseUpdateState.Available
                    DesignSettingsRow(
                        title = "版本",
                        subtitle = releaseUpdateState.versionSubtitle(BuildConfig.VERSION_NAME),
                        icon = Icons.Default.Info,
                        trailing = {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                if (updateAvailable) UpdateAvailableDot()
                                if (update != null) Icon(Icons.Default.ChevronRight, contentDescription = null)
                            }
                        },
                        onClick = { releaseNotesDialogVisible = true },
                    )
                    DesignSettingsRow(
                        title = "关于 mediatree",
                        subtitle = "Android client",
                        icon = Icons.Default.Info,
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                        onClick = { uriHandler.openUri(ReleaseUpdateChecker.REPOSITORY_URL) },
                    )
                }
            }
        }
    }
    editingConnection?.let { target ->
        ConnectionEditorDialog(
            target = target,
            onDismiss = { editingConnection = null },
            onLoginServer = { profileId, providerType, serverUrl, profileName, username, password ->
                vm.loginServerProfile(profileId, providerType, serverUrl, profileName, username, password)
                editingConnection = null
            },
            onLogoutServer = { profileId ->
                vm.logoutServerProfile(profileId)
                editingConnection = null
            },
            onSaveWebDav = { id, name, url, username, password, authType, enabled ->
                vm.saveWebDavSource(id, name, url, username, password, authType, enabled)
                editingConnection = null
            },
            onSaveSmb = { id, name, server, sharePath, username, password, enabled ->
                vm.saveSmbSource(id, name, server, sharePath, username, password, enabled)
                editingConnection = null
            },
        )
    }
    if (releaseNotesDialogVisible) {
        ReleaseNotesDialog(
            releaseUpdateState = releaseUpdateState,
            fallbackVersion = BuildConfig.VERSION_NAME,
            onDismiss = { releaseNotesDialogVisible = false },
        )
    }
}

@Composable
private fun BackendLibrarySelectorRow(
    libraries: List<SettingsViewModel.BackendLibraryItem>,
    activeProfileId: String,
    activeLibrary: String,
    onSelect: (String, String) -> Unit,
) {
    val firstLibrary = libraries.firstOrNull() ?: return
    var expanded by remember(firstLibrary.profileId, libraries) { mutableStateOf(false) }
    val selectedLibrary = libraries.firstOrNull {
        activeProfileId == it.profileId && activeLibrary == it.root.path
    }
    val backendSelected = selectedLibrary != null
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DesignSettingsRow(
            title = firstLibrary.profileName,
            subtitle = selectedLibrary?.let { library ->
                "${library.root.displayName()} · ${library.root.movieCount} 项"
            } ?: "${firstLibrary.providerType.labelText()} 后端 · ${libraries.size} 个媒体库",
            icon = firstLibrary.providerType.connectionIcon(),
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (backendSelected) Icon(Icons.Default.CheckCircle, contentDescription = "当前")
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            },
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                libraries.forEach { library ->
                    val isSelected = activeProfileId == library.profileId && activeLibrary == library.root.path
                    DesignSettingsRow(
                        title = library.root.displayName(),
                        subtitle = "${firstLibrary.providerType.labelText()} 媒体库 · ${library.root.movieCount} 项",
                        icon = library.providerType.connectionIcon(),
                        modifier = Modifier.padding(start = 18.dp),
                        trailing = {
                            if (isSelected) Icon(Icons.Default.CheckCircle, contentDescription = "当前")
                        },
                        onClick = {
                            expanded = false
                            onSelect(library.profileId, library.root.path)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectionsSection(
    session: Session,
    sources: List<ClientStorageSource>,
    onAdd: (ConnectionEditorTarget) -> Unit,
    onEdit: (ConnectionEditorTarget) -> Unit,
    onDeleteClientStorageSource: (String) -> Unit,
) {
    var addMenuExpanded by remember { mutableStateOf(false) }
    SettingsSectionCard(
        title = "后端连接",
        icon = Icons.Default.Dns,
        actions = {
            Box {
                IconButton(onClick = { addMenuExpanded = true }) {
                    Icon(Icons.Default.Add, contentDescription = "添加连接")
                }
                DropdownMenu(expanded = addMenuExpanded, onDismissRequest = { addMenuExpanded = false }) {
                    serverProviderTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text("${type.labelText()} 后端") },
                            leadingIcon = { Icon(type.connectionIcon(), contentDescription = null) },
                            onClick = {
                                addMenuExpanded = false
                                onAdd(ConnectionEditorTarget.Server(type = type))
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("WebDAV 存储源") },
                        leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                        onClick = {
                            addMenuExpanded = false
                            onAdd(ConnectionEditorTarget.WebDav())
                        },
                    )
                    DropdownMenuItem(
                        text = { Text("SMB 存储源") },
                        leadingIcon = { Icon(Icons.Default.Router, contentDescription = null) },
                        onClick = {
                            addMenuExpanded = false
                            onAdd(ConnectionEditorTarget.Smb())
                        },
                    )
                }
            }
        },
    ) {
        val profiles = session.resolvedProfiles.filter { it.type in serverProviderTypes }
        if (profiles.isEmpty() && sources.isEmpty()) {
            Text(
                text = "暂无连接，点击右上角添加。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        profiles.forEach { profile ->
            DesignSettingsRow(
                title = profile.displayName(),
                subtitle = "${profile.type.labelText()} 后端 · ${profile.connectionStatus(session)}",
                icon = profile.type.connectionIcon(),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Icon(Icons.Default.ChevronRight, contentDescription = null)
                    }
                },
                onClick = { onEdit(ConnectionEditorTarget.Server(type = profile.type, profile = profile)) },
            )
        }
        sources.forEach { source ->
            DesignSettingsRow(
                title = source.name,
                subtitle = "${source.type.labelText()} · ${source.storageSummary()}",
                icon = source.type.connectionIcon(),
                trailing = {
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        IconButton(onClick = { onDeleteClientStorageSource(source.id) }) {
                            Icon(Icons.Default.Delete, contentDescription = "删除存储源")
                        }
                    }
                },
                onClick = {
                    when (source.type) {
                        ClientStorageType.WebDAV -> onEdit(ConnectionEditorTarget.WebDav(source))
                        ClientStorageType.SMB -> onEdit(ConnectionEditorTarget.Smb(source))
                    }
                },
            )
        }
    }
}

@Composable
private fun ConnectionEditorDialog(
    target: ConnectionEditorTarget,
    onDismiss: () -> Unit,
    onLoginServer: (String?, ProviderType, String, String, String, String) -> Unit,
    onLogoutServer: (String?) -> Unit,
    onSaveWebDav: (String?, String, String, String, String, ClientStorageAuthType, Boolean) -> Unit,
    onSaveSmb: (String?, String, String, String, String, String, Boolean) -> Unit,
) {
    when (target) {
        is ConnectionEditorTarget.Server -> ServerConnectionDialog(
            target = target,
            onDismiss = onDismiss,
            onLogin = onLoginServer,
            onLogout = onLogoutServer,
        )
        is ConnectionEditorTarget.WebDav -> WebDavConnectionDialog(
            target = target,
            onDismiss = onDismiss,
            onSave = onSaveWebDav,
        )
        is ConnectionEditorTarget.Smb -> SmbConnectionDialog(
            target = target,
            onDismiss = onDismiss,
            onSave = onSaveSmb,
        )
    }
}

@Composable
private fun ServerConnectionDialog(
    target: ConnectionEditorTarget.Server,
    onDismiss: () -> Unit,
    onLogin: (String?, ProviderType, String, String, String, String) -> Unit,
    onLogout: (String?) -> Unit,
) {
    var profileName by remember(target) { mutableStateOf(target.profile?.displayName ?: target.type.labelText()) }
    var serverUrl by remember(target) { mutableStateOf(target.profile?.serverUrl.orEmpty()) }
    var username by remember(target) { mutableStateOf("") }
    var password by remember(target) { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val uriHandler = LocalUriHandler.current
    val title = "${if (target.profile == null) "添加" else "编辑"}${target.type.labelText()} 后端"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = profileName,
                    onValueChange = { profileName = it },
                    label = { Text("媒体库名称") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                if (target.type == ProviderType.MediaTree) {
                    TextButton(
                        onClick = { uriHandler.openUri(MEDIATREE_BACKEND_REPOSITORY_URL) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "MediaTree 配套后端，兼容性更高。点击了解",
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("服务器地址") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("用户名") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                PasswordTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码",
                    visible = passwordVisible,
                    onVisibleChange = { passwordVisible = it },
                )
            }
        },
        confirmButton = {
            FilledTonalButton(onClick = { onLogin(target.profile?.id, target.type, serverUrl, profileName, username, password) }) {
                Text("登录")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (target.profile != null) {
                    TextButton(
                        onClick = { onLogout(target.profile.id) },
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("登出")
                    }
                }
                TextButton(onClick = onDismiss) { Text("取消") }
            }
        },
    )
}

@Composable
private fun WebDavConnectionDialog(
    target: ConnectionEditorTarget.WebDav,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, ClientStorageAuthType, Boolean) -> Unit,
) {
    val source = target.source
    var name by remember(target) { mutableStateOf(source?.name ?: "WebDAV") }
    var url by remember(target) { mutableStateOf(source?.endpoint.orEmpty()) }
    var username by remember(target) { mutableStateOf(source?.username.orEmpty()) }
    var password by remember(target) { mutableStateOf("") }
    var authType by remember(target) { mutableStateOf(source?.authType ?: ClientStorageAuthType.Basic) }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source == null) "添加 WebDAV 存储源" else "编辑 WebDAV 存储源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text("WebDAV 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    ClientStorageAuthType.entries.forEach { type ->
                        DesignFilterChip(
                            selected = authType == type,
                            onClick = { authType = type },
                            label = if (type == ClientStorageAuthType.Basic) "Basic" else "Bearer",
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("用户名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                PasswordTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码或 Token",
                    visible = passwordVisible,
                    onVisibleChange = { passwordVisible = it },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(source?.id, name, url, username, password.ifBlank { source?.secret.orEmpty() }, authType, true) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun SmbConnectionDialog(
    target: ConnectionEditorTarget.Smb,
    onDismiss: () -> Unit,
    onSave: (String?, String, String, String, String, String, Boolean) -> Unit,
) {
    val source = target.source
    var name by remember(target) { mutableStateOf(source?.name ?: "SMB") }
    var server by remember(target) { mutableStateOf(source?.endpoint ?: "smb://192.168.1.10") }
    var sharePath by remember(target) { mutableStateOf(source?.path ?: "/Media") }
    var username by remember(target) { mutableStateOf(source?.username.orEmpty()) }
    var password by remember(target) { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (source == null) "添加 SMB 存储源" else "编辑 SMB 存储源") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("名称") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = server, onValueChange = { server = it }, label = { Text("SMB 地址") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = sharePath, onValueChange = { sharePath = it }, label = { Text("共享路径") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = username, onValueChange = { username = it }, label = { Text("用户名") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                PasswordTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = "密码",
                    visible = passwordVisible,
                    onVisibleChange = { passwordVisible = it },
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(source?.id, name, server, sharePath, username, password.ifBlank { source?.secret.orEmpty() }, true) }) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    )
}

@Composable
private fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    visible: Boolean,
    onVisibleChange: (Boolean) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { onVisibleChange(!visible) }) {
                Icon(
                    if (visible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = "切换密码显示",
                )
            }
        },
    )
}

private sealed interface ConnectionEditorTarget {
    data class Server(val type: ProviderType, val profile: ServerProfile? = null) : ConnectionEditorTarget
    data class WebDav(val source: ClientStorageSource? = null) : ConnectionEditorTarget
    data class Smb(val source: ClientStorageSource? = null) : ConnectionEditorTarget
}

@Composable
private fun <T> PreferenceExpandableRow(
    title: String,
    selectedLabel: String,
    icon: ImageVector,
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DesignSettingsRow(
            title = title,
            subtitle = selectedLabel,
            icon = icon,
            trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                options.forEach { (value, label) ->
                    val isSelected = value == selected
                    DesignSettingsRow(
                        title = label,
                        subtitle = "",
                        icon = icon,
                        modifier = Modifier.padding(start = 18.dp),
                        trailing = {
                            if (isSelected) Icon(Icons.Default.CheckCircle, contentDescription = "当前")
                        },
                        onClick = {
                            expanded = false
                            onSelect(value)
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ThemeColorPreferenceRow(
    title: String,
    themeColorPreference: String,
    onThemeColorChange: (String) -> Unit,
) {
    var expanded by remember(title) { mutableStateOf(false) }
    var customColor by remember(themeColorPreference) { mutableStateOf(themeColorPreference) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        DesignSettingsRow(
            title = title,
            subtitle = themeColorPreference,
            icon = Icons.Default.Visibility,
            trailing = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeColorSwatch(colorHex = themeColorPreference, selected = false, onClick = null)
                    Icon(Icons.Default.ChevronRight, contentDescription = null)
                }
            },
            onClick = { expanded = !expanded },
        )
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                    themeColorPresets.forEach { colorHex ->
                        ThemeColorSwatch(
                            colorHex = colorHex,
                            selected = colorHex.equals(themeColorPreference, ignoreCase = true),
                            onClick = { onThemeColorChange(colorHex) },
                        )
                    }
                }
                OutlinedTextField(
                    value = customColor,
                    onValueChange = { value ->
                        customColor = value
                        if (isValidThemeColor(value)) onThemeColorChange(sanitizeThemeColor(value))
                    },
                    label = { Text("#RRGGBB") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = customColor.isNotBlank() && !isValidThemeColor(customColor),
                )
            }
        }
    }
}

@Composable
private fun ThemeColorSwatch(
    colorHex: String,
    selected: Boolean,
    onClick: (() -> Unit)?,
) {
    val color = remember(colorHex) { themeColorFromHex(colorHex) }
    val shape = CircleShape
    Surface(
        modifier = Modifier
            .size(32.dp)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = shape,
        color = Color.Transparent,
    ) {
        Box(
            modifier = Modifier
                .background(color, shape)
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                    shape = shape,
                ),
        )
    }
}

private fun isValidThemeColor(value: String): Boolean =
    Regex("^#?[0-9A-Fa-f]{6}$").matches(value.trim())

private fun themeColorFromHex(value: String): Color {
    val normalized = sanitizeThemeColor(value).removePrefix("#")
    val rgb = normalized.toInt(16)
    return Color(
        red = ((rgb shr 16) and 0xFF) / 255f,
        green = ((rgb shr 8) and 0xFF) / 255f,
        blue = (rgb and 0xFF) / 255f,
        alpha = 1f,
    )
}

private fun HomeLayoutPreference.labelText(): String = when (this) {
    HomeLayoutPreference.MediaFeed -> "媒体流"
    HomeLayoutPreference.DirectoryFirst -> "目录优先"
}

private fun ThemeModePreference.labelText(): String = when (this) {
    ThemeModePreference.System -> "跟随系统"
    ThemeModePreference.Light -> "浅色模式"
    ThemeModePreference.Dark -> "深色模式"
}

private fun FullscreenModePreference.labelText(): String = when (this) {
    FullscreenModePreference.Portrait -> "竖向"
    FullscreenModePreference.Landscape -> "横向"
    FullscreenModePreference.Auto -> "自适应"
}

@Composable
private fun ReleaseNotesDialog(
    releaseUpdateState: ReleaseUpdateState,
    fallbackVersion: String,
    onDismiss: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current
    val downloadUrl = releaseUpdateState.downloadUrlForDialog()
    val releaseNotes = releaseUpdateState.releaseNotesForDialog().ifBlank { "暂无更新内容" }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(releaseUpdateState.releaseDialogTitle(fallbackVersion)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "更新内容",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onDismiss()
                    uriHandler.openUri(downloadUrl)
                },
            ) {
                Text("前往下载")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消")
            }
        },
    )
}

@Composable
fun UpdateAvailableDot(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(8.dp)
            .background(MaterialTheme.colorScheme.error, CircleShape),
    )
}

private fun ReleaseUpdateState.versionSubtitle(fallbackVersion: String): String = when (this) {
    is ReleaseUpdateState.Available -> "$currentVersion · 可更新到 $latestVersion"
    is ReleaseUpdateState.Checking -> "${currentVersion.ifBlank { fallbackVersion }} · 检查更新中"
    is ReleaseUpdateState.Current -> "$currentVersion · 已是最新"
    is ReleaseUpdateState.Failed -> "${currentVersion.ifBlank { fallbackVersion }} · 检查失败"
}

private fun ReleaseUpdateState.releaseDialogTitle(fallbackVersion: String): String = when (this) {
    is ReleaseUpdateState.Available -> "发现新版本 $latestVersion"
    is ReleaseUpdateState.Current -> "当前版本 ${currentVersion.ifBlank { fallbackVersion }}"
    is ReleaseUpdateState.Checking -> "当前版本 ${currentVersion.ifBlank { fallbackVersion }}"
    is ReleaseUpdateState.Failed -> "当前版本 ${currentVersion.ifBlank { fallbackVersion }}"
}

private fun ReleaseUpdateState.releaseNotesForDialog(): String = when (this) {
    is ReleaseUpdateState.Available -> releaseNotes
    is ReleaseUpdateState.Current -> releaseNotes
    is ReleaseUpdateState.Checking,
    is ReleaseUpdateState.Failed,
    -> ""
}

private fun ReleaseUpdateState.downloadUrlForDialog(): String = when (this) {
    is ReleaseUpdateState.Available -> downloadUrl
    is ReleaseUpdateState.Current -> downloadUrl
    is ReleaseUpdateState.Checking,
    is ReleaseUpdateState.Failed,
    -> "${ReleaseUpdateChecker.REPOSITORY_URL}/releases/latest"
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    DesignSectionCard(title = title, icon = icon, actions = actions, content = content)
}

private fun ClientStorageSource.storageSummary(): String {
    val provider = when (type) {
        ClientStorageType.WebDAV -> "WebDAV"
        ClientStorageType.SMB -> "SMB"
    }
    val location = listOf(endpoint, path)
        .filter { it.isNotBlank() }
        .joinToString(" ")
    return "$provider · $location · ${username.ifBlank { "匿名" }}"
}

private fun ServerProfile.connectionStatus(session: Session): String = when {
    id == session.activeProfileId && authenticated -> "当前 · 已登录"
    authenticated -> "已登录"
    else -> "未配置"
}

private fun ServerProfile.displayName(): String =
    displayName.ifBlank { serverUrl.ifBlank { type.labelText() } }

private fun MediaRootDto.displayName(): String =
    label.ifBlank { path.substringAfterLast("/") }.ifBlank { path }

private fun ServerProfile.canLoadMediaRoots(): Boolean = when (type) {
    ProviderType.MediaTree -> serverUrl.isNotBlank() && authenticated
    ProviderType.Jellyfin, ProviderType.Emby ->
        serverUrl.isNotBlank() && authenticated && token.isNotBlank() && userId.isNotBlank()
    ProviderType.WebDAV, ProviderType.SMB -> false
}

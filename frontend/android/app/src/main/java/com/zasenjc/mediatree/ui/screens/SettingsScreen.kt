package com.zasenjc.mediatree.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Storage
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zasenjc.mediatree.data.ApiException
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.ClientStorageAuthType
import com.zasenjc.mediatree.data.ClientStorageSource
import com.zasenjc.mediatree.data.ClientStorageType
import com.zasenjc.mediatree.data.HomeLayoutPreference
import com.zasenjc.mediatree.data.MediaRootDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.data.ServerProfile
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.ThemeModePreference
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

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    data class BackendLibraryItem(
        val profileId: String,
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
        val scanning: Boolean = false,
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

    fun saveServerProfile(profileId: String?, providerType: ProviderType, serverUrl: String) {
        viewModelScope.launch {
            kotlin.runCatching {
                container.sessionStore.saveProfile(profileId, serverUrl, providerType)
            }
                .onSuccess { _state.update { it.copy(message = "${providerType.labelText()} 已保存", error = null) } }
                .onFailure { throwable -> _state.update { it.copy(error = throwable.message) } }
        }
    }

    fun loginServerProfile(profileId: String?, providerType: ProviderType, serverUrl: String, username: String, password: String) {
        viewModelScope.launch {
            val normalized = UrlUtils.normalizeServerUrl(serverUrl)
            _state.update { it.copy(message = "", error = null) }
            try {
                container.sessionStore.saveProfile(profileId, normalized, providerType)
                val provider = container.mediaProviderFor(providerType)
                val status = provider.authStatus(normalized)
                if (!status.needAuth) {
                    container.sessionStore.saveSession(normalized, "", type = providerType)
                    _state.update { it.copy(message = "${providerType.labelText()} 无需登录，已连接") }
                    return@launch
                }
                val result = provider.login(normalized, username, password)
                if (result.ok) {
                    container.sessionStore.saveSession(normalized, result.token, type = providerType, userId = result.userId)
                    _state.update { it.copy(message = "${providerType.labelText()} 登录成功") }
                } else {
                    _state.update { it.copy(error = "${providerType.labelText()} 登录失败") }
                }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message ?: "${providerType.labelText()} 登录失败") }
            }
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
        }
    }

    fun saveServer() {
        viewModelScope.launch {
            val state = _state.value
            container.sessionStore.saveServer(state.serverInput, type = state.providerType)
            _state.update { it.copy(message = "服务器地址已保存") }
        }
    }

    fun login() {
        viewModelScope.launch {
            val state = _state.value
            val normalized = UrlUtils.normalizeServerUrl(state.serverInput)
            _state.update { it.copy(message = "", error = null) }
            try {
                val provider = container.mediaProviderFor(state.providerType)
                val status = provider.authStatus(normalized)
                if (!status.needAuth) {
                    container.sessionStore.saveSession(normalized, "", type = state.providerType)
                    _state.update { it.copy(message = "服务器无需登录，已连接") }
                    return@launch
                }
                val result = provider.login(normalized, state.username, state.password)
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

    fun logout() {
        viewModelScope.launch { container.sessionStore.logout() }
    }

    fun setActiveLibrary(path: String) {
        viewModelScope.launch { container.sessionStore.setActiveLibrary(path) }
    }

    fun scan(activeLibrary: String) {
        viewModelScope.launch {
            _state.update { it.copy(scanning = true, message = "", error = null) }
            kotlin.runCatching { container.mediaProviderFor(_state.value.providerType).scan(activeLibrary) }
                .onSuccess { r -> _state.update { it.copy(scanning = false, message = "扫描任务已触发，共 ${r.total} 项") } }
                .onFailure { throwable -> _state.update { it.copy(scanning = false, error = throwable.message) } }
        }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    session: Session,
    onError: (Throwable) -> Unit,
) {
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { SettingsViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var editingConnection by remember { mutableStateOf<ConnectionEditorTarget?>(null) }

    LaunchedEffect(session.serverUrl, session.activeProviderType, session.resolvedProfiles) {
        vm.initServerInput(session.serverUrl)
        vm.initProviderType(session.activeProviderType)
        if (session.resolvedProfiles.any { it.canLoadMediaRoots() }) {
            vm.loadRoots(session)
        } else {
            vm.clearBackendLibraries()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { onError(ApiException(0, it)) }
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
                    Text("首页布局", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        DesignFilterChip(
                            selected = state.homeLayoutPreference == HomeLayoutPreference.MediaFeed,
                            onClick = { vm.setHomeLayoutPreference(HomeLayoutPreference.MediaFeed) },
                            label = "媒体流",
                            modifier = Modifier.weight(1f),
                        )
                        DesignFilterChip(
                            selected = state.homeLayoutPreference == HomeLayoutPreference.DirectoryFirst,
                            onClick = { vm.setHomeLayoutPreference(HomeLayoutPreference.DirectoryFirst) },
                            label = "目录优先",
                            modifier = Modifier.weight(1f),
                        )
                    }
                    ThemeModeSelector(
                        selected = state.themeModePreference,
                        onSelect = vm::setThemeModePreference,
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
                    onLogout = vm::logout,
                )
            }
            item {
                SettingsSectionCard(title = "媒体库显示", icon = Icons.Default.Folder) {
                    state.backendLibraries.forEach { library ->
                        DesignSettingsRow(
                            title = library.root.label.ifBlank { library.root.path.substringAfterLast("/") },
                            subtitle = "${library.providerType.labelText()} 媒体库 · ${library.root.movieCount} 项",
                            icon = library.providerType.connectionIcon(),
                            trailing = {
                                if (session.activeProfileId == library.profileId && session.activeLibrary == library.root.path) {
                                    Icon(Icons.Default.CheckCircle, contentDescription = "当前")
                                }
                            },
                            onClick = { vm.selectBackendLibrary(library.profileId, library.root.path) },
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
                    Button(
                        enabled = !state.scanning,
                        onClick = { vm.scan(session.activeLibrary) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (state.scanning) "触发中..." else "立即扫描媒体库")
                    }
                }
            }
            if (state.message.isNotBlank()) {
                item {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 4.dp),
                    )
                }
            }
            item {
                SettingsSectionCard(title = "播放", icon = Icons.Default.PlayArrow) {
                    DesignSettingsRow(
                        title = "默认画面",
                        subtitle = "原生系统",
                        icon = Icons.Default.PlayArrow,
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
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
                    DesignSettingsRow(
                        title = "版本",
                        subtitle = "0.1.00",
                        icon = Icons.Default.Info,
                    )
                    DesignSettingsRow(
                        title = "关于 mediatree",
                        subtitle = "Android client",
                        icon = Icons.Default.Info,
                        trailing = { Icon(Icons.Default.ChevronRight, contentDescription = null) },
                    )
                }
            }
        }
    }
    editingConnection?.let { target ->
        ConnectionEditorDialog(
            target = target,
            onDismiss = { editingConnection = null },
            onSaveServer = { profileId, providerType, serverUrl ->
                vm.saveServerProfile(profileId, providerType, serverUrl)
                editingConnection = null
            },
            onLoginServer = { profileId, providerType, serverUrl, username, password ->
                vm.loginServerProfile(profileId, providerType, serverUrl, username, password)
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
}

@Composable
private fun ConnectionsSection(
    session: Session,
    sources: List<ClientStorageSource>,
    onAdd: (ConnectionEditorTarget) -> Unit,
    onEdit: (ConnectionEditorTarget) -> Unit,
    onDeleteClientStorageSource: (String) -> Unit,
    onLogout: () -> Unit,
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
                title = profile.serverUrl.ifBlank { profile.type.labelText() },
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
        if (profiles.isNotEmpty()) {
            TextButton(
                onClick = onLogout,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("退出所有后端登录")
            }
        }
    }
}

@Composable
private fun ConnectionEditorDialog(
    target: ConnectionEditorTarget,
    onDismiss: () -> Unit,
    onSaveServer: (String?, ProviderType, String) -> Unit,
    onLoginServer: (String?, ProviderType, String, String, String) -> Unit,
    onSaveWebDav: (String?, String, String, String, String, ClientStorageAuthType, Boolean) -> Unit,
    onSaveSmb: (String?, String, String, String, String, String, Boolean) -> Unit,
) {
    when (target) {
        is ConnectionEditorTarget.Server -> ServerConnectionDialog(
            target = target,
            onDismiss = onDismiss,
            onSave = onSaveServer,
            onLogin = onLoginServer,
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
    onSave: (String?, ProviderType, String) -> Unit,
    onLogin: (String?, ProviderType, String, String, String) -> Unit,
) {
    var serverUrl by remember(target) { mutableStateOf(target.profile?.serverUrl.orEmpty()) }
    var username by remember(target) { mutableStateOf("") }
    var password by remember(target) { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    val title = "${if (target.profile == null) "添加" else "编辑"}${target.type.labelText()} 后端"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
            FilledTonalButton(onClick = { onLogin(target.profile?.id, target.type, serverUrl, username, password) }) {
                Text("登录")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Button(onClick = { onSave(target.profile?.id, target.type, serverUrl) }) { Text("保存") }
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
private fun ThemeModeSelector(
    selected: ThemeModePreference,
    onSelect: (ThemeModePreference) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        listOf(
            ThemeModePreference.System to "跟随系统",
            ThemeModePreference.Light to "浅色模式",
            ThemeModePreference.Dark to "深色模式",
        ).forEach { (preference, label) ->
            DesignFilterChip(
                selected = selected == preference,
                onClick = { onSelect(preference) },
                label = label,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

private fun ThemeModePreference.labelText(): String = when (this) {
    ThemeModePreference.System -> "跟随系统"
    ThemeModePreference.Light -> "浅色模式"
    ThemeModePreference.Dark -> "深色模式"
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
    id == session.activeProfileId && token.isNotBlank() -> "当前 · 已登录"
    id == session.activeProfileId -> "当前 · 已连接"
    token.isNotBlank() -> "已登录"
    serverUrl.isNotBlank() -> "已连接"
    else -> "未配置"
}

private fun ServerProfile.canLoadMediaRoots(): Boolean = when (type) {
    ProviderType.MediaTree -> serverUrl.isNotBlank()
    ProviderType.Jellyfin, ProviderType.Emby ->
        serverUrl.isNotBlank() && token.isNotBlank() && userId.isNotBlank()
    ProviderType.WebDAV, ProviderType.SMB -> false
}

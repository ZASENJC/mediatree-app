package com.zasenjc.mediatree.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.zasenjc.mediatree.data.MediaRootDto
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.ui.shouldLoadRemoteContent
import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val libraryViews = listOf("全部媒体库", "电影库", "剧集库")

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val serverInput: String = "",
        val username: String = "",
        val password: String = "",
        val roots: List<MediaRootDto> = emptyList(),
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
        val smbEnabled: Boolean = true,
        val libraryView: String = "全部媒体库",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            container.clientStorageRepository.sourcesFlow.collect { sources ->
                _state.update { it.copy(clientStorageSources = sources) }
            }
        }
    }

    fun initServerInput(serverUrl: String) {
        if (_state.value.serverInput.isBlank() || _state.value.serverInput != serverUrl) {
            _state.update { it.copy(serverInput = serverUrl) }
        }
    }

    fun onServerInputChange(value: String) = _state.update { it.copy(serverInput = value, message = "") }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value) }
    fun onWebDavNameChange(value: String) = _state.update { it.copy(webDavName = value) }
    fun onWebDavUrlChange(value: String) = _state.update { it.copy(webDavUrl = value) }
    fun onWebDavUsernameChange(value: String) = _state.update { it.copy(webDavUsername = value) }
    fun onWebDavPasswordChange(value: String) = _state.update { it.copy(webDavPassword = value) }
    fun onWebDavAuthTypeChange(value: ClientStorageAuthType) = _state.update { it.copy(webDavAuthType = value) }
    fun onWebDavEnabledChange(value: Boolean) = _state.update { it.copy(webDavEnabled = value) }
    fun onSmbNameChange(value: String) = _state.update { it.copy(smbName = value) }
    fun onSmbAddressChange(value: String) = _state.update { it.copy(smbAddress = value) }
    fun onSmbSharePathChange(value: String) = _state.update { it.copy(smbSharePath = value) }
    fun onSmbUsernameChange(value: String) = _state.update { it.copy(smbUsername = value) }
    fun onSmbPasswordChange(value: String) = _state.update { it.copy(smbPassword = value) }
    fun onSmbEnabledChange(value: Boolean) = _state.update { it.copy(smbEnabled = value) }
    fun setLibraryView(value: String) = _state.update { it.copy(libraryView = value) }

    fun loadRoots() {
        viewModelScope.launch {
            try {
                val roots = container.mediaProvider.mediaRoots().items
                _state.update { it.copy(roots = roots) }
            } catch (e: Throwable) {
                _state.update { it.copy(error = e.message) }
            }
        }
    }

    fun saveServer() {
        viewModelScope.launch {
            container.sessionStore.saveServer(_state.value.serverInput)
            _state.update { it.copy(message = "服务器地址已保存") }
        }
    }

    fun login() {
        viewModelScope.launch {
            val state = _state.value
            val normalized = UrlUtils.normalizeServerUrl(state.serverInput)
            _state.update { it.copy(message = "", error = null) }
            try {
                val status = container.mediaProvider.authStatus(normalized)
                if (!status.needAuth) {
                    container.sessionStore.saveSession(normalized, "")
                    _state.update { it.copy(message = "服务器无需登录，已连接") }
                    return@launch
                }
                val result = container.mediaProvider.login(normalized, state.username, state.password)
                if (result.ok) {
                    container.sessionStore.saveSession(normalized, result.token)
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
            kotlin.runCatching { container.mediaProvider.scan(activeLibrary) }
                .onSuccess { r -> _state.update { it.copy(scanning = false, message = "扫描任务已触发，共 ${r.total} 项") } }
                .onFailure { throwable -> _state.update { it.copy(scanning = false, error = throwable.message) } }
        }
    }

    fun saveWebDavSource() {
        val state = _state.value
        viewModelScope.launch {
            kotlin.runCatching {
                container.clientStorageRepository.saveWebDav(
                    name = state.webDavName,
                    url = state.webDavUrl,
                    username = state.webDavUsername,
                    password = state.webDavPassword,
                    authType = state.webDavAuthType,
                    enabled = state.webDavEnabled,
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
        viewModelScope.launch {
            kotlin.runCatching {
                container.clientStorageRepository.saveSmb(
                    name = state.smbName,
                    server = state.smbAddress,
                    sharePath = state.smbSharePath,
                    username = state.smbUsername,
                    password = state.smbPassword,
                    enabled = state.smbEnabled,
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
    onOpenClientStorageSource: (String) -> Unit = {},
) {
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { SettingsViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()
    var passwordVisible by remember { mutableStateOf(false) }
    var webDavPasswordVisible by remember { mutableStateOf(false) }
    var smbPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(session.serverUrl) {
        vm.initServerInput(session.serverUrl)
        if (shouldLoadRemoteContent(session)) {
            vm.loadRoots()
        }
    }

    LaunchedEffect(state.error) {
        state.error?.let { onError(ApiException(0, it)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 16.dp, top = 10.dp, end = 16.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                SettingsSectionCard(title = "后端连接", icon = Icons.Default.Dns) {
                    OutlinedTextField(
                        value = state.serverInput,
                        onValueChange = vm::onServerInputChange,
                        label = { Text("服务器地址") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        ConnectionStatusChip(session)
                        AssistChip(
                            onClick = {},
                            label = {
                                Text(
                                    when {
                                        session.serverUrl.isBlank() -> "未连接"
                                        session.token.isBlank() -> "已连接"
                                        else -> "已登录"
                                    },
                                )
                            },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.username,
                            onValueChange = vm::onUsernameChange,
                            label = { Text("用户名") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.password,
                            onValueChange = vm::onPasswordChange,
                            label = { Text("密码") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                    Icon(
                                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "切换密码显示",
                                    )
                                }
                            },
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(onClick = vm::saveServer, modifier = Modifier.weight(1f)) { Text("保存连接") }
                        FilledTonalButton(onClick = vm::login, modifier = Modifier.weight(1f)) { Text("登录") }
                    }
                    TextButton(
                        onClick = vm::logout,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("退出登录")
                    }
                }
            }
            item {
                SettingsSectionCard(title = "客户端存储源", icon = Icons.Default.Storage) {
                    Text("WebDAV", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.webDavName,
                            onValueChange = vm::onWebDavNameChange,
                            label = { Text("名称") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Text("启用", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = state.webDavEnabled, onCheckedChange = vm::onWebDavEnabledChange)
                        }
                    }
                    OutlinedTextField(
                        value = state.webDavUrl,
                        onValueChange = vm::onWebDavUrlChange,
                        label = { Text("WebDAV 地址") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        ClientStorageAuthType.entries.forEach { authType ->
                            FilterChip(
                                selected = state.webDavAuthType == authType,
                                onClick = { vm.onWebDavAuthTypeChange(authType) },
                                label = { Text(if (authType == ClientStorageAuthType.Basic) "Basic" else "Bearer") },
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.webDavUsername,
                            onValueChange = vm::onWebDavUsernameChange,
                            label = { Text("用户名") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.webDavPassword,
                            onValueChange = vm::onWebDavPasswordChange,
                            label = { Text("密码或 Token") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            visualTransformation = if (webDavPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { webDavPasswordVisible = !webDavPasswordVisible }) {
                                    Icon(
                                        if (webDavPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "切换 WebDAV 密钥显示",
                                    )
                                }
                            },
                        )
                    }
                    Button(onClick = vm::saveWebDavSource, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("保存 WebDAV")
                    }

                    Text("SMB", style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.smbName,
                            onValueChange = vm::onSmbNameChange,
                            label = { Text("名称") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                            Text("启用", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Switch(checked = state.smbEnabled, onCheckedChange = vm::onSmbEnabledChange)
                        }
                    }
                    OutlinedTextField(
                        value = state.smbAddress,
                        onValueChange = vm::onSmbAddressChange,
                        label = { Text("SMB 地址") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = state.smbSharePath,
                        onValueChange = vm::onSmbSharePathChange,
                        label = { Text("共享路径") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = state.smbUsername,
                            onValueChange = vm::onSmbUsernameChange,
                            label = { Text("用户名") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = state.smbPassword,
                            onValueChange = vm::onSmbPasswordChange,
                            label = { Text("密码") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            visualTransformation = if (smbPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { smbPasswordVisible = !smbPasswordVisible }) {
                                    Icon(
                                        if (smbPasswordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = "切换 SMB 密码显示",
                                    )
                                }
                            },
                        )
                    }
                    Button(onClick = vm::saveSmbSource, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("保存 SMB")
                    }

                    state.clientStorageSources.forEach { source ->
                        ElevatedCard(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            ListItem(
                                headlineContent = { Text(source.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text(source.storageSummary(), maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                                trailingContent = {
                                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                                        if (source.enabled) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = "已启用")
                                        }
                                        if (source.type == ClientStorageType.WebDAV) {
                                            TextButton(onClick = { onOpenClientStorageSource("webdav/${source.id}") }) {
                                                Text("浏览")
                                            }
                                        }
                                        IconButton(onClick = { vm.deleteClientStorageSource(source.id) }) {
                                            Icon(Icons.Default.Delete, contentDescription = "删除存储源")
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
            item {
                SettingsSectionCard(title = "媒体库显示", icon = Icons.Default.Folder) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        libraryViews.forEach { item ->
                            FilterChip(
                                selected = state.libraryView == item,
                                onClick = { vm.setLibraryView(item) },
                                label = { Text(item) },
                            )
                        }
                    }
                    state.roots.filterForLibraryView(state.libraryView).forEach { root ->
                        ElevatedCard(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            ListItem(
                                headlineContent = {
                                    Text(root.label.ifBlank { root.path.substringAfterLast("/") }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = { Text("${root.movieCount} 部 · ${root.scraper.ifBlank { "auto" }}") },
                                leadingContent = { Icon(Icons.Default.Folder, contentDescription = null) },
                                trailingContent = {
                                    if (session.activeLibrary == root.path) Icon(Icons.Default.CheckCircle, contentDescription = "当前")
                                },
                                modifier = Modifier.clickable { vm.setActiveLibrary(root.path) },
                            )
                        }
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
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun ConnectionStatusChip(session: Session) {
    val label = when {
        session.serverUrl.isBlank() -> "离线"
        else -> "已连接"
    }
    AssistChip(
        onClick = {},
        leadingIcon = {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        label = { Text(label) },
    )
}

private fun List<MediaRootDto>.filterForLibraryView(view: String): List<MediaRootDto> = when (view) {
    "电影库" -> filter { it.label.contains("movie", true) || it.path.contains("movie", true) }
    "剧集库" -> filter { it.label.contains("tv", true) || it.path.contains("tv", true) || it.label.contains("show", true) }
    else -> this
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

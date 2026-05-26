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
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsEthernet
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
import com.zasenjc.mediatree.data.MediaRootDto
import com.zasenjc.mediatree.data.Session
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private val libraryViews = listOf("全部媒体库", "电影库", "剧集库")

data class SmbServerUi(
    val address: String,
    val sharePath: String,
    val username: String,
    val enabled: Boolean,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val serverInput: String = "",
        val username: String = "",
        val password: String = "",
        val roots: List<MediaRootDto> = emptyList(),
        val scanning: Boolean = false,
        val message: String = "",
        val error: String? = null,
        val smbAddress: String = "smb://192.168.1.10",
        val smbSharePath: String = "/Media",
        val smbUsername: String = "",
        val smbPassword: String = "",
        val smbEnabled: Boolean = true,
        val smbServers: List<SmbServerUi> = emptyList(),
        val libraryView: String = "全部媒体库",
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun initServerInput(serverUrl: String) {
        if (_state.value.serverInput.isBlank() || _state.value.serverInput != serverUrl) {
            _state.update { it.copy(serverInput = serverUrl) }
        }
    }

    fun onServerInputChange(value: String) = _state.update { it.copy(serverInput = value, message = "") }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value) }
    fun onSmbAddressChange(value: String) = _state.update { it.copy(smbAddress = value) }
    fun onSmbSharePathChange(value: String) = _state.update { it.copy(smbSharePath = value) }
    fun onSmbUsernameChange(value: String) = _state.update { it.copy(smbUsername = value) }
    fun onSmbPasswordChange(value: String) = _state.update { it.copy(smbPassword = value) }
    fun onSmbEnabledChange(value: Boolean) = _state.update { it.copy(smbEnabled = value) }
    fun setLibraryView(value: String) = _state.update { it.copy(libraryView = value) }

    fun loadRoots() {
        viewModelScope.launch {
            try {
                val roots = container.api.mediaRoots().items
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
                val status = container.api.authStatus(normalized)
                if (!status.needAuth) {
                    container.sessionStore.saveSession(normalized, "")
                    _state.update { it.copy(message = "服务器无需登录，已连接") }
                    return@launch
                }
                val result = container.api.login(normalized, state.username, state.password)
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
            kotlin.runCatching { container.api.scan(activeLibrary) }
                .onSuccess { r -> _state.update { it.copy(scanning = false, message = "扫描任务已触发，共 ${r.total} 项") } }
                .onFailure { throwable -> _state.update { it.copy(scanning = false, error = throwable.message) } }
        }
    }

    fun addSmbServer() {
        val state = _state.value
        if (state.smbAddress.isBlank() || state.smbSharePath.isBlank()) {
            _state.update { it.copy(error = "请填写 SMB 地址和共享路径") }
            return
        }
        val server = SmbServerUi(
            address = state.smbAddress,
            sharePath = state.smbSharePath,
            username = state.smbUsername,
            enabled = state.smbEnabled,
        )
        _state.update {
            it.copy(
                smbServers = it.smbServers + server,
                message = "SMB 服务器已加入本地草稿",
                smbSharePath = "",
            )
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
    var passwordVisible by remember { mutableStateOf(false) }
    var smbPasswordVisible by remember { mutableStateOf(false) }

    LaunchedEffect(session.serverUrl) {
        vm.initServerInput(session.serverUrl)
        vm.loadRoots()
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
                            label = { Text(if (session.token.isBlank()) "已连接" else "已登录") },
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
                SettingsSectionCard(title = "SMB 服务器", icon = Icons.Default.SettingsEthernet) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("启用 SMB 服务器", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = state.smbEnabled, onCheckedChange = vm::onSmbEnabledChange)
                    }
                    OutlinedTextField(
                        value = state.smbAddress,
                        onValueChange = vm::onSmbAddressChange,
                        label = { Text("服务器地址") },
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
                                        contentDescription = "切换密码显示",
                                    )
                                }
                            },
                        )
                    }
                    Button(onClick = vm::addSmbServer, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("添加 SMB 服务器")
                    }
                    state.smbServers.forEach { server ->
                        ElevatedCard(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                        ) {
                            ListItem(
                                headlineContent = { Text(server.sharePath.ifBlank { server.address }) },
                                supportingContent = { Text("${server.address} · ${server.username.ifBlank { "匿名" }}") },
                                leadingContent = { Icon(Icons.Default.Storage, contentDescription = null) },
                                trailingContent = {
                                    if (server.enabled) Icon(Icons.Default.CheckCircle, contentDescription = "已启用")
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

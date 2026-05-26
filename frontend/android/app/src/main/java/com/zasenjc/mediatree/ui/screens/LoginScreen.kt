package com.zasenjc.mediatree.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zasenjc.mediatree.data.ApiException
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class LoginViewModel(private val container: AppContainer, initialServerUrl: String) : ViewModel() {
    data class UiState(
        val serverUrl: String = "",
        val username: String = "",
        val password: String = "",
        val loading: Boolean = false,
        val error: String = "",
    )

    private val _state = MutableStateFlow(UiState(serverUrl = initialServerUrl))
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value) }
    fun onUsernameChange(value: String) = _state.update { it.copy(username = value) }
    fun onPasswordChange(value: String) = _state.update { it.copy(password = value) }

    fun login() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = "") }
            val normalized = UrlUtils.normalizeServerUrl(_state.value.serverUrl)
            try {
                val status = container.api.authStatus(normalized)
                if (!status.needAuth) {
                    container.sessionStore.saveSession(normalized, "")
                } else {
                    val result = container.api.login(normalized, _state.value.username, _state.value.password)
                    if (!result.ok) {
                        _state.update { it.copy(loading = false, error = "登录失败") }
                        return@launch
                    }
                    container.sessionStore.saveSession(normalized, result.token)
                }
                _state.update { it.copy(loading = false, error = "") }
            } catch (e: Throwable) {
                val msg = if (e is ApiException && e.statusCode == 401) "账号或密码错误" else "无法连接服务器"
                _state.update { it.copy(loading = false, error = msg) }
            }
        }
    }
}

@Composable
fun LoginScreen(container: AppContainer, initialServerUrl: String) {
    val vm: LoginViewModel = viewModel(factory = viewModelFactory { LoginViewModel(container, initialServerUrl) })
    val state by vm.state.collectAsStateWithLifecycle()

    Scaffold(contentWindowInsets = WindowInsets.safeDrawing) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("MediaTree", style = MaterialTheme.typography.headlineMedium)
                    Text("连接你的媒体库服务器", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(
                    value = state.serverUrl,
                    onValueChange = vm::onServerUrlChange,
                    label = { Text("服务器地址") },
                    placeholder = { Text("http://192.168.1.10:27580") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.username,
                    onValueChange = vm::onUsernameChange,
                    label = { Text("账号") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = state.password,
                    onValueChange = vm::onPasswordChange,
                    label = { Text("密码") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (state.error.isNotBlank()) {
                    Text(state.error, color = MaterialTheme.colorScheme.error)
                }
                Button(
                    onClick = vm::login,
                    enabled = !state.loading && state.serverUrl.isNotBlank(),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(if (state.loading) "连接中..." else "连接 / 登录")
                }
            }
        }
    }
}

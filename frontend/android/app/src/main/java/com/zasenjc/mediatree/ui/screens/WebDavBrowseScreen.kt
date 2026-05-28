package com.zasenjc.mediatree.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.ClientStorageSource
import com.zasenjc.mediatree.data.ClientStorageType
import com.zasenjc.mediatree.data.WebDavEntry
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.player.MediaTreePlayer
import com.zasenjc.mediatree.playback.PlaybackSource
import com.zasenjc.mediatree.ui.components.ErrorPane
import com.zasenjc.mediatree.ui.components.FullscreenSystemBarsEffect
import com.zasenjc.mediatree.ui.components.LoadingPane
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class WebDavBrowseViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val source: ClientStorageSource? = null,
        val path: String = "",
        val entries: List<WebDavEntry> = emptyList(),
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(sourceId: String, path: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, path = path) }
            try {
                val source = container.clientStorageRepository.load()
                    .firstOrNull { it.id == sourceId && it.type == ClientStorageType.WebDAV && it.enabled }
                    ?: throw IllegalArgumentException("WebDAV 存储源不可用")
                val entries = container.webDavClient.list(source, path)
                _state.update {
                    it.copy(
                        loading = false,
                        source = source,
                        path = path,
                        entries = entries,
                    )
                }
            } catch (e: Throwable) {
                _state.update { it.copy(loading = false, error = e) }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavBrowseScreen(
    container: AppContainer,
    sourceId: String,
    path: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val vm: WebDavBrowseViewModel = viewModel(factory = viewModelFactory { WebDavBrowseViewModel(container) })
    val state by vm.state.collectAsStateWithLifecycle()

    LaunchedEffect(sourceId, path) {
        vm.load(sourceId, path)
    }

    LaunchedEffect(state.error) {
        state.error?.let(onError)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(state.source?.name ?: "WebDAV", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { vm.load(sourceId, path) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "刷新")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            when {
                state.loading -> LoadingPane(Modifier.fillMaxSize())
                state.error != null -> ErrorPane(
                    message = state.error?.message ?: "WebDAV 目录加载失败",
                    onRetry = { vm.load(sourceId, path) },
                )
                else -> WebDavEntryList(
                    path = state.path,
                    entries = state.entries,
                    onOpenDirectory = { entry ->
                        onNavigate("webdav/$sourceId?path=${Uri.encode(entry.path)}")
                    },
                    onOpenVideo = { entry ->
                        onNavigate("webdavPlayer/$sourceId?path=${Uri.encode(entry.path)}")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun WebDavEntryList(
    path: String,
    entries: List<WebDavEntry>,
    onOpenDirectory: (WebDavEntry) -> Unit,
    onOpenVideo: (WebDavEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Text(
                text = path.ifBlank { "根目录" },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        items(entries, key = { it.path }) { entry ->
            WebDavEntryRow(
                entry = entry,
                onClick = {
                    when {
                        entry.isDirectory -> onOpenDirectory(entry)
                        entry.isPlayableVideo -> onOpenVideo(entry)
                    }
                },
            )
        }
        if (entries.isEmpty()) {
            item {
                Text(
                    text = "此目录为空",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                )
            }
        }
    }
}

@Composable
private fun WebDavEntryRow(entry: WebDavEntry, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(enabled = entry.isDirectory || entry.isPlayableVideo, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isPlayableVideo || entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    entryMeta(entry),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (entry.isPlayableVideo) {
                Icon(Icons.Default.PlayArrow, contentDescription = "播放")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebDavPlayerScreen(
    container: AppContainer,
    sourceId: String,
    path: String,
    onBack: () -> Unit,
    onError: (Throwable) -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    var source by remember { mutableStateOf<ClientStorageSource?>(null) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var positionSeconds by remember { mutableDoubleStateOf(0.0) }

    FullscreenSystemBarsEffect(isLandscape)
    BackHandler {
        if (isLandscape) {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        } else {
            onBack()
        }
    }

    LaunchedEffect(sourceId) {
        runCatching {
            container.clientStorageRepository.load()
                .firstOrNull { it.id == sourceId && it.type == ClientStorageType.WebDAV && it.enabled }
                ?: throw IllegalArgumentException("WebDAV 存储源不可用")
        }.onSuccess { source = it }
            .onFailure { error = it }
    }

    LaunchedEffect(error) {
        error?.let(onError)
    }

    val playbackSource = source?.let {
        PlaybackSource.webDav(source = it, path = path)
    }

    Scaffold(
        topBar = {
            if (!isLandscape) {
                TopAppBar(
                    title = { Text(path.substringAfterLast('/').ifBlank { "WebDAV 播放" }, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "横屏播放")
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                )
            }
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (isLandscape) Modifier else Modifier.padding(padding))
                .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                error != null -> ErrorPane(message = error?.message ?: "WebDAV 播放源加载失败", modifier = Modifier.fillMaxSize())
                playbackSource == null -> LoadingPane(Modifier.fillMaxSize())
                else -> {
                    val playerModifier = if (isLandscape) {
                        Modifier.fillMaxSize()
                    } else {
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .align(Alignment.TopCenter)
                    }
                    MediaTreePlayer(
                        playbackSource = playbackSource,
                        startPosition = positionSeconds,
                        onPlaybackPositionChange = { pos, _ -> positionSeconds = pos },
                        isFullscreen = isLandscape,
                        showFullscreenButton = true,
                        showAspectRatioControls = isLandscape,
                        onFullscreenRequest = {
                            activity?.requestedOrientation = if (isLandscape) {
                                ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                            } else {
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        },
                        modifier = playerModifier,
                    )
                    if (!isLandscape) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 16.dp)
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Spacer(Modifier.height(0.dp))
                            Text(path.substringAfterLast('/'), style = MaterialTheme.typography.titleMedium)
                            Text(source?.name.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun entryMeta(entry: WebDavEntry): String {
    if (entry.isDirectory) return "文件夹"
    val size = if (entry.sizeBytes > 0) readableBytes(entry.sizeBytes) else ""
    return listOf(entry.contentType, size, entry.modified.takeIf { it.isNotBlank() })
        .filter { !it.isNullOrBlank() }
        .joinToString(" · ")
        .ifBlank { "文件" }
}

private fun readableBytes(bytes: Long): String {
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return if (gb >= 1.0) {
        String.format("%.1f GB", gb)
    } else {
        String.format("%.0f MB", bytes / 1024.0 / 1024.0)
    }
}

private tailrec fun android.content.Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.findActivity()
    else -> null
}

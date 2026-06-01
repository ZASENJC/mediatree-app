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
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Folder
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
import androidx.compose.runtime.DisposableEffect
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
import com.zasenjc.mediatree.data.FullscreenModePreference
import com.zasenjc.mediatree.data.SmbClient
import com.zasenjc.mediatree.data.SmbEntry
import com.zasenjc.mediatree.data.viewModelFactory
import com.zasenjc.mediatree.player.MediaTreePlayer
import com.zasenjc.mediatree.ui.components.ErrorPane
import com.zasenjc.mediatree.ui.components.FullscreenSystemBarsEffect
import com.zasenjc.mediatree.ui.components.LoadingPane
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SmbBrowseViewModel(private val container: AppContainer) : ViewModel() {
    data class UiState(
        val loading: Boolean = true,
        val source: ClientStorageSource? = null,
        val path: String = "",
        val entries: List<SmbEntry> = emptyList(),
        val error: Throwable? = null,
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun load(sourceId: String, path: String) {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null, path = path) }
            try {
                val source = container.clientStorageRepository.load()
                    .firstOrNull { it.id == sourceId && it.type == ClientStorageType.SMB && it.enabled }
                    ?: throw IllegalArgumentException("SMB 存储源不可用")
                val entries = container.smbClient.list(source, path)
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
fun SmbBrowseScreen(
    container: AppContainer,
    sourceId: String,
    path: String,
    onBack: () -> Unit,
    onNavigate: (String) -> Unit,
    onError: (Throwable) -> Unit,
) {
    val vm: SmbBrowseViewModel = viewModel(factory = viewModelFactory { SmbBrowseViewModel(container) })
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
                title = { Text(state.source?.name ?: "SMB", maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    message = state.error?.message ?: "SMB 目录加载失败",
                    onRetry = { vm.load(sourceId, path) },
                )
                else -> SmbEntryList(
                    path = state.path,
                    entries = state.entries,
                    onOpenDirectory = { entry ->
                        onNavigate("smb/$sourceId?path=${Uri.encode(entry.path)}")
                    },
                    onOpenVideo = { entry ->
                        onNavigate("smbPlayer/$sourceId?path=${Uri.encode(entry.path)}")
                    },
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
    }
}

@Composable
private fun SmbEntryList(
    path: String,
    entries: List<SmbEntry>,
    onOpenDirectory: (SmbEntry) -> Unit,
    onOpenVideo: (SmbEntry) -> Unit,
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
            SmbEntryRow(
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
private fun SmbEntryRow(entry: SmbEntry, onClick: () -> Unit) {
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
                imageVector = if (entry.isDirectory) Icons.Default.Folder else Icons.AutoMirrored.Filled.InsertDriveFile,
                contentDescription = null,
                tint = if (entry.isPlayableVideo || entry.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(entry.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    smbEntryMeta(entry),
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
fun SmbPlayerScreen(
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
    val fullscreenModePreference by container.uiPreferencesStore.fullscreenModeFlow.collectAsStateWithLifecycle(
        initialValue = FullscreenModePreference.Landscape,
    )
    var currentPath by remember(path) { mutableStateOf(path) }
    var fullscreenRequested by remember { mutableStateOf(false) }
    var source by remember { mutableStateOf<ClientStorageSource?>(null) }
    var sameFolderVideos by remember { mutableStateOf<List<ClientStorageVideoItem>>(emptyList()) }
    var error by remember { mutableStateOf<Throwable?>(null) }
    var positionSeconds by remember { mutableDoubleStateOf(0.0) }

    val playerFullscreen = fullscreenRequested || isLandscape
    FullscreenSystemBarsEffect(playerFullscreen)
    BackHandler {
        if (playerFullscreen) {
            fullscreenRequested = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
        } else {
            onBack()
        }
    }

    LaunchedEffect(sourceId, currentPath) {
        runCatching {
            val loadedSource = container.clientStorageRepository.load()
                .firstOrNull { it.id == sourceId && it.type == ClientStorageType.SMB && it.enabled }
                ?: throw IllegalArgumentException("SMB 存储源不可用")
            val entries = container.smbClient.list(loadedSource, storageParentPath(currentPath))
            val currentItem = ClientStorageVideoItem(
                name = storageFileName(currentPath),
                path = currentPath,
                originalPath = SmbClient.buildSmbUrl(loadedSource, currentPath),
            )
            source = loadedSource
            sameFolderVideos = ensureCurrentVideo(
                videos = entries.filter { it.isPlayableVideo }.map { entry ->
                    ClientStorageVideoItem(
                        name = entry.name,
                        path = entry.path,
                        originalPath = SmbClient.buildSmbUrl(loadedSource, entry.path),
                    )
                },
                current = currentItem,
            )
            error = null
            loadedSource
        }.onSuccess { source = it }
            .onFailure { error = it }
    }

    LaunchedEffect(error) {
        error?.let(onError)
    }

    val playbackSource = remember(source?.id, currentPath) {
        source?.let { container.smbRangeProxy.playbackSource(source = it, path = currentPath) }
    }

    DisposableEffect(playbackSource) {
        onDispose { playbackSource?.onClose?.invoke() }
    }

    Scaffold(
        topBar = {
            if (!playerFullscreen) {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        IconButton(onClick = {
                            fullscreenRequested = true
                            requestFullscreenOrientation(activity, fullscreenModePreference)
                        }) {
                            Icon(Icons.Default.PlayArrow, contentDescription = "全屏播放")
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
                .then(if (playerFullscreen) Modifier else Modifier.padding(padding))
                .background(MaterialTheme.colorScheme.background),
        ) {
            when {
                error != null -> ErrorPane(message = error?.message ?: "SMB 播放源加载失败", modifier = Modifier.fillMaxSize())
                playbackSource == null -> LoadingPane(Modifier.fillMaxSize())
                else -> {
                    if (playerFullscreen) {
                        MediaTreePlayer(
                            playbackSource = playbackSource,
                            startPosition = positionSeconds,
                            onPlaybackPositionChange = { pos, _ -> positionSeconds = pos },
                            isFullscreen = true,
                            showFullscreenButton = true,
                            showAspectRatioControls = true,
                            onFullscreenRequest = {
                                fullscreenRequested = false
                                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT
                            },
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.TopCenter),
                        ) {
                            MediaTreePlayer(
                                playbackSource = playbackSource,
                                startPosition = positionSeconds,
                                onPlaybackPositionChange = { pos, _ -> positionSeconds = pos },
                                isFullscreen = false,
                                showFullscreenButton = true,
                                showAspectRatioControls = false,
                                onFullscreenRequest = {
                                    fullscreenRequested = true
                                    requestFullscreenOrientation(activity, fullscreenModePreference)
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(16f / 9f),
                            )
                            Spacer(Modifier.height(12.dp))
                            ClientStoragePlayerDetails(
                                fileName = storageFileName(currentPath),
                                originalPath = sameFolderVideos
                                    .firstOrNull { it.path == currentPath }
                                    ?.originalPath
                                    ?: source?.let { SmbClient.buildSmbUrl(it, currentPath) }
                                    .orEmpty(),
                                currentPath = currentPath,
                                videos = sameFolderVideos,
                                onSelectVideo = { item ->
                                    positionSeconds = 0.0
                                    currentPath = item.path
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun smbEntryMeta(entry: SmbEntry): String {
    if (entry.isDirectory) return "文件夹"
    return readableSmbBytes(entry.sizeBytes).ifBlank { "文件" }
}

private fun readableSmbBytes(bytes: Long): String {
    if (bytes <= 0) return ""
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

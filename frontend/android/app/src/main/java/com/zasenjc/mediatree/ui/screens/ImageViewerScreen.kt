package com.zasenjc.mediatree.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zasenjc.mediatree.data.AppContainer
import com.zasenjc.mediatree.data.ClientStorageSource
import com.zasenjc.mediatree.data.ClientStorageType
import com.zasenjc.mediatree.data.WebDavClient
import com.zasenjc.mediatree.ui.components.LocalMediaTreeImageAuth
import com.zasenjc.mediatree.ui.components.mediaTreeImageHeaders
import kotlin.math.abs

private const val ImageViewerMaxScale = 5f

private data class ImageViewerItem(
    val name: String,
    val path: String,
)

private data class ImageViewerLoadResult(
    val source: ClientStorageSource,
    val images: List<ImageViewerItem>,
)

@Composable
fun SmbImageViewerScreen(
    container: AppContainer,
    sourceId: String,
    path: String,
    onBack: () -> Unit,
) {
    var source by remember(sourceId) { mutableStateOf<ClientStorageSource?>(null) }
    var error by remember(sourceId, path) { mutableStateOf<Throwable?>(null) }
    var loading by remember(sourceId, path) { mutableStateOf(true) }
    var images by remember(sourceId, path) { mutableStateOf<List<ImageViewerItem>>(emptyList()) }

    LaunchedEffect(container, sourceId, path) {
        loading = true
        runCatching {
            val loadedSource = container.clientStorageRepository.load()
                .firstOrNull { it.id == sourceId && it.type == ClientStorageType.SMB && it.enabled }
                ?: throw IllegalArgumentException("SMB 存储源不可用")
            val current = ImageViewerItem(name = storageFileName(path), path = path)
            val sameFolderImages = container.smbClient.list(loadedSource, storageParentPath(path))
                .filter { it.isViewableImage }
                .map { entry -> ImageViewerItem(name = entry.name, path = entry.path) }
            ImageViewerLoadResult(
                source = loadedSource,
                images = ensureCurrentImage(sameFolderImages, current),
            )
        }.onSuccess {
            source = it.source
            images = it.images
            error = null
        }.onFailure {
            error = it
            images = emptyList()
        }.also {
            loading = false
        }
    }

    ImageViewerScaffold(
        fallbackTitle = storageFileName(path),
        initialPath = path,
        images = images,
        loading = loading || (source == null && error == null),
        errorMessage = error?.message,
        onBack = onBack,
    ) { item, onScaleChange ->
        source?.let { loadedSource ->
            SmbZoomableRemoteImage(
                container = container,
                source = loadedSource,
                item = item,
                onScaleChange = onScaleChange,
            )
        } ?: ImageViewerMessage("图片源不可用")
    }
}

@Composable
fun WebDavImageViewerScreen(
    container: AppContainer,
    sourceId: String,
    path: String,
    onBack: () -> Unit,
) {
    var source by remember(sourceId) { mutableStateOf<ClientStorageSource?>(null) }
    var error by remember(sourceId, path) { mutableStateOf<Throwable?>(null) }
    var loading by remember(sourceId, path) { mutableStateOf(true) }
    var images by remember(sourceId, path) { mutableStateOf<List<ImageViewerItem>>(emptyList()) }

    LaunchedEffect(container, sourceId, path) {
        loading = true
        runCatching {
            val loadedSource = container.clientStorageRepository.load()
                .firstOrNull { it.id == sourceId && it.type == ClientStorageType.WebDAV && it.enabled }
                ?: throw IllegalArgumentException("WebDAV 存储源不可用")
            val current = ImageViewerItem(name = storageFileName(path), path = path)
            val sameFolderImages = container.webDavClient.list(loadedSource, storageParentPath(path))
                .filter { it.isViewableImage }
                .map { entry -> ImageViewerItem(name = entry.name, path = entry.path) }
            ImageViewerLoadResult(
                source = loadedSource,
                images = ensureCurrentImage(sameFolderImages, current),
            )
        }.onSuccess {
            source = it.source
            images = it.images
            error = null
        }.onFailure {
            error = it
            images = emptyList()
        }.also { loading = false }
    }

    ImageViewerScaffold(
        fallbackTitle = storageFileName(path),
        initialPath = path,
        images = images,
        loading = loading || (source == null && error == null),
        errorMessage = error?.message,
        onBack = onBack,
    ) { item, onScaleChange ->
        source?.let { loadedSource ->
            ZoomableRemoteImage(
                imageUrl = WebDavClient.buildResourceUrl(loadedSource, item.path),
                headers = WebDavClient.authorizationHeaders(loadedSource),
                contentDescription = item.name,
                onScaleChange = onScaleChange,
            )
        } ?: ImageViewerMessage("图片源不可用")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageViewerScaffold(
    fallbackTitle: String,
    initialPath: String,
    images: List<ImageViewerItem>,
    loading: Boolean,
    errorMessage: String?,
    onBack: () -> Unit,
    imageContent: @Composable (ImageViewerItem, (Float) -> Unit) -> Unit,
) {
    if (images.isEmpty()) {
        ImageViewerFrame(
            title = fallbackTitle.ifBlank { "图片" },
            onBack = onBack,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> CircularProgressIndicator(color = Color.White)
                    errorMessage != null -> ImageViewerMessage(errorMessage)
                    else -> ImageViewerMessage("图片源不可用")
                }
            }
        }
        return
    }

    val imagesKey = remember(images) { images.joinToString("|") { it.path } }
    key(imagesKey) {
        val initialPage = images.indexOfFirst { it.path == initialPath }.takeIf { it >= 0 } ?: 0
        val pagerState = rememberPagerState(
            initialPage = initialPage.coerceIn(0, images.lastIndex),
            pageCount = { images.size },
        )
        var currentScale by remember(imagesKey) { mutableFloatStateOf(1f) }
        LaunchedEffect(pagerState.currentPage, imagesKey) {
            currentScale = 1f
        }
        val currentItem = images[pagerState.currentPage.coerceIn(0, images.lastIndex)]
        ImageViewerFrame(
            title = currentItem.name.ifBlank { fallbackTitle.ifBlank { "图片" } },
            onBack = onBack,
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    loading -> CircularProgressIndicator(color = Color.White)
                    errorMessage != null -> ImageViewerMessage(errorMessage)
                    else -> HorizontalPager(
                        state = pagerState,
                        userScrollEnabled = currentScale <= 1.01f,
                        modifier = Modifier.fillMaxSize(),
                    ) { page ->
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            imageContent(images[page]) { scale ->
                                if (page == pagerState.currentPage) {
                                    currentScale = scale
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImageViewerFrame(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        containerColor = Color.Black,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                ),
            )
        },
        content = content,
    )
}

@Composable
private fun SmbZoomableRemoteImage(
    container: AppContainer,
    source: ClientStorageSource,
    item: ImageViewerItem,
    onScaleChange: (Float) -> Unit,
) {
    val imageSource = remember(source.id, item.path) {
        container.smbRangeProxy.playbackSource(source = source, path = item.path)
    }
    DisposableEffect(imageSource) {
        onDispose { imageSource.onClose?.invoke() }
    }
    ZoomableRemoteImage(
        imageUrl = imageSource.uri,
        headers = emptyMap(),
        contentDescription = item.name,
        onScaleChange = onScaleChange,
    )
}

@Composable
fun ZoomableRemoteImage(
    imageUrl: String,
    headers: Map<String, String>,
    contentDescription: String,
    fallbackImageUrl: String? = null,
    onScaleChange: (Float) -> Unit,
) {
    var displayedImageUrl by remember(imageUrl, fallbackImageUrl) { mutableStateOf(imageUrl) }
    var failed by remember(displayedImageUrl, headers) { mutableStateOf(false) }
    var scale by remember(displayedImageUrl) { mutableFloatStateOf(1f) }
    var offset by remember(displayedImageUrl) { mutableStateOf(Offset.Zero) }
    var layoutSize by remember { mutableStateOf(IntSize.Zero) }
    LaunchedEffect(imageUrl, fallbackImageUrl) {
        displayedImageUrl = imageUrl
    }
    LaunchedEffect(displayedImageUrl) {
        scale = 1f
        offset = Offset.Zero
        onScaleChange(1f)
    }
    val request = rememberZoomableImageRequest(imageUrl = displayedImageUrl, headers = headers)
    AsyncImage(
        model = request,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        onSuccess = { failed = false },
        onError = {
            if (!fallbackImageUrl.isNullOrBlank() && displayedImageUrl != fallbackImageUrl) {
                displayedImageUrl = fallbackImageUrl
            } else {
                failed = true
            }
        },
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { layoutSize = it }
            .pointerInput(displayedImageUrl, layoutSize) {
                awaitEachGesture {
                    do {
                        val event = awaitPointerEvent()
                        val pressedPointers = event.changes.count { it.pressed }
                        if (pressedPointers > 1 || scale > 1f) {
                            val zoomChange = event.calculateZoom()
                            val panChange = event.calculatePan()
                            val nextScale = (scale * zoomChange).coerceIn(1f, ImageViewerMaxScale)
                            val scaleChanged = abs(nextScale - scale) > 0.001f
                            scale = nextScale
                            offset = if (scale <= 1.01f) {
                                Offset.Zero
                            } else {
                                (offset + panChange).clampedToScaleBounds(scale, layoutSize)
                            }
                            onScaleChange(scale)
                            if (pressedPointers > 1 || scale > 1f || scaleChanged) {
                                event.changes.forEach { change ->
                                    if (change.positionChanged()) change.consume()
                                }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            },
    )
    if (failed) {
        ImageViewerMessage("图片无法加载")
    }
}

@Composable
private fun rememberZoomableImageRequest(
    imageUrl: String,
    headers: Map<String, String>,
): ImageRequest {
    val context = LocalContext.current
    val auth = LocalMediaTreeImageAuth.current
    val mergedHeaders = remember(imageUrl, headers, auth) {
        buildMap {
            putAll(mediaTreeImageHeaders(imageUrl, auth))
            putAll(headers)
        }
    }
    val cacheKey = remember(imageUrl, mergedHeaders) { "$imageUrl#${mergedHeaders.hashCode()}" }
    return remember(context, imageUrl, mergedHeaders, cacheKey) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .apply {
                mergedHeaders.forEach { (name, value) -> addHeader(name, value) }
            }
            .crossfade(false)
            .memoryCacheKey(cacheKey)
            .diskCacheKey(cacheKey)
            .build()
    }
}

@Composable
private fun ImageViewerMessage(message: String) {
    Text(
        text = message,
        color = Color.White.copy(alpha = 0.82f),
        style = MaterialTheme.typography.bodyMedium,
    )
}

private fun ensureCurrentImage(
    images: List<ImageViewerItem>,
    current: ImageViewerItem,
): List<ImageViewerItem> =
    if (images.any { it.path == current.path }) images else listOf(current) + images

private fun Offset.clampedToScaleBounds(scale: Float, size: IntSize): Offset {
    if (size.width <= 0 || size.height <= 0 || scale <= 1f) return Offset.Zero
    val maxX = size.width * (scale - 1f) / 2f
    val maxY = size.height * (scale - 1f) / 2f
    return Offset(
        x = x.coerceIn(-maxX, maxX),
        y = y.coerceIn(-maxY, maxY),
    )
}

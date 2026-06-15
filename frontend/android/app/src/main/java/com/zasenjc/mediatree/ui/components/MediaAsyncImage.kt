package com.zasenjc.mediatree.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zasenjc.mediatree.util.UrlUtils

private const val AveragePosterImageWidthPx = 360
private const val AveragePosterImageHeightPx = 540
private val ProtectedMediaTreeImagePathPrefixes = listOf(
    "/api/cover/",
    "/api/episode-still/",
    "/api/thumbnail/",
    "/api/media/",
)

data class MediaTreeImageAuth(
    val serverUrl: String = "",
    val token: String = "",
)

val LocalMediaTreeImageAuth = staticCompositionLocalOf { MediaTreeImageAuth() }

@Composable
fun MediaAsyncImage(
    imageUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    cornerRadius: Dp = 18.dp,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Default.Movie,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
            modifier = Modifier.fillMaxSize(0.28f),
        )
        if (!imageUrl.isNullOrBlank()) {
            val imageRequest = rememberMediaTreeImageRequest(
                imageUrl = imageUrl,
                width = AveragePosterImageWidthPx,
                height = AveragePosterImageHeightPx,
                memoryCacheKeyValue = imageUrl,
                diskCacheKeyValue = imageUrl,
            )
            AsyncImage(
                model = imageRequest,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
fun rememberMediaTreeImageRequest(
    imageUrl: String?,
    width: Int? = null,
    height: Int? = null,
    memoryCacheKeyValue: String? = imageUrl,
    diskCacheKeyValue: String? = imageUrl,
): ImageRequest {
    val context = LocalContext.current
    val auth = LocalMediaTreeImageAuth.current
    val headers = remember(imageUrl, auth) { mediaTreeImageHeaders(imageUrl, auth) }
    return remember(context, imageUrl, width, height, memoryCacheKeyValue, diskCacheKeyValue, headers) {
        ImageRequest.Builder(context)
            .data(imageUrl)
            .apply {
                headers.forEach { (name, value) -> addHeader(name, value) }
                if (width != null && height != null) size(width, height)
                if (memoryCacheKeyValue != null) memoryCacheKey(memoryCacheKeyValue)
                if (diskCacheKeyValue != null) diskCacheKey(diskCacheKeyValue)
            }
            .crossfade(false)
            .build()
    }
}

internal fun mediaTreeImageHeaders(imageUrl: String?, auth: MediaTreeImageAuth): Map<String, String> =
    if (auth.token.isNotBlank() && isProtectedMediaTreeImageUrl(imageUrl, auth.serverUrl)) {
        mapOf("Authorization" to "Bearer ${auth.token}")
    } else {
        emptyMap()
    }

internal fun isProtectedMediaTreeImageUrl(imageUrl: String?, serverUrl: String): Boolean {
    val value = imageUrl?.trim().orEmpty()
    if (value.isBlank()) return false
    val apiPath = mediaTreeApiPath(value, serverUrl) ?: return false
    return ProtectedMediaTreeImagePathPrefixes.any { prefix -> apiPath.startsWith(prefix) }
}

private fun mediaTreeApiPath(imageUrl: String, serverUrl: String): String? {
    if (imageUrl.startsWith("/api/")) return imageUrl.substringBefore("?")
    val normalizedServerUrl = UrlUtils.normalizeServerUrl(serverUrl).takeIf { it.isNotBlank() } ?: return null
    val apiBase = UrlUtils.apiBase(normalizedServerUrl)
    if (!imageUrl.startsWith("$apiBase/")) return null
    return "/api/${imageUrl.removePrefix("$apiBase/").substringBefore("?")}"
}

fun mediaScrim(): Brush = Brush.verticalGradient(
    colorStops = arrayOf(
        0.18f to Color.Transparent,
        0.68f to Color.Black.copy(alpha = 0.48f),
        1f to Color.Black.copy(alpha = 0.88f),
    ),
)

package com.zasenjc.mediatree.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zasenjc.mediatree.ui.components.shapeAwareClickable

data class ClientStorageVideoItem(
    val name: String,
    val path: String,
    val originalPath: String,
)

@Composable
fun ClientStoragePlayerDetails(
    fileName: String,
    originalPath: String,
    currentPath: String,
    videos: List<ClientStorageVideoItem>,
    onSelectVideo: (ClientStorageVideoItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentIndex = videos.indexOfFirst { it.path == currentPath }
    val previous = videos.getOrNull(currentIndex - 1)
    val next = videos.getOrNull(currentIndex + 1)
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = fileName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "原路径",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = originalPath,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "同文件夹",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    enabled = previous != null,
                    onClick = { previous?.let(onSelectVideo) },
                ) {
                    Icon(Icons.Default.SkipPrevious, contentDescription = "上一部")
                }
                IconButton(
                    enabled = next != null,
                    onClick = { next?.let(onSelectVideo) },
                ) {
                    Icon(Icons.Default.SkipNext, contentDescription = "下一部")
                }
            }
        }
        LazyColumn(
            modifier = Modifier.heightIn(max = 240.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(videos, key = { it.path }) { item ->
                val selected = item.path == currentPath
                val rowShape = RoundedCornerShape(8.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = if (selected) {
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
                            } else {
                                androidx.compose.ui.graphics.Color.Transparent
                            },
                            shape = rowShape,
                        )
                        .shapeAwareClickable(shape = rowShape, enabled = !selected) { onSelectVideo(item) }
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        Icons.Default.Movie,
                        contentDescription = null,
                        tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = item.path,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

fun storageParentPath(path: String): String {
    val normalized = path.trim('/', '\\').replace('\\', '/')
    return normalized.substringBeforeLast("/", missingDelimiterValue = "")
}

fun storageFileName(path: String): String =
    path.trim('/', '\\').replace('\\', '/').substringAfterLast('/')

fun storageFileNameOrFallback(path: String, fallback: String): String {
    val leaf = storageFileName(path)
    if (leaf.isBlank()) return fallback
    val original = path.trim('/', '\\')
    return if (
        original.contains('/') ||
        original.contains('\\') ||
        leaf.contains('.') ||
        fallback.isBlank() ||
        leaf == fallback
    ) {
        leaf
    } else {
        fallback
    }
}

fun ensureCurrentVideo(
    videos: List<ClientStorageVideoItem>,
    current: ClientStorageVideoItem,
): List<ClientStorageVideoItem> =
    if (videos.any { it.path == current.path }) videos else listOf(current) + videos

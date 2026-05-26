package com.zasenjc.mediatree.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zasenjc.mediatree.data.MovieDto

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun MoviePosterCard(
    movie: MovieDto,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    aspectRatio: Float = 2f / 3f,
    showFavorite: Boolean = true,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val title = movie.displayTitle ?: movie.title ?: movie.code
    val progress = (movie.progressPercent ?: 0.0).coerceIn(0.0, 100.0)
    ElevatedCard(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = { sheetOpen = true }),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.35f to Color.Transparent,
                                0.78f to Color.Black.copy(alpha = 0.58f),
                                1f to Color.Black.copy(alpha = 0.86f),
                            ),
                        ),
                    ),
            )
            if (showFavorite) {
                FavoriteBadge(
                    checked = movie.tags.contains("favorite"),
                    modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = movieCardMeta(movie),
                    color = Color.White.copy(alpha = 0.82f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress > 0.0 && !movie.tags.contains("watched")) {
                    LinearProgressIndicator(
                        progress = { (progress / 100.0).toFloat() },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(8.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }
    }
    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = rememberModalBottomSheetState()) {
            ListItem(headlineContent = { Text(title) }, supportingContent = { Text(movie.path) })
            ListItem(
                headlineContent = { Text("播放") },
                leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                modifier = Modifier.combinedClickable(onClick = { sheetOpen = false; onClick() }),
            )
            ListItem(
                headlineContent = { Text("更多操作将在后续版本补齐") },
                leadingContent = { Icon(Icons.Default.MoreVert, contentDescription = null) },
            )
            Spacer(Modifier.height(24.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun EpisodeLandscapeCard(
    movie: MovieDto,
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    showFavorite: Boolean = false,
) {
    val title = movie.displayTitle ?: movie.title ?: movie.code
    val episode = episodeLabel(movie)
    val progress = (movie.progressPercent ?: 0.0).coerceIn(0.0, 100.0)
    ElevatedCard(
        modifier = modifier.combinedClickable(onClick = onClick, onLongClick = onClick),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Box {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f),
            )
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            colorStops = arrayOf(
                                0.25f to Color.Transparent,
                                0.72f to Color.Black.copy(alpha = 0.58f),
                                1f to Color.Black.copy(alpha = 0.88f),
                            ),
                        ),
                    ),
            )
            Surface(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                shape = CircleShape,
                color = Color.White.copy(alpha = 0.9f),
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(6.dp).size(18.dp),
                )
            }
            if (showFavorite) {
                FavoriteBadge(
                    checked = movie.tags.contains("favorite"),
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                )
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = episode,
                    color = Color.White.copy(alpha = 0.84f),
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (progress > 0.0 && !movie.tags.contains("watched")) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearProgressIndicator(
                            progress = { (progress / 100.0).toFloat() },
                            modifier = Modifier.weight(1f).height(3.dp).clip(RoundedCornerShape(8.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                        Text("${progress.toInt()}%", color = Color.White.copy(alpha = 0.82f), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteBadge(checked: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = Color.White.copy(alpha = if (checked) 0.94f else 0.72f),
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
            contentDescription = if (checked) "已收藏" else "未收藏",
            tint = if (checked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(5.dp).size(18.dp),
        )
    }
}

private fun movieCardMeta(movie: MovieDto): String {
    val year = movie.releaseDate?.take(4).orEmpty()
    val type = when {
        movie.tmdbEpisode != null || movie.episodeTitle != null -> "剧集"
        movie.tmdbType == "tv" -> "剧集"
        else -> "电影"
    }
    return listOf(type, year.ifBlank { movie.code }).filter { it.isNotBlank() }.joinToString(" · ")
}

private fun episodeLabel(movie: MovieDto): String {
    val seasonEpisode = if (movie.tmdbSeason != null || movie.tmdbEpisode != null) {
        "S${(movie.tmdbSeason ?: 0).toString().padStart(2, '0')}E${(movie.tmdbEpisode ?: 0).toString().padStart(2, '0')}"
    } else {
        movie.code
    }
    return listOf(seasonEpisode, movie.episodeTitle).filter { !it.isNullOrBlank() }.joinToString(" · ")
}

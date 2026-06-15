package com.zasenjc.mediatree.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
    titleOverride: String? = null,
) {
    var sheetOpen by remember { mutableStateOf(false) }
    val title = titleOverride ?: movie.displayTitle ?: movie.title ?: movie.code
    val progress = (movie.progressPercent ?: 0.0).coerceIn(0.0, 100.0)
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        PosterImageFrame(
            title = title,
            imageUrl = imageUrl,
            aspectRatio = aspectRatio,
            showFavorite = showFavorite,
            favorite = movie.tags.contains("favorite"),
            progress = progress.takeIf { it > 0.0 && !movie.tags.contains("watched") },
            onClick = onClick,
            onLongClick = { sheetOpen = true },
        )
        PosterTextBelow(
            title = title,
            meta = movieCardMeta(movie),
        )
    }
    if (sheetOpen) {
        ModalBottomSheet(onDismissRequest = { sheetOpen = false }, sheetState = rememberModalBottomSheetState()) {
            ListItem(headlineContent = { Text(title) }, supportingContent = { Text(movie.path) })
            ListItem(
                headlineContent = { Text("播放") },
                leadingContent = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                modifier = Modifier.shapeAwareCombinedClickable(
                    shape = RoundedCornerShape(12.dp),
                    onClick = { sheetOpen = false; onClick() },
                ),
            )
            ListItem(
                headlineContent = { Text("更多操作未实现") },
                supportingContent = { Text("后续版本补齐") },
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(7.dp)) {
        PosterImageFrame(
            title = title,
            imageUrl = imageUrl,
            aspectRatio = 16f / 9f,
            showFavorite = showFavorite,
            favorite = movie.tags.contains("favorite"),
            progress = progress.takeIf { it > 0.0 && !movie.tags.contains("watched") },
            onClick = onClick,
            onLongClick = onClick,
            leadingPlayIcon = true,
        )
        PosterTextBelow(title = title, meta = episode)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PosterImageFrame(
    title: String,
    imageUrl: String?,
    aspectRatio: Float,
    showFavorite: Boolean,
    favorite: Boolean,
    progress: Double?,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    leadingPlayIcon: Boolean = false,
) {
    val posterShape = RoundedCornerShape(16.dp)
    Card(
        modifier = Modifier.shapeAwareCombinedClickable(shape = posterShape, onClick = onClick, onLongClick = onLongClick),
        shape = posterShape,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Box {
            MediaAsyncImage(
                imageUrl = imageUrl,
                contentDescription = title,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio),
                cornerRadius = 16.dp,
            )
            if (leadingPlayIcon) {
                Surface(
                    modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(6.dp).size(18.dp),
                    )
                }
            }
            if (showFavorite) {
                BookmarkRibbon(
                    checked = favorite,
                    modifier = Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 8.dp),
                )
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { (progress / 100.0).toFloat() },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.72f),
                )
            }
        }
    }
}

@Composable
private fun PosterTextBelow(title: String, meta: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = meta,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun BookmarkRibbon(checked: Boolean, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp, bottomStart = 10.dp, bottomEnd = 10.dp),
        color = if (checked) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f)
        } else {
            MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
        },
    ) {
        Icon(
            imageVector = if (checked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
            contentDescription = if (checked) "已收藏" else "未收藏",
            tint = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 5.dp).size(16.dp),
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

package com.zasenjc.mediatree.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.zasenjc.mediatree.data.MovieDto

@Composable
fun MovieList(
    movies: List<MovieDto>,
    total: Int,
    loading: Boolean,
    serverUrl: String,
    onOpenMovie: (Int) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (loading) {
        LoadingPane(modifier)
        return
    }
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("共 $total 部", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        items(movies.chunked(2)) { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { movie ->
                    MoviePosterCard(
                        movie = movie,
                        imageUrl = "$serverUrl/api/cover/${movie.id}",
                        onClick = { onOpenMovie(movie.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        if (movies.size < total) {
            item {
                Button(onClick = onLoadMore, modifier = Modifier.fillMaxWidth()) { Text("加载更多") }
            }
        }
    }
}

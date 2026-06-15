package com.zasenjc.mediatree.ui.screens

import com.zasenjc.mediatree.data.MovieDto
import com.zasenjc.mediatree.data.ProviderType
import com.zasenjc.mediatree.util.UrlUtils

data class DetailStillImage(
    val url: String,
    val fallbackUrl: String? = null,
) {
    val viewerUrl: String = fallbackUrl ?: url
}

fun saveableStillImageKey(still: DetailStillImage): String =
    still.url

fun detailStillImages(
    movie: MovieDto,
    serverUrl: String,
    providerType: ProviderType,
    fallbackStill: String,
    thumbnailUrl: (movieId: Int, index: Int) -> String,
): List<DetailStillImage> = buildList {
    val primaryStill = movie.episodeStill?.let { UrlUtils.resolveApiUrl(serverUrl, it) } ?: fallbackStill
    if (primaryStill.isNotBlank()) add(DetailStillImage(url = primaryStill))

    movie.javdbThumbnails.forEachIndexed { index, value ->
        val resolved = UrlUtils.resolveApiUrl(serverUrl, value)
        if (providerType == ProviderType.MediaTree) {
            add(DetailStillImage(url = thumbnailUrl(movie.id, index), fallbackUrl = resolved))
        } else if (resolved != null) {
            add(DetailStillImage(url = resolved))
        }
    }
}.distinctBy { it.url }

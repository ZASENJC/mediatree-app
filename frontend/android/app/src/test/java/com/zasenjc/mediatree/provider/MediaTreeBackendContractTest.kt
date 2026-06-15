package com.zasenjc.mediatree.provider

import com.zasenjc.mediatree.data.FolderTreeResponseDto
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTreeBackendContractTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Test
    fun folderTreeDecodesBackendVideoCountSeparatelyFromTotalMovieCount() {
        val decoded = json.decodeFromString<FolderTreeResponseDto>(
            """
            {
              "tree": [
                {
                  "name": "Show",
                  "path": "Show",
                  "movie_count": 12,
                  "video_count": 3
                }
              ]
            }
            """.trimIndent(),
        )

        val folder = decoded.tree.single()
        assertEquals(12, folder.movieCount)
        assertEquals(3, folder.videoCount)
    }
}

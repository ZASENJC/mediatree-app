package com.zasenjc.mediatree.player

import com.zasenjc.mediatree.data.ProgressDto
import com.zasenjc.mediatree.ui.screens.mediaTreeResumePosition
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTreePlaybackMemoryTest {
    @Test
    fun resumesBackendPositionOnlyWhenVideoIsUnfinished() {
        assertEquals(
            120.0,
            mediaTreeResumePosition(ProgressDto(position = 120.0, played = false, progressPercent = 40.0)),
            0.001,
        )
    }

    @Test
    fun doesNotResumeBackendPositionWhenAlreadyWatched() {
        assertEquals(
            0.0,
            mediaTreeResumePosition(ProgressDto(position = 950.0, played = true, progressPercent = 95.0)),
            0.001,
        )
    }

    @Test
    fun doesNotResumeBackendPositionNearTheEnd() {
        assertEquals(
            0.0,
            mediaTreeResumePosition(ProgressDto(position = 949.0, played = false, progressPercent = 95.0)),
            0.001,
        )
    }
}

package com.zasenjc.mediatree.player

import com.zasenjc.mediatree.data.ProgressDto
import com.zasenjc.mediatree.ui.screens.bestPlaybackSnapshot
import com.zasenjc.mediatree.ui.screens.mediaTreeResumePosition
import org.junit.Assert.assertEquals
import org.junit.Test

class MediaTreePlaybackMemoryTest {
    @Test
    fun deferredPlaybackStartSeekKeepsPositiveResumePointsOutOfInitialLoad() {
        assertEquals(171.32, deferredPlaybackStartSeek(171.32) ?: 0.0, 0.001)
        assertEquals(null, deferredPlaybackStartSeek(0.0))
        assertEquals(null, deferredPlaybackStartSeek(Double.NaN))
    }

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

    @Test
    fun exitSnapshotFallsBackToLastKnownProgressWhenControllerReportsZero() {
        val snapshot = bestPlaybackSnapshot(
            controllerSnapshot = PlaybackPositionSnapshot(positionSeconds = 0.0, durationSeconds = 0.0),
            lastKnownSnapshot = PlaybackPositionSnapshot(positionSeconds = 186.5, durationSeconds = 1_000.0),
        )

        assertEquals(186.5, snapshot?.positionSeconds ?: 0.0, 0.001)
    }

    @Test
    fun exitSnapshotUsesPositiveControllerPositionEvenWhenItIsLowerThanLastKnownProgress() {
        val snapshot = bestPlaybackSnapshot(
            controllerSnapshot = PlaybackPositionSnapshot(positionSeconds = 80.0, durationSeconds = 1_000.0),
            lastKnownSnapshot = PlaybackPositionSnapshot(positionSeconds = 186.5, durationSeconds = 1_000.0),
        )

        assertEquals(80.0, snapshot?.positionSeconds ?: 0.0, 0.001)
    }
}

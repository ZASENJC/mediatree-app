package com.zasenjc.mediatree.data

class RemotePlaybackMemoryCoordinator(
    private val repository: RemotePlaybackMemoryRepository,
    private val providerFor: (ProviderType) -> MediaProvider,
) {
    suspend fun resumePosition(providerType: ProviderType, profileId: String, movieId: Int): Double {
        val localResume = repository.resumePosition(providerType, profileId, movieId)
        if (localResume > 0.0) {
            PlaybackMemoryLogger.debug(
                "remote-resume provider=${providerType.name} movieId=$movieId source=local position=${localResume.memoryLogValue()}",
            )
            return localResume
        }
        val backendResume = runCatching { backendPlaybackResumePosition(providerFor(providerType).progress(movieId)) }
            .onFailure { error ->
                PlaybackMemoryLogger.warn(
                    "remote-resume-failed provider=${providerType.name} movieId=$movieId",
                    error,
                )
            }
            .getOrDefault(0.0)
        PlaybackMemoryLogger.debug(
            "remote-resume provider=${providerType.name} movieId=$movieId source=backend position=${backendResume.memoryLogValue()}",
        )
        return backendResume
    }

    suspend fun recordProgress(
        providerType: ProviderType,
        profileId: String,
        mediaRoot: String,
        movie: MovieDto,
        positionSeconds: Double,
        durationSeconds: Double,
    ) {
        record(providerType, profileId, mediaRoot, movie, positionSeconds, durationSeconds, stopped = false)
    }

    suspend fun recordExit(
        providerType: ProviderType,
        profileId: String,
        mediaRoot: String,
        movie: MovieDto,
        positionSeconds: Double,
        durationSeconds: Double,
    ) {
        record(providerType, profileId, mediaRoot, movie, positionSeconds, durationSeconds, stopped = true)
    }

    suspend fun markFinished(providerType: ProviderType, profileId: String, movieId: Int) {
        repository.markFinished(providerType, profileId, movieId)
    }

    private suspend fun record(
        providerType: ProviderType,
        profileId: String,
        mediaRoot: String,
        movie: MovieDto,
        positionSeconds: Double,
        durationSeconds: Double,
        stopped: Boolean,
    ) {
        val decision = playbackMemorySaveDecision(positionSeconds, durationSeconds)
        PlaybackMemoryLogger.debug(
            "remote-record provider=${providerType.name} movieId=${movie.id} stopped=$stopped position=${positionSeconds.memoryLogValue()} duration=${durationSeconds.memoryLogValue()} decision=${decision.memoryLogName()}",
        )
        if (decision == PlaybackMemorySaveDecision.Ignore) return
        repository.save(
            providerType = providerType,
            profileId = profileId,
            mediaRoot = mediaRoot,
            movie = movie,
            positionSeconds = positionSeconds,
            durationSeconds = durationSeconds,
        )
        if (providerType.syncsPlaybackMemoryToBackend() && shouldSyncBackendProgress(positionSeconds)) {
            runCatching {
                providerFor(providerType).saveProgress(
                    movieId = movie.id,
                    position = positionSeconds,
                    duration = durationSeconds.takeIf { it.isFinite() && it > 0.0 },
                    stopped = stopped,
                )
            }.onSuccess {
                PlaybackMemoryLogger.debug(
                    "remote-backend-sync provider=${providerType.name} movieId=${movie.id} stopped=$stopped position=${positionSeconds.memoryLogValue()}",
                )
            }.onFailure { error ->
                PlaybackMemoryLogger.warn(
                    "remote-backend-sync-failed provider=${providerType.name} movieId=${movie.id} stopped=$stopped",
                    error,
                )
            }
        } else {
            PlaybackMemoryLogger.debug(
                "remote-backend-sync-skip provider=${providerType.name} movieId=${movie.id} position=${positionSeconds.memoryLogValue()}",
            )
        }
    }
}

private fun PlaybackMemorySaveDecision.memoryLogName(): String = when (this) {
    PlaybackMemorySaveDecision.Clear -> "clear"
    PlaybackMemorySaveDecision.Ignore -> "ignore"
    is PlaybackMemorySaveDecision.Remember -> "remember"
}

fun backendPlaybackResumePosition(progress: ProgressDto): Double {
    val position = progress.position.takeIf { it.isFinite() && it >= PlaybackMemoryMinimumPositionSeconds } ?: return 0.0
    if (progress.played || progress.progressPercent >= 95.0) return 0.0
    return position
}

fun shouldSyncBackendProgress(position: Double): Boolean =
    position.isFinite() && position >= PlaybackMemoryMinimumPositionSeconds

private fun ProviderType.syncsPlaybackMemoryToBackend(): Boolean = when (this) {
    ProviderType.MediaTree,
    ProviderType.Jellyfin,
    ProviderType.Emby,
    -> true
    ProviderType.SMB,
    ProviderType.WebDAV,
    ProviderType.M3U,
    -> false
}

package com.zasenjc.mediatree.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class PlayerUiCompletenessSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun mpvPlayerExposesCompletePrimaryControls() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertTrue(player.contains("onDoubleTap"))
        assertTrue(player.contains("PlayerGestureLayer"))
        assertTrue(player.contains("detectHorizontalDragGestures"))
        assertTrue(player.contains("horizontalSeekDeltaSeconds"))
        assertTrue(player.contains("HorizontalSeekSecondsPerScreen = 90.0"))
        assertTrue(player.contains("PlayerLock"))
        assertTrue(player.contains("FullscreenControl"))
        assertTrue(player.contains("PlaybackSpeedMenu"))
        assertTrue(player.contains("SubtitleTrackMenu"))
        assertTrue(player.contains("AudioTrackMenu"))
        assertTrue(player.contains("AspectRatioMenu"))
        assertTrue(player.contains("PlayerErrorOverlay"))
        assertTrue(player.contains("PlayerSeekBar"))
        assertFalse(player.contains("PlayerProgressBar"))
        assertFalse(player.contains("LinearProgressIndicator"))
        assertTrue(player.contains("Slider("))
        assertTrue(player.contains("controller.seekTo"))
        assertTrue(player.contains("controller.seekBy(deltaSeconds)"))
        assertTrue(player.contains("controller.setPlaybackSpeed"))
        assertTrue(player.contains("controller.selectAudioTrack"))
        assertTrue(player.contains("controller.setAspectRatio"))
        assertTrue(player.contains("controller.percentPosition()"))
        assertTrue(player.contains("controller.lastError()"))
    }

    @Test
    fun playerUiRemovesVisibleTenSecondSeekButtons() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertFalse(player.contains("Icons.Default.Replay10"))
        assertFalse(player.contains("Icons.Default.Forward10"))
        assertFalse(player.contains("onSeekBy ="))
        assertFalse(player.contains("快退10秒"))
        assertFalse(player.contains("快进10秒"))
    }

    @Test
    fun doubleTapUsesSideZonesAndCenterPause() {
        assertEquals(PlayerDoubleTapAction.TogglePlay, playerDoubleTapAction(tapX = 70f, width = 300))
        assertEquals(PlayerDoubleTapAction.Rewind, playerDoubleTapAction(tapX = 20f, width = 300))
        assertEquals(PlayerDoubleTapAction.TogglePlay, playerDoubleTapAction(tapX = 100f, width = 300))
        assertEquals(PlayerDoubleTapAction.TogglePlay, playerDoubleTapAction(tapX = 150f, width = 300))
        assertEquals(PlayerDoubleTapAction.TogglePlay, playerDoubleTapAction(tapX = 200f, width = 300))
        assertEquals(PlayerDoubleTapAction.TogglePlay, playerDoubleTapAction(tapX = 230f, width = 300))
        assertEquals(PlayerDoubleTapAction.Forward, playerDoubleTapAction(tapX = 280f, width = 300))
    }

    @Test
    fun temporaryFastForwardSpeedRestoresOriginalSpeed() {
        assertEquals(2.0, temporaryFastForwardSpeed(currentSpeed = 1.0), 0.001)
        assertEquals(1.25, restoredPlaybackSpeed(speedBeforeHold = 1.25, currentSpeed = 2.0), 0.001)
        assertEquals(2.0, restoredPlaybackSpeed(speedBeforeHold = 2.0, currentSpeed = 2.0), 0.001)
        assertEquals(0.75, restoredPlaybackSpeed(speedBeforeHold = 0.75, currentSpeed = 1.5), 0.001)
    }

    @Test
    fun playerLongPressUsesTemporaryFastForwardWithReleaseRestore() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()
        val gestureLayer = player
            .substringAfter("private fun PlayerGestureLayer(")
            .substringBefore("@Composable\nprivate fun PlayerControlsOverlay")

        assertTrue(player.contains("TemporaryFastForwardSpeed = 2.0"))
        assertTrue(player.contains("temporaryFastForwardSpeed(currentSpeed = playbackSpeed)"))
        assertTrue(player.contains("restoredPlaybackSpeed(speedBeforeHold = speedBeforeHold, currentSpeed = playbackSpeed)"))
        assertTrue(player.contains("hudMessage = \"2.0x 快速播放\""))
        assertTrue(player.contains("temporarySpeedRestoreValue"))
        assertTrue(player.contains("DisposableEffect(playbackSource.uri, playbackSource.headers, playerLocked)"))
        assertTrue(gestureLayer.contains("onLongPress: () -> Unit"))
        assertTrue(gestureLayer.contains("onPressEnd: () -> Unit"))
        assertTrue(gestureLayer.contains("detectTapGestures("))
        assertTrue(gestureLayer.contains("detectDragGesturesAfterLongPress("))
        assertTrue(gestureLayer.contains("onDragStart = { onLongPress() }"))
        assertTrue(gestureLayer.contains("onDragEnd = { onPressEnd() }"))
        assertTrue(gestureLayer.contains("onDragCancel = { onPressEnd() }"))
        assertTrue(gestureLayer.contains("change.consume()"))
        assertFalse(player.contains("showOverlay = true\n                    hudMessage = \"2.0x 快速播放\""))
    }

    @Test
    fun fullscreenAndLockBehaviorAreScoped() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()

        assertTrue(player.contains("LockedButtonAutoHideMillis = 5_000L"))
        assertTrue(player.contains("showLockedButton"))
        assertTrue(player.contains("onFullscreenRequest"))
        assertTrue(player.contains("showFullscreenButton"))
        assertTrue(player.contains("isFullscreen"))
        assertTrue(player.contains("showAspectRatioControls"))
        assertTrue(player.contains("if (showAspectRatioControls)"))
        assertTrue(player.contains("FullscreenControl("))
        assertTrue(player.contains("AspectRatioMenu("))
        assertFalse(player.contains("private fun FullscreenButton"))
        assertTrue(detailScreen.contains("playbackResumePositions"))
        assertTrue(detailScreen.contains("lastKnownPlaybackPositions"))
        assertTrue(detailScreen.contains("onPlaybackPositionChange"))
        assertTrue(detailScreen.contains("ActivityInfo.SCREEN_ORIENTATION_USER_PORTRAIT"))
        assertTrue(detailScreen.contains("onChromeVisibleChange"))
        assertTrue(detailScreen.contains("onChromeVisibleChange(!playerFullscreen)"))
        assertTrue(detailScreen.contains("onDispose { onChromeVisibleChange(true) }"))
        assertTrue(detailScreen.contains("isFullscreen = playerFullscreen"))
        assertTrue(detailScreen.contains("showAspectRatioControls = playerFullscreen"))
    }

    @Test
    fun fullscreenControlStaysOnBottomRowTrailingEdge() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        val speedIndex = player.indexOf("PlaybackSpeedMenu(selectedSpeed = playbackSpeed")
        val subtitleIndex = player.indexOf("SubtitleTrackMenu(")
        val audioIndex = player.indexOf("AudioTrackMenu(")
        val aspectIndex = player.indexOf("AspectRatioMenu(selectedAspectRatio = selectedAspectRatio")
        val fullscreenIndex = player.indexOf("FullscreenControl(isFullscreen = isFullscreen")

        assertTrue(speedIndex >= 0)
        assertTrue(subtitleIndex > speedIndex)
        assertTrue(audioIndex > subtitleIndex)
        assertTrue(aspectIndex > audioIndex)
        assertTrue(fullscreenIndex > aspectIndex)
        assertTrue(player.contains("Spacer(Modifier.weight(1f))"))
    }

    @Test
    fun playerControlMenusUseIconOnlyTriggers() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertFalse(player.contains("AssistChip("))
        assertFalse(player.contains("private fun PlayerMenuChip("))
        assertTrue(player.contains("private fun PlayerIconMenuButton("))
        assertTrue(player.contains("contentDescription = \"播放速度\""))
        assertTrue(player.contains("contentDescription = \"字幕\""))
        assertTrue(player.contains("contentDescription = \"音轨\""))
        assertTrue(player.contains("contentDescription = \"画面比例\""))
        assertTrue(player.contains("contentDescription = if (isFullscreen) \"退出全屏\" else \"全屏\""))
    }

    @Test
    fun playerPlayButtonLivesInBottomIconControlsAndProgressIsThin() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()
        val overlayStart = player.indexOf("private fun PlayerControlsOverlay(")
        val overlayEnd = player.indexOf("@Composable\n@OptIn(ExperimentalMaterial3Api::class)\nprivate fun PlayerSeekBar", overlayStart)
        val overlayBlock = player.substring(overlayStart, overlayEnd)
        val trackBlock = player
            .substringAfter("private fun ThinPlayerSliderTrack(")
            .substringBefore("@Composable\nprivate fun PlayPauseControl")
        val playIndex = overlayBlock.indexOf("PlayPauseControl(isPlaying = isPlaying, onTogglePlay = onTogglePlay)")
        val speedIndex = overlayBlock.indexOf("PlaybackSpeedMenu(selectedSpeed = playbackSpeed")

        assertTrue(player.contains("private const val DoubleTapSideZoneFraction = 0.22f"))
        assertTrue(player.contains("private fun PlayPauseControl("))
        assertTrue(playIndex >= 0)
        assertTrue(speedIndex > playIndex)
        assertFalse(overlayBlock.contains(".align(Alignment.Center)"))
        assertFalse(overlayBlock.contains("Color.Black.copy(alpha = 0.36f)"))
        assertTrue(player.contains("private fun ThinPlayerSliderTrack("))
        assertTrue(player.contains("modifier = modifier.height(20.dp)"))
        assertTrue(player.contains("Modifier.offset(y = 4.dp).offset { IntOffset(0, -3) }.size(12.dp).background(Color.White, CircleShape)"))
        assertTrue(trackBlock.contains(".height(2.dp)"))
        assertTrue(trackBlock.contains(".background(trackColor, RoundedCornerShape(50))"))
    }

    @Test
    fun playerDoesNotShowCollapsedBottomProgressBar() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertFalse(player.contains("if (!showOverlay && !playerLocked)"))
        assertFalse(player.contains("PlayerProgressBar("))
        assertFalse(player.contains("private fun PlayerProgressBar"))
        assertFalse(player.contains("LinearProgressIndicator"))
        assertTrue(player.contains("PlayerSeekBar("))
    }

    @Test
    fun playerPausesPlaybackWhenAppLeavesForeground() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertTrue(player.contains("LocalLifecycleOwner.current"))
        assertTrue(player.contains("LifecycleEventObserver"))
        assertTrue(player.contains("Lifecycle.Event.ON_PAUSE"))
        assertTrue(player.contains("Lifecycle.Event.ON_STOP"))
        assertFalse(player.contains("Lifecycle.Event.ON_RESUME"))
        assertTrue(player.contains("controller.pause()"))
        assertTrue(player.contains("isPlaying = false"))
        assertTrue(player.contains("lifecycle.addObserver(observer)"))
        assertTrue(player.contains("lifecycle.removeObserver(observer)"))
    }

    @Test
    fun playerSeekThumbIsAdjustedWithoutMovingThinTrack() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()
        val seekBarBlock = player
            .substringAfter("private fun PlayerSeekBar(")
            .substringBefore("@Composable\nprivate fun ThinPlayerSliderTrack")
        val trackBlock = player
            .substringAfter("private fun ThinPlayerSliderTrack(")
            .substringBefore("@Composable\nprivate fun PlayPauseControl")

        assertTrue(seekBarBlock.contains("thumb = {"))
        assertTrue(seekBarBlock.contains("Modifier.offset(y = 4.dp).offset { IntOffset(0, -3) }.size(12.dp).background(Color.White, CircleShape)"))
        assertTrue(trackBlock.contains("modifier = modifier.height(12.dp).fillMaxWidth()"))
        assertTrue(trackBlock.contains("contentAlignment = Alignment.CenterStart"))
        assertTrue(trackBlock.contains(".background(trackColor, RoundedCornerShape(50))"))
    }

    @Test
    fun detailPageKeepsSinglePlayerInstanceAcrossFullscreenChanges() {
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()

        assertEquals(1, Regex("""MediaTreePlayer\(""").findAll(detailScreen).count())
        assertTrue(detailScreen.contains("val playerModifier = if (playerFullscreen)"))
        assertTrue(detailScreen.contains("modifier = playerModifier"))
        assertFalse(detailScreen.contains("SubtitleSelector("))
        assertFalse(detailScreen.contains("private fun SubtitleSelector"))
        val playerStart = detailScreen.indexOf("MediaTreePlayer(")
        val playerEnd = detailScreen.indexOf("}", playerStart)
        val playerInvocation = detailScreen.substring(playerStart, playerEnd)
        assertFalse(playerInvocation.contains("FilterChip"))
    }

    @Test
    fun mediaTreeDetailSyncsPlaybackProgressWhenLeavingOrSwitchingEpisodes() {
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()

        assertTrue(detailScreen.contains("fun syncPlaybackProgress(providerType: ProviderType, profileId: String, movieId: Int, snapshot: PlaybackPositionSnapshot?)"))
        val memoryStore = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/RemotePlaybackMemoryStore.kt")
            .readText()
        val coordinator = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/RemotePlaybackMemoryCoordinator.kt")
            .readText()
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertTrue(memoryStore.contains("fun remotePlaybackMemoryKey(providerType: ProviderType, profileId: String, movieId: Int): Pair<String, String>"))
        assertTrue(detailScreen.contains("container.remotePlaybackMemoryCoordinator.resumePosition(providerType, profileId, movieId)"))
        assertTrue(detailScreen.contains("container.remotePlaybackMemoryCoordinator.recordProgress("))
        assertTrue(detailScreen.contains("container.remotePlaybackMemoryCoordinator.recordExit("))
        assertTrue(detailScreen.contains("container.remotePlaybackMemoryCoordinator.markFinished(providerType, profileId, movie.id)"))
        assertTrue(coordinator.contains("fun backendPlaybackResumePosition(progress: ProgressDto): Double"))
        assertTrue(coordinator.contains("providerFor(providerType).saveProgress("))
        assertTrue(detailScreen.contains("vm.syncPlaybackProgress(session.activeProviderType, session.activeProfileId, movieId, snapshot)"))
        assertTrue(player.contains("PlaybackMemorySaveIntervalMillis = 5_000L"))
        assertTrue(coordinator.contains("progress.position.takeIf { it.isFinite() && it >= PlaybackMemoryMinimumPositionSeconds }"))
        assertTrue(coordinator.contains("fun shouldSyncBackendProgress(position: Double): Boolean"))
        assertTrue(coordinator.contains("position >= PlaybackMemoryMinimumPositionSeconds"))
        assertTrue(coordinator.contains("if (providerType.syncsPlaybackMemoryToBackend() && shouldSyncBackendProgress(positionSeconds))"))
        assertTrue(player.contains("onProgressUpdate(target, durationSeconds)"))
        assertTrue(detailScreen.contains("bestPlaybackSnapshot("))
        assertTrue(detailScreen.contains("rememberablePlaybackPosition(snapshot.positionSeconds, snapshot.durationSeconds)"))
        assertTrue(detailScreen.contains("if (pos.isFinite() && pos > 0.0)"))
        assertTrue(detailScreen.contains("lastKnownPlaybackPositions[activeMovieId] = snapshot"))
        assertTrue(detailScreen.contains("LaunchedEffect(movieId, providerItemId, session.activeProviderType)"))
        assertTrue(detailScreen.contains("container.registerProviderItemId(session.activeProviderType, movieId, providerItemId)"))
        assertTrue(detailScreen.contains("capturePlaybackPosition(syncToBackend = true)"))
        assertTrue(detailScreen.contains("val onSelectEpisode: (Int) -> Unit = { episodeId ->"))
        assertTrue(detailScreen.contains("capturePlaybackPosition(syncToBackend = true)"))
        assertTrue(detailScreen.contains("DisposableEffect(activeMovieId, session.activeProviderType)"))
        assertTrue(detailScreen.contains("val playingMovieId = activeMovieId"))
        assertTrue(detailScreen.contains("onDispose { capturePlaybackPosition(movieId = playingMovieId, syncToBackend = true) }"))
        assertTrue(detailScreen.contains("stopped = true"))
    }

    @Test
    fun playerResetsVisiblePositionWhenPlaybackSourceReloadsWithResumePoint() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()
        val loadBlock = player.substringAfter("LaunchedEffect(playbackSource.uri, playbackSource.headers)").substringBefore("DisposableEffect(controller, playbackSource, selectedSubtitle)")

        assertTrue(loadBlock.contains("positionSeconds = startPosition.coerceAtLeast(0.0)"))
        assertTrue(loadBlock.contains("seekingPositionSeconds = null"))
        assertTrue(loadBlock.contains("completedReported = false"))
        assertTrue(loadBlock.contains("startPositionSeconds = startPosition"))
    }

    @Test
    fun playerSubtitleMenuSelectionWritesBackToScreenState() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()
        val playerSignature = player
            .substringAfter("fun MediaTreePlayer(")
            .substringBefore(") {")
        val subtitleChangeBlock = player
            .substringAfter("onSubtitleChange = { option ->")
            .substringBefore("onSeekPreview =")
        val playerInvocation = detailScreen
            .substringAfter("MediaTreePlayer(")
            .substringBefore("onPlaybackPositionChange")

        assertTrue(playerSignature.contains("onSubtitleSelected: (Int) -> Unit"))
        assertTrue(subtitleChangeBlock.contains("onSubtitleSelected(-1)"))
        assertTrue(subtitleChangeBlock.contains("onSubtitleSelected(option.index)"))
        assertTrue(playerInvocation.contains("onSubtitleSelected = vm::selectSubtitle"))
    }

    @Test
    fun detailPagePortraitInfoStartsTwelveDpBelowPlayer() {
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()
        val portraitStart = detailScreen.indexOf("private fun PortraitPlayerCard(")
        val portraitEnd = detailScreen.indexOf("@OptIn(ExperimentalAnimationApi::class)", portraitStart + 1)
        val portraitBlock = detailScreen.substring(portraitStart, portraitEnd)

        assertTrue(portraitBlock.contains("Spacer(Modifier.height(12.dp))"))
        assertTrue(portraitBlock.indexOf(".aspectRatio(16f / 9f)") < portraitBlock.indexOf("Spacer(Modifier.height(12.dp))"))
        assertTrue(portraitBlock.indexOf("Spacer(Modifier.height(12.dp))") < portraitBlock.indexOf("LazyColumn("))
    }

    @Test
    fun detailPagePortraitMetadataIsUnframedAndUsesThemeTextColors() {
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()
        val sharedComponents = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/SharedComponents.kt")
            .readText()
        val portraitStart = detailScreen.indexOf("private fun PortraitPlayerCard(")
        val portraitEnd = detailScreen.indexOf("@OptIn(ExperimentalAnimationApi::class)", portraitStart + 1)
        val portraitBlock = detailScreen.substring(portraitStart, portraitEnd)

        assertFalse(portraitBlock.contains("Surface("))
        assertTrue(portraitBlock.contains("LazyColumn("))
        assertTrue(detailScreen.contains("color = MaterialTheme.colorScheme.onBackground"))
        assertTrue(sharedComponents.contains("color = MaterialTheme.colorScheme.onBackground"))
    }

    @Test
    fun detailPageTabsOnlyExposeInfoAndStills() {
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()

        assertTrue(detailScreen.contains("items(listOf(\"信息\", \"剧照\"))"))
        assertTrue(detailScreen.contains("var selectedDetailTab by remember(movie.id)"))
        assertTrue(detailScreen.contains("selectedTab = selectedDetailTab"))
        assertTrue(detailScreen.contains("if (selectedDetailTab == \"剧照\")"))
        assertFalse(detailScreen.contains("items(listOf(\"信息\", \"剧照\", \"演员\", \"相关单集\"))"))
        assertFalse(detailScreen.contains("SectionHeader(\"精彩剧照\")"))
    }

    @Test
    fun detailPageStillsCanOpenImageViewer() {
        val detailScreen = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/DetailScreen.kt")
            .readText()
        val thumbnailStart = detailScreen.indexOf("private fun ThumbnailStrip(")
        val thumbnailEnd = detailScreen.indexOf("@Composable", thumbnailStart + 1)
        val thumbnailBlock = detailScreen.substring(thumbnailStart, thumbnailEnd)

        assertTrue(thumbnailBlock.contains("selectedThumbnailUrl"))
        assertTrue(thumbnailBlock.contains("shapeAwareClickable"))
        assertTrue(thumbnailBlock.contains("StillImageViewer("))
        assertTrue(detailScreen.contains("private fun StillImageViewer("))
        assertTrue(detailScreen.contains("contentDescription = \"关闭剧照预览\""))
    }

    @Test
    fun playerLoadEffectIgnoresSubtitleTrackListChanges() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertTrue(player.contains("LaunchedEffect(playbackSource.uri, playbackSource.headers)"))
        assertFalse(player.contains("LaunchedEffect(playbackSource)"))
    }

    @Test
    fun playbackProgressIsFiniteAndClamped() {
        assertEquals(0f, playbackProgress(positionSeconds = Double.NaN, durationSeconds = 100.0))
        assertEquals(0f, playbackProgress(positionSeconds = 30.0, durationSeconds = Double.NaN))
        assertEquals(0f, playbackProgress(positionSeconds = 0.0, durationSeconds = 0.0))
        assertEquals(0.5f, playbackProgress(positionSeconds = 30.0, durationSeconds = 60.0))
        assertEquals(1f, playbackProgress(positionSeconds = 90.0, durationSeconds = 60.0))
        assertEquals(0.25f, playbackProgress(positionSeconds = 30.0, durationSeconds = 0.0, percentPosition = 25.0))
        assertEquals(1f, playbackProgress(positionSeconds = 30.0, durationSeconds = 0.0, percentPosition = 120.0))
        assertEquals(0f, playbackProgress(positionSeconds = 30.0, durationSeconds = 0.0, percentPosition = Double.NaN))
        assertEquals(25.0, playbackPercent(positionSeconds = 30.0, durationSeconds = 120.0), 0.001)
        assertEquals(25.0, playbackPercent(positionSeconds = 0.0, durationSeconds = 0.0, fallbackPercent = 25.0), 0.001)
    }

    @Test
    fun horizontalSeekGestureUsesLowSensitivity() {
        assertEquals(45.0, horizontalSeekDeltaSeconds(dragAmountPx = 500f, widthPx = 1000), 0.001)
        assertEquals(-45.0, horizontalSeekDeltaSeconds(dragAmountPx = -500f, widthPx = 1000), 0.001)
        assertEquals(0.0, horizontalSeekDeltaSeconds(dragAmountPx = 500f, widthPx = 0), 0.001)
    }

    @Test
    fun playerUiKeepsNativeSurfaceOutOfLayerTransforms() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertTrue(player.contains("MpvPlayerView"))
        assertFalse(player.contains("import androidx.compose.ui.graphics.graphicsLayer"))
        assertFalse(player.contains(".graphicsLayer"))
    }

    @Test
    fun playbackKeepsScreenAwakeOnlyWhilePlayerIsComposed() {
        val player = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/player/MediaTreePlayer.kt")
            .readText()

        assertTrue(player.contains("WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON"))
        assertTrue(player.contains("window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"))
        assertTrue(player.contains("window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)"))
    }
}

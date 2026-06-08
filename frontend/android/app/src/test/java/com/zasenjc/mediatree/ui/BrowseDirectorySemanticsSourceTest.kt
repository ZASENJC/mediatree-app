package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class BrowseDirectorySemanticsSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    private val browseSource: String
        get() = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/BrowseScreen.kt")
            .readText()

    private val appSource: String
        get() = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()

    private val homeSource: String
        get() = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()

    private val thumbnailCacheSource: String
        get() = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/MountedVideoThumbnailCache.kt")
            .readText()

    @Test
    fun browseFolderTitleUsesOriginalFolderNameOnly() {
        val source = browseSource
        val titleBlock = source
            .substringAfter("private fun FolderNodeDto.browseTitle(): String =")
            .substringBefore("private fun FolderNodeDto.detailRoute")

        assertTrue(titleBlock.trim().startsWith("name"))
        assertFalse(titleBlock.contains("displayTitle"))
        assertFalse(titleBlock.contains("substringAfterLast"))
    }

    @Test
    fun browseScreenDoesNotRenderMountedRootBreadcrumbOrMediaGroupHeaders() {
        val screenBlock = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore("@Composable\nprivate fun DesignFolderRow")

        assertFalse(screenBlock.contains("BreadcrumbLine("))
        assertFalse(screenBlock.contains("媒体库组目录"))
        assertFalse(screenBlock.contains("影片 · 共"))
        assertFalse(screenBlock.contains("搜索影片"))
    }

    @Test
    fun browseScreenRemovesListModeAndDefaultsToCompact() {
        assertFalse(browseSource.contains("ViewList"))
        assertFalse(browseSource.contains("\"list\", \"列表\""))
        assertTrue(appSource.contains("var browseViewMode by rememberSaveable { mutableStateOf(\"compact\") }"))
    }

    @Test
    fun browseViewModeIsHoistedSoReturningKeepsSelection() {
        val browseSignature = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore(") {")
        val appBrowseCall = appSource
            .substringAfter("\"browse\" -> BrowseScreen(")
            .substringBefore(")")

        assertTrue(browseSignature.contains("viewMode: String"))
        assertTrue(browseSignature.contains("onViewModeChange: (String) -> Unit"))
        assertFalse(browseSource.contains("var viewMode by remember { mutableStateOf(\"compact\") }"))
        assertTrue(appBrowseCall.contains("viewMode = browseViewMode"))
        assertTrue(appBrowseCall.contains("onViewModeChange = { browseViewMode = it }"))
    }

    @Test
    fun browseFolderNavigationRemembersScrollPositionPerDirectory() {
        val screenBlock = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore("@Composable\nprivate fun DesignFolderRow")
        val browseSignature = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore(") {")
        val mainShellBlock = appSource
            .substringAfter("private fun MainShell")
            .substringBefore("fun detailMovieIdFromUri")
        val scrollKeyBlock = browseSource
            .substringAfter("private fun BrowseContentSnapshot.scrollMemoryKey")
            .substringBefore("private fun String.mountedLibrarySourceId")

        assertTrue(browseSource.contains("data class BrowseScrollPosition"))
        assertTrue(mainShellBlock.contains("val browseScrollPositions = remember { mutableMapOf<String, BrowseScrollPosition>() }"))
        assertTrue(browseSignature.contains("browseScrollPositions: MutableMap<String, BrowseScrollPosition>"))
        assertFalse(screenBlock.contains("val browseScrollPositions = remember { mutableMapOf<String, BrowseScrollPosition>() }"))
        assertTrue(screenBlock.contains("val scrollKey = snapshot.scrollMemoryKey("))
        assertTrue(screenBlock.contains("val rememberedScroll = browseScrollPositions[scrollKey]"))
        assertTrue(screenBlock.contains("initialFirstVisibleItemIndex = rememberedScroll?.firstVisibleItemIndex ?: 0"))
        assertTrue(screenBlock.contains("initialFirstVisibleItemScrollOffset = rememberedScroll?.firstVisibleItemScrollOffset ?: 0"))
        assertTrue(screenBlock.contains("DisposableEffect(scrollKey, snapshotListState)"))
        assertTrue(screenBlock.contains("browseScrollPositions[scrollKey] = snapshotListState.toBrowseScrollPosition()"))
        assertTrue(screenBlock.contains("snapshotFlow { snapshotListState.toBrowseScrollPosition() }"))
        assertTrue(scrollKeyBlock.contains("providerType.name"))
        assertTrue(scrollKeyBlock.contains("activeProfileId"))
        assertTrue(scrollKeyBlock.contains("activeLibrary"))
        assertTrue(scrollKeyBlock.contains("currentFolder"))
        assertTrue(scrollKeyBlock.contains("viewMode"))
        assertTrue(scrollKeyBlock.contains("sortMode"))
        assertTrue(scrollKeyBlock.contains("query.trim()"))
        assertTrue(scrollKeyBlock.contains("recursiveVideosOnly"))
    }

    @Test
    fun browseScrollMemorySurvivesOpeningVideoAndReturning() {
        val imports = appSource.substringBefore("private val Md3StandardEasing")
        val mainShellBlock = appSource
            .substringAfter("private fun MainShell")
            .substringBefore("fun detailMovieIdFromUri")
        val appBrowseCall = appSource
            .substringAfter("\"browse\" -> BrowseScreen(")
            .substringBefore(")")
        val homeSignature = homeSource
            .substringAfter("fun HomeScreen(")
            .substringBefore(") {")
        val homeBrowseWrapperCall = homeSource
            .substringAfter("BrowseScreen(")
            .substringBefore("return")

        assertTrue(imports.contains("import com.zasenjc.mediatree.ui.screens.BrowseScrollPosition"))
        assertTrue(mainShellBlock.contains("val browseScrollPositions = remember { mutableMapOf<String, BrowseScrollPosition>() }"))
        assertTrue(appBrowseCall.contains("browseScrollPositions = browseScrollPositions"))
        assertTrue(homeSignature.contains("browseScrollPositions: MutableMap<String, BrowseScrollPosition>"))
        assertTrue(homeBrowseWrapperCall.contains("browseScrollPositions = browseScrollPositions"))
        assertFalse(homeSource.contains("remember { mutableMapOf<String, BrowseScrollPosition>() }"))
    }

    @Test
    fun activeLibraryChangeResetsBrowseFolderToRoot() {
        val resetBlock = appSource
            .substringAfter("LaunchedEffect(session.activeProviderType, session.activeLibrary)")
            .substringBefore("fun navigateTopDestination")

        assertTrue(resetBlock.contains("browseFolder = \"\""))
        assertFalse(resetBlock.contains("browseViewMode = \"compact\""))
    }

    @Test
    fun compactRowsKeepRoundedShape() {
        val compactBrowserRow = browseSource
            .substringAfter("private fun CompactBrowserRow")
            .substringBefore("@Composable\nprivate fun BreadcrumbLine")

        assertTrue(compactBrowserRow.contains("shape = RoundedCornerShape(12.dp)"))
    }

    @Test
    fun smbMountedBrowseDoesNotPrefetchChildFolderCounts() {
        val loadSmbBlock = browseSource
            .substringAfter("private suspend fun loadSmb")
            .substringBefore("private suspend fun loadWebDav")

        assertTrue(loadSmbBlock.contains("container.smbClient.list(source, folder)"))
        assertFalse(loadSmbBlock.contains("smbDirectoryChildCount"))
        assertFalse(loadSmbBlock.contains("container.smbClient.list(source, entry.path)"))
        assertFalse(loadSmbBlock.contains("movieCount = 1"))
        assertFalse(browseSource.contains("private suspend fun smbDirectoryChildCount"))
    }

    @Test
    fun smbMountedBrowseKeepsImagesAsOpenableItems() {
        val loadSmbBlock = browseSource
            .substringAfter("private suspend fun loadSmb")
            .substringBefore("private suspend fun loadWebDav")
        val openRouteBlock = browseSource
            .substringAfter("private fun MovieDto.openRoute(): String =")
            .substringBefore("private fun MovieDto.isMountedLibraryItem")
        val thumbnailBlock = browseSource
            .substringAfter("private fun MountedVideoThumbnail(")
            .substringBefore("@Composable\nprivate fun BrowserListRow")

        assertTrue(loadSmbBlock.contains("it.isPlayableVideo || it.isViewableImage"))
        assertTrue(loadSmbBlock.contains("entry.toMountedMovieDto(source)"))
        assertFalse(loadSmbBlock.contains("entries.filter { it.isPlayableVideo }"))
        assertTrue(openRouteBlock.contains("if (isMountedImageItem()) \"smbImage\" else \"smbPlayer\""))
        assertTrue(browseSource.contains("scraperSource = MountedImageItemMarker.takeIf { isViewableImage }"))
        assertTrue(browseSource.contains("scraperSource == MountedImageItemMarker"))
        assertTrue(thumbnailBlock.contains("if (movie.isMountedImageItem()) return@LaunchedEffect"))
        assertTrue(thumbnailBlock.contains("Icons.Default.Image"))
    }

    @Test
    fun browseSupportsWebDavMountedLibrarySource() {
        val loadWebDavBlock = browseSource
            .substringAfter("private suspend fun loadWebDav")
            .substringBefore("fun loadMore")

        assertTrue(browseSource.contains("webDavLibrarySourceId"))
        assertTrue(browseSource.contains("private suspend fun loadWebDav"))
        assertTrue(browseSource.contains("container.webDavClient.list(source, folder)"))
        assertFalse(loadWebDavBlock.contains("webDavDirectoryChildCount"))
        assertFalse(loadWebDavBlock.contains("container.webDavClient.list(source, entry.path)"))
        assertFalse(browseSource.contains("private suspend fun webDavDirectoryChildCount"))
        assertTrue(browseSource.contains("webDavLibraryPath(sourceId)"))
        assertTrue(browseSource.contains("\"webdavImage\" else \"webdavPlayer\""))
    }

    @Test
    fun homeMediaFeedMountedFoldersOpenRecursiveBrowseVideoMode() {
        val openLibraryItemBlock = homeSource
            .substringAfter("fun openLibraryItem")
            .substringBefore("fun HomeScreen(")
        val handleNavigateBlock = appSource
            .substringAfter("fun handleAppNavigate(route: String)")
            .substringBefore("BackHandler(")

        assertTrue(openLibraryItemBlock.contains("browse?folder="))
        assertTrue(openLibraryItemBlock.contains("recursiveVideos=true"))
        assertFalse(openLibraryItemBlock.contains("onNavigate(\"smb/"))
        assertFalse(openLibraryItemBlock.contains("onNavigate(\"webdav/"))
        assertTrue(handleNavigateBlock.contains("browseRecursiveVideos"))
        assertTrue(handleNavigateBlock.contains("recursiveVideos"))
    }

    @Test
    fun mountedRecursiveBrowseModeLoadsDescendantVideosOnly() {
        val browseSignature = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore(") {")
        val loadSmbBlock = browseSource
            .substringAfter("private suspend fun loadSmb")
            .substringBefore("private suspend fun loadWebDav")
        val loadWebDavBlock = browseSource
            .substringAfter("private suspend fun loadWebDav")
            .substringBefore("fun loadMore")
        val appBrowseCall = appSource
            .substringAfter("\"browse\" -> BrowseScreen(")
            .substringBefore(")")

        assertTrue(browseSignature.contains("recursiveVideosOnly: Boolean"))
        assertTrue(appBrowseCall.contains("recursiveVideosOnly = browseRecursiveVideos"))
        assertTrue(loadSmbBlock.contains("recursiveVideosOnly"))
        assertTrue(loadSmbBlock.contains("collectSmbVideoEntries(source, folder)"))
        assertTrue(loadSmbBlock.contains("folders = if (recursiveVideosOnly || searching) emptyList()"))
        assertTrue(loadWebDavBlock.contains("recursiveVideosOnly"))
        assertTrue(loadWebDavBlock.contains("collectWebDavVideoEntries(source, folder)"))
        assertTrue(loadWebDavBlock.contains("folders = if (recursiveVideosOnly || searching) emptyList()"))
        assertTrue(browseSource.contains("container.smbClient.list(source, currentFolder)"))
        assertTrue(browseSource.contains("container.webDavClient.list(source, currentFolder)"))
        assertTrue(browseSource.contains("MountedVideoPosterCard("))
        assertTrue(browseSource.contains("MountedVideoThumbnail("))
    }

    @Test
    fun remoteLoadMoreAdvancesPageOnlyAfterRequestSucceeds() {
        val loadMoreBlock = browseSource
            .substringAfter("fun loadMore")
            .substringBefore("@OptIn(ExperimentalMaterial3Api::class)")
        val launchStart = loadMoreBlock.indexOf("viewModelScope.launch")
        val successUpdate = loadMoreBlock.indexOf("it.copy(", loadMoreBlock.indexOf("val mergedMovies"))
        val pageUpdate = loadMoreBlock.indexOf("page = next")
        val catchIndex = loadMoreBlock.indexOf("catch")

        assertFalse(loadMoreBlock.substringBefore("viewModelScope.launch").contains("page = next"))
        assertTrue(pageUpdate > successUpdate)
        assertTrue(pageUpdate < catchIndex)
        assertTrue(launchStart >= 0)
    }

    @Test
    fun mountedFolderMetaAvoidsUnloadedChildCounts() {
        val folderMetaBlock = browseSource
            .substringAfter("private fun FolderNodeDto.folderMeta(): String =")
            .substringBefore("private fun MovieDto.browseTitle")

        assertTrue(folderMetaBlock.contains("mediaRoot?.mountedLibrarySourceId() != null"))
        assertTrue(folderMetaBlock.contains("\"文件夹\""))
    }

    @Test
    fun mountedVideosUseRealFrameThumbnailsInPosterAndIconModes() {
        val screenBlock = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore("@Composable\nprivate fun DesignFolderRow")
        val thumbnailBlock = browseSource
            .substringAfter("private fun MountedVideoThumbnail")
            .substringBefore("@Composable\nprivate fun BrowserListRow")
        val mediaThumbnailBlock = browseSource
            .substringAfter("private fun MountedMediaThumbnail(")
            .substringBefore("@Composable\nprivate fun MountedImageThumbnail")
        val iconMovieRowBlock = browseSource
            .substringAfter("private fun IconMovieRow")
            .substringBefore("@Composable\nprivate fun IconTile")
        val mountedIconTileBlock = browseSource
            .substringAfter("private fun MountedVideoIconTile")
            .substringBefore("@Composable\nprivate fun IconTile")
        val compactMovieRowBlock = browseSource
            .substringAfter("private fun CompactMovieRow")
            .substringBefore("@Composable\nprivate fun CompactBrowserRow")

        assertTrue(screenBlock.contains("movie.isMountedLibraryItem()"))
        assertTrue(screenBlock.contains("MountedVideoPosterCard("))
        assertTrue(screenBlock.contains("CompactMovieRow("))
        assertTrue(iconMovieRowBlock.contains("MountedVideoIconTile("))
        assertTrue(mediaThumbnailBlock.contains("MountedVideoThumbnail("))
        assertTrue(mountedIconTileBlock.contains("showPlayIcon = false"))
        assertTrue(mountedIconTileBlock.contains("maxLines = 1"))
        assertFalse(mountedIconTileBlock.contains("movie.iconMovieMeta()"))
        assertFalse(mountedIconTileBlock.contains("ElevatedCard("))
        assertTrue(compactMovieRowBlock.contains("MountedMediaThumbnail("))
        assertTrue(compactMovieRowBlock.contains("framedIcon = !movie.isMountedLibraryItem()"))
        assertTrue(browseSource.contains("private fun MountedVideoThumbnail"))
        assertTrue(thumbnailCacheSource.contains("MediaMetadataRetriever"))
        assertTrue(thumbnailCacheSource.contains("setDataSource(source.uri, source.headers)"))
        assertTrue(thumbnailCacheSource.contains("getScaledFrameAtTime"))
        assertTrue(thumbnailCacheSource.contains("data class MountedVideoThumbnailSpec"))
        assertTrue(browseSource.contains("MountedPosterVideoFrameWidth = 120"))
        assertTrue(browseSource.contains("MountedPosterVideoFrameHeight = 180"))
        assertTrue(browseSource.contains("MountedLandscapeVideoFrameWidth = 128"))
        assertTrue(browseSource.contains("MountedLandscapeVideoFrameHeight = 72"))
        assertFalse(browseSource.contains("MountedVideoFrameWidth = 240"))
        assertFalse(browseSource.contains("MountedVideoFrameHeight = 360"))
        assertTrue(thumbnailCacheSource.contains("limitedParallelism(MountedVideoFrameParallelism)"))
        assertTrue(thumbnailCacheSource.contains("private const val MountedVideoFrameParallelism = 2"))
        assertTrue(thumbnailCacheSource.contains("Bitmap.Config.RGB_565"))
        assertTrue(thumbnailBlock.contains("filterQuality = FilterQuality.Low"))
        assertTrue(thumbnailCacheSource.contains("MountedVideoThumbnailMemoryCache"))
        assertTrue(thumbnailCacheSource.contains("MountedVideoThumbnailDiskCache"))
        assertTrue(thumbnailCacheSource.contains("private const val MountedVideoThumbnailCacheTtlMillis = 7 * 24 * 60 * 60 * 1000L"))
        assertTrue(thumbnailCacheSource.contains("context.cacheDir.resolve(\"mounted_video_thumbnails\")"))
        assertTrue(thumbnailCacheSource.contains("BitmapFactory.decodeFile"))
        assertTrue(thumbnailCacheSource.contains("Bitmap.CompressFormat.JPEG"))
        assertTrue(thumbnailCacheSource.contains("diskCache.getCached(cacheKey)"))
        assertTrue(thumbnailCacheSource.contains("diskCache.putCached(cacheKey, frame)"))
        assertTrue(thumbnailCacheSource.contains("runCatching { diskCache.putCached(cacheKey, frame) }"))
        assertTrue(thumbnailCacheSource.contains("pendingRequests.forEach { request -> request.cancel() }"))
        assertTrue(thumbnailCacheSource.contains("clear()"))
        assertTrue(thumbnailCacheSource.contains("sourceInfo.onClose?.invoke()"))
        assertTrue(thumbnailCacheSource.contains("container.smbRangeProxy.playbackSource"))
        assertTrue(thumbnailCacheSource.contains("PlaybackSource.webDav"))
        assertFalse(browseSource.contains("modifier = Modifier.align(Alignment.BottomStart).padding(6.dp)"))
    }

    @Test
    fun mountedImagesUseRealThumbnailsWithViewportScheduling() {
        val screenBlock = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore("@Composable\nprivate fun DesignFolderRow")
        val mediaThumbnailBlock = browseSource
            .substringAfter("private fun MountedMediaThumbnail(")
            .substringBefore("@Composable\nprivate fun MountedImageThumbnail")
        val imageThumbnailBlock = browseSource
            .substringAfter("private fun MountedImageThumbnail(")
            .substringBefore("@Composable\nprivate fun MountedVideoThumbnail")

        assertTrue(screenBlock.contains("val mountedImageThumbnailSourceLoader"))
        assertTrue(mediaThumbnailBlock.contains("movie.isMountedImageItem()"))
        assertTrue(mediaThumbnailBlock.contains("MountedImageThumbnail("))
        assertTrue(imageThumbnailBlock.contains("thumbnailViewportScheduler.awaitVisible(keyToWait)"))
        assertTrue(imageThumbnailBlock.contains("delay(MountedThumbnailVisibleDebounceMillis)"))
        assertTrue(imageThumbnailBlock.contains("thumbnailSourceLoader(source, movie)"))
        assertTrue(imageThumbnailBlock.contains("ImageRequest.Builder(context)"))
        assertTrue(imageThumbnailBlock.contains(".size(spec.width, spec.height)"))
        assertTrue(imageThumbnailBlock.contains("sourceInfo.headers.forEach { (name, value) -> addHeader(name, value) }"))
        assertTrue(imageThumbnailBlock.contains("AsyncImage("))
        assertTrue(imageThumbnailBlock.contains("imageSource?.onClose?.invoke()"))
        assertTrue(browseSource.contains("container.smbRangeProxy.playbackSource(source = resolvedSource, path = movie.path)"))
        assertTrue(browseSource.contains("WebDavClient.buildResourceUrl(resolvedSource, movie.path)"))
    }

    @Test
    fun mountedVideoThumbnailsUseViewportPriorityScheduling() {
        val screenBlock = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore("@Composable\nprivate fun DesignFolderRow")
        val thumbnailSignature = browseSource
            .substringAfter("private fun MountedVideoThumbnail(")
            .substringBefore(") {")
        val thumbnailBlock = browseSource
            .substringAfter("private fun MountedVideoThumbnail(")
            .substringBefore("@Composable\nprivate fun BrowserListRow")

        assertTrue(screenBlock.contains("val thumbnailViewportScheduler = rememberMountedThumbnailViewportScheduler(snapshotListState)"))
        assertTrue(screenBlock.contains("thumbnailViewportScheduler = thumbnailViewportScheduler"))
        assertTrue(browseSource.contains("private fun rememberMountedThumbnailViewportScheduler"))
        assertTrue(browseSource.contains("snapshotFlow { listState.layoutInfo.visibleItemsInfo.mapNotNull"))
        assertTrue(browseSource.contains("visibleKeys.indexOf(key)"))
        assertTrue(browseSource.contains("mountedThumbnailKey("))
        assertTrue(thumbnailSignature.contains("thumbnailViewportScheduler: MountedThumbnailViewportScheduler"))
        assertTrue(thumbnailBlock.contains("thumbnailViewportScheduler.awaitVisible(keyToWait)"))
        assertTrue(thumbnailBlock.contains("delay(MountedThumbnailVisibleDebounceMillis)"))
        assertTrue(browseSource.contains("private const val MountedThumbnailVisibleDebounceMillis = 120L"))
    }

    @Test
    fun browseSearchReloadsCurrentDirectoryRecursivelyInsteadOfFilteringVisibleRows() {
        val uiBlock = browseSource
            .substringAfter("fun BrowseScreen(")
            .substringBefore("@Composable\nprivate fun DesignFolderRow")
        val loadSignature = browseSource
            .substringAfter("fun load(")
            .substringBefore(") {")
        val loadSmbBlock = browseSource
            .substringAfter("private suspend fun loadSmb")
            .substringBefore("private suspend fun collectSmbVideoEntries")
        val loadWebDavBlock = browseSource
            .substringAfter("private suspend fun loadWebDav")
            .substringBefore("fun loadMore")

        assertTrue(loadSignature.contains("searchQuery: String = \"\""))
        assertTrue(uiBlock.contains("vm.load("))
        assertTrue(uiBlock.contains("searchQuery = request"))
        assertTrue(loadSmbBlock.contains("val searching = searchQuery.trim().isNotBlank()"))
        assertTrue(loadSmbBlock.contains("recursiveVideosOnly || searching"))
        assertTrue(loadSmbBlock.contains(".filterMoviesByQuery(searchQuery)"))
        assertTrue(loadWebDavBlock.contains("val searching = searchQuery.trim().isNotBlank()"))
        assertTrue(loadWebDavBlock.contains("recursiveVideosOnly || searching"))
        assertTrue(loadWebDavBlock.contains(".filterMoviesByQuery(searchQuery)"))
        assertTrue(uiBlock.contains("val filteredFolders = state.folders"))
        assertTrue(uiBlock.contains("val filteredMovies = state.movies"))
        assertFalse(uiBlock.contains("state.folders.filterFoldersByQuery(query)"))
        assertFalse(uiBlock.contains("state.movies.filterMoviesByQuery(query)"))
    }

    @Test
    fun browseSortUsesProviderSpecificKeysAndMountedMetadata() {
        val loadRemoteBlock = browseSource
            .substringAfter("val provider = container.mediaProviderFor(providerType)")
            .substringBefore("} catch (e: Throwable)")
        val loadMoreBlock = browseSource
            .substringAfter("fun loadMore")
            .substringBefore("private suspend fun collectWebDavVideoEntries")
        val loadSmbBlock = browseSource
            .substringAfter("private suspend fun loadSmb")
            .substringBefore("private suspend fun collectSmbVideoEntries")
        val loadWebDavBlock = browseSource
            .substringAfter("private suspend fun loadWebDav")
            .substringBefore("fun loadMore")
        val smbMappingBlock = browseSource
            .substringAfter("private fun com.zasenjc.mediatree.data.SmbEntry.toMountedMovieDto")
            .substringBefore("private fun com.zasenjc.mediatree.data.WebDavEntry.toMountedMovieDto")
        val webDavMappingBlock = browseSource
            .substringAfter("private fun com.zasenjc.mediatree.data.WebDavEntry.toMountedMovieDto")
            .substringBefore("private fun List<MovieDto>.sortedMoviesForBrowse")

        assertTrue(loadRemoteBlock.contains("sort = sort.toProviderBrowseMovieSort(providerType)"))
        assertTrue(loadRemoteBlock.contains("movies = response?.movies.orEmpty().sortedMoviesForBrowse(sort)"))
        assertTrue(loadMoreBlock.contains("s.sortMode.toProviderBrowseMovieSort(providerType)"))
        assertTrue(loadMoreBlock.contains("val mergedMovies = (it.movies + response.movies).sortedMoviesForBrowse(s.sortMode)"))
        assertTrue(loadMoreBlock.contains("movies = mergedMovies"))
        assertTrue(browseSource.contains("private fun String.toProviderBrowseMovieSort(providerType: ProviderType): String"))
        assertTrue(browseSource.contains("ProviderType.MediaTree -> toMediaTreeBrowseMovieSort()"))
        assertTrue(browseSource.contains("ProviderType.Jellyfin, ProviderType.Emby -> toJellyfinBrowseMovieSort()"))
        assertFalse(browseSource.contains("private fun String.toApiMovieSort()"))
        assertTrue(loadSmbBlock.contains("entry.toMountedMovieDto(source)"))
        assertTrue(loadWebDavBlock.contains("entry.toMountedMovieDto(source)"))
        assertTrue(smbMappingBlock.contains("updatedAt = modified.takeIf { it > 0L }?.toString()"))
        assertTrue(smbMappingBlock.contains("createdAt = modified.takeIf { it > 0L }?.toString()"))
        assertTrue(webDavMappingBlock.contains("updatedAt = modified.ifBlank { null }"))
        assertTrue(webDavMappingBlock.contains("createdAt = modified.ifBlank { null }"))
    }

    @Test
    fun browserRowsExposeThemeContentColorForDarkMode() {
        val source = browseSource
        val designFolderRow = source.substringAfter("private fun DesignFolderRow").substringBefore("@Composable\nprivate fun PosterFolderRow")
        val folderPosterCard = source.substringAfter("private fun FolderPosterCard").substringBefore("@Composable\nprivate fun IconFolderRow")
        val iconTile = source.substringAfter("private fun IconTile").substringBefore("@Composable\nprivate fun FolderListRow")
        val browserListRow = source.substringAfter("private fun BrowserListRow").substringBefore("@Composable\nprivate fun CompactFolderRow")
        val compactBrowserRow = source.substringAfter("private fun CompactBrowserRow").substringBefore("@Composable\nprivate fun BreadcrumbLine")

        assertTrue(designFolderRow.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(folderPosterCard.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(iconTile.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(browserListRow.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(compactBrowserRow.contains("contentColor = MaterialTheme.colorScheme.onSurface"))
        assertTrue(folderPosterCard.contains("color = MaterialTheme.colorScheme.onSurface"))
        assertTrue(iconTile.contains("color = MaterialTheme.colorScheme.onSurface"))
        assertTrue(browserListRow.contains("color = MaterialTheme.colorScheme.onSurface"))
        assertTrue(compactBrowserRow.contains("color = MaterialTheme.colorScheme.onSurface"))
    }
}

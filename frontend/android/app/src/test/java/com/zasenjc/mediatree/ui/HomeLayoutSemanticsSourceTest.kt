package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HomeLayoutSemanticsSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun mediaFeedUsesGroupedMediaPostersAndDirectoryFirstReusesBrowseScreen() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val mediaFeedBlock = source
            .substringAfter("} else if (showMediaFeed) {")
            .substringBefore("AnimatedVisibility(")
        val directoryFirstBlock = source
            .substringAfter("if (homeLayout == HomeLayoutPreference.DirectoryFirst) {")
            .substringBefore("val vm: HomeViewModel")

        assertTrue(mediaFeedBlock.contains("state.libraryItems"))
        assertTrue(mediaFeedBlock.contains("HomeMediaPosterCard"))
        assertTrue(mediaFeedBlock.contains("vm.openLibraryItem"))
        assertFalse(mediaFeedBlock.contains("state.feedMovies.isEmpty()"))

        assertTrue(directoryFirstBlock.contains("BrowseScreen("))
        assertTrue(directoryFirstBlock.contains("initialFolder = \"\""))
        assertTrue(directoryFirstBlock.contains("viewMode = browseViewMode"))
        assertTrue(directoryFirstBlock.contains("onViewModeChange = onBrowseViewModeChange"))
        assertTrue(source.contains("webDavLibrarySourceId"))
        assertFalse(source.contains("HomeDirectoryRow("))
    }

    @Test
    fun settingsDoesNotExplainHomeLayoutModesInline() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertFalse(source.contains("媒体流按剧集/电影显示单海报"))
        assertFalse(source.contains("目录优先直接显示媒体库或 SMB 的源文件夹结构"))
    }

    @Test
    fun homeMountedLibrariesSupportSortRefreshAndSearch() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val actionsBlock = source
            .substringAfter("actions = {")
            .substringBefore("HomeSearchOverlay(")
        val searchBlock = source
            .substringAfter("private fun HomeSearchOverlay")
            .substringBefore("@Composable\nprivate fun HomeSearchResultRow")
        val mountedSearchBlock = source
            .substringAfter("private suspend fun searchMountedLibrary")
            .substringBefore("private fun SmbEntry.toFolderNode")

        assertTrue(source.contains("private fun Session.canLoadHomeContent()"))
        assertTrue(actionsBlock.contains("session.canLoadHomeContent()"))
        assertTrue(actionsBlock.contains("vm.load(session.activeProviderType, session.activeProfileId, session.activeLibrary, key)"))
        assertFalse(actionsBlock.contains("if (shouldLoadRemoteContent(session))"))

        assertTrue(searchBlock.contains("session.activeLibrary.smbLibrarySourceId()"))
        assertTrue(searchBlock.contains("session.activeLibrary.webDavLibrarySourceId()"))
        assertTrue(searchBlock.contains("searchMountedLibrary"))
        assertTrue(mountedSearchBlock.contains("collectSmbMountedLibraryVideos(source, container)"))
        assertTrue(mountedSearchBlock.contains("collectWebDavMountedLibraryVideos(source, container)"))
        assertTrue(source.contains("container.smbClient.list(source, currentFolder)"))
        assertTrue(source.contains("container.webDavClient.list(source, currentFolder)"))
        assertFalse(mountedSearchBlock.contains("container.smbClient.list(source)\n"))
        assertFalse(mountedSearchBlock.contains("container.webDavClient.list(source)\n"))
        assertTrue(searchBlock.contains("mountedSearchResults"))
        assertTrue(searchBlock.contains("movie.openRoute()"))
    }

    @Test
    fun homeRefreshUsesPullToRefreshInsteadOfTopMenuRefresh() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val appShell = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()
        val screenBlock = source
            .substringAfter("fun HomeScreen(")
            .substringBefore("@Composable\nprivate fun HomeSectionHeader")
        val actionsBlock = source
            .substringAfter("actions = {")
            .substringBefore("HomeSearchOverlay(")

        assertTrue(appShell.contains("val pageActive = currentRoute == \"main\" && page == pagerState.currentPage"))
        assertTrue(source.contains("import androidx.compose.material3.pulltorefresh.PullToRefreshBox"))
        assertTrue(screenBlock.contains("PullToRefreshBox("))
        assertTrue(screenBlock.contains("isRefreshing = state.refreshing"))
        assertTrue(screenBlock.contains("onRefresh = {"))
        assertTrue(screenBlock.contains("vm.load(session.activeProviderType, session.activeProfileId, session.activeLibrary)"))
        assertFalse(screenBlock.contains("var showMore"))
        assertFalse(actionsBlock.contains("MoreVert"))
        assertFalse(actionsBlock.contains("Text(\"刷新\")"))
        assertFalse(actionsBlock.contains("Icons.Default.Refresh"))
    }

    @Test
    fun homeLoadAvoidsUnusedMovieFeedAndSearchKeepsProviderSpecificSort() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val loadBlock = source
            .substringAfter("fun load(providerType: ProviderType")
            .substringBefore("private suspend fun loadSmbLibrary")
        val searchBlock = source
            .substringAfter("private fun HomeSearchOverlay")
            .substringBefore("@Composable\nprivate fun HomeSearchResultRow")

        assertTrue(loadBlock.contains("val roots = if (activeLibrary.isBlank())"))
        assertTrue(loadBlock.contains("provider.mediaRoots().items"))
        assertFalse(loadBlock.contains("val roots = provider.mediaRoots().items"))
        assertTrue(loadBlock.contains("loadRemoteHome(providerType, profileId, activeLibrary, sort)"))
        assertTrue(loadBlock.contains("refreshHomeLibraryStage("))
        assertTrue(loadBlock.contains("refreshHomeRecentStage("))
        assertTrue(loadBlock.contains("provider.folders(mediaRoot = lib)"))
        assertTrue(loadBlock.contains("provider.recentWatched(limit = 20, mediaRoot = mediaRoot)"))
        assertTrue(loadBlock.indexOf("refreshHomeLibraryStage(") < loadBlock.indexOf("refreshHomeRecentStage("))
        assertTrue(loadBlock.contains("recent = recent"))
        assertTrue(loadBlock.contains("container.remotePlaybackMemoryRepository.listContinueWatching("))
        assertTrue(loadBlock.contains("mergeContinueWatchingWithMemory(providerRecent, localRecent, limit = 20)"))
        assertTrue(source.contains("container.clientPlaybackProgressRepository.listContinueWatching(source.id, limit = 20)"))
        assertTrue(source.contains("withClientPlaybackProgress(progress)"))
        assertFalse(loadBlock.contains("provider.movies("))
        assertFalse(source.contains("feedMovies"))
        assertTrue(searchBlock.contains("provider.search("))
        assertTrue(searchBlock.contains("query = request"))
        assertTrue(searchBlock.contains("sort = session.activeProviderType.defaultHomeSearchSort()"))
    }

    @Test
    fun homeSeriesPosterOpensLatestUnfinishedEpisode() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val openBlock = source
            .substringAfter("fun openLibraryItem(")
            .substringBefore("private suspend fun loadSmbLibrary")

        assertTrue(openBlock.contains("limit = 500"))
        assertTrue(openBlock.contains("profileId: String"))
        assertTrue(openBlock.contains("container.remotePlaybackMemoryRepository.listContinueWatching("))
        assertTrue(openBlock.contains("limit = 100"))
        assertTrue(openBlock.contains("response.movies.latestHomePlaybackCandidateWithMemory(localMemories)"))
        assertFalse(openBlock.contains("response.movies.firstOrNull()"))
        assertTrue(source.contains("fun List<MovieDto>.latestHomePlaybackCandidate()"))
        assertTrue(source.contains("fun List<MovieDto>.latestHomePlaybackCandidateWithMemory(localMemories: List<RemotePlaybackMemory>)"))
        assertTrue(source.contains("private fun MovieDto.isUnfinishedForHomePlayback()"))
        assertTrue(source.contains("progressPercent == null || progressPercent < 95.0"))
    }

    @Test
    fun homeMediaTreeLeafPosterDoesNotOpenSyntheticDetailRoute() {
        val source = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val openBlock = source
            .substringAfter("fun openLibraryItem(")
            .substringBefore("fun HomeScreen(")

        assertTrue(openBlock.contains("item.isLeaf && providerType != ProviderType.MediaTree"))
        assertFalse(openBlock.contains("} else if (item.isLeaf) {\n                    onNavigate(item.detailRoute())"))
        assertTrue(openBlock.contains("container.mediaProviderFor(providerType).movies("))
        assertTrue(openBlock.contains("onNavigate(movie.detailRoute())"))
    }
}

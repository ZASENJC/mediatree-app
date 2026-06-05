package com.zasenjc.mediatree.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class JellyfinEmbyProviderSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")
    private val dataRoot = appRoot.resolve("src/main/java/com/zasenjc/mediatree/data")
    private val screenRoot = appRoot.resolve("src/main/java/com/zasenjc/mediatree/ui/screens")

    @Test
    fun jellyfinProviderImplementsCoreMediaProviderSurface() {
        val source = dataRoot.resolve("JellyfinProvider.kt").readText()

        assertTrue(source.contains("open class JellyfinProvider"))
        assertTrue(source.contains(": MediaProvider"))
        assertTrue(source.contains("/Users/AuthenticateByName"))
        assertTrue(source.contains("/Users/${'$'}userId/Views"))
        assertTrue(source.contains("/Users/${'$'}userId/Items"))
        assertTrue(source.contains("/Items/${'$'}movieId/PlaybackInfo"))
        assertTrue(source.contains("/Items/${'$'}movieId/Images/Primary"))
        assertTrue(source.contains("/Videos/${'$'}movieId/stream"))
        assertTrue(source.contains("/Sessions/Playing/Progress"))
        assertTrue(source.contains("/Users/${'$'}{session.requireUserId()}/PlayedItems/${'$'}{providerItemId(movieId)}"))
        assertTrue(source.contains("/Users/${'$'}{session.requireUserId()}/FavoriteItems/${'$'}{providerItemId(movieId)}"))
        assertTrue(source.contains("AccessToken"))
        assertTrue(source.contains("User"))
    }

    @Test
    fun embyProviderReusesJellyfinStructureWithHeaderAndProgressDifferences() {
        val source = dataRoot.resolve("EmbyProvider.kt").readText()

        assertTrue(source.contains("class EmbyProvider"))
        assertTrue(source.contains(": JellyfinProvider"))
        assertTrue(source.contains("authorizationScheme: String = \"Emby\""))
        assertTrue(source.contains("/Users/${'$'}userId/PlayingItems/${'$'}movieId/Progress"))
        assertTrue(source.contains("api_key"))
    }

    @Test
    fun sessionStorePersistsTokensPerProfile() {
        val source = dataRoot.resolve("SessionStore.kt").readText()

        assertTrue(source.contains("type: ProviderType"))
        assertTrue(source.contains("userId: String"))
        assertTrue(source.contains("tokenKey(profile.id)"))
        assertTrue(source.contains("tokenKey(profileId: String)"))
        assertFalse(source.contains("type = ProviderType.MediaTree,\n            serverUrl = normalized,\n            token = \"\""))
    }

    @Test
    fun sessionStoreCreatesNewJellyfinProfilesWhenAddingInsteadOfEditing() {
        val source = dataRoot.resolve("SessionStore.kt").readText()

        assertTrue(source.contains("type == ProviderType.MediaTree -> current.activeProfile?.takeIf { it.type == ProviderType.MediaTree }"))
        assertTrue(source.contains("profileId != null"))
        assertTrue(source.contains("providerProfile(type, normalized, current.resolvedProfiles)"))
        assertFalse(source.contains("?: current.activeProfile?.takeIf { it.type == type }"))
    }

    @Test
    fun appContainerCanResolveProvidersByType() {
        val source = dataRoot.resolve("AppContainer.kt").readText()

        assertTrue(source.contains("val jellyfinProvider: MediaProvider = JellyfinProvider(sessionStore)"))
        assertTrue(source.contains("val embyProvider: MediaProvider = EmbyProvider(sessionStore)"))
        assertTrue(source.contains("fun mediaProviderFor(type: ProviderType?)"))
        assertTrue(source.contains("ProviderType.Jellyfin -> jellyfinProvider"))
        assertTrue(source.contains("ProviderType.Emby -> embyProvider"))
    }

    @Test
    fun settingsAndDetailUseSelectedProviderInsteadOfHardcodedMediaTree() {
        val settings = screenRoot.resolve("SettingsScreen.kt").readText()
        val detail = screenRoot.resolve("DetailScreen.kt").readText()

        assertTrue(settings.contains("serverProviderTypes.forEach"))
        assertTrue(settings.contains("ConnectionEditorTarget.Server(type = type)"))
        assertTrue(settings.contains("state.providerType"))
        assertTrue(settings.contains("container.mediaProviderFor(state.providerType)"))
        assertTrue(settings.contains("saveSession(normalized, result.token, type = state.providerType, userId = result.userId)"))

        assertTrue(detail.contains("container.mediaProviderFor(session.activeProviderType)"))
        assertTrue(detail.contains("movie.providerSeriesId"))
        assertTrue(detail.contains("mediaBrowserSeriesFolder"))
        assertTrue(detail.contains(".playbackSource("))
        assertFalse(detail.contains("PlaybackSource.mediaTree"))
    }

    @Test
    fun mediaBrowserCompatMapsJellyfinFieldsIntoExistingFrontendModel() {
        val compat = dataRoot.resolve("MediaBrowserCompat.kt").readText()
        val models = dataRoot.resolve("Models.kt").readText()

        assertTrue(models.contains("providerItemId"))
        assertTrue(models.contains("providerSeriesId"))
        assertTrue(compat.contains("fun MediaBrowserItemDto.toMediaTreeMovieDto"))
        assertTrue(compat.contains("folderLevels = mediaBrowserFolderLevels"))
        assertTrue(compat.contains("tmdbSeason = seasonNumber"))
        assertTrue(compat.contains("tmdbEpisode = episodeNumber"))
        assertTrue(compat.contains("cast = cast"))
        assertTrue(compat.contains("crew = crew"))
        assertTrue(compat.contains("fun MediaBrowserPlaybackInfoDto.toMediaInfoDto"))
        assertTrue(compat.contains("fun MediaBrowserItemDto.toMediaTreeFolderNodeDto"))
        assertTrue(compat.contains("recursiveItemCount"))
        assertTrue(compat.contains("isBrowsableMediaBrowserItem() -> 1"))
        assertTrue(compat.contains("\"BoxSet\""))
        assertTrue(compat.contains("\"Video\""))
    }

    @Test
    fun jellyfinFoldersUseMediaBrowserFolderCompatMapping() {
        val source = dataRoot.resolve("JellyfinProvider.kt").readText()

        assertTrue(source.contains("\"IncludeItemTypes\" to MediaBrowserFolderItemTypes"))
        assertTrue(source.contains("toMediaTreeFolderNodeDto"))
        assertTrue(source.contains("MediaBrowserFolderItemTypes"))
        assertTrue(source.contains("BoxSet"))
        assertTrue(source.contains("Video"))
        assertTrue(source.contains("RecursiveItemCount"))
        assertTrue(source.contains("CollectionType"))
    }
}

package com.zasenjc.mediatree.provider

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MediaProviderSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")
    private val dataRoot = appRoot.resolve("src/main/java/com/zasenjc/mediatree/data")
    private val screenRoot = appRoot.resolve("src/main/java/com/zasenjc/mediatree/ui/screens")

    @Test
    fun mediaProviderContractAndMediaTreeAdapterExist() {
        val providerFile = dataRoot.resolve("MediaProvider.kt")
        val mediaTreeProviderFile = dataRoot.resolve("MediaTreeProvider.kt")

        assertTrue(providerFile.exists())
        assertTrue(mediaTreeProviderFile.exists())

        val providerSource = providerFile.readText()
        val adapterSource = mediaTreeProviderFile.readText()

        assertTrue(providerSource.contains("interface MediaProvider"))
        listOf(
            "authStatus(",
            "login(",
            "mediaRoots(",
            "folders(",
            "recentWatched(",
            "movies(",
            "favorites(",
            "detail(",
            "progress(",
            "saveProgress(",
            "subtitleTracks(",
            "mediaInfo(",
            "addTag(",
            "removeTag(",
            "scan(",
            "coverUrl(",
            "episodeStillUrl(",
        ).forEach { methodName ->
            assertTrue("MediaProvider should expose $methodName", providerSource.contains(methodName))
            assertTrue("MediaTreeProvider should delegate $methodName", adapterSource.contains("api.$methodName"))
        }
        assertTrue(adapterSource.contains("class MediaTreeProvider("))
        assertTrue(adapterSource.contains("private val api: MediaTreeApi"))
        assertTrue(adapterSource.contains(": MediaProvider"))
    }

    @Test
    fun appContainerExposesProviderInsteadOfApi() {
        val appContainerSource = dataRoot.resolve("AppContainer.kt").readText()

        assertTrue(appContainerSource.contains("private val mediaTreeApi = MediaTreeApi(sessionStore)"))
        assertTrue(appContainerSource.contains("val mediaProvider: MediaProvider = MediaTreeProvider(mediaTreeApi)"))
        assertFalse(appContainerSource.contains("val api = MediaTreeApi"))
    }

    @Test
    fun uiScreensUseMediaProviderInsteadOfMediaTreeApi() {
        val migratedScreens = listOf(
            "HomeScreen.kt",
            "BrowseScreen.kt",
            "FavoritesScreen.kt",
            "DetailScreen.kt",
            "SettingsScreen.kt",
        )

        migratedScreens.forEach { fileName ->
            val source = screenRoot.resolve(fileName).readText()
            assertTrue("$fileName should use the provider", source.contains("container.mediaProvider"))
            assertFalse("$fileName should not call container.api", source.contains("container.api"))
            assertFalse("$fileName should not import MediaTreeApi", source.contains("MediaTreeApi"))
        }
    }
}

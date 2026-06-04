package com.zasenjc.mediatree.ui

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UnconfiguredBackendEmptyStateSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun homeBrowseAndFavoritesShareCenteredBackendSetupEmptyState() {
        val sharedComponents = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/components/SharedComponents.kt")
            .readText()
        val home = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/HomeScreen.kt")
            .readText()
        val browse = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/BrowseScreen.kt")
            .readText()
        val favorites = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/FavoritesScreen.kt")
            .readText()
        val expectedMessage = "请先在设置页中配置后端服务"

        assertTrue(sharedComponents.contains("const val BackendSetupRequiredMessage = \"$expectedMessage\""))
        assertTrue(sharedComponents.contains("fun BackendSetupRequiredState("))
        assertTrue(sharedComponents.contains("modifier.fillMaxSize()"))
        assertTrue(sharedComponents.contains("contentAlignment = Alignment.Center"))

        assertTrue(home.contains("BackendSetupRequiredState("))
        assertTrue(home.contains("icon = Icons.Filled.Home"))
        assertTrue(browse.contains("BackendSetupRequiredState("))
        assertTrue(browse.contains("icon = Icons.Filled.Folder"))
        assertTrue(favorites.contains("BackendSetupRequiredState("))
        assertTrue(favorites.contains("icon = Icons.Filled.Bookmarks"))

        assertTrue(home.contains("BackendSetupRequiredMessage"))
        assertTrue(browse.contains("BackendSetupRequiredMessage"))
        assertTrue(favorites.contains("BackendSetupRequiredMessage"))
    }
}

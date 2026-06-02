package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class StartupPerformanceSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun appContainerIsCreatedOnceAndPassedIntoAppShell() {
        val mainActivity = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/MainActivity.kt")
            .readText()
        val appShell = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()

        assertTrue(mainActivity.contains("val container = remember { AppContainer(this) }"))
        assertTrue(mainActivity.contains("MediaTreeApp(container = container, deepLinkData = deepLinkData)"))
        assertTrue(appShell.contains("fun MediaTreeApp(container: AppContainer, deepLinkData: Uri? = null)"))
        assertFalse(appShell.contains("remember { AppContainer(context) }"))
    }

    @Test
    fun homePagerDoesNotPrecomposeAdjacentTabsOnStartup() {
        val appShell = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()

        assertFalse(appShell.contains("beyondViewportPageCount = 1"))
        assertTrue(appShell.contains("beyondViewportPageCount = 0"))
    }

    @Test
    fun systemBarsAreAppliedOnlyWhenThemeChanges() {
        val mainActivity = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/MainActivity.kt")
            .readText()

        assertTrue(mainActivity.contains("LaunchedEffect(darkTheme)"))
        assertFalse(mainActivity.contains("SideEffect"))
    }
}

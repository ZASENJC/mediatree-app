package com.zasenjc.mediatree.ui

import com.zasenjc.mediatree.ui.motion.Md3FadeThroughEnterDelayMillis
import com.zasenjc.mediatree.ui.motion.Md3FadeThroughEnterDurationMillis
import com.zasenjc.mediatree.ui.motion.Md3FadeThroughExitDurationMillis
import com.zasenjc.mediatree.ui.motion.PlayerExitNavigationDelayMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class MotionSpecsTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun md3DefaultMotionUsesFadeThroughTiming() {
        assertEquals(210, Md3FadeThroughEnterDurationMillis)
        assertEquals(90, Md3FadeThroughExitDurationMillis)
        assertEquals(0, Md3FadeThroughEnterDelayMillis)
        assertTrue(PlayerExitNavigationDelayMillis <= Md3FadeThroughEnterDurationMillis)
    }

    @Test
    fun bottomNavigationIndicatorKeepsFixedHeightAndNoShadowLayer() {
        val appShell = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/MediaTreeApp.kt")
            .readText()
        val bottomNavigationSource = appShell.substringAfter("private fun DesignBottomNavigationBar")

        assertFalse(bottomNavigationSource.contains("shadowElevation"))
        assertFalse(bottomNavigationSource.contains("tonalElevation"))
        assertTrue(bottomNavigationSource.contains(".height(44.dp)"))
        assertTrue(bottomNavigationSource.contains(".fillMaxWidth(indicatorWidthFraction)"))
        assertFalse(bottomNavigationSource.contains(".fillMaxSize()\n                .clip(RoundedCornerShape(32.dp))\n                .graphicsLayer"))
        assertFalse(bottomNavigationSource.contains("scaleY = 0.96f + 0.04f * selectedAmount"))
    }
}

package com.zasenjc.mediatree.ui

import com.zasenjc.mediatree.ui.motion.Md3FadeThroughEnterDelayMillis
import com.zasenjc.mediatree.ui.motion.Md3FadeThroughEnterDurationMillis
import com.zasenjc.mediatree.ui.motion.Md3FadeThroughExitDurationMillis
import com.zasenjc.mediatree.ui.motion.PlayerExitNavigationDelayMillis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSpecsTest {
    @Test
    fun md3DefaultMotionUsesFadeThroughTiming() {
        assertEquals(210, Md3FadeThroughEnterDurationMillis)
        assertEquals(90, Md3FadeThroughExitDurationMillis)
        assertEquals(0, Md3FadeThroughEnterDelayMillis)
        assertTrue(PlayerExitNavigationDelayMillis <= Md3FadeThroughEnterDurationMillis)
    }
}

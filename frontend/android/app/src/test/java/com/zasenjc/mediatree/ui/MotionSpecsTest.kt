package com.zasenjc.mediatree.ui

import com.zasenjc.mediatree.ui.motion.FolderEnterDurationMillis
import com.zasenjc.mediatree.ui.motion.FolderExitDurationMillis
import com.zasenjc.mediatree.ui.motion.PlayerExitNavigationDelayMillis
import com.zasenjc.mediatree.ui.motion.PlayerRouteScrimFadeInMillis
import com.zasenjc.mediatree.ui.motion.PlayerRouteScrimFadeOutMillis
import com.zasenjc.mediatree.ui.motion.folderBackOffset
import com.zasenjc.mediatree.ui.motion.folderForwardOffset
import com.zasenjc.mediatree.ui.motion.isFolderBackNavigation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MotionSpecsTest {
    @Test
    fun folderBackNavigationDetectsParentRoute() {
        assertTrue(isFolderBackNavigation("Movies/Action/HD", "Movies/Action"))
        assertTrue(isFolderBackNavigation("Movies/Action", ""))
        assertFalse(isFolderBackNavigation("Movies/Action", "Movies/Action/HD"))
        assertFalse(isFolderBackNavigation("", "Movies"))
        assertFalse(isFolderBackNavigation("Movies/Action", "Shows/Action"))
    }

    @Test
    fun folderOffsetsStaySubtleAndDirectional() {
        assertEquals(86, folderForwardOffset(1080))
        assertEquals(-86, folderBackOffset(1080))
    }

    @Test
    fun motionDurationsStayShortAndSurfaceSafe() {
        assertEquals(300, FolderEnterDurationMillis)
        assertEquals(210, FolderExitDurationMillis)
        assertEquals(170, PlayerRouteScrimFadeInMillis)
        assertEquals(260, PlayerRouteScrimFadeOutMillis)
        assertTrue(PlayerExitNavigationDelayMillis < PlayerRouteScrimFadeOutMillis)
    }
}

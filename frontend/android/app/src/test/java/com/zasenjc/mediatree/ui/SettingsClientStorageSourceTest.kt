package com.zasenjc.mediatree.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class SettingsClientStorageSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun settingsScreenExposesWebDavAndSmbClientStorageManagement() {
        val settingsSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()

        assertTrue(settingsSource.contains("ConnectionsSection"))
        assertTrue(settingsSource.contains("后端连接"))
        assertTrue(settingsSource.contains("ConnectionEditorDialog"))
        assertTrue(settingsSource.contains("WebDAV"))
        assertTrue(settingsSource.contains("SMB"))
        assertTrue(settingsSource.contains("saveWebDavSource"))
        assertTrue(settingsSource.contains("saveSmbSource"))
        assertTrue(settingsSource.contains("deleteClientStorageSource"))
    }

    @Test
    fun clientStorageUiDoesNotTriggerServerScanOrMediaRootWrites() {
        val settingsSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()
        val clientStorageBlock = settingsSource
            .substringAfter("private fun ConnectionsSection")
            .substringBefore("媒体库显示")

        assertFalse(clientStorageBlock.contains("vm.scan"))
        assertFalse(clientStorageBlock.contains("api.scan"))
        assertFalse(clientStorageBlock.contains("mediaRoots"))
        assertFalse(clientStorageBlock.contains("setActiveLibrary"))
    }

    @Test
    fun settingsExposesMountedThumbnailCacheCleanup() {
        val settingsSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/ui/screens/SettingsScreen.kt")
            .readText()
        val appContainerSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/AppContainer.kt")
            .readText()

        assertTrue(appContainerSource.contains("val mountedVideoThumbnailCache = MountedVideoThumbnailCache(context, this)"))
        assertTrue(settingsSource.contains("fun clearMountedVideoThumbnailCache()"))
        assertTrue(settingsSource.contains("container.mountedVideoThumbnailCache.clear()"))
        assertTrue(settingsSource.contains("SettingsSectionCard(title = \"缓存\""))
        assertTrue(settingsSource.contains("title = \"视频缩略图缓存\""))
        assertTrue(settingsSource.contains("subtitle = \"清理 SMB / WebDAV 浏览页生成的缩略图\""))
        assertTrue(settingsSource.contains("onClick = vm::clearMountedVideoThumbnailCache"))
        assertTrue(settingsSource.contains("缩略图缓存已清理"))
    }
}

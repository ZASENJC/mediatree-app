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
}

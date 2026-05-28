package com.zasenjc.mediatree.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.File

class ClientStorageSourceTest {
    private val appRoot = File(System.getProperty("user.dir") ?: ".")

    @Test
    fun clientStorageStoreAndRepositoryAreDedicatedClientSideComponents() {
        val dataFiles = appRoot.resolve("src/main/java/com/zasenjc/mediatree/data")

        assertEquals(true, dataFiles.resolve("ClientStorageStore.kt").exists())
        assertEquals(true, dataFiles.resolve("ClientStorageRepository.kt").exists())
    }

    @Test
    fun repositoryDoesNotCallMediaTreeBackendScanOrRoots() {
        val repositorySource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/ClientStorageRepository.kt")
            .readText()

        assertFalse(repositorySource.contains("MediaTreeApi"))
        assertFalse(repositorySource.contains("/scan"))
        assertFalse(repositorySource.contains("scan("))
        assertFalse(repositorySource.contains("mediaRoots"))
        assertFalse(repositorySource.contains("media-roots"))
    }

    @Test
    fun storePersistsSecretsSeparatelyFromSerializableMetadata() {
        val storeSource = appRoot
            .resolve("src/main/java/com/zasenjc/mediatree/data/ClientStorageStore.kt")
            .readText()

        assertEquals(true, storeSource.contains("EncryptedSharedPreferences"))
        assertEquals(true, storeSource.contains("ClientStorageSourceMetadata"))
        assertEquals(true, storeSource.contains("writeSecret"))
        assertEquals(true, storeSource.contains("readSecret"))
        assertFalse(storeSource.contains("secret = source.secret"))
    }
}

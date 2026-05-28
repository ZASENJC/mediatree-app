package com.zasenjc.mediatree.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClientStorageRepositoryTest {
    @Test
    fun savesWebDavStorageSourceWithEncryptedSecretOnlyInRuntimeModel() = runTest {
        val store = FakeClientStorageStore()
        val repository = ClientStorageRepository(store)

        val saved = repository.saveWebDav(
            name = "Home WebDAV",
            url = "https://dav.example.com/remote.php/dav/files/me",
            username = "alice",
            password = "secret-password",
        )

        assertEquals(ClientStorageType.WebDAV, saved.type)
        assertEquals("Home WebDAV", saved.name)
        assertEquals("https://dav.example.com/remote.php/dav/files/me", saved.endpoint)
        assertEquals("", saved.path)
        assertEquals("alice", saved.username)
        assertEquals("secret-password", saved.secret)
        assertTrue(saved.enabled)
        assertEquals(listOf(saved), repository.sourcesFlow.first())

        val metadataJson = Json.encodeToString(ClientStorageSourceMetadata.from(saved))
        assertFalse(metadataJson.contains("secret-password"))
        assertFalse(metadataJson.contains("password", ignoreCase = true))
        assertFalse(metadataJson.contains("token", ignoreCase = true))
    }

    @Test
    fun savesSmbStorageSourceWithoutCallingServerSideLibraryState() = runTest {
        val store = FakeClientStorageStore()
        val repository = ClientStorageRepository(store)

        val saved = repository.saveSmb(
            name = "NAS",
            server = "smb://192.168.1.20",
            sharePath = "/Media",
            username = "guest",
            password = "smb-password",
            enabled = false,
        )

        assertEquals(ClientStorageType.SMB, saved.type)
        assertEquals("NAS", saved.name)
        assertEquals("smb://192.168.1.20", saved.endpoint)
        assertEquals("/Media", saved.path)
        assertEquals("guest", saved.username)
        assertEquals("smb-password", saved.secret)
        assertFalse(saved.enabled)
        assertEquals(listOf(saved), repository.sourcesFlow.first())
    }

    @Test
    fun deleteRemovesStorageSource() = runTest {
        val source = ClientStorageSource(
            id = "source-1",
            type = ClientStorageType.WebDAV,
            name = "WebDAV",
            endpoint = "https://dav.example.com",
        )
        val store = FakeClientStorageStore(listOf(source))
        val repository = ClientStorageRepository(store)

        repository.delete("source-1")

        assertEquals(emptyList<ClientStorageSource>(), repository.sourcesFlow.first())
    }

    @Test
    fun rejectsBlankRequiredConnectionFields() = runTest {
        val repository = ClientStorageRepository(FakeClientStorageStore())

        val webDavResult = runCatching {
            repository.saveWebDav(name = "Broken", url = "", username = "", password = "")
        }
        val smbResult = runCatching {
            repository.saveSmb(name = "Broken", server = "smb://nas", sharePath = "", username = "", password = "")
        }

        assertTrue(webDavResult.exceptionOrNull() is IllegalArgumentException)
        assertTrue(smbResult.exceptionOrNull() is IllegalArgumentException)
    }
}

private class FakeClientStorageStore(
    initialSources: List<ClientStorageSource> = emptyList(),
) : ClientStorageStore {
    private val mutableSources = MutableStateFlow(initialSources)

    override val sourcesFlow = mutableSources

    override suspend fun load(): List<ClientStorageSource> = mutableSources.value

    override suspend fun save(source: ClientStorageSource) {
        mutableSources.value = mutableSources.value
            .filterNot { it.id == source.id } + source
    }

    override suspend fun delete(sourceId: String) {
        mutableSources.value = mutableSources.value.filterNot { it.id == sourceId }
    }
}

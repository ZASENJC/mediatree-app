package com.zasenjc.mediatree.data

import kotlinx.coroutines.flow.Flow
import java.util.UUID

class ClientStorageRepository(private val store: ClientStorageStore) {
    val sourcesFlow: Flow<List<ClientStorageSource>> = store.sourcesFlow

    suspend fun saveWebDav(
        id: String = newId(),
        name: String,
        url: String,
        username: String,
        password: String,
        enabled: Boolean = true,
    ): ClientStorageSource {
        val endpoint = url.trim()
        require(endpoint.isNotBlank()) { "WebDAV 地址不能为空" }
        val source = ClientStorageSource(
            id = id,
            type = ClientStorageType.WebDAV,
            name = name.trim().ifBlank { "WebDAV" },
            endpoint = endpoint,
            username = username.trim(),
            secret = password,
            enabled = enabled,
        )
        store.save(source)
        return source
    }

    suspend fun saveSmb(
        id: String = newId(),
        name: String,
        server: String,
        sharePath: String,
        username: String,
        password: String,
        enabled: Boolean = true,
    ): ClientStorageSource {
        val endpoint = server.trim()
        val path = sharePath.trim()
        require(endpoint.isNotBlank()) { "SMB 地址不能为空" }
        require(path.isNotBlank()) { "SMB 共享路径不能为空" }
        val source = ClientStorageSource(
            id = id,
            type = ClientStorageType.SMB,
            name = name.trim().ifBlank { "SMB" },
            endpoint = endpoint,
            path = path,
            username = username.trim(),
            secret = password,
            enabled = enabled,
        )
        store.save(source)
        return source
    }

    suspend fun delete(sourceId: String) {
        store.delete(sourceId)
    }

    private fun newId(): String = UUID.randomUUID().toString()
}

package com.zasenjc.mediatree.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.clientStorageDataStore by preferencesDataStore("mediatree_client_storage")

private val clientStorageJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

@Serializable
enum class ClientStorageType {
    WebDAV,
    SMB,
}

@Serializable
enum class ClientStorageAuthType {
    Basic,
    Bearer,
}

@Serializable
data class ClientStorageSource(
    val id: String,
    val type: ClientStorageType,
    val name: String,
    val endpoint: String,
    val path: String = "",
    val username: String = "",
    val secret: String = "",
    val authType: ClientStorageAuthType = ClientStorageAuthType.Basic,
    val enabled: Boolean = true,
)

@Serializable
data class ClientStorageSourceMetadata(
    val id: String,
    val type: ClientStorageType,
    val name: String,
    val endpoint: String,
    val path: String = "",
    val username: String = "",
    val authType: ClientStorageAuthType = ClientStorageAuthType.Basic,
    val enabled: Boolean = true,
) {
    fun withSecret(secret: String): ClientStorageSource = ClientStorageSource(
        id = id,
        type = type,
        name = name,
        endpoint = endpoint,
        path = path,
        username = username,
        secret = secret,
        authType = authType,
        enabled = enabled,
    )

    companion object {
        fun from(source: ClientStorageSource): ClientStorageSourceMetadata = ClientStorageSourceMetadata(
            id = source.id,
            type = source.type,
            name = source.name,
            endpoint = source.endpoint,
            path = source.path,
            username = source.username,
            authType = source.authType,
            enabled = source.enabled,
        )
    }
}

interface ClientStorageStore {
    val sourcesFlow: Flow<List<ClientStorageSource>>
    suspend fun load(): List<ClientStorageSource>
    suspend fun save(source: ClientStorageSource)
    suspend fun delete(sourceId: String)
}

class AndroidClientStorageStore(context: Context) : ClientStorageStore {
    private val appContext = context.applicationContext
    private val securePrefs: SharedPreferences by lazy { createSecurePrefs() }

    override val sourcesFlow: Flow<List<ClientStorageSource>> = appContext.clientStorageDataStore.data.map { prefs ->
        decodeMetadata(prefs[SOURCES_KEY]).map { metadata ->
            metadata.withSecret(readSecret(metadata.id))
        }
    }

    override suspend fun load(): List<ClientStorageSource> = sourcesFlow.first()

    override suspend fun save(source: ClientStorageSource) {
        writeSecret(source.id, source.secret)
        appContext.clientStorageDataStore.edit { prefs ->
            val sources = decodeMetadata(prefs[SOURCES_KEY])
                .filterNot { it.id == source.id } + ClientStorageSourceMetadata.from(source)
            prefs[SOURCES_KEY] = clientStorageJson.encodeToString(sources)
        }
    }

    override suspend fun delete(sourceId: String) {
        securePrefs.edit().remove(secretKey(sourceId)).apply()
        appContext.clientStorageDataStore.edit { prefs ->
            val sources = decodeMetadata(prefs[SOURCES_KEY]).filterNot { it.id == sourceId }
            if (sources.isEmpty()) {
                prefs.remove(SOURCES_KEY)
            } else {
                prefs[SOURCES_KEY] = clientStorageJson.encodeToString(sources)
            }
        }
    }

    private fun decodeMetadata(value: String?): List<ClientStorageSourceMetadata> =
        value
            ?.let { runCatching { clientStorageJson.decodeFromString<List<ClientStorageSourceMetadata>>(it) }.getOrNull() }
            .orEmpty()

    private fun readSecret(sourceId: String): String =
        securePrefs.getString(secretKey(sourceId), "").orEmpty()

    private fun writeSecret(sourceId: String, secret: String) {
        securePrefs.edit().putString(secretKey(sourceId), secret).apply()
    }

    private fun secretKey(sourceId: String): String = "client_storage_secret_$sourceId"

    private fun createSecurePrefs(): SharedPreferences {
        return SecureSessionPrefsFactory.create {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "mediatree_secure_client_storage",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

    companion object {
        private val SOURCES_KEY = stringPreferencesKey("client_storage_sources")
    }
}

package com.zasenjc.mediatree.data

import android.content.Context
import android.content.SharedPreferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.zasenjc.mediatree.util.UrlUtils
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.sessionDataStore by preferencesDataStore("mediatree_session")

private val sessionJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

class CredentialStorageException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)

object SecureSessionPrefsFactory {
    fun create(createEncryptedPrefs: () -> SharedPreferences): SharedPreferences {
        return try {
            createEncryptedPrefs()
        } catch (e: Throwable) {
            throw CredentialStorageException("加密凭据存储不可用，无法安全保存登录凭据", e)
        }
    }
}

class SessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val securePrefs: SharedPreferences by lazy { createSecurePrefs() }

    val sessionFlow: Flow<Session> = appContext.sessionDataStore.data.map { prefs ->
        val legacyServerUrl = prefs[SERVER_URL].orEmpty()
        val activeLibrary = prefs[ACTIVE_LIBRARY].orEmpty()
        val storedProfiles = prefs[SERVER_PROFILES]
            ?.let { runCatching { sessionJson.decodeFromString<List<ServerProfile>>(it) }.getOrNull() }
            .orEmpty()
        val activeProfileId = prefs[ACTIVE_PROFILE_ID] ?: DEFAULT_MEDIATREE_PROFILE_ID
        val token = readToken(activeProfileId)
        val profiles = storedProfiles.ifEmpty {
            val legacyToken = readToken()
            if (legacyServerUrl.isNotBlank() || legacyToken.isNotBlank() || activeLibrary.isNotBlank()) {
                listOf(mediaTreeProfile(legacyServerUrl, legacyToken, activeLibrary))
            } else {
                emptyList()
            }
        }.map { profile ->
            profile.copy(token = readToken(profile.id))
        }

        Session(
            serverUrl = legacyServerUrl,
            token = token,
            activeLibrary = if (activeProfileId == DEFAULT_MEDIATREE_PROFILE_ID) activeLibrary else profiles.activeProfile(activeProfileId)?.activeLibrary.orEmpty(),
            profiles = profiles,
            activeProfileId = activeProfileId,
        )
    }

    suspend fun saveServer(
        serverUrl: String,
        type: ProviderType = ProviderType.MediaTree,
        name: String = "",
    ) {
        val current = sessionFlow.first()
        val normalized = UrlUtils.normalizeServerUrl(serverUrl)
        val existing = current.activeProfile?.takeIf { it.type == type }
        val profile = (existing ?: providerProfile(type, normalized))
            .copy(type = type, name = name.ifBlank { existing?.name.orEmpty() }.ifBlank { type.name }, serverUrl = normalized)
        val profiles = current.resolvedProfiles.upsertProfile(profile)
        appContext.sessionDataStore.edit { prefs ->
            prefs[SERVER_URL] = normalized
            prefs[SERVER_PROFILES] = sessionJson.encodeToString(profiles.withoutStoredSecrets())
            prefs[ACTIVE_PROFILE_ID] = profile.id
        }
    }

    suspend fun saveProfile(
        profileId: String?,
        serverUrl: String,
        type: ProviderType,
        name: String = "",
    ) {
        val current = sessionFlow.first()
        val normalized = UrlUtils.normalizeServerUrl(serverUrl)
        val existing = profileId
            ?.let { id -> current.resolvedProfiles.firstOrNull { it.id == id } }
            ?.takeIf { it.type == type }
        val profile = (existing ?: providerProfile(type, normalized)).copy(
            type = type,
            name = name.ifBlank { existing?.name.orEmpty() }.ifBlank { type.name },
            serverUrl = normalized,
        )
        val profiles = current.resolvedProfiles.upsertProfile(profile)
        appContext.sessionDataStore.edit { prefs ->
            prefs[SERVER_URL] = normalized
            prefs[SERVER_PROFILES] = sessionJson.encodeToString(profiles.withoutStoredSecrets())
            prefs[ACTIVE_PROFILE_ID] = profile.id
        }
    }

    suspend fun saveSession(
        serverUrl: String,
        token: String,
        type: ProviderType = ProviderType.MediaTree,
        userId: String = "",
        name: String = "",
    ) {
        val current = sessionFlow.first()
        val normalized = UrlUtils.normalizeServerUrl(serverUrl)
        val existing = current.activeProfile?.takeIf { it.type == type }
        val profile = (existing ?: providerProfile(type, normalized)).copy(
            type = type,
            name = name.ifBlank { existing?.name.orEmpty() }.ifBlank { type.name },
            serverUrl = normalized,
            userId = userId,
            token = "",
            activeLibrary = existing?.activeLibrary.orEmpty(),
        )
        val profiles = current.resolvedProfiles.upsertProfile(profile)
        writeToken(token, profile.id)
        tokenKey(profile.id)
        if (profile.id == DEFAULT_MEDIATREE_PROFILE_ID) writeToken(token)
        appContext.sessionDataStore.edit { prefs ->
            prefs[SERVER_URL] = normalized
            prefs[SERVER_PROFILES] = sessionJson.encodeToString(profiles.withoutStoredSecrets())
            prefs[ACTIVE_PROFILE_ID] = profile.id
        }
    }

    suspend fun activateProfile(profileId: String) {
        val current = sessionFlow.first()
        val profile = current.resolvedProfiles.firstOrNull { it.id == profileId } ?: return
        appContext.sessionDataStore.edit { prefs ->
            prefs[SERVER_URL] = profile.serverUrl
            prefs[ACTIVE_PROFILE_ID] = profile.id
        }
    }

    suspend fun setActiveLibrary(path: String) {
        val current = sessionFlow.first()
        val activeProfile = current.activeProfile
        val profiles = if (activeProfile == null) {
            current.resolvedProfiles
        } else {
            current.resolvedProfiles.upsertProfile(activeProfile.copy(activeLibrary = path))
        }
        appContext.sessionDataStore.edit { prefs ->
            if (path.isBlank()) prefs.remove(ACTIVE_LIBRARY) else prefs[ACTIVE_LIBRARY] = path
            if (profiles.isNotEmpty()) prefs[SERVER_PROFILES] = sessionJson.encodeToString(profiles.withoutStoredSecrets())
        }
    }

    suspend fun clearToken() {
        val current = sessionFlow.first()
        val activeProfile = current.activeProfile
        val profiles = if (activeProfile == null) {
            current.resolvedProfiles
        } else {
            current.resolvedProfiles.upsertProfile(activeProfile.copy(token = ""))
        }
        activeProfile?.let { writeToken("", it.id) }
        if (activeProfile?.id == DEFAULT_MEDIATREE_PROFILE_ID) writeToken("")
        appContext.sessionDataStore.edit {
            it[SERVER_URL] = it[SERVER_URL].orEmpty()
            if (profiles.isNotEmpty()) it[SERVER_PROFILES] = sessionJson.encodeToString(profiles.withoutStoredSecrets())
        }
    }

    suspend fun logout() {
        writeToken("")
        sessionFlow.first().resolvedProfiles.forEach { profile -> writeToken("", profile.id) }
        appContext.sessionDataStore.edit { prefs ->
            prefs.remove(SERVER_URL)
            prefs.remove(ACTIVE_LIBRARY)
            prefs.remove(SERVER_PROFILES)
            prefs.remove(ACTIVE_PROFILE_ID)
        }
    }

    fun readToken(): String = securePrefs.getString(TOKEN_KEY, "").orEmpty()

    fun readToken(profileId: String): String =
        securePrefs.getString(tokenKey(profileId), null) ?: if (profileId == DEFAULT_MEDIATREE_PROFILE_ID) readToken() else ""

    private fun writeToken(token: String, profileId: String? = null) {
        val key = profileId?.let(::tokenKey) ?: TOKEN_KEY
        securePrefs.edit().putString(key, token).apply()
    }

    private fun createSecurePrefs(): SharedPreferences {
        return SecureSessionPrefsFactory.create {
            val masterKey = MasterKey.Builder(appContext)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                appContext,
                "mediatree_secure_session",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
        }
    }

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val ACTIVE_LIBRARY = stringPreferencesKey("active_library")
        private val SERVER_PROFILES = stringPreferencesKey("server_profiles")
        private val ACTIVE_PROFILE_ID = stringPreferencesKey("active_profile_id")
        private const val TOKEN_KEY = "auth_token"
    }
}

private fun List<ServerProfile>.withoutStoredSecrets(): List<ServerProfile> =
    map { it.copy(token = "") }

private fun providerProfile(type: ProviderType, serverUrl: String): ServerProfile =
    if (type == ProviderType.MediaTree) {
        mediaTreeProfile(serverUrl)
    } else {
        ServerProfile(
            id = "${type.name.lowercase()}-${UrlUtils.normalizeServerUrl(serverUrl)}",
            type = type,
            name = type.name,
            serverUrl = serverUrl,
        )
    }

private fun tokenKey(profileId: String): String = "auth_token_${profileId}"

private fun List<ServerProfile>.upsertProfile(profile: ServerProfile): List<ServerProfile> {
    val existing = indexOfFirst { it.id == profile.id }
    return if (existing >= 0) {
        toMutableList().also { it[existing] = profile }
    } else {
        this + profile
    }
}

private fun List<ServerProfile>.activeProfile(activeProfileId: String): ServerProfile? =
    firstOrNull { it.id == activeProfileId } ?: firstOrNull()

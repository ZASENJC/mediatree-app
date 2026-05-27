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
        val token = readToken()
        val profiles = prefs[SERVER_PROFILES]
            ?.let { runCatching { sessionJson.decodeFromString<List<ServerProfile>>(it) }.getOrNull() }
            .orEmpty()
        val activeProfileId = prefs[ACTIVE_PROFILE_ID] ?: DEFAULT_MEDIATREE_PROFILE_ID

        Session(
            serverUrl = legacyServerUrl,
            token = token,
            activeLibrary = activeLibrary,
            profiles = profiles.ifEmpty {
                if (legacyServerUrl.isNotBlank() || token.isNotBlank() || activeLibrary.isNotBlank()) {
                    listOf(mediaTreeProfile(legacyServerUrl, token, activeLibrary))
                } else {
                    emptyList()
                }
            },
            activeProfileId = activeProfileId,
        )
    }

    suspend fun saveServer(serverUrl: String) {
        val current = sessionFlow.first()
        val normalized = UrlUtils.normalizeServerUrl(serverUrl)
        val profile = (current.activeProfile ?: mediaTreeProfile(normalized)).copy(serverUrl = normalized)
        val profiles = current.resolvedProfiles.upsertProfile(profile)
        appContext.sessionDataStore.edit { prefs ->
            prefs[SERVER_URL] = normalized
            prefs[SERVER_PROFILES] = sessionJson.encodeToString(profiles.withoutStoredSecrets())
            prefs[ACTIVE_PROFILE_ID] = profile.id
        }
    }

    suspend fun saveSession(serverUrl: String, token: String) {
        val current = sessionFlow.first()
        val normalized = UrlUtils.normalizeServerUrl(serverUrl)
        val profile = (current.activeProfile ?: mediaTreeProfile(normalized)).copy(
            type = ProviderType.MediaTree,
            serverUrl = normalized,
            token = "",
            activeLibrary = current.activeLibrary,
        )
        val profiles = current.resolvedProfiles.upsertProfile(profile)
        writeToken(token)
        appContext.sessionDataStore.edit { prefs ->
            prefs[SERVER_URL] = normalized
            prefs[SERVER_PROFILES] = sessionJson.encodeToString(profiles.withoutStoredSecrets())
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
        writeToken("")
        appContext.sessionDataStore.edit {
            it[SERVER_URL] = it[SERVER_URL].orEmpty()
            if (profiles.isNotEmpty()) it[SERVER_PROFILES] = sessionJson.encodeToString(profiles.withoutStoredSecrets())
        }
    }

    suspend fun logout() {
        writeToken("")
        appContext.sessionDataStore.edit { prefs ->
            prefs.remove(SERVER_URL)
            prefs.remove(ACTIVE_LIBRARY)
            prefs.remove(SERVER_PROFILES)
            prefs.remove(ACTIVE_PROFILE_ID)
        }
    }

    fun readToken(): String = securePrefs.getString(TOKEN_KEY, "").orEmpty()

    private fun writeToken(token: String) {
        securePrefs.edit().putString(TOKEN_KEY, token).apply()
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

private fun List<ServerProfile>.upsertProfile(profile: ServerProfile): List<ServerProfile> {
    val existing = indexOfFirst { it.id == profile.id }
    return if (existing >= 0) {
        toMutableList().also { it[existing] = profile }
    } else {
        this + profile
    }
}

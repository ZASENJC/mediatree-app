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
import kotlinx.coroutines.flow.map

private val Context.sessionDataStore by preferencesDataStore("mediatree_session")

class SessionStore(context: Context) {
    private val appContext = context.applicationContext
    private val securePrefs: SharedPreferences by lazy { createSecurePrefs() }

    val sessionFlow: Flow<Session> = appContext.sessionDataStore.data.map { prefs ->
        Session(
            serverUrl = prefs[SERVER_URL].orEmpty(),
            token = readToken(),
            activeLibrary = prefs[ACTIVE_LIBRARY].orEmpty(),
        )
    }

    suspend fun saveServer(serverUrl: String) {
        appContext.sessionDataStore.edit { prefs ->
            prefs[SERVER_URL] = UrlUtils.normalizeServerUrl(serverUrl)
        }
    }

    suspend fun saveSession(serverUrl: String, token: String) {
        writeToken(token)
        appContext.sessionDataStore.edit { prefs ->
            prefs[SERVER_URL] = UrlUtils.normalizeServerUrl(serverUrl)
        }
    }

    suspend fun setActiveLibrary(path: String) {
        appContext.sessionDataStore.edit { prefs ->
            if (path.isBlank()) prefs.remove(ACTIVE_LIBRARY) else prefs[ACTIVE_LIBRARY] = path
        }
    }

    suspend fun clearToken() {
        writeToken("")
        appContext.sessionDataStore.edit { it[SERVER_URL] = it[SERVER_URL].orEmpty() }
    }

    suspend fun logout() {
        writeToken("")
        appContext.sessionDataStore.edit { prefs ->
            prefs.remove(SERVER_URL)
            prefs.remove(ACTIVE_LIBRARY)
        }
    }

    fun readToken(): String = securePrefs.getString(TOKEN_KEY, "").orEmpty()

    private fun writeToken(token: String) {
        securePrefs.edit().putString(TOKEN_KEY, token).apply()
    }

    private fun createSecurePrefs(): SharedPreferences {
        return try {
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
        } catch (_: Throwable) {
            appContext.getSharedPreferences("mediatree_session_fallback", Context.MODE_PRIVATE)
        }
    }

    companion object {
        private val SERVER_URL = stringPreferencesKey("server_url")
        private val ACTIVE_LIBRARY = stringPreferencesKey("active_library")
        private const val TOKEN_KEY = "auth_token"
    }
}

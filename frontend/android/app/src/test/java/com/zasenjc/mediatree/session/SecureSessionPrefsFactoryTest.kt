package com.zasenjc.mediatree.data

import android.content.SharedPreferences
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class SecureSessionPrefsFactoryTest {
    @Test
    fun returnsEncryptedPrefsWhenCreationSucceeds() {
        val prefs = FakeSharedPreferences()

        assertSame(
            prefs,
            SecureSessionPrefsFactory.create {
                prefs
            },
        )
    }

    @Test
    fun failsClearlyWhenEncryptedPrefsCannotBeCreated() {
        val result = runCatching {
            SecureSessionPrefsFactory.create {
                throw IllegalStateException("keystore unavailable")
            }
        }

        assertTrue(result.exceptionOrNull() is CredentialStorageException)
        assertTrue(result.exceptionOrNull()?.message?.contains("加密凭据存储不可用") == true)
    }
}

private class FakeSharedPreferences : SharedPreferences {
    override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any>()
    override fun getString(key: String?, defValue: String?): String? = defValue
    override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
    override fun getInt(key: String?, defValue: Int): Int = defValue
    override fun getLong(key: String?, defValue: Long): Long = defValue
    override fun getFloat(key: String?, defValue: Float): Float = defValue
    override fun getBoolean(key: String?, defValue: Boolean): Boolean = defValue
    override fun contains(key: String?): Boolean = false
    override fun edit(): SharedPreferences.Editor = error("not needed")
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
}

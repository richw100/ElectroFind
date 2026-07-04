package com.richwatson.electrofind.auth

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class TokenManager(context: Context) {
    private val prefs: SharedPreferences = createPrefs(context)

    // Tokens are stored in Keystore-encrypted prefs. The encrypted file is also excluded
    // from backups (res/xml/backup_rules.xml) — its master key lives in the device Keystore
    // and doesn't transfer, so a restored copy would be undecryptable anyway.
    private fun createPrefs(context: Context): SharedPreferences {
        val legacy = context.getSharedPreferences(LEGACY_PREFS, Context.MODE_PRIVATE)
        val secure = try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            EncryptedSharedPreferences.create(
                context,
                SECURE_PREFS,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.w(TAG, "Encrypted prefs unavailable, falling back to plain prefs", e)
            return legacy
        }
        // One-time migration of tokens saved by older versions in plaintext prefs
        val keys = listOf(KEY_JWT, KEY_REFRESH, KEY_CSRF)
        val toMigrate = keys.mapNotNull { k -> legacy.getString(k, null)?.let { k to it } }
        if (toMigrate.isNotEmpty()) {
            secure.edit().apply { toMigrate.forEach { (k, v) -> putString(k, v) } }.apply()
            legacy.edit().apply { keys.forEach { remove(it) } }.apply()
        }
        return secure
    }

    var jwtToken: String?
        get() = prefs.getString(KEY_JWT, null)
        set(value) = prefs.edit().putString(KEY_JWT, value).apply()

    var refreshToken: String?
        get() = prefs.getString(KEY_REFRESH, null)
        set(value) = prefs.edit().putString(KEY_REFRESH, value).apply()

    var csrfToken: String?
        get() = prefs.getString(KEY_CSRF, null)
        set(value) = prefs.edit().putString(KEY_CSRF, value).apply()

    val isLoggedIn: Boolean
        get() = jwtToken != null

    fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val TAG = "TokenManager"
        private const val SECURE_PREFS = "secure_prefs"
        private const val LEGACY_PREFS = "electrofind_prefs"
        private const val KEY_JWT = "jwt_token"
        private const val KEY_REFRESH = "refresh_token"
        private const val KEY_CSRF = "csrf_token"
    }
}

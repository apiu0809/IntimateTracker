package com.mysecret.intimatetracker.data.sync

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** WebDAV 密码等敏感配置，存 EncryptedSharedPreferences，不进 Room。 */
@Singleton
class SecureStore @Inject constructor(
    @ApplicationContext context: Context,
) {

    private val prefs: SharedPreferences by lazy {
        val masterKey = MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            context,
            "secure_settings",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }

    fun getPassword(): String = prefs.getString(KEY_WEBDAV_PASSWORD, "").orEmpty()

    fun setPassword(value: String) {
        prefs.edit().putString(KEY_WEBDAV_PASSWORD, value).apply()
    }

    companion object {
        private const val KEY_WEBDAV_PASSWORD = "webdav.password"
    }
}

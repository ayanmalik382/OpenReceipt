package com.receiptbook.app.net

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Login session kept in Keystore-backed encrypted preferences. */
class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context.applicationContext,
        "session_secure",
        MasterKey.Builder(context.applicationContext).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private val _email = MutableStateFlow(prefs.getString("email", null))
    val email: StateFlow<String?> = _email

    val token: String? get() = prefs.getString("token", null)

    var cursor: Long
        get() = prefs.getLong("cursor:${_email.value}", 0L)
        set(v) { prefs.edit().putLong("cursor:${_email.value}", v).apply() }

    var lastSyncTime: Long
        get() = prefs.getLong("lastSync:${_email.value}", 0L)
        set(v) { prefs.edit().putLong("lastSync:${_email.value}", v).apply() }

    fun login(email: String, token: String) {
        prefs.edit().putString("email", email).putString("token", token).apply()
        _email.value = email
    }

    fun logout() {
        prefs.edit().remove("token").remove("email").apply()
        _email.value = null
    }

    fun forgetCursor() { prefs.edit().remove("cursor:${_email.value}").remove("lastSync:${_email.value}").apply() }
}

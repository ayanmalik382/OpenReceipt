package com.receiptbook.app.net
import org.json.JSONArray
import org.json.JSONObject
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

    // ---- Pending permanent deletions ----

    fun addPendingPurge(entity: String, id: String) {
        val email = _email.value ?: return
        val key = "pendingPurges:$email"

        val arr = try {
            JSONArray(prefs.getString(key, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }

        // Avoid adding the same purge twice.
        for (i in 0 until arr.length()) {
            val item = arr.optJSONObject(i)
            if (item?.optString("entity") == entity &&
                item.optString("id") == id
            ) {
                return
            }
        }

        arr.put(
            org.json.JSONObject()
                .put("entity", entity)
                .put("id", id)
        )

        prefs.edit().putString(key, arr.toString()).apply()
    }

    fun pendingPurges(): List<Pair<String, String>> {
        val email = _email.value ?: return emptyList()
        val key = "pendingPurges:$email"

        val arr = try {
            JSONArray(prefs.getString(key, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }

        return buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val entity = item.optString("entity")
                val id = item.optString("id")

                if (entity.isNotBlank() && id.isNotBlank()) {
                    add(entity to id)
                }
            }
        }
    }

    fun removePendingPurge(entity: String, id: String) {
        val email = _email.value ?: return
        val key = "pendingPurges:$email"

        val old = try {
            JSONArray(prefs.getString(key, "[]") ?: "[]")
        } catch (_: Exception) {
            JSONArray()
        }

        val updated = JSONArray()

        for (i in 0 until old.length()) {
            val item = old.optJSONObject(i) ?: continue

            if (item.optString("entity") != entity ||
                item.optString("id") != id
            ) {
                updated.put(item)
            }
        }

        prefs.edit().putString(key, updated.toString()).apply()
    }

    companion object {
        /** Tag used on data created while nobody is signed in. See Repository/BusinessListScreen:
         * the local database is always fully visible and usable regardless of login state -
         * signing in only adds cloud backup, it never gates local access. */
        const val LOCAL_OWNER = "local"
    }
}

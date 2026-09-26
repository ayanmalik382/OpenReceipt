package com.receiptbook.app.net

import com.receiptbook.app.R
import com.receiptbook.app.i18n.L
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ApiClient(private val baseUrl: String, private val session: SessionStore) {

    /** [errorCode] is the server's machine-readable code; the shown message is translated on the phone. */
    class ApiException(val code: Int, val errorCode: String?, private val raw: String) : Exception(raw) {
        override val message: String
            get() = ERRORS[errorCode]?.let { L.t(it) } ?: raw
    }

    companion object {
        private val ERRORS = mapOf(
            "INVALID_EMAIL" to R.string.srv_invalid_email,
            "WEAK_PASSWORD" to R.string.srv_password_len,
            "EMAIL_EXISTS" to R.string.srv_email_exists,
            "INVALID_CODE" to R.string.srv_invalid_code,
            "INVALID_CREDENTIALS" to R.string.srv_invalid_credentials,
            "UNVERIFIED" to R.string.srv_unverified,
            "WRONG_PASSWORD" to R.string.srv_wrong_password,
            "NOT_SIGNED_IN" to R.string.srv_session_expired,
            "SESSION_EXPIRED" to R.string.srv_session_expired,
            "RATE_LIMITED" to R.string.srv_rate_limited,
            "SERVER_ERROR" to R.string.srv_server_error,
            "INVALID_REQUEST" to R.string.srv_invalid_request,
            "NOT_FOUND" to R.string.srv_invalid_request
        )
    }

    class Change(val entity: String, val id: String, val updatedAt: Long, val deleted: Boolean, val data: String)
    class SyncResponse(val cursor: Long, val hasMore: Boolean, val changes: List<Change>)

    private suspend fun call(method: String, path: String, body: JSONObject?, auth: Boolean): JSONObject =
        withContext(Dispatchers.IO) {
            val conn = URL(baseUrl + path).openConnection() as HttpURLConnection
            try {
                conn.requestMethod = method
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Content-Type", "application/json")
                conn.setRequestProperty("Accept", "application/json")
                if (auth) session.token?.let { conn.setRequestProperty("Authorization", "Bearer $it") }
                if (body != null) {
                    conn.doOutput = true
                    conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() } ?: "{}"
                val json = try { JSONObject(text) } catch (e: Exception) { JSONObject() }
                if (code !in 200..299) {
                    throw ApiException(code, json.optString("code", "").ifBlank { null }, json.optString("error", "Request failed ($code)"))
                }
                json
            } finally {
                conn.disconnect()
            }
        }

    suspend fun register(email: String, password: String) {
        call("POST", "/auth/register", JSONObject().put("email", email).put("password", password), false)
    }

    suspend fun sendOtp(email: String) {
        call("POST", "/auth/otp", JSONObject().put("email", email), false)
    }

    suspend fun verify(email: String, otp: String): String =
        call("POST", "/auth/verify", JSONObject().put("email", email).put("otp", otp), false).getString("token")

    suspend fun login(email: String, password: String): String =
        call("POST", "/auth/login", JSONObject().put("email", email).put("password", password), false).getString("token")

    suspend fun reset(email: String, otp: String, password: String): String =
        call("POST", "/auth/reset", JSONObject().put("email", email).put("otp", otp).put("password", password), false)
            .getString("token")

    suspend fun deleteAccount(password: String) {
        call("DELETE", "/auth/account", JSONObject().put("password", password), true)
    }

    suspend fun sync(cursor: Long, changes: List<Change>): SyncResponse {
        val arr = JSONArray()
        changes.forEach {
            arr.put(
                JSONObject().put("entity", it.entity).put("id", it.id).put("updatedAt", it.updatedAt)
                    .put("deleted", it.deleted).put("data", it.data)
            )
        }
        val r = call("POST", "/sync", JSONObject().put("cursor", cursor).put("changes", arr), true)
        val out = mutableListOf<Change>()
        val ch = r.getJSONArray("changes")
        for (i in 0 until ch.length()) {
            val o = ch.getJSONObject(i)
            out += Change(o.getString("entity"), o.getString("id"), o.getLong("updatedAt"), o.getBoolean("deleted"), o.getString("data"))
        }
        return SyncResponse(r.getLong("cursor"), r.getBoolean("hasMore"), out)
    }
}

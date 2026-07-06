package com.classkey.modernattendance.data

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.classkey.modernattendance.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Minimal Supabase REST client (Auth + PostgREST + RPC) with zero extra dependencies.
 * All methods are synchronous — call them through [io] which runs on a background
 * thread and posts the result back to the main thread.
 *
 * Session (access/refresh token) is persisted in SharedPreferences; a 401 triggers
 * one silent refresh + retry. RLS on the server decides what each role can read.
 */
class SupabaseApi(context: Context) {

    private val prefs = context.getSharedPreferences("classkey_cloud", Context.MODE_PRIVATE)
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anon = BuildConfig.SUPABASE_ANON_KEY

    companion object {
        fun isConfigured(): Boolean =
            BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

        private val main = Handler(Looper.getMainLooper())

        /** Run [work] off the main thread, deliver Result on the main thread. */
        fun <T> io(work: () -> T, onResult: (Result<T>) -> Unit) {
            Thread {
                val r = try {
                    Result.success(work())
                } catch (e: Exception) {
                    Result.failure(e)
                }
                main.post { onResult(r) }
            }.start()
        }
    }

    // ── low-level HTTP ────────────────────────────────────────────────

    private data class Http(val code: Int, val body: String)

    private fun http(method: String, url: String, body: String?, bearer: String?): Http {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        conn.setRequestProperty("apikey", anon)
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("Prefer", "return=representation")
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body.toByteArray()) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
        conn.disconnect()
        return Http(code, text)
    }

    /** Authed call with one silent token refresh on 401. */
    private fun authed(method: String, path: String, body: String? = null): Http {
        var token = prefs.getString("access_token", null)
            ?: throw Exception("Not logged in.")
        var res = http(method, "$base$path", body, token)
        if (res.code == 401 && refreshSession()) {
            token = prefs.getString("access_token", null) ?: throw Exception("Session expired.")
            res = http(method, "$base$path", body, token)
        }
        if (res.code == 401) throw Exception("Session expired. Please log in again.")
        return res
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    // ── auth ──────────────────────────────────────────────────────────

    data class CloudUser(val id: String, val role: String, val fullName: String, val email: String)

    fun login(email: String, password: String): CloudUser {
        val body = JSONObject().put("email", email.trim()).put("password", password).toString()
        val res = http("POST", "$base/auth/v1/token?grant_type=password", body, null)
        if (res.code !in 200..299) {
            val msg = try { JSONObject(res.body).optString("error_description")
                .ifBlank { JSONObject(res.body).optString("msg") } } catch (_: Exception) { "" }
            throw Exception(msg.ifBlank { "Wrong email or password." })
        }
        val json = JSONObject(res.body)
        prefs.edit()
            .putString("access_token", json.getString("access_token"))
            .putString("refresh_token", json.getString("refresh_token"))
            .putString("user_id", json.getJSONObject("user").getString("id"))
            .apply()
        return fetchProfile()
    }

    private fun refreshSession(): Boolean {
        val rt = prefs.getString("refresh_token", null) ?: return false
        val res = http("POST", "$base/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", rt).toString(), null)
        if (res.code !in 200..299) return false
        val json = JSONObject(res.body)
        prefs.edit()
            .putString("access_token", json.getString("access_token"))
            .putString("refresh_token", json.getString("refresh_token"))
            .apply()
        return true
    }

    fun hasSession(): Boolean = prefs.getString("access_token", null) != null

    fun logout() {
        prefs.edit().clear().apply()
    }

    fun fetchProfile(): CloudUser {
        val uid = prefs.getString("user_id", null) ?: throw Exception("Not logged in.")
        val res = authed("GET", "/rest/v1/profiles?id=eq.$uid&select=id,role,full_name,email")
        val arr = JSONArray(res.body)
        if (arr.length() == 0) throw Exception("Profile not found. Ask admin to set up your account.")
        val p = arr.getJSONObject(0)
        return CloudUser(p.getString("id"), p.getString("role"), p.optString("full_name"), p.optString("email"))
    }

    // ── student ───────────────────────────────────────────────────────

    fun myStudentId(): String? {
        val uid = prefs.getString("user_id", null) ?: return null
        val res = authed("GET", "/rest/v1/students?profile_id=eq.$uid&select=id")
        val arr = JSONArray(res.body)
        return if (arr.length() > 0) arr.getJSONObject(0).getString("id") else null
    }

    data class MarkResult(val ok: Boolean, val message: String, val status: String?, val distance: Int?)

    fun markAttendance(lat: Double, lng: Double, accuracy: Double?, deviceId: String): MarkResult {
        val body = JSONObject()
            .put("p_latitude", lat)
            .put("p_longitude", lng)
            .put("p_accuracy", accuracy ?: JSONObject.NULL)
            .put("p_device_id", deviceId)
            .put("p_biometric_verified", true)
            .toString()
        val res = authed("POST", "/rest/v1/rpc/mark_attendance", body)
        if (res.code !in 200..299) throw Exception(friendlyPgError(res.body))
        val json = JSONObject(res.body)
        return MarkResult(
            ok = json.optBoolean("ok"),
            message = json.optString("message"),
            status = if (json.has("status")) json.optString("status") else null,
            distance = if (json.has("distance")) json.optInt("distance") else null
        )
    }

    data class CloudRecord(
        val date: String, val status: String, val method: String,
        val markedAt: String?, val note: String?, val name: String?, val regNo: String?
    )

    fun myHistory(limit: Int = 40): List<CloudRecord> {
        val res = authed("GET", "/rest/v1/attendance?select=*&order=attendance_date.desc&limit=$limit")
        return parseRecords(res.body)
    }

    fun myToday(): CloudRecord? {
        val res = authed("GET", "/rest/v1/attendance?attendance_date=eq.${today()}&select=*")
        return parseRecords(res.body).firstOrNull()
    }

    fun submitRequest(type: String, date: String, reason: String) {
        val sid = myStudentId() ?: throw Exception("Student record not found.")
        val body = JSONObject()
            .put("student_id", sid).put("request_type", type)
            .put("request_date", date).put("reason", reason).toString()
        val res = authed("POST", "/rest/v1/attendance_requests", body)
        if (res.code !in 200..299) throw Exception(friendlyPgError(res.body))
    }

    data class CloudRequest(
        val id: String, val type: String, val date: String, val reason: String,
        val status: String, val name: String?, val regNo: String?
    )

    fun myRequests(): List<CloudRequest> {
        val res = authed("GET", "/rest/v1/attendance_requests?select=*&order=created_at.desc&limit=30")
        return parseRequests(res.body)
    }

    // ── staff / admin ─────────────────────────────────────────────────

    fun todayAll(): List<CloudRecord> {
        val select = enc("*,students(register_number,profiles(full_name))")
        val res = authed("GET", "/rest/v1/attendance?attendance_date=eq.${today()}&select=$select&order=marked_at.desc")
        return parseRecords(res.body)
    }

    fun pendingRequests(): List<CloudRequest> {
        val select = enc("*,students(register_number,profiles(full_name))")
        val res = authed("GET", "/rest/v1/attendance_requests?status=eq.pending&select=$select&order=created_at.desc")
        return parseRequests(res.body)
    }

    fun decideRequest(id: String, approved: Boolean): String {
        val body = JSONObject()
            .put("p_request_id", id)
            .put("p_decision", if (approved) "approved" else "rejected")
            .put("p_review_note", JSONObject.NULL)
            .toString()
        val res = authed("POST", "/rest/v1/rpc/approve_attendance_request", body)
        if (res.code !in 200..299) throw Exception(friendlyPgError(res.body))
        return JSONObject(res.body).optString("message", "Done.")
    }

    fun studentCount(): Int {
        val res = authed("GET", "/rest/v1/students?select=id")
        return JSONArray(res.body).length()
    }

    // ── parsing helpers ───────────────────────────────────────────────

    private fun parseRecords(body: String): List<CloudRecord> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val stu = o.optJSONObject("students")
            val prof = stu?.optJSONObject("profiles")
            CloudRecord(
                date = o.optString("attendance_date"),
                status = o.optString("status"),
                method = o.optString("verification_method"),
                markedAt = o.optString("marked_at").takeIf { it.isNotBlank() && it != "null" },
                note = o.optString("manual_reason").takeIf { it.isNotBlank() && it != "null" },
                name = prof?.optString("full_name"),
                regNo = stu?.optString("register_number")
            )
        }
    }

    private fun parseRequests(body: String): List<CloudRequest> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val stu = o.optJSONObject("students")
            val prof = stu?.optJSONObject("profiles")
            CloudRequest(
                id = o.getString("id"),
                type = o.optString("request_type"),
                date = o.optString("request_date"),
                reason = o.optString("reason"),
                status = o.optString("status"),
                name = prof?.optString("full_name"),
                regNo = stu?.optString("register_number")
            )
        }
    }

    private fun friendlyPgError(body: String): String = try {
        val o = JSONObject(body)
        o.optString("message").ifBlank { o.optString("hint").ifBlank { "Request failed." } }
    } catch (_: Exception) {
        "Request failed. Check your connection."
    }

    private fun today(): String = java.time.LocalDate.now().toString()
}

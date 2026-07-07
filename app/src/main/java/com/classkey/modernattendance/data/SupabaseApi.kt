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
 * Supabase REST client (Auth + PostgREST + RPC + Storage) with zero extra dependencies.
 * All methods are synchronous — call them through [io], which runs the work on a
 * background thread and posts the Result back on the main thread.
 *
 * Sessions persist in SharedPreferences; a 401 triggers one silent refresh + retry.
 * All authority lives in the database (RLS + security-definer RPCs).
 */
class SupabaseApi(private val context: Context) {

    private val prefs = context.getSharedPreferences("classkey_cloud", Context.MODE_PRIVATE)
    private val base = BuildConfig.SUPABASE_URL.trimEnd('/')
    private val anon = BuildConfig.SUPABASE_ANON_KEY

    companion object {
        fun isConfigured(): Boolean =
            BuildConfig.SUPABASE_URL.isNotBlank() && BuildConfig.SUPABASE_ANON_KEY.isNotBlank()

        fun adminApiConfigured(): Boolean = BuildConfig.ADMIN_API_URL.isNotBlank()

        private val main = Handler(Looper.getMainLooper())

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

    private fun http(
        method: String, url: String, body: ByteArray?, bearer: String?,
        contentType: String = "application/json", extraHeaders: Map<String, String> = emptyMap()
    ): Http {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = method
        conn.connectTimeout = 15000
        conn.readTimeout = 25000
        conn.setRequestProperty("apikey", anon)
        conn.setRequestProperty("Content-Type", contentType)
        conn.setRequestProperty("Prefer", "return=representation")
        extraHeaders.forEach { (k, v) -> conn.setRequestProperty(k, v) }
        if (bearer != null) conn.setRequestProperty("Authorization", "Bearer $bearer")
        if (body != null) {
            conn.doOutput = true
            conn.outputStream.use { it.write(body) }
        }
        val code = conn.responseCode
        val stream = if (code in 200..299) conn.inputStream else conn.errorStream
        val text = stream?.bufferedReader()?.use(BufferedReader::readText) ?: ""
        conn.disconnect()
        return Http(code, text)
    }

    private fun authed(
        method: String, path: String, body: String? = null,
        contentType: String = "application/json", raw: ByteArray? = null,
        extraHeaders: Map<String, String> = emptyMap(), absoluteUrl: String? = null
    ): Http {
        var token = prefs.getString("access_token", null) ?: throw Exception("Not logged in.")
        val url = absoluteUrl ?: "$base$path"
        var res = http(method, url, raw ?: body?.toByteArray(), token, contentType, extraHeaders)
        if (res.code == 401 && refreshSession()) {
            token = prefs.getString("access_token", null) ?: throw Exception("Session expired.")
            res = http(method, url, raw ?: body?.toByteArray(), token, contentType, extraHeaders)
        }
        if (res.code == 401) throw Exception("Session expired. Please log in again.")
        return res
    }

    private fun enc(v: String): String = URLEncoder.encode(v, "UTF-8")

    private fun friendlyError(body: String): String = try {
        val o = JSONObject(body)
        listOf("message", "error", "msg", "error_description", "hint")
            .firstNotNullOfOrNull { k -> o.optString(k).takeIf { it.isNotBlank() } } ?: "Request failed."
    } catch (_: Exception) {
        "Request failed. Check your internet connection."
    }

    private fun requireOk(res: Http): String {
        if (res.code !in 200..299) throw Exception(friendlyError(res.body))
        return res.body
    }

    // ── auth ──────────────────────────────────────────────────────────

    data class CloudUser(
        val id: String, val role: String, val fullName: String, val email: String,
        val phone: String?, val username: String?, val avatarUrl: String?,
        val forcedPasswordChange: Boolean, val departmentId: String?
    )

    /** register no / username / phone / email → email, via SECURITY DEFINER RPC (anon-safe). */
    fun resolveLoginEmail(identifier: String): String {
        val id = identifier.trim()
        if (id.contains("@")) return id.lowercase()
        val res = http(
            "POST", "$base/rest/v1/rpc/resolve_login_email",
            JSONObject().put("p_identifier", id).toString().toByteArray(), null
        )
        if (res.code !in 200..299) throw Exception(friendlyError(res.body))
        val body = res.body.trim().trim('"')
        if (body.isBlank() || body == "null") throw Exception("No account found for \"$id\".")
        return body
    }

    fun login(identifier: String, password: String): CloudUser {
        val email = resolveLoginEmail(identifier)
        val body = JSONObject().put("email", email).put("password", password).toString()
        val res = http("POST", "$base/auth/v1/token?grant_type=password", body.toByteArray(), null)
        if (res.code !in 200..299) throw Exception(
            friendlyError(res.body).replace("Invalid login credentials", "Wrong password or account does not exist.")
        )
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
        val res = http(
            "POST", "$base/auth/v1/token?grant_type=refresh_token",
            JSONObject().put("refresh_token", rt).toString().toByteArray(), null
        )
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
        val res = authed("GET", "/rest/v1/profiles?id=eq.$uid&select=*")
        val arr = JSONArray(requireOk(res))
        if (arr.length() == 0) throw Exception("Profile not found. Ask admin to set up your account.")
        val p = arr.getJSONObject(0)
        if (!p.optBoolean("is_active", true)) throw Exception("This account is deactivated. Contact admin.")
        return CloudUser(
            id = p.getString("id"),
            role = p.getString("role"),
            fullName = p.optString("full_name"),
            email = p.optString("email"),
            phone = p.optString("phone").takeIf { it.isNotBlank() && it != "null" },
            username = p.optString("username").takeIf { it.isNotBlank() && it != "null" },
            avatarUrl = p.optString("avatar_url").takeIf { it.isNotBlank() && it != "null" },
            forcedPasswordChange = p.optBoolean("forced_password_change", false),
            departmentId = p.optString("department_id").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    /** Change own password via Supabase Auth, then clear the forced flag. */
    fun changePassword(newPassword: String) {
        val res = authed("PUT", "/auth/v1/user", JSONObject().put("password", newPassword).toString())
        requireOk(res)
        val uid = prefs.getString("user_id", null) ?: return
        authed("PATCH", "/rest/v1/profiles?id=eq.$uid", JSONObject().put("forced_password_change", false).toString())
    }

    // ── profile photo (Supabase Storage, public 'avatars' bucket) ─────

    fun uploadAvatar(jpegBytes: ByteArray): String {
        val uid = prefs.getString("user_id", null) ?: throw Exception("Not logged in.")
        val path = "avatars/$uid/profile.jpg"
        val res = authed(
            "POST", "/storage/v1/object/$path", raw = jpegBytes,
            contentType = "image/jpeg", extraHeaders = mapOf("x-upsert" to "true")
        )
        requireOk(res)
        val publicUrl = "$base/storage/v1/object/public/$path"
        authed("PATCH", "/rest/v1/profiles?id=eq.$uid", JSONObject().put("avatar_url", publicUrl).toString())
        return publicUrl
    }

    fun downloadBytes(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 20000
        return conn.inputStream.use { it.readBytes() }.also { conn.disconnect() }
    }

    // ── student ───────────────────────────────────────────────────────

    fun myStudentId(): String? {
        val uid = prefs.getString("user_id", null) ?: return null
        val res = authed("GET", "/rest/v1/students?profile_id=eq.$uid&select=id")
        val arr = JSONArray(requireOk(res))
        return if (arr.length() > 0) arr.getJSONObject(0).getString("id") else null
    }

    data class MarkResult(val ok: Boolean, val message: String, val session: String?, val status: String?, val distance: Int?)

    fun markAttendance(lat: Double, lng: Double, accuracy: Double?, deviceId: String): MarkResult {
        val body = JSONObject()
            .put("p_latitude", lat)
            .put("p_longitude", lng)
            .put("p_accuracy", accuracy ?: JSONObject.NULL)
            .put("p_device_id", deviceId)
            .put("p_biometric_verified", true)
            .toString()
        val res = authed("POST", "/rest/v1/rpc/mark_attendance", body)
        val json = JSONObject(requireOk(res))
        return MarkResult(
            ok = json.optBoolean("ok"),
            message = json.optString("message"),
            session = json.optString("session").takeIf { it.isNotBlank() },
            status = json.optString("status").takeIf { it.isNotBlank() },
            distance = if (json.has("distance")) json.optInt("distance") else null
        )
    }

    data class CloudRecord(
        val date: String, val session: String, val status: String, val method: String,
        val markedAt: String?, val note: String?, val name: String?, val regNo: String?
    )

    fun myHistory(limit: Int = 60): List<CloudRecord> {
        val res = authed("GET", "/rest/v1/attendance?select=*&order=attendance_date.desc,session.asc&limit=$limit")
        return parseRecords(requireOk(res))
    }

    fun myToday(): List<CloudRecord> {
        val res = authed("GET", "/rest/v1/attendance?attendance_date=eq.${today()}&select=*")
        return parseRecords(requireOk(res))
    }

    fun submitRequest(type: String, date: String, session: String, reason: String) {
        val sid = myStudentId() ?: throw Exception("Student record not found.")
        val body = JSONObject()
            .put("student_id", sid).put("request_type", type)
            .put("request_date", date).put("session", session).put("reason", reason).toString()
        requireOk(authed("POST", "/rest/v1/attendance_requests", body))
    }

    data class CloudRequest(
        val id: String, val type: String, val date: String, val session: String,
        val reason: String, val status: String, val name: String?, val regNo: String?
    )

    fun myRequests(): List<CloudRequest> {
        val res = authed("GET", "/rest/v1/attendance_requests?select=*&order=created_at.desc&limit=30")
        return parseRequests(requireOk(res))
    }

    // ── notifications ─────────────────────────────────────────────────

    data class CloudNotif(val id: String, val title: String, val body: String, val createdAt: String, val read: Boolean)

    fun myNotifications(): List<CloudNotif> {
        val uid = prefs.getString("user_id", null) ?: return emptyList()
        val res = authed("GET", "/rest/v1/notifications?profile_id=eq.$uid&order=created_at.desc&limit=50")
        val arr = JSONArray(requireOk(res))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            CloudNotif(
                o.getString("id"), o.optString("title"), o.optString("body"),
                o.optString("created_at").take(16).replace('T', ' '), o.optBoolean("is_read")
            )
        }
    }

    fun markNotificationsRead() {
        val uid = prefs.getString("user_id", null) ?: return
        authed("PATCH", "/rest/v1/notifications?profile_id=eq.$uid&is_read=eq.false",
            JSONObject().put("is_read", true).toString())
    }

    // ── staff / admin reads ───────────────────────────────────────────

    fun todayAll(): List<CloudRecord> {
        val select = enc("*,students(register_number,profiles(full_name))")
        val res = authed("GET", "/rest/v1/attendance?attendance_date=eq.${today()}&select=$select&order=marked_at.desc.nullslast")
        return parseRecords(requireOk(res))
    }

    fun recordsBetween(from: String, to: String): List<CloudRecord> {
        val select = enc("*,students(register_number,profiles(full_name))")
        val res = authed("GET", "/rest/v1/attendance?attendance_date=gte.$from&attendance_date=lte.$to&select=$select&order=attendance_date.desc&limit=2000")
        return parseRecords(requireOk(res))
    }

    fun pendingRequests(): List<CloudRequest> {
        val select = enc("*,students(register_number,profiles(full_name))")
        val res = authed("GET", "/rest/v1/attendance_requests?status=eq.pending&select=$select&order=created_at.desc")
        return parseRequests(requireOk(res))
    }

    fun decideRequest(id: String, approved: Boolean): String {
        val body = JSONObject()
            .put("p_request_id", id)
            .put("p_decision", if (approved) "approved" else "rejected")
            .put("p_review_note", JSONObject.NULL)
            .toString()
        val res = authed("POST", "/rest/v1/rpc/approve_attendance_request", body)
        return JSONObject(requireOk(res)).optString("message", "Done.")
    }

    fun manualOverride(studentId: String, status: String, session: String, reason: String): String {
        val body = JSONObject()
            .put("p_student_id", studentId).put("p_date", today())
            .put("p_status", status).put("p_reason", reason).put("p_session", session)
            .toString()
        val res = authed("POST", "/rest/v1/rpc/manual_attendance_override", body)
        val json = JSONObject(requireOk(res))
        if (!json.optBoolean("ok")) throw Exception(json.optString("message"))
        return json.optString("message")
    }

    data class CloudStudent(
        val id: String, val profileId: String, val name: String, val regNo: String,
        val email: String, val phone: String?, val year: Int?, val section: String?,
        val deviceStatus: String, val isActive: Boolean
    )

    fun students(): List<CloudStudent> {
        val select = enc("*,profiles(id,full_name,email,phone,is_active)")
        val res = authed("GET", "/rest/v1/students?select=$select&order=register_number")
        val arr = JSONArray(requireOk(res))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val p = o.optJSONObject("profiles")
            CloudStudent(
                id = o.getString("id"),
                profileId = o.getString("profile_id"),
                name = p?.optString("full_name") ?: "",
                regNo = o.optString("register_number"),
                email = p?.optString("email") ?: "",
                phone = p?.optString("phone"),
                year = if (o.isNull("year")) null else o.optInt("year"),
                section = o.optString("section").takeIf { it.isNotBlank() && it != "null" },
                deviceStatus = o.optString("device_status"),
                isActive = p?.optBoolean("is_active", true) ?: true
            )
        }
    }

    fun setStudentActive(profileId: String, active: Boolean) {
        requireOk(authed("PATCH", "/rest/v1/profiles?id=eq.$profileId", JSONObject().put("is_active", active).toString()))
    }

    fun studentCount(): Int {
        val res = authed("GET", "/rest/v1/students?select=id")
        return JSONArray(requireOk(res)).length()
    }

    fun suspiciousAttempts(): List<Triple<String, String, String>> {
        val select = enc("*,students(register_number,profiles(full_name))")
        val res = authed("GET", "/rest/v1/suspicious_attempts?select=$select&order=created_at.desc&limit=50")
        val arr = JSONArray(requireOk(res))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val stu = o.optJSONObject("students")
            val name = stu?.optJSONObject("profiles")?.optString("full_name") ?: "Unknown"
            val reg = stu?.optString("register_number") ?: ""
            Triple("$name · $reg", o.optString("reason"), o.optString("created_at").take(16).replace('T', ' '))
        }
    }

    // ── admin ─────────────────────────────────────────────────────────

    data class Campus(
        val id: String, val name: String, val lat: Double, val lng: Double,
        val radius: Int, val minAccuracy: Int,
        val morningOpen: String, val presentUntil: String, val lateUntil: String,
        val afternoonOpen: String, val afternoonPresentUntil: String, val afternoonLateUntil: String
    )

    fun campus(): Campus {
        val res = authed("GET", "/rest/v1/campus_config?select=*&limit=1")
        val arr = JSONArray(requireOk(res))
        if (arr.length() == 0) throw Exception("Campus not configured. Run the seed SQL.")
        val o = arr.getJSONObject(0)
        fun t(k: String, d: String) = o.optString(k, d).take(5)
        return Campus(
            o.getString("id"), o.optString("college_name"),
            o.optDouble("latitude"), o.optDouble("longitude"),
            o.optInt("allowed_radius_meters"), o.optInt("min_accuracy_meters", 100),
            t("morning_open", "08:00"), t("present_until", "09:30"), t("late_until", "13:00"),
            t("afternoon_open", "13:00"), t("afternoon_present_until", "14:00"), t("afternoon_late_until", "15:30")
        )
    }

    fun updateCampus(c: Campus) {
        val body = JSONObject()
            .put("college_name", c.name).put("latitude", c.lat).put("longitude", c.lng)
            .put("allowed_radius_meters", c.radius).put("min_accuracy_meters", c.minAccuracy)
            .put("morning_open", c.morningOpen).put("present_until", c.presentUntil).put("late_until", c.lateUntil)
            .put("afternoon_open", c.afternoonOpen).put("afternoon_present_until", c.afternoonPresentUntil)
            .put("afternoon_late_until", c.afternoonLateUntil)
            .toString()
        requireOk(authed("PATCH", "/rest/v1/campus_config?id=eq.${c.id}", body))
    }

    data class DeviceApproval(val id: String, val who: String, val oldDevice: String?, val newDevice: String, val status: String)

    fun deviceApprovals(): List<DeviceApproval> {
        val select = enc("*,students(register_number,profiles(full_name))")
        val res = authed("GET", "/rest/v1/device_approvals?select=$select&order=requested_at.desc&limit=50")
        val arr = JSONArray(requireOk(res))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val stu = o.optJSONObject("students")
            val name = stu?.optJSONObject("profiles")?.optString("full_name") ?: "Unknown"
            val reg = stu?.optString("register_number") ?: ""
            DeviceApproval(
                o.getString("id"), "$name · $reg",
                o.optString("old_device_id").takeIf { it.isNotBlank() && it != "null" },
                o.optString("new_device_id"), o.optString("status")
            )
        }
    }

    fun decideDevice(id: String, approved: Boolean): String {
        val body = JSONObject().put("p_approval_id", id)
            .put("p_decision", if (approved) "approved" else "rejected").toString()
        val res = authed("POST", "/rest/v1/rpc/approve_device", body)
        return JSONObject(requireOk(res)).optString("message", "Done.")
    }

    fun auditLogs(): List<Triple<String, String, String>> {
        val select = enc("*,profiles(full_name)")
        val res = authed("GET", "/rest/v1/audit_logs?select=$select&order=created_at.desc&limit=100")
        val arr = JSONArray(requireOk(res))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val actor = o.optJSONObject("profiles")?.optString("full_name") ?: "system"
            val detail = o.optJSONObject("new_value")?.toString() ?: ""
            Triple(o.optString("action").replace('_', ' '), "$actor $detail", o.optString("created_at").take(16).replace('T', ' '))
        }
    }

    data class Department(val id: String, val code: String, val name: String)

    fun departments(): List<Department> {
        val res = authed("GET", "/rest/v1/departments?select=*&order=code")
        val arr = JSONArray(requireOk(res))
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Department(o.getString("id"), o.optString("code"), o.optString("name"))
        }
    }

    // ── secure account creation (Edge Function or Vercel route) ──────

    fun createUser(
        role: String, fullName: String, email: String, phone: String, username: String,
        tempPassword: String, registerNumber: String?, departmentId: String?,
        year: Int?, section: String?, designation: String?
    ): String {
        val endpoint = BuildConfig.ADMIN_API_URL.trim()
        if (endpoint.isBlank()) {
            throw Exception("Account creation endpoint not configured. Add ADMIN_API_URL to local.properties (see README).")
        }
        val body = JSONObject()
            .put("action", "create")
            .put("role", role)
            .put("full_name", fullName)
            .put("email", email)
            .put("phone", phone)
            .put("username", username.ifBlank { JSONObject.NULL })
            .put("temp_password", tempPassword.ifBlank { JSONObject.NULL })
            .put("register_number", registerNumber ?: JSONObject.NULL)
            .put("department_id", departmentId ?: JSONObject.NULL)
            .put("year", year ?: JSONObject.NULL)
            .put("section", section ?: JSONObject.NULL)
            .put("designation", designation ?: JSONObject.NULL)
            .toString()
        val res = authed("POST", "", body = body, absoluteUrl = endpoint)
        val json = try { JSONObject(res.body) } catch (_: Exception) { JSONObject() }
        if (res.code !in 200..299) throw Exception(json.optString("error", "Could not create the account."))
        return json.optString("message", "Account created.")
    }

    // ── parsing ───────────────────────────────────────────────────────

    private fun parseRecords(body: String): List<CloudRecord> {
        val arr = JSONArray(body)
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val stu = o.optJSONObject("students")
            val prof = stu?.optJSONObject("profiles")
            CloudRecord(
                date = o.optString("attendance_date"),
                session = o.optString("session", "morning"),
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
                session = o.optString("session", "full_day"),
                reason = o.optString("reason"),
                status = o.optString("status"),
                name = prof?.optString("full_name"),
                regNo = stu?.optString("register_number")
            )
        }
    }

    private fun today(): String = java.time.LocalDate.now().toString()
}

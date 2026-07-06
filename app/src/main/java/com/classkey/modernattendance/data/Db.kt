package com.classkey.modernattendance.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object Crypto {
    fun newSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    fun hash(password: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest((salt + password).toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}

fun nowStamp(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
fun nowClock(): String = LocalDateTime.now().format(DateTimeFormatter.ofPattern("hh:mm a"))

class Db(context: Context) : SQLiteOpenHelper(context, "classkey_v3.db", null, 2) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """CREATE TABLE users(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                role TEXT NOT NULL,
                name TEXT NOT NULL,
                email TEXT UNIQUE NOT NULL,
                mobile TEXT NOT NULL,
                register_no TEXT UNIQUE,
                department TEXT NOT NULL DEFAULT 'CSE',
                year TEXT NOT NULL DEFAULT '',
                password_hash TEXT NOT NULL,
                salt TEXT NOT NULL,
                biometric_enabled INTEGER NOT NULL DEFAULT 0,
                device_id TEXT,
                photo_path TEXT,
                created_at TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE attendance(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                status TEXT NOT NULL,
                time TEXT NOT NULL,
                latitude REAL, longitude REAL, accuracy REAL, distance REAL,
                method TEXT NOT NULL,
                note TEXT NOT NULL DEFAULT '',
                marked_by INTEGER,
                UNIQUE(student_id, date)
            )"""
        )
        db.execSQL(
            """CREATE TABLE requests(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id INTEGER NOT NULL,
                type TEXT NOT NULL,
                date TEXT NOT NULL,
                reason TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'PENDING',
                decided_by INTEGER,
                created_at TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE notifications(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                audience TEXT NOT NULL,
                title TEXT NOT NULL,
                body TEXT NOT NULL,
                tone TEXT NOT NULL DEFAULT 'info',
                created_at TEXT NOT NULL,
                read INTEGER NOT NULL DEFAULT 0
            )"""
        )
        db.execSQL(
            """CREATE TABLE audit(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                actor_id INTEGER,
                action TEXT NOT NULL,
                detail TEXT NOT NULL,
                created_at TEXT NOT NULL
            )"""
        )
        db.execSQL(
            """CREATE TABLE attempts(
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                student_id INTEGER NOT NULL,
                date TEXT NOT NULL,
                time TEXT NOT NULL,
                reason TEXT NOT NULL
            )"""
        )
        db.execSQL("CREATE TABLE settings(key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE holidays(date TEXT PRIMARY KEY, note TEXT NOT NULL)")
        seed(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        listOf("users", "attendance", "requests", "notifications", "audit", "attempts", "settings", "holidays")
            .forEach { db.execSQL("DROP TABLE IF EXISTS $it") }
        onCreate(db)
    }

    private fun seed(db: SQLiteDatabase) {
        val demoPassword = "ChangeMe123!"
        insertUser(db, Role.ADMIN, "Dr. Anand Kumar", "admin@classkey.local", "9990001000", null, "ALL", "Office", demoPassword)
        insertUser(db, Role.STAFF, "Dr. Rajesh Kumar", "staff@classkey.local", "9990003000", null, "CSE", "Associate Professor", demoPassword)
        insertUser(db, Role.STUDENT, "Priya Sharma", "priya.sharma@classkey.local", "9990002000", "CS21001", "CSE", "3rd Year", demoPassword)
        insertUser(db, Role.STUDENT, "Arjun Mehta", "arjun.mehta@classkey.local", "9990002001", "CS21002", "CSE", "3rd Year", demoPassword)
        insertUser(db, Role.STUDENT, "Riya Patel", "riya.patel@classkey.local", "9990002002", "CS21003", "CSE", "3rd Year", demoPassword)
        insertUser(db, Role.STUDENT, "Karthik Iyer", "karthik.iyer@classkey.local", "9990002003", "CS21004", "CSE", "3rd Year", demoPassword)
        insertUser(db, Role.STUDENT, "Sneha Nair", "sneha.nair@classkey.local", "9990002004", "CS21005", "CSE", "3rd Year", demoPassword)

        putSetting(db, "campus_name", "College Main Gate")
        putSetting(db, "campus_lat", "11.01830")
        putSetting(db, "campus_lng", "76.97250")
        putSetting(db, "radius", "300")
        putSetting(db, "open_time", "06:00")
        putSetting(db, "late_after", "20:00")
        putSetting(db, "cutoff", "23:30")

        val cv = ContentValues().apply {
            put("audience", "ROLE:STUDENT")
            put("title", "Welcome to ClassKey")
            put("body", "Verify your location and fingerprint once each day to mark entry attendance.")
            put("tone", "info")
            put("created_at", nowStamp())
        }
        db.insert("notifications", null, cv)
    }

    fun insertUser(
        db: SQLiteDatabase, role: Role, name: String, email: String, mobile: String,
        registerNo: String?, department: String, year: String, password: String
    ): Long {
        val salt = Crypto.newSalt()
        val cv = ContentValues().apply {
            put("role", role.name)
            put("name", name)
            put("email", email.lowercase())
            put("mobile", mobile)
            put("register_no", registerNo)
            put("department", department)
            put("year", year)
            put("password_hash", Crypto.hash(password, salt))
            put("salt", salt)
            put("created_at", nowStamp())
        }
        return db.insert("users", null, cv)
    }

    // ---- users ----

    private fun Cursor.toUser() = User(
        id = getInt(getColumnIndexOrThrow("id")),
        role = Role.valueOf(getString(getColumnIndexOrThrow("role"))),
        name = getString(getColumnIndexOrThrow("name")),
        email = getString(getColumnIndexOrThrow("email")),
        mobile = getString(getColumnIndexOrThrow("mobile")),
        registerNo = getString(getColumnIndexOrThrow("register_no")),
        department = getString(getColumnIndexOrThrow("department")),
        year = getString(getColumnIndexOrThrow("year")),
        biometricEnabled = getInt(getColumnIndexOrThrow("biometric_enabled")) == 1,
        deviceId = getString(getColumnIndexOrThrow("device_id")),
        photoPath = getString(getColumnIndexOrThrow("photo_path"))
    )

    fun findUser(identifier: String): User? {
        val id = identifier.trim()
        readableDatabase.rawQuery(
            "SELECT * FROM users WHERE lower(email)=lower(?) OR mobile=? OR upper(register_no)=upper(?) LIMIT 1",
            arrayOf(id, id, id)
        ).use { c -> return if (c.moveToFirst()) c.toUser() else null }
    }

    fun credentialCheck(userId: Int, password: String): Boolean {
        readableDatabase.rawQuery("SELECT password_hash, salt FROM users WHERE id=?", arrayOf(userId.toString())).use { c ->
            if (!c.moveToFirst()) return false
            return c.getString(0) == Crypto.hash(password, c.getString(1))
        }
    }

    fun setPassword(userId: Int, newPassword: String) {
        val salt = Crypto.newSalt()
        val cv = ContentValues().apply {
            put("password_hash", Crypto.hash(newPassword, salt))
            put("salt", salt)
        }
        writableDatabase.update("users", cv, "id=?", arrayOf(userId.toString()))
    }

    fun userById(id: Int): User? {
        readableDatabase.rawQuery("SELECT * FROM users WHERE id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) c.toUser() else null
        }
    }

    fun students(): List<User> {
        val out = mutableListOf<User>()
        readableDatabase.rawQuery("SELECT * FROM users WHERE role='STUDENT' ORDER BY register_no", emptyArray()).use { c ->
            while (c.moveToNext()) out.add(c.toUser())
        }
        return out
    }

    fun updateUser(id: Int, cv: ContentValues) {
        writableDatabase.update("users", cv, "id=?", arrayOf(id.toString()))
    }

    /** Removes a user and all of their attendance / requests / attempts in one transaction. */
    fun deleteUserCascade(id: Int) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete("attendance", "student_id=?", arrayOf(id.toString()))
            writableDatabase.delete("requests", "student_id=?", arrayOf(id.toString()))
            writableDatabase.delete("attempts", "student_id=?", arrayOf(id.toString()))
            writableDatabase.delete("users", "id=?", arrayOf(id.toString()))
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    // ---- holidays ----

    fun insertHoliday(date: String, note: String) {
        val cv = ContentValues().apply { put("date", date); put("note", note) }
        writableDatabase.insertWithOnConflict("holidays", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun deleteHoliday(date: String) {
        writableDatabase.delete("holidays", "date=?", arrayOf(date))
    }

    fun isHoliday(date: String): Boolean {
        readableDatabase.rawQuery("SELECT 1 FROM holidays WHERE date=? LIMIT 1", arrayOf(date)).use { c ->
            return c.moveToFirst()
        }
    }

    fun holidays(): List<Holiday> {
        val out = mutableListOf<Holiday>()
        readableDatabase.rawQuery("SELECT date, note FROM holidays ORDER BY date DESC", emptyArray()).use { c ->
            while (c.moveToNext()) out.add(Holiday(c.getString(0), c.getString(1)))
        }
        return out
    }

    // ---- settings ----

    fun putSetting(db: SQLiteDatabase, key: String, value: String) {
        val cv = ContentValues().apply { put("key", key); put("value", value) }
        db.insertWithOnConflict("settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun putSetting(key: String, value: String) = putSetting(writableDatabase, key, value)

    fun getSetting(key: String, fallback: String): String {
        readableDatabase.rawQuery("SELECT value FROM settings WHERE key=?", arrayOf(key)).use { c ->
            return if (c.moveToFirst()) c.getString(0) else fallback
        }
    }

    // ---- attendance ----

    private fun Cursor.toAttendance(): AttendanceRec {
        fun optDouble(col: String): Double? {
            val i = getColumnIndexOrThrow(col)
            return if (isNull(i)) null else getDouble(i)
        }
        val markedByIdx = getColumnIndexOrThrow("marked_by")
        return AttendanceRec(
            id = getInt(getColumnIndexOrThrow("id")),
            studentId = getInt(getColumnIndexOrThrow("student_id")),
            date = getString(getColumnIndexOrThrow("date")),
            status = AttStatus.valueOf(getString(getColumnIndexOrThrow("status"))),
            time = getString(getColumnIndexOrThrow("time")),
            latitude = optDouble("latitude"),
            longitude = optDouble("longitude"),
            accuracy = optDouble("accuracy"),
            distance = optDouble("distance"),
            method = getString(getColumnIndexOrThrow("method")),
            note = getString(getColumnIndexOrThrow("note")) ?: "",
            markedBy = if (isNull(markedByIdx)) null else getInt(markedByIdx)
        )
    }

    fun upsertAttendance(
        studentId: Int, date: LocalDate, status: AttStatus, time: String,
        latitude: Double?, longitude: Double?, accuracy: Double?, distance: Double?,
        method: String, note: String, markedBy: Int?
    ) {
        val cv = ContentValues().apply {
            put("student_id", studentId)
            put("date", date.toString())
            put("status", status.name)
            put("time", time)
            if (latitude == null) putNull("latitude") else put("latitude", latitude)
            if (longitude == null) putNull("longitude") else put("longitude", longitude)
            if (accuracy == null) putNull("accuracy") else put("accuracy", accuracy)
            if (distance == null) putNull("distance") else put("distance", distance)
            put("method", method)
            put("note", note)
            if (markedBy == null) putNull("marked_by") else put("marked_by", markedBy)
        }
        writableDatabase.insertWithOnConflict("attendance", null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun attendanceFor(studentId: Int, date: LocalDate): AttendanceRec? {
        readableDatabase.rawQuery(
            "SELECT * FROM attendance WHERE student_id=? AND date=? LIMIT 1",
            arrayOf(studentId.toString(), date.toString())
        ).use { c -> return if (c.moveToFirst()) c.toAttendance() else null }
    }

    fun recordsForDate(date: LocalDate): List<AttendanceRec> {
        val out = mutableListOf<AttendanceRec>()
        readableDatabase.rawQuery(
            "SELECT * FROM attendance WHERE date=? ORDER BY id DESC", arrayOf(date.toString())
        ).use { c -> while (c.moveToNext()) out.add(c.toAttendance()) }
        return out
    }

    fun recordsBetween(from: LocalDate, to: LocalDate): List<AttendanceRec> {
        val out = mutableListOf<AttendanceRec>()
        readableDatabase.rawQuery(
            "SELECT * FROM attendance WHERE date>=? AND date<=? ORDER BY date DESC, id DESC",
            arrayOf(from.toString(), to.toString())
        ).use { c -> while (c.moveToNext()) out.add(c.toAttendance()) }
        return out
    }

    fun recordsForStudent(studentId: Int, from: LocalDate, to: LocalDate): List<AttendanceRec> {
        val out = mutableListOf<AttendanceRec>()
        readableDatabase.rawQuery(
            "SELECT * FROM attendance WHERE student_id=? AND date>=? AND date<=? ORDER BY date DESC",
            arrayOf(studentId.toString(), from.toString(), to.toString())
        ).use { c -> while (c.moveToNext()) out.add(c.toAttendance()) }
        return out
    }

    fun recentForStudent(studentId: Int, limit: Int): List<AttendanceRec> {
        val out = mutableListOf<AttendanceRec>()
        readableDatabase.rawQuery(
            "SELECT * FROM attendance WHERE student_id=? ORDER BY date DESC LIMIT ?",
            arrayOf(studentId.toString(), limit.toString())
        ).use { c -> while (c.moveToNext()) out.add(c.toAttendance()) }
        return out
    }

    // ---- requests ----

    private fun Cursor.toRequest() = LeaveReq(
        id = getInt(getColumnIndexOrThrow("id")),
        studentId = getInt(getColumnIndexOrThrow("student_id")),
        studentName = getString(getColumnIndexOrThrow("student_name")) ?: "",
        registerNo = getString(getColumnIndexOrThrow("reg_no")) ?: "",
        type = ReqType.valueOf(getString(getColumnIndexOrThrow("type"))),
        date = getString(getColumnIndexOrThrow("date")),
        reason = getString(getColumnIndexOrThrow("reason")),
        status = ReqStatus.valueOf(getString(getColumnIndexOrThrow("status"))),
        decidedByName = getString(getColumnIndexOrThrow("decider_name")),
        createdAt = getString(getColumnIndexOrThrow("created_at"))
    )

    private val requestSelect = """
        SELECT r.*, s.name AS student_name, s.register_no AS reg_no, d.name AS decider_name
        FROM requests r
        JOIN users s ON s.id = r.student_id
        LEFT JOIN users d ON d.id = r.decided_by
    """.trimIndent()

    fun insertRequest(studentId: Int, type: ReqType, date: LocalDate, reason: String): Long {
        val cv = ContentValues().apply {
            put("student_id", studentId)
            put("type", type.name)
            put("date", date.toString())
            put("reason", reason)
            put("status", ReqStatus.PENDING.name)
            put("created_at", nowStamp())
        }
        return writableDatabase.insert("requests", null, cv)
    }

    fun requestsForStudent(studentId: Int): List<LeaveReq> {
        val out = mutableListOf<LeaveReq>()
        readableDatabase.rawQuery(
            "$requestSelect WHERE r.student_id=? ORDER BY r.id DESC", arrayOf(studentId.toString())
        ).use { c -> while (c.moveToNext()) out.add(c.toRequest()) }
        return out
    }

    fun requests(onlyPending: Boolean): List<LeaveReq> {
        val where = if (onlyPending) "WHERE r.status='PENDING'" else ""
        val out = mutableListOf<LeaveReq>()
        readableDatabase.rawQuery("$requestSelect $where ORDER BY r.id DESC", emptyArray())
            .use { c -> while (c.moveToNext()) out.add(c.toRequest()) }
        return out
    }

    fun requestById(id: Int): LeaveReq? {
        readableDatabase.rawQuery("$requestSelect WHERE r.id=?", arrayOf(id.toString())).use { c ->
            return if (c.moveToFirst()) c.toRequest() else null
        }
    }

    fun decideRequest(id: Int, deciderId: Int, status: ReqStatus) {
        val cv = ContentValues().apply {
            put("status", status.name)
            put("decided_by", deciderId)
        }
        writableDatabase.update("requests", cv, "id=?", arrayOf(id.toString()))
    }

    // ---- notifications ----

    fun insertNotif(audience: String, title: String, body: String, tone: String) {
        val cv = ContentValues().apply {
            put("audience", audience)
            put("title", title)
            put("body", body)
            put("tone", tone)
            put("created_at", nowStamp())
        }
        writableDatabase.insert("notifications", null, cv)
    }

    fun notifsFor(audiences: List<String>): List<Notif> {
        val marks = audiences.joinToString(",") { "?" }
        val out = mutableListOf<Notif>()
        readableDatabase.rawQuery(
            "SELECT * FROM notifications WHERE audience IN ($marks) ORDER BY id DESC LIMIT 60",
            audiences.toTypedArray()
        ).use { c ->
            while (c.moveToNext()) out.add(
                Notif(
                    id = c.getInt(c.getColumnIndexOrThrow("id")),
                    title = c.getString(c.getColumnIndexOrThrow("title")),
                    body = c.getString(c.getColumnIndexOrThrow("body")),
                    tone = c.getString(c.getColumnIndexOrThrow("tone")),
                    createdAt = c.getString(c.getColumnIndexOrThrow("created_at")),
                    read = c.getInt(c.getColumnIndexOrThrow("read")) == 1
                )
            )
        }
        return out
    }

    fun unreadCount(audiences: List<String>): Int {
        val marks = audiences.joinToString(",") { "?" }
        readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM notifications WHERE read=0 AND audience IN ($marks)",
            audiences.toTypedArray()
        ).use { c -> return if (c.moveToFirst()) c.getInt(0) else 0 }
    }

    fun markAllRead(audiences: List<String>) {
        val marks = audiences.joinToString(",") { "?" }
        writableDatabase.execSQL(
            "UPDATE notifications SET read=1 WHERE audience IN ($marks)", audiences.toTypedArray()
        )
    }

    // ---- audit + attempts ----

    fun insertAudit(actorId: Int?, action: String, detail: String) {
        val cv = ContentValues().apply {
            if (actorId == null) putNull("actor_id") else put("actor_id", actorId)
            put("action", action)
            put("detail", detail)
            put("created_at", nowStamp())
        }
        writableDatabase.insert("audit", null, cv)
    }

    fun auditRows(limit: Int): List<AuditRow> {
        val out = mutableListOf<AuditRow>()
        readableDatabase.rawQuery(
            """SELECT a.id, COALESCE(u.name,'System') AS actor, a.action, a.detail, a.created_at
               FROM audit a LEFT JOIN users u ON u.id=a.actor_id
               ORDER BY a.id DESC LIMIT ?""",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(
                AuditRow(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))
            )
        }
        return out
    }

    fun insertAttempt(studentId: Int, date: LocalDate, reason: String) {
        val cv = ContentValues().apply {
            put("student_id", studentId)
            put("date", date.toString())
            put("time", nowClock())
            put("reason", reason)
        }
        writableDatabase.insert("attempts", null, cv)
    }

    fun attemptsForDate(date: LocalDate): List<Attempt> {
        val out = mutableListOf<Attempt>()
        readableDatabase.rawQuery(
            """SELECT t.id, u.name, COALESCE(u.register_no,''), t.reason, t.time
               FROM attempts t JOIN users u ON u.id=t.student_id
               WHERE t.date=? ORDER BY t.id DESC""",
            arrayOf(date.toString())
        ).use { c ->
            while (c.moveToNext()) out.add(
                Attempt(c.getInt(0), c.getString(1), c.getString(2), c.getString(3), c.getString(4))
            )
        }
        return out
    }
}

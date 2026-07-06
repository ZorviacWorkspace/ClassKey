package com.classkey.modernattendance.data

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.location.Location
import android.provider.Settings
import java.io.File
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class Repo(private val context: Context) {

    private val db = Db(context)
    private val prefs = context.getSharedPreferences("classkey_session", Context.MODE_PRIVATE)

    // ---------- session ----------

    fun sessionUser(): User? {
        val id = prefs.getInt("user_id", -1)
        return if (id > 0) db.userById(id) else null
    }

    fun boundUser(): User? {
        val id = prefs.getInt("bound_user_id", -1)
        val user = if (id > 0) db.userById(id) else null
        return if (user?.biometricEnabled == true) user else null
    }

    fun loginAs(user: User) {
        prefs.edit().putInt("user_id", user.id).apply()
        db.insertAudit(user.id, "LOGIN", "${user.role.name} signed in")
    }

    fun logout(user: User?) {
        prefs.edit().remove("user_id").apply()
        if (user != null) db.insertAudit(user.id, "LOGOUT", "${user.role.name} signed out")
    }

    fun login(identifier: String, password: String): Result<User> {
        val user = db.findUser(identifier)
            ?: return Result.failure(Exception("No account matches that ID. Check register no / email / mobile."))
        if (!db.credentialCheck(user.id, password)) {
            return Result.failure(Exception("Wrong password. Demo password is ChangeMe123!"))
        }
        loginAs(user)
        return Result.success(user)
    }

    fun signup(name: String, email: String, mobile: String, registerNo: String, password: String): Result<User> {
        val cleanEmail = email.trim().lowercase()
        if (name.isBlank() || registerNo.isBlank()) return Result.failure(Exception("Name and register number are required."))
        if (!cleanEmail.contains("@") ||
            !(cleanEmail.endsWith(".local") || cleanEmail.endsWith(".edu") || cleanEmail.endsWith(".ac.in") || cleanEmail.endsWith(".edu.in") || cleanEmail.endsWith(".in"))
        ) return Result.failure(Exception("Use your college email ID (e.g. name@college.edu / .ac.in / .local)."))
        if (mobile.trim().length < 10) return Result.failure(Exception("Enter a valid 10-digit mobile number."))
        if (password.length < 8) return Result.failure(Exception("Password must be at least 8 characters."))
        if (db.findUser(cleanEmail) != null) return Result.failure(Exception("An account with this email already exists."))
        if (db.findUser(registerNo.trim()) != null) return Result.failure(Exception("This register number is already registered."))

        val id = db.insertUser(
            db.writableDatabase, Role.STUDENT, name.trim(), cleanEmail, mobile.trim(),
            registerNo.trim().uppercase(), "CSE", "Student", password
        )
        val user = db.userById(id.toInt())!!
        db.insertAudit(user.id, "SIGNUP", "New student account ${user.registerNo}")
        loginAs(user)
        return Result.success(user)
    }

    fun changePassword(userId: Int, old: String, new: String): String? {
        if (!db.credentialCheck(userId, old)) return "Current password is incorrect."
        if (new.length < 8) return "New password must be at least 8 characters."
        db.setPassword(userId, new)
        db.insertAudit(userId, "PASSWORD_CHANGE", "Password updated")
        return null
    }

    fun refreshUser(id: Int): User? = db.userById(id)

    fun savePhoto(userId: Int, path: String) {
        db.updateUser(userId, ContentValues().apply { put("photo_path", path) })
    }

    @SuppressLint("HardwareIds")
    fun enableBiometric(user: User) {
        val deviceId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"
        db.updateUser(user.id, ContentValues().apply {
            put("biometric_enabled", 1)
            put("device_id", deviceId)
        })
        prefs.edit().putInt("bound_user_id", user.id).apply()
        db.insertAudit(user.id, "BIOMETRIC_ENABLED", "Device bound: $deviceId")
    }

    fun addStudent(name: String, email: String, mobile: String, registerNo: String, actor: User): String? {
        if (name.isBlank() || email.isBlank() || registerNo.isBlank()) return "Name, email and register number are required."
        if (db.findUser(email.trim()) != null) return "Email already exists."
        if (db.findUser(registerNo.trim()) != null) return "Register number already exists."
        db.insertUser(
            db.writableDatabase, Role.STUDENT, name.trim(), email.trim().lowercase(),
            mobile.trim(), registerNo.trim().uppercase(), "CSE", "Student", "ChangeMe123!"
        )
        db.insertAudit(actor.id, "STUDENT_ADDED", "Added ${registerNo.trim().uppercase()} ($name) · default password ChangeMe123!")
        return null
    }

    fun students(): List<User> = db.students()
    fun userById(id: Int): User? = db.userById(id)

    // ---------- campus config ----------

    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

    fun campus(): CampusCfg = CampusCfg(
        name = db.getSetting("campus_name", "College Main Gate"),
        latitude = db.getSetting("campus_lat", "11.01830").toDouble(),
        longitude = db.getSetting("campus_lng", "76.97250").toDouble(),
        radiusMeters = db.getSetting("radius", "300").toDouble(),
        openTime = LocalTime.parse(db.getSetting("open_time", "06:00")),
        lateAfter = LocalTime.parse(db.getSetting("late_after", "20:00")),
        cutoff = LocalTime.parse(db.getSetting("cutoff", "23:30"))
    )

    fun saveCampus(cfg: CampusCfg, actor: User) {
        db.putSetting("campus_name", cfg.name)
        db.putSetting("campus_lat", cfg.latitude.toString())
        db.putSetting("campus_lng", cfg.longitude.toString())
        db.putSetting("radius", cfg.radiusMeters.toString())
        db.putSetting("open_time", cfg.openTime.format(hhmm))
        db.putSetting("late_after", cfg.lateAfter.format(hhmm))
        db.putSetting("cutoff", cfg.cutoff.format(hhmm))
        db.insertAudit(
            actor.id, "CAMPUS_UPDATED",
            "Geofence ${"%.5f".format(cfg.latitude)},${"%.5f".format(cfg.longitude)} r=${cfg.radiusMeters.toInt()}m · " +
                "Opens ${cfg.openTime} · Present till ${cfg.lateAfter} · Cutoff ${cfg.cutoff}"
        )
    }

    // ---------- holidays ----------

    fun holidays(): List<Holiday> = db.holidays()
    fun isHoliday(date: LocalDate): Boolean = db.isHoliday(date.toString())

    fun addHoliday(date: LocalDate, note: String, actor: User): String? {
        if (note.isBlank()) return "Add a short note (e.g. Pongal, Exam leave)."
        db.insertHoliday(date.toString(), note.trim())
        db.insertAudit(actor.id, "HOLIDAY_ADDED", "$date · ${note.trim()}")
        return null
    }

    fun removeHoliday(date: String, actor: User) {
        db.deleteHoliday(date)
        db.insertAudit(actor.id, "HOLIDAY_REMOVED", date)
    }

    // ---------- student / staff management ----------

    fun addStaff(name: String, email: String, mobile: String, role: Role, actor: User): String? {
        if (name.isBlank() || email.isBlank()) return "Name and email are required."
        if (db.findUser(email.trim()) != null) return "Email already exists."
        db.insertUser(
            db.writableDatabase, role, name.trim(), email.trim().lowercase(),
            mobile.trim(), null, "ALL", if (role == Role.ADMIN) "Office" else "Staff", "ChangeMe123!"
        )
        db.insertAudit(actor.id, "STAFF_ADDED", "Added ${role.name} $name ($email) · default password ChangeMe123!")
        return null
    }

    fun editStudent(id: Int, name: String, email: String, mobile: String, registerNo: String, actor: User): String? {
        if (name.isBlank() || email.isBlank() || registerNo.isBlank()) return "Name, email and register number are required."
        val byEmail = db.findUser(email.trim())
        if (byEmail != null && byEmail.id != id) return "Another account already uses that email."
        val byReg = db.findUser(registerNo.trim())
        if (byReg != null && byReg.id != id) return "Another account already uses that register number."
        db.updateUser(id, ContentValues().apply {
            put("name", name.trim())
            put("email", email.trim().lowercase())
            put("mobile", mobile.trim())
            put("register_no", registerNo.trim().uppercase())
        })
        db.insertAudit(actor.id, "STUDENT_EDITED", "Updated ${registerNo.trim().uppercase()} ($name)")
        return null
    }

    fun deleteStudent(student: User, actor: User): String? {
        if (student.id == actor.id) return "You cannot delete your own account."
        db.deleteUserCascade(student.id)
        db.insertAudit(actor.id, "STUDENT_DELETED", "Removed ${student.registerNo} (${student.name}) and their records")
        return null
    }

    fun resetStudentPassword(student: User, actor: User) {
        db.setPassword(student.id, "ChangeMe123!")
        db.insertAudit(actor.id, "PASSWORD_RESET", "Reset password for ${student.registerNo} to ChangeMe123!")
        db.insertNotif("USER:${student.id}", "Password reset", "Your password was reset to ChangeMe123! by the office. Change it after login.", "warn")
    }

    fun editOwnProfile(user: User, name: String, mobile: String): String? {
        if (name.isBlank()) return "Name cannot be empty."
        db.updateUser(user.id, ContentValues().apply {
            put("name", name.trim())
            put("mobile", mobile.trim())
        })
        db.insertAudit(user.id, "PROFILE_EDITED", "Updated own profile")
        return null
    }

    fun distanceToCampus(lat: Double, lng: Double): Double {
        val cfg = campus()
        val results = FloatArray(1)
        Location.distanceBetween(lat, lng, cfg.latitude, cfg.longitude, results)
        return results[0].toDouble()
    }

    // ---------- attendance ----------

    fun attendanceFor(studentId: Int, date: LocalDate): AttendanceRec? = db.attendanceFor(studentId, date)

    fun statusFor(studentId: Int, date: LocalDate): AttStatus {
        db.attendanceFor(studentId, date)?.let { return it.status }
        if (db.isHoliday(date.toString())) return AttStatus.NOT_MARKED   // holidays never count as absent
        val today = LocalDate.now()
        return when {
            date.isAfter(today) -> AttStatus.NOT_MARKED
            date.isEqual(today) -> if (LocalTime.now().isAfter(campus().cutoff)) AttStatus.ABSENT else AttStatus.NOT_MARKED
            else -> AttStatus.ABSENT
        }
    }

    /**
     * Status that would be recorded right now, or an error when the window is not open.
     * Window: before openTime → not open · openTime..lateAfter → Present · lateAfter..cutoff → Late · after cutoff → closed.
     */
    fun windowStatusNow(): Pair<AttStatus?, String?> {
        val cfg = campus()
        if (isHoliday(LocalDate.now())) {
            return null to "Today is marked as a holiday. Attendance is not required."
        }
        val now = LocalTime.now()
        return when {
            now.isBefore(cfg.openTime) -> null to "Attendance opens at ${cfg.openTime}. Please come back then."
            !now.isAfter(cfg.lateAfter) -> AttStatus.PRESENT to null
            !now.isAfter(cfg.cutoff) -> AttStatus.LATE to null
            else -> null to "Self-marking is closed for today (cutoff ${cfg.cutoff}). Contact staff for assisted attendance."
        }
    }

    fun markSelf(student: User, lat: Double, lng: Double, accuracy: Double?, distance: Double): Result<AttendanceRec> {
        val today = LocalDate.now()
        if (db.attendanceFor(student.id, today) != null) {
            return Result.failure(Exception("Attendance already marked today. Duplicate entries are blocked."))
        }
        val (status, err) = windowStatusNow()
        if (status == null) return Result.failure(Exception(err ?: "Attendance window closed."))
        val time = nowClock()
        db.upsertAttendance(
            student.id, today, status, time, lat, lng, accuracy, distance,
            "Location + Biometric", "Verified ${distance.toInt()}m from campus", null
        )
        val rec = db.attendanceFor(student.id, today)!!
        db.insertAudit(student.id, "ATTENDANCE_MARKED", "${student.registerNo} self-marked ${status.label} at $time (${distance.toInt()}m)")
        db.insertNotif("USER:${student.id}", "Attendance marked", "You are marked ${status.label} at $time.", "success")
        db.insertNotif("ROLE:STAFF", "${student.name} marked ${status.label}", "${student.registerNo} · $time · ${distance.toInt()}m from gate", "info")
        db.insertNotif("ROLE:ADMIN", "${student.name} marked ${status.label}", "${student.registerNo} · $time · ${distance.toInt()}m from gate", "info")
        return Result.success(rec)
    }

    fun manualMark(actor: User, student: User, status: AttStatus, reason: String): String? {
        if (reason.isBlank()) return "An audit reason is required for manual attendance."
        val today = LocalDate.now()
        db.upsertAttendance(
            student.id, today, status, nowClock(), null, null, null, null,
            "Staff assisted", reason.trim(), actor.id
        )
        db.insertAudit(actor.id, "MANUAL_ATTENDANCE", "${student.registerNo} set to ${status.label} · reason: ${reason.trim()}")
        db.insertNotif("USER:${student.id}", "Attendance updated by staff", "You are marked ${status.label} today. Reason: ${reason.trim()}", "info")
        return null
    }

    fun recordsFor(date: LocalDate): List<AttendanceRec> = db.recordsForDate(date)
    fun recentRecords(studentId: Int, limit: Int): List<AttendanceRec> = db.recentForStudent(studentId, limit)

    /** Day-by-day statuses over the past [days] working days (weekends + holidays skipped), newest first. */
    fun history(studentId: Int, days: Int): List<Pair<LocalDate, AttStatus>> {
        val out = mutableListOf<Pair<LocalDate, AttStatus>>()
        var d = LocalDate.now()
        var guard = 0
        while (out.size < days && guard < days * 4 + 30) {
            guard++
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            if (!weekend && !db.isHoliday(d.toString())) {
                out.add(d to statusFor(studentId, d))
            }
            d = d.minusDays(1)
        }
        return out
    }

    fun weekStrip(studentId: Int): List<Pair<String, AttStatus>> {
        val monday = LocalDate.now().with(DayOfWeek.MONDAY)
        return (0..6).map { i ->
            val date = monday.plusDays(i.toLong())
            val label = date.dayOfWeek.name.first().toString()
            label to if (date.isAfter(LocalDate.now())) AttStatus.NOT_MARKED else statusFor(studentId, date)
        }
    }

    fun attendanceRate(studentId: Int): Int {
        val days = history(studentId, 14).filter { it.second != AttStatus.NOT_MARKED }
        if (days.isEmpty()) return 100
        val attended = days.count { it.second in listOf(AttStatus.PRESENT, AttStatus.LATE, AttStatus.OD, AttStatus.HALF_DAY, AttStatus.PERMISSION) }
        return (attended * 100) / days.size
    }

    // ---------- requests ----------

    fun submitRequest(student: User, type: ReqType, date: LocalDate, reason: String): String? {
        if (reason.trim().length < 4) return "Please describe the reason (at least a few words)."
        db.insertRequest(student.id, type, date, reason.trim())
        db.insertNotif("ROLE:STAFF", "New ${type.label} request", "${student.name} (${student.registerNo}) · $date · ${reason.trim()}", "warn")
        db.insertNotif("ROLE:ADMIN", "New ${type.label} request", "${student.name} (${student.registerNo}) · $date · ${reason.trim()}", "warn")
        db.insertAudit(student.id, "REQUEST_SUBMITTED", "${type.label} for $date")
        return null
    }

    fun requestsFor(studentId: Int): List<LeaveReq> = db.requestsForStudent(studentId)
    fun pendingRequests(): List<LeaveReq> = db.requests(onlyPending = true)
    fun allRequests(): List<LeaveReq> = db.requests(onlyPending = false)

    fun decideRequest(id: Int, approver: User, approved: Boolean) {
        val req = db.requestById(id) ?: return
        db.decideRequest(id, approver.id, if (approved) ReqStatus.APPROVED else ReqStatus.REJECTED)
        if (approved) {
            val status = when (req.type) {
                ReqType.OD -> AttStatus.OD
                ReqType.HALF_DAY -> AttStatus.HALF_DAY
                ReqType.FULL_DAY -> AttStatus.LEAVE
                ReqType.PERMISSION -> AttStatus.PERMISSION
            }
            val date = LocalDate.parse(req.date)
            db.upsertAttendance(
                req.studentId, date, status, nowClock(), null, null, null, null,
                "Approved ${req.type.label} request", req.reason, approver.id
            )
        }
        val verdict = if (approved) "approved" else "rejected"
        db.insertNotif(
            "USER:${req.studentId}",
            "Request $verdict",
            "Your ${req.type.label} request for ${req.date} was $verdict by ${approver.name}.",
            if (approved) "success" else "error"
        )
        db.insertAudit(approver.id, "REQUEST_${verdict.uppercase()}", "${req.registerNo} ${req.type.label} for ${req.date}")
    }

    // ---------- notifications ----------

    private fun audiencesFor(user: User) = listOf("USER:${user.id}", "ROLE:${user.role.name}")

    fun notifsFor(user: User): List<Notif> = db.notifsFor(audiencesFor(user))
    fun unread(user: User): Int = db.unreadCount(audiencesFor(user))
    fun markAllRead(user: User) = db.markAllRead(audiencesFor(user))

    // ---------- suspicious attempts + audit ----------

    fun logAttempt(student: User, reason: String) {
        db.insertAttempt(student.id, LocalDate.now(), reason)
        db.insertNotif("ROLE:STAFF", "Suspicious attempt", "${student.name} (${student.registerNo}): $reason", "error")
        db.insertNotif("ROLE:ADMIN", "Suspicious attempt", "${student.name} (${student.registerNo}): $reason", "error")
    }

    fun attemptsToday(): List<Attempt> = db.attemptsForDate(LocalDate.now())
    fun auditRows(limit: Int = 100): List<AuditRow> = db.auditRows(limit)

    // ---------- reports ----------

    fun todaySummary(): Map<AttStatus, Int> = summaryFor(LocalDate.now())

    /** Attendance counts for any date. Unmarked students count Absent only once the day is over and it isn't a holiday. */
    fun summaryFor(date: LocalDate): Map<AttStatus, Int> {
        val recs = db.recordsForDate(date)
        val counts = recs.groupingBy { it.status }.eachCount().toMutableMap()
        val today = LocalDate.now()
        val dayFinished = date.isBefore(today) || (date.isEqual(today) && LocalTime.now().isAfter(campus().cutoff))
        if (dayFinished && !isHoliday(date)) {
            val unmarked = students().size - recs.size
            counts[AttStatus.ABSENT] = (counts[AttStatus.ABSENT] ?: 0) + unmarked.coerceAtLeast(0)
        }
        return counts
    }

    fun weeklyReport(): List<DayReport> {
        val total = students().size
        val out = mutableListOf<DayReport>()
        var d = LocalDate.now()
        val fmt = DateTimeFormatter.ofPattern("EEE")
        var guard = 0
        while (out.size < 5 && guard < 40) {
            guard++
            val weekend = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY
            if (!weekend && !isHoliday(d)) {
                val recs = db.recordsForDate(d)
                val present = recs.count { it.status in listOf(AttStatus.PRESENT, AttStatus.OD, AttStatus.HALF_DAY, AttStatus.PERMISSION) }
                val late = recs.count { it.status == AttStatus.LATE }
                val absent = (total - present - late).coerceAtLeast(0)
                out.add(DayReport(d.format(fmt), present, late, absent))
            }
            d = d.minusDays(1)
        }
        return out.reversed()
    }

    fun exportCsv(from: LocalDate, to: LocalDate): File {
        val recs = db.recordsBetween(from, to)
        val userMap = students().associateBy { it.id }
        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "classkey_attendance_${from}_$to.csv")
        file.writeText(buildString {
            appendLine("date,register_no,name,status,time,distance_m,method,note")
            recs.forEach { r ->
                val u = userMap[r.studentId]
                appendLine(
                    listOf(
                        r.date, u?.registerNo ?: "", u?.name ?: "Unknown", r.status.label,
                        r.time, r.distance?.toInt()?.toString() ?: "", r.method,
                        r.note.replace(",", ";")
                    ).joinToString(",")
                )
            }
        })
        return file
    }
}

package com.classkey.modernattendance.data

import java.time.LocalTime

enum class Role { STUDENT, STAFF, ADMIN }

enum class AttStatus(val label: String) {
    PRESENT("Present"),
    LATE("Late"),
    OD("On Duty"),
    HALF_DAY("Half Day"),
    LEAVE("Leave"),
    PERMISSION("Permission"),
    ABSENT("Absent"),
    NOT_MARKED("Not Marked")
}

enum class ReqType(val label: String) {
    OD("OD"),
    HALF_DAY("Half Day"),
    FULL_DAY("Full Day"),
    PERMISSION("Permission")
}

enum class ReqStatus { PENDING, APPROVED, REJECTED }

data class User(
    val id: Int,
    val role: Role,
    val name: String,
    val email: String,
    val mobile: String,
    val registerNo: String?,
    val department: String,
    val year: String,
    val biometricEnabled: Boolean,
    val deviceId: String?,
    val photoPath: String?
) {
    val initials: String
        get() = name.split(" ").filter { it.isNotBlank() }.take(2)
            .joinToString("") { it.first().uppercase() }.ifBlank { "CK" }
}

data class AttendanceRec(
    val id: Int,
    val studentId: Int,
    val date: String,
    val status: AttStatus,
    val time: String,
    val latitude: Double?,
    val longitude: Double?,
    val accuracy: Double?,
    val distance: Double?,
    val method: String,
    val note: String,
    val markedBy: Int?
)

data class LeaveReq(
    val id: Int,
    val studentId: Int,
    val studentName: String,
    val registerNo: String,
    val type: ReqType,
    val date: String,
    val reason: String,
    val status: ReqStatus,
    val decidedByName: String?,
    val createdAt: String
)

data class Notif(
    val id: Int,
    val title: String,
    val body: String,
    val tone: String,
    val createdAt: String,
    val read: Boolean
)

data class AuditRow(
    val id: Int,
    val actorName: String,
    val action: String,
    val detail: String,
    val createdAt: String
)

data class Attempt(
    val id: Int,
    val studentName: String,
    val registerNo: String,
    val reason: String,
    val time: String
)

data class CampusCfg(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Double,
    val openTime: LocalTime,   // attendance opens (before this, marking is not allowed)
    val lateAfter: LocalTime,  // Present up to here, Late afterwards
    val cutoff: LocalTime      // after this, self-marking closes and unmarked = Absent
)

data class Holiday(val date: String, val note: String)

data class DayReport(
    val label: String,
    val present: Int,
    val late: Int,
    val absent: Int
)

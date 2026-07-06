package com.classkey.modernattendance.ui

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.outlined.ChevronLeft
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.classkey.modernattendance.AppViewModel
import com.classkey.modernattendance.data.AttStatus
import com.classkey.modernattendance.data.Role
import com.classkey.modernattendance.data.User
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ───────────────────────── Staff / Admin dashboard ─────────────────────────

@Composable
fun StaffDashboard(vm: AppViewModel, onTab: (String) -> Unit, onApprovals: () -> Unit, onAudit: () -> Unit) {
    val user = vm.user ?: return
    val reload = vm.reload
    val summary = remember(reload) { vm.repo.todaySummary() }
    val students = remember(reload) { vm.repo.students() }
    val records = remember(reload) { vm.repo.recordsFor(LocalDate.now()) }
    val pending = remember(reload) { vm.repo.pendingRequests() }
    val attempts = remember(reload) { vm.repo.attemptsToday() }
    val unread = remember(reload) { vm.repo.unread(user) }
    val markedCount = records.size
    val total = students.size.coerceAtLeast(1)

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            GreetingRow(vm, unread, onBell = { onTab("Alerts") }, onAvatar = { onTab("Profile") })
        }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(CK.Primary, Color(0xFF0DADA5))))
                    .padding(20.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).clip(CircleShape).background(Color(0xFF6BF598)))
                            Spacer(Modifier.width(6.dp))
                            Text("LIVE · Daily entry sheet", color = Color.White.copy(alpha = .85f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Text("$markedCount of ${students.size} students marked", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                    }
                    ProgressRing(markedCount.toFloat() / total, "${markedCount * 100 / total}%", color = Color.White)
                }
            }
        }
        item {
            SectionHeader("Today's Summary")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Present", summary[AttStatus.PRESENT] ?: 0, CK.Success, Modifier.weight(1f))
                StatCard("Late", summary[AttStatus.LATE] ?: 0, CK.Warning, Modifier.weight(1f))
                StatCard("Absent", summary[AttStatus.ABSENT] ?: 0, CK.Error, Modifier.weight(1f))
                StatCard("OD/Leave", (summary[AttStatus.OD] ?: 0) + (summary[AttStatus.HALF_DAY] ?: 0) + (summary[AttStatus.LEAVE] ?: 0) + (summary[AttStatus.PERMISSION] ?: 0), CK.Primary, Modifier.weight(1f))
            }
        }
        item {
            SectionHeader("Quick Actions")
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Outlined.Groups, "Student List", "Live status", CK.Primary, CK.SoftBlue, Modifier.weight(1f)) { onTab("Students") }
                    QuickAction(Icons.Outlined.AssignmentTurnedIn, "Approvals", "${pending.size} pending", CK.Warning, CK.SoftAmber, Modifier.weight(1f), badge = pending.size) { onApprovals() }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    QuickAction(Icons.Outlined.BarChart, "Reports", "Daily & weekly", CK.Purple, CK.SoftPurple, Modifier.weight(1f)) { onTab("Reports") }
                    if (user.role == Role.ADMIN) {
                        QuickAction(Icons.Outlined.Notifications, "Audit Log", "All actions", CK.Teal, CK.SoftTeal, Modifier.weight(1f)) { onAudit() }
                    } else {
                        QuickAction(Icons.Outlined.Notifications, "Alerts", null, CK.Teal, CK.SoftTeal, Modifier.weight(1f), badge = unread) { onTab("Alerts") }
                    }
                }
            }
        }
        if (attempts.isNotEmpty()) {
            item {
                SectionHeader("Suspicious Attempts Today")
                AppCard(padding = 0) {
                    attempts.take(5).forEach { a ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.Top) {
                            Icon(Icons.Outlined.Warning, null, Modifier.size(17.dp), tint = CK.Warning)
                            Spacer(Modifier.width(10.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${a.studentName} · ${a.registerNo}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                                Text(a.reason, fontSize = 11.sp, color = CK.Muted)
                            }
                            Text(a.time, fontSize = 11.sp, color = CK.Muted)
                        }
                    }
                }
            }
        }
        if (records.isNotEmpty()) {
            item { SectionHeader("Live Attendance", "Full list") { onTab("Students") } }
            items(records.take(5)) { rec -> AttendanceRow(vm, rec) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ───────────────────────── Student list + manual mark ─────────────────────────

@Composable
fun StudentListScreen(vm: AppViewModel) {
    val user = vm.user ?: return
    val reload = vm.reload
    var search by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<AttStatus?>(null) }
    var detailTarget by remember { mutableStateOf<User?>(null) }
    var showAddMenu by remember { mutableStateOf(false) }
    var showAddStudent by remember { mutableStateOf(false) }
    var showAddStaff by remember { mutableStateOf(false) }
    val students = remember(reload) { vm.repo.students() }
    val today = LocalDate.now()
    val statuses = remember(reload) { students.associate { it.id to vm.repo.statusFor(it.id, today) } }

    val visible = students.filter { s ->
        val matchSearch = search.isBlank() ||
            s.name.contains(search, ignoreCase = true) ||
            (s.registerNo ?: "").contains(search, ignoreCase = true)
        val st = statuses[s.id] ?: AttStatus.NOT_MARKED
        val matchFilter = filter == null || st == filter ||
            (filter == AttStatus.OD && st in listOf(AttStatus.OD, AttStatus.HALF_DAY, AttStatus.LEAVE, AttStatus.PERMISSION))
        matchSearch && matchFilter
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Student List", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
                    Text("${students.size} students · tap a row for manual assist", fontSize = 12.sp, color = CK.Muted)
                }
                if (user.role == Role.ADMIN) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(CK.Primary).clickable { showAddMenu = true },
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Outlined.Add, "Add account", tint = Color.White) }
                }
            }
        }
        item {
            OutlinedTextField(
                search, { search = it },
                placeholder = { Text("Search name or register number…", fontSize = 13.sp) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, Modifier.size(18.dp), tint = CK.Muted) },
                trailingIcon = {
                    if (search.isNotEmpty()) IconButton(onClick = { search = "" }) {
                        Icon(Icons.Outlined.Close, null, Modifier.size(16.dp), tint = CK.Muted)
                    }
                },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("All", filter == null) { filter = null }
                FilterPill("Present", filter == AttStatus.PRESENT) { filter = AttStatus.PRESENT }
                FilterPill("Late", filter == AttStatus.LATE) { filter = AttStatus.LATE }
                FilterPill("Absent", filter == AttStatus.ABSENT) { filter = AttStatus.ABSENT }
                FilterPill("OD+", filter == AttStatus.OD) { filter = AttStatus.OD }
            }
        }
        if (visible.isEmpty()) {
            item { EmptyCard("No students found", "Try a different search or filter.") }
        }
        items(visible) { student ->
            val st = statuses[student.id] ?: AttStatus.NOT_MARKED
            val rec = remember(reload, student.id) { vm.repo.attendanceFor(student.id, today) }
            AppCard(padding = 14) {
                Row(
                    Modifier.clickable { detailTarget = student },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Avatar(student, size = 40)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(student.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                        Text(student.registerNo ?: "", fontSize = 11.sp, color = CK.Muted)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        StatusChip(st)
                        if (rec != null) {
                            Spacer(Modifier.height(3.dp))
                            Text(rec.time, fontSize = 10.sp, color = CK.Muted)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    detailTarget?.let { target ->
        StudentDetailDialog(vm, target, onClose = { detailTarget = null })
    }
    if (showAddMenu) {
        AlertDialog(
            onDismissRequest = { showAddMenu = false },
            containerColor = CK.Card,
            title = { Text("Add account", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PrimaryButton("Add Student") { showAddMenu = false; showAddStudent = true }
                    GhostButton("Add Staff / Admin") { showAddMenu = false; showAddStaff = true }
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = { showAddMenu = false }) { Text("Cancel", color = CK.Muted) } }
        )
    }
    if (showAddStudent) AddStudentDialog(vm, onClose = { showAddStudent = false })
    if (showAddStaff) AddStaffDialog(vm, onClose = { showAddStaff = false })
}

@Composable
private fun StudentDetailDialog(vm: AppViewModel, student: User, onClose: () -> Unit) {
    val actor = vm.user ?: return
    val isAdmin = actor.role == Role.ADMIN
    val history = remember(student.id, vm.reload) { vm.repo.history(student.id, 6) }
    var status by remember { mutableStateOf(AttStatus.PRESENT) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    var editing by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf(student.name) }
    var email by remember { mutableStateOf(student.email) }
    var mobile by remember { mutableStateOf(student.mobile) }
    var reg by remember { mutableStateOf(student.registerNo ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CK.Card,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Avatar(student, size = 40)
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(student.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                    Text(student.registerNo ?: student.email, fontSize = 12.sp, color = CK.Muted)
                }
            }
        },
        text = {
            Column(Modifier.heightIn(max = 440.dp).verticalScroll(rememberScrollState())) {
                // recent history
                Text("RECENT", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CK.Muted, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(6.dp))
                if (history.all { it.second == AttStatus.NOT_MARKED }) {
                    Text("No records yet.", fontSize = 12.sp, color = CK.Muted)
                } else {
                    history.forEach { (date, st) ->
                        Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                date.format(DateTimeFormatter.ofPattern("EEE, d MMM")),
                                fontSize = 12.sp, color = CK.Text, modifier = Modifier.weight(1f)
                            )
                            StatusChip(st)
                        }
                    }
                }

                if (isAdmin && editing) {
                    Spacer(Modifier.height(14.dp))
                    Text("EDIT DETAILS", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CK.Muted, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(name, { name = it; error = null }, label = { Text("Name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(email, { email = it; error = null }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(mobile, { mobile = it.filter { c -> c.isDigit() } }, label = { Text("Mobile") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(reg, { reg = it; error = null }, label = { Text("Register no") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Save details") {
                        val err = vm.repo.editStudent(student.id, name, email, mobile, reg, actor)
                        if (err != null) error = err
                        else { vm.bump(); vm.say("Student details updated."); onClose() }
                    }
                } else {
                    // manual attendance
                    Spacer(Modifier.height(14.dp))
                    Text("MANUAL ASSIST", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CK.Muted, letterSpacing = 1.2.sp)
                    Text("Use only when phone verification fails. Reason is logged.", fontSize = 11.sp, color = CK.Muted)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(AttStatus.PRESENT, AttStatus.LATE, AttStatus.OD, AttStatus.ABSENT).forEach { s ->
                            FilterPill(s.label, status == s) { status = s }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        reason, { reason = it; error = null },
                        label = { Text("Audit reason (required)") },
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    PrimaryButton("Save manual attendance") {
                        val err = vm.repo.manualMark(actor, student, status, reason)
                        if (err != null) error = err
                        else { vm.bump(); vm.say("${student.registerNo} marked ${status.label}."); onClose() }
                    }
                }

                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = CK.Error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                if (isAdmin) {
                    Spacer(Modifier.height(14.dp))
                    Text("ADMIN", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CK.Muted, letterSpacing = 1.2.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GhostButton(if (editing) "Cancel edit" else "Edit", Modifier.weight(1f)) { editing = !editing }
                        GhostButton("Reset password", Modifier.weight(1f)) {
                            vm.repo.resetStudentPassword(student, actor); vm.bump(); vm.say("Password reset to ChangeMe123!")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            if (!confirmDelete) confirmDelete = true
                            else {
                                val err = vm.repo.deleteStudent(student, actor)
                                if (err != null) { error = err; confirmDelete = false }
                                else { vm.bump(); vm.say("${student.registerNo} removed."); onClose() }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = CK.Error)
                    ) { Text(if (confirmDelete) "Tap again to confirm delete" else "Delete student", fontWeight = FontWeight.Bold) }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Close", color = CK.Muted) } }
    )
}

@Composable
private fun AddStaffDialog(vm: AppViewModel, onClose: () -> Unit) {
    val actor = vm.user ?: return
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var role by remember { mutableStateOf(Role.STAFF) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CK.Card,
        title = { Text("Add staff / admin", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("Staff", role == Role.STAFF) { role = Role.STAFF }
                    FilterPill("Admin", role == Role.ADMIN) { role = Role.ADMIN }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(mobile, { mobile = it.filter { c -> c.isDigit() } }, label = { Text("Mobile") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Text("Default password: ChangeMe123!", fontSize = 11.sp, color = CK.Muted)
                if (error != null) Text(error!!, color = CK.Error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = vm.repo.addStaff(name, email, mobile, role, actor)
                if (err != null) error = err
                else { vm.bump(); vm.say("${role.name.lowercase()} account added."); onClose() }
            }) { Text("Add", fontWeight = FontWeight.Bold, color = CK.Primary) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", color = CK.Muted) } }
    )
}

@Composable
private fun AddStudentDialog(vm: AppViewModel, onClose: () -> Unit) {
    val actor = vm.user ?: return
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var reg by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CK.Card,
        title = { Text("Add student", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(email, { email = it }, label = { Text("College email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(mobile, { mobile = it.filter { c -> c.isDigit() } }, label = { Text("Mobile") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(reg, { reg = it }, label = { Text("Register no") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Text("Default password: ChangeMe123!", fontSize = 11.sp, color = CK.Muted)
                if (error != null) Text(error!!, color = CK.Error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = vm.repo.addStudent(name, email, mobile, reg, actor)
                if (err != null) error = err
                else {
                    vm.bump()
                    vm.say("Student added. They can log in with ChangeMe123!")
                    onClose()
                }
            }) { Text("Add", fontWeight = FontWeight.Bold, color = CK.Primary) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", color = CK.Muted) } }
    )
}

// ───────────────────────────── Approvals ─────────────────────────────

@Composable
fun ApprovalsScreen(vm: AppViewModel, asOverlay: Boolean = false, onClose: (() -> Unit)? = null) {
    val user = vm.user ?: return
    val reload = vm.reload
    val pending = remember(reload) { vm.repo.pendingRequests() }
    val decided = remember(reload) { vm.repo.allRequests().filter { it.status != com.classkey.modernattendance.data.ReqStatus.PENDING } }

    Column(Modifier.fillMaxSize().background(CK.Bg)) {
        if (asOverlay && onClose != null) OverlayHeader("Pending Approvals", onClose)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!asOverlay) {
                item {
                    Spacer(Modifier.height(4.dp))
                    ScreenTitle("Approvals", "OD, leave and permission requests.")
                }
            }
            if (pending.isEmpty()) {
                item { EmptyCard("All caught up!", "No pending approvals right now.") }
            }
            items(pending) { req ->
                RequestCard(req, canDecide = true) { approved ->
                    vm.repo.decideRequest(req.id, user, approved)
                    vm.bump()
                    vm.say(if (approved) "Request approved — attendance updated." else "Request rejected.")
                }
            }
            if (decided.isNotEmpty()) {
                item { SectionHeader("Decided") }
                items(decided.take(10)) { req -> RequestCard(req, canDecide = false, onDecide = {}) }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

// ───────────────────────────── Reports ─────────────────────────────

@Composable
fun ReportsScreen(vm: AppViewModel) {
    val reload = vm.reload
    val context = LocalContext.current
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    val isToday = selectedDate.isEqual(LocalDate.now())
    val summary = remember(reload, selectedDate) { vm.repo.summaryFor(selectedDate) }
    val weekly = remember(reload) { vm.repo.weeklyReport() }
    val records = remember(reload, selectedDate) { vm.repo.recordsFor(selectedDate) }

    fun shareCsv(from: LocalDate, to: LocalDate) {
        val file = vm.repo.exportCsv(from, to)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "ClassKey attendance $from to $to")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share attendance CSV"))
        vm.say("CSV generated — choose an app to share it.")
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            ScreenTitle("Reports", "Daily entry attendance, not subject-wise.")
        }
        item {
            AppCard(padding = 10) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { selectedDate = selectedDate.minusDays(1) }) {
                        Icon(Icons.Outlined.ChevronLeft, "Previous day", tint = CK.Primary)
                    }
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            if (isToday) "Today" else selectedDate.format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                            fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CK.Text
                        )
                        if (isToday) Text(selectedDate.format(DateTimeFormatter.ofPattern("d MMM yyyy")), fontSize = 11.sp, color = CK.Muted)
                    }
                    IconButton(onClick = { if (!isToday) selectedDate = selectedDate.plusDays(1) }, enabled = !isToday) {
                        Icon(Icons.Outlined.ChevronRight, "Next day", tint = if (isToday) CK.Muted.copy(alpha = .4f) else CK.Primary)
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Present", summary[AttStatus.PRESENT] ?: 0, CK.Success, Modifier.weight(1f))
                StatCard("Late", summary[AttStatus.LATE] ?: 0, CK.Warning, Modifier.weight(1f))
                StatCard("Absent", summary[AttStatus.ABSENT] ?: 0, CK.Error, Modifier.weight(1f))
            }
        }
        item {
            AppCard {
                Text("Attendance by day", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                Spacer(Modifier.height(14.dp))
                WeeklyBarChart(weekly)
            }
        }
        item {
            SectionHeader("Export")
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Export selected day (CSV)", icon = Icons.Outlined.Download) { shareCsv(selectedDate, selectedDate) }
                GhostButton("Export last 7 days (CSV)", icon = Icons.Outlined.Download) { shareCsv(LocalDate.now().minusDays(7), LocalDate.now()) }
                GhostButton("Export last 30 days (CSV)", icon = Icons.Outlined.Download) { shareCsv(LocalDate.now().minusDays(30), LocalDate.now()) }
            }
        }
        if (records.isEmpty()) {
            item { EmptyCard("No records for this day", "Records appear here as students mark attendance.") }
        } else {
            item { SectionHeader(if (isToday) "Today's Records" else "Records") }
            items(records) { rec -> AttendanceRow(vm, rec) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ───────────────────────────── Audit log ─────────────────────────────

@Composable
fun AuditScreen(vm: AppViewModel, onClose: () -> Unit) {
    val reload = vm.reload
    val rows = remember(reload) { vm.repo.auditRows(150) }
    Column(Modifier.fillMaxSize().background(CK.Bg)) {
        OverlayHeader("Audit Log", onClose)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows.isEmpty()) item { EmptyCard("No audit entries", "Actions will be recorded here.") }
            items(rows) { row ->
                AppCard(padding = 13) {
                    Row {
                        Column(Modifier.weight(1f)) {
                            Text(row.action.replace('_', ' '), fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                            Text(row.detail, fontSize = 12.sp, color = CK.Muted)
                            Text("by ${row.actorName}", fontSize = 11.sp, color = CK.Primary)
                        }
                        Text(row.createdAt, fontSize = 10.sp, color = CK.Muted)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

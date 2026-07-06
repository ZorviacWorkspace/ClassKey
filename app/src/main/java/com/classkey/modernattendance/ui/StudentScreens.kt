package com.classkey.modernattendance.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.classkey.modernattendance.AppViewModel
import com.classkey.modernattendance.data.AttStatus
import com.classkey.modernattendance.data.AttendanceRec
import com.classkey.modernattendance.data.ReqStatus
import com.classkey.modernattendance.data.ReqType
import com.classkey.modernattendance.hw.LocationClient
import com.classkey.modernattendance.hw.runBiometric
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

// ───────────────────────────── Student Home ─────────────────────────────

@Composable
fun StudentHome(vm: AppViewModel, onMark: () -> Unit, onTab: (String) -> Unit) {
    val user = vm.user ?: return
    val reload = vm.reload
    val today = LocalDate.now()
    val todayRec = remember(reload) { vm.repo.attendanceFor(user.id, today) }
    val week = remember(reload) { vm.repo.weekStrip(user.id) }
    val rate = remember(reload) { vm.repo.attendanceRate(user.id) }
    val recent = remember(reload) { vm.repo.recentRecords(user.id, 4) }
    val unread = remember(reload) { vm.repo.unread(user) }
    val cfg = remember(reload) { vm.repo.campus() }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(Modifier.height(4.dp))
            GreetingRow(vm, unread, onBell = { onTab("Alerts") }, onAvatar = { onTab("Profile") })
        }
        item {
            // Today's status — blue hero card
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(CK.Primary, Color(0xFF0DADA5))))
                    .padding(20.dp)
            ) {
                Column {
                    Text("Today's Entry Attendance", color = Color.White.copy(alpha = .85f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                        color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Spacer(Modifier.height(10.dp))
                    if (todayRec != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PillChip(todayRec.status.label, Color.White.copy(alpha = .22f), Color.White)
                            Spacer(Modifier.width(8.dp))
                            Text("Marked · ${todayRec.time}", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            "Opens ${cfg.openTime} · Present till ${cfg.lateAfter} · Late till ${cfg.cutoff}",
                            color = Color.White.copy(alpha = .85f), fontSize = 12.sp
                        )
                    }
                }
            }
        }
        item {
            // Mark attendance card
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(50.dp).clip(CircleShape)
                            .background(if (todayRec != null) CK.SoftGreen else CK.SoftBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (todayRec != null) Icons.Outlined.Check else Icons.Outlined.Fingerprint,
                            null, Modifier.size(26.dp),
                            tint = if (todayRec != null) CK.Success else CK.Primary
                        )
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (todayRec != null) "You're ${todayRec.status.label} today" else "Ready to mark",
                            fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CK.Text
                        )
                        Text(
                            if (todayRec != null) "Entry attendance recorded." else "Location + fingerprint verification",
                            fontSize = 12.sp, color = CK.Muted
                        )
                    }
                }
                if (todayRec == null) {
                    Spacer(Modifier.height(14.dp))
                    PrimaryButton("Mark Attendance", icon = Icons.Outlined.Fingerprint) { onMark() }
                }
            }
        }
        item {
            SectionHeader("This Week", "Full history") { onTab("History") }
            AppCard {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    week.forEach { (label, status) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(label, fontSize = 11.sp, color = CK.Muted, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(6.dp))
                            Box(
                                Modifier.size(30.dp).clip(CircleShape).background(
                                    if (status == AttStatus.NOT_MARKED) Color(0xFFEDF1F7) else statusColor(status)
                                ),
                                contentAlignment = Alignment.Center
                            ) {
                                when (status) {
                                    AttStatus.PRESENT, AttStatus.OD, AttStatus.HALF_DAY, AttStatus.PERMISSION, AttStatus.LEAVE ->
                                        Icon(Icons.Outlined.Check, null, Modifier.size(15.dp), tint = Color.White)
                                    AttStatus.LATE -> Text("L", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    AttStatus.ABSENT -> Text("✕", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    AttStatus.NOT_MARKED -> Unit
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Attendance rate (14 days)", fontSize = 12.sp, color = CK.Muted, modifier = Modifier.weight(1f))
                    Text("$rate%", fontSize = 14.sp, fontWeight = FontWeight.ExtraBold, color = if (rate >= 75) CK.Success else CK.Error)
                }
            }
        }
        item {
            SectionHeader("Quick Actions")
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                QuickAction(Icons.Outlined.History, "History", null, CK.Primary, CK.SoftBlue, Modifier.weight(1f)) { onTab("History") }
                QuickAction(Icons.Outlined.Description, "Requests", null, CK.Purple, CK.SoftPurple, Modifier.weight(1f)) { onTab("Requests") }
                QuickAction(Icons.Outlined.Notifications, "Alerts", null, CK.Teal, CK.SoftTeal, Modifier.weight(1f), badge = unread) { onTab("Alerts") }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionHeader("Recent", "See all") { onTab("History") } }
            items(recent) { rec -> AttendanceRow(vm, rec) }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun GreetingRow(vm: AppViewModel, unread: Int, onBell: () -> Unit, onAvatar: () -> Unit) {
    val user = vm.user ?: return
    val hour = LocalDateTime.now().hour
    val greeting = when {
        hour < 12 -> "Good morning,"
        hour < 17 -> "Good afternoon,"
        else -> "Good evening,"
    }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(greeting, fontSize = 12.sp, color = CK.Muted, fontWeight = FontWeight.Medium)
            Text("${user.name} 👋", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CK.Text)
        }
        Box {
            IconButton(onClick = onBell) {
                Icon(Icons.Outlined.Notifications, "Alerts", tint = CK.Text)
            }
            if (unread > 0) {
                Box(
                    Modifier.align(Alignment.TopEnd).padding(6.dp).size(15.dp).clip(CircleShape).background(CK.Error),
                    contentAlignment = Alignment.Center
                ) { Text(unread.coerceAtMost(9).toString(), color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold) }
            }
        }
        Spacer(Modifier.width(6.dp))
        Box(Modifier.clickable { onAvatar() }) { Avatar(user, size = 40) }
    }
}

// ───────────────────────── Mark Attendance flow ─────────────────────────

@Composable
fun MarkAttendanceScreen(
    vm: AppViewModel,
    activity: FragmentActivity,
    requestLocationPermission: (onGranted: () -> Unit) -> Unit,
    onClose: () -> Unit,
    onMarked: (AttendanceRec) -> Unit
) {
    val user = vm.user ?: return
    var locBusy by remember { mutableStateOf(false) }
    var locVerified by remember { mutableStateOf(false) }
    var distance by remember { mutableStateOf<Double?>(null) }
    var accuracy by remember { mutableStateOf<Double?>(null) }
    var lat by remember { mutableStateOf<Double?>(null) }
    var lng by remember { mutableStateOf<Double?>(null) }
    var stepError by remember { mutableStateOf<String?>(null) }
    var bioBusy by remember { mutableStateOf(false) }
    val cfg = remember { vm.repo.campus() }
    val locationClient = remember { LocationClient(activity.applicationContext) }

    fun verifyLocation() {
        stepError = null
        locBusy = true
        locationClient.current { location, error ->
            locBusy = false
            if (location == null) {
                stepError = error ?: "Could not fetch location."
                return@current
            }
            lat = location.latitude
            lng = location.longitude
            accuracy = location.accuracy.toDouble()
            val d = vm.repo.distanceToCampus(location.latitude, location.longitude)
            distance = d
            if (d <= cfg.radiusMeters) {
                locVerified = true
            } else {
                locVerified = false
                stepError = "You are ${d.toInt()}m from ${cfg.name} — outside the ${cfg.radiusMeters.toInt()}m allowed radius."
                vm.repo.logAttempt(user, "Location out of range: ${d.toInt()}m away")
            }
        }
    }

    fun verifyBiometricAndSubmit() {
        stepError = null
        bioBusy = true
        runBiometric(
            activity,
            "Confirm attendance",
            "Verify fingerprint or device lock to mark today's attendance.",
            onSuccess = {
                bioBusy = false
                val result = vm.repo.markSelf(user, lat ?: 0.0, lng ?: 0.0, accuracy, distance ?: 0.0)
                result.onSuccess { rec ->
                    vm.bump()
                    onMarked(rec)
                }.onFailure { stepError = it.message }
            },
            onError = { msg ->
                bioBusy = false
                stepError = msg
                vm.repo.logAttempt(user, "Biometric failed: $msg")
            }
        )
    }

    Column(Modifier.fillMaxSize().background(CK.Bg)) {
        OverlayHeader("Mark Attendance", onClose)
        LazyColumn(
            Modifier.weight(1f).padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Daily entry sheet", fontSize = 11.sp, color = CK.Muted)
                            Text(
                                LocalDate.now().format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                                fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CK.Text
                            )
                            Text("${cfg.name} · ${cfg.radiusMeters.toInt()}m radius", fontSize = 12.sp, color = CK.Muted)
                        }
                        PillChip("LIVE", CK.SoftGreen, CK.Success)
                    }
                }
            }
            item {
                AppCard {
                    StepRow(1, "Verify location", "GPS position checked against the campus geofence.", locVerified)
                    AnimatedVisibility(locBusy) {
                        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 8.dp), color = CK.Primary)
                    }
                    if (lat != null && distance != null) {
                        Text(
                            String.format(
                                java.util.Locale.US,
                                "Lat %.5f · Lng %.5f · ±%dm accuracy · %dm from gate",
                                lat, lng, accuracy?.toInt() ?: 0, distance!!.toInt()
                            ),
                            fontSize = 11.sp, color = CK.Muted
                        )
                        Spacer(Modifier.height(6.dp))
                    }
                    if (!locVerified) {
                        Spacer(Modifier.height(6.dp))
                        GhostButton(if (lat == null) "Verify Location" else "Retry Location", icon = Icons.Outlined.LocationOn) {
                            requestLocationPermission { verifyLocation() }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    StepRow(2, "Verify biometric", "Android fingerprint / device lock. Nothing raw is stored.", false)
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            PulsingCircleButton(
                                Icons.Outlined.Fingerprint,
                                enabled = locVerified,
                                scanning = bioBusy
                            ) { verifyBiometricAndSubmit() }
                            Text(
                                when {
                                    bioBusy -> "Waiting for fingerprint…"
                                    locVerified -> "Tap to verify and submit"
                                    else -> "Verify location first"
                                },
                                fontSize = 12.sp, color = CK.Muted
                            )
                        }
                    }
                    if (stepError != null) {
                        Spacer(Modifier.height(10.dp))
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CK.SoftRed).padding(12.dp)
                        ) {
                            Text(stepError!!, color = CK.Error, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            item {
                Text(
                    "If verification keeps failing, ask staff for assisted attendance — every manual entry needs an audit reason.",
                    fontSize = 11.sp, color = CK.Muted, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp)
                )
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
fun OverlayHeader(title: String, onClose: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onClose) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back", tint = CK.Primary)
        }
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CK.Text)
    }
}

@Composable
private fun StepRow(number: Int, title: String, body: String, done: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(if (done) CK.SoftGreen else CK.SoftBlue),
            contentAlignment = Alignment.Center
        ) {
            if (done) Icon(Icons.Outlined.Check, null, Modifier.size(19.dp), tint = CK.Success)
            else Text(number.toString(), color = CK.Primary, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = CK.Text)
            Text(body, fontSize = 11.sp, color = CK.Muted)
        }
    }
}

// ─────────────────────────── Success screen ───────────────────────────

@Composable
fun SuccessScreen(rec: AttendanceRec, onDone: () -> Unit) {
    val scale by animateFloatAsState(1f, animationSpec = tween(500), label = "check")
    Column(
        Modifier.fillMaxSize().background(CK.Bg).padding(26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.size((96 * scale).dp).clip(CircleShape).background(CK.Success),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Outlined.Check, null, Modifier.size(48.dp), tint = Color.White) }
        Spacer(Modifier.height(22.dp))
        Text("Attendance Marked!", fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
        Row {
            Text("You are marked ", fontSize = 14.sp, color = CK.Muted)
            Text(rec.status.label, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = statusColor(rec.status))
        }
        Spacer(Modifier.height(24.dp))
        AppCard {
            InfoRow("Date", rec.date)
            InfoRow("Time", rec.time)
            InfoRow("Status", rec.status.label, statusColor(rec.status))
            if (rec.distance != null) InfoRow("Distance from gate", "${rec.distance.toInt()} m")
            InfoRow("Method", rec.method)
        }
        Spacer(Modifier.height(24.dp))
        PrimaryButton("Back to Home") { onDone() }
    }
}

// ─────────────────────────── History screen ───────────────────────────

@Composable
fun HistoryScreen(vm: AppViewModel) {
    val user = vm.user ?: return
    val reload = vm.reload
    var filter by remember { mutableStateOf<AttStatus?>(null) }
    val days = remember(reload) { vm.repo.history(user.id, 14) }
    val rate = remember(reload) { vm.repo.attendanceRate(user.id) }
    val records = remember(reload) { vm.repo.recentRecords(user.id, 60).associateBy { it.date } }

    val counts = remember(days) {
        mapOf(
            AttStatus.PRESENT to days.count { it.second == AttStatus.PRESENT },
            AttStatus.LATE to days.count { it.second == AttStatus.LATE },
            AttStatus.ABSENT to days.count { it.second == AttStatus.ABSENT },
            AttStatus.OD to days.count { it.second in listOf(AttStatus.OD, AttStatus.HALF_DAY, AttStatus.LEAVE, AttStatus.PERMISSION) }
        )
    }
    val visible = days.filter { (_, s) ->
        filter == null || s == filter ||
            (filter == AttStatus.OD && s in listOf(AttStatus.OD, AttStatus.HALF_DAY, AttStatus.LEAVE, AttStatus.PERMISSION))
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            ScreenTitle("Attendance History", "Daily entry records — last 14 weekdays.")
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Present", counts[AttStatus.PRESENT] ?: 0, CK.Success, Modifier.weight(1f))
                StatCard("Late", counts[AttStatus.LATE] ?: 0, CK.Warning, Modifier.weight(1f))
                StatCard("Absent", counts[AttStatus.ABSENT] ?: 0, CK.Error, Modifier.weight(1f))
                StatCard("OD/Leave", counts[AttStatus.OD] ?: 0, CK.Primary, Modifier.weight(1f))
            }
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Overall attendance", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CK.Text, modifier = Modifier.weight(1f))
                    Text("$rate%", fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = if (rate >= 75) CK.Success else CK.Error)
                }
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { rate / 100f },
                    modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(99.dp)),
                    color = if (rate >= 75) CK.Success else CK.Error,
                    trackColor = Color(0xFFE8ECF2)
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (rate >= 75) "Minimum required: 75% · You are safe ✓" else "Below 75% minimum — attendance at risk",
                    fontSize = 11.sp, color = CK.Muted
                )
            }
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
        items(visible) { (date, status) ->
            val rec = records[date.toString()]
            AppCard(padding = 14) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            date.format(DateTimeFormatter.ofPattern("EEE, d MMM")),
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CK.Text
                        )
                        if (rec != null) Text("${rec.time} · ${rec.method}", fontSize = 11.sp, color = CK.Muted)
                        else Text("No entry recorded", fontSize = 11.sp, color = CK.Muted)
                    }
                    StatusChip(status)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun FilterPill(label: String, active: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.clip(RoundedCornerShape(99.dp))
            .background(if (active) CK.Primary else CK.Card)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 7.dp)
    ) {
        Text(
            label, fontSize = 12.sp, fontWeight = FontWeight.Bold,
            color = if (active) Color.White else CK.Muted
        )
    }
}

// ─────────────────────────── Requests screen ───────────────────────────

@Composable
fun RequestsScreen(vm: AppViewModel, onNew: () -> Unit) {
    val user = vm.user ?: return
    val reload = vm.reload
    var filter by remember { mutableStateOf<ReqStatus?>(null) }
    val requests = remember(reload) { vm.repo.requestsFor(user.id) }
    val visible = requests.filter { filter == null || it.status == filter }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Requests", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
                    Text("OD · Half day · Full day · Permission", fontSize = 13.sp, color = CK.Muted)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(CK.Primary).clickable { onNew() },
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Add, "New request", tint = Color.White) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("All", filter == null) { filter = null }
                FilterPill("Pending", filter == ReqStatus.PENDING) { filter = ReqStatus.PENDING }
                FilterPill("Approved", filter == ReqStatus.APPROVED) { filter = ReqStatus.APPROVED }
                FilterPill("Rejected", filter == ReqStatus.REJECTED) { filter = ReqStatus.REJECTED }
            }
        }
        if (visible.isEmpty()) {
            item { EmptyCard("No requests", "Tap + to submit an OD, leave or permission request.") }
        }
        items(visible) { req -> RequestCard(req, canDecide = false, onDecide = { _ -> }) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
fun NewRequestScreen(vm: AppViewModel, onClose: () -> Unit) {
    val user = vm.user ?: return
    var type by remember { mutableStateOf(ReqType.OD) }
    var dayOffset by remember { mutableStateOf(0L) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val targetDate = LocalDate.now().plusDays(dayOffset)

    Column(Modifier.fillMaxSize().background(CK.Bg)) {
        OverlayHeader("New Request", onClose)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                AppCard {
                    SectionHeader("Request type")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReqType.entries.forEach { t ->
                            FilterPill(t.label, type == t) { type = t }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    SectionHeader("Date")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterPill("Today", dayOffset == 0L) { dayOffset = 0L }
                        FilterPill("Tomorrow", dayOffset == 1L) { dayOffset = 1L }
                        FilterPill("Day after", dayOffset == 2L) { dayOffset = 2L }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        targetDate.format(DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy")),
                        fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CK.Primary
                    )
                    Spacer(Modifier.height(16.dp))
                    SectionHeader("Reason")
                    OutlinedTextField(
                        reason, { reason = it; error = null },
                        placeholder = { Text("Describe the reason for this request…") },
                        modifier = Modifier.fillMaxWidth().height(110.dp),
                        shape = RoundedCornerShape(13.dp)
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = CK.Error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(16.dp))
                    PrimaryButton("Submit Request") {
                        val err = vm.repo.submitRequest(user, type, targetDate, reason)
                        if (err != null) error = err
                        else {
                            vm.bump()
                            vm.say("${type.label} request submitted for approval.")
                            onClose()
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RequestCard(req: com.classkey.modernattendance.data.LeaveReq, canDecide: Boolean, onDecide: (Boolean) -> Unit) {
    AppCard(padding = 15) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PillChip(req.type.label, CK.SoftBlue, CK.Primary)
                    Spacer(Modifier.width(8.dp))
                    Text(req.date, fontSize = 12.sp, color = CK.Muted)
                }
                Spacer(Modifier.height(6.dp))
                if (canDecide) {
                    Text("${req.studentName} · ${req.registerNo}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                }
                Text(req.reason, fontSize = 13.sp, color = CK.Text)
                if (req.decidedByName != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "${if (req.status == ReqStatus.APPROVED) "✓ Approved" else "✕ Rejected"} by ${req.decidedByName}",
                        fontSize = 11.sp, color = CK.Muted
                    )
                }
            }
            PillChip(
                req.status.name.lowercase().replaceFirstChar { it.uppercase() },
                when (req.status) {
                    ReqStatus.PENDING -> CK.SoftAmber
                    ReqStatus.APPROVED -> CK.SoftGreen
                    ReqStatus.REJECTED -> CK.SoftRed
                },
                when (req.status) {
                    ReqStatus.PENDING -> CK.Warning
                    ReqStatus.APPROVED -> CK.Success
                    ReqStatus.REJECTED -> CK.Error
                }
            )
        }
        if (canDecide && req.status == ReqStatus.PENDING) {
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("Reject", Modifier.weight(1f)) { onDecide(false) }
                PrimaryButton("Approve", Modifier.weight(1f)) { onDecide(true) }
            }
        }
    }
}

// ─────────────────────── shared attendance row ───────────────────────

@Composable
fun AttendanceRow(vm: AppViewModel, rec: AttendanceRec) {
    val student = remember(rec.studentId) { vm.repo.userById(rec.studentId) }
    AppCard(padding = 14) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(student, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(student?.name ?: "Unknown", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                Text("${student?.registerNo ?: ""} · ${rec.date} · ${rec.time}", fontSize = 11.sp, color = CK.Muted)
                Text(rec.method, fontSize = 11.sp, color = CK.Primary)
                if (rec.note.isNotBlank()) Text(rec.note, fontSize = 11.sp, color = CK.Muted)
            }
            StatusChip(rec.status)
        }
    }
}

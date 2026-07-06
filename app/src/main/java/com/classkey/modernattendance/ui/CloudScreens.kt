package com.classkey.modernattendance.ui

import android.annotation.SuppressLint
import android.provider.Settings
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classkey.modernattendance.MainActivity
import com.classkey.modernattendance.R
import com.classkey.modernattendance.data.SupabaseApi
import com.classkey.modernattendance.data.SupabaseApi.Companion.io
import com.classkey.modernattendance.hw.LocationClient
import com.classkey.modernattendance.hw.runBiometric
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── shared bits ──────────────────────────────────────────────────────

private fun cloudStatusColor(s: String): Color = when (s) {
    "present" -> CK.Success
    "late" -> CK.Warning
    "absent" -> CK.Error
    "od" -> CK.Primary
    "half_day", "leave" -> CK.Purple
    "permission", "early_leave" -> CK.Teal
    "approved" -> CK.Success
    "rejected" -> CK.Error
    "pending" -> CK.Warning
    else -> CK.Muted
}

private fun cloudStatusLabel(s: String): String =
    s.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
fun CloudChip(status: String) {
    val c = cloudStatusColor(status)
    Box(
        Modifier.clip(RoundedCornerShape(99.dp)).background(c.copy(alpha = .13f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(cloudStatusLabel(status), color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

// ── entry point ──────────────────────────────────────────────────────

private enum class CloudStage { LOADING, LOGIN, HOME }

@Composable
fun CloudApp(activity: MainActivity) {
    val api = remember { SupabaseApi(activity.applicationContext) }
    var stage by remember { mutableStateOf(CloudStage.LOADING) }
    var user by remember { mutableStateOf<SupabaseApi.CloudUser?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        if (!api.hasSession()) {
            stage = CloudStage.LOGIN
        } else {
            io({ api.fetchProfile() }) { r ->
                r.onSuccess { user = it; stage = CloudStage.HOME }
                    .onFailure { api.logout(); stage = CloudStage.LOGIN }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(CK.Bg)) {
        when (stage) {
            CloudStage.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = CK.Primary)
            }
            CloudStage.LOGIN -> CloudLogin(api, say = { message = it }) { u ->
                user = u
                stage = CloudStage.HOME
            }
            CloudStage.HOME -> {
                val u = user
                if (u == null) {
                    stage = CloudStage.LOGIN
                } else if (u.role == "student") {
                    StudentCloud(activity, api, u, say = { message = it }, onLogout = {
                        api.logout(); user = null; stage = CloudStage.LOGIN
                    })
                } else {
                    StaffCloud(api, u, say = { message = it }, onLogout = {
                        api.logout(); user = null; stage = CloudStage.LOGIN
                    })
                }
            }
        }
        message?.let {
            MessageBar(it, onClose = { message = null }, Modifier.align(Alignment.BottomCenter))
        }
    }
}

// ── login (role cards → email + password, role verified server-side) ──

@Composable
private fun CloudLogin(api: SupabaseApi, say: (String) -> Unit, onLogin: (SupabaseApi.CloudUser) -> Unit) {
    var role by remember { mutableStateOf<String?>(null) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.classkey_logo), null, Modifier.size(84.dp))
                Spacer(Modifier.height(10.dp))
                Text("ClassKey", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
                Text("Secure attendance · Verified presence", fontSize = 13.sp, color = CK.Muted)
                Spacer(Modifier.height(4.dp))
                PillChip("CONNECTED TO COLLEGE CLOUD", CK.SoftGreen, CK.Success)
            }
            Spacer(Modifier.height(24.dp))

            if (role == null) {
                SectionHeader("Sign in as")
                RoleCard(Icons.Outlined.School, "Student", "Mark attendance · history · requests", CK.SoftBlue) { role = "student"; email = "priya.sharma@classkey.local"; password = "ChangeMe123!" }
                Spacer(Modifier.height(10.dp))
                RoleCard(Icons.Outlined.Person, "Staff", "Live attendance · approvals", CK.SoftTeal) { role = "staff"; email = "staff@classkey.local"; password = "ChangeMe123!" }
                Spacer(Modifier.height(10.dp))
                RoleCard(Icons.Outlined.AdminPanelSettings, "Admin", "Overview · approvals · web dashboard", CK.SoftPurple) { role = "admin"; email = "admin@classkey.local"; password = "ChangeMe123!" }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Your role is verified by the server after login — the buttons only pick the form.",
                    fontSize = 11.sp, color = CK.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            } else {
                AppCard(padding = 20) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { role = null; error = null }) { Text("‹ Back", color = CK.Muted) }
                        Text("${role!!.replaceFirstChar { it.uppercase() }} login", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(email, { email = it; error = null }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        password, { password = it; error = null }, label = { Text("Password") }, singleLine = true,
                        modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                        visualTransformation = PasswordVisualTransformation()
                    )
                    if (error != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(error!!, color = CK.Error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(Modifier.height(14.dp))
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = CK.Primary)
                    PrimaryButton("Login", enabled = !busy) {
                        busy = true; error = null
                        io({ api.login(email, password) }) { r ->
                            busy = false
                            r.onSuccess { u ->
                                if (u.role != role) {
                                    api.logout()
                                    error = "This is a ${u.role} account. Use the ${u.role} login."
                                } else {
                                    say("Welcome, ${u.fullName.split(" ").first()}!")
                                    onLogin(u)
                                }
                            }.onFailure { error = it.message }
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "Students who log in with register number/phone can use the web app; the Android app uses email.",
                        fontSize = 11.sp, color = CK.Muted
                    )
                }
            }
        }
    }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, sub: String, soft: Color, onClick: () -> Unit) {
    AppCard(padding = 16) {
        Row(
            Modifier.clickable { onClick() },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(14.dp)).background(soft), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(24.dp), tint = CK.Primary)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                Text(sub, fontSize = 12.sp, color = CK.Muted)
            }
            Text("›", fontSize = 20.sp, color = CK.Muted)
        }
    }
}

// ── student ──────────────────────────────────────────────────────────

@SuppressLint("HardwareIds")
@Composable
private fun StudentCloud(
    activity: MainActivity,
    api: SupabaseApi,
    user: SupabaseApi.CloudUser,
    say: (String) -> Unit,
    onLogout: () -> Unit
) {
    var tab by remember { mutableStateOf("Mark") }
    var reload by remember { mutableIntStateOf(0) }
    var today by remember { mutableStateOf<SupabaseApi.CloudRecord?>(null) }
    var history by remember { mutableStateOf<List<SupabaseApi.CloudRecord>>(emptyList()) }
    var requests by remember { mutableStateOf<List<SupabaseApi.CloudRequest>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val deviceId = remember {
        Settings.Secure.getString(activity.contentResolver, Settings.Secure.ANDROID_ID) ?: "android-unknown"
    }

    LaunchedEffect(reload) {
        io({ Triple(api.myToday(), api.myHistory(), api.myRequests()) }) { r ->
            r.onSuccess { (t, h, q) -> today = t; history = h; requests = q }
                .onFailure { say(it.message ?: "Could not load data.") }
        }
    }

    fun mark() {
        error = null
        activity.withLocationPermission {
            busy = true
            LocationClient(activity.applicationContext).current { loc, locErr ->
                if (loc == null) {
                    busy = false
                    error = locErr ?: "Could not get location."
                    return@current
                }
                runBiometric(
                    activity, "Confirm attendance",
                    "Verify fingerprint/device lock to mark today's attendance.",
                    onSuccess = {
                        io({ api.markAttendance(loc.latitude, loc.longitude, loc.accuracy.toDouble(), deviceId) }) { r ->
                            busy = false
                            r.onSuccess { m ->
                                if (m.ok) {
                                    say(m.message)
                                    reload++
                                } else error = m.message
                            }.onFailure { error = it.message }
                        }
                    },
                    onError = { msg ->
                        busy = false
                        error = msg
                    }
                )
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(user.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                    Text("Student · synced to college cloud", fontSize = 12.sp, color = CK.Muted)
                }
                TextButton(onClick = onLogout) { Text("Sign out", color = CK.Error, fontSize = 13.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("Mark", "History", "Requests").forEach { t -> FilterPill(t, tab == t) { tab = t } }
            }
        }

        when (tab) {
            "Mark" -> item {
                Box(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                        .background(Brush.linearGradient(listOf(CK.Primary, Color(0xFF0DADA5)))).padding(20.dp)
                ) {
                    Column {
                        Text("Today's entry attendance", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                        Text(
                            LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                            color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(Modifier.height(8.dp))
                        if (today != null) {
                            PillChip(cloudStatusLabel(today!!.status), Color.White.copy(alpha = .22f), Color.White)
                        } else {
                            Text("Not marked yet", color = Color.White.copy(alpha = .9f), fontSize = 12.sp)
                        }
                    }
                }
                Spacer(Modifier.height(12.dp))
                AppCard {
                    if (today != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(50.dp).clip(CircleShape).background(CK.SoftGreen), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Check, null, Modifier.size(26.dp), tint = CK.Success)
                            }
                            Spacer(Modifier.width(14.dp))
                            Column {
                                Text("You're ${cloudStatusLabel(today!!.status)} today", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                                Text("Saved in the college database — staff can see it live.", fontSize = 12.sp, color = CK.Muted)
                            }
                        }
                    } else {
                        Text("Mark attendance", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                        Text(
                            "Live GPS is verified on the server against the campus geofence, then your fingerprint confirms identity. One record per day.",
                            fontSize = 12.sp, color = CK.Muted
                        )
                        if (busy) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(Modifier.fillMaxWidth(), color = CK.Primary)
                        }
                        if (error != null) {
                            Spacer(Modifier.height(10.dp))
                            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(CK.SoftRed).padding(12.dp)) {
                                Text(error!!, color = CK.Error, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        PrimaryButton("Mark Attendance", enabled = !busy, icon = Icons.Outlined.Fingerprint) { mark() }
                    }
                }
            }

            "History" -> {
                if (history.isEmpty()) item { EmptyCard("No records yet", "Marked attendance appears here.") }
                items(history) { rec ->
                    AppCard(padding = 14) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(rec.date, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CK.Text)
                                Text(rec.method + (rec.note?.let { " · $it" } ?: ""), fontSize = 11.sp, color = CK.Muted)
                            }
                            CloudChip(rec.status)
                        }
                    }
                }
            }

            "Requests" -> {
                item { CloudRequestForm(api, say) { reload++ } }
                items(requests) { req ->
                    AppCard(padding = 14) {
                        Row(verticalAlignment = Alignment.Top) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    PillChip(cloudStatusLabel(req.type), CK.SoftBlue, CK.Primary)
                                    Spacer(Modifier.width(8.dp))
                                    Text(req.date, fontSize = 12.sp, color = CK.Muted)
                                }
                                Spacer(Modifier.height(6.dp))
                                Text(req.reason, fontSize = 13.sp, color = CK.Text)
                            }
                            CloudChip(req.status)
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

@Composable
private fun CloudRequestForm(api: SupabaseApi, say: (String) -> Unit, onSubmitted: () -> Unit) {
    var type by remember { mutableStateOf("od") }
    var offset by remember { mutableIntStateOf(0) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AppCard {
        Text("New request", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CK.Text)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("od", "half_day", "full_day_leave").forEach { t ->
                FilterPill(cloudStatusLabel(t), type == t) { type = t }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("permission", "early_leave").forEach { t ->
                FilterPill(cloudStatusLabel(t), type == t) { type = t }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Today", 1 to "Tomorrow", 2 to "+2 days").forEach { (o, label) ->
                FilterPill(label, offset == o) { offset = o }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(reason, { reason = it; error = null }, label = { Text("Reason") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error!!, color = CK.Error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        PrimaryButton("Submit Request", enabled = !busy) {
            if (reason.trim().length < 4) { error = "Describe the reason."; return@PrimaryButton }
            busy = true
            io({ api.submitRequest(type, LocalDate.now().plusDays(offset.toLong()).toString(), reason.trim()) }) { r ->
                busy = false
                r.onSuccess { reason = ""; say("Request submitted for approval."); onSubmitted() }
                    .onFailure { error = it.message }
            }
        }
    }
}

// ── staff / admin ────────────────────────────────────────────────────

@Composable
private fun StaffCloud(
    api: SupabaseApi,
    user: SupabaseApi.CloudUser,
    say: (String) -> Unit,
    onLogout: () -> Unit
) {
    var tab by remember { mutableStateOf("Today") }
    var reload by remember { mutableIntStateOf(0) }
    var records by remember { mutableStateOf<List<SupabaseApi.CloudRecord>>(emptyList()) }
    var pending by remember { mutableStateOf<List<SupabaseApi.CloudRequest>>(emptyList()) }
    var total by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(reload) {
        loading = true
        io({ Triple(api.todayAll(), api.pendingRequests(), api.studentCount()) }) { r ->
            loading = false
            r.onSuccess { (rec, pen, tot) -> records = rec; pending = pen; total = tot }
                .onFailure { say(it.message ?: "Could not load data.") }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(user.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                    Text("${user.role.replaceFirstChar { it.uppercase() }} · live college data", fontSize = 12.sp, color = CK.Muted)
                }
                TextButton(onClick = { reload++ }) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp), tint = CK.Primary)
                    Text(" Refresh", color = CK.Primary, fontSize = 13.sp)
                }
                TextButton(onClick = onLogout) { Text("Sign out", color = CK.Error, fontSize = 13.sp) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterPill("Today", tab == "Today") { tab = "Today" }
                FilterPill("Approvals (${pending.size})", tab == "Approvals") { tab = "Approvals" }
            }
        }

        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = CK.Primary) }

        if (tab == "Today") {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Present", records.count { it.status == "present" }, CK.Success, Modifier.weight(1f))
                    StatCard("Late", records.count { it.status == "late" }, CK.Warning, Modifier.weight(1f))
                    StatCard("Unmarked", (total - records.size).coerceAtLeast(0), CK.Error, Modifier.weight(1f))
                }
            }
            if (records.isEmpty() && !loading) item { EmptyCard("No attendance yet today", "Marks appear as students verify. Tap Refresh anytime.") }
            items(records) { rec ->
                AppCard(padding = 14) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(rec.name ?: "Student", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                            Text("${rec.regNo ?: ""} · ${rec.method}", fontSize = 11.sp, color = CK.Muted)
                        }
                        CloudChip(rec.status)
                    }
                }
            }
            item {
                Text(
                    "Full management (students, campus geofence & timings, devices, audit, reports) is on the web dashboard.",
                    fontSize = 11.sp, color = CK.Muted, modifier = Modifier.fillMaxWidth().padding(horizontal = 6.dp)
                )
            }
        } else {
            if (pending.isEmpty() && !loading) item { EmptyCard("All caught up!", "No pending requests.") }
            items(pending) { req ->
                AppCard(padding = 14) {
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PillChip(cloudStatusLabel(req.type), CK.SoftBlue, CK.Primary)
                            Spacer(Modifier.width(8.dp))
                            Text(req.date, fontSize = 12.sp, color = CK.Muted)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text("${req.name ?: ""} · ${req.regNo ?: ""}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                        Text(req.reason, fontSize = 13.sp, color = CK.Text)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton("Reject", Modifier.weight(1f)) {
                                io({ api.decideRequest(req.id, false) }) { r ->
                                    r.onSuccess { say(it); reload++ }.onFailure { say(it.message ?: "Failed.") }
                                }
                            }
                            PrimaryButton("Approve", Modifier.weight(1f)) {
                                io({ api.decideRequest(req.id, true) }) { r ->
                                    r.onSuccess { say(it); reload++ }.onFailure { say(it.message ?: "Failed.") }
                                }
                            }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(10.dp)) }
    }
}

package com.classkey.modernattendance.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.classkey.modernattendance.MainActivity
import com.classkey.modernattendance.R
import com.classkey.modernattendance.data.SupabaseApi
import com.classkey.modernattendance.data.SupabaseApi.Companion.io
import com.classkey.modernattendance.hw.LocationClient
import com.classkey.modernattendance.hw.runBiometric
import java.io.ByteArrayOutputStream
import java.io.File
import java.time.LocalDate
import java.time.format.DateTimeFormatter

// ── status helpers ───────────────────────────────────────────────────

private fun stColor(s: String): Color = when (s) {
    "present", "approved" -> CK.Success
    "late", "pending", "pending_replacement" -> CK.Warning
    "absent", "rejected", "blocked" -> CK.Error
    "od" -> CK.Primary
    "half_day", "leave", "full_day_leave" -> CK.Purple
    "permission", "early_leave" -> CK.Teal
    else -> CK.Muted
}

private fun stLabel(s: String): String = s.replace('_', ' ').replaceFirstChar { it.uppercase() }

@Composable
private fun CloudChip(status: String) {
    val c = stColor(status)
    Box(
        Modifier.clip(RoundedCornerShape(99.dp)).background(c.copy(alpha = .13f))
            .padding(horizontal = 10.dp, vertical = 5.dp)
    ) { Text(stLabel(status), color = c, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
}

/** Circular avatar: photo from Supabase Storage when available, initials otherwise. */
private val avatarCache = HashMap<String, Bitmap>()

@Composable
fun CloudAvatar(api: SupabaseApi, url: String?, name: String, size: Int = 42) {
    var bmp by remember(url) { mutableStateOf(url?.let { avatarCache[it] }) }
    LaunchedEffect(url) {
        if (url != null && bmp == null) {
            io({
                val bytes = api.downloadBytes(url)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            }) { r -> r.onSuccess { avatarCache[url] = it; bmp = it } }
        }
    }
    Box(
        Modifier.size(size.dp).clip(CircleShape).background(CK.Primary),
        contentAlignment = Alignment.Center
    ) {
        val b = bmp
        if (b != null) {
            Image(b.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            val initials = name.split(" ").filter { it.isNotBlank() }.take(2)
                .joinToString("") { it.first().uppercase() }.ifBlank { "CK" }
            Text(initials, color = Color.White, fontWeight = FontWeight.Bold, fontSize = (size / 2.6).sp)
        }
    }
}

// ── entry point ──────────────────────────────────────────────────────

private enum class CloudStage { LOADING, LOGIN, FORCE_PASSWORD, HOME }

@Composable
fun CloudApp(activity: MainActivity) {
    val api = remember { SupabaseApi(activity.applicationContext) }
    var stage by remember { mutableStateOf(CloudStage.LOADING) }
    var user by remember { mutableStateOf<SupabaseApi.CloudUser?>(null) }
    var message by remember { mutableStateOf<String?>(null) }

    fun enter(u: SupabaseApi.CloudUser) {
        user = u
        stage = if (u.forcedPasswordChange) CloudStage.FORCE_PASSWORD else CloudStage.HOME
    }

    LaunchedEffect(Unit) {
        if (!api.hasSession()) {
            stage = CloudStage.LOGIN
        } else {
            io({ api.fetchProfile() }) { r ->
                r.onSuccess { enter(it) }.onFailure { api.logout(); stage = CloudStage.LOGIN }
            }
        }
    }

    Box(Modifier.fillMaxSize().background(CK.Bg)) {
        when (stage) {
            CloudStage.LOADING -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(painterResource(R.drawable.classkey_logo), null, Modifier.size(96.dp))
                    Spacer(Modifier.height(14.dp))
                    Text("ClassKey", fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
                    Spacer(Modifier.height(18.dp))
                    CircularProgressIndicator(Modifier.size(24.dp), color = CK.Primary, strokeWidth = 2.5.dp)
                }
            }
            CloudStage.LOGIN -> CloudLogin(api) { enter(it) }
            CloudStage.FORCE_PASSWORD -> ForcePasswordScreen(api, say = { message = it }) {
                user = user?.copy(forcedPasswordChange = false)
                stage = CloudStage.HOME
            }
            CloudStage.HOME -> {
                val u = user
                if (u == null) stage = CloudStage.LOGIN
                else if (u.role == "student") StudentCloud(activity, api, u,
                    say = { message = it },
                    onUserChanged = { user = it },
                    onLogout = { api.logout(); user = null; stage = CloudStage.LOGIN })
                else StaffCloud(activity, api, u,
                    say = { message = it },
                    onUserChanged = { user = it },
                    onLogout = { api.logout(); user = null; stage = CloudStage.LOGIN })
            }
        }
        message?.let { MessageBar(it, onClose = { message = null }, Modifier.align(Alignment.BottomCenter).padding(bottom = 60.dp)) }
    }
}

// ── login ────────────────────────────────────────────────────────────

@Composable
private fun CloudLogin(api: SupabaseApi, onLogin: (SupabaseApi.CloudUser) -> Unit) {
    var role by remember { mutableStateOf<String?>(null) }
    var identifier by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.classkey_logo), null, Modifier.size(88.dp))
                Spacer(Modifier.height(10.dp))
                Text("ClassKey", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
                Text("Secure attendance. Verified presence.", fontSize = 13.sp, color = CK.Muted)
            }
            Spacer(Modifier.height(28.dp))

            if (role == null) {
                SectionHeader("Sign in as")
                RoleCard(Icons.Outlined.School, "Student", "Mark attendance · history · requests", CK.SoftBlue) { role = "student" }
                Spacer(Modifier.height(10.dp))
                RoleCard(Icons.Outlined.Person, "Staff", "Live attendance · students · approvals", CK.SoftTeal) { role = "staff" }
                Spacer(Modifier.height(10.dp))
                RoleCard(Icons.Outlined.AdminPanelSettings, "Admin", "Manage college · campus · audit", CK.SoftPurple) { role = "admin" }
            } else {
                AppCard(padding = 20) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { role = null; error = null; identifier = ""; password = "" }) { Text("‹", fontSize = 20.sp, color = CK.Muted) }
                        Text("${stLabel(role!!)} login", fontSize = 19.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        identifier, { identifier = it; error = null },
                        label = { Text(if (role == "student") "Register no / Username / Email / Phone" else "Username / Email / Phone") },
                        singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)
                    )
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
                    PrimaryButton("Login", enabled = !busy && identifier.isNotBlank() && password.isNotBlank()) {
                        busy = true; error = null
                        io({ api.login(identifier, password) }) { r ->
                            busy = false
                            r.onSuccess { u ->
                                if (u.role != role) {
                                    api.logout()
                                    error = "This is a ${u.role} account. Please use the ${u.role} login."
                                } else onLogin(u)
                            }.onFailure { error = it.message }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text("Forgot password? Ask the college office to reset it.", fontSize = 11.sp, color = CK.Muted)
                }
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun RoleCard(icon: ImageVector, title: String, sub: String, soft: Color, onClick: () -> Unit) {
    AppCard(padding = 16) {
        Row(Modifier.clickable { onClick() }, verticalAlignment = Alignment.CenterVertically) {
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

// ── forced password change ───────────────────────────────────────────

@Composable
private fun ForcePasswordScreen(api: SupabaseApi, say: (String) -> Unit, onDone: () -> Unit) {
    var p1 by remember { mutableStateOf("") }
    var p2 by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
        item {
            AppCard(padding = 22) {
                Box(Modifier.size(72.dp).clip(CircleShape).background(CK.SoftBlue), contentAlignment = Alignment.Center) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(34.dp), tint = CK.Primary)
                }
                Spacer(Modifier.height(14.dp))
                Text("Set your own password", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
                Text("You are using a temporary password. Choose a new one to continue.", fontSize = 13.sp, color = CK.Muted)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(p1, { p1 = it; error = null }, label = { Text("New password (min 8)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), visualTransformation = PasswordVisualTransformation())
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(p2, { p2 = it; error = null }, label = { Text("Confirm new password") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp), visualTransformation = PasswordVisualTransformation())
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = CK.Error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp), color = CK.Primary)
                PrimaryButton("Save & Continue", enabled = !busy) {
                    when {
                        p1.length < 8 -> error = "Password must be at least 8 characters."
                        p1 != p2 -> error = "Passwords do not match."
                        else -> {
                            busy = true
                            io({ api.changePassword(p1) }) { r ->
                                busy = false
                                r.onSuccess { say("Password updated."); onDone() }
                                    .onFailure { error = it.message }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── bottom nav ───────────────────────────────────────────────────────

private data class NavItem(val id: String, val label: String, val icon: ImageVector)

@Composable
private fun CloudNav(items: List<NavItem>, active: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CK.Card).navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 7.dp)
    ) {
        items.forEach { item ->
            val selected = item.id == active
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(13.dp)).clickable { onSelect(item.id) }.padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(item.icon, item.label, Modifier.size(21.dp), tint = if (selected) CK.Primary else CK.Muted)
                Spacer(Modifier.height(2.dp))
                Text(item.label, fontSize = 10.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) CK.Primary else CK.Muted)
            }
        }
    }
}

// ── STUDENT ──────────────────────────────────────────────────────────

@SuppressLint("HardwareIds")
@Composable
private fun StudentCloud(
    activity: MainActivity,
    api: SupabaseApi,
    user: SupabaseApi.CloudUser,
    say: (String) -> Unit,
    onUserChanged: (SupabaseApi.CloudUser) -> Unit,
    onLogout: () -> Unit
) {
    var tab by remember { mutableStateOf("home") }
    var reload by remember { mutableIntStateOf(0) }
    var today by remember { mutableStateOf<List<SupabaseApi.CloudRecord>>(emptyList()) }
    var history by remember { mutableStateOf<List<SupabaseApi.CloudRecord>>(emptyList()) }
    var requests by remember { mutableStateOf<List<SupabaseApi.CloudRequest>>(emptyList()) }
    var notifs by remember { mutableStateOf<List<SupabaseApi.CloudNotif>>(emptyList()) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val deviceId = remember { Settings.Secure.getString(activity.contentResolver, Settings.Secure.ANDROID_ID) ?: "android-unknown" }

    LaunchedEffect(reload) {
        io({ listOf(api.myToday(), api.myHistory(), api.myRequests(), api.myNotifications()) }) { r ->
            r.onSuccess { (t, h, q, n) ->
                @Suppress("UNCHECKED_CAST")
                today = t as List<SupabaseApi.CloudRecord>
                @Suppress("UNCHECKED_CAST")
                history = h as List<SupabaseApi.CloudRecord>
                @Suppress("UNCHECKED_CAST")
                requests = q as List<SupabaseApi.CloudRequest>
                @Suppress("UNCHECKED_CAST")
                notifs = n as List<SupabaseApi.CloudNotif>
            }.onFailure { say(it.message ?: "Could not load data. Check internet.") }
        }
    }

    fun mark() {
        error = null
        activity.withLocationPermission {
            busy = true
            LocationClient(activity.applicationContext).current { loc, locErr ->
                if (loc == null) {
                    busy = false; error = locErr ?: "Could not get location."
                    return@current
                }
                runBiometric(activity, "Confirm attendance", "Verify fingerprint / device lock to mark attendance.",
                    onSuccess = {
                        io({ api.markAttendance(loc.latitude, loc.longitude, loc.accuracy.toDouble(), deviceId) }) { r ->
                            busy = false
                            r.onSuccess { m -> if (m.ok) { say(m.message); reload++ } else error = m.message }
                                .onFailure { error = it.message }
                        }
                    },
                    onError = { msg -> busy = false; error = msg }
                )
            }
        }
    }

    val unread = notifs.count { !it.read }
    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                "home" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    item {
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CloudAvatar(api, user.avatarUrl, user.fullName, 44)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(user.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                                Text("Student", fontSize = 12.sp, color = CK.Muted)
                            }
                        }
                    }
                    item {
                        Box(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                                .background(Brush.linearGradient(listOf(CK.Primary, Color(0xFF0DADA5)))).padding(20.dp)
                        ) {
                            Column {
                                Text("Today", color = Color.White.copy(alpha = .85f), fontSize = 12.sp)
                                Text(
                                    LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                                    color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold
                                )
                            }
                        }
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            SessionCard("Morning", today.firstOrNull { it.session == "morning" }, Modifier.weight(1f))
                            SessionCard("Afternoon", today.firstOrNull { it.session == "afternoon" }, Modifier.weight(1f))
                        }
                    }
                    item {
                        AppCard {
                            if (today.size >= 2) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(46.dp).clip(CircleShape).background(CK.SoftGreen), contentAlignment = Alignment.Center) {
                                        Icon(Icons.Outlined.Check, null, Modifier.size(24.dp), tint = CK.Success)
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column {
                                        Text("Both sessions recorded", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                                        Text("Synced live to staff and admin.", fontSize = 12.sp, color = CK.Muted)
                                    }
                                }
                            } else {
                                Text("Mark attendance", fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                                Text(
                                    "Fingerprint + live GPS, verified on the college server. The current session is detected automatically.",
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
                    item { Spacer(Modifier.height(8.dp)) }
                }

                "history" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Spacer(Modifier.height(4.dp)); ScreenTitle("History", "Your session-wise records.") }
                    if (history.isEmpty()) item { EmptyCard("No records yet", "Marked attendance appears here.") }
                    items(history) { rec ->
                        AppCard(padding = 14) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${rec.date} · ${stLabel(rec.session)}", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CK.Text)
                                    Text(rec.method + (rec.note?.let { " · $it" } ?: ""), fontSize = 11.sp, color = CK.Muted)
                                }
                                CloudChip(rec.status)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                "requests" -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    item { Spacer(Modifier.height(4.dp)); ScreenTitle("Requests", "OD · leave · half day · permission.") }
                    item { CloudRequestForm(api, say) { reload++ } }
                    items(requests) { req ->
                        AppCard(padding = 14) {
                            Row(verticalAlignment = Alignment.Top) {
                                Column(Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        PillChip(stLabel(req.type), CK.SoftBlue, CK.Primary)
                                        Spacer(Modifier.width(6.dp))
                                        Text("${req.date} · ${stLabel(req.session)}", fontSize = 12.sp, color = CK.Muted)
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(req.reason, fontSize = 13.sp, color = CK.Text)
                                }
                                CloudChip(req.status)
                            }
                        }
                    }
                    item { Spacer(Modifier.height(8.dp)) }
                }

                "alerts" -> NotificationsList(api, notifs, onRead = { reload++ })

                else -> ProfileCloud(activity, api, user, say, onUserChanged, onLogout)
            }
        }
        CloudNav(
            listOf(
                NavItem("home", "Home", Icons.Outlined.Home),
                NavItem("history", "History", Icons.Outlined.History),
                NavItem("requests", "Requests", Icons.Outlined.Description),
                NavItem("alerts", if (unread > 0) "Alerts ($unread)" else "Alerts", Icons.Outlined.Notifications),
                NavItem("profile", "Profile", Icons.Outlined.Person)
            ), tab
        ) { tab = it }
    }
}

@Composable
private fun SessionCard(label: String, rec: SupabaseApi.CloudRecord?, modifier: Modifier = Modifier) {
    AppCard(modifier = modifier, padding = 14) {
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CK.Muted)
        Spacer(Modifier.height(8.dp))
        if (rec != null) CloudChip(rec.status) else CloudChip("not_marked")
    }
}

@Composable
private fun CloudRequestForm(api: SupabaseApi, say: (String) -> Unit, onSubmitted: () -> Unit) {
    var type by remember { mutableStateOf("od") }
    var session by remember { mutableStateOf("full_day") }
    var offset by remember { mutableIntStateOf(0) }
    var reason by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    AppCard {
        Text("New request", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = CK.Text)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("od", "half_day", "full_day_leave").forEach { t -> FilterPill(stLabel(t), type == t) { type = t } }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("permission", "early_leave").forEach { t -> FilterPill(stLabel(t), type == t) { type = t } }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf("full_day", "morning", "afternoon").forEach { s -> FilterPill(stLabel(s), session == s) { session = s } }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            listOf(0 to "Today", 1 to "Tomorrow", 2 to "+2 days").forEach { (o, label) -> FilterPill(label, offset == o) { offset = o } }
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
            io({ api.submitRequest(type, LocalDate.now().plusDays(offset.toLong()).toString(), session, reason.trim()) }) { r ->
                busy = false
                r.onSuccess { reason = ""; say("Request submitted for approval."); onSubmitted() }
                    .onFailure { error = it.message }
            }
        }
    }
}

@Composable
private fun NotificationsList(api: SupabaseApi, notifs: List<SupabaseApi.CloudNotif>, onRead: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Notifications", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text, modifier = Modifier.weight(1f))
                Text("Mark all read", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CK.Primary,
                    modifier = Modifier.clickable { io({ api.markNotificationsRead() }) { onRead() } })
            }
        }
        if (notifs.isEmpty()) item { EmptyCard("No notifications", "Attendance and approval updates appear here.") }
        items(notifs) { n ->
            AppCard(padding = 14) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(CK.SoftBlue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Notifications, null, Modifier.size(18.dp), tint = CK.Primary)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CK.Text, modifier = Modifier.weight(1f))
                            if (!n.read) Box(Modifier.size(8.dp).clip(CircleShape).background(CK.Primary))
                        }
                        Text(n.body, fontSize = 12.sp, color = CK.Muted)
                        Text(n.createdAt, fontSize = 10.sp, color = CK.Muted.copy(alpha = .7f))
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ── profile (all roles) ──────────────────────────────────────────────

@Composable
private fun ProfileCloud(
    activity: MainActivity,
    api: SupabaseApi,
    user: SupabaseApi.CloudUser,
    say: (String) -> Unit,
    onUserChanged: (SupabaseApi.CloudUser) -> Unit,
    onLogout: () -> Unit
) {
    var showPassword by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }

    fun pickAndUpload() {
        activity.pickPhoto { path ->
            if (path == null) return@pickPhoto
            uploading = true
            io({
                val opts = BitmapFactory.Options().apply { inSampleSize = 2 }
                val raw = BitmapFactory.decodeFile(path, opts) ?: throw Exception("Could not read the image.")
                val scale = 512f / maxOf(raw.width, raw.height).coerceAtLeast(1)
                val bmp = if (scale < 1f) Bitmap.createScaledBitmap(raw, (raw.width * scale).toInt(), (raw.height * scale).toInt(), true) else raw
                val out = ByteArrayOutputStream()
                bmp.compress(Bitmap.CompressFormat.JPEG, 82, out)
                api.uploadAvatar(out.toByteArray())
            }) { r ->
                uploading = false
                r.onSuccess { url ->
                    avatarCache.remove(url)
                    onUserChanged(user.copy(avatarUrl = url))
                    say("Profile photo updated.")
                }.onFailure { say(it.message ?: "Upload failed.") }
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            ScreenTitle("Profile", "Account and security.")
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        CloudAvatar(api, user.avatarUrl, user.fullName, 64)
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape)
                                .background(CK.Primary).clickable { pickAndUpload() },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.CameraAlt, null, Modifier.size(12.dp), tint = Color.White) }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(user.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                        Text(user.email, fontSize = 12.sp, color = CK.Muted)
                        user.phone?.let { Text(it, fontSize = 12.sp, color = CK.Muted) }
                        Spacer(Modifier.height(4.dp))
                        PillChip(user.role.uppercase(), CK.SoftBlue, CK.Primary)
                    }
                }
                if (uploading) {
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(Modifier.fillMaxWidth(), color = CK.Primary)
                }
            }
        }
        item {
            AppCard(padding = 0) {
                Row(
                    Modifier.fillMaxWidth().clickable { showPassword = true }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = CK.Primary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Change password", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CK.Text)
                        Text("Update your account password", fontSize = 11.sp, color = CK.Muted)
                    }
                }
            }
        }
        item {
            AppCard(padding = 0) {
                Row(
                    Modifier.fillMaxWidth().clickable { onLogout() }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp), tint = CK.Error)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", color = CK.Error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showPassword) {
        var p1 by remember { mutableStateOf("") }
        var p2 by remember { mutableStateOf("") }
        var err by remember { mutableStateOf<String?>(null) }
        AlertDialog(
            onDismissRequest = { showPassword = false },
            containerColor = CK.Card,
            title = { Text("Change password", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(p1, { p1 = it; err = null }, label = { Text("New password (min 8)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    OutlinedTextField(p2, { p2 = it; err = null }, label = { Text("Confirm") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    if (err != null) Text(err!!, color = CK.Error, fontSize = 12.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    when {
                        p1.length < 8 -> err = "At least 8 characters."
                        p1 != p2 -> err = "Passwords do not match."
                        else -> io({ api.changePassword(p1) }) { r ->
                            r.onSuccess { say("Password changed."); showPassword = false }
                                .onFailure { err = it.message }
                        }
                    }
                }) { Text("Save", fontWeight = FontWeight.Bold, color = CK.Primary) }
            },
            dismissButton = { TextButton(onClick = { showPassword = false }) { Text("Cancel", color = CK.Muted) } }
        )
    }
}

// ── STAFF / ADMIN ────────────────────────────────────────────────────

@Composable
private fun StaffCloud(
    activity: MainActivity,
    api: SupabaseApi,
    user: SupabaseApi.CloudUser,
    say: (String) -> Unit,
    onUserChanged: (SupabaseApi.CloudUser) -> Unit,
    onLogout: () -> Unit
) {
    val isAdmin = user.role == "admin"
    var tab by remember { mutableStateOf("today") }
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
                .onFailure { say(it.message ?: "Could not load data. Check internet.") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (tab) {
                "today" -> StaffToday(user, records, pending.size, total, loading, onRefresh = { reload++ })
                "students" -> StaffStudents(api, isAdmin, say, onChanged = { reload++ })
                "approvals" -> StaffApprovals(api, pending, say, onChanged = { reload++ })
                "more" -> StaffMore(activity, api, user, isAdmin, say, onUserChanged, onLogout)
                else -> StaffToday(user, records, pending.size, total, loading, onRefresh = { reload++ })
            }
        }
        CloudNav(
            listOf(
                NavItem("today", "Today", Icons.Outlined.Home),
                NavItem("students", "Students", Icons.Outlined.Groups),
                NavItem("approvals", if (pending.isNotEmpty()) "Approve (${pending.size})" else "Approvals", Icons.Outlined.AssignmentTurnedIn),
                NavItem("more", "More", Icons.Outlined.MoreHoriz)
            ), tab
        ) { tab = it }
    }
}

@Composable
private fun StaffToday(
    user: SupabaseApi.CloudUser,
    records: List<SupabaseApi.CloudRecord>,
    pendingCount: Int,
    total: Int,
    loading: Boolean,
    onRefresh: () -> Unit
) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(user.fullName, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                    Text("${stLabel(user.role)} · live college data", fontSize = 12.sp, color = CK.Muted)
                }
                TextButton(onClick = onRefresh) {
                    Icon(Icons.Outlined.Refresh, null, Modifier.size(16.dp), tint = CK.Primary)
                    Text(" Refresh", color = CK.Primary, fontSize = 13.sp)
                }
            }
        }
        item {
            Box(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(Brush.linearGradient(listOf(CK.Primary, Color(0xFF0DADA5)))).padding(20.dp)
            ) {
                Column {
                    Text(
                        LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                        color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        "${records.map { it.regNo }.distinct().size} of $total students · $pendingCount pending requests",
                        color = Color.White.copy(alpha = .9f), fontSize = 12.sp
                    )
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatCard("Present", records.count { it.status == "present" }, CK.Success, Modifier.weight(1f))
                StatCard("Late", records.count { it.status == "late" }, CK.Warning, Modifier.weight(1f))
                StatCard("OD/Leave", records.count { it.status in listOf("od", "half_day", "leave", "permission", "early_leave") }, CK.Primary, Modifier.weight(1f))
                StatCard("Absent", records.count { it.status == "absent" }, CK.Error, Modifier.weight(1f))
            }
        }
        if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = CK.Primary) }
        if (records.isEmpty() && !loading) item { EmptyCard("No attendance yet today", "Marks appear here as students verify.") }
        items(records) { rec ->
            AppCard(padding = 14) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(rec.name ?: "Student", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                        Text("${rec.regNo ?: ""} · ${stLabel(rec.session)} · ${rec.method}", fontSize = 11.sp, color = CK.Muted)
                    }
                    CloudChip(rec.status)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun StaffStudents(api: SupabaseApi, isAdmin: Boolean, say: (String) -> Unit, onChanged: () -> Unit) {
    var reload by remember { mutableIntStateOf(0) }
    var students by remember { mutableStateOf<List<SupabaseApi.CloudStudent>>(emptyList()) }
    var search by remember { mutableStateOf("") }
    var target by remember { mutableStateOf<SupabaseApi.CloudStudent?>(null) }
    var showAdd by remember { mutableStateOf(false) }
    var addRole by remember { mutableStateOf("student") }

    LaunchedEffect(reload) {
        io({ api.students() }) { r -> r.onSuccess { students = it }.onFailure { say(it.message ?: "Load failed.") } }
    }

    val visible = students.filter {
        search.isBlank() || it.name.contains(search, true) || it.regNo.contains(search, true)
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Students", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
                    Text("${students.size} accounts · tap to manage", fontSize = 12.sp, color = CK.Muted)
                }
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(CK.Primary)
                        .clickable { addRole = "student"; showAdd = true },
                    contentAlignment = Alignment.Center
                ) { Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
            }
            if (isAdmin) {
                Spacer(Modifier.height(6.dp))
                GhostButton("Add staff / admin account") { addRole = "staff"; showAdd = true }
            }
        }
        item {
            OutlinedTextField(
                search, { search = it }, placeholder = { Text("Search name or register no…", fontSize = 13.sp) },
                singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp)
            )
        }
        if (visible.isEmpty()) item { EmptyCard("No students", "Add your first student with the + button.") }
        items(visible) { s ->
            AppCard(padding = 14) {
                Row(Modifier.clickable { target = s }, verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(s.name, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (s.isActive) CK.Text else CK.Muted)
                        Text("${s.regNo} · device ${stLabel(s.deviceStatus)}${if (!s.isActive) " · DEACTIVATED" else ""}", fontSize = 11.sp, color = CK.Muted)
                    }
                    Text("›", fontSize = 18.sp, color = CK.Muted)
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }

    target?.let { s ->
        StudentManageDialog(api, s, isAdmin, say, onClose = { target = null }, onChanged = { target = null; reload++; onChanged() })
    }
    if (showAdd) {
        AddAccountDialog(api, addRole, say, onClose = { showAdd = false }, onAdded = { showAdd = false; reload++; onChanged() })
    }
}

@Composable
private fun StudentManageDialog(
    api: SupabaseApi, s: SupabaseApi.CloudStudent, isAdmin: Boolean,
    say: (String) -> Unit, onClose: () -> Unit, onChanged: () -> Unit
) {
    var status by remember { mutableStateOf("present") }
    var session by remember { mutableStateOf("full_day") }
    var reason by remember { mutableStateOf("") }
    var err by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CK.Card,
        title = { Text(s.name, fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("${s.regNo} · ${s.email}", fontSize = 12.sp, color = CK.Muted)
                Spacer(Modifier.height(12.dp))
                Text("MANUAL OVERRIDE (today)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = CK.Muted, letterSpacing = 1.2.sp)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("present", "late", "od", "absent").forEach { st -> FilterPill(stLabel(st), status == st) { status = st } }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("full_day", "morning", "afternoon").forEach { se -> FilterPill(stLabel(se), session == se) { session = se } }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(reason, { reason = it; err = null }, label = { Text("Audit reason (required)") }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Spacer(Modifier.height(8.dp))
                PrimaryButton("Save override") {
                    io({ api.manualOverride(s.id, status, session, reason) }) { r ->
                        r.onSuccess { say(it); onChanged() }.onFailure { err = it.message }
                    }
                }
                if (isAdmin || true) {
                    Spacer(Modifier.height(10.dp))
                    GhostButton(if (s.isActive) "Deactivate student" else "Reactivate student") {
                        io({ api.setStudentActive(s.profileId, !s.isActive) }) { r ->
                            r.onSuccess { say(if (s.isActive) "Student deactivated." else "Student reactivated."); onChanged() }
                                .onFailure { err = it.message }
                        }
                    }
                }
                if (err != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(err!!, color = CK.Error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onClose) { Text("Close", color = CK.Muted) } }
    )
}

@Composable
private fun AddAccountDialog(
    api: SupabaseApi, initialRole: String, say: (String) -> Unit,
    onClose: () -> Unit, onAdded: () -> Unit
) {
    var role by remember { mutableStateOf(initialRole) }
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var regNo by remember { mutableStateOf("") }
    var year by remember { mutableStateOf("3") }
    var section by remember { mutableStateOf("A") }
    var designation by remember { mutableStateOf("Staff") }
    var tempPassword by remember { mutableStateOf("") }
    var deptId by remember { mutableStateOf<String?>(null) }
    var depts by remember { mutableStateOf<List<SupabaseApi.Department>>(emptyList()) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        io({ api.departments() }) { r -> r.onSuccess { depts = it; if (deptId == null) deptId = it.firstOrNull()?.id } }
    }

    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CK.Card,
        title = { Text("Add account", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (initialRole != "student") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("student", "staff", "admin").forEach { x -> FilterPill(stLabel(x), role == x) { role = x } }
                    }
                }
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(email, { email = it }, label = { Text("Email") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(phone, { phone = it.filter { c -> c.isDigit() } }, label = { Text("Mobile") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(username, { username = it }, label = { Text("Username (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                if (role == "student") {
                    OutlinedTextField(regNo, { regNo = it }, label = { Text("Register number") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(year, { year = it.filter { c -> c.isDigit() } }, label = { Text("Year") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                        OutlinedTextField(section, { section = it }, label = { Text("Section") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(12.dp))
                    }
                }
                if (role == "staff") {
                    OutlinedTextField(designation, { designation = it }, label = { Text("Designation") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                }
                if (depts.isNotEmpty() && role != "admin") {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        depts.forEach { d -> FilterPill(d.code, deptId == d.id) { deptId = d.id } }
                    }
                }
                OutlinedTextField(tempPassword, { tempPassword = it }, label = { Text("Temporary password (min 8, blank = ChangeMe123!)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                Text("The user is asked to set their own password on first login.", fontSize = 11.sp, color = CK.Muted)
                if (err != null) Text(err!!, color = CK.Error, fontSize = 12.sp)
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth(), color = CK.Primary)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (name.isBlank() || email.isBlank()) { err = "Name and email are required."; return@TextButton }
                if (role == "student" && regNo.isBlank()) { err = "Register number is required."; return@TextButton }
                busy = true
                io({
                    api.createUser(
                        role, name.trim(), email.trim(), phone.trim(), username.trim(), tempPassword,
                        if (role == "student") regNo.trim() else null,
                        if (role == "admin") null else deptId,
                        if (role == "student") year.toIntOrNull() else null,
                        if (role == "student") section.trim() else null,
                        if (role == "staff") designation.trim() else null
                    )
                }) { r ->
                    busy = false
                    r.onSuccess { say(it); onAdded() }.onFailure { err = it.message }
                }
            }) { Text("Create", fontWeight = FontWeight.Bold, color = CK.Primary) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", color = CK.Muted) } }
    )
}

@Composable
private fun StaffApprovals(api: SupabaseApi, pending: List<SupabaseApi.CloudRequest>, say: (String) -> Unit, onChanged: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            ScreenTitle("Approvals", "OD, leave and permission requests.")
        }
        if (pending.isEmpty()) item { EmptyCard("All caught up!", "No pending requests.") }
        items(pending) { req ->
            AppCard(padding = 14) {
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PillChip(stLabel(req.type), CK.SoftBlue, CK.Primary)
                        Spacer(Modifier.width(6.dp))
                        Text("${req.date} · ${stLabel(req.session)}", fontSize = 12.sp, color = CK.Muted)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("${req.name ?: ""} · ${req.regNo ?: ""}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                    Text(req.reason, fontSize = 13.sp, color = CK.Text)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GhostButton("Reject", Modifier.weight(1f)) {
                            io({ api.decideRequest(req.id, false) }) { r -> r.onSuccess { say(it); onChanged() }.onFailure { say(it.message ?: "Failed.") } }
                        }
                        PrimaryButton("Approve", Modifier.weight(1f)) {
                            io({ api.decideRequest(req.id, true) }) { r -> r.onSuccess { say(it); onChanged() }.onFailure { say(it.message ?: "Failed.") } }
                        }
                    }
                }
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

// ── staff/admin "More" hub ───────────────────────────────────────────

@Composable
private fun StaffMore(
    activity: MainActivity,
    api: SupabaseApi,
    user: SupabaseApi.CloudUser,
    isAdmin: Boolean,
    say: (String) -> Unit,
    onUserChanged: (SupabaseApi.CloudUser) -> Unit,
    onLogout: () -> Unit
) {
    var view by remember { mutableStateOf("menu") }

    when (view) {
        "profile" -> ProfileCloud(activity, api, user, say, onUserChanged, onLogout)
        "suspicious" -> SimpleListScreen("Suspicious Attempts", { api.suspiciousAttempts() }, Icons.Outlined.Warning, CK.Warning) { view = "menu" }
        "audit" -> SimpleListScreen("Audit Logs", { api.auditLogs() }, Icons.Outlined.Description, CK.Primary) { view = "menu" }
        "devices" -> DevicesScreen(api, say) { view = "menu" }
        "campus" -> CampusScreen(activity, api, say) { view = "menu" }
        "reports" -> ReportsCloudScreen(activity, api, say) { view = "menu" }
        "alerts" -> {
            var notifs by remember { mutableStateOf<List<SupabaseApi.CloudNotif>>(emptyList()) }
            var tick by remember { mutableIntStateOf(0) }
            LaunchedEffect(tick) { io({ api.myNotifications() }) { r -> r.onSuccess { notifs = it } } }
            Column {
                OverlayHeader("Notifications") { view = "menu" }
                NotificationsList(api, notifs, onRead = { tick++ })
            }
        }
        else -> LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Spacer(Modifier.height(4.dp))
                ScreenTitle("More", if (isAdmin) "Administration tools." else "Staff tools.")
            }
            item { MoreRow(Icons.Outlined.Person, "My profile", "Photo, password, sign out") { view = "profile" } }
            item { MoreRow(Icons.Outlined.BarChart, "Reports", "Range, filters, CSV export") { view = "reports" } }
            item { MoreRow(Icons.Outlined.Warning, "Suspicious attempts", "Out-of-range and device alerts") { view = "suspicious" } }
            item { MoreRow(Icons.Outlined.Notifications, "Notifications", "Your alerts") { view = "alerts" } }
            if (isAdmin) {
                item { MoreRow(Icons.Outlined.MyLocation, "Campus settings", "Geofence, sessions, accuracy") { view = "campus" } }
                item { MoreRow(Icons.Outlined.Fingerprint, "Device approvals", "Approve replacement phones") { view = "devices" } }
                item { MoreRow(Icons.Outlined.Description, "Audit logs", "Every action, who and when") { view = "audit" } }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun MoreRow(icon: ImageVector, title: String, sub: String, onClick: () -> Unit) {
    AppCard(padding = 0) {
        Row(Modifier.fillMaxWidth().clickable { onClick() }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(CK.SoftBlue), contentAlignment = Alignment.Center) {
                Icon(icon, null, Modifier.size(19.dp), tint = CK.Primary)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CK.Text)
                Text(sub, fontSize = 11.sp, color = CK.Muted)
            }
            Text("›", fontSize = 18.sp, color = CK.Muted)
        }
    }
}

@Composable
private fun SimpleListScreen(
    title: String,
    loader: () -> List<Triple<String, String, String>>,
    icon: ImageVector,
    tint: Color,
    onBack: () -> Unit
) {
    var rows by remember { mutableStateOf<List<Triple<String, String, String>>?>(null) }
    LaunchedEffect(Unit) { io(loader) { r -> r.onSuccess { rows = it }.onFailure { rows = emptyList() } } }
    Column(Modifier.fillMaxSize()) {
        OverlayHeader(title, onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (rows == null) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = CK.Primary) }
            if (rows?.isEmpty() == true) item { EmptyCard("Nothing here", "Entries appear as they happen.") }
            items(rows ?: emptyList()) { (head, body, time) ->
                AppCard(padding = 13) {
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(icon, null, Modifier.size(17.dp), tint = tint)
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text(head, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                            Text(body, fontSize = 12.sp, color = CK.Muted)
                        }
                        Text(time, fontSize = 10.sp, color = CK.Muted)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun DevicesScreen(api: SupabaseApi, say: (String) -> Unit, onBack: () -> Unit) {
    var tick by remember { mutableIntStateOf(0) }
    var rows by remember { mutableStateOf<List<SupabaseApi.DeviceApproval>?>(null) }
    LaunchedEffect(tick) { io({ api.deviceApprovals() }) { r -> r.onSuccess { rows = it }.onFailure { rows = emptyList() } } }
    Column(Modifier.fillMaxSize()) {
        OverlayHeader("Device Approvals", onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (rows == null) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = CK.Primary) }
            val pending = rows?.filter { it.status == "pending" } ?: emptyList()
            val done = rows?.filter { it.status != "pending" } ?: emptyList()
            if (rows?.isEmpty() == true) item { EmptyCard("No device requests", "Requests appear when a student switches phones.") }
            items(pending) { d ->
                AppCard(padding = 14) {
                    Column {
                        Text(d.who, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                        Text("Old: ${d.oldDevice ?: "—"}\nNew: ${d.newDevice}", fontSize = 11.sp, color = CK.Muted)
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GhostButton("Reject", Modifier.weight(1f)) {
                                io({ api.decideDevice(d.id, false) }) { r -> r.onSuccess { say(it); tick++ }.onFailure { say(it.message ?: "Failed.") } }
                            }
                            PrimaryButton("Approve", Modifier.weight(1f)) {
                                io({ api.decideDevice(d.id, true) }) { r -> r.onSuccess { say(it); tick++ }.onFailure { say(it.message ?: "Failed.") } }
                            }
                        }
                    }
                }
            }
            items(done.take(10)) { d ->
                AppCard(padding = 13) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(d.who, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CK.Text, modifier = Modifier.weight(1f))
                        CloudChip(d.status)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun CampusScreen(activity: MainActivity, api: SupabaseApi, say: (String) -> Unit, onBack: () -> Unit) {
    var campus by remember { mutableStateOf<SupabaseApi.Campus?>(null) }
    var err by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { io({ api.campus() }) { r -> r.onSuccess { campus = it }.onFailure { err = it.message } } }

    Column(Modifier.fillMaxSize()) {
        OverlayHeader("Campus Settings", onBack)
        val c = campus
        if (c == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (err != null) Text(err!!, color = CK.Error, fontSize = 13.sp)
                else CircularProgressIndicator(color = CK.Primary)
            }
            return
        }
        var name by remember { mutableStateOf(c.name) }
        var lat by remember { mutableStateOf(c.lat.toString()) }
        var lng by remember { mutableStateOf(c.lng.toString()) }
        var radius by remember { mutableStateOf(c.radius.toString()) }
        var minAcc by remember { mutableStateOf(c.minAccuracy.toString()) }
        var mo by remember { mutableStateOf(c.morningOpen) }
        var mp by remember { mutableStateOf(c.presentUntil) }
        var ml by remember { mutableStateOf(c.lateUntil) }
        var ao by remember { mutableStateOf(c.afternoonOpen) }
        var ap by remember { mutableStateOf(c.afternoonPresentUntil) }
        var al by remember { mutableStateOf(c.afternoonLateUntil) }

        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                AppCard {
                    Text("Geofence", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(name, { name = it }, label = { Text("College name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                        OutlinedTextField(lng, { lng = it }, label = { Text("Longitude") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(radius, { radius = it.filter { ch -> ch.isDigit() } }, label = { Text("Radius (m)") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                        OutlinedTextField(minAcc, { minAcc = it.filter { ch -> ch.isDigit() } }, label = { Text("Min accuracy (m)") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                    }
                    Spacer(Modifier.height(10.dp))
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 6.dp), color = CK.Primary)
                    GhostButton("Set to this phone's location", icon = Icons.Outlined.MyLocation) {
                        activity.withLocationPermission {
                            busy = true
                            LocationClient(activity.applicationContext).current { loc, e ->
                                busy = false
                                if (loc != null) {
                                    lat = String.format(java.util.Locale.US, "%.5f", loc.latitude)
                                    lng = String.format(java.util.Locale.US, "%.5f", loc.longitude)
                                    say("Coordinates captured — tap Save.")
                                } else say(e ?: "Could not get location.")
                            }
                        }
                    }
                }
            }
            item {
                AppCard {
                    Text("Morning session (24h HH:mm)", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(mo, { mo = it }, label = { Text("Opens") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                        OutlinedTextField(mp, { mp = it }, label = { Text("Present till") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                        OutlinedTextField(ml, { ml = it }, label = { Text("Late till") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("Afternoon session", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(ao, { ao = it }, label = { Text("Opens") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                        OutlinedTextField(ap, { ap = it }, label = { Text("Present till") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                        OutlinedTextField(al, { al = it }, label = { Text("Late till") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                    }
                    if (err != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(err!!, color = CK.Error, fontSize = 12.sp)
                    }
                    Spacer(Modifier.height(12.dp))
                    PrimaryButton("Save Campus Settings") {
                        err = null
                        val la = lat.toDoubleOrNull(); val ln = lng.toDoubleOrNull()
                        val rad = radius.toIntOrNull(); val acc = minAcc.toIntOrNull()
                        val timeOk = listOf(mo, mp, ml, ao, ap, al).all { Regex("^\\d{2}:\\d{2}$").matches(it) }
                        when {
                            la == null || ln == null -> err = "Latitude/longitude must be numbers."
                            rad == null || rad < 20 -> err = "Radius must be at least 20 m."
                            acc == null || acc < 10 -> err = "Min accuracy must be at least 10 m."
                            !timeOk -> err = "Times must be HH:mm (24-hour)."
                            !(mo < mp && mp < ml) -> err = "Morning: opens < present till < late till."
                            !(ao < ap && ap < al) -> err = "Afternoon: opens < present till < late till."
                            else -> io({
                                api.updateCampus(SupabaseApi.Campus(c.id, name, la, ln, rad, acc, mo, mp, ml, ao, ap, al))
                            }) { r ->
                                r.onSuccess { say("Campus settings saved.") }.onFailure { err = it.message }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun ReportsCloudScreen(activity: MainActivity, api: SupabaseApi, say: (String) -> Unit, onBack: () -> Unit) {
    var days by remember { mutableIntStateOf(0) } // 0 = today, 7 = week, 30 = month
    var rows by remember { mutableStateOf<List<SupabaseApi.CloudRecord>?>(null) }
    LaunchedEffect(days) {
        rows = null
        val to = LocalDate.now().toString()
        val from = LocalDate.now().minusDays(days.toLong()).toString()
        io({ api.recordsBetween(from, to) }) { r -> r.onSuccess { rows = it }.onFailure { rows = emptyList(); say(it.message ?: "Load failed.") } }
    }

    fun exportCsv() {
        val list = rows ?: return
        io({
            val dir = File(activity.cacheDir, "exports").apply { mkdirs() }
            val f = File(dir, "classkey_report_${LocalDate.now()}.csv")
            f.writeText(buildString {
                appendLine("date,session,register_no,name,status,method,note")
                list.forEach { r ->
                    appendLine(listOf(r.date, r.session, r.regNo ?: "", r.name ?: "", r.status, r.method, (r.note ?: "").replace(",", ";")).joinToString(","))
                }
            })
            f
        }) { result ->
            result.onSuccess { f ->
                val uri = FileProvider.getUriForFile(activity, activity.packageName + ".fileprovider", f)
                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                activity.startActivity(Intent.createChooser(intent, "Share attendance CSV"))
            }.onFailure { say(it.message ?: "Export failed.") }
        }
    }

    Column(Modifier.fillMaxSize()) {
        OverlayHeader("Reports", onBack)
        LazyColumn(Modifier.weight(1f).padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterPill("Today", days == 0) { days = 0 }
                    FilterPill("Last 7 days", days == 7) { days = 7 }
                    FilterPill("Last 30 days", days == 30) { days = 30 }
                }
            }
            item {
                val list = rows ?: emptyList()
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StatCard("Present", list.count { it.status == "present" }, CK.Success, Modifier.weight(1f))
                    StatCard("Late", list.count { it.status == "late" }, CK.Warning, Modifier.weight(1f))
                    StatCard("Absent", list.count { it.status == "absent" }, CK.Error, Modifier.weight(1f))
                    StatCard("OD+", list.count { it.status in listOf("od", "half_day", "leave", "permission", "early_leave") }, CK.Primary, Modifier.weight(1f))
                }
            }
            item { GhostButton("Export CSV (share)") { exportCsv() } }
            if (rows == null) item { LinearProgressIndicator(Modifier.fillMaxWidth(), color = CK.Primary) }
            items(rows ?: emptyList()) { rec ->
                AppCard(padding = 13) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${rec.name ?: ""} · ${rec.regNo ?: ""}", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                            Text("${rec.date} · ${stLabel(rec.session)} · ${rec.method}", fontSize = 11.sp, color = CK.Muted)
                        }
                        CloudChip(rec.status)
                    }
                }
            }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

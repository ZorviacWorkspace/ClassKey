package com.classkey.modernattendance.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.classkey.modernattendance.AppViewModel
import com.classkey.modernattendance.Stage
import com.classkey.modernattendance.data.Role
import com.classkey.modernattendance.hw.runBiometric

// ─────────────────────────── Notifications ───────────────────────────

@Composable
fun NotificationsScreen(vm: AppViewModel) {
    val user = vm.user ?: return
    val reload = vm.reload
    val notifs = remember(reload) { vm.repo.notifsFor(user) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Spacer(Modifier.width(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Notifications", fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
                }
                Text(
                    "Mark all read", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = CK.Primary,
                    modifier = Modifier.clickable {
                        vm.repo.markAllRead(user)
                        vm.bump()
                    }
                )
            }
        }
        if (notifs.isEmpty()) {
            item { EmptyCard("No notifications", "Approvals, warnings and attendance updates appear here.") }
        }
        items(notifs) { n ->
            val (icon, tint, soft) = when (n.tone) {
                "success" -> Triple(Icons.Outlined.CheckCircle, CK.Success, CK.SoftGreen)
                "warn" -> Triple(Icons.Outlined.Warning, CK.Warning, CK.SoftAmber)
                "error" -> Triple(Icons.Outlined.Warning, CK.Error, CK.SoftRed)
                else -> Triple(Icons.Outlined.Notifications, CK.Primary, CK.SoftBlue)
            }
            AppCard(padding = 14) {
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(soft),
                        contentAlignment = Alignment.Center
                    ) { Icon(icon, null, Modifier.size(19.dp), tint = tint) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(n.title, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = CK.Text, modifier = Modifier.weight(1f))
                            if (!n.read) Box(Modifier.size(8.dp).clip(CircleShape).background(CK.Primary))
                        }
                        Text(n.body, fontSize = 12.sp, color = CK.Muted)
                        Spacer(Modifier.width(4.dp))
                        Text(n.createdAt, fontSize = 10.sp, color = CK.Muted.copy(alpha = .7f))
                    }
                }
            }
        }
        item { Spacer(Modifier.width(8.dp)) }
    }
}

// ─────────────────────────── Profile / Settings ───────────────────────────

@Composable
fun ProfileScreen(vm: AppViewModel, activity: FragmentActivity, onPickPhoto: () -> Unit) {
    val user = vm.user ?: return
    var showChangePassword by remember { mutableStateOf(false) }
    var showEditProfile by remember { mutableStateOf(false) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.width(4.dp))
            ScreenTitle("Profile", "Account, security and app settings.")
        }
        item {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Avatar(user, size = 64)
                        if (user.role == Role.STUDENT) {
                            Box(
                                Modifier.align(Alignment.BottomEnd).size(22.dp).clip(CircleShape)
                                    .background(CK.Primary).clickable { onPickPhoto() },
                                contentAlignment = Alignment.Center
                            ) { Icon(Icons.Outlined.CameraAlt, null, Modifier.size(12.dp), tint = Color.White) }
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column {
                        Text(user.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                        Text(
                            listOfNotNull(user.registerNo, user.department, user.year).joinToString(" · "),
                            fontSize = 12.sp, color = CK.Muted
                        )
                        Text(user.email, fontSize = 12.sp, color = CK.Muted)
                        Spacer(Modifier.width(4.dp))
                        PillChip(user.role.name, CK.SoftBlue, CK.Primary)
                    }
                }
            }
        }
        item {
            SectionHeader("Account")
            AppCard(padding = 0) {
                SettingRow(Icons.Outlined.Edit, "Edit profile", "Update your name and mobile number") {
                    showEditProfile = true
                }
            }
        }
        item {
            SectionHeader("Security")
            AppCard(padding = 0) {
                SettingRow(
                    Icons.Outlined.Fingerprint,
                    "Biometric login",
                    if (user.biometricEnabled) "Enabled · device bound" else "Not enabled",
                    valueColor = if (user.biometricEnabled) CK.Success else CK.Muted
                ) {
                    if (!user.biometricEnabled) {
                        runBiometric(
                            activity, "Enable ClassKey biometric",
                            "Verify once to bind this device to your account.",
                            onSuccess = {
                                vm.repo.enableBiometric(user)
                                vm.user = vm.repo.refreshUser(user.id)
                                vm.bump()
                                vm.say("Biometric login enabled.")
                            },
                            onError = { vm.say(it) }
                        )
                    } else vm.say("Biometric already enabled on this device.")
                }
                SettingRow(Icons.Outlined.Lock, "Change password", "Update your account password") {
                    showChangePassword = true
                }
                SettingRow(
                    Icons.Outlined.Info, "Device binding",
                    user.deviceId?.let { "Bound: ${it.take(10)}…" } ?: "No device bound yet"
                ) { vm.say("Attendance is accepted only after this device passes biometric verification.") }
            }
        }
        item {
            SectionHeader("About")
            AppCard(padding = 0) {
                SettingRow(Icons.Outlined.Info, "ClassKey", "Secure attendance · Verified presence · v2.0") {}
                SettingRow(
                    Icons.Outlined.Fingerprint, "Privacy",
                    "Raw fingerprints never leave Android's secure hardware"
                ) { vm.say("ClassKey stores only the verification result, never biometric data.") }
            }
        }
        item {
            AppCard(padding = 0) {
                Row(
                    Modifier.fillMaxWidth().clickable {
                        vm.repo.logout(user)
                        vm.user = null
                        vm.stage = Stage.LOGIN
                    }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.AutoMirrored.Outlined.Logout, null, Modifier.size(18.dp), tint = CK.Error)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out", color = CK.Error, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Spacer(Modifier.width(8.dp))
        }
    }

    if (showChangePassword) {
        ChangePasswordDialog(vm, onClose = { showChangePassword = false })
    }
    if (showEditProfile) {
        EditProfileDialog(vm, onClose = { showEditProfile = false })
    }
}

@Composable
private fun EditProfileDialog(vm: AppViewModel, onClose: () -> Unit) {
    val user = vm.user ?: return
    var name by remember { mutableStateOf(user.name) }
    var mobile by remember { mutableStateOf(user.mobile) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CK.Card,
        title = { Text("Edit profile", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it; error = null }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(mobile, { mobile = it.filter { c -> c.isDigit() } }, label = { Text("Mobile") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                if (error != null) Text(error!!, color = CK.Error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val err = vm.repo.editOwnProfile(user, name, mobile)
                if (err != null) error = err
                else {
                    vm.user = vm.repo.refreshUser(user.id)
                    vm.bump()
                    vm.say("Profile updated.")
                    onClose()
                }
            }) { Text("Save", fontWeight = FontWeight.Bold, color = CK.Primary) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", color = CK.Muted) } }
    )
}

@Composable
private fun SettingRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    valueColor: Color = CK.Muted,
    onClick: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(11.dp)).background(CK.SoftBlue),
            contentAlignment = Alignment.Center
        ) { Icon(icon, null, Modifier.size(18.dp), tint = CK.Primary) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CK.Text)
            Text(subtitle, fontSize = 11.sp, color = valueColor)
        }
    }
}

@Composable
private fun ChangePasswordDialog(vm: AppViewModel, onClose: () -> Unit) {
    val user = vm.user ?: return
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onClose,
        containerColor = CK.Card,
        title = { Text("Change password", fontSize = 17.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(old, { old = it; error = null }, label = { Text("Current password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(new, { new = it; error = null }, label = { Text("New password (min 8)") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                OutlinedTextField(confirm, { confirm = it; error = null }, label = { Text("Confirm new password") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp))
                if (error != null) Text(error!!, color = CK.Error, fontSize = 12.sp)
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (new != confirm) {
                    error = "New passwords do not match."
                    return@TextButton
                }
                val err = vm.repo.changePassword(user.id, old, new)
                if (err != null) error = err
                else {
                    vm.say("Password changed.")
                    onClose()
                }
            }) { Text("Save", fontWeight = FontWeight.Bold, color = CK.Primary) }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel", color = CK.Muted) } }
    )
}

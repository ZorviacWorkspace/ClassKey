package com.classkey.modernattendance.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Smartphone
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.classkey.modernattendance.AppViewModel
import com.classkey.modernattendance.R
import com.classkey.modernattendance.Stage
import com.classkey.modernattendance.hw.runBiometric
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(vm: AppViewModel) {
    val scale by animateFloatAsState(1f, animationSpec = tween(600), label = "logo")
    LaunchedEffect(Unit) {
        delay(1500)
        vm.stage = if (vm.user != null) Stage.HOME else Stage.LOGIN
    }
    Box(
        Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.White, CK.Bg))),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.classkey_logo), contentDescription = "ClassKey",
                modifier = Modifier.size(120.dp).scale(scale)
            )
            Spacer(Modifier.height(16.dp))
            Text("ClassKey", fontSize = 34.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text, letterSpacing = (-0.5).sp)
            Text("Secure attendance. Verified presence.", fontSize = 14.sp, color = CK.Muted)
            Spacer(Modifier.height(40.dp))
            CircularProgressIndicator(Modifier.size(22.dp), color = CK.Primary, strokeWidth = 2.5.dp)
        }
    }
}

@Composable
fun LoginScreen(vm: AppViewModel, activity: FragmentActivity) {
    var identifier by remember { mutableStateOf("CS21001") }
    var password by remember { mutableStateOf("ChangeMe123!") }
    var showPassword by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val boundUser = remember { vm.repo.boundUser() }

    LazyColumn(
        Modifier.fillMaxSize().padding(horizontal = 22.dp),
        verticalArrangement = Arrangement.Center
    ) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Image(painterResource(R.drawable.classkey_logo), null, Modifier.size(84.dp))
                Spacer(Modifier.height(12.dp))
                Text("ClassKey", fontSize = 30.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
                Text("Your presence, seamlessly verified.", fontSize = 13.sp, color = CK.Muted)
            }
            Spacer(Modifier.height(26.dp))
            AppCard(padding = 20) {
                Text("Sign in", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                Text("Use register no, email or mobile.", fontSize = 12.sp, color = CK.Muted)
                Spacer(Modifier.height(14.dp))
                OutlinedTextField(
                    identifier, { identifier = it; error = null },
                    label = { Text("Register no / Email / Mobile") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp)
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    password, { password = it; error = null },
                    label = { Text("Password") },
                    singleLine = true, modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(13.dp),
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showPassword = !showPassword }) {
                            Text(if (showPassword) "Hide" else "Show", fontSize = 12.sp)
                        }
                    }
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = CK.Error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Login") {
                    vm.repo.login(identifier, password)
                        .onSuccess { user ->
                            vm.user = user
                            vm.stage = Stage.HOME
                            vm.say("Welcome back, ${user.name.split(" ").first()}!")
                        }
                        .onFailure { error = it.message }
                }
                if (boundUser != null) {
                    Spacer(Modifier.height(10.dp))
                    GhostButton("Login with fingerprint · ${boundUser.name.split(" ").first()}", icon = Icons.Outlined.Fingerprint) {
                        runBiometric(
                            activity,
                            "ClassKey biometric login",
                            "Verify to sign in as ${boundUser.name}",
                            onSuccess = {
                                vm.repo.loginAs(boundUser)
                                vm.user = boundUser
                                vm.stage = Stage.HOME
                                vm.say("Biometric login successful.")
                            },
                            onError = { vm.say(it) }
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = { vm.stage = Stage.SIGNUP }, modifier = Modifier.fillMaxWidth()) {
                    Text("New student? Create account", color = CK.Primary, fontWeight = FontWeight.SemiBold)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                "QUICK DEMO LOGINS", fontSize = 10.sp, fontWeight = FontWeight.Bold,
                color = CK.Muted, letterSpacing = 1.5.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                DemoChip("Student", Icons.Outlined.School) { identifier = "CS21001"; password = "ChangeMe123!" }
                Spacer(Modifier.width(8.dp))
                DemoChip("Staff", Icons.Outlined.Person) { identifier = "9990003000"; password = "ChangeMe123!" }
                Spacer(Modifier.width(8.dp))
                DemoChip("Admin", Icons.Outlined.AdminPanelSettings) { identifier = "9990001000"; password = "ChangeMe123!" }
            }
            Spacer(Modifier.height(20.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                TrustBadge(Icons.Outlined.Shield, "Biometric")
                Spacer(Modifier.width(14.dp))
                TrustBadge(Icons.Outlined.LocationOn, "Location")
                Spacer(Modifier.width(14.dp))
                TrustBadge(Icons.Outlined.Smartphone, "Trusted device")
            }
            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun DemoChip(label: String, icon: ImageVector, onClick: () -> Unit) {
    AssistChip(
        onClick = onClick,
        label = { Text(label, fontSize = 12.sp) },
        leadingIcon = { Icon(icon, null, Modifier.size(16.dp), tint = CK.Primary) }
    )
}

@Composable
private fun TrustBadge(icon: ImageVector, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(14.dp), tint = CK.Primary)
        Spacer(Modifier.width(4.dp))
        Text(label, fontSize = 11.sp, color = CK.Muted)
    }
}

@Composable
fun SignupScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var email by remember { mutableStateOf("") }
    var mobile by remember { mutableStateOf("") }
    var reg by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
        item {
            ScreenTitle("Create student account", "College email required · staff and admin accounts are created by the office.")
            Spacer(Modifier.height(12.dp))
            AppCard(padding = 20) {
                OutlinedTextField(name, { name = it }, label = { Text("Full name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(email, { email = it }, label = { Text("College email ID") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(mobile, { mobile = it.filter { ch -> ch.isDigit() } }, label = { Text("Mobile number") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(reg, { reg = it }, label = { Text("Register number (e.g. CS21010)") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    password, { password = it }, label = { Text("Password (min 8 chars)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp),
                    visualTransformation = PasswordVisualTransformation()
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = CK.Error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Create Account") {
                    vm.repo.signup(name, email, mobile, reg, password)
                        .onSuccess { user ->
                            vm.user = user
                            vm.stage = Stage.PROFILE_SETUP
                            vm.say("Account created. Set up your profile.")
                        }
                        .onFailure { error = it.message }
                }
                TextButton(onClick = { vm.stage = Stage.LOGIN }, modifier = Modifier.fillMaxWidth()) {
                    Text("Back to login", color = CK.Muted)
                }
            }
        }
    }
}

@Composable
fun ProfileSetupScreen(vm: AppViewModel, onPickPhoto: () -> Unit) {
    val user = vm.user ?: return
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
        item {
            ScreenTitle("Profile setup", "Add a photo so staff can recognise you.")
            Spacer(Modifier.height(12.dp))
            AppCard(padding = 20) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        Avatar(user, size = 78)
                        Box(
                            Modifier.align(Alignment.BottomEnd).size(26.dp).clip(CircleShape)
                                .background(CK.Primary).clickable { onPickPhoto() },
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Outlined.CameraAlt, null, Modifier.size(14.dp), tint = Color.White) }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column {
                        Text(user.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                        Text(user.registerNo ?: "", fontSize = 13.sp, color = CK.Muted)
                        Text(user.email, fontSize = 12.sp, color = CK.Muted)
                    }
                }
                Spacer(Modifier.height(18.dp))
                GhostButton("Add profile photo", icon = Icons.Outlined.CameraAlt) { onPickPhoto() }
                Spacer(Modifier.height(10.dp))
                FeatureRow(Icons.Outlined.Badge, "Register number verified", true)
                FeatureRow(Icons.Outlined.Person, "College email on record", true)
                FeatureRow(Icons.Outlined.Smartphone, "This phone becomes your attendance device", true)
                Spacer(Modifier.height(14.dp))
                PrimaryButton("Continue to biometric setup") { vm.stage = Stage.BIOMETRIC_SETUP }
            }
        }
    }
}

@Composable
fun BiometricSetupScreen(vm: AppViewModel, activity: FragmentActivity) {
    val user = vm.user ?: return
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 22.dp), verticalArrangement = Arrangement.Center) {
        item {
            AppCard(padding = 22) {
                Box(
                    Modifier.size(84.dp).clip(CircleShape).background(CK.SoftBlue),
                    contentAlignment = Alignment.Center
                ) { Icon(Icons.Outlined.Fingerprint, null, Modifier.size(44.dp), tint = CK.Primary) }
                Spacer(Modifier.height(16.dp))
                Text("Enable ClassKey biometric", fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = CK.Text)
                Text(
                    "Android verifies your fingerprint or device lock securely. ClassKey never sees or stores raw fingerprint data — only the verified result.",
                    fontSize = 13.sp, color = CK.Muted
                )
                Spacer(Modifier.height(14.dp))
                FeatureRow(Icons.Outlined.Fingerprint, "One-tap biometric login", true)
                FeatureRow(Icons.Outlined.Shield, "Required before attendance is accepted", true)
                FeatureRow(Icons.Outlined.Smartphone, "Identity bound to this device", true)
                Spacer(Modifier.height(16.dp))
                PrimaryButton("Scan fingerprint / device lock", icon = Icons.Outlined.Fingerprint) {
                    runBiometric(
                        activity,
                        "Enable ClassKey biometric",
                        "Verify once to bind this device to your account.",
                        onSuccess = {
                            vm.repo.enableBiometric(user)
                            vm.user = vm.repo.refreshUser(user.id)
                            vm.stage = Stage.HOME
                            vm.say("Biometric enabled. This device is now bound to your account.")
                        },
                        onError = { vm.say(it) }
                    )
                }
                TextButton(
                    onClick = { vm.stage = Stage.HOME; vm.say("You can enable biometric later in Profile.") },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("Set up later", color = CK.Muted) }
            }
        }
    }
}

@Composable
private fun FeatureRow(icon: ImageVector, text: String, done: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(17.dp), tint = if (done) CK.Success else CK.Muted)
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 13.sp, color = CK.Text, modifier = Modifier.alpha(0.9f))
    }
}

package com.classkey.modernattendance

import android.Manifest
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AssignmentTurnedIn
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.viewmodel.compose.viewModel
import com.classkey.modernattendance.data.AttendanceRec
import com.classkey.modernattendance.data.Role
import com.classkey.modernattendance.ui.ApprovalsScreen
import com.classkey.modernattendance.ui.AuditScreen
import com.classkey.modernattendance.ui.BiometricSetupScreen
import com.classkey.modernattendance.ui.CK
import com.classkey.modernattendance.ui.CampusConfigScreen
import com.classkey.modernattendance.ui.ClassKeyTheme
import com.classkey.modernattendance.ui.HistoryScreen
import com.classkey.modernattendance.ui.LoginScreen
import com.classkey.modernattendance.ui.MarkAttendanceScreen
import com.classkey.modernattendance.ui.MessageBar
import com.classkey.modernattendance.ui.NewRequestScreen
import com.classkey.modernattendance.ui.NotificationsScreen
import com.classkey.modernattendance.ui.ProfileScreen
import com.classkey.modernattendance.ui.ProfileSetupScreen
import com.classkey.modernattendance.ui.ReportsScreen
import com.classkey.modernattendance.ui.RequestsScreen
import com.classkey.modernattendance.ui.SignupScreen
import com.classkey.modernattendance.ui.SplashScreen
import com.classkey.modernattendance.ui.StaffDashboard
import com.classkey.modernattendance.ui.StudentHome
import com.classkey.modernattendance.ui.StudentListScreen
import com.classkey.modernattendance.ui.SuccessScreen
import java.io.File

class MainActivity : FragmentActivity() {

    private var pendingPermissionAction: (() -> Unit)? = null
    private var onPhotoPicked: ((String?) -> Unit)? = null

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        val granted = result[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            result[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        val action = pendingPermissionAction
        pendingPermissionAction = null
        if (granted) action?.invoke()
    }

    private val photoPicker = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        val callback = onPhotoPicked
        onPhotoPicked = null
        if (uri == null) {
            callback?.invoke(null)
            return@registerForActivityResult
        }
        val path = try {
            val file = File(filesDir, "profile_${System.currentTimeMillis()}.jpg")
            contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { input.copyTo(it) }
            }
            file.absolutePath
        } catch (_: Exception) {
            null
        }
        callback?.invoke(path)
    }

    /** Runs [action] immediately when location permission is already granted, else asks first. */
    fun withLocationPermission(action: () -> Unit) {
        if (com.classkey.modernattendance.hw.hasLocationPermission(this)) {
            action()
        } else {
            pendingPermissionAction = action
            locationPermissionLauncher.launch(
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            )
        }
    }

    fun pickPhoto(onPicked: (String?) -> Unit) {
        onPhotoPicked = onPicked
        photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ClassKeyTheme {
                Surface(Modifier.fillMaxSize(), color = CK.Bg) {
                    if (com.classkey.modernattendance.data.SupabaseApi.isConfigured()) {
                        // Cloud mode: same Supabase project as the web dashboard.
                        Box(Modifier.fillMaxSize().statusBarsPadding()) {
                            com.classkey.modernattendance.ui.CloudApp(this@MainActivity)
                        }
                    } else {
                        // Offline demo mode (local SQLite) when no Supabase keys are set.
                        val vm: AppViewModel = viewModel()
                        ClassKeyApp(vm, this@MainActivity)
                    }
                }
            }
        }
    }
}

// ───────────────────────────── App shell ─────────────────────────────

private sealed class Overlay {
    data object Mark : Overlay()
    data class Success(val rec: AttendanceRec) : Overlay()
    data object NewRequest : Overlay()
    data object Approvals : Overlay()
    data object Audit : Overlay()
}

private data class TabItem(val label: String, val icon: ImageVector)

@Composable
fun ClassKeyApp(vm: AppViewModel, activity: MainActivity) {
    Box(Modifier.fillMaxSize().statusBarsPadding()) {
        AnimatedContent(
            targetState = vm.stage,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "stage"
        ) { stage ->
            when (stage) {
                Stage.SPLASH -> SplashScreen(vm)
                Stage.LOGIN -> LoginScreen(vm, activity)
                Stage.SIGNUP -> SignupScreen(vm)
                Stage.PROFILE_SETUP -> ProfileSetupScreen(vm) {
                    activity.pickPhoto { path ->
                        val user = vm.user
                        if (path != null && user != null) {
                            vm.repo.savePhoto(user.id, path)
                            vm.user = vm.repo.refreshUser(user.id)
                            vm.say("Profile photo saved.")
                        }
                    }
                }
                Stage.BIOMETRIC_SETUP -> BiometricSetupScreen(vm, activity)
                Stage.HOME -> HomeShell(vm, activity)
            }
        }
        vm.message?.let { msg ->
            MessageBar(
                msg,
                onClose = { vm.message = null },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 66.dp)
            )
        }
    }
}

@Composable
private fun HomeShell(vm: AppViewModel, activity: MainActivity) {
    val user = vm.user
    if (user == null) {
        vm.stage = Stage.LOGIN
        return
    }
    var tab by remember(user.id) { mutableStateOf("Home") }
    var overlay by remember(user.id) { mutableStateOf<Overlay?>(null) }

    val tabs = when (user.role) {
        Role.STUDENT -> listOf(
            TabItem("Home", Icons.Outlined.Home),
            TabItem("History", Icons.Outlined.History),
            TabItem("Requests", Icons.Outlined.Description),
            TabItem("Alerts", Icons.Outlined.Notifications),
            TabItem("Profile", Icons.Outlined.Person)
        )
        Role.STAFF -> listOf(
            TabItem("Home", Icons.Outlined.Home),
            TabItem("Students", Icons.Outlined.Groups),
            TabItem("Approvals", Icons.Outlined.AssignmentTurnedIn),
            TabItem("Reports", Icons.Outlined.BarChart),
            TabItem("Profile", Icons.Outlined.Person)
        )
        Role.ADMIN -> listOf(
            TabItem("Home", Icons.Outlined.Home),
            TabItem("Students", Icons.Outlined.Groups),
            TabItem("Campus", Icons.Outlined.MyLocation),
            TabItem("Reports", Icons.Outlined.BarChart),
            TabItem("Profile", Icons.Outlined.Person)
        )
    }

    val currentOverlay = overlay
    if (currentOverlay != null) {
        when (currentOverlay) {
            is Overlay.Mark -> MarkAttendanceScreen(
                vm, activity,
                requestLocationPermission = { onGranted -> activity.withLocationPermission(onGranted) },
                onClose = { overlay = null },
                onMarked = { rec -> overlay = Overlay.Success(rec) }
            )
            is Overlay.Success -> SuccessScreen(currentOverlay.rec) {
                overlay = null
                tab = "Home"
            }
            is Overlay.NewRequest -> NewRequestScreen(vm) { overlay = null }
            is Overlay.Approvals -> ApprovalsScreen(vm, asOverlay = true) { overlay = null }
            is Overlay.Audit -> AuditScreen(vm) { overlay = null }
        }
        return
    }

    Column(Modifier.fillMaxSize()) {
        Box(Modifier.weight(1f)) {
            when (user.role) {
                Role.STUDENT -> when (tab) {
                    "Home" -> StudentHome(vm, onMark = { overlay = Overlay.Mark }, onTab = { tab = it })
                    "History" -> HistoryScreen(vm)
                    "Requests" -> RequestsScreen(vm) { overlay = Overlay.NewRequest }
                    "Alerts" -> NotificationsScreen(vm)
                    else -> ProfileScreen(vm, activity) {
                        activity.pickPhoto { path ->
                            if (path != null) {
                                vm.repo.savePhoto(user.id, path)
                                vm.user = vm.repo.refreshUser(user.id)
                                vm.say("Profile photo updated.")
                            }
                        }
                    }
                }
                Role.STAFF -> when (tab) {
                    "Home" -> StaffDashboard(vm, onTab = { tab = it }, onApprovals = { tab = "Approvals" }, onAudit = {})
                    "Students" -> StudentListScreen(vm)
                    "Approvals" -> ApprovalsScreen(vm)
                    "Reports" -> ReportsScreen(vm)
                    else -> ProfileScreen(vm, activity) {}
                }
                Role.ADMIN -> when (tab) {
                    "Home" -> StaffDashboard(vm, onTab = { tab = it }, onApprovals = { overlay = Overlay.Approvals }, onAudit = { overlay = Overlay.Audit })
                    "Students" -> StudentListScreen(vm)
                    "Campus" -> CampusConfigScreen(vm, activity) { onGranted -> activity.withLocationPermission(onGranted) }
                    "Reports" -> ReportsScreen(vm)
                    else -> ProfileScreen(vm, activity) {}
                }
            }
        }
        BottomTabBar(tabs, tab) { tab = it }
    }
}

@Composable
private fun BottomTabBar(tabs: List<TabItem>, active: String, onSelect: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(CK.Card).navigationBarsPadding()
            .padding(horizontal = 6.dp, vertical = 7.dp)
    ) {
        tabs.forEach { item ->
            val selected = item.label == active
            Column(
                Modifier.weight(1f).clip(RoundedCornerShape(13.dp))
                    .clickable { onSelect(item.label) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    item.icon, item.label,
                    Modifier.size(21.dp),
                    tint = if (selected) CK.Primary else CK.Muted
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    item.label,
                    fontSize = 10.sp,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) CK.Primary else CK.Muted
                )
            }
        }
    }
}

package com.classkey.modernattendance.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.classkey.modernattendance.AppViewModel
import com.classkey.modernattendance.data.CampusCfg
import com.classkey.modernattendance.hw.LocationClient
import java.time.LocalTime
import java.time.format.DateTimeFormatter

@Composable
fun CampusConfigScreen(
    vm: AppViewModel,
    activity: FragmentActivity,
    requestLocationPermission: (onGranted: () -> Unit) -> Unit
) {
    val user = vm.user ?: return
    val reload = vm.reload
    val cfg = remember(reload) { vm.repo.campus() }
    var name by remember(cfg) { mutableStateOf(cfg.name) }
    var lat by remember(cfg) { mutableStateOf(String.format(java.util.Locale.US, "%.5f", cfg.latitude)) }
    var lng by remember(cfg) { mutableStateOf(String.format(java.util.Locale.US, "%.5f", cfg.longitude)) }
    var radius by remember(cfg) { mutableStateOf(cfg.radiusMeters.toInt().toString()) }
    var openTime by remember(cfg) { mutableStateOf(cfg.openTime.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var lateAfter by remember(cfg) { mutableStateOf(cfg.lateAfter.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var cutoff by remember(cfg) { mutableStateOf(cfg.cutoff.format(DateTimeFormatter.ofPattern("HH:mm"))) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val locationClient = remember { LocationClient(activity.applicationContext) }

    fun useCurrentLocation() {
        busy = true
        error = null
        locationClient.current { location, err ->
            busy = false
            if (location == null) {
                error = err ?: "Could not fetch location."
            } else {
                lat = String.format(java.util.Locale.US, "%.5f", location.latitude)
                lng = String.format(java.util.Locale.US, "%.5f", location.longitude)
                vm.say("Coordinates captured from this phone. Tap Save to apply.")
            }
        }
    }

    fun save() {
        error = null
        val la = lat.toDoubleOrNull()
        val ln = lng.toDoubleOrNull()
        val r = radius.toDoubleOrNull()
        val open = try { LocalTime.parse(openTime) } catch (_: Exception) { null }
        val late = try { LocalTime.parse(lateAfter) } catch (_: Exception) { null }
        val cut = try { LocalTime.parse(cutoff) } catch (_: Exception) { null }
        when {
            la == null || la < -90 || la > 90 -> error = "Latitude must be a number between -90 and 90."
            ln == null || ln < -180 || ln > 180 -> error = "Longitude must be a number between -180 and 180."
            r == null || r < 20 -> error = "Radius must be at least 20 meters."
            open == null -> error = "Opens-at time must be HH:mm (e.g. 08:00)."
            late == null -> error = "Present-till time must be HH:mm (e.g. 09:15)."
            cut == null -> error = "Cutoff time must be HH:mm (e.g. 17:00)."
            !late.isAfter(open) -> error = "Present-till must be after the opens-at time."
            cut.isBefore(late) -> error = "Cutoff must be at or after the present-till time."
            else -> {
                vm.repo.saveCampus(
                    CampusCfg(name.ifBlank { "College Main Gate" }, la, ln, r, open, late, cut), user
                )
                vm.bump()
                vm.say("Campus settings saved and audited.")
            }
        }
    }

    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Spacer(Modifier.height(4.dp))
            ScreenTitle("Campus Setup", "Geofence and attendance timing for the daily sheet.")
        }
        item {
            AppCard {
                Text("Geofence", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Location name") }, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp))
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(lat, { lat = it }, label = { Text("Latitude") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                    OutlinedTextField(lng, { lng = it }, label = { Text("Longitude") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    radius, { radius = it.filter { c -> c.isDigit() } },
                    label = { Text("Allowed radius (meters)") }, singleLine = true,
                    modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)
                )
                Spacer(Modifier.height(10.dp))
                if (busy) LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp), color = CK.Primary)
                GhostButton("Set to this phone's current location", icon = Icons.Outlined.MyLocation) {
                    requestLocationPermission { useCurrentLocation() }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Stand at the campus gate, tap the button above, then Save. Student attendance then verifies against this point.",
                    fontSize = 11.sp, color = CK.Muted
                )
            }
        }
        item {
            AppCard {
                Text("Attendance window", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CK.Text)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(openTime, { openTime = it }, label = { Text("Opens at") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                    OutlinedTextField(lateAfter, { lateAfter = it }, label = { Text("Present till") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                    OutlinedTextField(cutoff, { cutoff = it }, label = { Text("Cutoff") }, singleLine = true, modifier = Modifier.weight(1f), shape = RoundedCornerShape(13.dp))
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "Times are 24-hour HH:mm. Before 'Opens at' marking is not allowed · 'Opens at'→'Present till' = Present · 'Present till'→'Cutoff' = Late · after 'Cutoff' self-marking closes and unmarked students count Absent.",
                    fontSize = 11.sp, color = CK.Muted
                )
                if (error != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(error!!, color = CK.Error, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                PrimaryButton("Save Campus Settings") { save() }
            }
        }
        item { HolidaysCard(vm) }
        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HolidaysCard(vm: AppViewModel) {
    val user = vm.user ?: return
    val reload = vm.reload
    val holidays = remember(reload) { vm.repo.holidays() }
    var dayOffset by remember { mutableStateOf(0L) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val target = java.time.LocalDate.now().plusDays(dayOffset)

    AppCard {
        Text("Holidays", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = CK.Text)
        Text("Marked days never count as Absent for anyone.", fontSize = 11.sp, color = CK.Muted)
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            com.classkey.modernattendance.ui.FilterPill("Today", dayOffset == 0L) { dayOffset = 0L }
            com.classkey.modernattendance.ui.FilterPill("Tomorrow", dayOffset == 1L) { dayOffset = 1L }
            com.classkey.modernattendance.ui.FilterPill("+2 days", dayOffset == 2L) { dayOffset = 2L }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            note, { note = it; error = null },
            label = { Text("Reason (e.g. Pongal, Exam leave)") },
            singleLine = true, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(13.dp)
        )
        if (error != null) {
            Spacer(Modifier.height(6.dp))
            Text(error!!, color = CK.Error, fontSize = 12.sp)
        }
        Spacer(Modifier.height(10.dp))
        GhostButton("Mark ${target.format(DateTimeFormatter.ofPattern("EEE, d MMM"))} as holiday") {
            val err = vm.repo.addHoliday(target, note, user)
            if (err != null) error = err
            else { note = ""; vm.bump(); vm.say("Holiday saved.") }
        }
        if (holidays.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            holidays.forEach { h ->
                Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            java.time.LocalDate.parse(h.date).format(DateTimeFormatter.ofPattern("EEE, d MMM yyyy")),
                            fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = CK.Text
                        )
                        Text(h.note, fontSize = 11.sp, color = CK.Muted)
                    }
                    Text(
                        "Remove", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CK.Error,
                        modifier = Modifier.clickable {
                            vm.repo.removeHoliday(h.date, user); vm.bump(); vm.say("Holiday removed.")
                        }
                    )
                }
            }
        }
    }
}

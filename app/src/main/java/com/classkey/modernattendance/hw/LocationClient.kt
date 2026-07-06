package com.classkey.modernattendance.hw

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

/**
 * Fused Location Provider wrapper that never crashes the app:
 * handles permission missing, GPS off, timeout, null fixes and stale fallback.
 */
class LocationClient(private val context: Context) {

    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return try {
            manager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            false
        }
    }

    fun current(onResult: (Location?, String?) -> Unit) {
        if (!hasLocationPermission(context)) {
            onResult(null, "Location permission is required. Please allow location access.")
            return
        }
        if (!isLocationEnabled()) {
            onResult(null, "Location/GPS is OFF. Turn it on in quick settings and try again.")
            return
        }
        val client = LocationServices.getFusedLocationProviderClient(context)
        val cts = CancellationTokenSource()
        var finished = false
        val handler = Handler(Looper.getMainLooper())

        fun finish(location: Location?, error: String?) {
            if (finished) return
            finished = true
            onResult(location, error)
        }

        // Hard timeout so the UI never spins forever.
        handler.postDelayed({
            if (!finished) {
                cts.cancel()
                client.lastLocation
                    .addOnSuccessListener { last ->
                        if (last != null) finish(last, null)
                        else finish(null, "Could not get a GPS fix. Move near a window or open sky and retry.")
                    }
                    .addOnFailureListener {
                        finish(null, "Could not get a GPS fix. Move near a window or open sky and retry.")
                    }
            }
        }, 15_000)

        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                .addOnSuccessListener { loc ->
                    if (loc != null) finish(loc, null)
                    else client.lastLocation
                        .addOnSuccessListener { last ->
                            if (last != null) finish(last, null)
                            else finish(null, "Location unavailable right now. Move to open sky and retry.")
                        }
                        .addOnFailureListener { finish(null, "Location fetch failed. Try again.") }
                }
                .addOnFailureListener { finish(null, "Location fetch failed: ${it.message ?: "unknown error"}") }
        } catch (_: SecurityException) {
            finish(null, "Location permission was denied by Android. Allow it in app settings.")
        } catch (e: Exception) {
            finish(null, "Location error: ${e.message ?: "unknown"}")
        }
    }
}

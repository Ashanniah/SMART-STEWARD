package com.example.smart_steward

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Resolves a short human-readable label from the last fused fix (reverse geocode or coordinates).
 */
object LocationLabelHelper {

    private val io = Executors.newSingleThreadExecutor()

    fun resolveShortLabel(context: Context, callback: (String) -> Unit) {
        val app = context.applicationContext
        val fine = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            app,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) {
            callback(app.getString(R.string.location_unavailable_short))
            return
        }
        LocationServices.getFusedLocationProviderClient(app).lastLocation
            .addOnCompleteListener { task ->
                val loc = if (task.isSuccessful) task.result else null
                if (loc == null) {
                    callback(app.getString(R.string.location_unavailable_short))
                    return@addOnCompleteListener
                }
                val lat = loc.latitude
                val lng = loc.longitude
                io.execute {
                    val label = geocodeLabel(app, lat, lng)
                        ?: String.format(Locale.US, "%.5f, %.5f", lat, lng)
                    Handler(Looper.getMainLooper()).post { callback(label) }
                }
            }
    }

    private fun geocodeLabel(context: Context, lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        return try {
            @Suppress("DEPRECATION")
            val list = Geocoder(context, Locale.getDefault()).getFromLocation(lat, lng, 1)
            val a = list?.firstOrNull() ?: return null
            buildString {
                a.subLocality?.takeIf { it.isNotBlank() }?.let {
                    append(it)
                    append(", ")
                }
                a.locality?.takeIf { it.isNotBlank() }?.let {
                    append(it)
                    append(", ")
                }
                a.adminArea?.takeIf { it.isNotBlank() }?.let { append(it) }
            }
                .trim()
                .trimEnd(',')
                .trim()
                .ifBlank { null }
        } catch (_: Exception) {
            null
        }
    }
}

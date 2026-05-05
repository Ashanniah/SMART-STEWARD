package com.example.smart_steward

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
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
        val client = LocationServices.getFusedLocationProviderClient(app)
        val cts = CancellationTokenSource()
        // Prefer a fresh high-accuracy fix, then last known location.
        client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
            .addOnCompleteListener { curTask ->
                val cur = if (curTask.isSuccessful) curTask.result else null
                if (cur != null) {
                    postGeocodedLabel(app, cur.latitude, cur.longitude, callback)
                    return@addOnCompleteListener
                }
                client.lastLocation.addOnCompleteListener { lastTask ->
                    val loc = if (lastTask.isSuccessful) lastTask.result else null
                    if (loc == null) {
                        callback(app.getString(R.string.location_unavailable_short))
                    } else {
                        postGeocodedLabel(app, loc.latitude, loc.longitude, callback)
                    }
                }
            }
    }

    private fun postGeocodedLabel(
        app: Context,
        lat: Double,
        lng: Double,
        callback: (String) -> Unit
    ) {
        io.execute {
            val label = geocodeLabel(app, lat, lng)
                ?: String.format(Locale.US, "%.5f, %.5f", lat, lng)
            Handler(Looper.getMainLooper()).post { callback(label) }
        }
    }

    private fun geocodeLabel(context: Context, lat: Double, lng: Double): String? {
        if (!Geocoder.isPresent()) return null
        val locales = listOf(Locale("en", "PH"), Locale.getDefault())
        for (locale in locales) {
            try {
                @Suppress("DEPRECATION")
                val list = Geocoder(context, locale).getFromLocation(lat, lng, 5)
                val a = list?.firstOrNull() ?: continue
                a.getAddressLine(0)?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
                buildFallbackFromAddress(a)?.let { return it }
            } catch (_: Exception) {
                continue
            }
        }
        return null
    }

    private fun buildFallbackFromAddress(a: Address): String? {
        return buildString {
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
    }
}

package com.example.smart_steward

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.PorterDuff
import androidx.annotation.ColorInt
import androidx.annotation.DrawableRes
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private val REGION_CENTER = LatLng(10.3697, 123.9180)

enum class DashboardMapScope {
    ALL,
    INCIDENTS,
    ILLEGAL_ACTIVITIES,
    AGENCIES
}

enum class DashboardTypeFilter {
    ALL,
    DUMPING,
    BURNING,
    LOGGING,
    /** Incident types that do not match dumping, burning, or logging (e.g. theft). */
    OTHER
}

data class MapAgencyPin(
    val id: String,
    val name: String,
    val shortLabel: String,
    val position: LatLng
)

val DEFAULT_AGENCY_PINS: List<MapAgencyPin> = listOf(
    MapAgencyPin("denr_cebu", "DENR — Cebu City", "DENR", LatLng(10.3157, 123.8854)),
    MapAgencyPin("denr_field", "DENR Field Office (Talamban)", "DENR", LatLng(10.3725, 123.9201)),
    MapAgencyPin("pnp_cebu", "PNP — Cebu City", "PNP", LatLng(10.3096, 123.8937)),
    MapAgencyPin("bfp_cebu", "BFP Cebu City", "BFP", LatLng(10.2926, 123.9022)),
    MapAgencyPin("barangay_lahug", "Barangay Lahug Office", "Barangay", LatLng(10.3386, 123.9001))
)

fun matchesAgencyFilter(pin: MapAgencyPin, selectedAgency: String?): Boolean {
    if (selectedAgency.isNullOrBlank()) return true
    return pin.shortLabel.equals(selectedAgency, ignoreCase = true)
}

fun matchesReportAgencyFilter(report: UserReport, selectedAgency: String?): Boolean {
    if (selectedAgency.isNullOrBlank()) return true
    val assigned = report.assignedAgency.trim()
    if (assigned.isBlank()) return false
    val target = selectedAgency.trim()
    return assigned.equals(target, ignoreCase = true) ||
        assigned.contains(target, ignoreCase = true)
}

fun UserReport.displayTitle(): String =
    incidentType.substringBefore("(").trim().ifBlank { incidentType }

fun UserReport.locationDisplay(): String =
    locationLine.removePrefix("Location:").trim().ifBlank { "Unknown area" }

fun UserReport.effectivePosition(): LatLng {
    val lat = latitude
    val lng = longitude
    if (lat != null && lng != null) return LatLng(lat, lng)
    return pseudoPositionForId(id, REGION_CENTER)
}

private fun pseudoPositionForId(id: String, center: LatLng): LatLng {
    val h = id.hashCode()
    val angle = (h and 0xffff) * (2.0 * Math.PI / 65536.0)
    val radius = 0.0018 + (abs(h) % 600) / 180_000.0
    return LatLng(
        center.latitude + radius * cos(angle),
        center.longitude + radius * sin(angle)
    )
}

fun isIllegalActivityType(incidentType: String): Boolean {
    val t = incidentType.lowercase()
    return listOf(
        "dump", "burn", "log", "poach", "illegal", "waste", "fire", "tree", "forest",
        "theft", "steal", "rob", "vandal"
    ).any { t.contains(it) }
}

private fun incidentTypePrimaryBucket(incidentType: String): DashboardTypeFilter {
    val t = incidentType.lowercase()
    val isDump = t.contains("dump") || t.contains("waste") || t.contains("trash")
    val isBurn = t.contains("burn") || t.contains("fire") || t.contains("smoke")
    val isLog = t.contains("log") || t.contains("tree") || t.contains("forest")
    return when {
        isDump -> DashboardTypeFilter.DUMPING
        isBurn -> DashboardTypeFilter.BURNING
        isLog -> DashboardTypeFilter.LOGGING
        else -> DashboardTypeFilter.OTHER
    }
}

fun matchesTypeFilter(report: UserReport, filter: DashboardTypeFilter): Boolean {
    if (filter == DashboardTypeFilter.ALL) return true
    if (filter == DashboardTypeFilter.OTHER) {
        return incidentTypePrimaryBucket(report.incidentType) == DashboardTypeFilter.OTHER
    }
    return incidentTypePrimaryBucket(report.incidentType) == filter
}

private var cachedPendingMarker: BitmapDescriptor? = null
private var cachedInProgressMarker: BitmapDescriptor? = null

fun markerDescriptorForReport(context: Context, report: UserReport): BitmapDescriptor {
    when (report.status) {
        ReportStatusUi.PENDING -> {
            if (cachedPendingMarker == null) {
                val app = context.applicationContext
                cachedPendingMarker = bitmapDescriptorFromVector(app, R.drawable.ic_map_marker_gray, 40f)
            }
            return cachedPendingMarker!!
        }
        ReportStatusUi.REJECTED ->
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
        ReportStatusUi.IN_PROGRESS -> {
            // Tint the same teardrop pin with the exact amber used by the
            // "In Progress" pill in the Nearby Incidents list so the map and
            // list read as the same status at a glance.
            if (cachedInProgressMarker == null) {
                val app = context.applicationContext
                val tint = ContextCompat.getColor(app, R.color.activity_progress_blue)
                cachedInProgressMarker =
                    bitmapDescriptorFromVector(app, R.drawable.ic_map_marker_gray, 40f, tint)
            }
            return cachedInProgressMarker!!
        }
        ReportStatusUi.RESOLVED ->
            return BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
    }
}

fun markerSnippetStatus(status: ReportStatusUi): String = when (status) {
    ReportStatusUi.PENDING -> "PENDING"
    ReportStatusUi.IN_PROGRESS -> "IN PROGRESS"
    ReportStatusUi.RESOLVED -> "RESOLVED"
    ReportStatusUi.REJECTED -> "REJECTED"
}

/**
 * Resolved / rejected reports stay pinned to the map for this many
 * milliseconds after the status change so the outcome is briefly visible,
 * then auto-disappear while the report itself remains in history.
 */
const val TERMINAL_MAP_MARKER_TTL_MS: Long = 60_000L

/**
 * `true` for active reports (pending / in-progress) and for closed reports
 * whose status change is still inside [TERMINAL_MAP_MARKER_TTL_MS].
 */
fun UserReport.isVisibleOnMap(nowMs: Long = System.currentTimeMillis()): Boolean {
    return when (status) {
        ReportStatusUi.PENDING,
        ReportStatusUi.IN_PROGRESS -> true
        ReportStatusUi.RESOLVED,
        ReportStatusUi.REJECTED -> {
            val updated = statusUpdatedAt?.time ?: return false
            nowMs - updated <= TERMINAL_MAP_MARKER_TTL_MS
        }
    }
}

/**
 * Returns the earliest epoch-ms at which one of the closed markers in
 * [reports] is about to expire, or `Long.MAX_VALUE` if nothing is pending.
 */
fun nextMarkerExpiryMs(
    reports: Collection<UserReport>,
    nowMs: Long = System.currentTimeMillis()
): Long {
    var soonest = Long.MAX_VALUE
    for (r in reports) {
        if (r.status != ReportStatusUi.RESOLVED && r.status != ReportStatusUi.REJECTED) continue
        val updated = r.statusUpdatedAt?.time ?: continue
        val expiresAt = updated + TERMINAL_MAP_MARKER_TTL_MS
        if (expiresAt > nowMs && expiresAt < soonest) {
            soonest = expiresAt
        }
    }
    return soonest
}

private fun bitmapDescriptorFromVector(
    context: Context,
    @DrawableRes resId: Int,
    sizeDp: Float,
    @ColorInt tintColor: Int? = null,
): BitmapDescriptor {
    val original = ContextCompat.getDrawable(context, resId)!!
    val drawable = if (tintColor != null) {
        val wrapped = DrawableCompat.wrap(original.mutate())
        DrawableCompat.setTint(wrapped, tintColor)
        DrawableCompat.setTintMode(wrapped, PorterDuff.Mode.SRC_IN)
        wrapped
    } else {
        original
    }
    val px = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    drawable.setBounds(0, 0, px, px)
    val bitmap = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    drawable.draw(canvas)
    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

fun defaultMapCenter(): LatLng = REGION_CENTER

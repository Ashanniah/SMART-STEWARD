package com.example.smart_steward

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
    LOGGING
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
    MapAgencyPin("bfp_cebu", "BFP Cebu City", "BFP", LatLng(10.2926, 123.9022))
)

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
        "dump", "burn", "log", "poach", "illegal", "waste", "fire", "tree", "forest"
    ).any { t.contains(it) }
}

fun matchesTypeFilter(report: UserReport, filter: DashboardTypeFilter): Boolean {
    if (filter == DashboardTypeFilter.ALL) return true
    val t = report.incidentType.lowercase()
    return when (filter) {
        DashboardTypeFilter.DUMPING -> t.contains("dump") || t.contains("waste") || t.contains("trash")
        DashboardTypeFilter.BURNING -> t.contains("burn") || t.contains("fire") || t.contains("smoke")
        DashboardTypeFilter.LOGGING -> t.contains("log") || t.contains("tree") || t.contains("forest")
        DashboardTypeFilter.ALL -> true
    }
}

fun markerHueForReport(report: UserReport): Float {
    val t = report.incidentType.lowercase()
    return when {
        t.contains("dump") || t.contains("waste") -> BitmapDescriptorFactory.HUE_RED
        t.contains("burn") || t.contains("fire") -> BitmapDescriptorFactory.HUE_ORANGE
        t.contains("log") || t.contains("tree") || t.contains("forest") -> BitmapDescriptorFactory.HUE_GREEN
        t.contains("poach") || t.contains("wildlife") -> BitmapDescriptorFactory.HUE_VIOLET
        else -> BitmapDescriptorFactory.HUE_AZURE
    }
}

fun defaultMapCenter(): LatLng = REGION_CENTER

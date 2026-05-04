package com.example.smart_steward

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date

enum class ReportStatusUi {
    PENDING,
    IN_PROGRESS,
    RESOLVED
}

data class UserReport(
    val id: String,
    val incidentType: String,
    val locationLine: String,
    val submittedAt: Date?,
    val status: ReportStatusUi,
    val assignedAgency: String,
    val description: String,
    val photoUrl: String,
    val latitude: Double? = null,
    val longitude: Double? = null
) {
    val progressPercent: Int
        get() = when (status) {
            ReportStatusUi.PENDING -> 25
            ReportStatusUi.IN_PROGRESS -> 55
            ReportStatusUi.RESOLVED -> 100
        }

    val progressLabel: String
        get() = when (status) {
            ReportStatusUi.PENDING -> "Awaiting agency review"
            ReportStatusUi.IN_PROGRESS -> "Under investigation"
            ReportStatusUi.RESOLVED -> "Case resolved"
        }

    companion object {
        fun fromSnapshot(doc: DocumentSnapshot): UserReport? {
            val id = doc.id
            val incidentType = doc.getString("incidentType") ?: return null
            val locationLine = doc.getString("locationLine") ?: ""
            val submittedAt = when (val ts = doc.get("submittedAt")) {
                is Timestamp -> ts.toDate()
                is Date -> ts
                else -> null
            }
            val rawStatus = doc.getString("status")?.lowercase()?.trim().orEmpty()
            val status = when {
                rawStatus in setOf("resolved", "closed", "complete") -> ReportStatusUi.RESOLVED
                rawStatus in setOf("in_progress", "in progress", "investigating", "progress") ->
                    ReportStatusUi.IN_PROGRESS
                else -> ReportStatusUi.PENDING
            }
            val latRaw = (doc.get("latitude") as? Number)?.toDouble()
            val lngRaw = (doc.get("longitude") as? Number)?.toDouble()
            val latitude: Double?
            val longitude: Double?
            if (latRaw != null && lngRaw != null &&
                latRaw in -90.0..90.0 && lngRaw in -180.0..180.0
            ) {
                latitude = latRaw
                longitude = lngRaw
            } else {
                latitude = null
                longitude = null
            }
            return UserReport(
                id = id,
                incidentType = incidentType,
                locationLine = locationLine,
                submittedAt = submittedAt,
                status = status,
                assignedAgency = doc.getString("assignedAgency").orEmpty(),
                description = doc.getString("description").orEmpty(),
                photoUrl = doc.getString("photoUrl").orEmpty(),
                latitude = latitude,
                longitude = longitude
            )
        }
    }
}

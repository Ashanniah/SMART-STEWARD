package com.example.smart_steward

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import java.util.Date
import java.util.Locale

enum class ReportStatusUi {
    PENDING,
    IN_PROGRESS,
    RESOLVED,
    REJECTED
}

data class UserReport(
    val id: String,
    /** Stored public reference, e.g. REP-20260505-WY5. Derived when missing on older docs. */
    val publicReportId: String = "",
    val incidentType: String,
    val locationLine: String,
    val submittedAt: Date?,
    val status: ReportStatusUi,
    /** Lowercase normalized status string from Firestore (for timeline-style labels). */
    val statusRaw: String,
    /** Short label shown on badges, aligned with admin panel wording when possible. */
    val statusLabel: String,
    val assignedAgency: String,
    val description: String,
    val photoUrl: String,
    /** Non-empty when the report includes uploaded video (preview image is in [photoUrl] when available). */
    val videoUrl: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    /** Latest agency/admin note from a status update on the web panel (`lastStatusNote`). */
    val lastStatusNote: String = "",
    val statusUpdatedAt: Date? = null
) {
    val progressPercent: Int
        get() = when (status) {
            ReportStatusUi.PENDING -> 25
            ReportStatusUi.IN_PROGRESS -> 55
            ReportStatusUi.RESOLVED -> 100
            ReportStatusUi.REJECTED -> 0
        }

    val progressLabel: String
        get() = when (status) {
            ReportStatusUi.REJECTED -> "Report was rejected"
            ReportStatusUi.RESOLVED -> "Case resolved"
            ReportStatusUi.PENDING -> "Awaiting agency review"
            ReportStatusUi.IN_PROGRESS -> when {
                statusRaw.contains("under") && statusRaw.contains("review") ->
                    "Under review by agency"
                else -> "Under investigation"
            }
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
            val rawOriginal = doc.getString("status")?.trim().orEmpty()
            val normalized = rawOriginal.lowercase(Locale.US).replace("-", "_")

            val status = when {
                normalized.isEmpty() -> ReportStatusUi.PENDING
                normalized in setOf("resolved", "complete", "completed", "closed") ->
                    ReportStatusUi.RESOLVED
                normalized in setOf(
                    "rejected",
                    "reject",
                    "dismissed",
                    "invalid",
                    "declined"
                ) -> ReportStatusUi.REJECTED
                normalized in setOf(
                    "in_progress",
                    "in progress",
                    "investigating",
                    "progress",
                    "under_review",
                    "under review",
                    "reviewing",
                    "review",
                    "active"
                ) -> ReportStatusUi.IN_PROGRESS
                else -> ReportStatusUi.PENDING
            }

            val statusLabel = formatStatusLabel(rawOriginal, status)
            val statusRaw = normalized

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
            val publicReportId = doc.getString("publicReportId")?.trim().orEmpty()
            val lastStatusNote = sequenceOf(
                doc.getString("lastStatusNote"),
                doc.getString("statusNote"),
                doc.getString("adminRemarks"),
                doc.getString("remarks")
            ).mapNotNull { it?.trim()?.takeIf { s -> s.isNotEmpty() } }.firstOrNull().orEmpty()
            val statusUpdatedAt = when (val ts = doc.get("statusUpdatedAt")) {
                is Timestamp -> ts.toDate()
                is Date -> ts
                else -> null
            }

            return UserReport(
                id = id,
                publicReportId = publicReportId,
                incidentType = incidentType,
                locationLine = locationLine,
                submittedAt = submittedAt,
                status = status,
                statusRaw = statusRaw,
                statusLabel = statusLabel,
                assignedAgency = doc.getString("assignedAgency").orEmpty(),
                description = doc.getString("description").orEmpty(),
                photoUrl = doc.getString("photoUrl").orEmpty(),
                videoUrl = doc.getString("videoUrl").orEmpty(),
                latitude = latitude,
                longitude = longitude,
                lastStatusNote = lastStatusNote,
                statusUpdatedAt = statusUpdatedAt
            )
        }

        private fun formatStatusLabel(raw: String, bucket: ReportStatusUi): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) {
                return defaultStatusLabel(bucket)
            }
            return trimmed
                .replace("_", " ")
                .split(Regex("\\s+"))
                .filter { it.isNotBlank() }
                .joinToString(" ") { word ->
                    word.lowercase(Locale.getDefault()).replaceFirstChar { c ->
                        if (c.isLowerCase()) c.titlecase(Locale.getDefault()) else c.toString()
                    }
                }
        }

        private fun defaultStatusLabel(bucket: ReportStatusUi): String =
            when (bucket) {
                ReportStatusUi.PENDING -> "Pending"
                ReportStatusUi.IN_PROGRESS -> "In progress"
                ReportStatusUi.RESOLVED -> "Resolved"
                ReportStatusUi.REJECTED -> "Rejected"
            }
    }
}

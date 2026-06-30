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

data class StatusRemark(
    val agency: String,
    val note: String,
    val createdAt: Date? = null,
)

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
    /** Canonical agency key (e.g. PNP) that wrote [lastStatusNote] (`lastStatusNoteAgency`). */
    val lastStatusNoteAgency: String = "",
    /** Full remark history appended by agency admins (`statusRemarks`). */
    val statusRemarks: List<StatusRemark> = emptyList(),
    val statusUpdatedAt: Date? = null,
    /** Set by web admin status updates so mobile does not duplicate inbox notifications. */
    val lastCitizenNotifyFingerprint: String = "",
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

    /** All agency remarks, oldest first — merges Firestore array with legacy single-note fields. */
    fun resolvedStatusRemarks(): List<StatusRemark> {
        val merged = statusRemarks.toMutableList()
        val legacyNote = lastStatusNote.trim()
        if (legacyNote.isNotEmpty()) {
            val alreadyPresent = merged.any { it.note.trim() == legacyNote }
            if (!alreadyPresent) {
                merged.add(
                    StatusRemark(
                        agency = lastStatusNoteAgency,
                        note = legacyNote,
                        createdAt = statusUpdatedAt,
                    )
                )
            }
        }
        return merged.sortedBy { it.createdAt?.time ?: Long.MAX_VALUE }
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
            val lastStatusNoteAgency = doc.getString("lastStatusNoteAgency")?.trim().orEmpty()
            val statusRemarks = parseStatusRemarks(doc)
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
                photoUrl = resolvePhotoUrl(doc),
                videoUrl = resolveVideoUrl(doc),
                latitude = latitude,
                longitude = longitude,
                lastStatusNote = lastStatusNote,
                lastStatusNoteAgency = lastStatusNoteAgency,
                statusRemarks = statusRemarks,
                lastCitizenNotifyFingerprint = doc.getString("lastCitizenNotifyFingerprint").orEmpty(),
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

        /** Matches web [normalizeReportDoc] so list thumbnails work on older report shapes. */
        private fun resolvePhotoUrl(doc: DocumentSnapshot): String =
            sequenceOf(
                doc.getString("photoUrl"),
                doc.getString("imageUrl"),
                doc.getString("mediaUrl"),
                doc.getString("image"),
            )
                .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
                .firstOrNull()
                .orEmpty()

        private fun resolveVideoUrl(doc: DocumentSnapshot): String =
            sequenceOf(
                doc.getString("videoUrl"),
                doc.getString("mediaVideoUrl"),
            )
                .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
                .firstOrNull()
                .orEmpty()

        private fun defaultStatusLabel(bucket: ReportStatusUi): String =
            when (bucket) {
                ReportStatusUi.PENDING -> "Pending"
                ReportStatusUi.IN_PROGRESS -> "In progress"
                ReportStatusUi.RESOLVED -> "Resolved"
                ReportStatusUi.REJECTED -> "Rejected"
            }

        @Suppress("UNCHECKED_CAST")
        private fun parseStatusRemarks(doc: DocumentSnapshot): List<StatusRemark> {
            val raw = doc.get("statusRemarks") as? List<Map<String, Any?>> ?: return emptyList()
            return raw.mapNotNull { map ->
                val note = (map["note"] as? String)?.trim().orEmpty()
                if (note.isEmpty()) return@mapNotNull null
                val agency = (map["agency"] as? String)?.trim().orEmpty()
                val createdAt = when (val ts = map["createdAt"]) {
                    is Timestamp -> ts.toDate()
                    is Date -> ts
                    else -> null
                }
                StatusRemark(agency = agency, note = note, createdAt = createdAt)
            }
        }
    }
}

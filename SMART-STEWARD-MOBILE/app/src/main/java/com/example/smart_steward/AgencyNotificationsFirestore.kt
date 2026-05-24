package com.example.smart_steward

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

/**
 * Queues a notification for the web agency dashboard (collection `agencyNotifications`).
 * [targetAgency] must match the signed-in agency on the web (DENR, PNP, BFP, Barangay).
 */
object AgencyNotificationsFirestore {

    private const val COLLECTION = "agencyNotifications"

    /**
     * Standard inbox row when a citizen submits a report assigned to one or more agencies.
     */
    fun notifyNewReportForAgency(
        docId: String,
        incidentType: String,
        locationLine: String,
        assignedAgency: String
    ) {
        val targets = AgencyCanonical.parseAssignedAgencies(assignedAgency)
            .ifEmpty { listOf(AgencyCanonical.targetKey(assignedAgency)) }
        val title = "New report received: $incidentType"
        val body = locationLine.ifBlank { "Location not specified" }
        val db = FirebaseFirestore.getInstance()
        for (target in targets) {
            val data = hashMapOf<String, Any>(
                "targetAgency" to target,
                "reportDocId" to docId,
                "title" to title,
                "body" to body,
                "kind" to "new_report",
                "severity" to "info",
                "pinned" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection(COLLECTION).add(data)
        }
    }

    fun sendCitizenNotify(
        report: UserReport,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val targets = AgencyCanonical.parseAssignedAgencies(report.assignedAgency)
            .ifEmpty { listOf(AgencyCanonical.targetKey(report.assignedAgency)) }
        val title = "Citizen alert: ${report.displayTitle()}"
        val body = "${report.locationDisplay()} · ${report.incidentType}"
        val db = FirebaseFirestore.getInstance()
        var pending = targets.size
        var failed = false
        if (pending == 0) {
            onError("No agency assigned to this report.")
            return
        }
        for (target in targets) {
            val data = hashMapOf<String, Any>(
                "targetAgency" to target,
                "reportDocId" to report.id,
                "title" to title,
                "body" to body,
                "kind" to "citizen_notify",
                "severity" to "warning",
                "pinned" to false,
                "incidentType" to report.incidentType,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection(COLLECTION)
                .add(data)
                .addOnSuccessListener {
                    pending -= 1
                    if (pending == 0 && !failed) onSuccess()
                }
                .addOnFailureListener { e ->
                    if (!failed) {
                        failed = true
                        onError(e.message ?: "Could not send notification")
                    }
                }
        }
    }
}

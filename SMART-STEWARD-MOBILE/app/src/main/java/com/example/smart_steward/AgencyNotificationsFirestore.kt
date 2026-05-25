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
        val title = "New citizen report"
        val body = ""
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

    /**
     * Sends a "citizen is requesting an update" notification to EVERY
     * canonical agency assigned to the report.
     *
     * If the report's `assignedAgency` field lists multiple agencies
     * (e.g. `"DENR, PNP"` for Illegal Logging) one Firestore document is
     * written per agency, so each agency's admin dashboard surfaces the
     * nudge independently — per-agency credential isolation is preserved
     * because each document is still tagged with its own `targetAgency`.
     *
     * @param onSuccess invoked after every per-agency document has been
     *   acknowledged by the server; receives the canonical agency keys
     *   that were notified (e.g. `["DENR", "PNP"]`).
     */
    fun sendCitizenNotify(
        report: UserReport,
        onSuccess: (notifiedAgencies: List<String>) -> Unit,
        onError: (String) -> Unit
    ) {
        val targets = AgencyCanonical.parseAssignedAgencies(report.assignedAgency)
        if (targets.isEmpty()) {
            onError("No agency assigned to this report.")
            return
        }
        val title = "A citizen is requesting an update on this report."
        val db = FirebaseFirestore.getInstance()
        var pending = targets.size
        var failed = false
        for (target in targets) {
            val data = hashMapOf<String, Any>(
                "targetAgency" to target,
                "reportDocId" to report.id,
                "title" to title,
                "kind" to "citizen_notify",
                "severity" to "info",
                "pinned" to false,
                "incidentType" to report.incidentType,
                "createdAt" to FieldValue.serverTimestamp()
            )
            db.collection(COLLECTION)
                .add(data)
                .addOnSuccessListener {
                    pending -= 1
                    if (pending == 0 && !failed) onSuccess(targets)
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

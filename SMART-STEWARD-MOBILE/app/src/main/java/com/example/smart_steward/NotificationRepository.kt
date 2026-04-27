package com.example.smart_steward

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

object NotificationRepository {
    data class AppNotification(
        val id: String,
        val type: String,
        val title: String,
        val message: String,
        val reportId: String,
        val status: String,
        val agency: String,
        val location: String,
        val actionLabel: String,
        val createdAt: Timestamp?
    )

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    fun listen(
        onUpdate: (List<AppNotification>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        val currentUserId = auth.currentUser?.uid.orEmpty()

        return firestore.collection("notifications")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    onError(error.message ?: "Unable to load notifications.")
                    return@addSnapshotListener
                }

                val notifications = snapshot?.documents
                    ?.mapNotNull { document ->
                        val userId = document.getString("userId").orEmpty()
                        val audience = document.getString("audience").orEmpty()

                        if (userId != currentUserId && audience != "all") {
                            return@mapNotNull null
                        }

                        AppNotification(
                            id = document.id,
                            type = document.getString("type").orEmpty(),
                            title = document.getString("title").orEmpty(),
                            message = document.getString("message").orEmpty(),
                            reportId = document.getString("reportId").orEmpty(),
                            status = document.getString("status").orEmpty(),
                            agency = document.getString("agency").orEmpty(),
                            location = document.getString("location").orEmpty(),
                            actionLabel = document.getString("actionLabel").orEmpty(),
                            createdAt = document.getTimestamp("createdAt")
                        )
                    }
                    .orEmpty()

                onUpdate(notifications)
            }
    }

    fun seedDemoNotificationsIfNeeded() {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("notifications")
            .whereEqualTo("userId", currentUserId)
            .limit(1)
            .get()
            .addOnSuccessListener { snapshot ->
                if (!snapshot.isEmpty) return@addOnSuccessListener

                listOf(
                    mapOf(
                        "type" to "status_update",
                        "title" to "Report Status Update",
                        "message" to "Your report was submitted successfully and is now under review.",
                        "reportId" to "REP-20251024-001",
                        "status" to "Under review",
                        "agency" to "Barangay",
                        "location" to "Bgy Lahug, Cebu City",
                        "actionLabel" to "View report"
                    ),
                    mapOf(
                        "type" to "agency_acknowledgement",
                        "title" to "DENR Acknowledged",
                        "message" to "DENR acknowledged your report. The assigned agency has received the incident details.",
                        "reportId" to "REP-20251024-001",
                        "status" to "Acknowledged",
                        "agency" to "DENR",
                        "location" to "Bgy Lahug, Cebu City",
                        "actionLabel" to "View acknowledgement"
                    ),
                    mapOf(
                        "type" to "status_update",
                        "title" to "Report Resolved",
                        "message" to "Barangay marked your report as resolved.",
                        "reportId" to "REP-20251024-001",
                        "status" to "Resolved",
                        "agency" to "Barangay",
                        "location" to "Bgy Lahug, Cebu City",
                        "actionLabel" to "View resolution"
                    ),
                    mapOf(
                        "type" to "advisory",
                        "title" to "Public Advisory",
                        "message" to "New advisory from PNP: Illegal dumping operations are ongoing in your area.",
                        "reportId" to "",
                        "status" to "Advisory",
                        "agency" to "PNP",
                        "location" to "Nearby area",
                        "actionLabel" to "View advisory"
                    ),
                    mapOf(
                        "type" to "action_request",
                        "title" to "More Evidence Needed",
                        "message" to "Please upload clearer evidence for your report.",
                        "reportId" to "REP-20251024-001",
                        "status" to "Action needed",
                        "agency" to "Barangay",
                        "location" to "Bgy Lahug, Cebu City",
                        "actionLabel" to "Update report"
                    )
                ).forEach { notification ->
                    firestore.collection("notifications").add(
                        notification + mapOf(
                            "userId" to currentUserId,
                            "read" to false,
                            "createdAt" to Timestamp.now()
                        )
                    )
                }
            }
    }

    fun createReportSubmittedNotification(reportType: String, agency: String) {
        val currentUserId = auth.currentUser?.uid ?: return
        firestore.collection("notifications").add(
            mapOf(
                "userId" to currentUserId,
                "type" to "status_update",
                "title" to "Report Submitted",
                "message" to "Your $reportType report was submitted successfully.",
                "reportId" to "REP-20251024-001",
                "status" to "Submitted successfully",
                "agency" to agency,
                "location" to "Bgy Lahug, Cebu City",
                "actionLabel" to "View report",
                "read" to false,
                "createdAt" to FieldValue.serverTimestamp()
            )
        )
    }
}

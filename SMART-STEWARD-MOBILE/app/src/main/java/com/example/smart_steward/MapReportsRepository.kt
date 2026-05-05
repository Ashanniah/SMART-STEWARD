package com.example.smart_steward

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query

object MapReportsRepository {
    private const val DEFAULT_LIMIT = 120L
    private val firestore = FirebaseFirestore.getInstance()

    fun watchRecentReports(
        limit: Long = DEFAULT_LIMIT,
        onUpdate: (List<UserReport>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return firestore.collection("reports")
            .orderBy("submittedAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onError(e.message ?: "Failed to load reports.")
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { UserReport.fromSnapshot(it) }
                    ?.sortedByDescending { it.submittedAt?.time ?: 0L }
                    .orEmpty()
                onUpdate(list)
            }
    }
}

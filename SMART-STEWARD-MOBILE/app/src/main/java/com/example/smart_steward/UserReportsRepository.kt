package com.example.smart_steward

import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration

object UserReportsRepository {
    private val firestore = FirebaseFirestore.getInstance()

    /**
     * One-time load (e.g. tests or callers that do not need live updates).
     */
    fun loadReportsForUser(
        userId: String,
        onResult: (List<UserReport>) -> Unit,
        onError: (String) -> Unit
    ) {
        if (userId.isBlank()) {
            onResult(emptyList())
            return
        }
        firestore.collection("reports")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { snap ->
                val list = snap.documents.mapNotNull { UserReport.fromSnapshot(it) }
                    .sortedByDescending { it.submittedAt?.time ?: 0L }
                onResult(list)
            }
            .addOnFailureListener { e ->
                onError(e.message ?: "Failed to load reports.")
            }
    }

    /**
     * Live updates when admins change report status (or any field) in Firestore.
     */
    fun watchReportsForUser(
        userId: String,
        onUpdate: (List<UserReport>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return firestore.collection("reports")
            .whereEqualTo("userId", userId)
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

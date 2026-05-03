package com.example.smart_steward

import com.google.firebase.firestore.FirebaseFirestore

object UserReportsRepository {
    private val firestore = FirebaseFirestore.getInstance()

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
}

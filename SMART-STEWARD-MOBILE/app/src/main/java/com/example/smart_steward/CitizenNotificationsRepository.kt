package com.example.smart_steward

import com.google.firebase.FirebaseApp
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import java.util.Date

/**
 * Citizen notification inbox at `users/{uid}/citizenInbox/{docId}`.
 * Firestore rules should allow read/update/create when `request.auth.uid == uid`.
 */
data class CitizenInboxItem(
    val id: String,
    val kindKey: String?,
    val categoryLine: String,
    val title: String,
    val body: String,
    val read: Boolean,
    val createdAt: Date?,
    val reportId: String
)

object CitizenNotificationsRepository {
    private const val LIMIT = 100
    private val firestore = FirebaseFirestore.getInstance()

    private fun appContext() = FirebaseApp.getInstance().applicationContext

    private fun inboxCollection(uid: String) =
        firestore.collection("users").document(uid).collection("citizenInbox")

    fun append(
        userId: String?,
        kind: CitizenNotificationKind,
        agency: String,
        reportId: String?
    ) {
        if (userId.isNullOrBlank()) return
        val ctx = appContext()
        val ag = agency.ifBlank { ctx.getString(R.string.notif_agency_placeholder) }
        val categoryLine = "${ctx.getString(kind.categoryRes())} • $ag"
        val title = ctx.getString(kind.titleRes())
        val body = when (kind) {
            CitizenNotificationKind.LIFECYCLE_RECEIVED,
            CitizenNotificationKind.ADMIN_AGENCY_MESSAGE -> ctx.getString(kind.bodyRes(), ag)
            else -> ctx.getString(kind.bodyRes())
        }
        val data = hashMapOf<String, Any>(
            "kind" to kind.key,
            "categoryLine" to categoryLine,
            "title" to title,
            "body" to body,
            "agency" to ag,
            "reportId" to (reportId ?: ""),
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        inboxCollection(userId).add(data)
    }

    fun watchInbox(
        userId: String,
        onUpdate: (List<CitizenInboxItem>) -> Unit,
        onError: (String) -> Unit
    ): ListenerRegistration {
        return inboxCollection(userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(LIMIT.toLong())
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onError(e.message ?: "Failed to load notifications.")
                    return@addSnapshotListener
                }
                val list = snap?.documents?.mapNotNull { doc ->
                    val createdAt = when (val ts = doc.get("createdAt")) {
                        is Timestamp -> ts.toDate()
                        is Date -> ts
                        else -> null
                    }
                    CitizenInboxItem(
                        id = doc.id,
                        kindKey = doc.getString("kind"),
                        categoryLine = doc.getString("categoryLine").orEmpty(),
                        title = doc.getString("title").orEmpty(),
                        body = doc.getString("body").orEmpty(),
                        read = doc.getBoolean("read") == true,
                        createdAt = createdAt,
                        reportId = doc.getString("reportId").orEmpty()
                    )
                }.orEmpty()
                onUpdate(list)
            }
    }

    fun markRead(userId: String, docId: String) {
        if (userId.isBlank() || docId.isBlank()) return
        inboxCollection(userId).document(docId).update("read", true)
    }

    fun markAllRead(userId: String, onDone: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        markAllReadState(userId, read = true, onDone, onError)
    }

    fun markAllUnread(userId: String, onDone: (() -> Unit)? = null, onError: ((String) -> Unit)? = null) {
        markAllReadState(userId, read = false, onDone, onError)
    }

    private fun markAllReadState(
        userId: String,
        read: Boolean,
        onDone: (() -> Unit)?,
        onError: ((String) -> Unit)?
    ) {
        if (userId.isBlank()) return
        inboxCollection(userId)
            .whereEqualTo("read", !read)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    onDone?.invoke()
                    return@addOnSuccessListener
                }
                val batch = firestore.batch()
                var count = 0
                for (doc in snap.documents) {
                    batch.update(doc.reference, "read", read)
                    count++
                    if (count >= 450) break
                }
                batch.commit()
                    .addOnSuccessListener { onDone?.invoke() }
                    .addOnFailureListener { ex ->
                        val action = if (read) "read" else "unread"
                        onError?.invoke(ex.message ?: "Could not mark all $action.")
                    }
            }
            .addOnFailureListener { ex ->
                val action = if (read) "read" else "unread"
                onError?.invoke(ex.message ?: "Could not mark all $action.")
            }
    }

    fun countUnread(userId: String, onResult: (Int) -> Unit, onError: ((String) -> Unit)? = null) {
        if (userId.isBlank()) {
            onResult(0)
            return
        }
        inboxCollection(userId)
            .whereEqualTo("read", false)
            .get()
            .addOnSuccessListener { onResult(it.size()) }
            .addOnFailureListener { ex ->
                onError?.invoke(ex.message ?: "")
                onResult(0)
            }
    }
}

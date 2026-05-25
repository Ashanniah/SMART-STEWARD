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

    /**
     * Persist a single notification card for the citizen.
     *
     * Bodies for the report-lifecycle kinds (and for ADMIN_COMMENT / USER_NOTIFIED_AGENCY)
     * accept three positional placeholders so each card surfaces the report it refers to:
     *   - `%1$s` → incident type (e.g. "Illegal Gambling")
     *   - `%2$s` → agency short name (e.g. "DENR, PNP")
     *   - `%3$s` → public-facing report reference (e.g. "REP-20260525-AB12")
     *
     * @param customBody overrides the localized body when provided (used for free-form
     *                   admin remarks captured on the web dashboard).
     */
    fun append(
        userId: String?,
        kind: CitizenNotificationKind,
        agency: String,
        reportId: String?,
        incidentType: String? = null,
        publicReportId: String? = null,
        customBody: String? = null
    ) {
        if (userId.isNullOrBlank()) return
        val ctx = appContext()
        val ag = agency.ifBlank { ctx.getString(R.string.notif_agency_placeholder) }
        val incident = incidentType?.trim().orEmpty()
            .ifBlank { ctx.getString(R.string.notif_report_kind_fallback) }
        val ref = publicReportId?.trim().orEmpty()
            .ifBlank { ctx.getString(R.string.notif_report_ref_fallback) }
        val categoryLine = "${ctx.getString(kind.categoryRes())} • $ag"
        val title = ctx.getString(kind.titleRes())
        val body = customBody?.takeIf { it.isNotBlank() } ?: run {
            when (kind) {
                CitizenNotificationKind.LIFECYCLE_SUBMITTED -> ctx.getString(kind.bodyRes(), incident, ref)
                CitizenNotificationKind.LIFECYCLE_RECEIVED,
                CitizenNotificationKind.LIFECYCLE_UNDER_REVIEW,
                CitizenNotificationKind.LIFECYCLE_IN_PROGRESS,
                CitizenNotificationKind.LIFECYCLE_PENDING,
                CitizenNotificationKind.LIFECYCLE_RESOLVED,
                CitizenNotificationKind.LIFECYCLE_REJECTED,
                CitizenNotificationKind.ADMIN_COMMENT,
                CitizenNotificationKind.USER_NOTIFIED_AGENCY -> ctx.getString(kind.bodyRes(), incident, ag, ref)
                CitizenNotificationKind.ADMIN_AGENCY_MESSAGE -> ctx.getString(kind.bodyRes(), ag)
                else -> ctx.getString(kind.bodyRes())
            }
        }
        val data = hashMapOf<String, Any>(
            "kind" to kind.key,
            "categoryLine" to categoryLine,
            "title" to title,
            "body" to body.take(2000),
            "agency" to ag,
            "incidentType" to incident,
            "publicReportId" to ref,
            "reportId" to (reportId ?: ""),
            "read" to false,
            "createdAt" to FieldValue.serverTimestamp()
        )
        inboxCollection(userId).add(data)

        // Also surface the same content in the system tray so the user is
        // alerted even when they're not currently on the Notifications tab.
        // Notifications are keyed by report id so successive updates for the
        // same report replace each other instead of stacking. ADMIN_COMMENT
        // gets its own tag so an admin remark doesn't overwrite the most
        // recent lifecycle update for that report.
        val safeReportId = reportId?.takeIf { it.isNotBlank() } ?: return
        val tagSuffix = if (kind == CitizenNotificationKind.ADMIN_COMMENT) "remark" else "lifecycle"
        LocalNotificationCenter.postReportNotification(
            context = ctx,
            title = title,
            body = body,
            reportId = safeReportId,
            notificationTag = "report_${safeReportId}_$tagSuffix"
        )
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

    /**
     * Permanently deletes every notification document under
     * `users/{uid}/citizenInbox`. The deletion is split into Firestore-batch-sized
     * chunks (500 docs each) so very large inboxes still complete reliably.
     */
    fun clearAll(
        userId: String,
        onDone: (() -> Unit)? = null,
        onError: ((String) -> Unit)? = null
    ) {
        if (userId.isBlank()) {
            onDone?.invoke()
            return
        }
        inboxCollection(userId)
            .get()
            .addOnSuccessListener { snap ->
                if (snap.isEmpty) {
                    onDone?.invoke()
                    return@addOnSuccessListener
                }
                val docs = snap.documents
                val chunks = docs.chunked(450)
                var remaining = chunks.size
                var failed = false
                for (chunk in chunks) {
                    val batch = firestore.batch()
                    for (doc in chunk) {
                        batch.delete(doc.reference)
                    }
                    batch.commit()
                        .addOnSuccessListener {
                            remaining -= 1
                            if (remaining == 0 && !failed) {
                                onDone?.invoke()
                            }
                        }
                        .addOnFailureListener { ex ->
                            if (!failed) {
                                failed = true
                                onError?.invoke(ex.message ?: "Could not clear notifications.")
                            }
                        }
                }
            }
            .addOnFailureListener { ex ->
                onError?.invoke(ex.message ?: "Could not clear notifications.")
            }
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

    /**
     * Live unread-count subscription that fires every time a notification is added,
     * marked read, or marked unread. The returned [ListenerRegistration] must be
     * [ListenerRegistration.remove]d when the observer is no longer interested.
     */
    fun watchUnreadCount(
        userId: String,
        onResult: (Int) -> Unit,
        onError: ((String) -> Unit)? = null
    ): ListenerRegistration? {
        if (userId.isBlank()) {
            onResult(0)
            return null
        }
        return inboxCollection(userId)
            .whereEqualTo("read", false)
            .addSnapshotListener { snap, e ->
                if (e != null) {
                    onError?.invoke(e.message ?: "")
                    return@addSnapshotListener
                }
                onResult(snap?.size() ?: 0)
            }
    }
}

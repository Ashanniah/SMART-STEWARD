package com.example.smart_steward

import android.content.Context

/**
 * Emits citizen inbox notifications for changes admins make on the web panel.
 *
 * Two transitions are tracked per report via [Context.getSharedPreferences]
 * to avoid duplicate emits across snapshot refreshes:
 *
 *   1. **Status fingerprint** ([KEY_PREFIX]) — covers Pending → In review → In
 *      progress → Resolved / Rejected lifecycle transitions and surfaces a
 *      lifecycle notification (`LIFECYCLE_RESOLVED`, etc.).
 *
 *   2. **Status note** ([NOTE_KEY_PREFIX]) — covers the free-form remark the
 *      admin types when updating the status. Whenever `lastStatusNote` changes
 *      to a non-empty value we emit an `ADMIN_COMMENT` notification carrying
 *      the actual remark as the body. Clearing the note (e.g. an admin
 *      removing a remark) silently re-baselines without notifying.
 *
 * Both are written from the citizen's own signed-in app, so Firestore rules
 * that scope every document under `users/{uid}/citizenInbox` to the inbox
 * owner are respected.
 * The web admin intentionally does NOT write to this collection — see the
 * payload comment in `ReportStatusUpdate.jsx`.
 */
object ReportStatusNotificationSync {
    private const val PREFS = "smart_steward_report_notif"
    private const val KEY_PREFIX = "fp_"
    private const val NOTE_KEY_PREFIX = "note_"

    private fun fingerprint(r: UserReport): String = when {
        r.status == ReportStatusUi.RESOLVED -> "RESOLVED"
        r.status == ReportStatusUi.REJECTED -> "REJECTED"
        r.status == ReportStatusUi.PENDING -> "PENDING"
        r.status == ReportStatusUi.IN_PROGRESS &&
            r.statusRaw.contains("review") -> "IN_REVIEW"

        r.status == ReportStatusUi.IN_PROGRESS -> "IN_WORK"
        else -> "PENDING"
    }

    fun sync(context: Context, userId: String?, reports: List<UserReport>) {
        if (userId.isNullOrBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = prefs.edit()
        for (r in reports) {
            syncLifecycle(prefs, editor, userId, r)
            syncAdminRemarks(prefs, editor, userId, r)
        }
        editor.apply()
    }

    private fun syncLifecycle(
        prefs: android.content.SharedPreferences,
        editor: android.content.SharedPreferences.Editor,
        userId: String,
        r: UserReport,
    ) {
        val key = KEY_PREFIX + r.id
        val next = fingerprint(r)
        val prev = prefs.getString(key, null)
        // First observation: silently record the baseline so we don't replay
        // historical transitions when the app is opened for the first time.
        if (prev == null) {
            editor.putString(key, next)
            return
        }
        if (prev == next) return

        val kind = transitionKind(prev, next)
        if (kind != null) {
            CitizenNotificationsRepository.append(
                userId = userId,
                kind = kind,
                agency = r.assignedAgency,
                reportId = r.id,
                incidentType = r.incidentType,
                publicReportId = r.publicReportId,
            )
        }
        editor.putString(key, next)
    }

    private fun syncAdminRemarks(
        prefs: android.content.SharedPreferences,
        editor: android.content.SharedPreferences.Editor,
        userId: String,
        r: UserReport,
    ) {
        val key = NOTE_KEY_PREFIX + r.id
        val nextNote = r.lastStatusNote.trim()
        val prevNote = prefs.getString(key, null)
        // First observation: silently baseline so existing remarks don't
        // trigger an "agency added remarks" alert on first launch.
        if (prevNote == null) {
            editor.putString(key, nextNote)
            return
        }
        if (nextNote == prevNote) return
        if (nextNote.isNotEmpty()) {
            CitizenNotificationsRepository.append(
                userId = userId,
                kind = CitizenNotificationKind.ADMIN_COMMENT,
                agency = r.assignedAgency,
                reportId = r.id,
                incidentType = r.incidentType,
                publicReportId = r.publicReportId,
                customBody = nextNote,
            )
        }
        editor.putString(key, nextNote)
    }

    private fun transitionKind(prev: String, next: String): CitizenNotificationKind? {
        if (next == "REJECTED") return CitizenNotificationKind.LIFECYCLE_REJECTED
        if (next == "RESOLVED") return CitizenNotificationKind.LIFECYCLE_RESOLVED
        // Admin moved the report back to (or rebaselined it to) Pending —
        // covers every prev → PENDING transition except the no-op
        // PENDING → PENDING (already short-circuited by the equality
        // check in syncLifecycle before we ever get here).
        if (next == "PENDING") return CitizenNotificationKind.LIFECYCLE_PENDING
        return when {
            prev == "PENDING" && next == "IN_REVIEW" ->
                CitizenNotificationKind.LIFECYCLE_UNDER_REVIEW

            prev == "PENDING" && next == "IN_WORK" ->
                CitizenNotificationKind.LIFECYCLE_RECEIVED

            prev == "IN_REVIEW" && next == "IN_WORK" ->
                CitizenNotificationKind.LIFECYCLE_IN_PROGRESS

            prev == "IN_WORK" && next == "IN_REVIEW" ->
                CitizenNotificationKind.LIFECYCLE_UNDER_REVIEW

            else -> null
        }
    }
}

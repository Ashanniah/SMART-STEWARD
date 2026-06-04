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
 *   2. **Status remarks** ([REMARKS_KEY_PREFIX]) — tracks every entry in
 *      `statusRemarks` (and legacy `lastStatusNote`). Each new remark emits an
 *      `ADMIN_COMMENT` notification attributed to the sending agency.
 *
 * Both are written from the citizen's own signed-in app, so Firestore rules
 * that scope every document under `users/{uid}/citizenInbox` to the inbox
 * owner are respected.
 * The web admin intentionally does NOT write to this collection — see the
 * payload comment in `ReportStatusUpdate.jsx`.
 */
object ReportStatusNotificationSync {
    private fun appContext() =
        com.google.firebase.FirebaseApp.getInstance().applicationContext

    private const val PREFS = "smart_steward_report_notif"
    private const val KEY_PREFIX = "fp_"
    private const val REMARKS_KEY_PREFIX = "remarks_set_"
    private const val REMARK_FP_SEP = "\u001E"

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

    private fun remarkFingerprint(remark: StatusRemark): String {
        val agency = remark.agency.trim()
        val note = remark.note.trim()
        val ts = remark.createdAt?.time?.toString().orEmpty()
        return "$agency|$note|$ts"
    }

    private fun syncAdminRemarks(
        prefs: android.content.SharedPreferences,
        editor: android.content.SharedPreferences.Editor,
        userId: String,
        r: UserReport,
    ) {
        val key = REMARKS_KEY_PREFIX + r.id
        val remarks = r.resolvedStatusRemarks()
        val currentFingerprints = remarks.map { remarkFingerprint(it) }.toSet()
        val storedRaw = prefs.getString(key, null)

        if (storedRaw == null) {
            editor.putString(key, currentFingerprints.joinToString(REMARK_FP_SEP))
            return
        }

        val previousFingerprints = storedRaw
            .split(REMARK_FP_SEP)
            .filter { it.isNotEmpty() }
            .toSet()

        for (remark in remarks) {
            val fp = remarkFingerprint(remark)
            if (fp in previousFingerprints) continue
            val note = remark.note.trim()
            if (note.isEmpty()) continue

            val senderAgency = remark.agency.ifBlank { r.assignedAgency }
            val senderLabel = AgencyCanonical.shortName(senderAgency).ifBlank { senderAgency }
            val body = if (senderLabel.isNotBlank()) {
                appContext().getString(R.string.report_remarks_attributed_format, senderLabel, note)
            } else {
                note
            }
            CitizenNotificationsRepository.append(
                userId = userId,
                kind = CitizenNotificationKind.ADMIN_COMMENT,
                agency = senderAgency,
                reportId = r.id,
                incidentType = r.incidentType,
                publicReportId = r.publicReportId,
                customBody = body,
            )
        }

        editor.putString(key, currentFingerprints.joinToString(REMARK_FP_SEP))
    }

    private fun transitionKind(prev: String, next: String): CitizenNotificationKind? {
        if (next == "REJECTED") return CitizenNotificationKind.LIFECYCLE_REJECTED
        if (next == "RESOLVED") return CitizenNotificationKind.LIFECYCLE_RESOLVED
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

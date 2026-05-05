package com.example.smart_steward

import android.content.Context

/**
 * Emits lifecycle notifications when a report's status (or review sub-state) changes.
 * Uses [Context.getSharedPreferences] name [PREFS] to avoid duplicate emits per report.
 */
object ReportStatusNotificationSync {
    private const val PREFS = "smart_steward_report_notif"
    private const val KEY_PREFIX = "fp_"

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
            val key = KEY_PREFIX + r.id
            val next = fingerprint(r)
            val prev = prefs.getString(key, null)
            if (prev == null) {
                editor.putString(key, next)
                continue
            }
            if (prev == next) continue

            val kind = transitionKind(prev, next)
            if (kind != null) {
                CitizenNotificationsRepository.append(
                    userId,
                    kind,
                    r.assignedAgency,
                    r.id
                )
            }
            editor.putString(key, next)
        }
        editor.apply()
    }

    private fun transitionKind(prev: String, next: String): CitizenNotificationKind? {
        if (next == "REJECTED") return CitizenNotificationKind.LIFECYCLE_REJECTED
        if (next == "RESOLVED") return CitizenNotificationKind.LIFECYCLE_RESOLVED
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

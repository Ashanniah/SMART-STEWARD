package com.example.smart_steward

import android.content.Intent
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth

/**
 * Single entry point used by the in-app inbox row and by the system-tray
 * tap to land the citizen on the specific report a notification refers to.
 *
 * The router fetches the report once, then:
 *
 *  - Shows [ReportReceiptDialog] directly if the report exists (best UX —
 *    no extra navigation tier).
 *  - Falls back to a list activity scoped to the report's lifecycle bucket
 *    (Resolved/Rejected → [ReportHistoryActivity]; everything else →
 *    [MyActivityActivity]) when the receipt dialog can't be shown because
 *    e.g. the report was cached from a snapshot that no longer exists.
 */
object ReportRouter {

    /**
     * Resolve [reportId] and present the matching report to the user.
     *
     * The fetch runs against the currently signed-in user — if no one is
     * signed in or the report id is blank we no-op so the caller never has
     * to guard against null state.
     */
    fun openReport(activity: AppCompatActivity, reportId: String?) {
        val trimmed = reportId?.trim().orEmpty()
        if (trimmed.isEmpty()) return
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            Toast.makeText(
                activity,
                activity.getString(R.string.report_router_unavailable),
                Toast.LENGTH_SHORT
            ).show()
            return
        }

        UserReportsRepository.loadReportsForUser(
            uid,
            onResult = { reports ->
                if (activity.isFinishing || activity.isDestroyed) return@loadReportsForUser
                val report = reports.firstOrNull { it.id == trimmed }
                if (report != null) {
                    ReportReceiptDialog.show(activity, report)
                } else {
                    // Report id pointed at something we can't see anymore —
                    // most commonly because the citizen used the system
                    // notification after the report was removed. Route to
                    // the bucket where it would have lived as a fallback.
                    activity.startActivity(
                        Intent(activity, MyActivityActivity::class.java)
                            .putExtra(MyActivityActivity.EXTRA_FOCUS_REPORT_ID, trimmed)
                    )
                }
            },
            onError = { _ ->
                if (activity.isFinishing || activity.isDestroyed) return@loadReportsForUser
                Toast.makeText(
                    activity,
                    activity.getString(R.string.report_router_unavailable),
                    Toast.LENGTH_SHORT
                ).show()
            }
        )
    }
}

package com.example.smart_steward

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Posts in-tray system notifications that mirror the citizen inbox entries
 * written by [CitizenNotificationsRepository.append]. These are *local*
 * notifications (no FCM round-trip): they fire from inside the app the
 * moment [ReportStatusNotificationSync] detects an admin change to one of
 * the user's reports.
 *
 * Tapping a notification deep-links into [NotificationActivity] which then
 * hands the report id to [ReportRouter] to open the specific report
 * (receipt dialog) — see [NotificationActivity.EXTRA_OPEN_REPORT_ID].
 */
object LocalNotificationCenter {
    private const val CHANNEL_ID = "smart_steward_status_updates"
    private const val REQUEST_CODE_BASE = 6100

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        val existing = manager.getNotificationChannel(CHANNEL_ID)
        if (existing != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.notif_channel_status_updates_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.notif_channel_status_updates_desc)
            enableLights(true)
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Builds and posts a NotificationCompat that mirrors a freshly-appended
     * citizen inbox entry. The notification is silently skipped (no crash)
     * when the user has revoked POST_NOTIFICATIONS on Android 13+.
     */
    fun postReportNotification(
        context: Context,
        title: String,
        body: String,
        reportId: String,
        notificationTag: String
    ) {
        if (!hasPostPermission(context)) return
        ensureChannel(context)

        val openIntent = Intent(context, NotificationActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NEW_TASK
            if (reportId.isNotBlank()) {
                putExtra(NotificationActivity.EXTRA_OPEN_REPORT_ID, reportId)
            }
        }
        val pendingFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_CODE_BASE + notificationTag.hashCode(),
            openIntent,
            pendingFlags
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notif_status_small)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        // Tag scopes the notification to a single report so subsequent
        // updates (e.g. Pending -> In Progress -> Resolved) overwrite the
        // older tray entry instead of stacking. The numeric id is unused
        // because the tag is already unique.
        try {
            NotificationManagerCompat.from(context).notify(notificationTag, 0, notification)
        } catch (_: SecurityException) {
            // Permission was revoked between hasPostPermission() and notify().
        }
    }

    private fun hasPostPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    }
}

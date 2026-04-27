package com.example.smart_steward

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Locale

class NotificationActivity : AppCompatActivity() {
    private lateinit var notificationListContainer: LinearLayout
    private var notificationListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        notificationListContainer = findViewById(R.id.notificationListContainer)

        findViewById<ImageView>(R.id.notificationBackButton).setOnClickListener {
            finish()
        }

        findViewById<LinearLayout>(R.id.notificationNavHome).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            finish()
        }

        findViewById<LinearLayout>(R.id.notificationNavActivity).setOnClickListener {
            Toast.makeText(this, "Activity history coming soon.", Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.notificationNavProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.notificationCameraFab).setOnClickListener {
            startActivity(Intent(this, DashboardActivity::class.java))
            Toast.makeText(this, "Tap the camera button to capture evidence.", Toast.LENGTH_SHORT).show()
        }

        NotificationRepository.seedDemoNotificationsIfNeeded()
        listenForNotifications()
    }

    override fun onDestroy() {
        notificationListener?.remove()
        super.onDestroy()
    }

    private fun listenForNotifications() {
        notificationListener = NotificationRepository.listen(
            onUpdate = { notifications ->
                renderNotifications(notifications)
            },
            onError = { errorMessage ->
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun renderNotifications(notifications: List<NotificationRepository.AppNotification>) {
        notificationListContainer.removeAllViews()

        if (notifications.isEmpty()) {
            notificationListContainer.addView(
                TextView(this).apply {
                    text = "No notifications yet."
                    setTextColor(getColor(R.color.black))
                    textSize = 14f
                    gravity = android.view.Gravity.CENTER
                },
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    86.dp
                )
            )
            return
        }

        notifications.forEach { notification ->
            notificationListContainer.addView(createNotificationCard(notification))
        }
    }

    private fun createNotificationCard(
        notification: NotificationRepository.AppNotification
    ): LinearLayout {
        val cardHeight = if (notification.type == "advisory") 100.dp else 86.dp

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(12.dp, 0, 12.dp, 0)
            setBackgroundResource(R.drawable.bg_profile_option_item)
            isClickable = true
            isFocusable = true
            setOnClickListener { showNotificationDetail(notification) }

            addView(createIcon(notification.type))

            addView(
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(10.dp, 0, 0, 0)

                    addView(
                        TextView(context).apply {
                            text = notification.message
                            setTextColor(getColor(R.color.black))
                            textSize = 14f
                            maxLines = 2
                        }
                    )

                    addView(
                        TextView(context).apply {
                            text = notification.relativeTime()
                            setTextColor(getColor(R.color.black))
                            textSize = 12f
                            setPadding(0, 8.dp, 0, 0)
                        }
                    )
                },
                LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            )

            addView(
                TextView(context).apply {
                    text = notification.actionLabel.ifBlank { "Tap to View" }
                    setTextColor(getColor(R.color.black))
                    textSize = 12f
                }
            )
        }.also { card ->
            card.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                cardHeight
            ).apply {
                bottomMargin = 12.dp
            }
        }
    }

    private fun createIcon(type: String): TextView {
        val isPositive = type == "status_update" || type == "agency_acknowledgement"
        return TextView(this).apply {
            width = 32.dp
            height = 32.dp
            gravity = android.view.Gravity.CENTER
            text = if (isPositive) "✓" else "!"
            textSize = if (isPositive) 20f else 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(if (isPositive) getColor(R.color.white) else getColor(R.color.black))
            setBackgroundColor(
                when (type) {
                    "advisory" -> getColor(R.color.yellow)
                    "action_request" -> getColor(R.color.yellow)
                    else -> 0xFF1FE03C.toInt()
                }
            )
        }
    }

    private fun showNotificationDetail(notification: NotificationRepository.AppNotification) {
        val createdAt = notification.createdAt?.toDate()?.let {
            SimpleDateFormat("MMM dd, yyyy hh:mm a", Locale.getDefault()).format(it)
        } ?: "Just now"

        val details = buildString {
            appendLine(notification.message)
            appendLine()
            if (notification.reportId.isNotBlank()) appendLine("Report ID: ${notification.reportId}")
            appendLine("Type: ${notification.type.displayName()}")
            if (notification.status.isNotBlank()) appendLine("Status: ${notification.status}")
            if (notification.agency.isNotBlank()) appendLine("Assigned Agency: ${notification.agency}")
            if (notification.location.isNotBlank()) appendLine("Location: ${notification.location}")
            appendLine("Date: $createdAt")
            appendLine()
            appendLine("Status Timeline:")
            appendLine("Submitted → Acknowledged → Under Review → Verified → In Progress → Resolved")
        }

        AlertDialog.Builder(this)
            .setTitle(notification.title.ifBlank { "Notification Detail" })
            .setMessage(details)
            .setPositiveButton("OK", null)
            .setNegativeButton("Back to Dashboard") { _, _ ->
                startActivity(Intent(this, DashboardActivity::class.java))
                finish()
            }
            .show()
    }

    private fun NotificationRepository.AppNotification.relativeTime(): String {
        val createdDate = createdAt?.toDate() ?: return "Just now"
        val diffMillis = System.currentTimeMillis() - createdDate.time
        val minute = 60_000L
        val hour = 60 * minute

        return when {
            diffMillis < minute -> "Just now"
            diffMillis < hour -> "${diffMillis / minute} Minute ago"
            diffMillis < 24 * hour -> "${diffMillis / hour} Hour ago"
            else -> SimpleDateFormat("MMM dd", Locale.getDefault()).format(createdDate)
        }
    }

    private fun String.displayName(): String {
        return split("_").joinToString(" ") { word ->
            word.replaceFirstChar { char -> char.titlecase(Locale.getDefault()) }
        }
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()
}

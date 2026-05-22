package com.example.smart_steward

import android.content.Intent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import com.google.firebase.auth.FirebaseAuth

enum class MainBottomNavTab {
    HOME,
    ACTIVITY,
    HISTORY,
    NOTIFICATIONS
}

object MainBottomNav {

    fun setup(activity: AppCompatActivity, selected: MainBottomNavTab?) {
        applySelection(activity, selected)

        activity.findViewById<LinearLayout>(R.id.bottomNavHome)?.setOnClickListener {
            if (activity is DashboardActivity) return@setOnClickListener
            activity.startActivity(
                Intent(activity, DashboardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            activity.finish()
        }

        activity.findViewById<LinearLayout>(R.id.bottomNavActivity)?.setOnClickListener {
            if (activity is MyActivityActivity) return@setOnClickListener
            activity.startActivity(Intent(activity, MyActivityActivity::class.java))
            if (activity !is DashboardActivity) {
                activity.finish()
            }
        }

        activity.findViewById<LinearLayout>(R.id.bottomNavHistory)?.setOnClickListener {
            if (activity is ReportHistoryActivity) return@setOnClickListener
            activity.startActivity(Intent(activity, ReportHistoryActivity::class.java))
            activity.finish()
        }

        activity.findViewById<LinearLayout>(R.id.bottomNavNotification)?.setOnClickListener {
            if (activity is NotificationActivity) return@setOnClickListener
            activity.startActivity(Intent(activity, NotificationActivity::class.java))
            if (activity !is DashboardActivity) {
                activity.finish()
            }
        }

        activity.findViewById<FrameLayout>(R.id.bottomNavCameraFab)?.setOnClickListener {
            if (activity is DashboardActivity) {
                activity.openMediaCaptureChooser()
            } else {
                activity.startActivity(
                    Intent(activity, DashboardActivity::class.java)
                        .putExtra(DashboardActivity.EXTRA_OPEN_CAMERA, true)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
                activity.finish()
            }
        }
    }

    fun updateBadge(activity: AppCompatActivity) {
        val badge = activity.findViewById<TextView>(R.id.bottomNavBadge) ?: return
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            badge.visibility = View.GONE
            return
        }
        CitizenNotificationsRepository.countUnread(uid, onResult = { unread ->
            activity.runOnUiThread {
                badge.visibility = if (unread > 0) View.VISIBLE else View.GONE
                if (unread > 0) {
                    badge.text = if (unread > 99) "99+" else unread.toString()
                }
            }
        })
    }

    private fun applySelection(activity: AppCompatActivity, selected: MainBottomNavTab?) {
        val active = ContextCompat.getColor(activity, R.color.register_button_green)
        val inactive = ContextCompat.getColor(activity, R.color.bottom_nav_inactive)

        fun styleTab(
            iconId: Int,
            labelId: Int,
            isSelected: Boolean
        ) {
            val color = if (isSelected) active else inactive
            activity.findViewById<ImageView>(iconId)?.let {
                ImageViewCompat.setImageTintList(it, android.content.res.ColorStateList.valueOf(color))
            }
            activity.findViewById<TextView>(labelId)?.setTextColor(color)
        }

        styleTab(
            R.id.bottomNavHomeIcon,
            R.id.bottomNavHomeLabel,
            selected == MainBottomNavTab.HOME
        )
        styleTab(
            R.id.bottomNavActivityIcon,
            R.id.bottomNavActivityLabel,
            selected == MainBottomNavTab.ACTIVITY
        )
        styleTab(
            R.id.bottomNavHistoryIcon,
            R.id.bottomNavHistoryLabel,
            selected == MainBottomNavTab.HISTORY
        )
        styleTab(
            R.id.bottomNavNotificationIcon,
            R.id.bottomNavNotificationLabel,
            selected == MainBottomNavTab.NOTIFICATIONS
        )
    }
}

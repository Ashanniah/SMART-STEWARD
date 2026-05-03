package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity

class NotificationActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        findViewById<ImageView>(R.id.notificationBackButton).setOnClickListener {
            finish()
        }

        findViewById<LinearLayout>(R.id.notificationNavHome).setOnClickListener {
            startActivity(
                Intent(this, DashboardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }

        findViewById<LinearLayout>(R.id.notificationNavActivity).setOnClickListener {
            startActivity(Intent(this, MyActivityActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.notificationNavNotification).setOnClickListener { }

        findViewById<LinearLayout>(R.id.notificationNavProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.notificationCameraFab).setOnClickListener {
            startActivity(
                Intent(this, DashboardActivity::class.java)
                    .putExtra(DashboardActivity.EXTRA_OPEN_CAMERA, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
    }
}

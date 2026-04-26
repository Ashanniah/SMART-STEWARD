package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
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
            finish()
        }

        findViewById<LinearLayout>(R.id.notificationNavProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
    }
}

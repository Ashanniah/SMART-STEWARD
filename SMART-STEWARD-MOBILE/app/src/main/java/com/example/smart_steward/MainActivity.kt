package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        findViewById<android.widget.Button>(R.id.getStartedButton).setOnClickListener {
            LandingGate.markLandingSeen(this)
            val nextScreen = intent.getStringExtra(LandingGate.EXTRA_NEXT_SCREEN)
            val nextIntent = if (nextScreen == LandingGate.NEXT_LOGIN) {
                Intent(this, LoginActivity::class.java)
            } else {
                Intent(this, DashboardActivity::class.java)
            }
            startActivity(nextIntent)
            finish()
        }
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
    }
}
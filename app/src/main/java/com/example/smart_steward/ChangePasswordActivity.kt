package com.example.smart_steward

import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class ChangePasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        findViewById<Button>(R.id.sendNewPasswordButton).setOnClickListener {
            finish()
        }
    }
}

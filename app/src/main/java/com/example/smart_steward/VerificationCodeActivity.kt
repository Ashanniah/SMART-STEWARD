package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VerificationCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_code)

        findViewById<Button>(R.id.submitCodeButton).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        findViewById<TextView>(R.id.verificationSignUpLink).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}

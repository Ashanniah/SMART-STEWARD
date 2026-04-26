package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class ForgotPasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_forgot_password)

        findViewById<TextView>(R.id.backToLoginLink).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.forgotSignUpLink).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<Button>(R.id.sendCodeButton).setOnClickListener {
            startActivity(Intent(this, VerificationCodeActivity::class.java))
        }
    }
}

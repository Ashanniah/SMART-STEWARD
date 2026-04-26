package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
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

        val emailInput = findViewById<EditText>(R.id.forgotEmailInput)
        findViewById<Button>(R.id.sendCodeButton).setOnClickListener {
            val email = emailInput.text.toString().trim()

            if (email.isBlank()) {
                FormValidation.toast(this, "Please enter your email address.")
                return@setOnClickListener
            }

            if (!FormValidation.isValidEmail(email)) {
                FormValidation.toast(this, "Please enter a valid email address.")
                return@setOnClickListener
            }

            FormValidation.toast(this, "Sending verification code...")
            FormValidation.toast(this, "Verification code sent successfully.")
            startActivity(Intent(this, VerificationCodeActivity::class.java))
        }
    }
}

package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class VerificationCodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_verification_code)

        val codeInputs = listOf(
            findViewById<EditText>(R.id.codeDigitOne),
            findViewById<EditText>(R.id.codeDigitTwo),
            findViewById<EditText>(R.id.codeDigitThree),
            findViewById<EditText>(R.id.codeDigitFour)
        )

        findViewById<Button>(R.id.submitCodeButton).setOnClickListener {
            val digits = codeInputs.map { it.text.toString().trim() }

            if (digits.all { it.isBlank() }) {
                FormValidation.toast(this, "Please enter the verification code.")
                return@setOnClickListener
            }

            if (digits.any { it.isBlank() }) {
                FormValidation.toast(this, "Please complete the verification code.")
                return@setOnClickListener
            }

            if (digits.any { digit -> digit.any { !it.isDigit() } }) {
                FormValidation.toast(this, "Only numbers are allowed.")
                return@setOnClickListener
            }

            FormValidation.toast(this, "Verifying code...")
            FormValidation.toast(this, "Verification successful.")
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        findViewById<TextView>(R.id.resendCodeLink).setOnClickListener {
            FormValidation.toast(this, "A new verification code has been sent.")
        }

        findViewById<View>(R.id.verificationSignUpLink).setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }
}

package com.example.smart_steward

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class ChangePasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        val newPasswordInput = findViewById<EditText>(R.id.newPasswordInput)
        val confirmNewPasswordInput = findViewById<EditText>(R.id.confirmNewPasswordInput)

        findViewById<Button>(R.id.sendNewPasswordButton).setOnClickListener {
            val newPassword = newPasswordInput.text.toString().trim()
            val confirmPassword = confirmNewPasswordInput.text.toString().trim()

            if (newPassword.isBlank() && confirmPassword.isBlank()) {
                FormValidation.toast(this, "Please complete all required fields.")
                return@setOnClickListener
            }

            if (newPassword.isBlank()) {
                FormValidation.toast(this, "Please enter your new password.")
                return@setOnClickListener
            }

            if (confirmPassword.isBlank()) {
                FormValidation.toast(this, "Please confirm your new password.")
                return@setOnClickListener
            }

            FormValidation.passwordError(newPassword)?.let { message ->
                FormValidation.toast(this, message)
                return@setOnClickListener
            }

            if (newPassword != confirmPassword) {
                FormValidation.toast(this, "Passwords do not match.")
                return@setOnClickListener
            }

            FormValidation.toast(this, "Updating password...")
            FormValidation.toast(this, "Password updated successfully.")
            finish()
        }
    }
}

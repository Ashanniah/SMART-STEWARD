package com.example.smart_steward

import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AppCompatActivity

class ChangePasswordActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_change_password)

        val currentPasswordInput = findViewById<EditText>(R.id.currentPasswordInput)
        val newPasswordInput = findViewById<EditText>(R.id.newPasswordInput)
        val confirmNewPasswordInput = findViewById<EditText>(R.id.confirmNewPasswordInput)

        setupPasswordToggle(currentPasswordInput)
        setupPasswordToggle(newPasswordInput)
        setupPasswordToggle(confirmNewPasswordInput)

        findViewById<Button>(R.id.sendNewPasswordButton).setOnClickListener {
            val currentPassword = currentPasswordInput.text.toString()
            val newPassword = newPasswordInput.text.toString().trim()
            val confirmPassword = confirmNewPasswordInput.text.toString().trim()

            if (currentPassword.isBlank()) {
                FormValidation.toast(this, "Please enter your current password.")
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

            if (newPassword == currentPassword.trim()) {
                FormValidation.toast(this, "New password must be different from your current password.")
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

    private fun setupPasswordToggle(input: EditText) {
        var isVisible = false

        input.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val endDrawable = input.compoundDrawablesRelative[2] ?: input.compoundDrawables[2]
                if (endDrawable != null) {
                    val tapStart = input.width - input.paddingEnd - endDrawable.bounds.width()
                    if (event.x >= tapStart) {
                        isVisible = !isVisible
                        val cursor = input.selectionEnd

                        input.inputType = if (isVisible) {
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                        } else {
                            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                        }

                        input.setCompoundDrawablesRelativeWithIntrinsicBounds(
                            R.drawable.register_ic_lock,
                            0,
                            if (isVisible) R.drawable.ic_hide_sized else R.drawable.ic_eye_sized,
                            0
                        )
                        input.setSelection(if (cursor >= 0) cursor else input.text.length)
                        return@setOnTouchListener true
                    }
                }
            }
            false
        }
    }
}

package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.example.smart_steward.api.ApiProvider
import com.example.smart_steward.api.routes.AuthRoutes

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val firstNameInput = findViewById<EditText>(R.id.registerFirstNameInput)
        val middleNameInput = findViewById<EditText>(R.id.registerMiddleNameInput)
        val lastNameInput = findViewById<EditText>(R.id.registerLastNameInput)
        val emailInput = findViewById<EditText>(R.id.registerEmailInput)
        val passwordInput = findViewById<EditText>(R.id.registerPasswordInput)
        val confirmPasswordInput = findViewById<EditText>(R.id.registerConfirmPasswordInput)
        val termsText = findViewById<TextView>(R.id.registerTermsText)

        setupPasswordToggle(passwordInput)
        setupPasswordToggle(confirmPasswordInput)

        termsText.text = HtmlCompat.fromHtml(
            getString(R.string.terms_privacy_text),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        findViewById<Button>(R.id.registerButton).setOnClickListener {
            val firstName = firstNameInput.text.toString().trim()
            val middleName = middleNameInput.text.toString().trim()
            val lastName = lastNameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()
            val confirmPassword = confirmPasswordInput.text.toString().trim()

            if (firstName.isBlank() || middleName.isBlank() || lastName.isBlank() || email.isBlank() || password.isBlank() || confirmPassword.isBlank()) {
                Toast.makeText(this, "Complete all fields first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Passwords do not match.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val fullName = "$firstName $middleName $lastName"

            ApiProvider.auth.call(
                route = AuthRoutes.REGISTER_WITH_EMAIL,
                params = mapOf(
                    "displayName" to fullName,
                    "firstName" to firstName,
                    "middleName" to middleName,
                    "lastName" to lastName,
                    "email" to email,
                    "password" to password
                ),
                onSuccess = {
                    Toast.makeText(this, "Registration successful. Please log in.", Toast.LENGTH_LONG).show()
                    startActivity(Intent(this, LoginActivity::class.java))
                    finish()
                },
                onError = { error ->
                    Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                }
            )
        }

        findViewById<View>(R.id.signInLink).setOnClickListener {
            startActivity(Intent(this, LoginActivity::class.java))
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
                            R.drawable.ic_login_field_padlock,
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

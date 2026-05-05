package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
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

            if (firstName.isBlank() && middleName.isBlank() && lastName.isBlank() && email.isBlank() && password.isBlank() && confirmPassword.isBlank()) {
                FormValidation.toast(this, "Please complete all registration fields.")
                return@setOnClickListener
            }

            if (firstName.isBlank() || lastName.isBlank()) {
                FormValidation.toast(this, "Please enter your first and last name.")
                return@setOnClickListener
            }

            if (email.isBlank()) {
                FormValidation.toast(this, "Please enter your email address.")
                return@setOnClickListener
            }

            if (!FormValidation.isValidEmail(email)) {
                FormValidation.toast(this, "Please enter a valid email address.")
                return@setOnClickListener
            }

            if (password.isBlank()) {
                FormValidation.toast(this, "Please enter your password.")
                return@setOnClickListener
            }

            if (confirmPassword.isBlank()) {
                FormValidation.toast(this, "Please confirm your password.")
                return@setOnClickListener
            }

            FormValidation.passwordError(password)?.let { message ->
                FormValidation.toast(this, message)
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                FormValidation.toast(this, "Passwords do not match.")
                return@setOnClickListener
            }

            val fullName = listOf(firstName, middleName, lastName)
                .filter { it.isNotBlank() }
                .joinToString(" ")

            FormValidation.toast(this, "Creating account...")
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
                    FormValidation.toast(this, "Account created successfully.")
                    val nextIntent = if (LandingGate.hasSeenLanding(this)) {
                        Intent(this, LoginActivity::class.java)
                    } else {
                        Intent(this, MainActivity::class.java).putExtra(
                            LandingGate.EXTRA_NEXT_SCREEN,
                            LandingGate.NEXT_LOGIN
                        )
                    }
                    startActivity(nextIntent)
                    finish()
                },
                onError = { error ->
                    FormValidation.toast(this, FormValidation.registerErrorMessage(error))
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

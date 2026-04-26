package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.MotionEvent
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_steward.api.ApiProvider
import com.example.smart_steward.api.routes.AuthRoutes

class LoginActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailInput = findViewById<EditText>(R.id.loginEmailInput)
        val passwordInput = findViewById<EditText>(R.id.loginPasswordInput)
        setupPasswordToggle(passwordInput)

        findViewById<TextView>(R.id.forgotPasswordLink).setOnClickListener {
            startActivity(Intent(this, ForgotPasswordActivity::class.java))
        }

        findViewById<Button>(R.id.loginButton).setOnClickListener {
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (email.isBlank() && password.isBlank()) {
                FormValidation.toast(this, "Please complete all required fields.")
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

            if (password.length < 8) {
                FormValidation.toast(this, "Password must be at least 8 characters.")
                return@setOnClickListener
            }

            FormValidation.toast(this, "Loading account...")
            ApiProvider.auth.call(
                route = AuthRoutes.LOGIN_WITH_EMAIL,
                params = mapOf(
                    "email" to email,
                    "password" to password
                ),
                onSuccess = {
                    FormValidation.toast(this, "Login successful.")
                    val nextIntent = if (LandingGate.hasSeenLanding(this)) {
                        Intent(this, DashboardActivity::class.java)
                    } else {
                        Intent(this, MainActivity::class.java).putExtra(
                            LandingGate.EXTRA_NEXT_SCREEN,
                            LandingGate.NEXT_DASHBOARD
                        )
                    }
                    startActivity(nextIntent)
                    finish()
                },
                onError = { error ->
                    FormValidation.toast(this, FormValidation.loginErrorMessage(error))
                }
            )
        }

        val openSignUp = {
            startActivity(Intent(this, RegisterActivity::class.java))
        }

        findViewById<TextView>(R.id.signUpTextLink).setOnClickListener {
            openSignUp()
        }

        findViewById<android.widget.LinearLayout>(R.id.signUpLink).setOnClickListener {
            openSignUp()
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

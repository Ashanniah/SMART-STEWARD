package com.example.smart_steward

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_steward.api.ApiProvider
import com.example.smart_steward.api.routes.AuthRoutes

class LoginActivity : AppCompatActivity() {

    private val loginPrefs by lazy {
        getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /** Avoid persisting while restoring UI from SharedPreferences. */
    private var suppressRememberCallback = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        val emailInput = findViewById<EditText>(R.id.loginEmailInput)
        val passwordInput = findViewById<EditText>(R.id.loginPasswordInput)
        val rememberMe = findViewById<CheckBox>(R.id.loginRememberMe)

        applySavedEmail(emailInput, rememberMe)

        rememberMe.setOnCheckedChangeListener { _, isChecked ->
            if (suppressRememberCallback) return@setOnCheckedChangeListener
            persistRememberState(isChecked, emailInput.text.toString().trim())
        }

        emailInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (suppressRememberCallback) return
                if (!rememberMe.isChecked) return
                val email = s?.toString()?.trim().orEmpty()
                if (email.isNotBlank() && FormValidation.isValidEmail(email)) {
                    loginPrefs.edit().putString(KEY_SAVED_EMAIL, email).apply()
                }
            }
        })

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
                    persistRememberState(rememberMe.isChecked, email)
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

    private fun applySavedEmail(emailInput: EditText, rememberMe: CheckBox) {
        suppressRememberCallback = true
        try {
            val remember = loginPrefs.getBoolean(KEY_REMEMBER_ME, false)
            rememberMe.isChecked = remember
            if (remember) {
                emailInput.setText(loginPrefs.getString(KEY_SAVED_EMAIL, "").orEmpty())
            }
        } finally {
            suppressRememberCallback = false
        }
    }

    private fun persistRememberState(remember: Boolean, email: String) {
        loginPrefs.edit().apply {
            putBoolean(KEY_REMEMBER_ME, remember)
            if (remember) {
                if (email.isNotBlank() && FormValidation.isValidEmail(email)) {
                    putString(KEY_SAVED_EMAIL, email)
                }
            } else {
                remove(KEY_SAVED_EMAIL)
            }
            apply()
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

    companion object {
        private const val PREFS_NAME = "smart_steward_login"
        private const val KEY_REMEMBER_ME = "remember_me"
        private const val KEY_SAVED_EMAIL = "saved_email"
    }
}

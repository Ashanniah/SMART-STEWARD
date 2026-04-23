package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.text.HtmlCompat
import com.example.smart_steward.api.ApiProvider
import com.example.smart_steward.api.routes.AuthRoutes

class RegisterActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        val usernameInput = findViewById<EditText>(R.id.registerUsernameInput)
        val emailInput = findViewById<EditText>(R.id.registerEmailInput)
        val passwordInput = findViewById<EditText>(R.id.registerPasswordInput)
        val termsText = findViewById<TextView>(R.id.registerTermsText)
        termsText.text = HtmlCompat.fromHtml(
            getString(R.string.terms_privacy_text),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        )

        findViewById<Button>(R.id.registerButton).setOnClickListener {
            val username = usernameInput.text.toString().trim()
            val email = emailInput.text.toString().trim()
            val password = passwordInput.text.toString().trim()

            if (username.isBlank() || email.isBlank() || password.isBlank()) {
                Toast.makeText(this, "Complete all fields first.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            ApiProvider.auth.call(
                route = AuthRoutes.REGISTER_WITH_EMAIL,
                params = mapOf(
                    "displayName" to username,
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
}

package com.example.smart_steward

import android.content.Context
import android.util.Patterns
import android.widget.Toast

object FormValidation {
    private val specialCharacterRegex = Regex("[^A-Za-z0-9]")

    fun toast(context: Context, message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    fun isValidEmail(email: String): Boolean =
        Patterns.EMAIL_ADDRESS.matcher(email).matches()

    fun passwordError(password: String): String? = when {
        password.length < 8 -> "Password must be at least 8 characters."
        password.none { it.isUpperCase() } -> "Password must contain at least one uppercase letter."
        password.none { it.isLowerCase() } -> "Password must contain at least one lowercase letter."
        password.none { it.isDigit() } -> "Password must contain at least one number."
        !specialCharacterRegex.containsMatchIn(password) ->
            "Password must contain at least one special character."
        else -> null
    }

    fun loginErrorMessage(error: String): String {
        val lowerError = error.lowercase()
        return when {
            "network" in lowerError || "connection" in lowerError ->
                "Connection error. Please try again."
            "disabled" in lowerError ->
                "This account is inactive."
            "too many" in lowerError ->
                "Too many login attempts. Please try again later."
            "verify" in lowerError || "verified" in lowerError ->
                "Please verify your email before logging in."
            "user" in lowerError && "not" in lowerError ->
                "Account not found."
            "password" in lowerError || "credential" in lowerError || "invalid" in lowerError ->
                "Invalid email or password."
            else -> "Server unavailable. Please try later."
        }
    }

    fun registerErrorMessage(error: String): String {
        val lowerError = error.lowercase()
        return when {
            "network" in lowerError || "connection" in lowerError ->
                "Connection error. Please try again."
            "email" in lowerError && ("already" in lowerError || "in use" in lowerError) ->
                "Email address is already registered."
            "weak" in lowerError || "password" in lowerError ->
                "Password is too weak."
            else -> "Unable to create account. Please try again."
        }
    }
}

package com.example.smart_steward.api.services

import com.example.smart_steward.api.routes.AuthRoutes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class FirebaseAuthApiService : ApiService {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    override fun call(
        route: String,
        params: Map<String, Any?>,
        onSuccess: (Map<String, Any?>) -> Unit,
        onError: (String) -> Unit
    ) {
        when (route) {
            AuthRoutes.REGISTER_WITH_EMAIL -> {
                val email = params["email"] as? String
                val password = params["password"] as? String
                val displayName = params["displayName"] as? String
                val firstName = params["firstName"] as? String ?: ""
                val middleName = params["middleName"] as? String ?: ""
                val lastName = params["lastName"] as? String ?: ""

                if (email.isNullOrBlank() || password.isNullOrBlank()) {
                    onError("Email and password are required.")
                    return
                }

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        val profileUpdates = UserProfileChangeRequest.Builder()
                            .setDisplayName(displayName ?: "")
                            .build()
                        val currentUser = auth.currentUser
                        if (currentUser == null) {
                            onError("Unable to access registered user.")
                            return@addOnSuccessListener
                        }

                        currentUser.updateProfile(profileUpdates)
                            .addOnSuccessListener {
                                val userData = hashMapOf(
                                    "firstName" to firstName,
                                    "middleName" to middleName,
                                    "lastName" to lastName,
                                    "displayName" to (displayName ?: ""),
                                    "email" to (currentUser.email ?: email.orEmpty()),
                                    "role" to "citizen",
                                    "createdAt" to FieldValue.serverTimestamp()
                                )

                                firestore.collection("users")
                                    .document(currentUser.uid)
                                    .set(userData, SetOptions.merge())
                                    .addOnSuccessListener {
                                        onSuccess(
                                            mapOf(
                                                "route" to route,
                                                "uid" to (auth.currentUser?.uid ?: ""),
                                                "email" to (auth.currentUser?.email ?: "")
                                            )
                                        )
                                    }
                                    .addOnFailureListener { error ->
                                        onError(error.message ?: "Unable to save user profile.")
                                    }
                            }
                            .addOnFailureListener { error ->
                                onError(error.message ?: "Unable to update profile.")
                            }
                    }
                    .addOnFailureListener { error ->
                        onError(error.message ?: "Registration failed.")
                    }
            }

            AuthRoutes.LOGIN_WITH_EMAIL -> {
                val email = params["email"] as? String
                val password = params["password"] as? String

                if (email.isNullOrBlank() || password.isNullOrBlank()) {
                    onError("Email and password are required.")
                    return
                }

                auth.signInWithEmailAndPassword(email, password)
                    .addOnSuccessListener {
                        onSuccess(
                            mapOf(
                                "route" to route,
                                "uid" to (auth.currentUser?.uid ?: ""),
                                "email" to (auth.currentUser?.email ?: "")
                            )
                        )
                    }
                    .addOnFailureListener { error ->
                        onError(error.message ?: "Login failed.")
                    }
            }

            AuthRoutes.SIGN_IN_ANONYMOUSLY -> {
                auth.signInAnonymously()
                    .addOnSuccessListener {
                        onSuccess(
                            mapOf(
                                "route" to route,
                                "uid" to (auth.currentUser?.uid ?: "")
                            )
                        )
                    }
                    .addOnFailureListener { error ->
                        onError(error.message ?: "Anonymous sign-in failed.")
                    }
            }

            AuthRoutes.GET_CURRENT_USER_ID -> {
                onSuccess(
                    mapOf(
                        "route" to route,
                        "uid" to (auth.currentUser?.uid ?: "")
                    )
                )
            }

            AuthRoutes.SIGN_OUT -> {
                auth.signOut()
                onSuccess(mapOf("route" to route, "signedOut" to true))
            }

            else -> onError("Unknown route: $route")
        }
    }
}

package com.example.smart_steward

import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

class EditProfileActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var firstNameInput: EditText
    private lateinit var middleNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var barangayInput: EditText
    private lateinit var saveButton: TextView
    private lateinit var headerInitials: TextView
    private lateinit var headerName: TextView
    private lateinit var headerEmail: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        firstNameInput = findViewById(R.id.editProfileFirstNameInput)
        middleNameInput = findViewById(R.id.editProfileMiddleNameInput)
        lastNameInput = findViewById(R.id.editProfileLastNameInput)
        emailInput = findViewById(R.id.editProfileEmailInput)
        phoneInput = findViewById(R.id.editProfilePhoneInput)
        barangayInput = findViewById(R.id.editProfileBarangayInput)
        saveButton = findViewById(R.id.editProfileSaveButton)
        headerInitials = findViewById(R.id.editProfileHeaderInitials)
        headerName = findViewById(R.id.editProfileHeaderName)
        headerEmail = findViewById(R.id.editProfileHeaderEmail)

        findViewById<ImageView>(R.id.editProfileBackButton).setOnClickListener { finish() }

        loadCurrentProfile()

        saveButton.setOnClickListener { saveProfile() }
    }

    private fun loadCurrentProfile() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, "Sign in to edit your profile.", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        emailInput.setText(user.email.orEmpty())
        headerEmail.text = user.email.orEmpty()
        val uid = user.uid
        firestore.collection("users").document(uid).get()
            .addOnSuccessListener { doc ->
                val first = doc.getString("firstName").orEmpty()
                val middle = doc.getString("middleName").orEmpty()
                val last = doc.getString("lastName").orEmpty()
                phoneInput.setText(doc.getString("phoneNumber").orEmpty())
                barangayInput.setText(doc.getString("homeBarangay").orEmpty())
                if (first.isNotBlank() || middle.isNotBlank() || last.isNotBlank()) {
                    firstNameInput.setText(first)
                    middleNameInput.setText(middle)
                    lastNameInput.setText(last)
                    bindHeaderName(first, middle, last)
                } else {
                    val parts = user.displayName.orEmpty().trim().split(Regex("\\s+"))
                        .filter { it.isNotBlank() }
                    if (parts.isNotEmpty()) {
                        firstNameInput.setText(parts.first())
                        if (parts.size >= 3) {
                            middleNameInput.setText(parts.subList(1, parts.size - 1).joinToString(" "))
                            lastNameInput.setText(parts.last())
                        } else if (parts.size == 2) {
                            lastNameInput.setText(parts.last())
                        }
                    }
                    bindHeaderName(
                        firstNameInput.text.toString(),
                        middleNameInput.text.toString(),
                        lastNameInput.text.toString()
                    )
                }
            }
    }

    private fun saveProfile() {
        val user = auth.currentUser ?: return
        val first = firstNameInput.text.toString().trim()
        val middle = middleNameInput.text.toString().trim()
        val last = lastNameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val barangay = barangayInput.text.toString().trim()

        if (first.isBlank() || last.isBlank()) {
            Toast.makeText(this, "First name and last name are required.", Toast.LENGTH_LONG).show()
            return
        }

        val displayName = listOf(first, middle, last).filter { it.isNotBlank() }.joinToString(" ")
        saveButton.isEnabled = false
        saveButton.alpha = 0.7f

        val updates = hashMapOf<String, Any>(
            "firstName" to first,
            "middleName" to middle,
            "lastName" to last,
            "displayName" to displayName,
            "phoneNumber" to phone,
            "homeBarangay" to barangay
        )

        firestore.collection("users").document(user.uid)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                user.updateProfile(profile)
                    .addOnCompleteListener {
                        bindHeaderName(first, middle, last)
                        Toast.makeText(this, "Profile updated.", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
            }
            .addOnFailureListener { e ->
                saveButton.isEnabled = true
                saveButton.alpha = 1f
                Toast.makeText(this, e.message ?: "Failed to update profile.", Toast.LENGTH_LONG).show()
            }
    }

    private fun bindHeaderName(first: String, middle: String, last: String) {
        val displayName = listOf(first.trim(), middle.trim(), last.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { getString(R.string.profile_name_placeholder) }
        headerName.text = displayName
        headerInitials.text = initialsFrom(displayName)
    }

    private fun initialsFrom(displayName: String): String {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return "?"
        if (parts.size == 1) return parts[0].take(2).uppercase()
        return (parts[0].first().toString() + parts[parts.lastIndex].first().toString()).uppercase()
    }
}

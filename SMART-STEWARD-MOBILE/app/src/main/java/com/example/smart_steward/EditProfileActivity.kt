package com.example.smart_steward

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import java.util.Calendar

class EditProfileActivity : AppCompatActivity() {

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    private lateinit var firstNameInput: EditText
    private lateinit var middleNameInput: EditText
    private lateinit var lastNameInput: EditText
    private lateinit var emailInput: EditText
    private lateinit var phoneInput: EditText
    private lateinit var homeAddressInput: EditText
    private lateinit var saveButton: TextView
    private lateinit var headerAvatar: ImageView
    private lateinit var headerName: TextView
    private lateinit var headerEmail: TextView
    private lateinit var joinedBadge: TextView

    private var isSaving = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_edit_profile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.editProfileRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        firstNameInput = findViewById(R.id.editProfileFirstNameInput)
        middleNameInput = findViewById(R.id.editProfileMiddleNameInput)
        lastNameInput = findViewById(R.id.editProfileLastNameInput)
        emailInput = findViewById(R.id.editProfileEmailInput)
        phoneInput = findViewById(R.id.editProfilePhoneInput)
        homeAddressInput = findViewById(R.id.editProfileHomeAddressInput)
        saveButton = findViewById(R.id.editProfileSaveButton)
        headerAvatar = findViewById(R.id.editProfileAvatarImage)
        ProfileInitials.bindDefaultAvatar(headerAvatar)
        headerName = findViewById(R.id.editProfileHeaderName)
        headerEmail = findViewById(R.id.editProfileHeaderEmail)
        joinedBadge = findViewById(R.id.editProfileJoinedBadge)

        findViewById<ImageView>(R.id.editProfileBackButton).setOnClickListener { finish() }
        saveButton.setOnClickListener { saveProfile() }

        val nameWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                bindHeaderName(
                    firstNameInput.text.toString(),
                    middleNameInput.text.toString(),
                    lastNameInput.text.toString()
                )
            }
        }
        firstNameInput.addTextChangedListener(nameWatcher)
        middleNameInput.addTextChangedListener(nameWatcher)
        lastNameInput.addTextChangedListener(nameWatcher)

        loadCurrentProfile()
    }

    private fun loadCurrentProfile() {
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, R.string.edit_profile_sign_in_required, Toast.LENGTH_LONG).show()
            finish()
            return
        }

        val email = user.email.orEmpty()
        emailInput.setText(email)
        headerEmail.text = email.ifBlank { getString(R.string.profile_email_placeholder) }

        Toast.makeText(this, R.string.edit_profile_loading, Toast.LENGTH_SHORT).show()

        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val first = doc.getString("firstName").orEmpty()
                val middle = doc.getString("middleName").orEmpty()
                val last = doc.getString("lastName").orEmpty()
                phoneInput.setText(doc.getString("phoneNumber").orEmpty())
                val home = doc.getString("homeAddress")
                    ?: doc.getString("homeBarangay")
                    ?: ""
                homeAddressInput.setText(home)

                if (first.isNotBlank() || middle.isNotBlank() || last.isNotBlank()) {
                    firstNameInput.setText(first)
                    middleNameInput.setText(middle)
                    lastNameInput.setText(last)
                } else {
                    splitDisplayNameIntoFields(user.displayName.orEmpty())
                }

                bindHeaderName(
                    firstNameInput.text.toString(),
                    middleNameInput.text.toString(),
                    lastNameInput.text.toString()
                )

                val joinedYear = resolveJoinedYear(doc.getTimestamp("createdAt"), user)
                joinedBadge.text = getString(R.string.edit_profile_joined_since, joinedYear)
            }
            .addOnFailureListener {
                splitDisplayNameIntoFields(user.displayName.orEmpty())
                bindHeaderName(
                    firstNameInput.text.toString(),
                    middleNameInput.text.toString(),
                    lastNameInput.text.toString()
                )
                joinedBadge.text = getString(
                    R.string.edit_profile_joined_since,
                    Calendar.getInstance().get(Calendar.YEAR)
                )
                Toast.makeText(this, R.string.edit_profile_load_failed, Toast.LENGTH_LONG).show()
            }
    }

    private fun splitDisplayNameIntoFields(displayName: String) {
        val parts = displayName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.isEmpty()) return
        firstNameInput.setText(parts.first())
        if (parts.size >= 3) {
            middleNameInput.setText(parts.subList(1, parts.size - 1).joinToString(" "))
            lastNameInput.setText(parts.last())
        } else if (parts.size == 2) {
            lastNameInput.setText(parts.last())
        }
    }

    private fun resolveJoinedYear(
        createdAt: com.google.firebase.Timestamp?,
        user: com.google.firebase.auth.FirebaseUser
    ): Int {
        createdAt?.toDate()?.let { return Calendar.getInstance().apply { time = it }.get(Calendar.YEAR) }
        user.metadata?.creationTimestamp?.let { ms ->
            if (ms > 0L) {
                return Calendar.getInstance().apply { timeInMillis = ms }.get(Calendar.YEAR)
            }
        }
        return Calendar.getInstance().get(Calendar.YEAR)
    }

    private fun saveProfile() {
        if (isSaving) return
        val user = auth.currentUser
        if (user == null) {
            Toast.makeText(this, R.string.edit_profile_sign_in_required, Toast.LENGTH_LONG).show()
            return
        }

        val first = firstNameInput.text.toString().trim()
        val middle = middleNameInput.text.toString().trim()
        val last = lastNameInput.text.toString().trim()
        val phone = phoneInput.text.toString().trim()
        val homeAddress = homeAddressInput.text.toString().trim()

        if (first.isBlank() || last.isBlank()) {
            Toast.makeText(this, R.string.edit_profile_name_required, Toast.LENGTH_LONG).show()
            return
        }

        val displayName = listOf(first, middle, last).filter { it.isNotBlank() }.joinToString(" ")
        isSaving = true
        setSaveEnabled(false)
        Toast.makeText(this, R.string.edit_profile_saving, Toast.LENGTH_SHORT).show()

        val updates = hashMapOf<String, Any>(
            "firstName" to first,
            "middleName" to middle,
            "lastName" to last,
            "displayName" to displayName,
            "phoneNumber" to phone,
            "homeAddress" to homeAddress,
            "homeBarangay" to homeAddress,
            "updatedAt" to com.google.firebase.Timestamp.now()
        )

        firestore.collection("users").document(user.uid)
            .set(updates, SetOptions.merge())
            .addOnSuccessListener {
                val profile = UserProfileChangeRequest.Builder()
                    .setDisplayName(displayName)
                    .build()
                user.updateProfile(profile)
                    .addOnSuccessListener {
                        onSaveComplete(first, middle, last, success = true, authFailed = false)
                    }
                    .addOnFailureListener {
                        onSaveComplete(first, middle, last, success = true, authFailed = true)
                    }
            }
            .addOnFailureListener { e ->
                isSaving = false
                setSaveEnabled(true)
                Toast.makeText(
                    this,
                    e.message?.takeIf { it.isNotBlank() }
                        ?: getString(R.string.edit_profile_save_failed),
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun onSaveComplete(
        first: String,
        middle: String,
        last: String,
        success: Boolean,
        authFailed: Boolean
    ) {
        isSaving = false
        setSaveEnabled(true)
        bindHeaderName(first, middle, last)
        if (success) {
            Toast.makeText(
                this,
                if (authFailed) R.string.edit_profile_auth_update_failed
                else R.string.edit_profile_saved,
                Toast.LENGTH_LONG
            ).show()
            setResult(RESULT_OK)
            finish()
        }
    }

    private fun setSaveEnabled(enabled: Boolean) {
        saveButton.isEnabled = enabled
        saveButton.alpha = if (enabled) 1f else 0.65f
    }

    private fun bindHeaderName(first: String, middle: String, last: String) {
        val displayName = listOf(first.trim(), middle.trim(), last.trim())
            .filter { it.isNotBlank() }
            .joinToString(" ")
            .ifBlank { getString(R.string.profile_name_placeholder) }
        headerName.text = displayName
    }
}

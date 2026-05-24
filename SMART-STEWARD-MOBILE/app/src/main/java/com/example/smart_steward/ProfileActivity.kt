package com.example.smart_steward

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.smart_steward.api.ApiProvider
import com.example.smart_steward.api.routes.AuthRoutes
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class ProfileActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_EDIT_PROFILE = 4001
    }

    private val auth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }
    private val firestore: FirebaseFirestore by lazy { FirebaseFirestore.getInstance() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.profileRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        ProfileInitials.bindDefaultAvatar(findViewById(R.id.profileAvatarImage))

        findViewById<ImageView>(R.id.profileBackButton).setOnClickListener { finish() }

        findViewById<TextView>(R.id.profileEditButton).setOnClickListener {
            @Suppress("DEPRECATION")
            startActivityForResult(Intent(this, EditProfileActivity::class.java), REQUEST_EDIT_PROFILE)
        }

        findViewById<LinearLayout>(R.id.profileRowAccountSettings).setOnClickListener {
            startActivity(Intent(this, ChangePasswordActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.profileRowNotifications).setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.profileRowPrivacy).setOnClickListener {
            startActivity(Intent(this, ReportHistoryActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.profileRowTerms).setOnClickListener {
            showInfoDialog(
                title = getString(R.string.terms_conditions_short),
                message = getString(R.string.terms_and_condition)
            )
        }

        findViewById<LinearLayout>(R.id.profileRowHelp).setOnClickListener {
            showInfoDialog(
                title = getString(R.string.help_and_support),
                message = getString(R.string.profile_help_message)
            )
        }

        findViewById<LinearLayout>(R.id.profileRowAbout).setOnClickListener {
            showInfoDialog(
                title = getString(R.string.profile_about_smart_steward),
                message = getString(
                    R.string.profile_about_dialog_fmt,
                    getString(R.string.profile_about_message),
                    versionLabel()
                )
            )
        }

        findViewById<LinearLayout>(R.id.logoutButton).setOnClickListener {
            confirmLogout()
        }

        bindProfileHeader()
        bindAboutVersion()
        bindNotificationBadge()
        MainBottomNav.setup(this, selected = null)
    }

    override fun onResume() {
        super.onResume()
        bindProfileHeader()
        bindAboutVersion()
        bindNotificationBadge()
        MainBottomNav.updateBadge(this)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_EDIT_PROFILE && resultCode == RESULT_OK) {
            bindProfileHeader()
            Toast.makeText(this, R.string.edit_profile_saved, Toast.LENGTH_SHORT).show()
        }
    }

    private fun bindProfileHeader() {
        val user = auth.currentUser
        if (user == null) {
            findViewById<TextView>(R.id.profileName).text = getString(R.string.profile_name_placeholder)
            findViewById<TextView>(R.id.profileEmail).text = getString(R.string.profile_email_placeholder)
            return
        }

        val email = user.email.orEmpty()
        findViewById<TextView>(R.id.profileEmail).text =
            email.ifBlank { getString(R.string.profile_email_placeholder) }

        val displayFromAuth = user.displayName?.trim().orEmpty()
        if (displayFromAuth.isNotEmpty()) {
            findViewById<TextView>(R.id.profileName).text = displayFromAuth
        }

        firestore.collection("users").document(user.uid).get()
            .addOnSuccessListener { doc ->
                val first = doc.getString("firstName").orEmpty()
                val middle = doc.getString("middleName").orEmpty()
                val last = doc.getString("lastName").orEmpty()
                val fromDoc = listOf(first, middle, last).filter { it.isNotBlank() }.joinToString(" ")
                val name = fromDoc.ifBlank {
                    doc.getString("displayName")?.trim().orEmpty()
                }.ifBlank {
                    displayFromAuth
                }.ifBlank {
                    getString(R.string.profile_name_placeholder)
                }
                findViewById<TextView>(R.id.profileName).text = name
            }
    }

    private fun bindAboutVersion() {
        findViewById<TextView>(R.id.profileAboutVersion).text = versionLabel()
    }

    private fun bindNotificationBadge() {
        val badge = findViewById<TextView>(R.id.profileNotifBadge)
        val uid = auth.currentUser?.uid
        if (uid.isNullOrBlank()) {
            badge.visibility = View.GONE
            return
        }
        CitizenNotificationsRepository.countUnread(uid, onResult = { unread ->
            runOnUiThread {
                if (unread > 0) {
                    badge.visibility = View.VISIBLE
                    badge.text = getString(R.string.profile_badge_unread_fmt, unread)
                } else {
                    badge.visibility = View.GONE
                }
            }
        })
    }

    private fun versionLabel(): String {
        return try {
            val ver = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                ).versionName
            } else {
                @Suppress("DEPRECATION")
                packageManager.getPackageInfo(packageName, 0).versionName
            }
            ver ?: "1.0"
        } catch (_: Exception) {
            "1.0"
        }
    }

    private fun showInfoDialog(title: String, message: String) {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    private fun confirmLogout() {
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.logout_confirm_title))
            .setMessage(getString(R.string.logout_confirm_message))
            .setNegativeButton(getString(R.string.logout_confirm_no), null)
            .setPositiveButton(getString(R.string.logout_confirm_yes)) { _, _ ->
                performLogout()
            }
            .show()
    }

    private fun performLogout() {
        ApiProvider.auth.call(
            route = AuthRoutes.SIGN_OUT,
            onSuccess = {
                Toast.makeText(this, R.string.profile_logout_success, Toast.LENGTH_SHORT).show()
                val intent = Intent(this, LoginActivity::class.java)
                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                startActivity(intent)
                finish()
            },
            onError = { error ->
                Toast.makeText(
                    this,
                    error.ifBlank { getString(R.string.profile_logout_failed) },
                    Toast.LENGTH_LONG
                ).show()
            }
        )
    }
}

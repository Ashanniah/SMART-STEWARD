package com.example.smart_steward

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.smart_steward.api.ApiProvider
import com.example.smart_steward.api.routes.AuthRoutes
import com.google.firebase.auth.FirebaseAuth

class ProfileActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)

        bindProfileHeader()

        findViewById<TextView>(R.id.profileAboutVersion).text = versionLabel()

        findViewById<TextView>(R.id.profileEditButton).setOnClickListener {
            startActivity(Intent(this, EditProfileActivity::class.java))
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
            Toast.makeText(this, getString(R.string.terms_and_condition), Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.profileRowHelp).setOnClickListener {
            Toast.makeText(this, getString(R.string.help_and_support), Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.profileRowAbout).setOnClickListener {
            Toast.makeText(this, getString(R.string.profile_about_smart_steward), Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.logoutButton).setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle(getString(R.string.logout_confirm_title))
                .setMessage(getString(R.string.logout_confirm_message))
                .setNegativeButton(getString(R.string.logout_confirm_no), null)
                .setPositiveButton(getString(R.string.logout_confirm_yes)) { _, _ ->
                    ApiProvider.auth.call(
                        route = AuthRoutes.SIGN_OUT,
                        onSuccess = {
                            val intent = Intent(this, LoginActivity::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            startActivity(intent)
                        },
                        onError = { error ->
                            Toast.makeText(this, error, Toast.LENGTH_LONG).show()
                        }
                    )
                }
                .show()
        }

        MainBottomNav.setup(this, selected = null)
    }

    override fun onResume() {
        super.onResume()
        bindProfileHeader()
        MainBottomNav.updateBadge(this)
    }

    private fun bindProfileHeader() {
        val user = FirebaseAuth.getInstance().currentUser
        val name = user?.displayName.takeUnless { it.isNullOrBlank() }
            ?: getString(R.string.profile_name_placeholder)
        findViewById<TextView>(R.id.profileName).text = name
        findViewById<TextView>(R.id.profileEmail).text = user?.email.takeUnless { it.isNullOrBlank() }
            ?: getString(R.string.profile_email_placeholder)
        findViewById<TextView>(R.id.profileInitials).text = ProfileInitials.fromDisplayName(name)
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
            getString(R.string.profile_about_version_fmt, ver ?: "1.0")
        } catch (_: Exception) {
            getString(R.string.profile_about_version_fmt, "1.0")
        }
    }

}

package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.widget.Button
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

        val user = FirebaseAuth.getInstance().currentUser
        findViewById<TextView>(R.id.profileName).text = user?.displayName.takeUnless { it.isNullOrBlank() }
            ?: getString(R.string.profile_name_placeholder)
        findViewById<TextView>(R.id.profileEmail).text = user?.email.takeUnless { it.isNullOrBlank() }
            ?: getString(R.string.profile_email_placeholder)

        findViewById<Button>(R.id.logoutButton).setOnClickListener {
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

        findViewById<LinearLayout>(R.id.profileNavHome).setOnClickListener {
            finish()
        }

        findViewById<LinearLayout>(R.id.profileNavNotification).setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }
    }
}

package com.example.smart_steward

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class NotificationActivity : AppCompatActivity() {
    private lateinit var navBadge: TextView
    private lateinit var advisoryCard: LinearLayout
    private lateinit var ackCard: LinearLayout
    private lateinit var resolvedCard: LinearLayout
    private lateinit var advisoryDot: android.view.View
    private lateinit var ackDot: android.view.View
    private lateinit var resolvedDot: android.view.View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.notificationRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        navBadge = findViewById(R.id.notificationNavBadge)
        advisoryCard = findViewById(R.id.notifCardAdvisory)
        ackCard = findViewById(R.id.notifCardAcknowledged)
        resolvedCard = findViewById(R.id.notifCardResolved)
        advisoryDot = findViewById(R.id.notifDotAdvisory)
        ackDot = findViewById(R.id.notifDotAcknowledged)
        resolvedDot = findViewById(R.id.notifDotResolved)

        findViewById<ImageView>(R.id.notificationBackButton).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.notificationMarkAllReadButton).setOnClickListener {
            advisoryDot.visibility = android.view.View.GONE
            ackDot.visibility = android.view.View.GONE
            resolvedDot.visibility = android.view.View.GONE
            navBadge.visibility = android.view.View.GONE
            Toast.makeText(this, getString(R.string.notif_mark_all_read_done), Toast.LENGTH_SHORT).show()
        }

        findViewById<TextView>(R.id.notifAdvisoryDismissButton).setOnClickListener {
            dismissCard(advisoryCard, advisoryDot)
        }
        findViewById<TextView>(R.id.notifAdvisoryViewMapButton).setOnClickListener {
            openMapFor("Talamban, Cebu City")
        }

        findViewById<TextView>(R.id.notifAckDismissButton).setOnClickListener {
            dismissCard(ackCard, ackDot)
        }
        findViewById<TextView>(R.id.notifAckTrackButton).setOnClickListener {
            startActivity(Intent(this, MyActivityActivity::class.java))
        }

        findViewById<TextView>(R.id.notifResolvedDismissButton).setOnClickListener {
            dismissCard(resolvedCard, resolvedDot)
        }
        findViewById<TextView>(R.id.notifResolvedActionButton).setOnClickListener {
            Toast.makeText(this, getString(R.string.notif_resolved_desc_line), Toast.LENGTH_SHORT).show()
        }

        findViewById<LinearLayout>(R.id.notificationNavHome).setOnClickListener {
            startActivity(
                Intent(this, DashboardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }

        findViewById<LinearLayout>(R.id.notificationNavActivity).setOnClickListener {
            startActivity(Intent(this, MyActivityActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.notificationNavNotification).setOnClickListener { }

        findViewById<LinearLayout>(R.id.notificationNavProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.notificationCameraFab).setOnClickListener {
            startActivity(
                Intent(this, DashboardActivity::class.java)
                    .putExtra(DashboardActivity.EXTRA_OPEN_CAMERA, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
    }

    private fun dismissCard(card: LinearLayout, dot: android.view.View) {
        card.visibility = android.view.View.GONE
        dot.visibility = android.view.View.GONE
        if (advisoryDot.visibility != android.view.View.VISIBLE &&
            ackDot.visibility != android.view.View.VISIBLE &&
            resolvedDot.visibility != android.view.View.VISIBLE
        ) {
            navBadge.visibility = android.view.View.GONE
        }
    }

    private fun openMapFor(query: String) {
        val uri = Uri.parse("geo:0,0?q=${Uri.encode(query)}")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }
}

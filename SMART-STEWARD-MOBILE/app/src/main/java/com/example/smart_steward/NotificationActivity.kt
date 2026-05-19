package com.example.smart_steward

import android.content.Intent
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
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class NotificationActivity : AppCompatActivity() {

    private lateinit var navBadge: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var adapter: NotificationsListAdapter

    private var inboxListener: ListenerRegistration? = null
    private var reportsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.notificationRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        navBadge = findViewById(R.id.notificationNavBadge)
        recycler = findViewById(R.id.notificationRecycler)
        empty = findViewById(R.id.notificationEmpty)

        adapter = NotificationsListAdapter { item ->
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@NotificationsListAdapter
            CitizenNotificationsRepository.markRead(uid, item.id)
            if (item.reportId.isNotBlank()) {
                startActivity(
                    Intent(this, ReportHistoryActivity::class.java)
                        .putExtra(ReportHistoryActivity.EXTRA_FOCUS_REPORT_ID, item.reportId)
                )
            }
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<ImageView>(R.id.notificationBackButton).setOnClickListener {
            finish()
        }

        findViewById<TextView>(R.id.notificationMarkAllReadButton).setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) return@setOnClickListener
            CitizenNotificationsRepository.markAllRead(
                uid,
                onDone = {
                    runOnUiThread {
                        Toast.makeText(
                            this,
                            getString(R.string.notif_mark_all_read_done),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                },
                onError = { msg ->
                    runOnUiThread {
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                    }
                }
            )
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

        findViewById<LinearLayout>(R.id.notificationNavHistory).setOnClickListener {
            startActivity(Intent(this, ReportHistoryActivity::class.java))
            finish()
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

    override fun onStart() {
        super.onStart()
        attachListeners()
    }

    override fun onStop() {
        super.onStop()
        detachListeners()
    }

    private fun attachListeners() {
        detachListeners()
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            adapter.submit(emptyList())
            empty.visibility = View.VISIBLE
            empty.setText(R.string.notif_empty_signed_out)
            recycler.visibility = View.GONE
            navBadge.visibility = View.GONE
            return
        }

        empty.setText(R.string.notif_empty_signed_in)

        inboxListener = CitizenNotificationsRepository.watchInbox(
            uid,
            onUpdate = { list ->
                val rows = NotificationListRows.build(
                    list,
                    getString(R.string.notif_section_new),
                    getString(R.string.notif_section_earlier)
                )
                adapter.submit(rows)
                val unread = list.count { !it.read }
                navBadge.visibility = if (unread > 0) View.VISIBLE else View.GONE
                if (unread > 0) {
                    navBadge.text = if (unread > 99) "99+" else unread.toString()
                }
                val hasCards = list.isNotEmpty()
                empty.visibility = if (hasCards) View.GONE else View.VISIBLE
                recycler.visibility = if (hasCards) View.VISIBLE else View.GONE
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        )

        reportsListener = UserReportsRepository.watchReportsForUser(
            uid,
            onUpdate = { reports ->
                ReportStatusNotificationSync.sync(applicationContext, uid, reports)
            },
            onError = { /* non-fatal for notifications screen */ }
        )
    }

    private fun detachListeners() {
        inboxListener?.remove()
        inboxListener = null
        reportsListener?.remove()
        reportsListener = null
    }
}

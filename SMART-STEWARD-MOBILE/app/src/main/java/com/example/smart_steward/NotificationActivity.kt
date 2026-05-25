package com.example.smart_steward

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration

class NotificationActivity : AppCompatActivity() {

    companion object {
        /**
         * When the activity is launched from a system-tray notification the
         * tapped report id is passed via this extra. We route through
         * [ReportRouter] to open the corresponding [ReportReceiptDialog]
         * once Firestore has resolved the report.
         */
        const val EXTRA_OPEN_REPORT_ID = "extra_open_report_id"
    }

    private lateinit var navBadge: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var markAllButton: TextView
    private lateinit var clearAllButton: TextView
    private lateinit var clearAllFooter: LinearLayout
    private lateinit var adapter: NotificationsListAdapter

    private var inboxListener: ListenerRegistration? = null
    private var reportsListener: ListenerRegistration? = null

    private val requestPostNotifications = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* Result ignored — we'll simply skip system pushes if denied. */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_notification)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.notificationRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        navBadge = findViewById(R.id.bottomNavBadge)
        recycler = findViewById(R.id.notificationRecycler)
        empty = findViewById(R.id.notificationEmpty)

        adapter = NotificationsListAdapter { item ->
            val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return@NotificationsListAdapter
            CitizenNotificationsRepository.markRead(uid, item.id)
            ReportRouter.openReport(this, item.reportId)
        }
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        findViewById<ImageView>(R.id.notificationBackButton).setOnClickListener {
            finish()
        }

        findViewById<View>(R.id.notificationHeaderProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        ProfileInitials.bindDefaultAvatar(findViewById(R.id.notificationHeaderProfileAvatar))

        clearAllFooter = findViewById(R.id.notificationClearAllFooter)
        clearAllButton = findViewById(R.id.notificationClearAllButton)
        clearAllButton.setOnClickListener { confirmClearAll() }

        maybeRequestPostNotificationsPermission()
        handleOpenReportExtra(intent)

        markAllButton = findViewById(R.id.notificationMarkAllReadButton)
        markAllButton.setOnClickListener {
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            if (uid.isNullOrBlank()) return@setOnClickListener
            val markingUnread =
                markAllButton.text == getString(R.string.notif_mark_all_unread)
            if (markingUnread) {
                CitizenNotificationsRepository.markAllUnread(
                    uid,
                    onDone = {
                        runOnUiThread {
                            Toast.makeText(
                                this,
                                getString(R.string.notif_mark_all_unread_done),
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
            } else {
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
        }

        MainBottomNav.setup(this, MainBottomNavTab.NOTIFICATIONS)
    }

    override fun onResume() {
        super.onResume()
        MainBottomNav.updateBadge(this)
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
            if (::clearAllFooter.isInitialized) clearAllFooter.visibility = View.GONE
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
                updateMarkAllButton(unread, list.isNotEmpty())
                navBadge.visibility = if (unread > 0) View.VISIBLE else View.GONE
                if (unread > 0) {
                    navBadge.text = if (unread > 99) "99+" else unread.toString()
                }
                val hasCards = list.isNotEmpty()
                empty.visibility = if (hasCards) View.GONE else View.VISIBLE
                recycler.visibility = if (hasCards) View.VISIBLE else View.GONE
                clearAllFooter.visibility = if (hasCards) View.VISIBLE else View.GONE
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

    private fun confirmClearAll() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(getString(R.string.notif_clear_all_confirm_title))
            .setMessage(getString(R.string.notif_clear_all_confirm_message))
            .setNegativeButton(getString(R.string.notif_clear_all_confirm_no), null)
            .setPositiveButton(getString(R.string.notif_clear_all_confirm_yes)) { _, _ ->
                performClearAll(uid)
            }
            .show()
    }

    private fun performClearAll(uid: String) {
        clearAllButton.isEnabled = false
        clearAllButton.alpha = 0.5f
        CitizenNotificationsRepository.clearAll(
            userId = uid,
            onDone = {
                runOnUiThread {
                    if (::clearAllButton.isInitialized) {
                        clearAllButton.isEnabled = true
                        clearAllButton.alpha = 1f
                    }
                    Toast.makeText(
                        this,
                        getString(R.string.notif_clear_all_done),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onError = { msg ->
                runOnUiThread {
                    if (::clearAllButton.isInitialized) {
                        clearAllButton.isEnabled = true
                        clearAllButton.alpha = 1f
                    }
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            }
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Activity is set to singleTop in the manifest, so a fresh tap on a
        // system-tray notification arrives here instead of recreating the
        // activity. Apply the new extra without restarting.
        setIntent(intent)
        handleOpenReportExtra(intent)
    }

    private fun handleOpenReportExtra(intent: Intent?) {
        val reportId = intent
            ?.getStringExtra(EXTRA_OPEN_REPORT_ID)
            ?.trim()
            .orEmpty()
        if (reportId.isEmpty()) return
        // Clear the extra so a config change (rotation, theme switch, etc.)
        // does not re-open the same report.
        intent?.removeExtra(EXTRA_OPEN_REPORT_ID)
        ReportRouter.openReport(this, reportId)
    }

    private fun maybeRequestPostNotificationsPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) return
        requestPostNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private fun updateMarkAllButton(unreadCount: Int, hasNotifications: Boolean) {
        if (!::markAllButton.isInitialized) return
        when {
            !hasNotifications -> {
                markAllButton.isEnabled = false
                markAllButton.alpha = 0.5f
                markAllButton.setText(R.string.notif_mark_all_read)
            }
            unreadCount > 0 -> {
                markAllButton.isEnabled = true
                markAllButton.alpha = 1f
                markAllButton.setText(R.string.notif_mark_all_read)
            }
            else -> {
                markAllButton.isEnabled = true
                markAllButton.alpha = 1f
                markAllButton.setText(R.string.notif_mark_all_unread)
            }
        }
    }
}

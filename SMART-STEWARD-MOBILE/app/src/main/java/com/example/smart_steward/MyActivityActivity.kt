package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.graphics.Typeface
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MyActivityActivity : AppCompatActivity() {

    companion object {
        /** When set, scrolls the report list to this report id after data loads. */
        const val EXTRA_FOCUS_REPORT_ID = "focus_report_id"
    }

    private var pendingFocusReportId: String? = null

    private enum class Filter {
        ALL,
        PENDING,
        IN_PROGRESS,
        RESOLVED,
        TRENDING
    }

    private enum class FilterDimension {
        STATUS,
        DATE
    }

    private var allReports: List<UserReport> = emptyList()
    private var filter = Filter.ALL
    private var filterDimension = FilterDimension.STATUS
    private var newestFirst = true
    private lateinit var adapter: MyActivityReportsAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var statTotal: TextView
    private lateinit var statPending: TextView
    private lateinit var statResolved: TextView
    private lateinit var dimensionSpinner: Spinner
    private lateinit var valueSpinner: Spinner
    private var spinnerSkipCallback = false
    private var reportsListener: ListenerRegistration? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_activity)

        pendingFocusReportId = intent.getStringExtra(EXTRA_FOCUS_REPORT_ID)?.trim()?.takeIf { it.isNotEmpty() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.myActivityRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageView>(R.id.myActivityBack).setOnClickListener { finish() }

        statTotal = findViewById(R.id.myActivityStatTotal)
        statPending = findViewById(R.id.myActivityStatPending)
        statResolved = findViewById(R.id.myActivityStatResolved)
        empty = findViewById(R.id.myActivityEmpty)
        dimensionSpinner = findViewById(R.id.myActivityFilterDimensionSpinner)
        valueSpinner = findViewById(R.id.myActivityFilterValueSpinner)

        recycler = findViewById(R.id.myActivityRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = MyActivityReportsAdapter(
            onViewOnMap = { report ->
                startActivity(
                    Intent(this, DashboardActivity::class.java)
                        .putExtra(DashboardActivity.EXTRA_FOCUS_REPORT_ID, report.id)
                        .apply {
                            report.latitude?.let { putExtra(DashboardActivity.EXTRA_FOCUS_LAT, it) }
                            report.longitude?.let { putExtra(DashboardActivity.EXTRA_FOCUS_LNG, it) }
                        }
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                )
            },
            onTrackReport = { showReportDetailsDialog(it) }
        )
        recycler.adapter = adapter

        setupFilterSpinners()
        setupBottomNav()
        startReportsWatcher()
    }

    override fun onResume() {
        super.onResume()
        if (reportsListener == null) {
            startReportsWatcher()
        }
        updateNotificationBadge()
    }

    override fun onStop() {
        super.onStop()
        reportsListener?.remove()
        reportsListener = null
    }

    private fun setupFilterSpinners() {
        val dimLabels = listOf(
            getString(R.string.my_activity_filter_status),
            getString(R.string.my_activity_filter_date)
        )
        dimensionSpinner.adapter = spinnerAdapter(dimLabels)
        dimensionSpinner.setSelection(filterDimension.ordinal)

        dimensionSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (spinnerSkipCallback) return
                filterDimension = FilterDimension.values()[position]
                bindValueSpinnerFromState()
                refreshList()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        valueSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (spinnerSkipCallback) return
                when (filterDimension) {
                    FilterDimension.STATUS -> {
                        filter = Filter.entries[position]
                    }
                    FilterDimension.DATE -> {
                        newestFirst = position == 0
                    }
                }
                refreshList()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        bindValueSpinnerFromState()
    }

    private fun bindValueSpinnerFromState() {
        spinnerSkipCallback = true
        when (filterDimension) {
            FilterDimension.STATUS -> {
                val labels = listOf(
                    getString(R.string.my_activity_chip_all),
                    getString(R.string.my_activity_pending),
                    getString(R.string.my_activity_in_progress),
                    getString(R.string.my_activity_resolved),
                    getString(R.string.my_activity_chip_trending)
                )
                valueSpinner.adapter = spinnerAdapter(labels)
                valueSpinner.setSelection(filter.ordinal.coerceIn(0, Filter.values().lastIndex))
            }
            FilterDimension.DATE -> {
                val labels = listOf(
                    getString(R.string.my_activity_newest_first),
                    getString(R.string.my_activity_oldest_first)
                )
                valueSpinner.adapter = spinnerAdapter(labels)
                valueSpinner.setSelection(if (newestFirst) 0 else 1)
            }
        }
        spinnerSkipCallback = false
    }

    private fun spinnerAdapter(items: List<String>): ArrayAdapter<String> {
        val adapter = ArrayAdapter(
            this,
            R.layout.spinner_item_my_activity,
            android.R.id.text1,
            items
        )
        adapter.setDropDownViewResource(R.layout.spinner_dropdown_my_activity)
        return adapter
    }

    private fun showReportDetailsDialog(report: UserReport) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_report_receipt, null)
        dialogView.findViewById<TextView>(R.id.receiptHeaderId).text = report.id
        val content = dialogView.findViewById<LinearLayout>(R.id.receiptContent)

        fun addRow(label: String, value: String, brandGreenValue: Boolean = false) {
            val row = layoutInflater.inflate(R.layout.item_receipt_row, content, false)
            row.findViewById<TextView>(R.id.receiptRowLabel).text = label
            val tv = row.findViewById<TextView>(R.id.receiptRowValue)
            tv.text = value.ifBlank { "—" }
            if (brandGreenValue) {
                tv.setTextColor(getColor(R.color.register_button_green))
                tv.setTypeface(tv.typeface, Typeface.NORMAL)
                tv.textSize = 14f
            }
            content.addView(row)
        }

        fun addDescription(label: String, body: String) {
            val block = layoutInflater.inflate(R.layout.item_receipt_description, content, false)
            block.findViewById<TextView>(R.id.receiptDescLabel).text = label
            block.findViewById<TextView>(R.id.receiptDescBody).text = body.ifBlank { "—" }
            content.addView(block)
        }

        val reportTypeDisplay = report.incidentType.trim().ifBlank { "—" }
        val dateFmt = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        val submitted = report.submittedAt?.let { dateFmt.format(it) } ?: "—"
        val location = report.locationLine.removePrefix("Location:").trim().ifBlank { "—" }

        addRow(getString(R.string.my_activity_detail_report_id), report.id)
        addRow(getString(R.string.my_activity_detail_report_type), reportTypeDisplay)
        addRow(getString(R.string.my_activity_detail_date_submitted), submitted)
        addRow(getString(R.string.my_activity_detail_location), location)
        addDescription(getString(R.string.my_activity_detail_description), report.description)

        val videoUrl = report.videoUrl.trim()
        val photoUrl = report.photoUrl.trim()
        val attachmentSummary = when {
            videoUrl.isNotEmpty() -> getString(R.string.my_activity_attachment_video)
            photoUrl.isNotEmpty() -> getString(R.string.my_activity_attachment_photo)
            else -> getString(R.string.my_activity_attachment_none)
        }
        addRow(getString(R.string.my_activity_detail_attachment), attachmentSummary)

        val density = resources.displayMetrics.density
        when {
            videoUrl.isNotEmpty() -> {
                val thumbHeight = (180 * density).toInt()
                val hMargin = (16 * density).toInt()
                val frame = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        thumbHeight
                    ).apply {
                        setMargins(hMargin, 0, hMargin, (8 * density).toInt())
                    }
                    isClickable = true
                    isFocusable = true
                }
                val thumb = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    val preview = photoUrl.ifBlank { videoUrl }
                    load(preview)
                }
                val playSize = (44 * density).toInt()
                val playPad = (8 * density).toInt()
                val playOverlay = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(playSize, playSize).apply {
                        gravity = Gravity.CENTER
                    }
                    setBackgroundResource(R.drawable.bg_play_circle)
                    setImageResource(android.R.drawable.ic_media_play)
                    imageTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.white))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(playPad, playPad, playPad, playPad)
                    contentDescription = getString(R.string.play_video)
                    isClickable = false
                    isFocusable = false
                }
                val openVideo = View.OnClickListener {
                    MediaPlayback.openRemoteVideo(this@MyActivityActivity, videoUrl)
                }
                frame.setOnClickListener(openVideo)
                frame.addView(thumb)
                frame.addView(playOverlay)
                content.addView(frame)
                val caption = TextView(this).apply {
                    text = getString(R.string.my_activity_attachment_video)
                    setTextColor(getColor(R.color.activity_muted))
                    textSize = 12f
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(
                        (16 * density).toInt(),
                        0,
                        (16 * density).toInt(),
                        (12 * density).toInt()
                    )
                }
                content.addView(caption)
            }
            photoUrl.isNotEmpty() -> {
                val img = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (180 * density).toInt()
                    ).apply {
                        val m = (16 * density).toInt()
                        setMargins(m, 0, m, (8 * density).toInt())
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    load(photoUrl)
                    setOnClickListener {
                        MediaPlayback.openRemoteImage(this@MyActivityActivity, photoUrl)
                    }
                }
                content.addView(img)
            }
        }

        addRow(
            getString(R.string.my_activity_detail_assigned),
            report.assignedAgency.trim().ifBlank { "—" },
            brandGreenValue = true
        )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialogView.findViewById<TextView>(R.id.receiptCloseButton).setOnClickListener {
            dialog.dismiss()
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun startReportsWatcher() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            empty.text = getString(R.string.my_activity_sign_in)
            empty.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            statTotal.text = "0"
            statPending.text = "0"
            statResolved.text = "0"
            allReports = emptyList()
            return
        }
        reportsListener?.remove()
        reportsListener = UserReportsRepository.watchReportsForUser(
            uid,
            onUpdate = { list ->
                allReports = list
                updateStats(list)
                refreshList()
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun updateStats(list: List<UserReport>) {
        statTotal.text = list.size.toString()
        val open = list.count {
            it.status == ReportStatusUi.PENDING || it.status == ReportStatusUi.IN_PROGRESS
        }
        statPending.text = open.toString()
        statResolved.text = list.count { it.status == ReportStatusUi.RESOLVED }.toString()
    }

    private fun refreshList() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return

        var list = when (filterDimension) {
            FilterDimension.DATE -> allReports
            FilterDimension.STATUS -> when (filter) {
                Filter.ALL -> allReports
                Filter.PENDING -> allReports.filter { it.status == ReportStatusUi.PENDING }
                Filter.IN_PROGRESS -> allReports.filter { it.status == ReportStatusUi.IN_PROGRESS }
                Filter.RESOLVED -> allReports.filter { it.status == ReportStatusUi.RESOLVED }
                Filter.TRENDING -> {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.DAY_OF_YEAR, -14)
                    val cutoff = cal.timeInMillis
                    allReports.filter { (it.submittedAt?.time ?: 0L) >= cutoff }
                }
            }
        }
        list = if (newestFirst) {
            list.sortedByDescending { it.submittedAt?.time ?: 0L }
        } else {
            list.sortedBy { it.submittedAt?.time ?: 0L }
        }
        adapter.submitList(list)

        pendingFocusReportId?.let { id ->
            val pos = list.indexOfFirst { it.id == id }
            if (pos >= 0) {
                recycler.scrollToPosition(pos)
                pendingFocusReportId = null
            }
        }

        val showEmpty = list.isEmpty()
        empty.visibility = if (showEmpty) View.VISIBLE else View.GONE
        recycler.visibility = if (showEmpty) View.GONE else View.VISIBLE
        when {
            allReports.isEmpty() -> empty.setText(R.string.my_activity_no_reports)
            showEmpty -> empty.setText(R.string.my_activity_no_filtered)
        }
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.myActivityNavHome).setOnClickListener {
            startActivity(
                Intent(this, DashboardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
        findViewById<LinearLayout>(R.id.myActivityNavActivity).setOnClickListener { }
        findViewById<LinearLayout>(R.id.myActivityNavNotification).setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.myActivityNavProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<FrameLayout>(R.id.myActivityCameraFab).setOnClickListener {
            startActivity(
                Intent(this, DashboardActivity::class.java)
                    .putExtra(DashboardActivity.EXTRA_OPEN_CAMERA, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
    }

    private fun updateNotificationBadge() {
        val badge = findViewById<TextView>(R.id.myActivityNavBadge)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            badge.visibility = View.GONE
            return
        }
        CitizenNotificationsRepository.countUnread(uid, onResult = { unread ->
            runOnUiThread {
                badge.visibility = if (unread > 0) View.VISIBLE else View.GONE
                if (unread > 0) {
                    badge.text = if (unread > 99) "99+" else unread.toString()
                }
            }
        })
    }
}

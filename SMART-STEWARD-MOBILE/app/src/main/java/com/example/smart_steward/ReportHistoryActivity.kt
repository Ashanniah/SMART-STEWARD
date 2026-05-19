package com.example.smart_steward

import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ReportHistoryActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_FOCUS_REPORT_ID = "focus_report_id"
    }

    private enum class StatusFilter {
        ALL,
        RESOLVED,
        PENDING,
        IN_PROGRESS,
        REJECTED
    }

    private var pendingFocusReportId: String? = null
    private var allReports: List<UserReport> = emptyList()
    private var statusFilter = StatusFilter.ALL
    private var searchQuery = ""
    private var newestFirst = true
    private lateinit var adapter: ReportHistoryAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var searchInput: EditText
    private var reportsListener: ListenerRegistration? = null

    private lateinit var chipAll: TextView
    private lateinit var chipResolved: TextView
    private lateinit var chipPending: TextView
    private lateinit var chipInProgress: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_report_history)

        pendingFocusReportId = intent.getStringExtra(EXTRA_FOCUS_REPORT_ID)?.trim()?.takeIf { it.isNotEmpty() }

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.reportHistoryRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageView>(R.id.reportHistoryBack).setOnClickListener { finish() }

        bindStatCard(
            R.id.reportHistoryStatTotalBlock,
            accentColor = R.color.activity_accent_line,
            label = getString(R.string.my_activity_total).uppercase(Locale.getDefault())
        ) { statusFilter = StatusFilter.ALL; refreshChipSelection(); refreshList() }

        bindStatCard(
            R.id.reportHistoryStatResolvedBlock,
            accentColor = R.color.activity_resolved_green,
            label = getString(R.string.my_activity_resolved).uppercase(Locale.getDefault())
        ) { statusFilter = StatusFilter.RESOLVED; refreshChipSelection(); refreshList() }

        bindStatCard(
            R.id.reportHistoryStatPendingBlock,
            accentColor = R.color.activity_progress_blue,
            label = getString(R.string.my_activity_pending).uppercase(Locale.getDefault())
        ) { statusFilter = StatusFilter.PENDING; refreshChipSelection(); refreshList() }

        bindStatCard(
            R.id.reportHistoryStatRejectedBlock,
            accentColor = R.color.activity_rejected_gray,
            label = getString(R.string.my_activity_rejected).uppercase(Locale.getDefault())
        ) { statusFilter = StatusFilter.REJECTED; refreshChipSelection(); refreshList() }

        empty = findViewById(R.id.reportHistoryEmpty)
        searchInput = findViewById(R.id.reportHistorySearchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                refreshList()
            }
        })

        chipAll = findViewById(R.id.reportHistoryChipAll)
        chipResolved = findViewById(R.id.reportHistoryChipResolved)
        chipPending = findViewById(R.id.reportHistoryChipPending)
        chipInProgress = findViewById(R.id.reportHistoryChipInProgress)
        chipAll.setOnClickListener { applyChipFilter(StatusFilter.ALL) }
        chipResolved.setOnClickListener { applyChipFilter(StatusFilter.RESOLVED) }
        chipPending.setOnClickListener { applyChipFilter(StatusFilter.PENDING) }
        chipInProgress.setOnClickListener { applyChipFilter(StatusFilter.IN_PROGRESS) }
        refreshChipSelection()

        findViewById<ImageView>(R.id.reportHistorySortButton).setOnClickListener { anchor ->
            val menu = PopupMenu(this, anchor)
            menu.menu.add(0, 0, 0, R.string.my_activity_newest_first)
            menu.menu.add(0, 1, 1, R.string.my_activity_oldest_first)
            menu.setOnMenuItemClickListener { item ->
                newestFirst = item.itemId == 0
                refreshList()
                true
            }
            menu.show()
        }

        recycler = findViewById(R.id.reportHistoryRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = ReportHistoryAdapter(onViewReport = { showReportDetailsDialog(it) })
        recycler.adapter = adapter

        setupBottomNav()
        startReportsWatcher()
    }

    override fun onResume() {
        super.onResume()
        if (reportsListener == null) startReportsWatcher()
        updateNotificationBadge()
    }

    override fun onStop() {
        super.onStop()
        reportsListener?.remove()
        reportsListener = null
    }

    private fun bindStatCard(
        includeId: Int,
        accentColor: Int,
        label: String,
        onClick: () -> Unit
    ) {
        val root = findViewById<View>(includeId)
        root.findViewById<TextView>(R.id.historyStatLabel).text = label
        root.findViewById<View>(R.id.historyStatAccent)
            .setBackgroundColor(ContextCompat.getColor(this, accentColor))
        root.setOnClickListener { onClick() }
    }

    private fun updateStatValues(list: List<UserReport>) {
        setStatValue(R.id.reportHistoryStatTotalBlock, list.size.toString())
        setStatValue(
            R.id.reportHistoryStatResolvedBlock,
            list.count { it.status == ReportStatusUi.RESOLVED }.toString()
        )
        setStatValue(
            R.id.reportHistoryStatPendingBlock,
            list.count { it.status == ReportStatusUi.PENDING }.toString()
        )
        setStatValue(
            R.id.reportHistoryStatRejectedBlock,
            list.count { it.status == ReportStatusUi.REJECTED }.toString()
        )
    }

    private fun setStatValue(includeId: Int, value: String) {
        findViewById<View>(includeId).findViewById<TextView>(R.id.historyStatValue).text = value
    }

    private fun applyChipFilter(filter: StatusFilter) {
        statusFilter = filter
        refreshChipSelection()
        refreshList()
    }

    private fun refreshChipSelection() {
        val selectedBg = R.drawable.bg_history_chip_selected
        val unselectedBg = R.drawable.bg_history_chip_unselected
        val selectedText = ContextCompat.getColor(this, R.color.white)
        val unselectedText = ContextCompat.getColor(this, R.color.register_button_green)

        fun style(chip: TextView, selected: Boolean) {
            chip.setBackgroundResource(if (selected) selectedBg else unselectedBg)
            chip.setTextColor(if (selected) selectedText else unselectedText)
        }

        style(chipAll, statusFilter == StatusFilter.ALL)
        style(chipResolved, statusFilter == StatusFilter.RESOLVED)
        style(chipPending, statusFilter == StatusFilter.PENDING)
        style(chipInProgress, statusFilter == StatusFilter.IN_PROGRESS)
    }

    private fun showReportDetailsDialog(report: UserReport) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_report_receipt, null)
        dialogView.findViewById<TextView>(R.id.receiptHeaderId).text = report.displayReportRef()
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

        addRow(getString(R.string.my_activity_detail_report_id), report.displayReportRef())
        addRow(getString(R.string.my_activity_detail_report_type), reportTypeDisplay)
        addRow(getString(R.string.my_activity_detail_date_submitted), submitted)
        addRow(getString(R.string.my_activity_detail_location), location)
        addRow(getString(R.string.my_activity_detail_status), report.statusLabel)
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
                    load(photoUrl.ifBlank { videoUrl })
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
                    MediaPlayback.openRemoteVideo(this@ReportHistoryActivity, videoUrl)
                }
                frame.setOnClickListener(openVideo)
                frame.addView(thumb)
                frame.addView(playOverlay)
                content.addView(frame)
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
                        MediaPlayback.openRemoteImage(this@ReportHistoryActivity, photoUrl)
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

        val dialog = AlertDialog.Builder(this).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.receiptCloseButton).setOnClickListener { dialog.dismiss() }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun startReportsWatcher() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            empty.text = getString(R.string.my_activity_sign_in)
            empty.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            updateStatValues(emptyList())
            allReports = emptyList()
            return
        }
        reportsListener?.remove()
        reportsListener = UserReportsRepository.watchReportsForUser(
            uid,
            onUpdate = { list ->
                allReports = list
                updateStatValues(list)
                refreshList()
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun refreshList() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return

        var list = when (statusFilter) {
            StatusFilter.ALL -> allReports
            StatusFilter.RESOLVED -> allReports.filter { it.status == ReportStatusUi.RESOLVED }
            StatusFilter.PENDING -> allReports.filter { it.status == ReportStatusUi.PENDING }
            StatusFilter.IN_PROGRESS -> allReports.filter { it.status == ReportStatusUi.IN_PROGRESS }
            StatusFilter.REJECTED -> allReports.filter { it.status == ReportStatusUi.REJECTED }
        }

        val q = searchQuery.trim().lowercase(Locale.getDefault())
        if (q.isNotEmpty()) {
            list = list.filter { report ->
                val haystack = listOf(
                    report.incidentType,
                    report.locationLine,
                    report.assignedAgency,
                    report.description,
                    report.statusLabel,
                    report.id,
                    report.displayReportRef()
                ).joinToString(" ").lowercase(Locale.getDefault())
                haystack.contains(q)
            }
        }

        list = if (newestFirst) {
            list.sortedByDescending { it.submittedAt?.time ?: 0L }
        } else {
            list.sortedBy { it.submittedAt?.time ?: 0L }
        }

        val grouped = buildGroupedList(list)
        adapter.submitItems(grouped)

        pendingFocusReportId?.let { id ->
            val index = grouped.indexOfFirst {
                it is ReportHistoryListItem.ReportRow && it.report.id == id
            }
            if (index >= 0) {
                recycler.scrollToPosition(index)
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

    private fun buildGroupedList(reports: List<UserReport>): List<ReportHistoryListItem> {
        if (reports.isEmpty()) return emptyList()
        val monthFmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        val monthKeyFmt = SimpleDateFormat("yyyy-MM", Locale.US)
        val byMonth = linkedMapOf<String, MutableList<UserReport>>()
        val monthLabels = linkedMapOf<String, String>()

        for (report in reports) {
            val date = report.submittedAt ?: Date(0)
            val key = monthKeyFmt.format(date)
            monthLabels.getOrPut(key) { monthFmt.format(date).uppercase(Locale.getDefault()) }
            byMonth.getOrPut(key) { mutableListOf() }.add(report)
        }

        val items = ArrayList<ReportHistoryListItem>()
        for ((key, monthReports) in byMonth) {
            val label = monthLabels[key].orEmpty()
            items.add(ReportHistoryListItem.MonthHeader(label, monthReports.size))
            monthReports.forEach { items.add(ReportHistoryListItem.ReportRow(it)) }
        }
        return items
    }

    private fun setupBottomNav() {
        findViewById<LinearLayout>(R.id.reportHistoryNavHome).setOnClickListener {
            startActivity(
                Intent(this, DashboardActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
        findViewById<LinearLayout>(R.id.reportHistoryNavActivity).setOnClickListener {
            startActivity(Intent(this, MyActivityActivity::class.java))
            finish()
        }
        findViewById<LinearLayout>(R.id.reportHistoryNavHistory).setOnClickListener { }
        findViewById<LinearLayout>(R.id.reportHistoryNavNotification).setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }
        findViewById<LinearLayout>(R.id.reportHistoryNavProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        findViewById<FrameLayout>(R.id.reportHistoryCameraFab).setOnClickListener {
            startActivity(
                Intent(this, DashboardActivity::class.java)
                    .putExtra(DashboardActivity.EXTRA_OPEN_CAMERA, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }
    }

    private fun updateNotificationBadge() {
        val badge = findViewById<TextView>(R.id.reportHistoryNavBadge)
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

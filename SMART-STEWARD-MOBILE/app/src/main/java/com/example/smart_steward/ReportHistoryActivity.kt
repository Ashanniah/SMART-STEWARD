package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
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
        REJECTED
    }

    private var pendingFocusReportId: String? = null
    private var allReports: List<UserReport> = emptyList()
    private var statusFilter = StatusFilter.ALL
    private var searchQuery = ""
    private val newestFirst = true
    private lateinit var adapter: ReportHistoryAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var searchInput: EditText
    private lateinit var statusSpinner: Spinner
    private var spinnerSkipCallback = false
    private var reportsListener: ListenerRegistration? = null

    private fun archiveReports(): List<UserReport> = ReportStatusColors.filterArchiveReports(allReports)

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

        findViewById<View>(R.id.reportHistoryHeaderProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        ProfileInitials.bindDefaultAvatar(findViewById(R.id.reportHistoryHeaderProfileAvatar))

        bindStatCard(
            R.id.reportHistoryStatTotalBlock,
            iconRes = R.drawable.ic_stat_total,
            label = getString(R.string.history_stat_archived)
        ) { applyStatusFilter(StatusFilter.ALL) }

        bindStatCard(
            R.id.reportHistoryStatResolvedBlock,
            iconRes = R.drawable.ic_stat_resolved,
            label = getString(R.string.my_activity_resolved)
        ) { applyStatusFilter(StatusFilter.RESOLVED) }

        bindStatCard(
            R.id.reportHistoryStatRejectedBlock,
            iconRes = R.drawable.ic_stat_rejected,
            label = getString(R.string.my_activity_rejected)
        ) { applyStatusFilter(StatusFilter.REJECTED) }

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

        statusSpinner = findViewById(R.id.reportHistoryFilterStatusSpinner)
        setupStatusFilterSpinner()

        recycler = findViewById(R.id.reportHistoryRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = ReportHistoryAdapter(onViewReport = { ReportReceiptDialog.show(this, it) })
        recycler.adapter = adapter

        MainBottomNav.setup(this, MainBottomNavTab.HISTORY)
        startReportsWatcher()
    }

    override fun onResume() {
        super.onResume()
        if (reportsListener == null) startReportsWatcher()
        MainBottomNav.updateBadge(this)
    }

    override fun onStop() {
        super.onStop()
        reportsListener?.remove()
        reportsListener = null
    }

    private fun setupStatusFilterSpinner() {
        val labels = listOf(
            getString(R.string.history_filter_all),
            getString(R.string.my_activity_resolved),
            getString(R.string.my_activity_rejected)
        )
        statusSpinner.adapter = spinnerAdapter(labels)
        statusSpinner.setSelection(filterToSpinnerIndex(statusFilter))

        statusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (spinnerSkipCallback) return
                applyStatusFilter(spinnerIndexToFilter(position))
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun spinnerIndexToFilter(index: Int): StatusFilter = when (index) {
        1 -> StatusFilter.RESOLVED
        2 -> StatusFilter.REJECTED
        else -> StatusFilter.ALL
    }

    private fun filterToSpinnerIndex(value: StatusFilter): Int = when (value) {
        StatusFilter.RESOLVED -> 1
        StatusFilter.REJECTED -> 2
        StatusFilter.ALL -> 0
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

    private fun bindStatCard(includeId: Int, iconRes: Int, label: String, onClick: () -> Unit) {
        val root = findViewById<View>(includeId)
        root.findViewById<ImageView>(R.id.myActivityStatIcon).setImageResource(iconRes)
        root.findViewById<TextView>(R.id.myActivityStatLabel).text = label
        root.setOnClickListener { onClick() }
    }

    private fun applyStatusFilter(filter: StatusFilter) {
        statusFilter = filter
        syncSpinnerToFilter()
        refreshList()
    }

    private fun syncSpinnerToFilter() {
        val index = filterToSpinnerIndex(statusFilter)
        if (statusSpinner.selectedItemPosition != index) {
            spinnerSkipCallback = true
            statusSpinner.setSelection(index)
            spinnerSkipCallback = false
        }
    }

    private fun updateStatValues(archive: List<UserReport>) {
        setStatValue(R.id.reportHistoryStatTotalBlock, archive.size.toString())
        setStatValue(
            R.id.reportHistoryStatResolvedBlock,
            archive.count { it.status == ReportStatusUi.RESOLVED }.toString()
        )
        setStatValue(
            R.id.reportHistoryStatRejectedBlock,
            archive.count { it.status == ReportStatusUi.REJECTED }.toString()
        )
    }

    private fun setStatValue(includeId: Int, value: String) {
        findViewById<View>(includeId).findViewById<TextView>(R.id.myActivityStatValue).text = value
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
                ReportStatusNotificationSync.sync(applicationContext, uid, list)
                updateStatValues(archiveReports())
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

        var list = archiveReports()
        list = when (statusFilter) {
            StatusFilter.ALL -> list
            StatusFilter.RESOLVED -> list.filter { it.status == ReportStatusUi.RESOLVED }
            StatusFilter.REJECTED -> list.filter { it.status == ReportStatusUi.REJECTED }
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
                    report.displayReportRef(),
                    report.lastStatusNote
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
                val report = (grouped[index] as ReportHistoryListItem.ReportRow).report
                ReportReceiptDialog.show(this, report)
                pendingFocusReportId = null
            }
        }

        val showEmpty = list.isEmpty()
        empty.visibility = if (showEmpty) View.VISIBLE else View.GONE
        recycler.visibility = if (showEmpty) View.GONE else View.VISIBLE
        when {
            allReports.isEmpty() -> empty.setText(R.string.my_activity_no_reports)
            archiveReports().isEmpty() -> empty.setText(R.string.history_archive_empty)
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
}

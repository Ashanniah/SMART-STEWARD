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

    private var allReports: List<UserReport> = emptyList()
    private var filter = Filter.ALL
    private var searchQuery = ""
    private var newestFirst = true
    private lateinit var adapter: MyActivityReportsAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var searchInput: EditText
    private lateinit var statusSpinner: Spinner
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

        findViewById<View>(R.id.myActivityHeaderProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }
        ProfileInitials.bind(findViewById(R.id.myActivityHeaderProfileInitials))

        bindStatCard(
            R.id.myActivityStatTotalBlock,
            iconRes = R.drawable.ic_stat_total,
            label = getString(R.string.my_activity_total)
        ) { applyFilter(Filter.ALL) }

        bindStatCard(
            R.id.myActivityStatPendingBlock,
            iconRes = R.drawable.ic_stat_pending,
            label = getString(R.string.my_activity_pending)
        ) { applyFilter(Filter.PENDING) }

        bindStatCard(
            R.id.myActivityStatResolvedBlock,
            iconRes = R.drawable.ic_stat_resolved,
            label = getString(R.string.my_activity_resolved)
        ) { applyFilter(Filter.RESOLVED) }

        empty = findViewById(R.id.myActivityEmpty)
        searchInput = findViewById(R.id.myActivitySearchInput)
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                searchQuery = s?.toString().orEmpty()
                refreshList()
            }
        })

        statusSpinner = findViewById(R.id.myActivityFilterStatusSpinner)
        setupStatusFilterSpinner()

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
            onTrackReport = { ReportReceiptDialog.show(this, it) }
        )
        recycler.adapter = adapter

        MainBottomNav.setup(this, MainBottomNavTab.ACTIVITY)
        startReportsWatcher()
    }

    override fun onResume() {
        super.onResume()
        if (reportsListener == null) {
            startReportsWatcher()
        }
        MainBottomNav.updateBadge(this)
    }

    override fun onStop() {
        super.onStop()
        reportsListener?.remove()
        reportsListener = null
    }

    private fun setupStatusFilterSpinner() {
        val labels = listOf(
            getString(R.string.my_activity_select_status),
            getString(R.string.my_activity_pending),
            getString(R.string.my_activity_in_progress),
            getString(R.string.my_activity_resolved),
            getString(R.string.my_activity_chip_trending)
        )
        statusSpinner.adapter = spinnerAdapter(labels)
        statusSpinner.setSelection(filterToSpinnerIndex(filter))

        statusSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (spinnerSkipCallback) return
                filter = spinnerIndexToFilter(position)
                refreshList()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    /** Spinner index 0 = Select Status (show all). */
    private fun spinnerIndexToFilter(index: Int): Filter = when (index) {
        1 -> Filter.PENDING
        2 -> Filter.IN_PROGRESS
        3 -> Filter.RESOLVED
        4 -> Filter.TRENDING
        else -> Filter.ALL
    }

    private fun filterToSpinnerIndex(value: Filter): Int = when (value) {
        Filter.PENDING -> 1
        Filter.IN_PROGRESS -> 2
        Filter.RESOLVED -> 3
        Filter.TRENDING -> 4
        Filter.ALL -> 0
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

    private fun updateStats(list: List<UserReport>) {
        setStatValue(R.id.myActivityStatTotalBlock, list.size.toString())
        setStatValue(
            R.id.myActivityStatPendingBlock,
            list.count { it.status == ReportStatusUi.PENDING }.toString()
        )
        setStatValue(
            R.id.myActivityStatResolvedBlock,
            list.count { it.status == ReportStatusUi.RESOLVED }.toString()
        )
    }

    private fun setStatValue(includeId: Int, value: String) {
        findViewById<View>(includeId).findViewById<TextView>(R.id.myActivityStatValue).text = value
    }

    private fun applyFilter(newFilter: Filter) {
        filter = newFilter
        spinnerSkipCallback = true
        statusSpinner.setSelection(filterToSpinnerIndex(filter))
        spinnerSkipCallback = false
        refreshList()
    }

    private fun startReportsWatcher() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            empty.text = getString(R.string.my_activity_sign_in)
            empty.visibility = View.VISIBLE
            recycler.visibility = View.GONE
            updateStats(emptyList())
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

    private fun refreshList() {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) return

        var list = when (filter) {
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
}

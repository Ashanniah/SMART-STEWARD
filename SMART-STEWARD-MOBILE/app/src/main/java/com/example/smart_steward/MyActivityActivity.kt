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
            onTrackReport = { ReportReceiptDialog.show(this, it) }
        )
        recycler.adapter = adapter

        setupFilterSpinners()
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

}

package com.example.smart_steward

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
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
import java.util.Calendar
import java.util.LinkedHashMap

class MyActivityActivity : AppCompatActivity() {

    private enum class Filter {
        ALL,
        PENDING,
        IN_PROGRESS,
        RESOLVED,
        TRENDING
    }

    private var allReports: List<UserReport> = emptyList()
    private var filter = Filter.ALL
    private var newestFirst = true
    private lateinit var adapter: MyActivityReportsAdapter
    private lateinit var recycler: RecyclerView
    private lateinit var empty: TextView
    private lateinit var statTotal: TextView
    private lateinit var statPending: TextView
    private lateinit var statResolved: TextView
    private lateinit var sortLabel: TextView
    private lateinit var chipsRow: LinearLayout
    private val chipViews = LinkedHashMap<Filter, TextView>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_my_activity)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.myActivityRoot)) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        findViewById<ImageView>(R.id.myActivityBack).setOnClickListener { finish() }
        sortLabel = findViewById(R.id.myActivitySortLabel)
        findViewById<ImageView>(R.id.myActivitySortToggle).setOnClickListener {
            newestFirst = !newestFirst
            sortLabel.setText(
                if (newestFirst) R.string.my_activity_newest_first
                else R.string.my_activity_oldest_first
            )
            refreshList()
        }

        statTotal = findViewById(R.id.myActivityStatTotal)
        statPending = findViewById(R.id.myActivityStatPending)
        statResolved = findViewById(R.id.myActivityStatResolved)
        empty = findViewById(R.id.myActivityEmpty)
        chipsRow = findViewById(R.id.myActivityChipsRow)

        recycler = findViewById(R.id.myActivityRecycler)
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = MyActivityReportsAdapter { report ->
            Toast.makeText(
                this,
                getString(R.string.my_activity_track) + " #" + report.id.take(8),
                Toast.LENGTH_SHORT
            ).show()
        }
        recycler.adapter = adapter

        setupChips()
        setupBottomNav()
        loadReports()
    }

    override fun onResume() {
        super.onResume()
        loadReports()
    }

    private fun setupChips() {
        val labels = listOf(
            Filter.ALL to getString(R.string.my_activity_chip_all),
            Filter.PENDING to getString(R.string.my_activity_pending),
            Filter.IN_PROGRESS to getString(R.string.my_activity_in_progress),
            Filter.RESOLVED to getString(R.string.my_activity_resolved),
            Filter.TRENDING to getString(R.string.my_activity_chip_trending)
        )
        chipsRow.removeAllViews()
        chipViews.clear()
        val padV = (8 * resources.displayMetrics.density).toInt()
        val padH = (14 * resources.displayMetrics.density).toInt()
        val margin = (8 * resources.displayMetrics.density).toInt()
        labels.forEach { (f, label) ->
            val tv = TextView(this)
            tv.text = label
            tv.textSize = 12f
            tv.setPadding(padH, padV, padH, padV)
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.marginEnd = margin
            tv.layoutParams = lp
            tv.setOnClickListener {
                filter = f
                chipViews.forEach { (k, v) -> styleChip(v, k == filter) }
                refreshList()
            }
            chipViews[f] = tv
            chipsRow.addView(tv)
        }
        chipViews.forEach { (k, v) -> styleChip(v, k == filter) }
    }

    private fun styleChip(tv: TextView, selected: Boolean) {
        if (selected) {
            tv.setBackgroundResource(R.drawable.bg_activity_chip_selected)
            tv.setTextColor(getColor(R.color.white))
        } else {
            tv.setBackgroundResource(R.drawable.bg_activity_chip_unselected)
            tv.setTextColor(getColor(R.color.activity_title_bar))
        }
    }

    private fun loadReports() {
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
        UserReportsRepository.loadReportsForUser(
            uid,
            onResult = { list ->
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
        list = if (newestFirst) {
            list.sortedByDescending { it.submittedAt?.time ?: 0L }
        } else {
            list.sortedBy { it.submittedAt?.time ?: 0L }
        }
        adapter.submitList(list)

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
}

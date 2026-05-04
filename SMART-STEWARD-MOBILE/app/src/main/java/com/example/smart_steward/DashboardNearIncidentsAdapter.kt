package com.example.smart_steward

import android.content.res.ColorStateList
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class DashboardNearIncidentsAdapter(
    private val onTap: (UserReport) -> Unit
) : RecyclerView.Adapter<DashboardNearIncidentsAdapter.VH>() {

    private val items = ArrayList<UserReport>()
    private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    fun submitList(newItems: List<UserReport>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_dashboard_near_report, parent, false) as MaterialCardView
        return VH(view)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position], dateFmt, onTap)
    }

    class VH(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        fun bind(report: UserReport, fmt: SimpleDateFormat, onTap: (UserReport) -> Unit) {
            val ctx = card.context
            val title = report.displayTitle()
            val date = report.submittedAt?.let { fmt.format(it) } ?: "—"
            val meta = "${report.locationDisplay()} • $date"

            card.findViewById<TextView>(R.id.nearRowTitle).text = title
            card.findViewById<TextView>(R.id.nearRowMeta).text = meta

            val icon = card.findViewById<TextView>(R.id.nearRowTypeIcon)
            icon.text = when {
                title.contains("dump", true) -> "🗑️"
                title.contains("burn", true) || title.contains("fire", true) -> "🔥"
                title.contains("log", true) || title.contains("tree", true) -> "🌳"
                else -> "📍"
            }

            val tag = card.findViewById<TextView>(R.id.nearRowStatus)
            when (report.status) {
                ReportStatusUi.PENDING -> {
                    tag.text = "Pending"
                    styleTag(tag, ContextCompat.getColor(ctx, R.color.activity_pending_orange))
                }

                ReportStatusUi.IN_PROGRESS -> {
                    tag.text = "In Progress"
                    styleTag(tag, ContextCompat.getColor(ctx, R.color.activity_progress_blue))
                }

                ReportStatusUi.RESOLVED -> {
                    tag.text = "Resolved"
                    styleTag(tag, ContextCompat.getColor(ctx, R.color.activity_resolved_green))
                }
            }
            card.setOnClickListener { onTap(report) }
        }

        private fun styleTag(view: TextView, color: Int) {
            view.setTextColor(ContextCompat.getColor(view.context, R.color.white))
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * view.resources.displayMetrics.density
                setColor(ColorStateList.valueOf(color))
            }
        }
    }
}

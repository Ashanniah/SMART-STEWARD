package com.example.smart_steward

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import java.text.SimpleDateFormat
import java.util.Locale

sealed class ReportHistoryListItem {
    data class MonthHeader(val label: String, val count: Int) : ReportHistoryListItem()
    data class ReportRow(val report: UserReport) : ReportHistoryListItem()
}

class ReportHistoryAdapter(
    private val onViewReport: (UserReport) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_MONTH = 0
        private const val TYPE_REPORT = 1
    }

    private val items = ArrayList<ReportHistoryListItem>()
    private val dayFmt = SimpleDateFormat("MMM d", Locale.getDefault())

    fun submitItems(newItems: List<ReportHistoryListItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (items[position]) {
        is ReportHistoryListItem.MonthHeader -> TYPE_MONTH
        is ReportHistoryListItem.ReportRow -> TYPE_REPORT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_MONTH -> MonthVH(
                inflater.inflate(R.layout.item_report_history_month, parent, false)
            )
            else -> ReportVH(
                inflater.inflate(R.layout.item_report_history, parent, false)
            )
        }
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = items[position]) {
            is ReportHistoryListItem.MonthHeader -> (holder as MonthVH).bind(item)
            is ReportHistoryListItem.ReportRow -> (holder as ReportVH).bind(item.report, dayFmt, onViewReport)
        }
    }

    private class MonthVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val label = itemView.findViewById<TextView>(R.id.historyMonthLabel)
        private val count = itemView.findViewById<TextView>(R.id.historyMonthCount)

        fun bind(header: ReportHistoryListItem.MonthHeader) {
            label.text = header.label
            count.text = itemView.context.getString(R.string.history_reports_in_month, header.count)
        }
    }

    class ReportVH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        fun bind(
            report: UserReport,
            dayFmt: SimpleDateFormat,
            onViewReport: (UserReport) -> Unit
        ) {
            val ctx = itemView.context
            val title = report.incidentType.substringBefore("(").trim().ifBlank { report.incidentType }
            itemView.findViewById<TextView>(R.id.historyTitle).text = title

            val loc = report.locationLine
                .removePrefix("Location:")
                .trim()
                .ifBlank { "—" }
            itemView.findViewById<TextView>(R.id.historyLocation).text = loc

            itemView.findViewById<TextView>(R.id.historyDate).text =
                report.submittedAt?.let { dayFmt.format(it) } ?: "—"

            val statusBadge = itemView.findViewById<TextView>(R.id.historyStatusBadge)
            statusBadge.text = report.statusLabel
            val statusColor = statusColorFor(report.status, ctx)
            stylePill(statusBadge, statusColor, textOnFill = true)

            val dot = itemView.findViewById<View>(R.id.historyTimelineDot)
            dot.background = circleDrawable(statusColor)

            val (emoji, tileColor) = typeVisuals(report.incidentType)
            val iconView = itemView.findViewById<TextView>(R.id.historyTypeIcon)
            val thumb = itemView.findViewById<ImageView>(R.id.historyThumb)
            iconView.text = emoji
            iconView.background = roundedRect(ContextCompat.getColor(ctx, tileColor), dp(ctx, 10f))
            val photoUrl = report.photoUrl.trim()
            if (photoUrl.isNotEmpty()) {
                thumb.visibility = View.VISIBLE
                iconView.visibility = View.GONE
                thumb.load(photoUrl) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(dp(ctx, 10f)))
                    listener(
                        onError = { _, _ ->
                            thumb.visibility = View.GONE
                            iconView.visibility = View.VISIBLE
                        }
                    )
                }
            } else {
                thumb.setImageDrawable(null)
                thumb.visibility = View.GONE
                iconView.visibility = View.VISIBLE
            }

            val agencyChip = itemView.findViewById<TextView>(R.id.historyAgencyChip)
            val agency = AgencyCanonical.shortName(report.assignedAgency)
            agencyChip.text = agency
            stylePill(
                agencyChip,
                ContextCompat.getColor(ctx, R.color.activity_chip_bg),
                textOnFill = false,
                textColor = ContextCompat.getColor(ctx, R.color.activity_muted)
            )

            val metaChip = itemView.findViewById<TextView>(R.id.historyMetaChip)
            if (report.status == ReportStatusUi.REJECTED) {
                val reason = report.description.trim().take(48).ifBlank {
                    report.displayReportRef()
                }
                metaChip.text = reason
            } else {
                metaChip.text = report.displayReportRef()
            }
            stylePill(
                metaChip,
                ContextCompat.getColor(ctx, R.color.activity_chip_bg),
                textOnFill = false,
                textColor = ContextCompat.getColor(ctx, R.color.activity_muted)
            )

            itemView.findViewById<TextView>(R.id.historyViewAction).setOnClickListener {
                onViewReport(report)
            }
            itemView.setOnClickListener { onViewReport(report) }
        }

        private fun statusColorFor(status: ReportStatusUi, ctx: android.content.Context): Int =
            when (status) {
                ReportStatusUi.PENDING ->
                    ContextCompat.getColor(ctx, R.color.activity_progress_blue)
                ReportStatusUi.IN_PROGRESS ->
                    ContextCompat.getColor(ctx, R.color.activity_progress_blue)
                ReportStatusUi.RESOLVED ->
                    ContextCompat.getColor(ctx, R.color.activity_resolved_green)
                ReportStatusUi.REJECTED ->
                    ContextCompat.getColor(ctx, R.color.activity_rejected_gray)
            }

        private fun stylePill(
            tv: TextView,
            fillColor: Int,
            textOnFill: Boolean,
            textColor: Int? = null
        ) {
            tv.background = roundedRect(fillColor, dp(tv.context, 20f))
            tv.setTextColor(
                textColor ?: if (textOnFill) 0xFFFFFFFF.toInt()
                else ContextCompat.getColor(tv.context, R.color.activity_title_bar)
            )
        }

        private fun circleDrawable(color: Int): GradientDrawable =
            GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(color)
            }

        private fun roundedRect(color: Int, radiusPx: Float): GradientDrawable =
            GradientDrawable().apply {
                cornerRadius = radiusPx
                setColor(color)
            }

        private fun dp(ctx: android.content.Context, dp: Float): Float =
            dp * ctx.resources.displayMetrics.density

        private fun typeVisuals(incidentType: String): Pair<String, Int> {
            val t = incidentType.lowercase(Locale.getDefault())
            return when {
                t.contains("burn") || t.contains("fire") -> "🔥" to R.color.activity_pending_orange
                t.contains("dump") || t.contains("waste") || t.contains("garbage") ->
                    "🗑️" to R.color.activity_progress_blue
                t.contains("log") || t.contains("tree") || t.contains("forest") ->
                    "🌳" to R.color.activity_resolved_green
                t.contains("poach") || t.contains("hunt") || t.contains("wildlife") ->
                    "🛡️" to R.color.activity_progress_blue
                else -> "📋" to R.color.activity_chip_bg
            }
        }
    }
}

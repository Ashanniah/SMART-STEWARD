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
    private val dayFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

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
            is ReportHistoryListItem.ReportRow -> (holder as ReportVH).bind(
                item.report,
                dayFmt,
                timeFmt,
                onViewReport
            )
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
            timeFmt: SimpleDateFormat,
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

            val submitted = report.submittedAt
            val dateLine = when {
                submitted == null -> "—"
                else -> "${dayFmt.format(submitted)} · ${timeFmt.format(submitted)}"
            }
            itemView.findViewById<TextView>(R.id.historyDate).text = dateLine

            val statusBadge = itemView.findViewById<TextView>(R.id.historyStatusBadge)
            statusBadge.text = report.statusLabel
            val fill = ReportStatusColors.fillColor(ctx, report.status)
            val text = ReportStatusColors.textColor(ctx, report.status)
            stylePill(statusBadge, fill, text)

            itemView.findViewById<View>(R.id.historyStatusAccent).setBackgroundColor(fill)

            val (emoji, tileColor) = typeVisuals(report.incidentType)
            val iconView = itemView.findViewById<TextView>(R.id.historyTypeIcon)
            val thumb = itemView.findViewById<ImageView>(R.id.historyThumb)
            iconView.text = emoji
            iconView.background = roundedRect(ContextCompat.getColor(ctx, tileColor), dp(ctx, 12f))
            val photoUrl = report.photoUrl.trim()
            if (photoUrl.isNotEmpty()) {
                thumb.visibility = View.VISIBLE
                iconView.visibility = View.GONE
                thumb.load(photoUrl) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(dp(ctx, 12f)))
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
            agencyChip.text = AgencyCanonical.shortName(report.assignedAgency).ifBlank { "—" }
            stylePill(
                agencyChip,
                ContextCompat.getColor(ctx, R.color.activity_chip_bg),
                ContextCompat.getColor(ctx, R.color.activity_muted)
            )

            val metaChip = itemView.findViewById<TextView>(R.id.historyMetaChip)
            val note = report.lastStatusNote.trim()
            metaChip.text = when {
                report.status == ReportStatusUi.REJECTED && note.isNotEmpty() ->
                    note.take(56).let { if (note.length > 56) "$it…" else it }
                else -> report.displayReportRef()
            }
            stylePill(
                metaChip,
                ContextCompat.getColor(ctx, R.color.activity_chip_bg),
                ContextCompat.getColor(ctx, R.color.activity_muted)
            )

            itemView.findViewById<TextView>(R.id.historyViewAction).setOnClickListener {
                onViewReport(report)
            }
            itemView.setOnClickListener { onViewReport(report) }
        }

        private fun stylePill(tv: TextView, fillColor: Int, textColor: Int) {
            tv.background = roundedRect(fillColor, dp(tv.context, 20f))
            tv.setTextColor(textColor)
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
                t.contains("burn") || t.contains("fire") || t.contains("arson") ->
                    "🔥" to R.color.profile_tile_peach
                t.contains("dump") || t.contains("waste") || t.contains("garbage") ->
                    "🗑️" to R.color.profile_tile_blue
                t.contains("log") || t.contains("tree") || t.contains("forest") ->
                    "🌳" to R.color.profile_tile_green
                else -> "📋" to R.color.profile_tile_neutral
            }
        }
    }
}

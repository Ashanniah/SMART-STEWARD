package com.example.smart_steward



import android.graphics.drawable.GradientDrawable

import android.view.LayoutInflater

import android.view.View

import android.view.ViewGroup

import android.widget.ImageView

import android.widget.TextView

import androidx.core.content.ContextCompat

import androidx.recyclerview.widget.RecyclerView

import com.google.android.material.card.MaterialCardView

import java.text.SimpleDateFormat

import java.util.Locale



class MyActivityReportsAdapter(

    private val onViewOnMap: (UserReport) -> Unit,

    private val onTrackReport: (UserReport) -> Unit,

    private val onTrackHistory: (UserReport) -> Unit

) : RecyclerView.Adapter<MyActivityReportsAdapter.VH>() {



    private val items = ArrayList<UserReport>()

    private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())



    fun submitList(newItems: List<UserReport>) {

        items.clear()

        items.addAll(newItems)

        notifyDataSetChanged()

    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {

        val v = LayoutInflater.from(parent.context)

            .inflate(R.layout.item_my_activity_report, parent, false) as MaterialCardView

        return VH(v)

    }



    override fun getItemCount(): Int = items.size



    override fun onBindViewHolder(holder: VH, position: Int) {

        holder.bind(

            items[position],

            dateFmt,

            timeFmt,

            onViewOnMap,

            onTrackReport,

            onTrackHistory

        )

    }



    class VH(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {



        fun bind(

            report: UserReport,

            dateFmt: SimpleDateFormat,

            timeFmt: SimpleDateFormat,

            onViewOnMap: (UserReport) -> Unit,

            onTrackReport: (UserReport) -> Unit,

            onTrackHistory: (UserReport) -> Unit

        ) {

            val ctx = card.context

            bindThumbnail(report)



            bindDetailRow(

                card.findViewById(R.id.reportRowStatus),

                iconRes = R.drawable.ic_receipt_detail_check,

                label = ctx.getString(R.string.receipt_current_status),

                value = null,

                badgeText = report.statusLabel,

                tintIcon = false

            )

            applyStatusBadge(card.findViewById(R.id.reportRowStatus), report)



            bindDetailRow(

                card.findViewById(R.id.reportRowType),

                iconRes = R.drawable.problem,

                label = ctx.getString(R.string.my_activity_detail_report_type),

                value = report.displayTitle(),

                badgeText = null

            )



            val submitted = report.submittedAt

            bindDetailRow(

                card.findViewById(R.id.reportRowDate),

                iconRes = R.drawable.ic_dashboard_detail_calendar,

                label = ctx.getString(R.string.my_activity_detail_date_submitted),

                value = submitted?.let { dateFmt.format(it) } ?: "—",

                badgeText = null,

                tintIcon = false

            )

            bindDetailRow(

                card.findViewById(R.id.reportRowTime),

                iconRes = R.drawable.clock,

                label = ctx.getString(R.string.dashboard_detail_time_label),

                value = submitted?.let { timeFmt.format(it) } ?: "—",

                badgeText = null

            )

            bindDetailRow(

                card.findViewById(R.id.reportRowLocation),

                iconRes = R.drawable.loc,

                label = ctx.getString(R.string.my_activity_detail_location),

                value = report.locationDisplay().ifBlank { "—" },

                badgeText = null

            )

            bindDetailRow(

                card.findViewById(R.id.reportRowAgency),

                iconRes = R.drawable.agency,

                label = ctx.getString(R.string.dashboard_detail_agency_label),

                value = if (report.assignedAgency.isNotBlank()) {

                    AgencyCanonical.shortName(report.assignedAgency)

                } else {

                    ctx.getString(R.string.dashboard_detail_agency_unassigned)

                },

                badgeText = null

            )



            val secondary = card.findViewById<TextView>(R.id.reportBtnSecondary)

            val primary = card.findViewById<TextView>(R.id.reportBtnPrimary)

            secondary.setText(R.string.my_activity_view_map)



            val isClosed = ReportStatusColors.isClosedArchive(report.status)

            if (isClosed) {

                primary.setText(R.string.my_activity_track_history)

            } else {

                primary.setText(R.string.my_activity_track)

            }



            secondary.setOnClickListener { onViewOnMap(report) }

            primary.setOnClickListener {

                if (isClosed) onTrackHistory(report) else onTrackReport(report)

            }

        }



        private fun bindThumbnail(report: UserReport) {
            val ctx = card.context
            ReportThumbnailBinder.bind(
                context = ctx,
                report = report,
                thumb = card.findViewById(R.id.reportThumbnail),
                thumbContainer = card.findViewById(R.id.reportThumbContainer),
                videoPlay = card.findViewById(R.id.reportVideoPlay),
                emojiFallback = card.findViewById(R.id.reportTypeEmojiFallback),
                cornerDp = 10f,
                typeVisuals = ::typeVisuals
            )
        }



        private fun bindDetailRow(

            row: View,

            iconRes: Int,

            label: String,

            value: String?,

            badgeText: String?,

            tintIcon: Boolean = true

        ) {

            val icon = row.findViewById<ImageView>(R.id.activityDetailIcon)

            icon.setImageResource(iconRes)

            if (tintIcon) {

                icon.setColorFilter(

                    ContextCompat.getColor(row.context, R.color.activity_title_bar)

                )

            } else {

                icon.clearColorFilter()

            }

            row.findViewById<TextView>(R.id.activityDetailLabel).text = label



            val badge = row.findViewById<TextView>(R.id.activityDetailBadge)

            if (!badgeText.isNullOrBlank()) {

                badge.visibility = View.VISIBLE

                badge.text = badgeText

            } else {

                badge.visibility = View.GONE

            }



            val valueView = row.findViewById<TextView>(R.id.activityDetailValue)

            if (!value.isNullOrBlank()) {

                valueView.visibility = View.VISIBLE

                valueView.text = value

            } else {

                valueView.visibility = View.GONE

            }

        }



        private fun applyStatusBadge(row: View, report: UserReport) {

            val badge = row.findViewById<TextView>(R.id.activityDetailBadge)

            val fill = ReportStatusColors.fillColor(row.context, report.status)

            val text = ReportStatusColors.textColor(row.context, report.status)

            badge.setTextColor(text)

            badge.background = roundedRect(fill, dp(row.context, 20f))

        }



        private fun roundedRect(color: Int, radiusPx: Float): GradientDrawable {

            return GradientDrawable().apply {

                cornerRadius = radiusPx

                setColor(color)

            }

        }



        private fun dp(ctx: android.content.Context, dp: Float): Float =

            dp * ctx.resources.displayMetrics.density



        private fun typeVisuals(incidentType: String): Pair<String, Int> {
            val t = incidentType.lowercase(Locale.getDefault())
            return when {
                t.contains("burn") || t.contains("fire") -> "🔥" to R.color.activity_pending_orange
                t.contains("log") || t.contains("tree") || t.contains("forest") ->
                    "🌳" to R.color.activity_resolved_green
                t.contains("poach") || t.contains("hunt") || t.contains("wildlife") ->
                    "🛡️" to R.color.activity_progress_blue
                else -> "📋" to R.color.activity_chip_bg
            }
        }

    }

}


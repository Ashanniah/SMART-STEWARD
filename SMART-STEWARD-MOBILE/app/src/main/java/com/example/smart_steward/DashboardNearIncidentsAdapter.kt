package com.example.smart_steward

import android.content.res.ColorStateList
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
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class DashboardNearIncidentsAdapter(
    private val onTap: (UserReport) -> Unit
) : RecyclerView.Adapter<DashboardNearIncidentsAdapter.VH>() {

    private val items = ArrayList<UserReport>()
    private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())

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
        holder.bind(items[position], dateFmt, timeFmt, onTap)
    }

    class VH(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {
        fun bind(
            report: UserReport,
            dateFmt: SimpleDateFormat,
            timeFmt: SimpleDateFormat,
            onTap: (UserReport) -> Unit
        ) {
            val ctx = card.context

            card.findViewById<TextView>(R.id.nearRowTitle).text = report.displayTitle()

            val submitted = report.submittedAt
            val locationText = report.locationDisplay().ifBlank { "—" }
            val dateText = submitted?.let { dateFmt.format(it) } ?: "—"
            val timeText = submitted?.let { timeFmt.format(it) } ?: "—"

            bindDetailRow(
                card.findViewById(R.id.nearRowLocation),
                iconRes = R.drawable.loc,
                label = ctx.getString(R.string.my_activity_detail_location),
                value = locationText
            )
            bindDetailRow(
                card.findViewById(R.id.nearRowDate),
                iconRes = R.drawable.ic_dashboard_detail_calendar,
                label = ctx.getString(R.string.dashboard_detail_date_label),
                value = dateText,
                tintIcon = false
            )
            bindDetailRow(
                card.findViewById(R.id.nearRowTime),
                iconRes = R.drawable.clock,
                label = ctx.getString(R.string.dashboard_detail_time_label),
                value = timeText
            )

            val photo = card.findViewById<ImageView>(R.id.nearRowPhoto)
            val thumbContainer = card.findViewById<View>(R.id.nearRowThumbContainer)
            val videoPlay = card.findViewById<ImageView>(R.id.nearRowVideoPlay)
            val cornerRadiusPx = 8f * ctx.resources.displayMetrics.density
            val url = report.photoUrl.trim()
            if (url.isNotEmpty()) {
                photo.load(url) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(cornerRadiusPx))
                    placeholder(R.drawable.bg_near_report_thumb_placeholder)
                    error(R.drawable.bg_near_report_thumb_placeholder)
                }
            } else {
                photo.setImageResource(R.drawable.bg_near_report_thumb_placeholder)
            }

            val videoRemote = report.videoUrl.trim()
            if (videoRemote.isNotEmpty()) {
                videoPlay.visibility = View.VISIBLE
                thumbContainer.setOnClickListener {
                    MediaPlayback.openRemoteVideo(ctx, videoRemote)
                }
            } else if (url.isNotEmpty()) {
                videoPlay.visibility = View.GONE
                thumbContainer.setOnClickListener {
                    MediaPlayback.openRemoteImage(ctx, url)
                }
            } else {
                videoPlay.visibility = View.GONE
                thumbContainer.setOnClickListener(null)
            }

            val tag = card.findViewById<TextView>(R.id.nearRowStatus)
            tag.text = report.statusLabel
            val fill = ReportStatusColors.fillColor(ctx, report.status)
            val text = ReportStatusColors.textColor(ctx, report.status)
            styleTag(tag, fill, text)
            card.setOnClickListener { onTap(report) }
        }

        private fun bindDetailRow(
            row: View,
            iconRes: Int,
            label: String,
            value: String?,
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
            row.findViewById<TextView>(R.id.activityDetailBadge).visibility = View.GONE

            val valueView = row.findViewById<TextView>(R.id.activityDetailValue)
            if (!value.isNullOrBlank()) {
                valueView.visibility = View.VISIBLE
                valueView.text = value
            } else {
                valueView.visibility = View.GONE
            }
        }

        private fun styleTag(view: TextView, fillColor: Int, textColor: Int) {
            view.setTextColor(textColor)
            view.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 18f * view.resources.displayMetrics.density
                setColor(ColorStateList.valueOf(fillColor))
            }
        }
    }
}

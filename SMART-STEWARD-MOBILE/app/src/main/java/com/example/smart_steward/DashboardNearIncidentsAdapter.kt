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
            val color = when (report.status) {
                ReportStatusUi.PENDING ->
                    ContextCompat.getColor(ctx, R.color.activity_pending_orange)
                ReportStatusUi.IN_PROGRESS ->
                    ContextCompat.getColor(ctx, R.color.activity_progress_blue)
                ReportStatusUi.RESOLVED ->
                    ContextCompat.getColor(ctx, R.color.activity_resolved_green)
                ReportStatusUi.REJECTED ->
                    ContextCompat.getColor(ctx, R.color.activity_rejected_gray)
            }
            styleTag(tag, color)
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

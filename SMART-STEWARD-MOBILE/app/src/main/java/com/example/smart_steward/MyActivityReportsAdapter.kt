package com.example.smart_steward

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.card.MaterialCardView
import java.text.SimpleDateFormat
import java.util.Locale

class MyActivityReportsAdapter(
    private val onViewOnMap: (UserReport) -> Unit,
    private val onTrackReport: (UserReport) -> Unit
) : RecyclerView.Adapter<MyActivityReportsAdapter.VH>() {

    private val items = ArrayList<UserReport>()
    private val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())

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
        holder.bind(items[position], dateFmt, onViewOnMap, onTrackReport)
    }

    class VH(private val card: MaterialCardView) : RecyclerView.ViewHolder(card) {

        fun bind(
            report: UserReport,
            dateFmt: SimpleDateFormat,
            onViewOnMap: (UserReport) -> Unit,
            onTrackReport: (UserReport) -> Unit
        ) {
            val ctx = card.context
            val title = report.incidentType.substringBefore("(").trim()
                .ifBlank { report.incidentType }
            card.findViewById<TextView>(R.id.reportTitle).text = title

            val loc = report.locationLine
                .removePrefix("Location:")
                .trim()
                .ifBlank { ctx.getString(R.string.my_activity_view_map) }
            val dateStr = report.submittedAt?.let { dateFmt.format(it) } ?: "—"
            card.findViewById<TextView>(R.id.reportLocationDate).text = "$loc • $dateStr"

            val (emoji, tileColorRes) = typeVisuals(report.incidentType)
            val thumb = card.findViewById<ImageView>(R.id.reportThumbnail)
            val thumbContainer = card.findViewById<View>(R.id.reportThumbContainer)
            val videoPlay = card.findViewById<ImageView>(R.id.reportVideoPlay)
            val emojiFallback = card.findViewById<TextView>(R.id.reportTypeEmojiFallback)
            val cornerPx = dp(ctx, 10f)
            emojiFallback.text = emoji
            emojiFallback.background = roundedRect(
                ContextCompat.getColor(ctx, tileColorRes),
                cornerPx
            )
            val url = report.photoUrl.trim()
            val videoRemote = report.videoUrl.trim()
            if (url.isNotEmpty()) {
                thumb.visibility = View.VISIBLE
                emojiFallback.visibility = View.GONE
                thumb.load(url) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(cornerPx))
                    listener(
                        onError = { _, _ ->
                            thumb.setImageDrawable(null)
                            thumb.visibility = View.GONE
                            emojiFallback.visibility = View.VISIBLE
                        },
                        onCancel = {
                            thumb.setImageDrawable(null)
                            thumb.visibility = View.GONE
                            emojiFallback.visibility = View.VISIBLE
                        }
                    )
                }
            } else {
                thumb.setImageDrawable(null)
                thumb.visibility = View.GONE
                emojiFallback.visibility = View.VISIBLE
            }

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

            val statusTag = card.findViewById<TextView>(R.id.reportStatusTag)
            statusTag.text = report.statusLabel
            val badgeColor = when (report.status) {
                ReportStatusUi.PENDING ->
                    ContextCompat.getColor(ctx, R.color.activity_pending_orange)
                ReportStatusUi.IN_PROGRESS ->
                    ContextCompat.getColor(ctx, R.color.activity_progress_blue)
                ReportStatusUi.RESOLVED ->
                    ContextCompat.getColor(ctx, R.color.activity_resolved_green)
                ReportStatusUi.REJECTED ->
                    ContextCompat.getColor(ctx, R.color.activity_rejected_gray)
            }
            styleTag(statusTag, badgeColor)

            val secondary = card.findViewById<TextView>(R.id.reportBtnSecondary)
            val primary = card.findViewById<TextView>(R.id.reportBtnPrimary)
            if (report.status == ReportStatusUi.RESOLVED) {
                secondary.setText(R.string.my_activity_view_evidence)
                primary.setText(R.string.my_activity_see_resolution)
            } else {
                secondary.setText(R.string.my_activity_view_map)
                primary.setText(R.string.my_activity_track)
            }

            secondary.setOnClickListener {
                if (report.status == ReportStatusUi.RESOLVED) {
                    val videoEvidence = report.videoUrl.trim()
                    val photoEvidence = report.photoUrl.trim()
                    when {
                        videoEvidence.isNotEmpty() -> MediaPlayback.openRemoteVideo(ctx, videoEvidence)
                        photoEvidence.isNotEmpty() -> MediaPlayback.openRemoteImage(ctx, photoEvidence)
                        else -> Toast.makeText(
                            ctx,
                            ctx.getString(R.string.my_activity_view_evidence),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                } else {
                    onViewOnMap(report)
                }
            }
            primary.setOnClickListener {
                if (report.status == ReportStatusUi.RESOLVED) {
                    Toast.makeText(
                        ctx,
                        ctx.getString(R.string.my_activity_see_resolution) + ": " + report.assignedAgency,
                        Toast.LENGTH_LONG
                    ).show()
                } else {
                    onTrackReport(report)
                }
            }
        }

        private fun styleTag(tv: TextView, color: Int) {
            tv.setTextColor(0xFFFFFFFF.toInt())
            tv.background = roundedRect(color, dp(tv.context, 20f))
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

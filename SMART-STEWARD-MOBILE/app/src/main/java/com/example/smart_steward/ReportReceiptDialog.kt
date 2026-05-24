package com.example.smart_steward

import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Locale

object ReportReceiptDialog {

    fun show(activity: AppCompatActivity, report: UserReport) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_report_receipt, null)

        dialogView.findViewById<TextView>(R.id.receiptReportId).text = report.displayReportRef()

        bindDetailRow(
            dialogView.findViewById(R.id.receiptRowStatus),
            iconRes = R.drawable.check,
            label = activity.getString(R.string.receipt_current_status),
            value = null,
            badgeText = report.statusLabel
        )
        applyStatusBadge(activity, dialogView.findViewById(R.id.receiptRowStatus), report)

        bindDetailRow(
            dialogView.findViewById(R.id.receiptRowType),
            iconRes = R.drawable.problem,
            label = activity.getString(R.string.my_activity_detail_report_type),
            value = report.displayTitle(),
            badgeText = null
        )

        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val submitted = report.submittedAt

        bindDetailRow(
            dialogView.findViewById(R.id.receiptRowDate),
            iconRes = R.drawable.calendar,
            label = activity.getString(R.string.my_activity_detail_date_submitted),
            value = submitted?.let { dateFmt.format(it) } ?: "—",
            badgeText = null
        )
        bindDetailRow(
            dialogView.findViewById(R.id.receiptRowTime),
            iconRes = R.drawable.clock,
            label = activity.getString(R.string.dashboard_detail_time_label),
            value = submitted?.let { timeFmt.format(it) } ?: "—",
            badgeText = null
        )
        bindDetailRow(
            dialogView.findViewById(R.id.receiptRowLocation),
            iconRes = R.drawable.loc,
            label = activity.getString(R.string.my_activity_detail_location),
            value = report.locationDisplay().ifBlank { "—" },
            badgeText = null
        )

        dialogView.findViewById<TextView>(R.id.receiptDescriptionBody).text =
            report.description.trim().ifBlank { "—" }

        bindPhotoSection(activity, dialogView, report)

        bindDetailRow(
            dialogView.findViewById(R.id.receiptRowAgency),
            iconRes = R.drawable.agency,
            label = activity.getString(R.string.dashboard_detail_agency_label),
            value = if (report.assignedAgency.isNotBlank()) {
                AgencyCanonical.shortName(report.assignedAgency)
            } else {
                activity.getString(R.string.dashboard_detail_agency_unassigned)
            },
            badgeText = null
        )

        val adminNote = report.lastStatusNote.trim()
        val remarksSection = dialogView.findViewById<LinearLayout>(R.id.receiptRemarksSection)
        if (adminNote.isNotEmpty()) {
            remarksSection.visibility = View.VISIBLE
            dialogView.findViewById<TextView>(R.id.receiptRemarksBody).text = adminNote
        } else {
            remarksSection.visibility = View.GONE
        }

        val dialog = BottomSheetDialog(activity)
        dialog.setContentView(dialogView)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogView.findViewById<View>(R.id.receiptCloseButton).setOnClickListener {
            dialog.dismiss()
        }

        dialog.setOnShowListener {
            val scroll = dialogView.findViewById<NestedScrollView>(R.id.receiptScroll)
            scroll.post {
                val maxHeight = minOf(
                    (activity.resources.displayMetrics.heightPixels * 0.85f).toInt(),
                    activity.resources.getDimensionPixelSize(R.dimen.report_receipt_scroll_max_height)
                )
                val contentHeight = scroll.getChildAt(0)?.height ?: 0
                scroll.layoutParams = scroll.layoutParams.apply {
                    height = if (contentHeight > maxHeight) maxHeight else ViewGroup.LayoutParams.WRAP_CONTENT
                }
                scroll.requestLayout()
            }
        }
        dialog.show()
    }

    private fun bindDetailRow(
        rowRoot: View,
        iconRes: Int,
        label: String,
        value: String?,
        badgeText: String?
    ) {
        val icon = rowRoot.findViewById<ImageView>(R.id.receiptDetailIcon)
        icon.setImageResource(iconRes)
        icon.setColorFilter(
            ContextCompat.getColor(rowRoot.context, R.color.activity_title_bar),
            PorterDuff.Mode.SRC_IN
        )
        rowRoot.findViewById<TextView>(R.id.receiptDetailLabel).text = label
        val valueView = rowRoot.findViewById<TextView>(R.id.receiptDetailValue)
        val badgeView = rowRoot.findViewById<TextView>(R.id.receiptDetailBadge)
        if (badgeText != null) {
            valueView.visibility = View.GONE
            badgeView.visibility = View.VISIBLE
            badgeView.text = badgeText
        } else {
            badgeView.visibility = View.GONE
            valueView.visibility = View.VISIBLE
            valueView.text = value.orEmpty()
        }
    }

    private fun bindPhotoSection(activity: AppCompatActivity, dialogView: View, report: UserReport) {
        val photoSection = dialogView.findViewById<LinearLayout>(R.id.receiptPhotoSection)
        val photoUrl = report.photoUrl.trim()
        val videoUrl = report.videoUrl.trim()
        if (photoUrl.isEmpty() && videoUrl.isEmpty()) {
            photoSection.visibility = View.GONE
            return
        }
        photoSection.visibility = View.VISIBLE
        val thumb = dialogView.findViewById<ImageView>(R.id.receiptPhotoThumb)
        val play = dialogView.findViewById<ImageView>(R.id.receiptPhotoVideoPlay)
        val container = dialogView.findViewById<View>(R.id.receiptPhotoThumbContainer)
        val cornerRadiusPx = 6f * activity.resources.displayMetrics.density

        if (photoUrl.isNotEmpty()) {
            thumb.load(photoUrl) {
                crossfade(true)
                transformations(RoundedCornersTransformation(cornerRadiusPx))
                placeholder(R.drawable.bg_near_report_thumb_placeholder)
                error(R.drawable.bg_near_report_thumb_placeholder)
            }
        } else {
            thumb.setImageResource(R.drawable.bg_near_report_thumb_placeholder)
        }

        if (videoUrl.isNotEmpty()) {
            play.visibility = View.VISIBLE
            container.setOnClickListener {
                MediaPlayback.openRemoteVideo(activity, videoUrl)
            }
        } else if (photoUrl.isNotEmpty()) {
            play.visibility = View.GONE
            container.setOnClickListener {
                MediaPlayback.openRemoteImage(activity, photoUrl)
            }
        } else {
            play.visibility = View.GONE
            container.setOnClickListener(null)
        }
    }

    private fun applyStatusBadge(activity: AppCompatActivity, statusRow: View, report: UserReport) {
        val badge = statusRow.findViewById<TextView>(R.id.receiptDetailBadge)
        val (bgColor, textColor) = when (report.status) {
            ReportStatusUi.PENDING -> Pair(
                ContextCompat.getColor(activity, R.color.activity_pending_orange),
                ContextCompat.getColor(activity, R.color.white)
            )
            ReportStatusUi.IN_PROGRESS -> Pair(0xFF1565C0.toInt(), ContextCompat.getColor(activity, R.color.white))
            ReportStatusUi.RESOLVED -> Pair(
                ContextCompat.getColor(activity, R.color.activity_resolved_green),
                ContextCompat.getColor(activity, R.color.white)
            )
            ReportStatusUi.REJECTED -> Pair(
                ContextCompat.getColor(activity, R.color.activity_rejected_gray),
                ContextCompat.getColor(activity, R.color.white)
            )
        }
        badge.setTextColor(textColor)
        badge.background = roundedRect(bgColor, dp(activity, 20f))
    }

    private fun roundedRect(fill: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radiusPx
            setColor(fill)
        }

    private fun dp(activity: AppCompatActivity, dp: Float): Float =
        dp * activity.resources.displayMetrics.density
}

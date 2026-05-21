package com.example.smart_steward

import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
import com.google.android.material.bottomsheet.BottomSheetDialog
import java.text.SimpleDateFormat
import java.util.Locale

object ReportReceiptDialog {

    fun show(activity: AppCompatActivity, report: UserReport) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_report_receipt, null)

        dialogView.findViewById<TextView>(R.id.receiptReportId).text = report.displayReportRef()

        bindRow(
            dialogView.findViewById(R.id.receiptRowType),
            activity.getString(R.string.receipt_row_report_type),
            report.incidentType.trim().ifBlank { "—" },
            showTypeDot = true
        )
        val dateFmt = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        val submitted = report.submittedAt?.let { dateFmt.format(it) } ?: "—"
        bindRow(
            dialogView.findViewById(R.id.receiptRowDate),
            activity.getString(R.string.receipt_row_date_submitted),
            submitted
        )
        bindRow(
            dialogView.findViewById(R.id.receiptRowLocation),
            activity.getString(R.string.receipt_row_location),
            report.locationDisplay().ifBlank { "—" }
        )

        val descBody = buildDescriptionBody(report)
        dialogView.findViewById<TextView>(R.id.receiptDescriptionBody).text = descBody

        applyStatusBadge(activity, dialogView, report)

        val videoUrl = report.videoUrl.trim()
        val photoUrl = report.photoUrl.trim()
        val attachmentChip = dialogView.findViewById<LinearLayout>(R.id.receiptAttachmentChip)
        if (videoUrl.isNotEmpty() || photoUrl.isNotEmpty()) {
            attachmentChip.visibility = View.VISIBLE
            val fileCount = if (videoUrl.isNotEmpty() && photoUrl.isNotEmpty()) 2 else 1
            dialogView.findViewById<TextView>(R.id.receiptAttachmentLabel).text =
                if (videoUrl.isNotEmpty()) {
                    activity.getString(R.string.receipt_video_attached)
                } else {
                    activity.getString(R.string.receipt_photo_attached)
                }
            dialogView.findViewById<TextView>(R.id.receiptAttachmentCount).text =
                if (fileCount == 1) {
                    activity.getString(R.string.receipt_files_count, fileCount)
                } else {
                    activity.getString(R.string.receipt_files_count_plural, fileCount)
                }
            attachmentChip.setOnClickListener {
                when {
                    videoUrl.isNotEmpty() -> MediaPlayback.openRemoteVideo(activity, videoUrl)
                    else -> MediaPlayback.openRemoteImage(activity, photoUrl)
                }
            }
        } else {
            attachmentChip.visibility = View.GONE
        }

        val extra = dialogView.findViewById<LinearLayout>(R.id.receiptExtraContent)
        val adminNote = report.lastStatusNote.trim()
        if (adminNote.isNotEmpty()) {
            extra.visibility = View.VISIBLE
            addAdminRemarksRow(activity, extra, adminNote)
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
                    (activity.resources.displayMetrics.heightPixels * 0.75f).toInt(),
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

    private fun bindRow(
        rowRoot: View,
        label: String,
        value: String,
        showTypeDot: Boolean = false
    ) {
        rowRoot.findViewById<TextView>(R.id.receiptRowLabel).text = label
        rowRoot.findViewById<TextView>(R.id.receiptRowValue).text = value
        rowRoot.findViewById<View>(R.id.receiptRowTypeDot).visibility =
            if (showTypeDot) View.VISIBLE else View.GONE
    }

    private fun buildDescriptionBody(report: UserReport): String =
        report.description.trim().ifBlank { "—" }

    private fun addAdminRemarksRow(activity: AppCompatActivity, parent: LinearLayout, note: String) {
        val row = activity.layoutInflater.inflate(R.layout.item_receipt_row, parent, false)
        val label = activity.getString(R.string.my_activity_detail_admin_remarks)
            .trimEnd(':')
            .trim()
        bindRow(row, label, note)
        parent.addView(row)
    }

    private fun applyStatusBadge(activity: AppCompatActivity, dialogView: View, report: UserReport) {
        val statusText = dialogView.findViewById<TextView>(R.id.receiptStatusText)
        val statusDot = dialogView.findViewById<View>(R.id.receiptStatusDot)
        val statusBadge = dialogView.findViewById<LinearLayout>(R.id.receiptStatusBadge)

        val (labelRes, dotColor, bgColor, borderColor, textColor) = when (report.status) {
            ReportStatusUi.PENDING -> StatusStyle(
                R.string.receipt_status_pending_review,
                0xFFEAB308.toInt(),
                0xFFFFF9E8.toInt(),
                ContextCompat.getColor(activity, R.color.notif_advisory_border),
                ContextCompat.getColor(activity, R.color.notif_gold_text)
            )

            ReportStatusUi.IN_PROGRESS -> StatusStyle(
                R.string.receipt_status_in_progress,
                0xFF1565C0.toInt(),
                ContextCompat.getColor(activity, R.color.profile_tile_blue),
                0xFF90CAF9.toInt(),
                0xFF1565C0.toInt()
            )

            ReportStatusUi.RESOLVED -> StatusStyle(
                R.string.receipt_status_resolved,
                ContextCompat.getColor(activity, R.color.activity_resolved_green),
                0xFFE8F5EC.toInt(),
                0xFFA5D6A7.toInt(),
                ContextCompat.getColor(activity, R.color.notif_title_green)
            )

            ReportStatusUi.REJECTED -> StatusStyle(
                R.string.receipt_status_rejected,
                ContextCompat.getColor(activity, R.color.activity_rejected_gray),
                0xFFFCE8E8.toInt(),
                0xFFEF9A9A.toInt(),
                ContextCompat.getColor(activity, R.color.activity_rejected_gray)
            )
        }

        statusText.text = activity.getString(labelRes)
        statusText.setTextColor(textColor)
        statusDot.background = circleDrawable(dotColor)
        statusBadge.background = roundedRect(
            bgColor,
            borderColor,
            dp(activity, 20f),
            strokePx = dp(activity, 1f).toInt().coerceAtLeast(1)
        )
    }

    private data class StatusStyle(
        val labelRes: Int,
        val dotColor: Int,
        val bgColor: Int,
        val borderColor: Int,
        val textColor: Int
    )

    private fun circleDrawable(color: Int): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(color)
        }

    private fun roundedRect(
        fill: Int,
        stroke: Int,
        radiusPx: Float,
        strokePx: Int
    ): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radiusPx
            setColor(fill)
            setStroke(strokePx, stroke)
        }

    private fun dp(activity: AppCompatActivity, dp: Float): Float =
        dp * activity.resources.displayMetrics.density
}

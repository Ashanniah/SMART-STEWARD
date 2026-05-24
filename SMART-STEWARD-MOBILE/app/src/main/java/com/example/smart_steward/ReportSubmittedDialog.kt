package com.example.smart_steward

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.NestedScrollView
object ReportSubmittedDialog {

    data class Payload(
        val reportId: String,
        val incidentType: String,
        val assignedAgency: String,
        val agencyShort: String,
        val description: String,
        val locationDisplay: String,
        val dateText: String,
        val timeText: String,
        val photoBitmap: Bitmap?,
        val videoUri: Uri?,
        val agencyRemarks: String? = null
    )

    fun show(
        activity: AppCompatActivity,
        payload: Payload,
        onTrack: () -> Unit,
        onDismiss: () -> Unit
    ) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_report_submitted, null)

        val agenciesDisplay = payload.agencyShort.ifBlank {
            AgencyCanonical.shortName(payload.assignedAgency)
        }.ifBlank { payload.assignedAgency }

        dialogView.findViewById<TextView>(R.id.submittedDialogSubtitle).text =
            activity.getString(R.string.submitted_success_subtitle, agenciesDisplay)

        bindDetailRow(
            dialogView.findViewById(R.id.submittedRowReportId),
            iconRes = R.drawable.paper,
            label = activity.getString(R.string.receipt_label_report_id) + ":",
            value = payload.reportId,
            badgeText = null
        )
        bindDetailRow(
            dialogView.findViewById(R.id.submittedRowStatus),
            iconRes = R.drawable.ic_receipt_detail_check,
            label = activity.getString(R.string.receipt_current_status),
            value = null,
            badgeText = activity.getString(R.string.my_activity_status_pending),
            tintIcon = false
        )
        applyPendingBadge(activity, dialogView.findViewById(R.id.submittedRowStatus))

        bindDetailRow(
            dialogView.findViewById(R.id.submittedRowType),
            iconRes = R.drawable.problem,
            label = activity.getString(R.string.receipt_label_report_type) + ":",
            value = payload.incidentType,
            badgeText = null
        )
        bindDetailRow(
            dialogView.findViewById(R.id.submittedRowDate),
            iconRes = R.drawable.calendar,
            label = activity.getString(R.string.receipt_label_date_submitted) + ":",
            value = payload.dateText,
            badgeText = null
        )
        bindDetailRow(
            dialogView.findViewById(R.id.submittedRowTime),
            iconRes = R.drawable.clock,
            label = activity.getString(R.string.dashboard_detail_time_label) + ":",
            value = payload.timeText,
            badgeText = null
        )
        bindDetailRow(
            dialogView.findViewById(R.id.submittedRowLocation),
            iconRes = R.drawable.loc,
            label = activity.getString(R.string.receipt_label_location) + ":",
            value = payload.locationDisplay.ifBlank { "—" },
            badgeText = null
        )

        dialogView.findViewById<TextView>(R.id.submittedDialogDescription).text =
            payload.description.trim().ifBlank { "—" }

        bindPhotoSection(activity, dialogView, payload)

        bindDetailRow(
            dialogView.findViewById(R.id.submittedRowAgency),
            iconRes = R.drawable.agency,
            label = activity.getString(R.string.dashboard_detail_agency_label) + ":",
            value = agenciesDisplay,
            badgeText = null
        )

        val remarks = payload.agencyRemarks?.trim().orEmpty()
        val remarksSection = dialogView.findViewById<LinearLayout>(R.id.submittedRemarksSection)
        if (remarks.isNotEmpty()) {
            remarksSection.visibility = View.VISIBLE
            dialogView.findViewById<TextView>(R.id.submittedDialogRemarks).text = remarks
        } else {
            remarksSection.visibility = View.GONE
        }

        val dialog = Dialog(activity, R.style.Theme_ReportSubmittedDialog)
        dialog.setContentView(dialogView)
        dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * 0.92f).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog.setCancelable(false)

        val close = { dialog.dismiss(); onDismiss() }
        dialogView.findViewById<View>(R.id.submittedDialogClose).setOnClickListener { close() }
        dialogView.findViewById<View>(R.id.submittedDialogOkButton).setOnClickListener { close() }
        dialogView.findViewById<View>(R.id.submittedDialogTrackButton).setOnClickListener {
            dialog.dismiss()
            onTrack()
        }

        dialog.setOnShowListener {
            val scroll = dialogView.findViewById<NestedScrollView>(R.id.submittedDialogScroll)
            scroll.post {
                val maxHeight = minOf(
                    (activity.resources.displayMetrics.heightPixels * 0.42f).toInt(),
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
        badgeText: String?,
        tintIcon: Boolean = true
    ) {
        val icon = rowRoot.findViewById<ImageView>(R.id.receiptDetailIcon)
        icon.setImageResource(iconRes)
        if (tintIcon) {
            icon.setColorFilter(
                ContextCompat.getColor(rowRoot.context, R.color.activity_title_bar),
                PorterDuff.Mode.SRC_IN
            )
        } else {
            icon.clearColorFilter()
        }
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

    private fun applyPendingBadge(activity: AppCompatActivity, statusRow: View) {
        val badge = statusRow.findViewById<TextView>(R.id.receiptDetailBadge)
        val bg = ReportStatusColors.fillColor(activity, ReportStatusUi.PENDING)
        val text = ReportStatusColors.textColor(activity, ReportStatusUi.PENDING)
        badge.setTextColor(text)
        badge.background = roundedRect(bg, dp(activity, 20f))
    }

    private fun bindPhotoSection(activity: AppCompatActivity, dialogView: View, payload: Payload) {
        val section = dialogView.findViewById<LinearLayout>(R.id.submittedPhotoSection)
        val thumb = dialogView.findViewById<ImageView>(R.id.submittedPhotoThumb)
        val play = dialogView.findViewById<ImageView>(R.id.submittedPhotoVideoPlay)
        val container = dialogView.findViewById<View>(R.id.submittedPhotoThumbContainer)
        val placeholder = ContextCompat.getColor(activity, R.color.register_field_fill)

        when {
            payload.photoBitmap != null -> {
                section.visibility = View.VISIBLE
                thumb.setImageBitmap(payload.photoBitmap)
                play.visibility = View.GONE
                container.setOnClickListener(null)
            }
            payload.videoUri != null -> {
                section.visibility = View.VISIBLE
                val frame = activity.loadVideoFrameForDialog(payload.videoUri)
                if (frame != null) {
                    thumb.setImageBitmap(frame)
                } else {
                    thumb.setImageDrawable(null)
                    thumb.setBackgroundColor(placeholder)
                }
                play.visibility = View.VISIBLE
                container.setOnClickListener {
                    MediaPlayback.openLocalVideo(activity, payload.videoUri)
                }
            }
            else -> section.visibility = View.GONE
        }
    }

    private fun AppCompatActivity.loadVideoFrameForDialog(uri: Uri): Bitmap? {
        val retriever = android.media.MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun roundedRect(fill: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radiusPx
            setColor(fill)
        }

    private fun dp(activity: AppCompatActivity, dp: Float): Float =
        dp * activity.resources.displayMetrics.density
}

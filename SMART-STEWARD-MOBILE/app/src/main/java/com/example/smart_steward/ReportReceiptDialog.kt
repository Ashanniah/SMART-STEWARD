package com.example.smart_steward

import android.graphics.Typeface
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.NestedScrollView
import coil.load
import java.text.SimpleDateFormat
import java.util.Locale

object ReportReceiptDialog {

    fun show(activity: AppCompatActivity, report: UserReport) {
        val dialogView = activity.layoutInflater.inflate(R.layout.dialog_report_receipt, null)
        val content = dialogView.findViewById<LinearLayout>(R.id.receiptContent)

        fun addRow(label: String, value: String, brandGreenValue: Boolean = false) {
            val row = activity.layoutInflater.inflate(R.layout.item_receipt_row, content, false)
            row.findViewById<TextView>(R.id.receiptRowLabel).text = label
            val tv = row.findViewById<TextView>(R.id.receiptRowValue)
            tv.text = value.ifBlank { "—" }
            if (brandGreenValue) {
                tv.setTextColor(activity.getColor(R.color.register_button_green))
                tv.setTypeface(tv.typeface, Typeface.NORMAL)
                tv.textSize = 14f
            }
            content.addView(row)
        }

        fun addDescription(label: String, body: String) {
            val block = activity.layoutInflater.inflate(R.layout.item_receipt_description, content, false)
            block.findViewById<TextView>(R.id.receiptDescLabel).text = label
            block.findViewById<TextView>(R.id.receiptDescBody).text = body.ifBlank { "—" }
            content.addView(block)
        }

        val dateFmt = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        val submitted = report.submittedAt?.let { dateFmt.format(it) } ?: "—"
        val location = report.locationDisplay().ifBlank { "—" }

        addRow(activity.getString(R.string.my_activity_detail_report_id), report.displayReportRef())
        addRow(
            activity.getString(R.string.my_activity_detail_report_type),
            report.incidentType.trim().ifBlank { "—" }
        )
        addRow(activity.getString(R.string.my_activity_detail_date_submitted), submitted)
        addRow(activity.getString(R.string.my_activity_detail_location), location)
        addRow(activity.getString(R.string.my_activity_detail_status), report.statusLabel)

        val adminNote = report.lastStatusNote.trim()
        if (adminNote.isNotEmpty()) {
            addDescription(activity.getString(R.string.my_activity_detail_admin_remarks), adminNote)
        }

        addDescription(
            activity.getString(R.string.my_activity_detail_description),
            report.description
        )

        val videoUrl = report.videoUrl.trim()
        val photoUrl = report.photoUrl.trim()
        val attachmentSummary = when {
            videoUrl.isNotEmpty() -> activity.getString(R.string.my_activity_attachment_video)
            photoUrl.isNotEmpty() -> activity.getString(R.string.my_activity_attachment_photo)
            else -> activity.getString(R.string.my_activity_attachment_none)
        }
        addRow(activity.getString(R.string.my_activity_detail_attachment), attachmentSummary)

        val density = activity.resources.displayMetrics.density
        when {
            videoUrl.isNotEmpty() -> {
                val thumbHeight = (180 * density).toInt()
                val hMargin = (16 * density).toInt()
                val frame = FrameLayout(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        thumbHeight
                    ).apply {
                        setMargins(hMargin, 0, hMargin, (8 * density).toInt())
                    }
                    isClickable = true
                    isFocusable = true
                }
                val thumb = ImageView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    load(photoUrl.ifBlank { videoUrl })
                }
                val playSize = (44 * density).toInt()
                val playPad = (8 * density).toInt()
                val playOverlay = ImageView(activity).apply {
                    layoutParams = FrameLayout.LayoutParams(playSize, playSize).apply {
                        gravity = Gravity.CENTER
                    }
                    setBackgroundResource(R.drawable.bg_play_circle)
                    setImageResource(android.R.drawable.ic_media_play)
                    imageTintList = android.content.res.ColorStateList.valueOf(
                        activity.getColor(R.color.white)
                    )
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    setPadding(playPad, playPad, playPad, playPad)
                    contentDescription = activity.getString(R.string.play_video)
                    isClickable = false
                    isFocusable = false
                }
                val openVideo = {
                    MediaPlayback.openRemoteVideo(activity, videoUrl)
                }
                frame.setOnClickListener { openVideo() }
                frame.addView(thumb)
                frame.addView(playOverlay)
                content.addView(frame)
            }
            photoUrl.isNotEmpty() -> {
                val img = ImageView(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        (180 * density).toInt()
                    ).apply {
                        val m = (16 * density).toInt()
                        setMargins(m, 0, m, (8 * density).toInt())
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    load(photoUrl)
                    setOnClickListener {
                        MediaPlayback.openRemoteImage(activity, photoUrl)
                    }
                }
                content.addView(img)
            }
        }

        addRow(
            activity.getString(R.string.my_activity_detail_assigned),
            report.assignedAgency.trim().ifBlank { "—" },
            brandGreenValue = true
        )

        val dialog = AlertDialog.Builder(activity).setView(dialogView).create()
        dialogView.findViewById<TextView>(R.id.receiptCloseButton).setOnClickListener {
            dialog.dismiss()
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setOnShowListener {
            val scroll = dialogView.findViewById<NestedScrollView>(R.id.receiptScroll)
            scroll.post {
                val maxHeight = minOf(
                    (activity.resources.displayMetrics.heightPixels * 0.62f).toInt(),
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
}

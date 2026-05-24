package com.example.smart_steward

import android.content.Context
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat

/** Shared status pill colors: pending gray, in progress yellow, resolved green, rejected red. */
object ReportStatusColors {

    @ColorInt
    fun fillColor(context: Context, status: ReportStatusUi): Int =
        when (status) {
            ReportStatusUi.PENDING ->
                ContextCompat.getColor(context, R.color.activity_pending_orange)
            ReportStatusUi.IN_PROGRESS ->
                ContextCompat.getColor(context, R.color.activity_progress_blue)
            ReportStatusUi.RESOLVED ->
                ContextCompat.getColor(context, R.color.activity_resolved_green)
            ReportStatusUi.REJECTED ->
                ContextCompat.getColor(context, R.color.activity_rejected_gray)
        }

    @ColorInt
    fun textColor(context: Context, status: ReportStatusUi): Int =
        when (status) {
            ReportStatusUi.IN_PROGRESS ->
                ContextCompat.getColor(context, R.color.black)
            else -> ContextCompat.getColor(context, R.color.white)
        }

    fun isClosedArchive(status: ReportStatusUi): Boolean =
        status == ReportStatusUi.RESOLVED || status == ReportStatusUi.REJECTED

    fun filterArchiveReports(reports: List<UserReport>): List<UserReport> =
        reports.filter { isClosedArchive(it.status) }
}

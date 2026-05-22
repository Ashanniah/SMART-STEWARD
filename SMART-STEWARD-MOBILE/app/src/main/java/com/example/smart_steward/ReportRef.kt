package com.example.smart_steward

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Canonical citizen-facing report reference, e.g. `REP-20260505-WY5`. */
object ReportRef {
    private val ymdFmt = SimpleDateFormat("yyyyMMdd", Locale.US)

    fun format(docId: String, submittedAt: Date? = Date()): String {
        val ymd = ymdFmt.format(submittedAt ?: Date())
        val compact = docId.filter { it.isLetterOrDigit() }
        val suffix = when {
            compact.length >= 3 -> compact.takeLast(3).uppercase(Locale.US)
            docId.length >= 3 -> docId.takeLast(3).uppercase(Locale.US)
            else -> docId.uppercase(Locale.US).padEnd(3, 'X')
        }
        return "REP-$ymd-$suffix"
    }
}

fun UserReport.displayReportRef(): String {
    if (publicReportId.isNotBlank()) return publicReportId
    return ReportRef.format(id, submittedAt)
}

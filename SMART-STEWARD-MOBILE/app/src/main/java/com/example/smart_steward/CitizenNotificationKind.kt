package com.example.smart_steward

import androidx.annotation.StringRes

/**
 * Notification kinds stored in Firestore under `users/{uid}/inbox` as field `kind`.
 * Copy is localized via string resources.
 */
enum class CitizenNotificationKind(val key: String) {
    LIFECYCLE_SUBMITTED("lifecycle_submitted"),
    LIFECYCLE_RECEIVED("lifecycle_received"),
    LIFECYCLE_UNDER_REVIEW("lifecycle_under_review"),
    LIFECYCLE_IN_PROGRESS("lifecycle_in_progress"),
    LIFECYCLE_PENDING("lifecycle_pending"),
    LIFECYCLE_RESOLVED("lifecycle_resolved"),
    LIFECYCLE_REJECTED("lifecycle_rejected"),

    AREA_NEARBY_INCIDENT("area_nearby_incident"),
    AREA_ONGOING_HAZARD("area_ongoing_hazard"),
    AREA_EMERGENCY("area_emergency"),

    USER_MORE_INFO("user_more_info"),
    USER_EVIDENCE_NEEDED("user_evidence_needed"),
    USER_NOTIFIED_AGENCY("user_notified_agency"),

    STATUS_TRACK_REMINDER("status_track_reminder"),
    STATUS_NO_UPDATE("status_no_update"),

    ADMIN_AGENCY_MESSAGE("admin_agency_message"),
    ADMIN_COMMENT("admin_comment"),

    RESOLUTION_SUMMARY("resolution_summary"),
    RESOLUTION_PROOF("resolution_proof"),

    SYSTEM_LOGIN("system_login"),
    SYSTEM_MAINTENANCE("system_maintenance"),

    DUPLICATE_REPORT("duplicate_report"),
    AI_REVIEW("ai_review");

    @StringRes
    fun categoryRes(): Int = when (this) {
        LIFECYCLE_SUBMITTED, LIFECYCLE_RECEIVED, LIFECYCLE_UNDER_REVIEW, LIFECYCLE_IN_PROGRESS,
        LIFECYCLE_PENDING, LIFECYCLE_RESOLVED, LIFECYCLE_REJECTED -> R.string.notif_cat_report_lifecycle

        AREA_NEARBY_INCIDENT, AREA_ONGOING_HAZARD, AREA_EMERGENCY -> R.string.notif_cat_area_alerts

        USER_MORE_INFO, USER_EVIDENCE_NEEDED, USER_NOTIFIED_AGENCY -> R.string.notif_cat_user_action

        STATUS_TRACK_REMINDER, STATUS_NO_UPDATE -> R.string.notif_cat_status_interaction

        ADMIN_AGENCY_MESSAGE, ADMIN_COMMENT -> R.string.notif_cat_admin

        RESOLUTION_SUMMARY, RESOLUTION_PROOF -> R.string.notif_cat_resolution

        SYSTEM_LOGIN, SYSTEM_MAINTENANCE -> R.string.notif_cat_system

        DUPLICATE_REPORT -> R.string.notif_cat_duplicate
        AI_REVIEW -> R.string.notif_cat_ai
    }

    @StringRes
    fun titleRes(): Int = when (this) {
        LIFECYCLE_SUBMITTED -> R.string.notif_lifecycle_submitted_title
        LIFECYCLE_RECEIVED -> R.string.notif_lifecycle_received_title
        LIFECYCLE_UNDER_REVIEW -> R.string.notif_lifecycle_under_review_title
        LIFECYCLE_IN_PROGRESS -> R.string.notif_lifecycle_in_progress_title
        LIFECYCLE_PENDING -> R.string.notif_lifecycle_pending_title
        LIFECYCLE_RESOLVED -> R.string.notif_lifecycle_resolved_title
        LIFECYCLE_REJECTED -> R.string.notif_lifecycle_rejected_title

        AREA_NEARBY_INCIDENT -> R.string.notif_area_nearby_title
        AREA_ONGOING_HAZARD -> R.string.notif_area_hazard_title
        AREA_EMERGENCY -> R.string.notif_area_emergency_title

        USER_MORE_INFO -> R.string.notif_user_more_info_title
        USER_EVIDENCE_NEEDED -> R.string.notif_user_evidence_title
        USER_NOTIFIED_AGENCY -> R.string.notif_user_notified_title

        STATUS_TRACK_REMINDER -> R.string.notif_status_track_title
        STATUS_NO_UPDATE -> R.string.notif_status_sla_title

        ADMIN_AGENCY_MESSAGE -> R.string.notif_admin_agency_title
        ADMIN_COMMENT -> R.string.notif_admin_comment_title

        RESOLUTION_SUMMARY -> R.string.notif_resolution_summary_title
        RESOLUTION_PROOF -> R.string.notif_resolution_proof_title

        SYSTEM_LOGIN -> R.string.notif_system_login_title
        SYSTEM_MAINTENANCE -> R.string.notif_system_maintenance_title

        DUPLICATE_REPORT -> R.string.notif_duplicate_title
        AI_REVIEW -> R.string.notif_ai_review_title
    }

    @StringRes
    fun bodyRes(): Int = when (this) {
        LIFECYCLE_SUBMITTED -> R.string.notif_lifecycle_submitted_body
        LIFECYCLE_RECEIVED -> R.string.notif_lifecycle_received_body
        LIFECYCLE_UNDER_REVIEW -> R.string.notif_lifecycle_under_review_body
        LIFECYCLE_IN_PROGRESS -> R.string.notif_lifecycle_in_progress_body
        LIFECYCLE_PENDING -> R.string.notif_lifecycle_pending_body
        LIFECYCLE_RESOLVED -> R.string.notif_lifecycle_resolved_body
        LIFECYCLE_REJECTED -> R.string.notif_lifecycle_rejected_body

        AREA_NEARBY_INCIDENT -> R.string.notif_area_nearby_body
        AREA_ONGOING_HAZARD -> R.string.notif_area_hazard_body
        AREA_EMERGENCY -> R.string.notif_area_emergency_body

        USER_MORE_INFO -> R.string.notif_user_more_info_body
        USER_EVIDENCE_NEEDED -> R.string.notif_user_evidence_body
        USER_NOTIFIED_AGENCY -> R.string.notif_user_notified_body

        STATUS_TRACK_REMINDER -> R.string.notif_status_track_body
        STATUS_NO_UPDATE -> R.string.notif_status_sla_body

        ADMIN_AGENCY_MESSAGE -> R.string.notif_admin_agency_body
        ADMIN_COMMENT -> R.string.notif_admin_comment_body

        RESOLUTION_SUMMARY -> R.string.notif_resolution_summary_body
        RESOLUTION_PROOF -> R.string.notif_resolution_proof_body

        SYSTEM_LOGIN -> R.string.notif_system_login_body
        SYSTEM_MAINTENANCE -> R.string.notif_system_maintenance_body

        DUPLICATE_REPORT -> R.string.notif_duplicate_body
        AI_REVIEW -> R.string.notif_ai_review_body
    }

    companion object {
        fun fromKey(key: String?): CitizenNotificationKind? =
            entries.firstOrNull { it.key == key }
    }
}

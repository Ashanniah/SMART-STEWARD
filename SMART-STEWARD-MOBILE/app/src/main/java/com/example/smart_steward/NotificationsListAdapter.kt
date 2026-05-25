package com.example.smart_steward

import android.graphics.drawable.GradientDrawable
import android.text.format.DateUtils
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Date

sealed class NotificationListRow {
    data class Section(val title: String, val unreadCount: Int = 0) : NotificationListRow()
    data class Entry(val item: CitizenInboxItem) : NotificationListRow()
    object Footer : NotificationListRow()
}

enum class NotificationVisual {
    /** Orange/advisory styling reserved for area-wide alerts (nearby incident,
     *  ongoing hazard, emergency, duplicate). Not used for the user's own
     *  report-lifecycle events — those get status-coloured tiles instead. */
    ALERT,

    /** Green tile — used for lifecycle PENDING / SUBMITTED / RESOLVED and
     *  other "good news" categories. Matches the resolved status pill. */
    SUCCESS,

    INFO,

    ACTIVE,

    /** Amber/yellow tile — lifecycle transitions into an in-progress phase
     *  (received-by-agency, under-review, in-progress). Matches the
     *  in-progress status pill colour (#EAB308). */
    LIFECYCLE_PROGRESS,

    /** Red tile — lifecycle rejection. Matches the rejected status pill
     *  colour (#DC2626). Renders on the standard card chrome, NOT the
     *  orange area-advisory chrome. */
    LIFECYCLE_REJECTED
}

private enum class NotificationStatusBadge {
    ALERT,
    NEW,
    READ,
    ACTIVE,
    RESOLVED
}

private fun notificationVisual(kindKey: String?): NotificationVisual {
    val k = CitizenNotificationKind.fromKey(kindKey) ?: return NotificationVisual.INFO
    return when (k) {
        // Green tile — pending / submitted / resolved follow the user-requested
        // mapping (pending → green, resolved → green) and reuse the same mint
        // chrome as other "good news" categories.
        CitizenNotificationKind.LIFECYCLE_PENDING,
        CitizenNotificationKind.LIFECYCLE_SUBMITTED,
        CitizenNotificationKind.LIFECYCLE_RESOLVED,
        CitizenNotificationKind.RESOLUTION_SUMMARY,
        CitizenNotificationKind.RESOLUTION_PROOF -> NotificationVisual.SUCCESS

        // Amber tile — every "the report is being worked on" phase. Naming
        // is historical: LIFECYCLE_RECEIVED actually means "moved to In
        // Progress from Pending" (see strings.xml).
        CitizenNotificationKind.LIFECYCLE_RECEIVED,
        CitizenNotificationKind.LIFECYCLE_UNDER_REVIEW,
        CitizenNotificationKind.LIFECYCLE_IN_PROGRESS -> NotificationVisual.LIFECYCLE_PROGRESS

        // Red tile, regular card chrome — distinct from area advisories.
        CitizenNotificationKind.LIFECYCLE_REJECTED -> NotificationVisual.LIFECYCLE_REJECTED

        // Area-wide alerts and admin pings keep the orange advisory chrome.
        CitizenNotificationKind.AREA_NEARBY_INCIDENT,
        CitizenNotificationKind.AREA_ONGOING_HAZARD,
        CitizenNotificationKind.AREA_EMERGENCY,
        CitizenNotificationKind.USER_MORE_INFO,
        CitizenNotificationKind.USER_EVIDENCE_NEEDED,
        CitizenNotificationKind.DUPLICATE_REPORT -> NotificationVisual.ALERT

        CitizenNotificationKind.STATUS_TRACK_REMINDER -> NotificationVisual.ACTIVE

        else -> NotificationVisual.INFO
    }
}

private fun statusBadgeFor(item: CitizenInboxItem): NotificationStatusBadge {
    val k = CitizenNotificationKind.fromKey(item.kindKey)
    return when {
        k in setOf(
            CitizenNotificationKind.AREA_NEARBY_INCIDENT,
            CitizenNotificationKind.AREA_ONGOING_HAZARD,
            CitizenNotificationKind.AREA_EMERGENCY,
            CitizenNotificationKind.LIFECYCLE_REJECTED,
            CitizenNotificationKind.DUPLICATE_REPORT
        ) -> NotificationStatusBadge.ALERT

        k in setOf(
            CitizenNotificationKind.LIFECYCLE_RESOLVED,
            CitizenNotificationKind.RESOLUTION_SUMMARY,
            CitizenNotificationKind.RESOLUTION_PROOF
        ) -> NotificationStatusBadge.RESOLVED

        k in setOf(
            CitizenNotificationKind.LIFECYCLE_IN_PROGRESS,
            CitizenNotificationKind.STATUS_TRACK_REMINDER,
            CitizenNotificationKind.STATUS_NO_UPDATE
        ) -> NotificationStatusBadge.ACTIVE

        !item.read && k in setOf(
            CitizenNotificationKind.LIFECYCLE_SUBMITTED,
            CitizenNotificationKind.LIFECYCLE_RECEIVED,
            CitizenNotificationKind.LIFECYCLE_UNDER_REVIEW,
            CitizenNotificationKind.ADMIN_AGENCY_MESSAGE,
            CitizenNotificationKind.ADMIN_COMMENT
        ) -> NotificationStatusBadge.NEW

        item.read -> NotificationStatusBadge.READ
        else -> if (item.read) NotificationStatusBadge.READ else NotificationStatusBadge.NEW
    }
}

private fun agencyFromCategory(categoryLine: String): String {
    val parts = categoryLine.split("•", "·").map { it.trim() }
    return parts.lastOrNull()?.takeIf { it.isNotBlank() }.orEmpty()
}

object NotificationListRows {
    private const val NEW_CUTOFF_MS = 48L * 60L * 60L * 1000L

    fun build(
        items: List<CitizenInboxItem>,
        newSectionTitle: String,
        earlierSectionTitle: String
    ): List<NotificationListRow> {
        val sorted = items.sortedByDescending { it.createdAt?.time ?: 0L }
        val cutoff = System.currentTimeMillis() - NEW_CUTOFF_MS
        val newer = sorted.filter { (it.createdAt?.time ?: Long.MAX_VALUE) >= cutoff }
        val older = sorted.filter { (it.createdAt?.time ?: 0L) < cutoff }
        val out = mutableListOf<NotificationListRow>()
        if (newer.isNotEmpty()) {
            val unreadNew = newer.count { !it.read }
            out += NotificationListRow.Section(newSectionTitle, unreadNew)
        }
        newer.forEach { out += NotificationListRow.Entry(it) }
        if (older.isNotEmpty()) {
            out += NotificationListRow.Section(earlierSectionTitle, unreadCount = 0)
        }
        older.forEach { out += NotificationListRow.Entry(it) }
        if (sorted.isNotEmpty()) {
            out += NotificationListRow.Footer
        }
        return out
    }
}

class NotificationsListAdapter(
    private val onEntryClick: (CitizenInboxItem) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<NotificationListRow> = emptyList()

    fun submit(rows: List<NotificationListRow>) {
        this.rows = rows
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is NotificationListRow.Section -> 0
        is NotificationListRow.Entry -> 1
        NotificationListRow.Footer -> 2
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            0 -> SectionVH(
                inflater.inflate(R.layout.item_notification_section_header, parent, false)
            )

            1 -> EntryVH(
                inflater.inflate(R.layout.item_notification_card, parent, false),
                onEntryClick
            )

            else -> FooterVH(
                inflater.inflate(R.layout.item_notification_footer, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is NotificationListRow.Section -> (holder as SectionVH).bind(row)
            is NotificationListRow.Entry -> (holder as EntryVH).bind(row.item)
            NotificationListRow.Footer -> Unit
        }
    }

    override fun getItemCount(): Int = rows.size

    private class SectionVH(view: View) : RecyclerView.ViewHolder(view) {
        private val title = view.findViewById<TextView>(R.id.notifSectionTitle)
        private val badge = view.findViewById<TextView>(R.id.notifSectionUnreadBadge)

        fun bind(section: NotificationListRow.Section) {
            title.text = section.title
            if (section.unreadCount > 0) {
                badge.visibility = View.VISIBLE
                badge.text = itemView.context.getString(
                    R.string.notif_unread_badge_count,
                    section.unreadCount
                )
            } else {
                badge.visibility = View.GONE
            }
        }
    }

    private class FooterVH(view: View) : RecyclerView.ViewHolder(view)

    private class EntryVH(
        view: View,
        private val onClick: (CitizenInboxItem) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val root = view.findViewById<LinearLayout>(R.id.notifCardRoot)
        private val advisoryStrip = view.findViewById<LinearLayout>(R.id.notifAdvisoryStrip)
        private val advisoryLabel = view.findViewById<TextView>(R.id.notifAdvisoryLabel)
        private val agencyBadge = view.findViewById<TextView>(R.id.notifAgencyBadge)
        private val iconBg = view.findViewById<View>(R.id.notifItemIconBg)
        private val icon = view.findViewById<ImageView>(R.id.notifItemIcon)
        private val title = view.findViewById<TextView>(R.id.notifItemTitle)
        private val body = view.findViewById<TextView>(R.id.notifItemBody)
        private val time = view.findViewById<TextView>(R.id.notifItemTime)
        private val dot = view.findViewById<View>(R.id.notifItemDot)
        private val statusBadge = view.findViewById<TextView>(R.id.notifItemStatusBadge)

        fun bind(item: CitizenInboxItem) {
            val ctx = itemView.context
            val visual = notificationVisual(item.kindKey)
            val agency = agencyFromCategory(item.categoryLine)
                .ifBlank { ctx.getString(R.string.notif_agency_placeholder) }
            when (visual) {
                NotificationVisual.ALERT -> {
                    root.setBackgroundResource(R.drawable.bg_notif_card_advisory)
                    advisoryStrip.visibility = View.VISIBLE
                    advisoryLabel.text = ctx.getString(R.string.notif_area_advisory_label, agency)
                    agencyBadge.text = agency
                    iconBg.setBackgroundResource(R.drawable.bg_notif_icon_circle_warm)
                    setIcon(R.drawable.notification)
                }

                NotificationVisual.SUCCESS -> {
                    root.setBackgroundResource(R.drawable.bg_notif_card_standard)
                    advisoryStrip.visibility = View.GONE
                    iconBg.setBackgroundResource(R.drawable.bg_notif_icon_circle_mint)
                    setIcon(R.drawable.checked)
                }

                NotificationVisual.LIFECYCLE_PROGRESS -> {
                    root.setBackgroundResource(R.drawable.bg_notif_card_standard)
                    advisoryStrip.visibility = View.GONE
                    iconBg.setBackgroundResource(R.drawable.bg_notif_icon_circle_amber)
                    setIcon(R.drawable.notification)
                }

                NotificationVisual.LIFECYCLE_REJECTED -> {
                    root.setBackgroundResource(R.drawable.bg_notif_card_standard)
                    advisoryStrip.visibility = View.GONE
                    iconBg.setBackgroundResource(R.drawable.bg_notif_icon_circle_red)
                    setIcon(R.drawable.notification)
                }

                NotificationVisual.ACTIVE -> {
                    root.setBackgroundResource(R.drawable.bg_notif_card_standard)
                    advisoryStrip.visibility = View.GONE
                    iconBg.setBackgroundResource(R.drawable.bg_notif_icon_circle_blue)
                    setIcon(R.drawable.notification)
                }

                NotificationVisual.INFO -> {
                    root.setBackgroundResource(R.drawable.bg_notif_card_standard)
                    advisoryStrip.visibility = View.GONE
                    iconBg.setBackgroundResource(R.drawable.bg_notif_icon_circle_mint)
                    setIcon(R.drawable.notification)
                }
            }

            title.text = item.title
            body.text = formatBody(item, agency)
            time.text = formatTimestamp(item.createdAt)
            time.visibility = if (time.text.isNullOrBlank()) View.GONE else View.VISIBLE
            dot.visibility = if (item.read) View.GONE else View.VISIBLE
            if (!item.read) {
                statusBadge.visibility = View.GONE
            } else {
                applyStatusBadge(ctx, statusBadgeFor(item))
            }

            root.setOnClickListener { onClick(item) }
        }

        private fun setIcon(drawableRes: Int) {
            icon.setImageResource(drawableRes)
            icon.imageTintList = null
        }

        private fun formatBody(item: CitizenInboxItem, agency: String): String {
            val raw = item.body.trim()
            if (raw.isNotEmpty()) return raw
            return itemView.context.getString(R.string.notif_body_fallback, agency)
        }

        /**
         * Renders [createdAt] as a localized relative phrase:
         *   - "Just now"           for < 1 minute
         *   - "5 min ago"          for < 1 hour
         *   - "2 hr ago"           for < 1 day
         *   - "Yesterday"          for < 2 days
         *   - "May 24, 2026"       for older entries
         *
         * Returns an empty string when [createdAt] is null so the caller
         * can hide the label entirely.
         */
        private fun formatTimestamp(createdAt: Date?): String {
            if (createdAt == null) return ""
            val nowMs = System.currentTimeMillis()
            val thenMs = createdAt.time
            val deltaMs = nowMs - thenMs
            if (deltaMs in 0L until DateUtils.MINUTE_IN_MILLIS) {
                return "Just now"
            }
            return DateUtils.getRelativeTimeSpanString(
                thenMs,
                nowMs,
                DateUtils.MINUTE_IN_MILLIS,
                DateUtils.FORMAT_ABBREV_RELATIVE
            ).toString()
        }

        private fun applyStatusBadge(ctx: android.content.Context, badge: NotificationStatusBadge) {
            statusBadge.visibility = View.VISIBLE
            val (labelRes, bgColor, textColor) = when (badge) {
                NotificationStatusBadge.ALERT -> Triple(
                    R.string.notif_status_alert,
                    0xFFFFF8E1.toInt(),
                    ContextCompat.getColor(ctx, R.color.notif_gold_text)
                )

                NotificationStatusBadge.NEW -> Triple(
                    R.string.notif_status_read,
                    0xFFE8F5EC.toInt(),
                    ContextCompat.getColor(ctx, R.color.notif_title_green)
                )

                NotificationStatusBadge.READ -> Triple(
                    R.string.notif_status_read,
                    0xFFE8F5EC.toInt(),
                    ContextCompat.getColor(ctx, R.color.notif_title_green)
                )

                NotificationStatusBadge.ACTIVE -> Triple(
                    R.string.notif_status_active,
                    ContextCompat.getColor(ctx, R.color.profile_tile_blue),
                    0xFF1565C0.toInt()
                )

                NotificationStatusBadge.RESOLVED -> Triple(
                    R.string.notif_status_resolved,
                    0xFFE8F5EC.toInt(),
                    ContextCompat.getColor(ctx, R.color.notif_title_green)
                )
            }
            statusBadge.text = ctx.getString(labelRes)
            statusBadge.background = roundedRect(bgColor, dp(ctx, 20f))
            statusBadge.setTextColor(textColor)
        }

        private fun roundedRect(color: Int, radiusPx: Float): GradientDrawable =
            GradientDrawable().apply {
                cornerRadius = radiusPx
                setColor(color)
            }

        private fun dp(ctx: android.content.Context, dp: Float): Float =
            dp * ctx.resources.displayMetrics.density
    }
}

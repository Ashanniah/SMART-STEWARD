package com.example.smart_steward

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.util.Date
import java.util.concurrent.TimeUnit

sealed class NotificationListRow {
    data class Section(val title: String) : NotificationListRow()
    data class Entry(val item: CitizenInboxItem) : NotificationListRow()
    object Footer : NotificationListRow()
}

enum class NotificationVisual {
    ALERT,
    SUCCESS,
    INFO
}

private fun notificationVisual(kindKey: String?): NotificationVisual {
    val k = CitizenNotificationKind.fromKey(kindKey) ?: return NotificationVisual.INFO
    return when (k) {
        CitizenNotificationKind.LIFECYCLE_RESOLVED,
        CitizenNotificationKind.RESOLUTION_SUMMARY,
        CitizenNotificationKind.RESOLUTION_PROOF -> NotificationVisual.SUCCESS

        CitizenNotificationKind.AREA_NEARBY_INCIDENT,
        CitizenNotificationKind.AREA_ONGOING_HAZARD,
        CitizenNotificationKind.AREA_EMERGENCY,
        CitizenNotificationKind.LIFECYCLE_REJECTED,
        CitizenNotificationKind.USER_MORE_INFO,
        CitizenNotificationKind.USER_EVIDENCE_NEEDED,
        CitizenNotificationKind.DUPLICATE_REPORT -> NotificationVisual.ALERT

        else -> NotificationVisual.INFO
    }
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
            out += NotificationListRow.Section(newSectionTitle)
        }
        newer.forEach { out += NotificationListRow.Entry(it) }
        if (older.isNotEmpty()) {
            out += NotificationListRow.Section(earlierSectionTitle)
        }
        older.forEach { out += NotificationListRow.Entry(it) }
        if (sorted.isNotEmpty()) {
            out += NotificationListRow.Footer
        }
        return out
    }
}

private fun relativeTime(context: Context, date: Date?): String {
    if (date == null) return context.getString(R.string.notif_just_now)
    val delta = System.currentTimeMillis() - date.time
    if (delta < TimeUnit.MINUTES.toMillis(1)) {
        return context.getString(R.string.notif_just_now)
    }
    if (delta < TimeUnit.HOURS.toMillis(1)) {
        val mins = (delta / TimeUnit.MINUTES.toMillis(1)).coerceAtLeast(1).toInt()
        return if (mins == 1) {
            context.getString(R.string.notif_one_minute_ago)
        } else {
            context.getString(R.string.notif_minutes_ago, mins)
        }
    }
    if (delta < TimeUnit.DAYS.toMillis(1)) {
        val hours = (delta / TimeUnit.HOURS.toMillis(1)).coerceAtLeast(1).toInt()
        return context.getString(R.string.notif_hours_ago, hours)
    }
    val days = (delta / TimeUnit.DAYS.toMillis(1)).coerceAtLeast(1).toInt()
    return context.getString(R.string.notif_days_ago, days)
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
            is NotificationListRow.Section -> (holder as SectionVH).bind(row.title)
            is NotificationListRow.Entry -> (holder as EntryVH).bind(row.item)
            NotificationListRow.Footer -> Unit
        }
    }

    override fun getItemCount(): Int = rows.size

    private class SectionVH(view: View) : RecyclerView.ViewHolder(view) {
        private val text = view as TextView
        fun bind(title: String) {
            text.text = title
        }
    }

    private class FooterVH(view: View) : RecyclerView.ViewHolder(view)

    private class EntryVH(
        view: View,
        private val onClick: (CitizenInboxItem) -> Unit
    ) : RecyclerView.ViewHolder(view) {
        private val root = view as LinearLayout
        private val tagLine: TextView = view.findViewById(R.id.notifItemTag)
        private val dot: View = view.findViewById(R.id.notifItemDot)
        private val iconBg: View = view.findViewById(R.id.notifItemIconBg)
        private val icon: ImageView = view.findViewById(R.id.notifItemIcon)
        private val title: TextView = view.findViewById(R.id.notifItemTitle)
        private val body: TextView = view.findViewById(R.id.notifItemBody)
        private val time: TextView = view.findViewById(R.id.notifItemTime)

        fun bind(item: CitizenInboxItem) {
            val ctx = itemView.context
            val visual = notificationVisual(item.kindKey)
            val green = ContextCompat.getColor(ctx, R.color.notif_title_green)
            val gold = ContextCompat.getColor(ctx, R.color.notif_gold_text)

            when (visual) {
                NotificationVisual.ALERT -> {
                    root.setBackgroundResource(R.drawable.bg_notif_card_advisory)
                    tagLine.setTextColor(gold)
                    iconBg.setBackgroundResource(R.drawable.bg_notif_icon_circle_warm)
                    icon.setImageResource(R.drawable.notification)
                    icon.imageTintList = android.content.res.ColorStateList.valueOf(gold)
                }

                NotificationVisual.SUCCESS -> {
                    root.setBackgroundResource(R.drawable.bg_notif_card_standard)
                    tagLine.setTextColor(green)
                    iconBg.setBackgroundResource(R.drawable.bg_notif_icon_circle_mint)
                    icon.setImageResource(R.drawable.ic_ai_check_circle)
                    icon.imageTintList = null
                }

                NotificationVisual.INFO -> {
                    root.setBackgroundResource(R.drawable.bg_notif_card_standard)
                    tagLine.setTextColor(green)
                    iconBg.setBackgroundResource(R.drawable.bg_notif_icon_circle_mint)
                    icon.setImageResource(R.drawable.notification)
                    icon.imageTintList = android.content.res.ColorStateList.valueOf(green)
                }
            }

            val category = item.categoryLine.ifBlank {
                val k = CitizenNotificationKind.fromKey(item.kindKey)
                if (k != null) {
                    "${ctx.getString(k.categoryRes())} • ${ctx.getString(R.string.notif_agency_placeholder)}"
                } else {
                    ""
                }
            }
            tagLine.text = category
            title.text = item.title
            body.text = item.body
            time.text = relativeTime(ctx, item.createdAt)
            dot.visibility = if (item.read) View.GONE else View.VISIBLE

            root.setOnClickListener { onClick(item) }
        }
    }
}

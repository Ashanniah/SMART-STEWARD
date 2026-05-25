package com.example.smart_steward

import android.content.Context
import com.google.firebase.auth.FirebaseAuth

/**
 * Per-user, per-report cooldown for the citizen "Notify" action that lives in
 * the dashboard map quick-card.
 *
 * Persists the last successful notify timestamp in [SharedPreferences] keyed
 * by `${uid}__${reportId}` so:
 *
 *  - Spamming the same report is blocked for [COOLDOWN_MS] (15 minutes).
 *  - Different reports remain independently notifiable.
 *  - Cooldowns survive process death / app restarts.
 *  - Two citizens sharing one device do not steal each other's cooldowns
 *    (different uid → different key); anonymous fallback is "anon".
 */
object NotifyCooldownStore {

    private const val PREFS = "smart_steward_notify_cooldown"
    private const val AGENCIES_PREFIX = "agencies__"

    /** 15 minutes, expressed in milliseconds. */
    const val COOLDOWN_MS: Long = 15L * 60L * 1000L

    /** Convenience for the UI layer when formatting copy. */
    const val COOLDOWN_MINUTES: Int = 15

    private fun key(reportId: String): String {
        val uid = FirebaseAuth.getInstance().currentUser?.uid
            ?.takeIf { it.isNotBlank() }
            ?: "anon"
        return "${uid}__${reportId}"
    }

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /**
     * Stamps a fresh cooldown for the given report and remembers which
     * canonical agencies received the notification. The agency list is
     * surfaced later by [notifiedAgencies] so the cooldown toast can name
     * them precisely instead of just saying "wait N minutes".
     */
    fun recordNotify(
        context: Context,
        reportId: String,
        agencies: List<String> = emptyList(),
        nowMs: Long = System.currentTimeMillis(),
    ) {
        if (reportId.isBlank()) return
        val k = key(reportId)
        val editor = prefs(context).edit().putLong(k, nowMs)
        if (agencies.isNotEmpty()) {
            editor.putString(
                AGENCIES_PREFIX + k,
                agencies.filter { it.isNotBlank() }.joinToString(",")
            )
        } else {
            editor.remove(AGENCIES_PREFIX + k)
        }
        editor.apply()
    }

    /**
     * Returns the canonical agency keys (e.g. `["DENR", "PNP"]`) that
     * received the most recent Notify for this report from this user,
     * or an empty list if no notify has been recorded or the previous
     * notify pre-dates this feature.
     */
    fun notifiedAgencies(context: Context, reportId: String): List<String> {
        if (reportId.isBlank()) return emptyList()
        val raw = prefs(context).getString(AGENCIES_PREFIX + key(reportId), null) ?: return emptyList()
        return raw.split(',').map { it.trim() }.filter { it.isNotEmpty() }
    }

    /**
     * Remaining cooldown in milliseconds, or `0L` if the report can be
     * notified again right now (no cooldown active or it has elapsed).
     */
    fun remainingMs(
        context: Context,
        reportId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Long {
        if (reportId.isBlank()) return 0L
        val last = prefs(context).getLong(key(reportId), 0L)
        if (last <= 0L) return 0L
        val elapsed = nowMs - last
        return (COOLDOWN_MS - elapsed).coerceAtLeast(0L)
    }

    fun isInCooldown(
        context: Context,
        reportId: String,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean = remainingMs(context, reportId, nowMs) > 0L
}

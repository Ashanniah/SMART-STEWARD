package com.example.smart_steward

import android.content.Context

object LandingGate {
    const val EXTRA_NEXT_SCREEN = "extra_next_screen"
    const val NEXT_DASHBOARD = "dashboard"
    const val NEXT_LOGIN = "login"

    private const val PREFS_NAME = "smart_steward_prefs"
    private const val KEY_LANDING_SEEN = "landing_seen"

    fun hasSeenLanding(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_LANDING_SEEN, false)

    fun markLandingSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_LANDING_SEEN, true)
            .apply()
    }
}

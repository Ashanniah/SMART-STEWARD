package com.example.smart_steward

import android.app.Application
import android.content.pm.PackageManager
import com.example.smart_steward.api.ApiProvider
import com.google.android.libraries.places.api.Places

class SmartStewardApp : Application() {
    override fun onCreate() {
        super.onCreate()
        if (!Places.isInitialized()) {
            val key = packageManager
                .getApplicationInfo(packageName, PackageManager.GET_META_DATA)
                .metaData
                ?.getString("com.google.android.geo.API_KEY")
            if (!key.isNullOrBlank()) {
                Places.initialize(applicationContext, key)
            }
        }
        ApiProvider.init(this)
        OfflineDraftSyncManager.start(this)
        // Register the system-tray channel up front so the very first call
        // to LocalNotificationCenter.postReportNotification(...) succeeds on
        // Android 8.0+ without the channel-not-found warning.
        LocalNotificationCenter.ensureChannel(this)
    }
}

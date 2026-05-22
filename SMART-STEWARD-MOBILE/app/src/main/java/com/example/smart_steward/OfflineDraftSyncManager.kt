package com.example.smart_steward

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import java.util.concurrent.atomic.AtomicBoolean

object OfflineDraftSyncManager {
    private val syncing = AtomicBoolean(false)
    private var callbackRegistered = false

    fun start(context: Context) {
        if (callbackRegistered) return
        callbackRegistered = true
        val app = context.applicationContext
        val cm = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        cm?.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                syncNow(app)
            }
        })
        syncNow(app)
    }

    fun syncNow(context: Context) {
        val app = context.applicationContext
        if (!isOnline(app)) return
        if (!syncing.compareAndSet(false, true)) return
        syncNext(app)
    }

    private fun syncNext(context: Context) {
        val next = OfflineReportDraftStore.getAll(context).firstOrNull()
        if (next == null) {
            syncing.set(false)
            return
        }
        val photo = OfflineReportDraftStore.loadPhotoBitmap(next.photoPath)
        val videoUri = OfflineReportDraftStore.loadVideoUri(next.videoPath)
        ReportFirestore.submitReport(
            userId = next.userId,
            incidentType = next.incidentType,
            assignedAgency = next.assignedAgency,
            description = next.description,
            locationLine = next.locationLine,
            photo = photo,
            videoUri = videoUri,
            latitude = next.latitude,
            longitude = next.longitude,
            onSuccess = { _, _ ->
                OfflineReportDraftStore.remove(context, next.id)
                syncNext(context)
            },
            onError = {
                syncing.set(false)
            },
            onWarning = { /* non-fatal */ }
        )
    }

    fun isOnline(context: Context): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
            ?: return false
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

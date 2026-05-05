package com.example.smart_steward

import android.content.Context
import android.net.Uri
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * Camera/video intents usually return a temporary [content://] URI. Permissions can expire
 * after leaving the camera activity, so uploads later (after AI analysis) see an unreadable URI.
 * Copying into app cache keeps a stable file path for Firebase Storage.
 */
object MediaCapturePersistence {

    private const val TAG = "MediaCapturePersistence"

    fun copyVideoToCache(context: Context, source: Uri): Uri? {
        if (source.scheme == "file") return source
        return try {
            val cr = context.contentResolver
            val ext = when (cr.getType(source)) {
                "video/webm" -> "webm"
                "video/3gpp", "audio/3gpp" -> "3gp"
                "video/quicktime" -> "mov"
                else -> "mp4"
            }
            val dest = File(context.cacheDir, "incident_video_${System.currentTimeMillis()}.$ext")
            cr.openInputStream(source)?.use { input ->
                FileOutputStream(dest).use { output -> input.copyTo(output) }
            } ?: run {
                Log.e(TAG, "openInputStream returned null for $source")
                return null
            }
            @Suppress("DEPRECATION")
            val out = Uri.fromFile(dest)
            Log.d(TAG, "Persisted video to $out")
            out
        } catch (e: Exception) {
            Log.e(TAG, "Failed to persist video from $source", e)
            null
        }
    }
}

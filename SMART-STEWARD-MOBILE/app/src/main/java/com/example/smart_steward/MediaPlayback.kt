package com.example.smart_steward

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast

object MediaPlayback {

    fun openRemoteImage(context: Context, url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url.trim()), "image/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(Intent.createChooser(intent, context.getString(R.string.tap_to_view)))
        } catch (_: Exception) {
            // Fallback to in-app preview if the device has no gallery/photo app.
            try {
                val fallback = Intent(context, MediaPreviewActivity::class.java).apply {
                    putExtra(MediaPreviewActivity.EXTRA_KIND, MediaPreviewActivity.KIND_IMAGE)
                    putExtra(MediaPreviewActivity.EXTRA_URI, url.trim())
                    if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallback)
            } catch (_: Exception) {
                Toast.makeText(context, R.string.cannot_open_image, Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun openBitmapZoom(context: Context, bitmap: Bitmap) {
        CapturedMediaStore.capturedBitmap = bitmap
        val intent = Intent(context, MediaPreviewActivity::class.java).apply {
            putExtra(MediaPreviewActivity.EXTRA_KIND, MediaPreviewActivity.KIND_BITMAP)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.cannot_open_image, Toast.LENGTH_SHORT).show()
        }
    }

    fun openRemoteVideo(context: Context, url: String) {
        if (url.isBlank()) return
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse(url.trim()), "video/*")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.cannot_open_video, Toast.LENGTH_SHORT).show()
        }
    }

    fun openLocalVideo(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "video/*")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        try {
            context.startActivity(intent)
        } catch (_: Exception) {
            Toast.makeText(context, R.string.cannot_open_video, Toast.LENGTH_SHORT).show()
        }
    }
}

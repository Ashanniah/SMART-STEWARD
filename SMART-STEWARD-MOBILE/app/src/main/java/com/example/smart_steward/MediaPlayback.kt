package com.example.smart_steward

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.widget.Toast

/**
 * Opens images and videos inside the app via [MediaPreviewActivity] so the user can
 * pinch-zoom or scrub without leaving Smart Steward. We intentionally do NOT use
 * [Intent.ACTION_VIEW] / [Intent.createChooser] here, otherwise Android offers the
 * device's Gallery / Photos app and the user is taken out of our flow.
 */
object MediaPlayback {

    fun openRemoteImage(context: Context, url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        launchPreview(context) {
            putExtra(MediaPreviewActivity.EXTRA_KIND, MediaPreviewActivity.KIND_IMAGE)
            putExtra(MediaPreviewActivity.EXTRA_URI, trimmed)
        } ?: Toast.makeText(context, R.string.cannot_open_image, Toast.LENGTH_SHORT).show()
    }

    fun openBitmapZoom(context: Context, bitmap: Bitmap) {
        CapturedMediaStore.capturedBitmap = bitmap
        launchPreview(context) {
            putExtra(MediaPreviewActivity.EXTRA_KIND, MediaPreviewActivity.KIND_BITMAP)
        } ?: Toast.makeText(context, R.string.cannot_open_image, Toast.LENGTH_SHORT).show()
    }

    fun openRemoteVideo(context: Context, url: String) {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return
        launchPreview(context) {
            putExtra(MediaPreviewActivity.EXTRA_KIND, MediaPreviewActivity.KIND_VIDEO)
            putExtra(MediaPreviewActivity.EXTRA_URI, trimmed)
        } ?: Toast.makeText(context, R.string.cannot_open_video, Toast.LENGTH_SHORT).show()
    }

    fun openLocalVideo(context: Context, uri: Uri) {
        launchPreview(context) {
            putExtra(MediaPreviewActivity.EXTRA_KIND, MediaPreviewActivity.KIND_VIDEO)
            putExtra(MediaPreviewActivity.EXTRA_URI, uri.toString())
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } ?: Toast.makeText(context, R.string.cannot_open_video, Toast.LENGTH_SHORT).show()
    }

    private inline fun launchPreview(
        context: Context,
        configure: Intent.() -> Unit
    ): Unit? {
        val intent = Intent(context, MediaPreviewActivity::class.java).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            configure()
        }
        return try {
            context.startActivity(intent)
            Unit
        } catch (_: Exception) {
            null
        }
    }
}

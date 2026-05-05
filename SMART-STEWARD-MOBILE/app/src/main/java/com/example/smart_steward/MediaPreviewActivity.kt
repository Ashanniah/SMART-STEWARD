package com.example.smart_steward

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.MediaController
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import coil.load

class MediaPreviewActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_KIND = "kind"
        const val EXTRA_URI = "uri"
        const val KIND_IMAGE = "image"
        const val KIND_VIDEO = "video"
        const val KIND_BITMAP = "bitmap"
    }

    private lateinit var previewImage: ImageView
    private lateinit var previewVideo: VideoView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_media_preview)

        previewImage = findViewById(R.id.mediaPreviewImage)
        previewVideo = findViewById(R.id.mediaPreviewVideo)

        findViewById<View>(R.id.mediaPreviewClose).setOnClickListener { finish() }

        when (intent.getStringExtra(EXTRA_KIND)) {
            KIND_VIDEO -> showVideo(intent.getStringExtra(EXTRA_URI))
            KIND_BITMAP -> showBitmap()
            else -> showImage(intent.getStringExtra(EXTRA_URI))
        }
    }

    override fun onPause() {
        super.onPause()
        if (::previewVideo.isInitialized && previewVideo.isPlaying) {
            previewVideo.pause()
        }
    }

    override fun onDestroy() {
        if (::previewVideo.isInitialized) {
            previewVideo.stopPlayback()
        }
        super.onDestroy()
    }

    private fun showImage(uriString: String?) {
        val uri = uriString?.trim().orEmpty()
        if (uri.isBlank()) {
            finish()
            return
        }
        previewVideo.visibility = View.GONE
        previewImage.visibility = View.VISIBLE
        previewImage.load(uri) {
            crossfade(true)
        }
    }

    private fun showBitmap() {
        val bitmap = CapturedMediaStore.capturedBitmap
        if (bitmap == null) {
            finish()
            return
        }
        previewVideo.visibility = View.GONE
        previewImage.visibility = View.VISIBLE
        previewImage.setImageBitmap(bitmap)
    }

    private fun showVideo(uriString: String?) {
        val uri = uriString?.trim().orEmpty()
        if (uri.isBlank()) {
            finish()
            return
        }
        previewImage.visibility = View.GONE
        previewVideo.visibility = View.VISIBLE
        val controller = MediaController(this)
        controller.setAnchorView(previewVideo)
        previewVideo.setMediaController(controller)
        previewVideo.setOnErrorListener { _, _, _ ->
            finish()
            true
        }
        previewVideo.setOnPreparedListener { mp ->
            mp.isLooping = false
            previewVideo.start()
        }
        previewVideo.setVideoURI(Uri.parse(uri))
        previewVideo.requestFocus()
    }
}

package com.example.smart_steward

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import coil.decode.VideoFrameDecoder
import coil.load
import coil.request.videoFrameMillis
import coil.transform.RoundedCornersTransformation
import java.util.Locale

object ReportThumbnailBinder {

    fun bind(
        context: Context,
        report: UserReport,
        thumb: ImageView,
        thumbContainer: View,
        videoPlay: ImageView,
        emojiFallback: TextView,
        cornerDp: Float = 10f,
        typeVisuals: (String) -> Pair<String, Int> = ::defaultTypeVisuals
    ) {
        val cornerPx = cornerDp * context.resources.displayMetrics.density
        val (emoji, tileColorRes) = typeVisuals(report.incidentType)
        emojiFallback.text = emoji
        emojiFallback.background = roundedRect(
            ContextCompat.getColor(context, tileColorRes),
            cornerPx
        )

        val imageUrl = report.photoUrl.trim()
        val videoUrl = report.videoUrl.trim()

        fun showEmojiFallback() {
            thumb.setImageDrawable(null)
            thumb.visibility = View.GONE
            emojiFallback.visibility = View.VISIBLE
        }

        fun showImage() {
            emojiFallback.visibility = View.GONE
            thumb.visibility = View.VISIBLE
        }

        when {
            imageUrl.isNotEmpty() -> {
                showImage()
                thumb.load(imageUrl) {
                    crossfade(true)
                    transformations(RoundedCornersTransformation(cornerPx))
                    listener(
                        onError = { _, _ -> showEmojiFallback() },
                        onCancel = { showEmojiFallback() }
                    )
                }
            }
            videoUrl.isNotEmpty() -> {
                showImage()
                thumb.load(videoUrl) {
                    crossfade(true)
                    decoderFactory { result, options, _ ->
                        VideoFrameDecoder(result.source, options)
                    }
                    videoFrameMillis(0)
                    transformations(RoundedCornersTransformation(cornerPx))
                    listener(
                        onError = { _, _ -> showEmojiFallback() },
                        onCancel = { showEmojiFallback() }
                    )
                }
            }
            else -> showEmojiFallback()
        }

        if (videoUrl.isNotEmpty()) {
            videoPlay.visibility = View.VISIBLE
            thumbContainer.setOnClickListener {
                MediaPlayback.openRemoteVideo(context, videoUrl)
            }
        } else if (imageUrl.isNotEmpty()) {
            videoPlay.visibility = View.GONE
            thumbContainer.setOnClickListener {
                MediaPlayback.openRemoteImage(context, imageUrl)
            }
        } else {
            videoPlay.visibility = View.GONE
            thumbContainer.setOnClickListener(null)
        }
    }

    private fun defaultTypeVisuals(incidentType: String): Pair<String, Int> {
        val t = incidentType.lowercase(Locale.getDefault())
        return when {
            t.contains("burn") || t.contains("fire") -> "🔥" to R.color.activity_pending_orange
            t.contains("log") || t.contains("tree") || t.contains("forest") ->
                "🌳" to R.color.activity_resolved_green
            t.contains("poach") || t.contains("hunt") || t.contains("wildlife") ->
                "🛡️" to R.color.activity_progress_blue
            else -> "📋" to R.color.activity_chip_bg
        }
    }

    private fun roundedRect(color: Int, radiusPx: Float): GradientDrawable =
        GradientDrawable().apply {
            cornerRadius = radiusPx
            setColor(color)
        }
}

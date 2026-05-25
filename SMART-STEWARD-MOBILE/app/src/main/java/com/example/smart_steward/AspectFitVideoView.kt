package com.example.smart_steward

import android.content.Context
import android.util.AttributeSet
import android.widget.VideoView

/**
 * [VideoView] subclass that sizes itself to match the playing video's
 * aspect ratio while staying inside its parent's bounds.
 *
 * The stock [VideoView] uses the surface's own dimensions, so when its
 * `layout_width`/`layout_height` are `match_parent` the video frame gets
 * pinned to the top-left of the surface and only fills part of the screen
 * (you end up with a large black band below it). By overriding [onMeasure]
 * to fit-inside the parent the view occupies just the video's bounds and
 * `layout_gravity="center"` then visually centres it inside the popup.
 */
class AspectFitVideoView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : VideoView(context, attrs, defStyleAttr) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val videoWidth = getDefaultSize(0, widthMeasureSpec)
        val videoHeight = getDefaultSize(0, heightMeasureSpec)
        if (videoWidth <= 0 || videoHeight <= 0) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            return
        }

        val intrinsicW = if (mVideoWidthSafe > 0) mVideoWidthSafe else videoWidth
        val intrinsicH = if (mVideoHeightSafe > 0) mVideoHeightSafe else videoHeight

        val widthRatio = videoWidth.toFloat() / intrinsicW
        val heightRatio = videoHeight.toFloat() / intrinsicH
        val scale = minOf(widthRatio, heightRatio)
        val scaledWidth = (intrinsicW * scale).toInt().coerceAtLeast(1)
        val scaledHeight = (intrinsicH * scale).toInt().coerceAtLeast(1)
        setMeasuredDimension(scaledWidth, scaledHeight)
    }

    /**
     * [VideoView] exposes video dimensions via package-private fields; we
     * read them through [getDuration] / [getCurrentPosition] only after
     * `onPrepared`, so we cache the intrinsic size when the host activity
     * forwards it. When the size is unknown we just fall back to the
     * parent measure (covered by the early-return above).
     */
    private var mVideoWidthSafe: Int = 0
    private var mVideoHeightSafe: Int = 0

    fun setVideoIntrinsicSize(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == mVideoWidthSafe && height == mVideoHeightSafe) return
        mVideoWidthSafe = width
        mVideoHeightSafe = height
        requestLayout()
    }
}

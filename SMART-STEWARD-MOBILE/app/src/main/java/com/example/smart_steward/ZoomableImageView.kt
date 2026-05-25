package com.example.smart_steward

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView

/**
 * Minimal pan + pinch-to-zoom [AppCompatImageView] used inside the in-app
 * [MediaPreviewActivity] popup. Supports:
 *  - pinch to zoom (1x .. 5x)
 *  - one-finger pan once zoomed in
 *  - double-tap to toggle between fit and 2.5x
 */
class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    companion object {
        private const val MIN_SCALE = 1.0f
        private const val MAX_SCALE = 5.0f
        private const val DOUBLE_TAP_SCALE = 2.5f
    }

    private val transform = Matrix()
    private val tmpValues = FloatArray(9)
    private val lastTouch = PointF()
    private var activePointerId = MotionEvent.INVALID_POINTER_ID

    private val scaleDetector = ScaleGestureDetector(context, object :
        ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val current = currentScale()
            var factor = detector.scaleFactor
            val target = (current * factor).coerceIn(MIN_SCALE, MAX_SCALE)
            factor = target / current
            transform.postScale(factor, factor, detector.focusX, detector.focusY)
            clampTranslation()
            imageMatrix = transform
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object :
        GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val current = currentScale()
            val target = if (current > MIN_SCALE + 0.05f) MIN_SCALE else DOUBLE_TAP_SCALE
            val factor = target / current
            transform.postScale(factor, factor, e.x, e.y)
            clampTranslation()
            imageMatrix = transform
            return true
        }
    })

    init {
        scaleType = ScaleType.MATRIX
        super.setImageMatrix(transform)
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        post { fitImageToView() }
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        post { fitImageToView() }
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        post { fitImageToView() }
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        fitImageToView()
    }

    private fun fitImageToView() {
        val drawable = drawable ?: return
        val viewW = (width - paddingLeft - paddingRight).toFloat()
        val viewH = (height - paddingTop - paddingBottom).toFloat()
        val intrW = drawable.intrinsicWidth.toFloat().takeIf { it > 0f } ?: return
        val intrH = drawable.intrinsicHeight.toFloat().takeIf { it > 0f } ?: return
        if (viewW <= 0f || viewH <= 0f) return
        val scale = minOf(viewW / intrW, viewH / intrH)
        val dx = (viewW - intrW * scale) / 2f + paddingLeft
        val dy = (viewH - intrH * scale) / 2f + paddingTop
        transform.reset()
        transform.postScale(scale, scale)
        transform.postTranslate(dx, dy)
        imageMatrix = transform
    }

    private fun currentScale(): Float {
        transform.getValues(tmpValues)
        return tmpValues[Matrix.MSCALE_X]
    }

    private fun clampTranslation() {
        val drawable = drawable ?: return
        transform.getValues(tmpValues)
        val scale = tmpValues[Matrix.MSCALE_X]
        val transX = tmpValues[Matrix.MTRANS_X]
        val transY = tmpValues[Matrix.MTRANS_Y]
        val contentW = drawable.intrinsicWidth * scale
        val contentH = drawable.intrinsicHeight * scale
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val minTransX: Float
        val maxTransX: Float
        if (contentW <= viewW) {
            minTransX = (viewW - contentW) / 2f
            maxTransX = minTransX
        } else {
            minTransX = viewW - contentW
            maxTransX = 0f
        }
        val minTransY: Float
        val maxTransY: Float
        if (contentH <= viewH) {
            minTransY = (viewH - contentH) / 2f
            maxTransY = minTransY
        } else {
            minTransY = viewH - contentH
            maxTransY = 0f
        }
        val dx = transX.coerceIn(minTransX, maxTransX) - transX
        val dy = transY.coerceIn(minTransY, maxTransY) - transY
        if (dx != 0f || dy != 0f) {
            transform.postTranslate(dx, dy)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouch.set(event.x, event.y)
                activePointerId = event.getPointerId(0)
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && currentScale() > MIN_SCALE + 0.01f) {
                    val pointerIndex = event.findPointerIndex(activePointerId)
                    if (pointerIndex >= 0) {
                        val x = event.getX(pointerIndex)
                        val y = event.getY(pointerIndex)
                        transform.postTranslate(x - lastTouch.x, y - lastTouch.y)
                        clampTranslation()
                        imageMatrix = transform
                        lastTouch.set(x, y)
                    }
                }
            }
            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_CANCEL -> {
                activePointerId = MotionEvent.INVALID_POINTER_ID
                parent?.requestDisallowInterceptTouchEvent(false)
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                val pointerId = event.getPointerId(pointerIndex)
                if (pointerId == activePointerId) {
                    val newIndex = if (pointerIndex == 0) 1 else 0
                    lastTouch.set(event.getX(newIndex), event.getY(newIndex))
                    activePointerId = event.getPointerId(newIndex)
                }
            }
        }
        return true
    }
}

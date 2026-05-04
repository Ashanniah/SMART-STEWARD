package com.example.smart_steward

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

class AiAnalysisActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REANALYZE = "reanalyze"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val totalMs = 14_000L
    private val tickMs = 50L
    private var elapsed = 0L
    private var reanalyze = false

    private lateinit var timeRemaining: TextView
    private lateinit var overallProgress: ProgressBar
    private lateinit var headline: TextView
    private lateinit var subheadline: TextView
    private lateinit var reportMeta: TextView

    private lateinit var step1Card: View
    private lateinit var step1Icon: ImageView
    private lateinit var step1Title: TextView
    private lateinit var step1Subtitle: TextView
    private lateinit var step1Badge: TextView
    private lateinit var step1Bar: ProgressBar

    private lateinit var step2Card: View
    private lateinit var step2Icon: ImageView
    private lateinit var step2Title: TextView
    private lateinit var step2Subtitle: TextView
    private lateinit var step2Badge: TextView
    private lateinit var step2Bar: ProgressBar

    private lateinit var step3Card: View
    private lateinit var step3Icon: ImageView
    private lateinit var step3Title: TextView
    private lateinit var step3Subtitle: TextView
    private lateinit var step3Badge: TextView
    private lateinit var step3Bar: ProgressBar

    private lateinit var step4Card: View
    private lateinit var step4Icon: ImageView
    private lateinit var step4Title: TextView
    private lateinit var step4Subtitle: TextView
    private lateinit var step4Badge: TextView
    private lateinit var step4Bar: ProgressBar

    private val tickRunnable = object : Runnable {
        override fun run() {
            elapsed += tickMs
            val t = (elapsed.toFloat() / totalMs).coerceIn(0f, 1f)
            applyPipelineUi(t)
            if (elapsed < totalMs) {
                handler.postDelayed(this, tickMs)
            } else {
                completeOk()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_analysis)

        reanalyze = intent.getBooleanExtra(EXTRA_REANALYZE, false)

        timeRemaining = findViewById(R.id.aiTimeRemaining)
        overallProgress = findViewById(R.id.aiOverallProgress)
        headline = findViewById(R.id.aiHeadline)
        subheadline = findViewById(R.id.aiSubheadline)
        reportMeta = findViewById(R.id.aiReportMeta)

        step1Card = findViewById(R.id.aiStep1Card)
        step1Icon = findViewById(R.id.aiStep1Icon)
        step1Title = findViewById(R.id.aiStep1Title)
        step1Subtitle = findViewById(R.id.aiStep1Subtitle)
        step1Badge = findViewById(R.id.aiStep1Badge)
        step1Bar = findViewById(R.id.aiStep1RunningBar)

        step2Card = findViewById(R.id.aiStep2Card)
        step2Icon = findViewById(R.id.aiStep2Icon)
        step2Title = findViewById(R.id.aiStep2Title)
        step2Subtitle = findViewById(R.id.aiStep2Subtitle)
        step2Badge = findViewById(R.id.aiStep2Badge)
        step2Bar = findViewById(R.id.aiStep2RunningBar)

        step3Card = findViewById(R.id.aiStep3Card)
        step3Icon = findViewById(R.id.aiStep3Icon)
        step3Title = findViewById(R.id.aiStep3Title)
        step3Subtitle = findViewById(R.id.aiStep3Subtitle)
        step3Badge = findViewById(R.id.aiStep3Badge)
        step3Bar = findViewById(R.id.aiStep3RunningBar)

        step4Card = findViewById(R.id.aiStep4Card)
        step4Icon = findViewById(R.id.aiStep4Icon)
        step4Title = findViewById(R.id.aiStep4Title)
        step4Subtitle = findViewById(R.id.aiStep4Subtitle)
        step4Badge = findViewById(R.id.aiStep4Badge)
        step4Bar = findViewById(R.id.aiStep4RunningBar)

        if (reanalyze) {
            headline.setText(R.string.ai_headline_reanalyze)
            subheadline.setText(R.string.ai_subheadline_reanalyze)
        }

        bindStaticCopy()
        findViewById<ImageView>(R.id.aiBackButton).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        applyPipelineUi(0f)
        handler.post(tickRunnable)
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    /** Photos shown in UI (demo uses 3 when a still image was captured). */
    private fun photoCountForUi(): Int =
        if (CapturedMediaStore.capturedBitmap != null) 3 else 1

    private fun mediaLabel(): String =
        if (CapturedMediaStore.capturedBitmap != null) {
            val n = photoCountForUi()
            if (n == 1) getString(R.string.ai_media_photo_one)
            else getString(R.string.ai_media_photos_fmt, n)
        } else {
            getString(R.string.ai_media_video_one)
        }

    private fun bindStaticCopy() {
        step1Title.setText(R.string.ai_step_media_title)
        step2Title.setText(R.string.ai_step_location_title)
        step3Title.setText(R.string.ai_step_classify_title)
        step4Title.setText(R.string.ai_step_route_title)

        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
        reportMeta.text = getString(
            R.string.ai_report_meta_fmt,
            "Brgy. Labangon",
            dateStr,
            mediaLabel()
        )
    }

    private fun applyPipelineUi(t: Float) {
        overallProgress.progress = (t * 100).toInt().coerceIn(0, 100)
        val secLeft = ceil((1f - t.coerceIn(0f, 1f)) * 15.0).toInt().coerceAtLeast(0)
        timeRemaining.text = getString(R.string.ai_time_remaining_fmt, secLeft)

        val n = max(photoCountForUi(), 1)
        val mediaDoneSubtitle = getString(R.string.ai_step_media_sub_done, n)

        when {
            t < 0.18f -> {
                stepRunning(
                    step1Card, step1Icon, step1Subtitle, step1Badge, step1Bar,
                    getString(R.string.ai_step_media_sub_running),
                    activeCard = false
                )
                stepQueued(
                    step2Card, step2Icon, step2Subtitle, step2Badge, step2Bar,
                    alpha = 1f,
                    line = getString(R.string.ai_location_placeholder)
                )
                stepQueued(
                    step3Card, step3Icon, step3Subtitle, step3Badge, step3Bar,
                    alpha = 1f,
                    line = getString(R.string.ai_step_queued_generic)
                )
                stepQueued(
                    step4Card, step4Icon, step4Subtitle, step4Badge, step4Bar,
                    alpha = 0.65f,
                    line = getString(R.string.ai_step_route_sub_queued)
                )
            }
            t < 0.38f -> {
                stepDone(step1Card, step1Icon, step1Subtitle, step1Badge, step1Bar, mediaDoneSubtitle)
                stepRunning(
                    step2Card, step2Icon, step2Subtitle, step2Badge, step2Bar,
                    getString(R.string.ai_step_location_sub_running),
                    activeCard = false
                )
                stepQueued(
                    step3Card, step3Icon, step3Subtitle, step3Badge, step3Bar,
                    alpha = 1f,
                    line = getString(R.string.ai_step_queued_generic)
                )
                stepQueued(
                    step4Card, step4Icon, step4Subtitle, step4Badge, step4Bar,
                    alpha = 0.65f,
                    line = getString(R.string.ai_step_route_sub_queued)
                )
            }
            t < 0.72f -> {
                stepDone(step1Card, step1Icon, step1Subtitle, step1Badge, step1Bar, mediaDoneSubtitle)
                stepDone(
                    step2Card, step2Icon, step2Subtitle, step2Badge, step2Bar,
                    getString(R.string.ai_step_location_sub_done)
                )
                stepRunning(
                    step3Card, step3Icon, step3Subtitle, step3Badge, step3Bar,
                    getString(R.string.ai_step_classify_sub_running),
                    activeCard = true
                )
                stepQueued(
                    step4Card, step4Icon, step4Subtitle, step4Badge, step4Bar,
                    alpha = 0.65f,
                    line = getString(R.string.ai_step_route_sub_queued)
                )
            }
            t < 0.94f -> {
                stepDone(step1Card, step1Icon, step1Subtitle, step1Badge, step1Bar, mediaDoneSubtitle)
                stepDone(
                    step2Card, step2Icon, step2Subtitle, step2Badge, step2Bar,
                    getString(R.string.ai_step_location_sub_done)
                )
                stepDone(
                    step3Card, step3Icon, step3Subtitle, step3Badge, step3Bar,
                    getString(R.string.ai_step_classify_sub_done)
                )
                stepRunning(
                    step4Card, step4Icon, step4Subtitle, step4Badge, step4Bar,
                    getString(R.string.ai_step_route_sub_running),
                    activeCard = true
                )
            }
            else -> {
                stepDone(step1Card, step1Icon, step1Subtitle, step1Badge, step1Bar, mediaDoneSubtitle)
                stepDone(
                    step2Card, step2Icon, step2Subtitle, step2Badge, step2Bar,
                    getString(R.string.ai_step_location_sub_done)
                )
                stepDone(
                    step3Card, step3Icon, step3Subtitle, step3Badge, step3Bar,
                    getString(R.string.ai_step_classify_sub_done)
                )
                stepDone(
                    step4Card, step4Icon, step4Subtitle, step4Badge, step4Bar,
                    getString(R.string.ai_step_route_sub_queued)
                )
            }
        }
    }

    private fun stepDone(
        card: View,
        icon: ImageView,
        subtitle: TextView,
        badge: TextView,
        bar: ProgressBar,
        subtitleText: String
    ) {
        card.alpha = 1f
        card.setBackgroundResource(R.drawable.bg_ai_card)
        icon.setImageResource(R.drawable.ic_ai_check_circle)
        subtitle.text = subtitleText
        bar.visibility = View.GONE
        bar.isIndeterminate = false
        badge.text = getString(R.string.ai_status_done)
        badge.setBackgroundResource(R.drawable.bg_ai_badge_done)
        badge.setTextColor(ContextCompat.getColor(this, R.color.ai_primary))
    }

    private fun stepRunning(
        card: View,
        icon: ImageView,
        subtitle: TextView,
        badge: TextView,
        bar: ProgressBar,
        subtitleText: String,
        activeCard: Boolean
    ) {
        card.alpha = 1f
        card.setBackgroundResource(
            if (activeCard) R.drawable.bg_ai_card_active else R.drawable.bg_ai_card
        )
        icon.setImageResource(R.drawable.ic_ai_running_icon)
        subtitle.text = subtitleText
        bar.visibility = View.VISIBLE
        bar.isIndeterminate = true
        badge.text = getString(R.string.ai_status_running)
        badge.setBackgroundResource(R.drawable.bg_ai_badge_running)
        badge.setTextColor(ContextCompat.getColor(this, R.color.white))
    }

    private fun stepQueued(
        card: View,
        icon: ImageView,
        subtitleView: TextView,
        badge: TextView,
        bar: ProgressBar,
        alpha: Float,
        line: String
    ) {
        card.alpha = alpha
        card.setBackgroundResource(R.drawable.bg_ai_card)
        icon.setImageResource(R.drawable.ic_ai_clock)
        subtitleView.text = line
        bar.visibility = View.GONE
        bar.isIndeterminate = false
        badge.text = getString(R.string.ai_status_queued)
        badge.setBackgroundResource(R.drawable.bg_ai_badge_queued_muted)
        badge.setTextColor(ContextCompat.getColor(this, R.color.ai_badge_queued_text))
    }

    private fun completeOk() {
        setResult(Activity.RESULT_OK)
        finish()
    }
}


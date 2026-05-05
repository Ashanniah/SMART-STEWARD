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
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.smart_steward.BuildConfig
import com.example.smart_steward.net.SmartStewardAiClient
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.ceil

class AiAnalysisActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_REANALYZE = "reanalyze"
        /** Optional user text (e.g. corrected description) sent as multipart `message`. */
        const val EXTRA_USER_MESSAGE = "extra_user_message"
        const val EXTRA_INCIDENT_TITLE = "extra_incident_title"
        const val EXTRA_INCIDENT_SUBTITLE = "extra_incident_subtitle"
        const val EXTRA_AGENCY_TITLE = "extra_agency_title"
        const val EXTRA_AGENCY_SUBLINE = "extra_agency_subline"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_LOCATION_SHORT = "extra_location_short"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val totalMs = 5_000L
    private val countdownSeconds = 5
    private val tickMs = 50L
    private var elapsed = 0L
    private var reanalyze = false
    private var userMessage: String? = null

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var animationFinished = false
    @Volatile
    private var apiFinished = false
    @Volatile
    private var apiResultIntent: Intent? = null
    @Volatile
    private var apiError: String? = null
    private var finalized = false

    /** True after [startAiRequest] has been scheduled (location resolved first so API gets real device label). */
    private var aiRequestStarted = false

    /** Reverse-geocoded or coordinate label; used in UI and passed to the review screen. */
    @Volatile
    private var resolvedLocationShort: String? = null

    private var lastPipelineT = 0f

    private lateinit var timeRemaining: TextView
    private lateinit var overallProgress: ProgressBar
    private lateinit var headline: TextView
    private lateinit var subheadline: TextView
    private lateinit var reportMeta: TextView
    private lateinit var reportIncidentTitle: TextView

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
                animationFinished = true
                tryFinalize()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_analysis)

        reanalyze = intent.getBooleanExtra(EXTRA_REANALYZE, false)
        userMessage = intent.getStringExtra(EXTRA_USER_MESSAGE)

        timeRemaining = findViewById(R.id.aiTimeRemaining)
        overallProgress = findViewById(R.id.aiOverallProgress)
        headline = findViewById(R.id.aiHeadline)
        subheadline = findViewById(R.id.aiSubheadline)
        reportMeta = findViewById(R.id.aiReportMeta)
        reportIncidentTitle = findViewById(R.id.aiReportIncidentTitle)

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
        LocationLabelHelper.resolveShortLabel(this) { label ->
            resolvedLocationShort = label
            bindStaticCopy()
            applyPipelineUi(lastPipelineT)
            if (!aiRequestStarted) {
                aiRequestStarted = true
                startAiRequest()
            }
        }
        findViewById<ImageView>(R.id.aiBackButton).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        applyPipelineUi(0f)
        handler.post(tickRunnable)
    }

    /** Runs after device location label is ready so the AI request and UI show the user’s area (not a stale fallback). */
    private fun startAiRequest() {
        executor.execute {
            try {
                val locForApi = resolvedLocationShort?.trim()?.takeIf { it.isNotEmpty() }
                val result = SmartStewardAiClient.analyzeToResultIntent(
                    applicationContext,
                    BuildConfig.API_BASE_URL,
                    userMessage,
                    reanalyze,
                    locForApi
                )
                result.apply {
                    locForApi?.let { putExtra(EXTRA_LOCATION_SHORT, it) }
                }
                apiResultIntent = result
                apiError = null
                runOnUiThread {
                    result.getStringExtra(EXTRA_INCIDENT_TITLE)
                        ?.takeIf { it.isNotBlank() }
                        ?.let { reportIncidentTitle.text = it }
                }
            } catch (e: Exception) {
                apiResultIntent = null
                apiError = e.message ?: e.javaClass.simpleName
            } finally {
                apiFinished = true
                runOnUiThread { tryFinalize() }
            }
        }
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun tryFinalize() {
        if (isFinishing) return
        if (!animationFinished || !apiFinished || finalized) return
        finalized = true
        val intent = apiResultIntent ?: buildAnalysisResultIntent().also {
            apiError?.let { msg ->
                Toast.makeText(this, getString(R.string.ai_api_fallback_toast, msg), Toast.LENGTH_LONG).show()
            }
        }
        resolvedLocationShort?.trim()?.takeIf { it.isNotEmpty() }?.let { loc ->
            intent.putExtra(EXTRA_LOCATION_SHORT, loc)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun mediaLabel(): String =
        if (CapturedMediaStore.capturedBitmap != null) {
            getString(R.string.ai_media_photo_one)
        } else {
            getString(R.string.ai_media_video_one)
        }

    private fun mediaScanDoneSubtitle(): String =
        when {
            CapturedMediaStore.capturedBitmap != null -> getString(R.string.ai_step_media_done_photo)
            CapturedMediaStore.capturedVideoUri != null -> getString(R.string.ai_step_media_done_video)
            else -> getString(R.string.ai_step_media_done_photo)
        }

    private fun mediaRunningSubtitle(): String =
        when {
            CapturedMediaStore.capturedBitmap != null -> getString(R.string.ai_step_media_sub_running_photo)
            CapturedMediaStore.capturedVideoUri != null -> getString(R.string.ai_step_media_sub_running_video)
            else -> getString(R.string.ai_step_media_sub_running)
        }

    /** Card headline driven by re-analysis vs image vs video capture. */
    private fun preliminaryReportTitle(): String =
        when {
            reanalyze -> getString(R.string.ai_report_title_reanalyze)
            CapturedMediaStore.capturedVideoUri != null && CapturedMediaStore.capturedBitmap == null ->
                getString(R.string.ai_report_title_video_evidence)
            else -> getString(R.string.ai_report_pending_title)
        }

    private fun bindStaticCopy() {
        step1Title.setText(R.string.ai_step_media_title)
        step2Title.setText(R.string.ai_step_location_title)
        step3Title.setText(R.string.ai_step_classify_title)
        step4Title.setText(R.string.ai_step_route_title)

        reportIncidentTitle.text = apiResultIntent?.getStringExtra(EXTRA_INCIDENT_TITLE)
            ?: preliminaryReportTitle()

        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(Date())
        reportMeta.text = getString(
            R.string.ai_report_meta_fmt,
            locationDisplayForUi(),
            dateStr,
            mediaLabel()
        )
    }

    private fun locationDisplayForUi(): String =
        resolvedLocationShort?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.ai_location_placeholder)

    private fun locationStepDoneSubtitle(): String =
        getString(R.string.ai_step_location_sub_done_fmt, locationDisplayForUi())

    private fun applyPipelineUi(t: Float) {
        lastPipelineT = t
        overallProgress.progress = (t * 100).toInt().coerceIn(0, 100)
        val secLeft = ceil((1f - t.coerceIn(0f, 1f)) * countdownSeconds.toDouble()).toInt().coerceAtLeast(0)
        timeRemaining.text = getString(R.string.ai_time_remaining_fmt, secLeft)

        val mediaDoneSubtitle = mediaScanDoneSubtitle()

        when {
            t < 0.18f -> {
                stepRunning(
                    step1Card, step1Icon, step1Subtitle, step1Badge, step1Bar,
                    mediaRunningSubtitle(),
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
                    locationStepDoneSubtitle()
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
                    locationStepDoneSubtitle()
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
                    locationStepDoneSubtitle()
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

    private fun fallbackLocationExtra(): String =
        resolvedLocationShort?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.review_location_short_default)

    private fun buildAnalysisResultIntent(): Intent {
        val hasPhoto = CapturedMediaStore.capturedBitmap != null
        val hasVideo = CapturedMediaStore.capturedVideoUri != null
        return Intent().apply {
            if (reanalyze) {
                putExtra(EXTRA_INCIDENT_TITLE, getString(R.string.review_detected_incident_default))
                putExtra(EXTRA_INCIDENT_SUBTITLE, getString(R.string.review_incident_subtitle_default))
                putExtra(EXTRA_AGENCY_TITLE, getString(R.string.review_detected_agency_title_default))
                putExtra(
                    EXTRA_AGENCY_SUBLINE,
                    getString(
                        R.string.review_agency_subtitle_fmt,
                        getString(R.string.review_detected_agency_short_default)
                    )
                )
                putExtra(EXTRA_DESCRIPTION, getString(R.string.ai_result_desc_reanalyze))
                putExtra(EXTRA_LOCATION_SHORT, fallbackLocationExtra())
            } else {
                when {
                    hasPhoto && hasVideo -> {
                        putExtra(EXTRA_INCIDENT_TITLE, getString(R.string.review_detected_incident_default))
                        putExtra(EXTRA_INCIDENT_SUBTITLE, getString(R.string.review_incident_subtitle_default))
                        putExtra(EXTRA_AGENCY_TITLE, getString(R.string.review_detected_agency_title_default))
                        putExtra(
                            EXTRA_AGENCY_SUBLINE,
                            getString(
                                R.string.review_agency_subtitle_fmt,
                                getString(R.string.review_detected_agency_short_default)
                            )
                        )
                        putExtra(EXTRA_DESCRIPTION, getString(R.string.ai_result_desc_mixed))
                        putExtra(EXTRA_LOCATION_SHORT, fallbackLocationExtra())
                    }
                    hasVideo && !hasPhoto -> {
                        putExtra(EXTRA_INCIDENT_TITLE, getString(R.string.ai_result_title_video_focus))
                        putExtra(EXTRA_INCIDENT_SUBTITLE, getString(R.string.ai_result_subtitle_transport))
                        putExtra(EXTRA_AGENCY_TITLE, getString(R.string.review_detected_agency_title_default))
                        putExtra(
                            EXTRA_AGENCY_SUBLINE,
                            getString(
                                R.string.review_agency_subtitle_fmt,
                                getString(R.string.review_detected_agency_short_default)
                            )
                        )
                        putExtra(EXTRA_DESCRIPTION, getString(R.string.ai_result_desc_video))
                        putExtra(EXTRA_LOCATION_SHORT, fallbackLocationExtra())
                    }
                    else -> {
                        putExtra(EXTRA_INCIDENT_TITLE, getString(R.string.review_detected_incident_default))
                        putExtra(EXTRA_INCIDENT_SUBTITLE, getString(R.string.review_incident_subtitle_default))
                        putExtra(EXTRA_AGENCY_TITLE, getString(R.string.review_detected_agency_title_default))
                        putExtra(
                            EXTRA_AGENCY_SUBLINE,
                            getString(
                                R.string.review_agency_subtitle_fmt,
                                getString(R.string.review_detected_agency_short_default)
                            )
                        )
                        putExtra(EXTRA_DESCRIPTION, getString(R.string.ai_result_desc_photo))
                        putExtra(EXTRA_LOCATION_SHORT, fallbackLocationExtra())
                    }
                }
            }
        }
    }

}


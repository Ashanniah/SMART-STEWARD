package com.example.smart_steward

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.smart_steward.BuildConfig
import com.example.smart_steward.net.AiAnalyzeCallbacks
import com.example.smart_steward.net.SmartStewardAiClient
import com.example.smart_steward.net.VideoTooLargeException
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class AiAnalysisActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SmartStewardAiClient"
        const val EXTRA_REANALYZE = "reanalyze"
        const val EXTRA_USER_MESSAGE = "extra_user_message"
        const val EXTRA_INCIDENT_TITLE = "extra_incident_title"
        const val EXTRA_INCIDENT_SUBTITLE = "extra_incident_subtitle"
        const val EXTRA_AGENCY_TITLE = "extra_agency_title"
        const val EXTRA_AGENCY_SUBLINE = "extra_agency_subline"
        const val EXTRA_DESCRIPTION = "extra_description"
        const val EXTRA_LOCATION_SHORT = "extra_location_short"
        const val EXTRA_REPORTABLE = "extra_reportable"
        const val EXTRA_SEVERITY = "extra_severity"
        const val EXTRA_CONFIDENCE_SCORE = "extra_confidence_score"
        const val EXTRA_AI_CATEGORY = "extra_ai_category"
        const val EXTRA_AI_SUMMARY = "extra_ai_summary"
        const val EXTRA_AI_SYNTHESIS = "extra_ai_synthesis"
        const val EXTRA_AI_FILE = "extra_ai_file"
        const val EXTRA_AI_FRAME_ANALYSIS_JSON = "extra_ai_frame_analysis_json"
        const val EXTRA_AI_SEVERITY_REASON = "extra_ai_severity_reason"
    }

    private val handler = Handler(Looper.getMainLooper())
    private val tickMs = 50L
    private val minDisplayMs = 1_800L
    private val localPhaseMs = 2_800L
    private var elapsed = 0L

    private enum class UploadPhase {
        IDLE,
        PREPARING,
        UPLOADING,
        WAITING_SERVER,
        FAILED
    }

    @Volatile
    private var uploadPhase = UploadPhase.IDLE

    @Volatile
    private var uploadProgressPercent = 0

    @Volatile
    private var preparedVideoSizeBytes = 0L

    @Volatile
    private var preparedVideoOriginalSizeBytes = 0L

    @Volatile
    private var videoWasCompressed = false

    @Volatile
    private var videoPrepareProgress = 0

    @Volatile
    private var videoTooLarge = false
    private var reanalyze = false
    private var userMessage: String? = null

    private val executor = Executors.newSingleThreadExecutor()
    @Volatile
    private var apiFinished = false
    @Volatile
    private var apiResultIntent: Intent? = null
    @Volatile
    private var apiError: String? = null
    private var finalized = false

    private var aiRequestStarted = false
    @Volatile
    private var resolvedLocationShort: String? = null
    @Volatile
    private var locationReady = false
    @Volatile
    private var mediaReady = false

    private lateinit var timeRemaining: TextView
    private lateinit var overallProgress: ProgressBar
    private lateinit var headline: TextView
    private lateinit var subheadline: TextView
    private lateinit var reportIncidentTitle: TextView
    private lateinit var reportHeaderBadge: TextView
    private lateinit var reportThumbnail: ImageView
    private lateinit var errorBanner: View
    private lateinit var errorBannerText: TextView

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
            if (finalized || isFinishing) return
            elapsed += tickMs
            applyPipelineUi()
            tryFinalize()
            if (!finalized) {
                handler.postDelayed(this, tickMs)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ai_analysis)

        setupToolbarInsets()
        WindowInsetsControllerCompat(window, window.decorView).isAppearanceLightStatusBars = false
        window.statusBarColor = getColor(R.color.activity_title_bar)

        reanalyze = intent.getBooleanExtra(EXTRA_REANALYZE, false)
        userMessage = intent.getStringExtra(EXTRA_USER_MESSAGE)

        timeRemaining = findViewById(R.id.aiTimeRemaining)
        overallProgress = findViewById(R.id.aiOverallProgress)
        headline = findViewById(R.id.aiHeadline)
        subheadline = findViewById(R.id.aiSubheadline)
        reportIncidentTitle = findViewById(R.id.aiReportIncidentTitle)
        reportHeaderBadge = findViewById(R.id.aiReportHeaderBadge)
        reportThumbnail = findViewById(R.id.aiReportThumbnail)
        errorBanner = findViewById(R.id.aiErrorBanner)
        errorBannerText = findViewById(R.id.aiErrorBannerText)

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

        bindReportThumbnail()
        bindStaticCopy()

        LocationLabelHelper.resolveShortLabel(this) { label ->
            resolvedLocationShort = label
            locationReady = true
            bindStaticCopy()
            applyPipelineUi()
            if (!aiRequestStarted) {
                aiRequestStarted = true
                startAiRequest()
            }
        }

        findViewById<ImageView>(R.id.aiBackButton).setOnClickListener {
            setResult(Activity.RESULT_CANCELED)
            finish()
        }

        applyPipelineUi()
        handler.post(tickRunnable)
    }

    private fun setupToolbarInsets() {
        val toolbar = findViewById<View>(R.id.aiToolbar)
        val padV = resources.getDimensionPixelSize(R.dimen.incident_header_padding_vertical)
        ViewCompat.setOnApplyWindowInsetsListener(toolbar) { view, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, statusTop + padV, view.paddingRight, padV)
            insets
        }
        ViewCompat.requestApplyInsets(toolbar)
    }

    private fun isVideoOnly(): Boolean =
        CapturedMediaStore.capturedVideoUri != null && CapturedMediaStore.capturedBitmap == null

    private fun bindReportThumbnail() {
        CapturedMediaStore.capturedBitmap?.let { bitmap ->
            reportThumbnail.setImageBitmap(bitmap)
            mediaReady = true
            return
        }
        CapturedMediaStore.capturedVideoUri?.let { uri ->
            loadVideoFrame(uri)?.let { frame ->
                reportThumbnail.setImageBitmap(frame)
            }
            mediaReady = false
            return
        }
        mediaReady = true
    }

    private fun loadVideoFrame(uri: Uri): Bitmap? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(this, uri)
            retriever.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun startAiRequest() {
        Log.i(TAG, "startAiRequest() — API=${BuildConfig.API_BASE_URL}")
        executor.execute {
            try {
                var preparedVideoFile: java.io.File? = null
                if (isVideoOnly()) {
                    val uri = CapturedMediaStore.capturedVideoUri
                        ?: throw IllegalStateException("No video to analyze.")
                    runOnUiThread {
                        uploadPhase = UploadPhase.PREPARING
                        videoPrepareProgress = 0
                        applyPipelineUi()
                    }
                    val prepared =
                        SmartStewardAiClient.prepareVideoUploadFile(applicationContext, uri) { pct ->
                            runOnUiThread {
                                videoPrepareProgress = pct
                                applyPipelineUi()
                            }
                        }
                    preparedVideoFile = prepared.file
                    preparedVideoOriginalSizeBytes = prepared.originalSizeBytes
                    preparedVideoSizeBytes = prepared.finalSizeBytes
                    videoWasCompressed = prepared.wasCompressed
                    runOnUiThread {
                        mediaReady = true
                        uploadPhase = UploadPhase.UPLOADING
                        uploadProgressPercent = 0
                        applyPipelineUi()
                    }
                } else if (CapturedMediaStore.capturedBitmap != null) {
                    runOnUiThread {
                        uploadPhase = UploadPhase.PREPARING
                        applyPipelineUi()
                    }
                    preparedVideoOriginalSizeBytes =
                        estimatePhotoUploadBytes(CapturedMediaStore.capturedBitmap!!)
                    preparedVideoSizeBytes = preparedVideoOriginalSizeBytes
                    videoWasCompressed = false
                    mediaReady = true
                    runOnUiThread {
                        uploadPhase = UploadPhase.UPLOADING
                        uploadProgressPercent = 0
                        applyPipelineUi()
                    }
                }

                val locForApi = resolvedLocationShort?.trim()?.takeIf { it.isNotEmpty() }
                val callbacks = object : AiAnalyzeCallbacks {
                    override fun onUploadStarted(totalBytes: Long) {
                        runOnUiThread {
                            uploadPhase = UploadPhase.UPLOADING
                            uploadProgressPercent = 0
                            if (totalBytes > 0L && !isVideoOnly()) {
                                preparedVideoSizeBytes = totalBytes
                            }
                            applyPipelineUi()
                        }
                    }

                    override fun onUploadProgress(
                        percent: Int,
                        bytesWritten: Long,
                        totalBytes: Long
                    ) {
                        runOnUiThread {
                            uploadProgressPercent = percent
                            uploadPhase = UploadPhase.UPLOADING
                            applyPipelineUi()
                        }
                    }

                    override fun onUploadComplete() {
                        runOnUiThread {
                            uploadProgressPercent = 100
                            applyPipelineUi()
                        }
                    }

                    override fun onWaitingForServer() {
                        runOnUiThread {
                            uploadPhase = UploadPhase.WAITING_SERVER
                            applyPipelineUi()
                        }
                    }
                }

                val result = SmartStewardAiClient.analyzeToResultIntent(
                    applicationContext,
                    BuildConfig.API_BASE_URL,
                    userMessage,
                    reanalyze,
                    locForApi,
                    callbacks,
                    preparedVideoFile = preparedVideoFile,
                )
                result.apply {
                    locForApi?.let { putExtra(EXTRA_LOCATION_SHORT, it) }
                }
                apiResultIntent = result
                apiError = null
            } catch (e: VideoTooLargeException) {
                Log.e(TAG, "Video too large: ${e.sizeBytes} bytes", e)
                videoTooLarge = true
                apiResultIntent = null
                apiError = getString(
                    R.string.ai_video_too_large_fmt,
                    SmartStewardAiClient.formatMegabytes(e.sizeBytes)
                )
                runOnUiThread {
                    uploadPhase = UploadPhase.FAILED
                }
            } catch (e: Exception) {
                Log.e(TAG, "AI analysis failed: ${e.message}", e)
                apiResultIntent = null
                apiError = e.message ?: e.javaClass.simpleName
            } finally {
                apiFinished = true
                runOnUiThread {
                    onApiFinished()
                }
            }
        }
    }

    private fun onApiFinished() {
        if (videoTooLarge) {
            errorBanner.visibility = View.VISIBLE
            errorBannerText.text = apiError
        } else if (apiError != null) {
            errorBanner.visibility = View.VISIBLE
            errorBannerText.text = getString(R.string.ai_error_banner_fmt, apiError)
        } else {
            errorBanner.visibility = View.GONE
        }

        bindStaticCopy()
        applyPipelineUi()
        tryFinalize()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun tryFinalize() {
        if (isFinishing || finalized) return
        if (!apiFinished) return
        if (videoTooLarge) return
        if (elapsed < minDisplayMs) return

        finalized = true
        handler.removeCallbacks(tickRunnable)

        val intent = apiResultIntent ?: buildAnalysisResultIntent().also {
            if (apiError != null) {
                Toast.makeText(
                    this,
                    getString(R.string.ai_api_fallback_toast, apiError),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
        resolvedLocationShort?.trim()?.takeIf { it.isNotEmpty() }?.let { loc ->
            intent.putExtra(EXTRA_LOCATION_SHORT, loc)
        }
        setResult(Activity.RESULT_OK, intent)
        finish()
    }

    private fun reportCardTitle(): String =
        when {
            reanalyze -> getString(R.string.ai_report_title_reanalyze)
            CapturedMediaStore.capturedVideoUri != null && CapturedMediaStore.capturedBitmap == null ->
                getString(R.string.ai_report_type_video)
            else -> getString(R.string.ai_report_type_photo)
        }

    private fun bindReportMetaRow(
        includeId: Int,
        iconRes: Int,
        label: String,
        value: String,
    ) {
        val root = findViewById<View>(includeId)
        root.findViewById<ImageView>(R.id.aiReportMetaIcon).setImageResource(iconRes)
        root.findViewById<TextView>(R.id.aiReportMetaLabel).text = label
        root.findViewById<TextView>(R.id.aiReportMetaValue).text = value
    }

    private fun bindStaticCopy() {
        step1Title.setText(
            if (isVideoOnly()) R.string.ai_step_process_video_title
            else R.string.ai_step_process_photo_title
        )
        step2Title.setText(R.string.ai_step_upload_title)
        step3Title.setText(R.string.ai_step_wait_server_title)
        step4Title.setText(R.string.ai_step_route_title)

        reportIncidentTitle.text = reportCardTitle()

        val now = Date()
        val dateStr = SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(now)
        val timeStr = SimpleDateFormat("h:mm a", Locale.getDefault()).format(now)
        bindReportMetaRow(
            R.id.aiReportLocationRow,
            R.drawable.loc,
            getString(R.string.receipt_label_location) + ":",
            locationDisplayForUi(),
        )
        bindReportMetaRow(
            R.id.aiReportDateRow,
            R.drawable.calendar,
            getString(R.string.receipt_label_date_submitted) + ":",
            dateStr,
        )
        bindReportMetaRow(
            R.id.aiReportTimeRow,
            R.drawable.clock,
            getString(R.string.dashboard_detail_time_label),
            timeStr,
        )
    }

    private fun locationDisplayForUi(): String =
        resolvedLocationShort?.trim()?.takeIf { it.isNotEmpty() }
            ?: getString(R.string.ai_location_placeholder)

    private fun estimatePhotoUploadBytes(bitmap: Bitmap): Long {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 88, out)
        return out.size().toLong().coerceAtLeast(1L)
    }

    private fun processStep1DoneSubtitle(): String {
        if (isVideoOnly()) {
            if (preparedVideoSizeBytes <= 0L) {
                return getString(R.string.ai_step_process_video_checking)
            }
            if (videoWasCompressed && preparedVideoOriginalSizeBytes > 0L) {
                return getString(
                    R.string.ai_step_process_video_ready_optimized_fmt,
                    SmartStewardAiClient.formatMegabytes(preparedVideoOriginalSizeBytes),
                    SmartStewardAiClient.formatMegabytes(preparedVideoSizeBytes),
                )
            }
            return getString(
                R.string.ai_step_process_video_ready_fmt,
                SmartStewardAiClient.formatMegabytes(preparedVideoSizeBytes),
            )
        }
        if (preparedVideoSizeBytes > 0L) {
            return getString(
                R.string.ai_step_process_photo_ready_fmt,
                SmartStewardAiClient.formatMegabytes(preparedVideoSizeBytes),
            )
        }
        return getString(R.string.ai_step_media_done_photo)
    }

    private fun processStep1RunningSubtitle(): String {
        if (isVideoOnly()) {
            if (videoPrepareProgress > 0) {
                return getString(
                    R.string.ai_step_process_video_optimizing_pct,
                    videoPrepareProgress.coerceIn(0, 100),
                )
            }
            return getString(R.string.ai_step_process_video_checking)
        }
        return getString(R.string.ai_step_process_photo_checking)
    }

    private fun waitServerRunningSubtitle(): String =
        if (isVideoOnly()) getString(R.string.ai_step_wait_server_running)
        else getString(R.string.ai_step_wait_server_running_photo)

    private fun processMediaCheckingStatus(): String =
        if (isVideoOnly()) getString(R.string.ai_step_process_video_checking)
        else getString(R.string.ai_step_process_photo_checking)

    private fun applyPipelineUi() {
        updateReportHeaderBadge()
        if (isVideoOnly() || CapturedMediaStore.capturedBitmap != null) {
            applyUploadPipelineUi()
        }
    }

    private fun updateReportHeaderBadge() {
        when {
            apiFinished && apiError == null -> {
                reportHeaderBadge.text = getString(R.string.ai_report_status_analyzed)
                reportHeaderBadge.setBackgroundResource(R.drawable.bg_ai_badge_done_filled)
                reportHeaderBadge.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
            else -> {
                reportHeaderBadge.text = getString(R.string.ai_report_status_analyzing)
                reportHeaderBadge.setBackgroundResource(R.drawable.bg_ai_badge_header_queued)
                reportHeaderBadge.setTextColor(ContextCompat.getColor(this, R.color.white))
            }
        }
    }

    private fun routeDoneSubtitle(): String {
        val agency = apiResultIntent?.getStringExtra(EXTRA_AGENCY_TITLE)?.trim().orEmpty()
        return if (agency.isNotEmpty()) {
            getString(R.string.ai_step_route_sub_done_fmt, agency)
        } else {
            getString(R.string.ai_step_route_sub_running)
        }
    }

    private fun applyUploadPipelineUi() {
        val apiDone = apiFinished
        val phase = uploadPhase

        overallProgress.isIndeterminate = false
        overallProgress.progress = when {
            apiDone -> 100
            phase == UploadPhase.FAILED -> 0
            phase == UploadPhase.PREPARING -> 8
            phase == UploadPhase.UPLOADING ->
                (12 + uploadProgressPercent * 0.58f).toInt().coerceIn(12, 70)
            phase == UploadPhase.WAITING_SERVER -> {
                val waitMs = elapsed.coerceAtLeast(0L)
                val waitT = (waitMs.toFloat() / 30_000f).coerceIn(0f, 1f)
                (70 + waitT * 25).toInt().coerceIn(70, 95)
            }
            else -> 5
        }

        timeRemaining.text = when {
            apiDone -> getString(R.string.ai_processing_complete)
            phase == UploadPhase.FAILED -> getString(R.string.ai_status_failed)
            phase == UploadPhase.PREPARING -> processMediaCheckingStatus()
            phase == UploadPhase.UPLOADING && uploadProgressPercent > 0 ->
                getString(R.string.ai_upload_progress_fmt, uploadProgressPercent)
            phase == UploadPhase.UPLOADING -> getString(R.string.ai_step_upload_title)
            phase == UploadPhase.WAITING_SERVER -> getString(R.string.ai_waiting_server)
            else -> getString(R.string.ai_processing_status)
        }

        if (phase == UploadPhase.FAILED) {
            stepFailed(
                step1Card, step1Icon, step1Subtitle, step1Badge, step1Bar,
                apiError ?: getString(R.string.ai_status_failed)
            )
            stepQueued(
                step2Card, step2Icon, step2Subtitle, step2Badge, step2Bar, 0.5f,
                getString(R.string.ai_step_queued_generic)
            )
            stepQueued(
                step3Card, step3Icon, step3Subtitle, step3Badge, step3Bar, 0.5f,
                getString(R.string.ai_step_queued_generic)
            )
            stepQueued(
                step4Card, step4Icon, step4Subtitle, step4Badge, step4Bar, 0.5f,
                getString(R.string.ai_step_queued_generic)
            )
            return
        }

        when (phase) {
            UploadPhase.PREPARING -> {
                stepRunning(
                    step1Card, step1Icon, step1Subtitle, step1Badge, step1Bar,
                    processStep1RunningSubtitle(),
                    activeCard = true
                )
                stepQueued(
                    step2Card, step2Icon, step2Subtitle, step2Badge, step2Bar, 0.65f,
                    getString(R.string.ai_step_queued_generic)
                )
                stepQueued(
                    step3Card, step3Icon, step3Subtitle, step3Badge, step3Bar, 0.65f,
                    getString(R.string.ai_step_queued_generic)
                )
                stepQueued(
                    step4Card, step4Icon, step4Subtitle, step4Badge, step4Bar, 0.65f,
                    getString(R.string.ai_step_queued_generic)
                )
            }
            UploadPhase.UPLOADING -> {
                stepDone(
                    step1Card, step1Icon, step1Subtitle, step1Badge, step1Bar,
                    processStep1DoneSubtitle(),
                )
                stepRunningDeterminate(
                    step2Card, step2Icon, step2Subtitle, step2Badge, step2Bar,
                    getString(R.string.ai_step_upload_running_fmt, uploadProgressPercent),
                    uploadProgressPercent.coerceIn(0, 100),
                    activeCard = true
                )
                stepQueued(
                    step3Card, step3Icon, step3Subtitle, step3Badge, step3Bar, 0.65f,
                    getString(R.string.ai_step_queued_generic)
                )
                stepQueued(
                    step4Card, step4Icon, step4Subtitle, step4Badge, step4Bar, 0.65f,
                    getString(R.string.ai_step_queued_generic)
                )
            }
            UploadPhase.WAITING_SERVER -> {
                stepDone(
                    step1Card, step1Icon, step1Subtitle, step1Badge, step1Bar,
                    processStep1DoneSubtitle(),
                )
                stepDone(
                    step2Card, step2Icon, step2Subtitle, step2Badge, step2Bar,
                    getString(R.string.ai_step_upload_done)
                )
                if (!apiDone) {
                    stepRunning(
                        step3Card, step3Icon, step3Subtitle, step3Badge, step3Bar,
                        waitServerRunningSubtitle(),
                        activeCard = true
                    )
                    stepQueued(
                        step4Card, step4Icon, step4Subtitle, step4Badge, step4Bar,
                        0.85f,
                        getString(R.string.ai_step_route_sub_queued)
                    )
                } else {
                    stepDone(
                        step3Card, step3Icon, step3Subtitle, step3Badge, step3Bar,
                        getString(R.string.ai_step_wait_server_done)
                    )
                    stepDone(
                        step4Card, step4Icon, step4Subtitle, step4Badge, step4Bar,
                        routeDoneSubtitle()
                    )
                }
            }
            else -> {
                stepRunning(
                    step1Card, step1Icon, step1Subtitle, step1Badge, step1Bar,
                    processStep1RunningSubtitle(),
                    activeCard = true
                )
                stepQueued(
                    step2Card, step2Icon, step2Subtitle, step2Badge, step2Bar, 0.65f,
                    getString(R.string.ai_step_queued_generic)
                )
                stepQueued(
                    step3Card, step3Icon, step3Subtitle, step3Badge, step3Bar, 0.65f,
                    getString(R.string.ai_step_queued_generic)
                )
                stepQueued(
                    step4Card, step4Icon, step4Subtitle, step4Badge, step4Bar, 0.65f,
                    getString(R.string.ai_step_queued_generic)
                )
            }
        }

        if (locationReady && !apiDone && phase != UploadPhase.PREPARING) {
            bindStaticCopy()
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
        badge.setBackgroundResource(R.drawable.bg_ai_badge_done_filled)
        badge.setTextColor(ContextCompat.getColor(this, R.color.white))
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
        card.setBackgroundResource(R.drawable.bg_ai_card)
        icon.setImageResource(R.drawable.ic_ai_check_circle)
        subtitle.text = subtitleText
        bar.visibility = View.VISIBLE
        bar.isIndeterminate = true
        bar.max = 100
        badge.text = getString(R.string.ai_status_running)
        badge.setBackgroundResource(R.drawable.bg_ai_badge_running_blue)
        badge.setTextColor(ContextCompat.getColor(this, R.color.white))
    }

    private fun stepRunningDeterminate(
        card: View,
        icon: ImageView,
        subtitle: TextView,
        badge: TextView,
        bar: ProgressBar,
        subtitleText: String,
        progressPercent: Int,
        activeCard: Boolean
    ) {
        card.alpha = 1f
        card.setBackgroundResource(R.drawable.bg_ai_card)
        icon.setImageResource(R.drawable.ic_ai_check_circle)
        subtitle.text = subtitleText
        bar.visibility = View.VISIBLE
        bar.isIndeterminate = false
        bar.max = 100
        bar.progress = progressPercent
        badge.text = getString(R.string.ai_status_running)
        badge.setBackgroundResource(R.drawable.bg_ai_badge_running_blue)
        badge.setTextColor(ContextCompat.getColor(this, R.color.white))
    }

    private fun stepFailed(
        card: View,
        icon: ImageView,
        subtitle: TextView,
        badge: TextView,
        bar: ProgressBar,
        subtitleText: String
    ) {
        card.alpha = 1f
        card.setBackgroundResource(R.drawable.bg_ai_card)
        icon.setImageResource(R.drawable.ic_review_warning)
        subtitle.text = subtitleText
        bar.visibility = View.GONE
        badge.text = getString(R.string.ai_status_failed)
        badge.setBackgroundResource(R.drawable.bg_ai_badge_running)
        badge.setTextColor(ContextCompat.getColor(this, R.color.white))
        errorBanner.visibility = View.VISIBLE
        errorBannerText.text = subtitleText
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
        icon.setImageResource(R.drawable.ic_ai_check_circle)
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

package com.example.smart_steward

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.gms.location.LocationServices
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.firebase.auth.FirebaseAuth
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IncidentFlowActivity : AppCompatActivity() {

    companion object {
        private const val KEY_SCREEN_STATE = "incident_flow_screen_state"
        private const val KEY_INITIAL_ANALYSIS_DONE = "incident_flow_initial_analysis_done"
    }

    private enum class ScreenState {
        PREVIEW,
        ANALYZING,
        DETECTED,
        NOT_DETECTED,
        EDIT,
        REANALYZING,
        SUBMITTED
    }

    private lateinit var titleText: TextView
    private lateinit var incidentHeader: View
    private lateinit var incidentBackButton: ImageView
    private lateinit var previewContainer: View
    private lateinit var analyzingContainer: View
    private lateinit var detectedContainer: View
    private lateinit var noIncidentContainer: View
    private lateinit var editContainer: View
    private lateinit var submittedContainer: View
    private lateinit var imagePreviewLarge: ImageView
    private lateinit var imagePreviewEdit: ImageView
    private lateinit var descriptionInput: EditText
    private lateinit var analysisStepOne: TextView
    private lateinit var analysisStepTwo: TextView
    private lateinit var analysisStepThree: TextView

    private var currentState = ScreenState.PREVIEW

    private var afterAiAnalysis: (() -> Unit)? = null

    /** When true, backing out of [AiAnalysisActivity] closes this screen (initial capture flow). */
    private var finishOnAiCancel = false

    /** Latest classification payload from [AiAnalysisActivity] (activity result). */
    private var lastAnalysisResult: Intent? = null

    /** True after the first AI pass finished; prevents re-analysis on rotation. */
    private var initialAnalysisDone = false

    private var submittedDialogVisible = false

    @Volatile
    private var submitInProgress = false

    private lateinit var submitDetectedButton: Button
    private lateinit var confirmSubmitButton: Button
    private lateinit var editButton: Button
    private lateinit var editAgainButton: Button

    private val mainHandler = Handler(Looper.getMainLooper())

    private val aiAnalysisLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) {
            afterAiAnalysis = null
            if (finishOnAiCancel) finish()
            finishOnAiCancel = false
            return@registerForActivityResult
        }
        finishOnAiCancel = false
        lastAnalysisResult = result.data
        val action = afterAiAnalysis
        afterAiAnalysis = null
        action?.invoke()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incident_flow)

        titleText = findViewById(R.id.incidentFlowTitle)
        incidentHeader = findViewById(R.id.incidentHeader)
        incidentBackButton = findViewById(R.id.incidentBackButton)
        setupIncidentHeaderInsets()
        previewContainer = findViewById(R.id.previewContainer)
        analyzingContainer = findViewById(R.id.analyzingContainer)
        detectedContainer = findViewById(R.id.detectedContainer)
        noIncidentContainer = findViewById(R.id.noIncidentContainer)
        editContainer = findViewById(R.id.editContainer)
        submittedContainer = findViewById(R.id.submittedContainer)
        imagePreviewLarge = findViewById(R.id.imagePreviewLarge)
        imagePreviewEdit = findViewById(R.id.imagePreviewEdit)
        descriptionInput = findViewById(R.id.incidentDescriptionInput)
        analysisStepOne = findViewById(R.id.analysisStepOne)
        analysisStepTwo = findViewById(R.id.analysisStepTwo)
        analysisStepThree = findViewById(R.id.analysisStepThree)

        findViewById<ImageView>(R.id.incidentBackButton).setOnClickListener {
            finish()
        }

        MainBottomNav.setup(this, selected = null)

        // Temporary camera content URIs may expire; copy to cache before AI / submit (stable path).
        CapturedMediaStore.capturedVideoUri?.let { u ->
            if (u.scheme == "content") {
                MediaCapturePersistence.copyVideoToCache(this, u)?.let { persisted ->
                    CapturedMediaStore.capturedVideoUri = persisted
                }
            }
        }

        findViewById<Button>(R.id.editButton).also { editButton = it }.setOnClickListener {
            populateEditFromDetected()
            showState(ScreenState.EDIT)
        }

        findViewById<Button>(R.id.submitDetectedButton).also { submitDetectedButton = it }.setOnClickListener {
            submitReportAndShowSubmitted(
                incidentType = findViewById<TextView>(R.id.detectedIncidentTypeText).text.toString(),
                assignedAgency = findViewById<TextView>(R.id.detectedAgencyText).text.toString(),
                description = findViewById<TextView>(R.id.detectedDescriptionText).text.toString(),
                locationLine = formatLocationForSubmit(
                    findViewById<TextView>(R.id.detectedLocationText).text.toString()
                )
            )
        }

        findViewById<Button>(R.id.editAgainButton).also { editAgainButton = it }.setOnClickListener {
            syncEditToDetected()
            showState(ScreenState.DETECTED)
        }

        findViewById<Button>(R.id.confirmSubmitButton).also { confirmSubmitButton = it }.setOnClickListener {
            syncEditToDetected()
            val description = descriptionInput.text.toString()
            findViewById<TextView>(R.id.detectedDescriptionText).text = description
            submitReportAndShowSubmitted(
                incidentType = findViewById<TextView>(R.id.detectedIncidentTypeText).text.toString(),
                assignedAgency = findViewById<TextView>(R.id.detectedAgencyText).text.toString(),
                description = description,
                locationLine = formatLocationForSubmit(
                    findViewById<TextView>(R.id.editLocationText).text.toString()
                )
            )
        }

        findViewById<Button>(R.id.backToDashboardButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.noIncidentCaptureAgainButton).setOnClickListener {
            navigateHome(openCamera = true)
        }

        findViewById<TextView>(R.id.noIncidentExampleLink).setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.no_incident_example)
                .setMessage(R.string.no_incident_example_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        findViewById<Button>(R.id.trackReportButton).setOnClickListener {
            Toast.makeText(this, "Track Report coming soon.", Toast.LENGTH_SHORT).show()
        }

        CapturedMediaStore.capturedBitmap?.let { bitmap ->
            imagePreviewLarge.setImageBitmap(bitmap)
            imagePreviewEdit.setImageBitmap(bitmap)
        } ?: run {
            CapturedMediaStore.capturedVideoUri?.let { uri ->
                loadVideoFrame(uri)?.let { frame ->
                    imagePreviewLarge.setImageBitmap(frame)
                    imagePreviewEdit.setImageBitmap(frame)
                }
            }
        }

        if (savedInstanceState != null) {
            initialAnalysisDone = savedInstanceState.getBoolean(KEY_INITIAL_ANALYSIS_DONE, false)
            if (initialAnalysisDone) {
                restoreScreenState(savedInstanceState)
            } else {
                startInitialAiAnalysis()
            }
        } else {
            startInitialAiAnalysis()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_SCREEN_STATE, currentState.name)
        outState.putBoolean(KEY_INITIAL_ANALYSIS_DONE, initialAnalysisDone)
    }

    /** After rotation or process restore, stay on the current step instead of re-running AI. */
    private fun restoreScreenState(savedInstanceState: Bundle) {
        val name = savedInstanceState.getString(KEY_SCREEN_STATE) ?: return
        val restored = runCatching { ScreenState.valueOf(name) }.getOrNull() ?: return
        showState(restored)
    }

    private fun startInitialAiAnalysis() {
        val hasMedia =
            CapturedMediaStore.capturedBitmap != null || CapturedMediaStore.capturedVideoUri != null
        if (!hasMedia) {
            Toast.makeText(this, "No media captured.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }
        previewContainer.visibility = View.GONE
        titleText.text = getString(R.string.incident_title_analyzing)
        finishOnAiCancel = true
        afterAiAnalysis = { showAnalysisResultScreen() }
        aiAnalysisLauncher.launch(Intent(this, AiAnalysisActivity::class.java))
    }

    private fun showAnalysisResultScreen() {
        initialAnalysisDone = true
        if (isLastAnalysisReportable()) {
            showState(ScreenState.DETECTED)
        } else {
            showState(ScreenState.NOT_DETECTED)
        }
    }

    private fun isLastAnalysisReportable(): Boolean {
        return lastAnalysisResult?.getBooleanExtra(AiAnalysisActivity.EXTRA_REPORTABLE, true) != false
    }

    private fun analysisSeverity(): String? {
        val raw = lastAnalysisResult?.getStringExtra(AiAnalysisActivity.EXTRA_SEVERITY)?.trim().orEmpty()
        return raw.takeIf { it.isNotBlank() && !it.equals("—", ignoreCase = true) }
    }

    private fun analysisAiConfidencePercent(): Int? {
        val result = lastAnalysisResult ?: return null
        val score = result.getFloatExtra(AiAnalysisActivity.EXTRA_CONFIDENCE_SCORE, Float.NaN)
        if (score.isNaN()) return null
        val pct = if (score in 0f..1f) {
            (score * 100f).toInt()
        } else {
            score.toInt()
        }
        return pct.coerceIn(0, 100)
    }

    private fun navigateHome(openCamera: Boolean) {
        val intent = Intent(this, DashboardActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        if (openCamera) {
            intent.putExtra(DashboardActivity.EXTRA_OPEN_CAMERA, true)
        }
        startActivity(intent)
        finish()
    }

    override fun onResume() {
        super.onResume()
        MainBottomNav.updateBadge(this)
    }

    override fun onDestroy() {
        mainHandler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }

    private fun loadVideoFrame(uri: Uri): Bitmap? {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(this, uri)
            r.frameAtTime
        } catch (_: Exception) {
            null
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun formatLocationForSubmit(display: String): String {
        val t = display.trim()
        if (t.startsWith("Location", ignoreCase = true)) return t
        return "Location: $t"
    }

    private fun applyAnalysisExtrasToReviewScreen(data: Intent?) {
        if (data == null) {
            findViewById<TextView>(R.id.detectedIncidentTypeText).setText(R.string.review_detected_incident_default)
            findViewById<TextView>(R.id.detectedIncidentSubtitle).setText(R.string.review_incident_subtitle_default)
            findViewById<TextView>(R.id.detectedAgencyText).setText(R.string.review_detected_agency_short_default)
            findViewById<TextView>(R.id.detectedAgencySubtitle)?.visibility = View.GONE
            findViewById<TextView>(R.id.detectedDescriptionText).setText(R.string.review_ai_description_default)
            findViewById<TextView>(R.id.detectedLocationText).setText(R.string.review_location_short_default)
            return
        }
        findViewById<TextView>(R.id.detectedIncidentTypeText).text =
            data.getStringExtra(AiAnalysisActivity.EXTRA_INCIDENT_TITLE)
                ?: getString(R.string.review_detected_incident_default)
        val incidentSubtitle = data.getStringExtra(AiAnalysisActivity.EXTRA_INCIDENT_SUBTITLE)
            ?: getString(R.string.review_incident_subtitle_default)
        val typeOnly = incidentSubtitle.split(" · ").firstOrNull() ?: incidentSubtitle
        findViewById<TextView>(R.id.detectedIncidentSubtitle).text = typeOnly
        findViewById<TextView>(R.id.detectedAgencyText).text =
            formatAssignedAgencyLabel(
                data.getStringExtra(AiAnalysisActivity.EXTRA_AGENCY_TITLE)
                    ?: getString(R.string.review_detected_agency_short_default),
            )
        findViewById<TextView>(R.id.detectedAgencySubtitle)?.visibility = View.GONE
        findViewById<TextView>(R.id.detectedDescriptionText).text =
            data.getStringExtra(AiAnalysisActivity.EXTRA_DESCRIPTION)
                ?: getString(R.string.review_ai_description_default)
        findViewById<TextView>(R.id.detectedLocationText).text =
            data.getStringExtra(AiAnalysisActivity.EXTRA_LOCATION_SHORT)
                ?: getString(R.string.review_location_short_default)
    }

    private fun populateDetectedReviewUi() {
        applyAnalysisExtrasToReviewScreen(lastAnalysisResult)
        bindMediaPreview(
            findViewById(R.id.detectedSinglePreview),
            findViewById(R.id.detectedVideoPlayOverlay)
        )
        // Show the latest GPS reverse-geocode here (not only the value bundled from AI analysis).
        refreshDetectedLocationFromGps()
    }

    private fun populateNoIncidentUi() {
        bindMediaPreview(
            findViewById(R.id.noIncidentPreview),
            findViewById(R.id.noIncidentVideoPlayOverlay)
        )
        updateNoIncidentVideoDurationBadge()
        val summary = lastAnalysisResult
            ?.getStringExtra(AiAnalysisActivity.EXTRA_DESCRIPTION)
            .orEmpty()
            .trim()
        findViewById<TextView>(R.id.noIncidentExplanationText).text =
            noIncidentExplanationText(summary)
        populateNoIncidentReportableGrid()
    }

    private data class NoIncidentReportableItem(
        val iconRes: Int,
        val iconPillBgRes: Int,
        val titleRes: Int,
        val descRes: Int
    )

    private fun populateNoIncidentReportableGrid() {
        val grid = findViewById<LinearLayout>(R.id.noIncidentReportableGrid)
        grid.removeAllViews()
        val items = listOf(
            NoIncidentReportableItem(
                R.drawable.fire_flame,
                R.drawable.bg_reportable_icon_pill_orange,
                R.string.no_incident_reportable_burning_title,
                R.string.no_incident_reportable_burning_desc
            ),
            NoIncidentReportableItem(
                R.drawable.delete,
                R.drawable.bg_reportable_icon_pill_red,
                R.string.no_incident_reportable_dumping_title,
                R.string.no_incident_reportable_dumping_desc
            ),
            NoIncidentReportableItem(
                R.drawable.tree,
                R.drawable.bg_reportable_icon_pill_green,
                R.string.no_incident_reportable_logging_title,
                R.string.no_incident_reportable_logging_desc
            ),
            NoIncidentReportableItem(
                R.drawable.knife,
                R.drawable.bg_reportable_icon_pill_grey,
                R.string.no_incident_reportable_murder_title,
                R.string.no_incident_reportable_murder_desc
            ),
            NoIncidentReportableItem(
                R.drawable.anti_theft_system,
                R.drawable.bg_reportable_icon_pill_purple,
                R.string.no_incident_reportable_theft_title,
                R.string.no_incident_reportable_theft_desc
            ),
            NoIncidentReportableItem(
                R.drawable.ace_of_spades,
                R.drawable.bg_reportable_icon_pill_brown,
                R.string.no_incident_reportable_gambling_title,
                R.string.no_incident_reportable_gambling_desc
            )
        )
        val gapPx = (6 * resources.displayMetrics.density).toInt()
        items.chunked(2).forEachIndexed { rowIndex, rowItems ->
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    if (rowIndex > 0) topMargin = gapPx
                }
            }
            rowItems.forEachIndexed { colIndex, item ->
                val card = layoutInflater.inflate(R.layout.item_no_incident_reportable, row, false)
                card.findViewById<View>(R.id.reportableIconPill)
                    .setBackgroundResource(item.iconPillBgRes)
                card.findViewById<ImageView>(R.id.reportableIcon).setImageResource(item.iconRes)
                card.findViewById<TextView>(R.id.reportableTitle).setText(item.titleRes)
                card.findViewById<TextView>(R.id.reportableDesc).setText(item.descRes)
                val lp = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                if (colIndex == 0 && rowItems.size > 1) lp.marginEnd = gapPx
                if (colIndex == 1) lp.marginStart = gapPx
                row.addView(card, lp)
            }
            grid.addView(row)
        }
    }

    private fun updateNoIncidentVideoDurationBadge() {
        val badge = findViewById<TextView>(R.id.noIncidentVideoDuration)
        val videoUri = CapturedMediaStore.capturedVideoUri
        if (videoUri == null) {
            badge.visibility = View.GONE
            return
        }
        val ms = videoDurationMs(videoUri)
        if (ms <= 0L) {
            badge.visibility = View.GONE
            return
        }
        badge.text = formatVideoDuration(ms)
        badge.visibility = View.VISIBLE
    }

    private fun videoDurationMs(uri: Uri): Long {
        val r = MediaMetadataRetriever()
        return try {
            r.setDataSource(this, uri)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (_: Exception) {
            0L
        } finally {
            try {
                r.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun formatVideoDuration(ms: Long): String {
        val totalSec = (ms / 1000).toInt().coerceAtLeast(0)
        val min = totalSec / 60
        val sec = totalSec % 60
        return if (min > 0) "%d:%02d".format(min, sec) else "0:%02d".format(sec)
    }

    private fun noIncidentExplanationText(aiSummary: String): String {
        val generic = setOf(
            "no reportable issue detected",
            "not a valid incident"
        )
        if (aiSummary.isBlank() || generic.any { aiSummary.equals(it, ignoreCase = true) }) {
            return getString(R.string.no_incident_body_default)
        }
        return aiSummary
    }

    private fun bindMediaPreview(preview: ImageView, playOverlay: ImageView) {
        val placeholder = ContextCompat.getColor(this, R.color.register_field_fill)
        val bitmap = CapturedMediaStore.capturedBitmap
        val videoUri = CapturedMediaStore.capturedVideoUri

        preview.setOnClickListener(null)
        when {
            bitmap != null -> {
                preview.setImageBitmap(bitmap)
                preview.background = null
                playOverlay.visibility = View.GONE
                preview.setOnClickListener {
                    MediaPlayback.openBitmapZoom(this, bitmap)
                }
            }
            videoUri != null -> {
                val frame = loadVideoFrame(videoUri)
                if (frame != null) {
                    preview.setImageBitmap(frame)
                    preview.background = null
                } else {
                    preview.setImageDrawable(null)
                    preview.setBackgroundColor(placeholder)
                }
                playOverlay.visibility = View.VISIBLE
                val play = View.OnClickListener { MediaPlayback.openLocalVideo(this, videoUri) }
                playOverlay.setOnClickListener(play)
                preview.setOnClickListener(play)
            }
            else -> {
                preview.setImageDrawable(null)
                preview.setBackgroundColor(placeholder)
                playOverlay.visibility = View.GONE
            }
        }
    }

    /** Updates the review row from the current fused location so the label matches "where I am now". */
    private fun refreshDetectedLocationFromGps() {
        val locView = findViewById<TextView>(R.id.detectedLocationText)
        LocationLabelHelper.resolveShortLabel(this) { label ->
            locView.text = label
        }
    }

    private fun refreshDetectedTimestamp() {
        val now = Date()
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val combined = "${dateFmt.format(now)} · ${timeFmt.format(now)}"
        findViewById<TextView>(R.id.detectedDateText)?.text = dateFmt.format(now)
        findViewById<TextView>(R.id.detectedTimeText)?.text = timeFmt.format(now)
        findViewById<TextView>(R.id.detectedTimestampText)?.text = combined
    }

    private fun setupIncidentHeaderInsets() {
        val padV = resources.getDimensionPixelSize(R.dimen.incident_header_padding_vertical)
        ViewCompat.setOnApplyWindowInsetsListener(incidentHeader) { view, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            view.setPadding(view.paddingLeft, statusTop + padV, view.paddingRight, padV)
            insets
        }
        ViewCompat.requestApplyInsets(incidentHeader)
    }

    private fun stripLocationPrefix(text: String): String =
        text.trim().removePrefix("Location:").trim()

    private fun applyIncidentHeaderStyle(brandHeader: Boolean) {
        val statusBarController = WindowInsetsControllerCompat(window, window.decorView)
        if (brandHeader) {
            incidentHeader.setBackgroundColor(getColor(R.color.activity_title_bar))
            titleText.setTextColor(getColor(R.color.white))
            incidentBackButton.setColorFilter(getColor(R.color.white))
            window.statusBarColor = getColor(R.color.activity_title_bar)
            statusBarController.isAppearanceLightStatusBars = false
        } else {
            incidentHeader.setBackgroundColor(getColor(R.color.notif_header_bg))
            titleText.setTextColor(getColor(R.color.black))
            incidentBackButton.setColorFilter(getColor(R.color.register_button_green))
            window.statusBarColor = getColor(R.color.notif_header_bg)
            statusBarController.isAppearanceLightStatusBars = true
        }
    }

    private fun formatAssignedAgencyLabel(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return getString(R.string.review_detected_agency_short_default)
        return AgencyCanonical.shortName(trimmed).ifBlank { trimmed }
    }

    private fun populateEditFromDetected() {
        findViewById<TextView>(R.id.editIncidentTypeText).text =
            findViewById<TextView>(R.id.detectedIncidentTypeText).text
        findViewById<TextView>(R.id.editIncidentSubtitle).text =
            findViewById<TextView>(R.id.detectedIncidentSubtitle).text
        findViewById<TextView>(R.id.editAgencyText).text =
            findViewById<TextView>(R.id.detectedAgencyText).text
        findViewById<TextView>(R.id.editAgencySubtitle)?.visibility = View.GONE
        descriptionInput.setText(findViewById<TextView>(R.id.detectedDescriptionText).text)
        findViewById<TextView>(R.id.editLocationText).text =
            stripLocationPrefix(findViewById<TextView>(R.id.detectedLocationText).text.toString())
        applyEditTimestampDisplay(
            findViewById<TextView>(R.id.detectedTimestampText).text.toString()
        )
        bindMediaPreview(
            imagePreviewEdit,
            findViewById(R.id.editVideoPlayOverlay)
        )
    }

    private fun applyEditTimestampDisplay(combined: String) {
        findViewById<TextView>(R.id.editTimestampText)?.text = combined
        val parts = combined.split(" · ", limit = 2)
        if (parts.size == 2) {
            findViewById<TextView>(R.id.editDateText)?.text = parts[0].trim()
            findViewById<TextView>(R.id.editTimeText)?.text = parts[1].trim()
        }
    }

    private fun syncEditToDetected() {
        findViewById<TextView>(R.id.detectedIncidentTypeText).text =
            findViewById<TextView>(R.id.editIncidentTypeText).text
        findViewById<TextView>(R.id.detectedIncidentSubtitle).text =
            findViewById<TextView>(R.id.editIncidentSubtitle).text
        findViewById<TextView>(R.id.detectedAgencyText).text =
            findViewById<TextView>(R.id.editAgencyText).text
        findViewById<TextView>(R.id.detectedAgencySubtitle)?.visibility = View.GONE
        findViewById<TextView>(R.id.detectedDescriptionText).text =
            descriptionInput.text.toString()
        findViewById<TextView>(R.id.detectedLocationText).text =
            stripLocationPrefix(findViewById<TextView>(R.id.editLocationText).text.toString())
        applyDetectedTimestampDisplay(
            findViewById<TextView>(R.id.editTimestampText).text.toString()
        )
    }

    private fun applyDetectedTimestampDisplay(combined: String) {
        findViewById<TextView>(R.id.detectedTimestampText)?.text = combined
        val parts = combined.split(" · ", limit = 2)
        if (parts.size == 2) {
            findViewById<TextView>(R.id.detectedDateText)?.text = parts[0].trim()
            findViewById<TextView>(R.id.detectedTimeText)?.text = parts[1].trim()
        }
    }

    private fun currentUserId(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun setSubmitInProgress(active: Boolean) {
        submitInProgress = active
        if (!::submitDetectedButton.isInitialized) return
        submitDetectedButton.isEnabled = !active
        confirmSubmitButton.isEnabled = !active
        editButton.isEnabled = !active
        editAgainButton.isEnabled = !active
        submitDetectedButton.text = if (active) {
            getString(R.string.submit_in_progress)
        } else {
            getString(R.string.submit)
        }
        confirmSubmitButton.text = if (active) {
            getString(R.string.submit_confirm_in_progress)
        } else {
            getString(R.string.incident_confirm_submit)
        }
    }

    private fun submitReportAndShowSubmitted(
        incidentType: String,
        assignedAgency: String,
        description: String,
        locationLine: String
    ) {
        if (submitInProgress) return
        if (!isLastAnalysisReportable()) {
            showState(ScreenState.NOT_DETECTED)
            Toast.makeText(
                this,
                getString(R.string.submit_blocked_not_reportable),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val agency = AgencyCanonical.shortName(assignedAgency).ifBlank { assignedAgency.trim() }
        if (agency.equals("N/A", ignoreCase = true) || agency.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.submit_blocked_no_agency),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        setSubmitInProgress(true)

        val bitmapCapture = CapturedMediaStore.capturedBitmap
        val videoUri = CapturedMediaStore.capturedVideoUri
        val thumbnail = bitmapCapture ?: videoUri?.let { loadVideoFrame(it) }
        fun showSubmittedFromDraft(draftId: String) {
            Toast.makeText(this, getString(R.string.submitted_saved_draft), Toast.LENGTH_LONG).show()
            showSubmittedSuccessDialog(
                draftId,
                incidentType,
                agency,
                description,
                locationLine
            )
        }

        fun saveOfflineDraft(lat: Double?, lng: Double?) {
            val uid = currentUserId().orEmpty()
            if (uid.isBlank()) {
                setSubmitInProgress(false)
                Toast.makeText(this, "Sign in to save draft reports.", Toast.LENGTH_LONG).show()
                return
            }
            val draft = OfflineReportDraftStore.addDraft(
                context = this,
                userId = uid,
                incidentType = incidentType,
                assignedAgency = agency,
                description = description,
                locationLine = locationLine,
                photoBitmap = thumbnail,
                videoUri = videoUri,
                latitude = lat,
                longitude = lng,
                severity = analysisSeverity(),
                aiConfidence = analysisAiConfidencePercent(),
            )
            showSubmittedFromDraft(draft.id)
        }

        fun submitWithCoords(lat: Double?, lng: Double?) {
            if (!OfflineDraftSyncManager.isOnline(this)) {
                saveOfflineDraft(lat, lng)
                return
            }
            ReportFirestore.submitReport(
                userId = currentUserId(),
                incidentType = incidentType,
                assignedAgency = agency,
                description = description,
                locationLine = locationLine,
                photo = thumbnail,
                videoUri = videoUri,
                latitude = lat,
                longitude = lng,
                severity = analysisSeverity(),
                aiConfidence = analysisAiConfidencePercent(),
                onSuccess = { docId, _ ->
                Toast.makeText(this, getString(R.string.submitted_success_toast), Toast.LENGTH_LONG).show()
                showSubmittedSuccessDialog(
                    docId,
                    incidentType,
                    agency,
                    description,
                    locationLine
                )
            },
            onError = { msg ->
                if (!OfflineDraftSyncManager.isOnline(this)) {
                    saveOfflineDraft(lat, lng)
                } else {
                    setSubmitInProgress(false)
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            },
            onWarning = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
            )
        }

        var coordsResolved = false
        var locationTimeout: Runnable? = null

        fun proceed(lat: Double?, lng: Double?) {
            if (coordsResolved) return
            coordsResolved = true
            locationTimeout?.let { mainHandler.removeCallbacks(it) }
            submitWithCoords(lat, lng)
        }

        val locOk = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (locOk) {
            LocationServices.getFusedLocationProviderClient(this).lastLocation
                .addOnCompleteListener { task ->
                    val loc = if (task.isSuccessful) task.result else null
                    proceed(loc?.latitude, loc?.longitude)
                }
            locationTimeout = Runnable { proceed(null, null) }
            mainHandler.postDelayed(locationTimeout!!, 2_500L)
        } else {
            proceed(null, null)
        }
    }

    private fun showSubmittedSuccessDialog(
        docId: String,
        incidentType: String,
        assignedAgency: String,
        description: String,
        locationLine: String
    ) {
        if (submittedDialogVisible) return
        initialAnalysisDone = true

        val agencyShort = AgencyCanonical.shortName(assignedAgency)
        val ref = ReportRef.format(docId, Date())
        val now = Date()
        val dateFmt = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
        val timeFmt = SimpleDateFormat("h:mm a", Locale.getDefault())
        val locationDisplay = when {
            locationLine.startsWith("Location:", ignoreCase = true) ->
                locationLine.substringAfter(":").trim()
            else -> locationLine.trim()
        }

        val payload = ReportSubmittedDialog.Payload(
            reportId = ref,
            incidentType = incidentType,
            assignedAgency = assignedAgency,
            agencyShort = agencyShort,
            description = description,
            locationDisplay = locationDisplay,
            dateText = dateFmt.format(now),
            timeText = timeFmt.format(now),
            photoBitmap = CapturedMediaStore.capturedBitmap,
            videoUri = CapturedMediaStore.capturedVideoUri
        )

        submittedDialogVisible = true
        if (currentState != ScreenState.DETECTED && currentState != ScreenState.EDIT) {
            showState(ScreenState.DETECTED)
        }
        applyIncidentHeaderStyle(true)

        ReportSubmittedDialog.show(
            activity = this,
            payload = payload,
            onTrack = {
                submittedDialogVisible = false
                startActivity(Intent(this, MyActivityActivity::class.java))
                finish()
            },
            onDismiss = {
                submittedDialogVisible = false
                navigateHome(openCamera = false)
            }
        )
    }

    private fun showState(state: ScreenState) {
        currentState = state
        if (state == ScreenState.SUBMITTED) {
            initialAnalysisDone = true
            submittedContainer.visibility = View.GONE
        }
        previewContainer.visibility = if (state == ScreenState.PREVIEW) View.VISIBLE else View.GONE
        analyzingContainer.visibility =
            if (state == ScreenState.ANALYZING || state == ScreenState.REANALYZING) View.VISIBLE else View.GONE
        detectedContainer.visibility = if (state == ScreenState.DETECTED) View.VISIBLE else View.GONE
        noIncidentContainer.visibility =
            if (state == ScreenState.NOT_DETECTED) View.VISIBLE else View.GONE
        editContainer.visibility = if (state == ScreenState.EDIT) View.VISIBLE else View.GONE
        submittedContainer.visibility = View.GONE

        titleText.text = when (state) {
            ScreenState.PREVIEW -> getString(R.string.send_ai_title)
            ScreenState.ANALYZING -> getString(R.string.incident_title_analyzing)
            ScreenState.DETECTED -> getString(R.string.review_ai_detection_title)
            ScreenState.NOT_DETECTED -> getString(R.string.review_ai_detection_title)
            ScreenState.EDIT -> getString(R.string.incident_title_edit)
            ScreenState.REANALYZING -> getString(R.string.incident_title_reanalyzing)
            ScreenState.SUBMITTED -> getString(R.string.incident_title_submitted)
        }

        if (state == ScreenState.REANALYZING) {
            findViewById<TextView>(R.id.analyzingHeadline).text =
                "AI is updating the incident\nclassification using your\ncorrected description"
            analysisStepOne.text = "✓ Analyzing User Correction"
            analysisStepTwo.text = "✓ Updating Incident Type"
            analysisStepThree.text = "✓ Assigning Responsible Agency"
        } else {
            findViewById<TextView>(R.id.analyzingHeadline).text = "Please wait while AI analyzes\nthe report"
            analysisStepOne.text = "✓ Media Analysis"
            analysisStepTwo.text = "✓ Location Detection"
            analysisStepThree.text = "✓ Activity Classification"
        }

        applyIncidentHeaderStyle(
            state == ScreenState.DETECTED ||
                state == ScreenState.EDIT ||
                state == ScreenState.NOT_DETECTED
        )

        if (state == ScreenState.DETECTED) {
            refreshDetectedTimestamp()
            populateDetectedReviewUi()
        }
        if (state == ScreenState.NOT_DETECTED) {
            populateNoIncidentUi()
        }
    }
}

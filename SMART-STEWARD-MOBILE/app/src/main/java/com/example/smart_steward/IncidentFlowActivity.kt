package com.example.smart_steward

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
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
import com.google.android.gms.location.LocationServices
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

        findViewById<Button>(R.id.editButton).setOnClickListener {
            populateEditFromDetected()
            showState(ScreenState.EDIT)
        }

        findViewById<Button>(R.id.submitDetectedButton).setOnClickListener {
            submitReportAndShowSubmitted(
                incidentType = findViewById<TextView>(R.id.detectedIncidentTypeText).text.toString(),
                assignedAgency = findViewById<TextView>(R.id.detectedAgencyText).text.toString(),
                description = findViewById<TextView>(R.id.detectedDescriptionText).text.toString(),
                locationLine = formatLocationForSubmit(
                    findViewById<TextView>(R.id.detectedLocationText).text.toString()
                )
            )
        }

        findViewById<Button>(R.id.editAgainButton).setOnClickListener {
            syncEditToDetected()
            showState(ScreenState.DETECTED)
        }

        findViewById<Button>(R.id.confirmSubmitButton).setOnClickListener {
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
            findViewById<TextView>(R.id.detectedAgencyText).setText(R.string.review_detected_agency_title_default)
            findViewById<TextView>(R.id.detectedAgencySubtitle)?.text = getString(
                R.string.review_agency_subtitle_fmt,
                getString(R.string.review_detected_agency_short_default)
            )
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
        val agencyTitle = data.getStringExtra(AiAnalysisActivity.EXTRA_AGENCY_TITLE)
            ?: getString(R.string.review_detected_agency_title_default)
        findViewById<TextView>(R.id.detectedAgencyText).text = agencyTitle
        val agencySub = data.getStringExtra(AiAnalysisActivity.EXTRA_AGENCY_SUBLINE)?.trim().orEmpty()
        findViewById<TextView>(R.id.detectedAgencySubtitle)?.apply {
            when {
                agencySub.isEmpty() -> visibility = View.GONE
                agencySub.equals(agencyTitle.trim(), ignoreCase = true) -> visibility = View.GONE
                else -> {
                    visibility = View.VISIBLE
                    text = agencySub
                }
            }
        }
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
        val fmt = SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        findViewById<TextView>(R.id.detectedTimestampText).text = fmt.format(Date())
    }

    private fun copyAgencySubtitleState(dest: TextView?, src: TextView?) {
        if (dest == null) return
        if (src == null) {
            dest.visibility = View.GONE
            return
        }
        dest.visibility = src.visibility
        dest.text = src.text
    }

    private fun populateEditFromDetected() {
        findViewById<TextView>(R.id.editIncidentTypeText).text =
            findViewById<TextView>(R.id.detectedIncidentTypeText).text
        findViewById<TextView>(R.id.editIncidentSubtitle).text =
            findViewById<TextView>(R.id.detectedIncidentSubtitle).text
        findViewById<TextView>(R.id.editAgencyText).text =
            findViewById<TextView>(R.id.detectedAgencyText).text
        copyAgencySubtitleState(
            findViewById(R.id.editAgencySubtitle),
            findViewById(R.id.detectedAgencySubtitle)
        )
        descriptionInput.setText(findViewById<TextView>(R.id.detectedDescriptionText).text)
        findViewById<TextView>(R.id.editLocationText).text =
            formatLocationForSubmit(findViewById<TextView>(R.id.detectedLocationText).text.toString())
        findViewById<TextView>(R.id.editTimestampText).text =
            findViewById<TextView>(R.id.detectedTimestampText).text
    }

    private fun syncEditToDetected() {
        findViewById<TextView>(R.id.detectedIncidentTypeText).text =
            findViewById<TextView>(R.id.editIncidentTypeText).text
        findViewById<TextView>(R.id.detectedIncidentSubtitle).text =
            findViewById<TextView>(R.id.editIncidentSubtitle).text
        findViewById<TextView>(R.id.detectedAgencyText).text =
            findViewById<TextView>(R.id.editAgencyText).text
        copyAgencySubtitleState(
            findViewById(R.id.detectedAgencySubtitle),
            findViewById(R.id.editAgencySubtitle)
        )
        findViewById<TextView>(R.id.detectedDescriptionText).text =
            descriptionInput.text.toString()
        findViewById<TextView>(R.id.detectedLocationText).text =
            findViewById<TextView>(R.id.editLocationText).text
        findViewById<TextView>(R.id.detectedTimestampText).text =
            findViewById<TextView>(R.id.editTimestampText).text
    }

    private fun currentUserId(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun submitReportAndShowSubmitted(
        incidentType: String,
        assignedAgency: String,
        description: String,
        locationLine: String
    ) {
        if (!isLastAnalysisReportable()) {
            showState(ScreenState.NOT_DETECTED)
            Toast.makeText(
                this,
                getString(R.string.submit_blocked_not_reportable),
                Toast.LENGTH_LONG
            ).show()
            return
        }
        val agency = assignedAgency.trim()
        if (agency.equals("N/A", ignoreCase = true) || agency.isEmpty()) {
            Toast.makeText(
                this,
                getString(R.string.submit_blocked_no_agency),
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val bitmapCapture = CapturedMediaStore.capturedBitmap
        val videoUri = CapturedMediaStore.capturedVideoUri
        val thumbnail = bitmapCapture ?: videoUri?.let { loadVideoFrame(it) }
        fun showSubmittedFromDraft(draftId: String) {
            Toast.makeText(this, getString(R.string.submitted_saved_draft), Toast.LENGTH_LONG).show()
            populateSubmittedSummary(
                draftId,
                incidentType,
                assignedAgency,
                description,
                locationLine
            )
            showState(ScreenState.SUBMITTED)
        }

        fun saveOfflineDraft(lat: Double?, lng: Double?) {
            val uid = currentUserId().orEmpty()
            if (uid.isBlank()) {
                Toast.makeText(this, "Sign in to save draft reports.", Toast.LENGTH_LONG).show()
                return
            }
            val draft = OfflineReportDraftStore.addDraft(
                context = this,
                userId = uid,
                incidentType = incidentType,
                assignedAgency = assignedAgency,
                description = description,
                locationLine = locationLine,
                photoBitmap = thumbnail,
                videoUri = videoUri,
                latitude = lat,
                longitude = lng
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
                assignedAgency = assignedAgency,
                description = description,
                locationLine = locationLine,
                photo = thumbnail,
                videoUri = videoUri,
                latitude = lat,
                longitude = lng,
                onSuccess = { docId, _ ->
                Toast.makeText(this, getString(R.string.submitted_success_toast), Toast.LENGTH_LONG).show()
                populateSubmittedSummary(
                    docId,
                    incidentType,
                    assignedAgency,
                    description,
                    locationLine
                )
                showState(ScreenState.SUBMITTED)
            },
            onError = { msg ->
                if (!OfflineDraftSyncManager.isOnline(this)) {
                    saveOfflineDraft(lat, lng)
                } else {
                    Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                }
            },
            onWarning = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
            )
        }

        val locOk = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (locOk) {
            LocationServices.getFusedLocationProviderClient(this).lastLocation
                .addOnCompleteListener { task ->
                    val loc = if (task.isSuccessful) task.result else null
                    submitWithCoords(loc?.latitude, loc?.longitude)
                }
        } else {
            submitWithCoords(null, null)
        }
    }

    private fun populateSubmittedSummary(
        docId: String,
        incidentType: String,
        assignedAgency: String,
        description: String,
        locationLine: String
    ) {
        val agencyShort = agencyShortName(assignedAgency)
        findViewById<TextView>(R.id.submittedSuccessSubtitle).text =
            getString(R.string.submitted_success_subtitle, agencyShort)

        val ref = ReportRef.format(docId, Date())
        findViewById<TextView>(R.id.submittedReceiptNumberText).text = ref
        findViewById<TextView>(R.id.submittedReceiptReportIdValue).text = ref

        findViewById<TextView>(R.id.submittedReceiptTypeValue).text = incidentType

        val dateFmt = SimpleDateFormat("MMM d, yyyy - h:mm a", Locale.getDefault())
        findViewById<TextView>(R.id.submittedReceiptDateValue).text = dateFmt.format(Date())

        val locationDisplay = when {
            locationLine.startsWith("Location:", ignoreCase = true) ->
                locationLine.substringAfter(":").trim()
            else -> locationLine.trim()
        }
        findViewById<TextView>(R.id.submittedReceiptLocationValue).text =
            locationDisplay.ifBlank { "—" }

        val desc = description.trim()
        findViewById<TextView>(R.id.submittedReceiptDescriptionValue).text =
            desc.ifBlank { "—" }

        val thumb = findViewById<ImageView>(R.id.submittedReceiptMediaThumb)
        val kindLabel = findViewById<TextView>(R.id.submittedReceiptMediaKind)
        val bitmap = CapturedMediaStore.capturedBitmap
        val videoUri = CapturedMediaStore.capturedVideoUri
        val placeholder = ContextCompat.getColor(this, R.color.register_field_fill)
        when {
            bitmap != null -> {
                thumb.setImageBitmap(bitmap)
                thumb.visibility = View.VISIBLE
                thumb.background = null
                kindLabel.text = getString(R.string.receipt_attached_one_photo)
            }
            videoUri != null -> {
                val frame = loadVideoFrame(videoUri)
                if (frame != null) {
                    thumb.setImageBitmap(frame)
                    thumb.background = null
                } else {
                    thumb.setImageDrawable(null)
                    thumb.setBackgroundColor(placeholder)
                }
                thumb.visibility = View.VISIBLE
                kindLabel.text = getString(R.string.receipt_attached_one_video)
            }
            else -> {
                thumb.setImageDrawable(null)
                thumb.setBackgroundColor(placeholder)
                thumb.visibility = View.VISIBLE
                kindLabel.text = getString(R.string.receipt_attached_none)
            }
        }

        findViewById<TextView>(R.id.submittedReceiptAgencyValue).text = assignedAgency
    }

    private fun agencyShortName(assignedAgency: String): String {
        val inParens = Regex("\\(([^)]+)\\)").find(assignedAgency)?.groupValues?.getOrNull(1)?.trim()
        if (!inParens.isNullOrBlank()) return inParens
        val first = assignedAgency.split(",").firstOrNull()?.trim().orEmpty()
        return first.ifBlank { assignedAgency }
    }

    private fun showState(state: ScreenState) {
        currentState = state
        if (state == ScreenState.SUBMITTED) {
            initialAnalysisDone = true
        }
        previewContainer.visibility = if (state == ScreenState.PREVIEW) View.VISIBLE else View.GONE
        analyzingContainer.visibility =
            if (state == ScreenState.ANALYZING || state == ScreenState.REANALYZING) View.VISIBLE else View.GONE
        detectedContainer.visibility = if (state == ScreenState.DETECTED) View.VISIBLE else View.GONE
        noIncidentContainer.visibility =
            if (state == ScreenState.NOT_DETECTED) View.VISIBLE else View.GONE
        editContainer.visibility = if (state == ScreenState.EDIT) View.VISIBLE else View.GONE
        submittedContainer.visibility = if (state == ScreenState.SUBMITTED) View.VISIBLE else View.GONE

        titleText.text = when (state) {
            ScreenState.PREVIEW -> getString(R.string.send_ai_title)
            ScreenState.ANALYZING -> getString(R.string.incident_title_analyzing)
            ScreenState.DETECTED -> getString(R.string.review_ai_detection_title)
            ScreenState.NOT_DETECTED -> getString(R.string.no_incident_title)
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

        if (state == ScreenState.DETECTED) {
            refreshDetectedTimestamp()
            populateDetectedReviewUi()
        }
        if (state == ScreenState.NOT_DETECTED) {
            populateNoIncidentUi()
        }
    }
}

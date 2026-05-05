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

    private enum class ScreenState {
        PREVIEW,
        ANALYZING,
        DETECTED,
        EDIT,
        REANALYZING,
        SUBMITTED
    }

    private lateinit var titleText: TextView
    private lateinit var previewContainer: View
    private lateinit var analyzingContainer: View
    private lateinit var detectedContainer: View
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

    /** Latest classification payload from [AiAnalysisActivity] (activity result). */
    private var lastAnalysisResult: Intent? = null

    private val aiAnalysisLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != RESULT_OK) return@registerForActivityResult
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

        findViewById<LinearLayout>(R.id.incidentNavHome).setOnClickListener {
            startActivity(
                Intent(this, DashboardActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }

        findViewById<LinearLayout>(R.id.incidentNavActivity).setOnClickListener {
            startActivity(Intent(this, MyActivityActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.incidentNavNotification).setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.incidentNavProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.incidentCameraFab).setOnClickListener {
            startActivity(
                Intent(this, DashboardActivity::class.java)
                    .putExtra(DashboardActivity.EXTRA_OPEN_CAMERA, true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            )
            finish()
        }

        // Temporary camera content URIs may expire; copy to cache before AI / submit (stable path).
        CapturedMediaStore.capturedVideoUri?.let { u ->
            if (u.scheme == "content") {
                MediaCapturePersistence.copyVideoToCache(this, u)?.let { persisted ->
                    CapturedMediaStore.capturedVideoUri = persisted
                }
            }
        }

        findViewById<Button>(R.id.sendToAiButton).setOnClickListener {
            afterAiAnalysis = { showState(ScreenState.DETECTED) }
            aiAnalysisLauncher.launch(Intent(this, AiAnalysisActivity::class.java))
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
            val description = descriptionInput.text.toString()
            afterAiAnalysis = {
                lastAnalysisResult?.let { applyAnalysisExtrasToReviewScreen(it) }
                findViewById<TextView>(R.id.detectedDescriptionText).text = description
                submitReportAndShowSubmitted(
                    findViewById<TextView>(R.id.detectedIncidentTypeText).text.toString(),
                    findViewById<TextView>(R.id.detectedAgencyText).text.toString(),
                    description,
                    formatLocationForSubmit(
                        findViewById<TextView>(R.id.detectedLocationText).text.toString()
                    )
                )
            }
            aiAnalysisLauncher.launch(
                Intent(this, AiAnalysisActivity::class.java)
                    .putExtra(AiAnalysisActivity.EXTRA_REANALYZE, true)
                    .putExtra(AiAnalysisActivity.EXTRA_USER_MESSAGE, description)
            )
        }

        findViewById<Button>(R.id.backToDashboardButton).setOnClickListener {
            finish()
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

        showState(ScreenState.PREVIEW)
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
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

    private fun updateNotificationBadge() {
        val badge = findViewById<TextView>(R.id.incidentNavBadge)
        val uid = FirebaseAuth.getInstance().currentUser?.uid
        if (uid.isNullOrBlank()) {
            badge.visibility = View.GONE
            return
        }
        CitizenNotificationsRepository.countUnread(uid, onResult = { unread ->
            runOnUiThread {
                badge.visibility = if (unread > 0) View.VISIBLE else View.GONE
                if (unread > 0) {
                    badge.text = if (unread > 99) "99+" else unread.toString()
                }
            }
        })
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

        val preview = findViewById<ImageView>(R.id.detectedSinglePreview)
        val playOverlay = findViewById<ImageView>(R.id.detectedVideoPlayOverlay)
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

        // Show the latest GPS reverse-geocode here (not only the value bundled from AI analysis).
        refreshDetectedLocationFromGps()
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

        val ref = buildString {
            append("#REP-")
            append(SimpleDateFormat("yyyyMMdd", Locale.US).format(Date()))
            append("-")
            val compact = docId.filter { it.isLetterOrDigit() }
            val suffix = when {
                compact.length >= 3 -> compact.takeLast(3).uppercase(Locale.US)
                docId.length >= 3 -> docId.takeLast(3).uppercase(Locale.US)
                else -> docId.uppercase(Locale.US).padEnd(3, 'X')
            }
            append(suffix)
        }
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
        previewContainer.visibility = if (state == ScreenState.PREVIEW) View.VISIBLE else View.GONE
        analyzingContainer.visibility =
            if (state == ScreenState.ANALYZING || state == ScreenState.REANALYZING) View.VISIBLE else View.GONE
        detectedContainer.visibility = if (state == ScreenState.DETECTED) View.VISIBLE else View.GONE
        editContainer.visibility = if (state == ScreenState.EDIT) View.VISIBLE else View.GONE
        submittedContainer.visibility = if (state == ScreenState.SUBMITTED) View.VISIBLE else View.GONE

        titleText.text = when (state) {
            ScreenState.PREVIEW -> getString(R.string.send_ai_title)
            ScreenState.ANALYZING -> getString(R.string.incident_title_analyzing)
            ScreenState.DETECTED -> getString(R.string.review_ai_detection_title)
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
    }
}

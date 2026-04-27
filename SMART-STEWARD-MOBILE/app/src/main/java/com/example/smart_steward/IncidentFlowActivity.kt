package com.example.smart_steward

import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

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
    private lateinit var incidentHeader: View
    private lateinit var contentFrame: View
    private lateinit var incidentBottomNav: View
    private lateinit var incidentCameraFab: View
    private lateinit var previewContainer: View
    private lateinit var analyzingContainer: View
    private lateinit var detectedContainer: View
    private lateinit var editContainer: View
    private lateinit var submittedContainer: View
    private lateinit var imagePreviewLarge: ImageView
    private lateinit var imagePreviewSmall: ImageView
    private lateinit var imagePreviewEdit: ImageView
    private lateinit var descriptionInput: EditText
    private lateinit var detectedIncidentTypeText: TextView
    private lateinit var detectedAssignedAgencyText: TextView
    private lateinit var detectedDescriptionText: TextView
    private lateinit var editIncidentTypeText: TextView
    private lateinit var editAssignedAgencyText: TextView
    private lateinit var submittedReportTypeText: TextView
    private lateinit var submittedDescriptionText: TextView
    private lateinit var submittedAssignedAgencyText: TextView
    private lateinit var analysisStepOne: TextView
    private lateinit var analysisStepTwo: TextView
    private lateinit var analysisStepThree: TextView

    private var currentState = ScreenState.PREVIEW
    private var currentIncidentType = "Incident"
    private var currentAssignedAgency = "Barangay"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_incident_flow)

        titleText = findViewById(R.id.incidentFlowTitle)
        incidentHeader = findViewById(R.id.incidentHeader)
        contentFrame = findViewById(R.id.contentFrame)
        incidentBottomNav = findViewById(R.id.incidentBottomNav)
        incidentCameraFab = findViewById(R.id.incidentCameraFab)
        previewContainer = findViewById(R.id.previewContainer)
        analyzingContainer = findViewById(R.id.analyzingContainer)
        detectedContainer = findViewById(R.id.detectedContainer)
        editContainer = findViewById(R.id.editContainer)
        submittedContainer = findViewById(R.id.submittedContainer)
        imagePreviewLarge = findViewById(R.id.imagePreviewLarge)
        imagePreviewSmall = findViewById(R.id.imagePreviewSmall)
        imagePreviewEdit = findViewById(R.id.imagePreviewEdit)
        descriptionInput = findViewById(R.id.incidentDescriptionInput)
        detectedIncidentTypeText = findViewById(R.id.detectedIncidentTypeText)
        detectedAssignedAgencyText = findViewById(R.id.detectedAssignedAgencyText)
        detectedDescriptionText = findViewById(R.id.detectedDescriptionText)
        editIncidentTypeText = findViewById(R.id.editIncidentTypeText)
        editAssignedAgencyText = findViewById(R.id.editAssignedAgencyText)
        submittedReportTypeText = findViewById(R.id.submittedReportTypeText)
        submittedDescriptionText = findViewById(R.id.submittedDescriptionText)
        submittedAssignedAgencyText = findViewById(R.id.submittedAssignedAgencyText)
        analysisStepOne = findViewById(R.id.analysisStepOne)
        analysisStepTwo = findViewById(R.id.analysisStepTwo)
        analysisStepThree = findViewById(R.id.analysisStepThree)

        findViewById<ImageView>(R.id.incidentBackButton).setOnClickListener {
            finish()
        }

        findViewById<ImageView>(R.id.previewBackButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.sendToAiButton).setOnClickListener {
            analyzeIncident()
        }

        findViewById<Button>(R.id.editButton).setOnClickListener {
            showState(ScreenState.EDIT)
        }

        findViewById<Button>(R.id.submitDetectedButton).setOnClickListener {
            NotificationRepository.createReportSubmittedNotification(
                reportType = currentIncidentType,
                agency = currentAssignedAgency
            )
            showState(ScreenState.SUBMITTED)
        }

        findViewById<Button>(R.id.editAgainButton).setOnClickListener {
            showState(ScreenState.DETECTED)
        }

        findViewById<Button>(R.id.confirmSubmitButton).setOnClickListener {
            analyzeIncident(
                userCorrection = descriptionInput.text.toString().trim(),
                submitAfterAnalysis = true
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
            imagePreviewSmall.setImageBitmap(bitmap)
            imagePreviewEdit.setImageBitmap(bitmap)
        }
        CapturedMediaStore.capturedVideoUri?.let { videoUri ->
            val thumbnail = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                contentResolver.loadThumbnail(videoUri, Size(512, 512), null)
            } else {
                null
            }
            if (thumbnail != null) {
                imagePreviewLarge.setImageBitmap(thumbnail)
                imagePreviewSmall.setImageBitmap(thumbnail)
                imagePreviewEdit.setImageBitmap(thumbnail)
            }
        }

        showState(ScreenState.PREVIEW)
    }

    private fun showState(state: ScreenState) {
        currentState = state
        val isPreview = state == ScreenState.PREVIEW
        incidentHeader.visibility = if (isPreview) View.GONE else View.VISIBLE
        incidentBottomNav.visibility = if (isPreview) View.GONE else View.VISIBLE
        incidentCameraFab.visibility = if (isPreview) View.GONE else View.VISIBLE
        contentFrame.setPadding(
            if (isPreview) 0 else 22.dp,
            if (isPreview) 0 else 18.dp,
            if (isPreview) 0 else 22.dp,
            if (isPreview) 0 else 90.dp
        )
        window.statusBarColor = if (isPreview) Color.BLACK else Color.WHITE
        window.navigationBarColor = if (isPreview) Color.BLACK else Color.WHITE

        previewContainer.visibility = if (state == ScreenState.PREVIEW) View.VISIBLE else View.GONE
        analyzingContainer.visibility =
            if (state == ScreenState.ANALYZING || state == ScreenState.REANALYZING) View.VISIBLE else View.GONE
        detectedContainer.visibility = if (state == ScreenState.DETECTED) View.VISIBLE else View.GONE
        editContainer.visibility = if (state == ScreenState.EDIT) View.VISIBLE else View.GONE
        submittedContainer.visibility = if (state == ScreenState.SUBMITTED) View.VISIBLE else View.GONE

        titleText.text = when (state) {
            ScreenState.PREVIEW -> "Send AI"
            ScreenState.ANALYZING -> "AI Analyzing Incident"
            ScreenState.DETECTED -> "AI Detected Incident"
            ScreenState.EDIT -> "Review and Edit Information"
            ScreenState.REANALYZING -> "AI Re-analyzing Incident"
            ScreenState.SUBMITTED -> "Report Submitted"
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
    }

    private val Int.dp: Int
        get() = (this * resources.displayMetrics.density).toInt()

    private fun analyzeIncident(
        userCorrection: String? = null,
        submitAfterAnalysis: Boolean = false
    ) {
        showState(if (submitAfterAnalysis) ScreenState.REANALYZING else ScreenState.ANALYZING)

        val message = if (submitAfterAnalysis && !userCorrection.isNullOrBlank()) {
            "The user corrected the incident description to: $userCorrection. Re-classify the incident and assigned agency."
        } else if (CapturedMediaStore.capturedVideoUri != null) {
            "Analyze this Smart Steward incident report from the attached video."
        } else {
            "Analyze this Smart Steward incident report from the attached media."
        }

        IncidentAnalysisClient.analyze(
            context = this,
            message = message,
            bitmap = CapturedMediaStore.capturedBitmap,
            videoUri = CapturedMediaStore.capturedVideoUri,
            onSuccess = { result ->
                applyAnalysisResult(result)
                if (submitAfterAnalysis) {
                    NotificationRepository.createReportSubmittedNotification(
                        reportType = result.incidentType,
                        agency = result.assignedAgency
                    )
                }
                showState(if (submitAfterAnalysis) ScreenState.SUBMITTED else ScreenState.DETECTED)
            },
            onError = { errorMessage ->
                Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
                showState(if (submitAfterAnalysis) ScreenState.EDIT else ScreenState.PREVIEW)
            }
        )
    }

    private fun applyAnalysisResult(result: IncidentAnalysisClient.AnalysisResult) {
        currentIncidentType = result.incidentType
        currentAssignedAgency = result.assignedAgency

        detectedIncidentTypeText.text = result.incidentType
        detectedAssignedAgencyText.text = result.assignedAgency
        detectedDescriptionText.text = result.summary

        editIncidentTypeText.text = "${result.incidentType} (Severity: ${result.severity})"
        editAssignedAgencyText.text = result.assignedAgency
        descriptionInput.setText(result.summary)

        submittedReportTypeText.text = "Report Type: ${result.incidentType}"
        submittedDescriptionText.text = "Description: ${result.summary}"
        submittedAssignedAgencyText.text = "Assigned to: ${result.assignedAgency}"
    }
}

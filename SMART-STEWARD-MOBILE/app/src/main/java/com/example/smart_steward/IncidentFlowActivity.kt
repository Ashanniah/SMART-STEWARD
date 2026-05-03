package com.example.smart_steward

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
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
    private lateinit var imagePreviewSmall: ImageView
    private lateinit var imagePreviewEdit: ImageView
    private lateinit var descriptionInput: EditText
    private lateinit var analysisStepOne: TextView
    private lateinit var analysisStepTwo: TextView
    private lateinit var analysisStepThree: TextView

    private var currentState = ScreenState.PREVIEW

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
        imagePreviewSmall = findViewById(R.id.imagePreviewSmall)
        imagePreviewEdit = findViewById(R.id.imagePreviewEdit)
        descriptionInput = findViewById(R.id.incidentDescriptionInput)
        analysisStepOne = findViewById(R.id.analysisStepOne)
        analysisStepTwo = findViewById(R.id.analysisStepTwo)
        analysisStepThree = findViewById(R.id.analysisStepThree)

        findViewById<ImageView>(R.id.incidentBackButton).setOnClickListener {
            finish()
        }

        findViewById<Button>(R.id.sendToAiButton).setOnClickListener {
            showState(ScreenState.ANALYZING)
            findViewById<View>(R.id.incidentRoot).postDelayed({
                showState(ScreenState.DETECTED)
            }, 1800)
        }

        findViewById<Button>(R.id.editButton).setOnClickListener {
            showState(ScreenState.EDIT)
        }

        findViewById<Button>(R.id.submitDetectedButton).setOnClickListener {
            submitReportAndShowSubmitted(
                incidentType = findViewById<TextView>(R.id.detectedIncidentTypeText).text.toString(),
                assignedAgency = findViewById<TextView>(R.id.detectedAgencyText).text.toString(),
                description = findViewById<TextView>(R.id.detectedDescriptionText).text.toString(),
                locationLine = findViewById<TextView>(R.id.detectedLocationText).text.toString()
            )
        }

        findViewById<Button>(R.id.editAgainButton).setOnClickListener {
            showState(ScreenState.DETECTED)
        }

        findViewById<Button>(R.id.confirmSubmitButton).setOnClickListener {
            val incidentType = findViewById<TextView>(R.id.editIncidentTypeText).text.toString()
            val agency = findViewById<TextView>(R.id.editAgencyText).text.toString()
            val description = descriptionInput.text.toString()
            val locationLine = findViewById<TextView>(R.id.editLocationText).text.toString()
            showState(ScreenState.REANALYZING)
            findViewById<View>(R.id.incidentRoot).postDelayed({
                findViewById<TextView>(R.id.detectedDescriptionText).text = description
                submitReportAndShowSubmitted(incidentType, agency, description, locationLine)
            }, 1700)
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

        showState(ScreenState.PREVIEW)
    }

    private fun currentUserId(): String? = FirebaseAuth.getInstance().currentUser?.uid

    private fun submitReportAndShowSubmitted(
        incidentType: String,
        assignedAgency: String,
        description: String,
        locationLine: String
    ) {
        val photo = CapturedMediaStore.capturedBitmap
        val hadLocalPhoto = photo != null
        ReportFirestore.submitReport(
            userId = currentUserId(),
            incidentType = incidentType,
            assignedAgency = assignedAgency,
            description = description,
            locationLine = locationLine,
            photo = photo,
            onSuccess = { docId, photoInCloud ->
                populateSubmittedSummary(
                    docId,
                    incidentType,
                    assignedAgency,
                    description,
                    locationLine,
                    hadLocalPhoto,
                    photoInCloud
                )
                showState(ScreenState.SUBMITTED)
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            },
            onWarning = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
            }
        )
    }

    private fun populateSubmittedSummary(
        docId: String,
        incidentType: String,
        assignedAgency: String,
        description: String,
        locationLine: String,
        hadLocalPhoto: Boolean,
        photoInCloud: Boolean
    ) {
        findViewById<TextView>(R.id.submittedReportIdText).text = "Report ID: $docId"
        findViewById<TextView>(R.id.submittedReportTypeText).text = "Report Type: $incidentType"
        val fmt = SimpleDateFormat("MMMM d, yyyy", Locale.getDefault())
        findViewById<TextView>(R.id.submittedDateText).text =
            "Date Submitted: ${fmt.format(Date())}"
        findViewById<TextView>(R.id.submittedLocationText).text =
            if (locationLine.startsWith("Location:")) locationLine else "Location: $locationLine"
        findViewById<TextView>(R.id.submittedDescriptionText).text = "Description: $description"
        val mediaLine = when {
            photoInCloud -> "Attach Photo/Video: Attached (cloud)"
            hadLocalPhoto -> "Attach Photo/Video: Not uploaded (enable Firebase Storage)"
            else -> "Attach Photo/Video: None"
        }
        findViewById<TextView>(R.id.submittedMediaText).text = mediaLine
        findViewById<TextView>(R.id.submittedAgencyText).text = "Assigned to: $assignedAgency"
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
            ScreenState.PREVIEW -> "Send AI"
            ScreenState.ANALYZING -> "AI Analyzing Incident"
            ScreenState.DETECTED -> "AI Detected Incident"
            ScreenState.EDIT -> "Updated Information"
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
}

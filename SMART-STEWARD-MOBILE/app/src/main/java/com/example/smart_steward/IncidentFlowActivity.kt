package com.example.smart_steward

import android.os.Bundle
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
            showState(ScreenState.SUBMITTED)
        }

        findViewById<Button>(R.id.editAgainButton).setOnClickListener {
            showState(ScreenState.DETECTED)
        }

        findViewById<Button>(R.id.confirmSubmitButton).setOnClickListener {
            showState(ScreenState.REANALYZING)
            findViewById<View>(R.id.incidentRoot).postDelayed({
                findViewById<TextView>(R.id.detectedDescriptionText).text = descriptionInput.text.toString()
                showState(ScreenState.SUBMITTED)
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

package com.example.smart_steward

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class DashboardActivity : AppCompatActivity() {
    private lateinit var mapPreview: TextView

    private val takePicturePreview = registerForActivityResult(
        ActivityResultContracts.TakePicturePreview()
    ) { bitmap ->
        if (bitmap == null) {
            Toast.makeText(this, "Camera canceled.", Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }

        mapPreview.background = BitmapDrawable(resources, bitmap)
        mapPreview.text = ""
        Toast.makeText(this, "Photo captured.", Toast.LENGTH_SHORT).show()
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            takePicturePreview.launch(null)
        } else {
            Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        mapPreview = findViewById(R.id.dashboardMapPreview)

        findViewById<FrameLayout>(R.id.cameraFab).setOnClickListener {
            openCamera()
        }

        findViewById<LinearLayout>(R.id.dashboardNavProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.dashboardNavNotification).setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }
    }

    private fun openCamera() {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCameraPermission) {
            takePicturePreview.launch(null)
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }
}

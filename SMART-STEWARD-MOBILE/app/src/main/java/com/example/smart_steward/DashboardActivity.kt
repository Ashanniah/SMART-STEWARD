package com.example.smart_steward

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.abs
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions

class DashboardActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        const val EXTRA_OPEN_CAMERA = "open_camera"
    }

    private enum class CameraAction {
        PHOTO,
        VIDEO
    }

    private var map: GoogleMap? = null
    private var pendingCameraAction: CameraAction? = null

    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val capturedBitmap = result.data?.extras?.get("data") as? Bitmap
            if (capturedBitmap != null) {
                CapturedMediaStore.capturedVideoUri = null
                CapturedMediaStore.capturedBitmap = capturedBitmap
                startActivity(Intent(this, IncidentFlowActivity::class.java))
            } else {
                Toast.makeText(this, "Unable to read captured photo.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private val captureVideoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data?.data
            if (uri != null) {
                CapturedMediaStore.capturedBitmap = null
                CapturedMediaStore.capturedVideoUri = uri
            }
            startActivity(Intent(this, IncidentFlowActivity::class.java))
        }
    }

    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        val action = pendingCameraAction
        pendingCameraAction = null

        if (granted && action != null) {
            launchCameraAction(action)
        } else {
            Toast.makeText(this, "Camera permission is required to capture evidence.", Toast.LENGTH_LONG)
                .show()
        }
    }

    private val requestLocationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            enableCurrentLocation()
        } else {
            Toast.makeText(
                this,
                "Location permission denied. Map is shown without your current location.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_dashboard)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.dashboardRoot)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.dashboardMap) as? SupportMapFragment
        mapFragment?.getMapAsync(this)

        if (intent.getBooleanExtra(EXTRA_OPEN_CAMERA, false)) {
            findViewById<View>(R.id.dashboardRoot).post {
                showMediaCaptureDialog()
            }
        }

        findViewById<LinearLayout>(R.id.dashboardNavActivity).setOnClickListener {
            startActivity(Intent(this, MyActivityActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.dashboardNavNotification).setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.dashboardNavProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.dashboardCameraFab).setOnClickListener {
            showMediaCaptureDialog()
        }
    }

    private fun showMediaCaptureDialog() {
        val dialog = Dialog(this, R.style.Theme_MediaCaptureDialog)
        dialog.setContentView(R.layout.dialog_media_capture)
        dialog.window?.apply {
            setLayout(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT
            )
            setBackgroundDrawableResource(android.R.color.transparent)
        }

        dialog.findViewById<View>(R.id.mediaCaptureDimRoot).setOnClickListener {
            dialog.dismiss()
        }
        dialog.findViewById<View>(R.id.mediaCaptureCard).setOnClickListener { }

        dialog.findViewById<View>(R.id.capturePhotoRow).setOnClickListener {
            dialog.dismiss()
            handleCameraAction(CameraAction.PHOTO)
        }
        dialog.findViewById<View>(R.id.captureVideoRow).setOnClickListener {
            dialog.dismiss()
            handleCameraAction(CameraAction.VIDEO)
        }

        dialog.show()
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val talamban = LatLng(10.3697, 123.9180)
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = true
        findViewById<LinearLayout>(R.id.dashboardBottomNav).doOnLayout { nav ->
            val fabLift = abs(resources.getDimensionPixelSize(R.dimen.bottom_nav_fab_lift))
            googleMap.setPadding(0, 0, 0, nav.height + fabLift + 16)
        }
        googleMap.addMarker(MarkerOptions().position(talamban).title("Talamban"))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(talamban, 14f))
        googleMap.setOnMapLoadedCallback {
            requestLocationDialog()
        }
    }

    private fun requestLocationDialog() {
        AlertDialog.Builder(this)
            .setTitle("Allow location access")
            .setMessage("Enable location while using the app to show your current position on the map.")
            .setNegativeButton("Not now", null)
            .setPositiveButton("Allow") { _, _ ->
                handleLocationPermission()
            }
            .show()
    }

    private fun handleLocationPermission() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation) {
            enableCurrentLocation()
        } else {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
        }
    }

    private fun handleCameraAction(action: CameraAction) {
        val hasCameraPermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (hasCameraPermission) {
            launchCameraAction(action)
        } else {
            pendingCameraAction = action
            requestCameraPermission.launch(Manifest.permission.CAMERA)
        }
    }

    private fun launchCameraAction(action: CameraAction) {
        when (action) {
            CameraAction.PHOTO -> CapturedMediaStore.capturedVideoUri = null
            CameraAction.VIDEO -> CapturedMediaStore.capturedBitmap = null
        }

        val captureIntent = when (action) {
            CameraAction.PHOTO -> Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            CameraAction.VIDEO -> Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)
        }

        if (captureIntent.resolveActivity(packageManager) == null) {
            Toast.makeText(this, "No camera app available.", Toast.LENGTH_SHORT).show()
            return
        }

        when (action) {
            CameraAction.PHOTO -> takePhotoLauncher.launch(captureIntent)
            CameraAction.VIDEO -> captureVideoLauncher.launch(captureIntent)
        }
    }

    private fun enableCurrentLocation() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation) {
            map?.isMyLocationEnabled = true
        }
    }
}

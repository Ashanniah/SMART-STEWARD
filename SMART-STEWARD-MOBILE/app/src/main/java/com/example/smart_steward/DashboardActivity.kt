package com.example.smart_steward

import android.Manifest
import android.content.Intent
<<<<<<< HEAD
=======
import android.graphics.Bitmap
>>>>>>> feat/mobile-UI-updated
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
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
    private var map: GoogleMap? = null
    private val takePhotoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
<<<<<<< HEAD
            Toast.makeText(this, "Photo captured successfully.", Toast.LENGTH_SHORT).show()
=======
            val capturedBitmap = result.data?.extras?.get("data") as? Bitmap
            if (capturedBitmap != null) {
                CapturedMediaStore.capturedBitmap = capturedBitmap
                startActivity(Intent(this, IncidentFlowActivity::class.java))
            } else {
                Toast.makeText(this, "Unable to read captured photo.", Toast.LENGTH_SHORT).show()
            }
>>>>>>> feat/mobile-UI-updated
        }
    }

    private val captureVideoLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
<<<<<<< HEAD
            Toast.makeText(this, "Video recorded successfully.", Toast.LENGTH_SHORT).show()
=======
            CapturedMediaStore.capturedBitmap = null
            startActivity(Intent(this, IncidentFlowActivity::class.java))
>>>>>>> feat/mobile-UI-updated
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

        findViewById<LinearLayout>(R.id.dashboardNavNotification).setOnClickListener {
            startActivity(Intent(this, NotificationActivity::class.java))
        }

        findViewById<LinearLayout>(R.id.dashboardNavProfile).setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        findViewById<FrameLayout>(R.id.dashboardCameraFab).setOnClickListener {
            val photoIntent = Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE)
            if (photoIntent.resolveActivity(packageManager) != null) {
                takePhotoLauncher.launch(photoIntent)
            } else {
                Toast.makeText(this, "No camera app available.", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<FrameLayout>(R.id.dashboardCameraFab).setOnLongClickListener {
            val videoIntent = Intent(android.provider.MediaStore.ACTION_VIDEO_CAPTURE)
            if (videoIntent.resolveActivity(packageManager) != null) {
                captureVideoLauncher.launch(videoIntent)
            } else {
                Toast.makeText(this, "No camera app available.", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        val talamban = LatLng(10.3697, 123.9180)
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = true
        findViewById<LinearLayout>(R.id.dashboardBottomNav).doOnLayout { nav ->
            googleMap.setPadding(0, 0, 0, nav.height + 16)
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

package com.example.smart_steward

import android.Manifest
import android.app.Dialog
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.abs
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.model.Place.Field
import com.google.android.libraries.places.widget.AutocompleteSupportFragment
import com.google.android.libraries.places.widget.listener.PlaceSelectionListener
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.chip.Chip
import com.google.android.gms.common.api.Status
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

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
    private var reportsListener: ListenerRegistration? = null
    private lateinit var adapter: DashboardNearIncidentsAdapter
    private var bottomSheetBehavior: BottomSheetBehavior<LinearLayout>? = null
    private lateinit var bottomSheet: LinearLayout
    private lateinit var chipTypeDumping: Chip
    private lateinit var chipTypeBurning: Chip
    private lateinit var chipTypeLogging: Chip
    private var allReports: List<UserReport> = emptyList()
    private var filteredReports: List<UserReport> = emptyList()
    private var mapScope: DashboardMapScope = DashboardMapScope.ALL
    private var typeFilter: DashboardTypeFilter = DashboardTypeFilter.ALL
    private val reportMarkers = LinkedHashMap<String, Marker>()
    private val agencyMarkers = LinkedHashMap<String, Marker>()
    private var activeQuickCardReport: UserReport? = null

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

        setupBottomSheetList()
        setupFilters()
        setupFloatingControls()
        setupSearch()
        setupQuickCard()

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

        watchReports()
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
        val talamban = defaultMapCenter()
        googleMap.uiSettings.isMapToolbarEnabled = false
        googleMap.uiSettings.isMyLocationButtonEnabled = false
        googleMap.uiSettings.isCompassEnabled = false
        findViewById<LinearLayout>(R.id.dashboardBottomNav).doOnLayout { applyMapPadding() }
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(talamban, 14f))
        googleMap.setOnInfoWindowClickListener { marker ->
            val report = marker.tag as? UserReport ?: return@setOnInfoWindowClickListener
            focusReport(report, openSheet = true)
        }
        googleMap.setOnMarkerClickListener { marker ->
            val report = marker.tag as? UserReport ?: return@setOnMarkerClickListener false
            showQuickCard(report)
            true
        }
        renderMapLayers()
        googleMap.setOnMapLoadedCallback {
            requestLocationDialog()
        }
    }

    private fun setupBottomSheetList() {
        bottomSheet = findViewById(R.id.dashboardBottomSheet)
        bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            skipCollapsed = false
            isFitToContents = false
            expandedOffset = 110
            peekHeight = resources.getDimensionPixelSize(R.dimen.dashboard_bottom_sheet_peek)
            isHideable = true
        }

        adapter = DashboardNearIncidentsAdapter { report ->
            focusReport(report, openSheet = false)
        }
        findViewById<RecyclerView>(R.id.dashboardNearRecycler).apply {
            layoutManager = LinearLayoutManager(this@DashboardActivity)
            adapter = this@DashboardActivity.adapter
        }
    }

    private fun setupSearch() {
        val autocompleteFragment = supportFragmentManager
            .findFragmentById(R.id.autocomplete_container) as? AutocompleteSupportFragment
        autocompleteFragment?.apply {
            setPlaceFields(
                listOf(
                    Place.Field.ID,
                    Place.Field.DISPLAY_NAME,
                    Place.Field.LOCATION,
                    Place.Field.FORMATTED_ADDRESS
                )
            )
            setCountries(listOf("PH"))
            setOnPlaceSelectedListener(object : PlaceSelectionListener {
                override fun onPlaceSelected(place: Place) {
                    val latLng = place.location
                    if (latLng != null) {
                        map?.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(latLng, 15f)
                        )
                        Toast.makeText(
                            this@DashboardActivity,
                            place.displayName ?: "Moved to location",
                            Toast.LENGTH_SHORT
                        ).show()
                    } else {
                        Toast.makeText(
                            this@DashboardActivity,
                            "Unable to get location coordinates",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

                override fun onError(status: Status) {
                    Toast.makeText(
                        this@DashboardActivity,
                        "Search error: ${status.statusMessage}",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            })
        }
    }

    private fun setupFloatingControls() {
        findViewById<ImageButton>(R.id.dashboardZoomIn).setOnClickListener {
            map?.animateCamera(CameraUpdateFactory.zoomIn())
        }
        findViewById<ImageButton>(R.id.dashboardZoomOut).setOnClickListener {
            map?.animateCamera(CameraUpdateFactory.zoomOut())
        }
        findViewById<ImageButton>(R.id.dashboardMyLocation).setOnClickListener {
            centerOnCurrentLocation(showCard = false)
        }
        findViewById<ImageButton>(R.id.dashboardLayerButton).setOnClickListener {
            mapScope = when (mapScope) {
                DashboardMapScope.ALL -> DashboardMapScope.INCIDENTS
                DashboardMapScope.INCIDENTS -> DashboardMapScope.ILLEGAL_ACTIVITIES
                DashboardMapScope.ILLEGAL_ACTIVITIES -> DashboardMapScope.AGENCIES
                DashboardMapScope.AGENCIES -> DashboardMapScope.ALL
            }
            bindScopeChips()
            applyFiltersAndRender()
        }
    }

    private fun setupFilters() {
        val scopeAll = findViewById<Chip>(R.id.chipScopeAll)
        val scopeIncidents = findViewById<Chip>(R.id.chipScopeIncidents)
        val scopeIllegal = findViewById<Chip>(R.id.chipScopeIllegal)
        val scopeAgencies = findViewById<Chip>(R.id.chipScopeAgencies)
        scopeAll.setOnClickListener { mapScope = DashboardMapScope.ALL; bindScopeChips(); applyFiltersAndRender() }
        scopeIncidents.setOnClickListener { mapScope = DashboardMapScope.INCIDENTS; bindScopeChips(); applyFiltersAndRender() }
        scopeIllegal.setOnClickListener { mapScope = DashboardMapScope.ILLEGAL_ACTIVITIES; bindScopeChips(); applyFiltersAndRender() }
        scopeAgencies.setOnClickListener { mapScope = DashboardMapScope.AGENCIES; bindScopeChips(); applyFiltersAndRender() }

        val typeAll = findViewById<Chip>(R.id.chipTypeAll)
        chipTypeDumping = findViewById(R.id.chipTypeDumping)
        chipTypeBurning = findViewById(R.id.chipTypeBurning)
        chipTypeLogging = findViewById(R.id.chipTypeLogging)
        val typeDump = chipTypeDumping
        val typeBurn = chipTypeBurning
        val typeLog = chipTypeLogging
        typeAll.setOnClickListener { typeFilter = DashboardTypeFilter.ALL; bindTypeChips(); applyFiltersAndRender() }
        typeDump.setOnClickListener { typeFilter = DashboardTypeFilter.DUMPING; bindTypeChips(); applyFiltersAndRender() }
        typeBurn.setOnClickListener { typeFilter = DashboardTypeFilter.BURNING; bindTypeChips(); applyFiltersAndRender() }
        typeLog.setOnClickListener { typeFilter = DashboardTypeFilter.LOGGING; bindTypeChips(); applyFiltersAndRender() }
        bindScopeChips()
        bindTypeChips()
    }

    private fun bindScopeChips() {
        findViewById<Chip>(R.id.chipScopeAll).isChecked = mapScope == DashboardMapScope.ALL
        findViewById<Chip>(R.id.chipScopeIncidents).isChecked = mapScope == DashboardMapScope.INCIDENTS
        findViewById<Chip>(R.id.chipScopeIllegal).isChecked = mapScope == DashboardMapScope.ILLEGAL_ACTIVITIES
        findViewById<Chip>(R.id.chipScopeAgencies).isChecked = mapScope == DashboardMapScope.AGENCIES
    }

    private fun bindTypeChips() {
        findViewById<Chip>(R.id.chipTypeAll).isChecked = typeFilter == DashboardTypeFilter.ALL
        findViewById<Chip>(R.id.chipTypeDumping).isChecked = typeFilter == DashboardTypeFilter.DUMPING
        findViewById<Chip>(R.id.chipTypeBurning).isChecked = typeFilter == DashboardTypeFilter.BURNING
        findViewById<Chip>(R.id.chipTypeLogging).isChecked = typeFilter == DashboardTypeFilter.LOGGING
    }

    private fun watchReports() {
        reportsListener?.remove()
        reportsListener = MapReportsRepository.watchRecentReports(
            onUpdate = { list ->
                allReports = list
                applyFiltersAndRender()
            },
            onError = { msg ->
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        )
    }

    private fun applyFiltersAndRender() {
        updateTypeChipVisibility()
        val incidentFiltered = when (mapScope) {
            DashboardMapScope.AGENCIES -> emptyList()
            DashboardMapScope.ILLEGAL_ACTIVITIES ->
                allReports.filter { isIllegalActivityType(it.incidentType) && matchesTypeFilter(it, typeFilter) }
            DashboardMapScope.ALL, DashboardMapScope.INCIDENTS ->
                allReports.filter { matchesTypeFilter(it, typeFilter) }
        }
        filteredReports = incidentFiltered
        adapter.submitList(filteredReports)
        findViewById<TextView>(R.id.dashboardNearCount).text = "${filteredReports.size} reports in area"
        updateBottomSheetVisibility()
        renderMapLayers()
        applyMapPadding()
    }

    private fun updateTypeChipVisibility() {
        val hasDumping = allReports.any { matchesTypeFilter(it, DashboardTypeFilter.DUMPING) }
        val hasBurning = allReports.any { matchesTypeFilter(it, DashboardTypeFilter.BURNING) }
        val hasLogging = allReports.any { matchesTypeFilter(it, DashboardTypeFilter.LOGGING) }
        chipTypeDumping.visibility = if (hasDumping) View.VISIBLE else View.GONE
        chipTypeBurning.visibility = if (hasBurning) View.VISIBLE else View.GONE
        chipTypeLogging.visibility = if (hasLogging) View.VISIBLE else View.GONE

        val selectedHidden = (typeFilter == DashboardTypeFilter.DUMPING && !hasDumping) ||
            (typeFilter == DashboardTypeFilter.BURNING && !hasBurning) ||
            (typeFilter == DashboardTypeFilter.LOGGING && !hasLogging)
        if (selectedHidden) {
            typeFilter = DashboardTypeFilter.ALL
            bindTypeChips()
        }
    }

    private fun updateBottomSheetVisibility() {
        val hasReports = allReports.isNotEmpty()
        if (hasReports) {
            bottomSheet.visibility = View.VISIBLE
            if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        } else {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
            bottomSheet.visibility = View.GONE
        }
    }

    private fun renderMapLayers() {
        val gMap = map ?: return
        reportMarkers.values.forEach { it.remove() }
        reportMarkers.clear()
        agencyMarkers.values.forEach { it.remove() }
        agencyMarkers.clear()

        if (mapScope != DashboardMapScope.AGENCIES) {
            filteredReports.forEach { report ->
                val marker = gMap.addMarker(
                    MarkerOptions()
                        .position(report.effectivePosition())
                        .title(report.displayTitle())
                        .snippet("${report.locationDisplay()} · ${report.status.name.replace("_", " ")}")
                        .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(markerHueForReport(report)))
                )
                if (marker != null) {
                    marker.tag = report
                    reportMarkers[report.id] = marker
                }
            }
        }

        if (mapScope == DashboardMapScope.ALL || mapScope == DashboardMapScope.AGENCIES) {
            DEFAULT_AGENCY_PINS.forEach { agency ->
                val marker = gMap.addMarker(
                    MarkerOptions()
                        .position(agency.position)
                        .title(agency.shortLabel)
                        .snippet(agency.name)
                        .icon(com.google.android.gms.maps.model.BitmapDescriptorFactory.defaultMarker(
                            com.google.android.gms.maps.model.BitmapDescriptorFactory.HUE_AZURE
                        ))
                )
                if (marker != null) agencyMarkers[agency.id] = marker
            }
        }
    }

    private fun focusReport(report: UserReport, openSheet: Boolean) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(report.effectivePosition(), 16.2f))
        reportMarkers[report.id]?.showInfoWindow()
        showQuickCard(report)
        if (openSheet) {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
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

    private fun centerOnCurrentLocation(showCard: Boolean = false) {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFineLocation) {
            requestLocationPermission.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            return
        }
        LocationServices.getFusedLocationProviderClient(this).lastLocation
            .addOnCompleteListener { task ->
                val location = if (task.isSuccessful) task.result else null
                if (location != null) {
                    map?.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                            LatLng(location.latitude, location.longitude),
                            16f
                        )
                    )
                    if (showCard) {
                        val nearest = filteredReports.minByOrNull {
                            val p = it.effectivePosition()
                            val dy = p.latitude - location.latitude
                            val dx = p.longitude - location.longitude
                            (dy * dy) + (dx * dx)
                        }
                        if (nearest != null) showQuickCard(nearest)
                    }
                } else {
                    Toast.makeText(this, "Current location unavailable.", Toast.LENGTH_SHORT).show()
                }
            }
    }

    private fun setupQuickCard() {
        findViewById<ImageButton>(R.id.dashboardQuickDismiss).setOnClickListener {
            hideQuickCard()
        }
        findViewById<TextView>(R.id.dashboardQuickSecondaryBtn).setOnClickListener {
            hideQuickCard()
        }
        findViewById<TextView>(R.id.dashboardQuickPrimaryBtn).setOnClickListener {
            val report = activeQuickCardReport
            if (report != null) {
                Toast.makeText(this, "Notified ${report.assignedAgency.ifBlank { "DENR" }}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showQuickCard(report: UserReport) {
        activeQuickCardReport = report
        findViewById<TextView>(R.id.dashboardQuickType).text = report.displayTitle()
        findViewById<TextView>(R.id.dashboardQuickTitle).text = report.displayTitle()
        val dateText = report.submittedAt?.let { java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it) }
            ?: "recently"
        findViewById<TextView>(R.id.dashboardQuickMeta).text = "${report.locationDisplay()} - Reported $dateText"
        findViewById<LinearLayout>(R.id.dashboardQuickCard).visibility = View.VISIBLE
    }

    private fun hideQuickCard() {
        findViewById<LinearLayout>(R.id.dashboardQuickCard).visibility = View.GONE
        activeQuickCardReport = null
    }

    private fun applyMapPadding() {
        val gMap = map ?: return
        val nav = findViewById<LinearLayout>(R.id.dashboardBottomNav)
        val fabLift = abs(resources.getDimensionPixelSize(R.dimen.bottom_nav_fab_lift))
        val sheetPad = if (::bottomSheet.isInitialized && bottomSheet.visibility == View.VISIBLE) {
            resources.getDimensionPixelSize(R.dimen.dashboard_bottom_sheet_peek)
        } else {
            0
        }
        gMap.setPadding(0, 180, 0, nav.height + fabLift + sheetPad + 24)
    }

    override fun onDestroy() {
        reportsListener?.remove()
        reportsListener = null
        super.onDestroy()
    }
}

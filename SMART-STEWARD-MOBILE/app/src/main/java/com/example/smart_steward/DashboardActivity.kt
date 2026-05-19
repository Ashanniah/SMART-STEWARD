package com.example.smart_steward

import android.Manifest
import android.app.Dialog
import android.content.res.ColorStateList
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import kotlin.math.abs
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.doOnLayout
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
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
import com.google.android.material.chip.ChipGroup
import com.google.android.gms.common.api.Status
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.ListenerRegistration
import java.util.Locale

class DashboardActivity : AppCompatActivity(), OnMapReadyCallback {

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
    private lateinit var typeFilterChipGroup: ChipGroup
    private var allReports: List<UserReport> = emptyList()
    private var filteredReports: List<UserReport> = emptyList()
    private var mapScope: DashboardMapScope = DashboardMapScope.ALL
    /** Null = show all incident types; otherwise matches [UserReport.displayTitle]. */
    private var selectedIncidentTypeLabel: String? = null
    /** Null = all agencies, else one of DENR/PNP/BFP/Barangay. */
    private var selectedAgencyLabel: String? = null
    private val reportMarkers = LinkedHashMap<String, Marker>()
    private val agencyMarkers = LinkedHashMap<String, Marker>()
    private var activeQuickCardReport: UserReport? = null
    private var pendingFocusReportId: String? = null
    private var pendingFocusLat: Double? = null
    private var pendingFocusLng: Double? = null

    companion object {
        const val EXTRA_OPEN_CAMERA = "open_camera"
        const val EXTRA_FOCUS_REPORT_ID = "focus_report_id"
        const val EXTRA_FOCUS_LAT = "focus_lat"
        const val EXTRA_FOCUS_LNG = "focus_lng"
        private const val PREFS_NAME = "dashboard_prefs"
        private const val KEY_LOCATION_DIALOG_SHOWN = "location_dialog_shown"
        /** Chip.tag for the “All” filter (empty string — not a valid report title). */
        private const val TYPE_CHIP_TAG_ALL = ""
        private val AGENCY_FILTER_OPTIONS = listOf("DENR", "PNP", "BFP", "Barangay")
    }

    private fun isLocationDialogShown(): Boolean {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        return prefs.getBoolean(KEY_LOCATION_DIALOG_SHOWN, false)
    }

    private fun setLocationDialogShown() {
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_LOCATION_DIALOG_SHOWN, true).apply()
    }

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
                ?: result.data?.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
            if (uri != null) {
                CapturedMediaStore.capturedBitmap = null
                CapturedMediaStore.capturedVideoUri =
                    MediaCapturePersistence.copyVideoToCache(this, uri) ?: uri
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

        captureFocusIntent(intent)

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

        findViewById<LinearLayout>(R.id.dashboardNavHistory).setOnClickListener {
            startActivity(Intent(this, ReportHistoryActivity::class.java))
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
        updateZoomControlsPosition()
    }

    override fun onResume() {
        super.onResume()
        updateNotificationBadge()
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
        runPendingFocusAfterRender()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        captureFocusIntent(intent)
        applyFiltersAndRender()
    }

    private fun captureFocusIntent(i: Intent?) {
        if (i == null) return
        val id = i.getStringExtra(EXTRA_FOCUS_REPORT_ID)
        val hasLatLng = i.hasExtra(EXTRA_FOCUS_LAT) && i.hasExtra(EXTRA_FOCUS_LNG)
        if (id == null && !hasLatLng) return
        pendingFocusReportId = id
        if (hasLatLng) {
            val lat = i.getDoubleExtra(EXTRA_FOCUS_LAT, Double.NaN)
            val lng = i.getDoubleExtra(EXTRA_FOCUS_LNG, Double.NaN)
            if (!lat.isNaN() && !lng.isNaN()) {
                pendingFocusLat = lat
                pendingFocusLng = lng
            }
        } else if (id != null) {
            pendingFocusLat = null
            pendingFocusLng = null
        }
    }

    private fun runPendingFocusAfterRender() {
        val gMap = map ?: return
        val id = pendingFocusReportId

        if (id != null) {
            if (mapScope != DashboardMapScope.ALL || selectedIncidentTypeLabel != null) {
                mapScope = DashboardMapScope.ALL
                selectedIncidentTypeLabel = null
                bindScopeChips()
                applyFiltersAndRender()
                return
            }
            val report = allReports.find { it.id == id }
            if (report != null) {
                pendingFocusReportId = null
                pendingFocusLat = null
                pendingFocusLng = null
                focusReport(report, openSheet = false)
                return
            }
        }

        val lat = pendingFocusLat
        val lng = pendingFocusLng
        if (lat != null && lng != null) {
            gMap.animateCamera(CameraUpdateFactory.newLatLngZoom(LatLng(lat, lng), 16.2f))
            if (id == null) {
                pendingFocusLat = null
                pendingFocusLng = null
            }
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
            showReportDetailsDialog(report)
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
    }

    private fun setupFilters() {
        val scopeAll = findViewById<Chip>(R.id.chipScopeAll)
        val scopeIncidents = findViewById<Chip>(R.id.chipScopeIncidents)
        val scopeIllegal = findViewById<Chip>(R.id.chipScopeIllegal)
        val scopeAgencies = findViewById<Chip>(R.id.chipScopeAgencies)
        scopeAll.setOnClickListener {
            mapScope = DashboardMapScope.ALL
            selectedAgencyLabel = null
            bindScopeChips()
            applyFiltersAndRender()
        }
        scopeIncidents.setOnClickListener {
            mapScope = DashboardMapScope.INCIDENTS
            selectedAgencyLabel = null
            bindScopeChips()
            applyFiltersAndRender()
        }
        scopeIllegal.setOnClickListener {
            mapScope = DashboardMapScope.ILLEGAL_ACTIVITIES
            selectedAgencyLabel = null
            bindScopeChips()
            applyFiltersAndRender()
        }
        scopeAgencies.setOnClickListener {
            mapScope = DashboardMapScope.AGENCIES
            bindScopeChips()
            showAgencyDropdown(scopeAgencies)
            applyFiltersAndRender()
        }

        typeFilterChipGroup = findViewById(R.id.dashboardTypeFilterRow)
        bindScopeChips()
    }

    private fun bindScopeChips() {
        findViewById<Chip>(R.id.chipScopeAll).isChecked = mapScope == DashboardMapScope.ALL
        findViewById<Chip>(R.id.chipScopeIncidents).isChecked = mapScope == DashboardMapScope.INCIDENTS
        findViewById<Chip>(R.id.chipScopeIllegal).isChecked = mapScope == DashboardMapScope.ILLEGAL_ACTIVITIES
        findViewById<Chip>(R.id.chipScopeAgencies).isChecked = mapScope == DashboardMapScope.AGENCIES
    }

    private fun showAgencyDropdown(anchor: View) {
        val popup = PopupMenu(this, anchor)
        popup.menu.add(0, 0, 0, "All agencies")
        AGENCY_FILTER_OPTIONS.forEachIndexed { idx, label ->
            popup.menu.add(0, idx + 1, idx + 1, label)
        }
        popup.setOnMenuItemClickListener { item ->
            selectedAgencyLabel = if (item.itemId == 0) null else item.title.toString()
            applyFiltersAndRender()
            true
        }
        popup.show()
    }

    private fun reportsInCurrentScope(): List<UserReport> {
        return when (mapScope) {
            DashboardMapScope.AGENCIES -> emptyList()
            DashboardMapScope.ILLEGAL_ACTIVITIES ->
                allReports.filter { isIllegalActivityType(it.incidentType) }
            DashboardMapScope.ALL, DashboardMapScope.INCIDENTS -> allReports
        }
    }

    private fun matchesSelectedIncidentType(report: UserReport): Boolean {
        val sel = selectedIncidentTypeLabel ?: return true
        return report.displayTitle().equals(sel, ignoreCase = true)
    }

    /** One chip per distinct report title in scope + “All”. No synthetic “Other” row. */
    private fun syncIncidentTypeChips() {
        if (mapScope == DashboardMapScope.AGENCIES) {
            typeFilterChipGroup.removeAllViews()
            return
        }
        val inScope = reportsInCurrentScope()
        val distinctTypes = inScope.map { it.displayTitle().trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()

        if (selectedIncidentTypeLabel != null &&
            distinctTypes.none { it.equals(selectedIncidentTypeLabel, ignoreCase = true) }
        ) {
            selectedIncidentTypeLabel = null
        }

        typeFilterChipGroup.removeAllViews()
        val density = resources.displayMetrics.density
        val cornerPx = 18f * density
        val minHeightPx = 36f * density

        fun addTypeChip(label: String, selectionKey: String?) {
            val chip = Chip(this, null, R.style.Widget_SMARTSTEWARD_FilterChip)
            chip.text = label
            chip.tag = selectionKey ?: TYPE_CHIP_TAG_ALL
            chip.chipCornerRadius = cornerPx
            chip.chipMinHeight = minHeightPx
            chip.isSingleLine = true
            chip.setEnsureMinTouchTargetSize(false)
            chip.isCheckable = true
            chip.isCheckedIconVisible = false
            chip.checkedIcon = null
            chip.chipBackgroundColor = ColorStateList.valueOf(
                ContextCompat.getColor(this, R.color.register_button_green)
            )
            chip.setTextColor(
                ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
            )
            chip.setOnClickListener {
                selectedIncidentTypeLabel = selectionKey
                applyFiltersAndRender()
            }
            typeFilterChipGroup.addView(chip)
        }

        addTypeChip(getString(R.string.dashboard_filter_all), null)
        for (t in distinctTypes) {
            addTypeChip(t, t)
        }

        for (i in 0 until typeFilterChipGroup.childCount) {
            val chip = typeFilterChipGroup.getChildAt(i) as Chip
            val key = chip.tag as? String ?: TYPE_CHIP_TAG_ALL
            chip.isChecked = when {
                selectedIncidentTypeLabel == null -> key == TYPE_CHIP_TAG_ALL
                else -> key.equals(selectedIncidentTypeLabel, ignoreCase = true)
            }
        }
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
        syncIncidentTypeChips()
        val incidentFiltered = when (mapScope) {
            DashboardMapScope.AGENCIES ->
                allReports.filter { matchesReportAgencyFilter(it, selectedAgencyLabel) }
            DashboardMapScope.ILLEGAL_ACTIVITIES ->
                allReports.filter { isIllegalActivityType(it.incidentType) && matchesSelectedIncidentType(it) }
            DashboardMapScope.INCIDENTS ->
                allReports.filter { matchesSelectedIncidentType(it) }
            DashboardMapScope.ALL ->
                allReports.filter { matchesSelectedIncidentType(it) }
        }
        filteredReports = incidentFiltered
        adapter.submitList(filteredReports)
        findViewById<TextView>(R.id.dashboardNearCount).text = "${filteredReports.size} reports in area"
        updateBottomSheetVisibility()
        renderMapLayers()
        applyMapPadding()
        runPendingFocusAfterRender()
    }

    private fun updateBottomSheetVisibility() {
        val hasListToShow = filteredReports.isNotEmpty()
        if (hasListToShow) {
            bottomSheet.visibility = View.VISIBLE
            if (bottomSheetBehavior?.state == BottomSheetBehavior.STATE_HIDDEN) {
                bottomSheetBehavior?.state = BottomSheetBehavior.STATE_COLLAPSED
            }
        } else {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_HIDDEN
            bottomSheet.visibility = View.GONE
        }
        updateZoomControlsPosition()
    }

    private fun updateZoomControlsPosition() {
        if (!::bottomSheet.isInitialized) return
        val controls = findViewById<LinearLayout>(R.id.dashboardFloatingControls)
        val lp = controls.layoutParams as CoordinatorLayout.LayoutParams
        val marginRes = if (bottomSheet.visibility == View.VISIBLE) {
            R.dimen.dashboard_zoom_controls_margin_with_sheet
        } else {
            R.dimen.dashboard_zoom_controls_margin_no_sheet
        }
        lp.bottomMargin = resources.getDimensionPixelSize(marginRes)
        controls.layoutParams = lp
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
                        .snippet("${report.locationDisplay()} · ${markerSnippetStatus(report.status)}")
                        .icon(markerDescriptorForReport(this, report))
                )
                if (marker != null) {
                    marker.tag = report
                    reportMarkers[report.id] = marker
                }
            }
        }

        // Agency scope now filters report markers by assigned agency and keeps status colors.
        // No separate blue agency pins are rendered.
    }

    private fun focusReport(report: UserReport, openSheet: Boolean) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(report.effectivePosition(), 16.2f))
        reportMarkers[report.id]?.showInfoWindow()
        showQuickCard(report)
        if (openSheet && bottomSheet.visibility == View.VISIBLE) {
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
        }
    }

    private fun requestLocationDialog() {
        val hasFineLocation = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFineLocation) {
            enableCurrentLocation()
            return
        }

        if (isLocationDialogShown()) {
            return
        }

        setLocationDialogShown()

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

        val locationManager = getSystemService(LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) && 
            !locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            Toast.makeText(this, "Please enable location services in Settings.", Toast.LENGTH_LONG).show()
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
                    Toast.makeText(this, "Unable to get current location. Please try again or ensure GPS is enabled.", Toast.LENGTH_LONG).show()
                }
            }
    }

    private fun setupQuickCard() {
        findViewById<TextView>(R.id.dashboardQuickSecondaryBtn).setOnClickListener {
            hideQuickCard()
        }
        findViewById<TextView>(R.id.dashboardQuickPrimaryBtn).setOnClickListener {
            val report = activeQuickCardReport ?: return@setOnClickListener
            when (report.status) {
                ReportStatusUi.IN_PROGRESS -> {
                    Toast.makeText(
                        this,
                        "You can’t notify because the report is already in progress.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                ReportStatusUi.RESOLVED -> {
                    Toast.makeText(
                        this,
                        "You can’t notify because the report is already resolved.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                ReportStatusUi.REJECTED -> {
                    Toast.makeText(
                        this,
                        "You can’t notify because the report is already closed.",
                        Toast.LENGTH_LONG
                    ).show()
                    return@setOnClickListener
                }
                ReportStatusUi.PENDING -> Unit
            }
            if (report.assignedAgency.isBlank()) {
                Toast.makeText(this, R.string.dashboard_no_agency_assigned, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            AgencyNotificationsFirestore.sendCitizenNotify(
                report,
                onSuccess = {
                    Toast.makeText(
                        this,
                        getString(
                            R.string.dashboard_notify_sent,
                            AgencyCanonical.shortName(report.assignedAgency)
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    hideQuickCard()
                },
                onError = { msg -> Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
            )
        }
    }

    private fun showQuickCard(report: UserReport) {
        activeQuickCardReport = report
        findViewById<TextView>(R.id.dashboardQuickTitle).text = report.displayTitle()
        val primaryBtn = findViewById<TextView>(R.id.dashboardQuickPrimaryBtn)
        primaryBtn.text =
            if (report.assignedAgency.isNotBlank()) {
                getString(
                    R.string.dashboard_notify_agency,
                    AgencyCanonical.shortName(report.assignedAgency)
                )
            } else {
                getString(R.string.dashboard_notify_generic)
            }
        val canNotify = report.status == ReportStatusUi.PENDING
        primaryBtn.alpha = if (canNotify) 1f else 0.55f
        val dateText = report.submittedAt?.let { java.text.SimpleDateFormat("MMM d, yyyy", Locale.getDefault()).format(it) }
            ?: "recently"
        val coords = report.latitude?.let { lat ->
            report.longitude?.let { lng ->
                " · ${String.format(Locale.US, "%.4f°, %.4f°", lat, lng)}"
            }
        }.orEmpty()
        findViewById<TextView>(R.id.dashboardQuickMeta).text =
            "${report.locationDisplay()} - Reported $dateText$coords"
        findViewById<LinearLayout>(R.id.dashboardQuickCard).visibility = View.VISIBLE
    }

    private fun hideQuickCard() {
        findViewById<LinearLayout>(R.id.dashboardQuickCard).visibility = View.GONE
        activeQuickCardReport = null
    }

    private fun showReportDetailsDialog(report: UserReport) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_report_receipt, null)
        dialogView.findViewById<TextView>(R.id.receiptHeaderId).text = report.displayReportRef()
        val content = dialogView.findViewById<LinearLayout>(R.id.receiptContent)

        fun addRow(label: String, value: String, highlightAgency: Boolean = false) {
            val row = layoutInflater.inflate(R.layout.item_receipt_row, content, false)
            row.findViewById<TextView>(R.id.receiptRowLabel).text = label
            val valueView = row.findViewById<TextView>(R.id.receiptRowValue)
            valueView.text = value.ifBlank { "—" }
            if (highlightAgency) {
                valueView.setTextColor(getColor(R.color.register_button_green))
            }
            content.addView(row)
        }

        fun addDescription(label: String, body: String) {
            val block = layoutInflater.inflate(R.layout.item_receipt_description, content, false)
            block.findViewById<TextView>(R.id.receiptDescLabel).text = label
            block.findViewById<TextView>(R.id.receiptDescBody).text = body.ifBlank { "—" }
            content.addView(block)
        }

        val dateFmt = java.text.SimpleDateFormat("MMM d, yyyy · h:mm a", Locale.getDefault())
        val submitted = report.submittedAt?.let { dateFmt.format(it) } ?: "—"

        addRow(getString(R.string.my_activity_detail_report_id), report.displayReportRef())
        addRow(getString(R.string.my_activity_detail_report_type), report.incidentType.trim().ifBlank { "—" })
        addRow(getString(R.string.my_activity_detail_date_submitted), submitted)
        addRow(getString(R.string.my_activity_detail_location), report.locationDisplay())
        addDescription(getString(R.string.my_activity_detail_description), report.description)

        val videoUrl = report.videoUrl.trim()
        val photoUrl = report.photoUrl.trim()
        val attachmentSummary = when {
            videoUrl.isNotEmpty() -> getString(R.string.my_activity_attachment_video)
            photoUrl.isNotEmpty() -> getString(R.string.my_activity_attachment_photo)
            else -> getString(R.string.my_activity_attachment_none)
        }
        addRow(getString(R.string.my_activity_detail_attachment), attachmentSummary)

        val density = resources.displayMetrics.density
        when {
            videoUrl.isNotEmpty() -> {
                val frame = FrameLayout(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (180 * density).toInt()
                    ).apply {
                        val m = (16 * density).toInt()
                        setMargins(m, 0, m, (8 * density).toInt())
                    }
                    isClickable = true
                    isFocusable = true
                }
                val thumb = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.MATCH_PARENT,
                        FrameLayout.LayoutParams.MATCH_PARENT
                    )
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    val preview = photoUrl.ifBlank { videoUrl }
                    load(preview)
                }
                val playOverlay = ImageView(this).apply {
                    layoutParams = FrameLayout.LayoutParams(
                        (44 * density).toInt(),
                        (44 * density).toInt()
                    ).apply { gravity = android.view.Gravity.CENTER }
                    setBackgroundResource(R.drawable.bg_play_circle)
                    setImageResource(android.R.drawable.ic_media_play)
                    imageTintList = ColorStateList.valueOf(getColor(R.color.white))
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val p = (8 * density).toInt()
                    setPadding(p, p, p, p)
                }
                frame.setOnClickListener { MediaPlayback.openRemoteVideo(this, videoUrl) }
                frame.addView(thumb)
                frame.addView(playOverlay)
                content.addView(frame)
            }
            photoUrl.isNotEmpty() -> {
                val image = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        (180 * density).toInt()
                    ).apply {
                        val m = (16 * density).toInt()
                        setMargins(m, 0, m, (8 * density).toInt())
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    load(photoUrl)
                    setOnClickListener { MediaPlayback.openRemoteImage(this@DashboardActivity, photoUrl) }
                }
                content.addView(image)
            }
        }

        addRow(
            getString(R.string.my_activity_detail_assigned),
            report.assignedAgency.trim().ifBlank { "—" },
            highlightAgency = true
        )

        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .create()
        dialogView.findViewById<TextView>(R.id.receiptCloseButton).setOnClickListener {
            dialog.dismiss()
        }
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
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

    private fun updateNotificationBadge() {
        val badge = findViewById<TextView>(R.id.dashboardNavBadge)
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
}

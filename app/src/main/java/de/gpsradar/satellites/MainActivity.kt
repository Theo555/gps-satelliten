package de.gpsradar.satellites

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.ui.GpsViewModel
import de.gpsradar.satellites.ui.components.AboutScreen
import de.gpsradar.satellites.ui.components.MeasurementScreen
import de.gpsradar.satellites.ui.components.NavigationScreen
import de.gpsradar.satellites.ui.components.PermissionAndStatusBanner
import de.gpsradar.satellites.ui.components.PositionFixScreen
import de.gpsradar.satellites.ui.components.RadarSkyView
import de.gpsradar.satellites.ui.components.SatelliteDetailDialog
import de.gpsradar.satellites.ui.components.SatelliteListScreen
import de.gpsradar.satellites.ui.components.SignalBarChart
import de.gpsradar.satellites.ui.theme.AccentCyan
import de.gpsradar.satellites.ui.theme.DeepNavy
import de.gpsradar.satellites.ui.theme.GpsRadarTheme
import de.gpsradar.satellites.ui.theme.SpaceNavy

private const val REPOSITORY_URL = "https://github.com/Theo555/gps-satelliten"
private const val DONATION_URL = "https://paypal.me/gps-satelliten"

enum class AppTab(val title: String, val icon: ImageVector, val tag: String) {
    RADAR("Radar", Icons.Default.Radar, "tab_radar"),
    SIGNALS("Signale", Icons.Default.BarChart, "tab_signals"),
    LIST("Liste", Icons.AutoMirrored.Filled.List, "tab_list"),
    POSITION("Position", Icons.Default.MyLocation, "tab_position"),
    NAVIGATION("Nav", Icons.Default.Navigation, "tab_navigation"),
    MESSUNG("Messung", Icons.Default.Speed, "tab_measurement")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GpsRadarTheme {
                GpsAppMainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GpsAppMainScreen(
    viewModel: GpsViewModel = viewModel()
) {
    val context = LocalContext.current
    val versionName = remember {
        @Suppress("DEPRECATION")
        try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "?"
        } catch (e: Exception) {
            "?"
        }
    }
    val versionCode = remember {
        @Suppress("DEPRECATION")
        try {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
            else info.versionCode.toLong()
        } catch (e: Exception) {
            0L
        }
    }
    val gpsState by viewModel.rawGpsState.collectAsState()
    val displayedSatellites by viewModel.displayedSatellites.collectAsState()
    val selectedFilter by viewModel.selectedFilter.collectAsState()
    val selectedSort by viewModel.selectedSort.collectAsState()
    val selectedSatellite by viewModel.selectedSatellite.collectAsState()
    val waypoints by viewModel.waypoints.collectAsState()
    val caches by viewModel.caches.collectAsState()
    val freeWaypoints by viewModel.freeWaypoints.collectAsState()
    val activeWaypointId by viewModel.activeWaypointId.collectAsState()
    val tracks by viewModel.tracks.collectAsState()
    val activeTrackId by viewModel.activeTrackId.collectAsState()
    val activeTrackProgress by viewModel.activeTrackProgress.collectAsState()
    val isRecording by viewModel.isRecording.collectAsState()
    val isRecordingPaused by viewModel.isRecordingPaused.collectAsState()
    val recordingPoints by viewModel.recordingPoints.collectAsState()
    val supportsNavigationMessages by viewModel.supportsNavigationMessages.collectAsState()
    val selectedSatelliteNavMessages by viewModel.selectedSatelliteNavMessages.collectAsState()
    val supportsRawMeasurements by viewModel.supportsRawMeasurements.collectAsState()
    val rawMeasurements by viewModel.rawMeasurements.collectAsState()
    val rawClock by viewModel.rawClock.collectAsState()
    val hintR13Map by viewModel.hintR13ByKey.collectAsState()

    var currentTab by remember { mutableStateOf(AppTab.RADAR) }
    var showAbout by remember { mutableStateOf(false) }

    // Bildschirm wach halten, solange ein Ziel/Track aktiv ist oder aufgezeichnet wird.
    val keepScreenOn = activeWaypointId != null || activeTrackId != null || isRecording
    val view = LocalView.current
    SideEffect { view.keepScreenOn = keepScreenOn }

    // Peilung zum aktiven Ziel für den Radar-Pfeil.
    val activeWaypoint = waypoints.find { it.id == activeWaypointId }
    val targetBearing: Float? = remember(gpsState.latitude, gpsState.longitude, activeWaypoint?.id) {
        val lat = gpsState.latitude
        val lon = gpsState.longitude
        val wp = activeWaypoint
        if (lat != null && lon != null && wp != null) {
            Navigation.initialBearingDegrees(lat, lon, wp.latitude, wp.longitude).toFloat()
        } else {
            null
        }
    }

    // Permission launcher for Location permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        viewModel.onPermissionResult(fineGranted || coarseGranted)
    }

    // Check permission on entry and lifecycle
    LaunchedEffect(Unit) {
        val hasFine = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (hasFine || hasCoarse) {
            viewModel.startTracking(true)
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            viewModel.stopTracking()
        }
    }

    // Back handler: schließt "Über die App", dann Satellite-Dialog, dann zurück zum Radar-Tab
    BackHandler(enabled = showAbout || selectedSatellite != null || currentTab != AppTab.RADAR) {
        if (showAbout) {
            showAbout = false
        } else if (selectedSatellite != null) {
            viewModel.selectSatellite(null)
        } else if (currentTab != AppTab.RADAR) {
            currentTab = AppTab.RADAR
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = SpaceNavy,
        topBar = {
            TopAppBar(
                navigationIcon = {
                    if (showAbout) {
                        IconButton(
                            onClick = { showAbout = false },
                            modifier = Modifier.testTag("about_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Zurück",
                                tint = Color.White
                            )
                        }
                    }
                },
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = (if (gpsState.isDemoMode) "Simulation aktiv" else "GNSS Empfänger") + " · v$versionName",
                            fontSize = 11.sp,
                            color = if (gpsState.isDemoMode) AccentCyan else Color(0xFF94A3B8)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepNavy,
                    titleContentColor = Color.White
                ),
                actions = {
                    // Über die App
                    IconButton(
                        onClick = { showAbout = true },
                        modifier = Modifier.size(40.dp).testTag("about_btn")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.HelpOutline,
                            contentDescription = "Über die App",
                            tint = if (showAbout) AccentCyan else Color(0xFF94A3B8),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    // Demo Mode toggle
                    IconButton(
                        onClick = { viewModel.toggleDemoMode() },
                        modifier = Modifier.testTag("toggle_demo_mode_action")
                    ) {
                        Icon(
                            imageVector = if (gpsState.isDemoMode) Icons.Filled.Science else Icons.Outlined.Science,
                            contentDescription = "Simulationsmodus umschalten",
                            tint = if (gpsState.isDemoMode) AccentCyan else Color(0xFF94A3B8)
                        )
                    }
                }
            )
        },
        bottomBar = {
            // Auf der Seite "Über die App" keine Navigationsleiste – der Zurück-Pfeil
            // in der Kopfzeile führt wieder heraus.
            if (!showAbout) NavigationBar(
                containerColor = DeepNavy,
                contentColor = Color.White
            ) {
                val visibleTabs = AppTab.entries.filter { it != AppTab.MESSUNG || supportsRawMeasurements }
                visibleTabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentTab == tab,
                        onClick = { currentTab = tab },
                        icon = {
                            Icon(
                                imageVector = tab.icon,
                                contentDescription = tab.title,
                                modifier = Modifier.size(22.dp)
                            )
                        },
                        label = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontWeight = if (currentTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = Color.Black,
                            selectedTextColor = AccentCyan,
                            indicatorColor = AccentCyan,
                            unselectedIconColor = Color(0xFF94A3B8),
                            unselectedTextColor = Color(0xFF94A3B8)
                        ),
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Permission and Status Banner (nicht auf der Info-Seite)
            if (!showAbout) PermissionAndStatusBanner(
                state = gpsState,
                onRequestPermission = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                        )
                    )
                },
                onToggleDemoMode = { viewModel.toggleDemoMode() }
            )

            // Main Content by Active Tab
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (showAbout) {
                    AboutScreen(
                        versionName = versionName,
                        versionCode = versionCode,
                        onOpenRepository = { openUrl(context, REPOSITORY_URL) },
                        onOpenDonation = { openUrl(context, DONATION_URL) }
                    )
                } else when (currentTab) {
                    AppTab.RADAR -> {
                        RadarSkyView(
                            state = gpsState,
                            satellites = displayedSatellites,
                            selectedFilter = selectedFilter,
                            onFilterSelected = { viewModel.setFilter(it) },
                            onToggleHeadingUp = { viewModel.toggleOrientationMode() },
                            onSatelliteClick = { viewModel.selectSatellite(it) },
                            targetBearing = targetBearing
                        )
                    }
                    AppTab.SIGNALS -> {
                        SignalBarChart(
                            state = gpsState,
                            satellites = displayedSatellites,
                            selectedFilter = selectedFilter,
                            onFilterSelected = { viewModel.setFilter(it) },
                            onSatelliteClick = { viewModel.selectSatellite(it) }
                        )
                    }
                    AppTab.LIST -> {
                        SatelliteListScreen(
                            state = gpsState,
                            satellites = displayedSatellites,
                            selectedFilter = selectedFilter,
                            selectedSort = selectedSort,
                            onFilterSelected = { viewModel.setFilter(it) },
                            onSortSelected = { viewModel.setSort(it) },
                            onSatelliteClick = { viewModel.selectSatellite(it) }
                        )
                    }
                    AppTab.POSITION -> {
                        PositionFixScreen(
                            state = gpsState
                        )
                    }
                    AppTab.NAVIGATION -> {
                        NavigationScreen(
                            state = gpsState,
                            waypoints = waypoints,
                            caches = caches,
                            freeWaypoints = freeWaypoints,
                            activeWaypointId = activeWaypointId,
                            tracks = tracks,
                            activeTrackId = activeTrackId,
                            activeTrackProgress = activeTrackProgress,
                            onAddCurrentPosition = { name, code -> viewModel.addWaypointAtCurrentPosition(name, code) },
                            onAddManual = { name, code, lat, lon -> viewModel.addWaypoint(name, code, lat, lon) },
                            onUpdateWaypoint = { wp -> viewModel.updateWaypoint(wp) },
                            onProjectWaypoint = { name, bearing, dist -> viewModel.projectWaypoint(name, bearing, dist) },
                            onToggleFound = { id -> viewModel.toggleFound(id) },
                            onDeleteWaypoint = { id -> viewModel.deleteWaypoint(id) },
                            onSetActiveWaypoint = { id -> viewModel.setActiveWaypoint(id) },
                            onDeleteTrack = { id -> viewModel.deleteTrack(id) },
                            onSetActiveTrack = { id -> viewModel.setActiveTrack(id) },
                            onImportWaypoints = { uri -> viewModel.importWaypoints(uri) },
                            onExportGpx = { uri -> viewModel.exportGpx(uri) },
                            onExportJson = { uri -> viewModel.exportJson(uri) },
                            hintR13Map = hintR13Map,
                            onToggleHintFormat = { key -> viewModel.toggleHintFormat(key) },
                            isRecording = isRecording,
                            isRecordingPaused = isRecordingPaused,
                            recordingPointCount = recordingPoints.size,
                            onStartRecording = { viewModel.startRecording() },
                            onStopRecording = { name -> viewModel.stopRecording(name) },
                            onDefaultRecordingName = { viewModel.defaultRecordingName() },
                            onPauseRecording = { viewModel.pauseRecording() },
                            onResumeRecording = { viewModel.resumeRecording() },
                            onReverseTrack = { id -> viewModel.reverseTrack(id) }
                        )
                    }
                    AppTab.MESSUNG -> {
                        MeasurementScreen(
                            measurements = rawMeasurements,
                            clock = rawClock
                        )
                    }
                }
            }
        }
    }

    // Detail Dialog for clicked satellite
    selectedSatellite?.let { satellite ->
        SatelliteDetailDialog(
            satellite = satellite,
            navigationMessages = selectedSatelliteNavMessages,
            supportsNavigationMessages = supportsNavigationMessages,
            onDismiss = { viewModel.selectSatellite(null) }
        )
    }
}

/** Öffnet einen Link im Browser. Die App selbst braucht dafür keine Internet-Berechtigung. */
private fun openUrl(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: Exception) {
        Toast.makeText(context, "Keine App zum Öffnen des Links gefunden", Toast.LENGTH_SHORT).show()
    }
}

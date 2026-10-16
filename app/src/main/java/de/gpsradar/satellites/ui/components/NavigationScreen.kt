package de.gpsradar.satellites.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.model.CACHE_TYPES
import de.gpsradar.satellites.model.Coordinates
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.model.WaypointCodec
import de.gpsradar.satellites.model.cacheTypeEmoji
import de.gpsradar.satellites.ui.theme.CardNavy
import de.gpsradar.satellites.ui.theme.SignalGreen
import java.util.Locale

@Composable
fun NavigationScreen(
    state: GpsLocationState,
    waypoints: List<Waypoint>,
    activeWaypointId: Long?,
    tracks: List<Track>,
    activeTrackId: Long?,
    activeTrackProgress: Int,
    onAddCurrentPosition: (name: String, code: String?) -> Unit,
    onAddManual: (name: String, code: String?, latitude: Double, longitude: Double) -> Unit,
    onUpdateWaypoint: (Waypoint) -> Unit,
    onProjectWaypoint: (name: String, bearingDegrees: Double, distanceMeters: Double) -> String,
    onToggleFound: (Long) -> Unit,
    onDeleteWaypoint: (Long) -> Unit,
    onSetActiveWaypoint: (Long?) -> Unit,
    onDeleteTrack: (Long) -> Unit,
    onSetActiveTrack: (Long?) -> Unit,
    onImportWaypoints: (Uri) -> String,
    onExportGpx: (Uri) -> Boolean,
    onExportJson: (Uri) -> Boolean,
    isRecording: Boolean,
    isRecordingPaused: Boolean,
    recordingPointCount: Int,
    onStartRecording: () -> Unit,
    onStopRecording: (String) -> String,
    onDefaultRecordingName: () -> String,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onReverseTrack: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Zielpunkte und Navigation
        WaypointsCard(
            state = state,
            waypoints = waypoints,
            activeWaypointId = activeWaypointId,
            onAddCurrentPosition = onAddCurrentPosition,
            onAddManual = onAddManual,
            onUpdateWaypoint = onUpdateWaypoint,
            onProjectWaypoint = onProjectWaypoint,
            onToggleFound = onToggleFound,
            onDeleteWaypoint = onDeleteWaypoint,
            onSetActiveWaypoint = onSetActiveWaypoint,
            onImportWaypoints = onImportWaypoints,
            onExportGpx = onExportGpx,
            onExportJson = onExportJson
        )

        // Tracks / Routen
        TracksCard(
            state = state,
            tracks = tracks,
            activeTrackId = activeTrackId,
            activeTrackProgress = activeTrackProgress,
            isRecording = isRecording,
            isRecordingPaused = isRecordingPaused,
            recordingPointCount = recordingPointCount,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onDefaultRecordingName = onDefaultRecordingName,
            onPauseRecording = onPauseRecording,
            onResumeRecording = onResumeRecording,
            onReverseTrack = onReverseTrack,
            onDeleteTrack = onDeleteTrack,
            onSetActiveTrack = onSetActiveTrack
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun WaypointsCard(
    state: GpsLocationState,
    waypoints: List<Waypoint>,
    activeWaypointId: Long?,
    onAddCurrentPosition: (name: String, code: String?) -> Unit,
    onAddManual: (name: String, code: String?, latitude: Double, longitude: Double) -> Unit,
    onUpdateWaypoint: (Waypoint) -> Unit,
    onProjectWaypoint: (name: String, bearingDegrees: Double, distanceMeters: Double) -> String,
    onToggleFound: (Long) -> Unit,
    onDeleteWaypoint: (Long) -> Unit,
    onSetActiveWaypoint: (Long?) -> Unit,
    onImportWaypoints: (Uri) -> String,
    onExportGpx: (Uri) -> Boolean,
    onExportJson: (Uri) -> Boolean
) {
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            val message = onImportWaypoints(u)
            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
        }
    }

    val exportGpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        uri?.let { u ->
            val ok = onExportGpx(u)
            Toast.makeText(context, if (ok) "GPX exportiert" else "Export fehlgeschlagen", Toast.LENGTH_SHORT).show()
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { u ->
            val ok = onExportJson(u)
            Toast.makeText(context, if (ok) "JSON exportiert" else "Export fehlgeschlagen", Toast.LENGTH_SHORT).show()
        }
    }

    var showCurrentDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showProjectionDialog by remember { mutableStateOf(false) }
    var editingWaypoint by remember { mutableStateOf<Waypoint?>(null) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("waypoints_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Zielpunkte (${waypoints.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                IconButton(
                    onClick = { showCurrentDialog = true },
                    modifier = Modifier.size(32.dp).testTag("add_waypoint_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLocation,
                        contentDescription = "Ziel hinzufügen",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val active = waypoints.find { it.id == activeWaypointId }

            if (active != null) {
                ActiveTargetSummary(
                    state = state,
                    waypoint = active,
                    onOpenMap = { openInMap(context, active) },
                    onStop = { onSetActiveWaypoint(null) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (waypoints.isEmpty()) {
                Text(
                    text = "Noch keine Ziele gespeichert. Speichere deine aktuelle Position als Zielpunkt mit eigenem Namen.",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    waypoints.forEach { wp ->
                        WaypointRow(
                            state = state,
                            waypoint = wp,
                            isActive = wp.id == activeWaypointId,
                            onClick = { onSetActiveWaypoint(wp.id) },
                            onEdit = { editingWaypoint = wp },
                            onToggleFound = { onToggleFound(wp.id) },
                            onOpenMap = { openInMap(context, wp) },
                            onDelete = { onDeleteWaypoint(wp.id) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { showCurrentDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("set_waypoint_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.AddLocation,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Position speichern")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showManualDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("manual_waypoint_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Flag,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Koordinaten eingeben")
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { showProjectionDialog = true },
                modifier = Modifier.fillMaxWidth().testTag("projection_btn")
            ) {
                Icon(
                    imageVector = Icons.Default.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Projektion (Peilung + Entfernung)")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Datei-Übertragung",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = {
                    importLauncher.launch(
                        arrayOf(
                            "application/gpx+xml",
                            "application/xml",
                            "text/xml",
                            "application/json",
                            "text/plain",
                            "*/*"
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth().testTag("import_btn")
            ) {
                Text("Importieren (GPX / JSON)")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { exportGpxLauncher.launch("gps_zielpunkte.gpx") },
                    modifier = Modifier.weight(1f).testTag("export_gpx_btn")
                ) {
                    Text("Export GPX", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { exportJsonLauncher.launch("gps_zielpunkte.json") },
                    modifier = Modifier.weight(1f).testTag("export_json_btn")
                ) {
                    Text("Export JSON", fontSize = 12.sp)
                }
            }
        }
    }

    if (showCurrentDialog) {
        AddWaypointDialog(
            onConfirm = { name, code ->
                onAddCurrentPosition(name, code)
                showCurrentDialog = false
            },
            onDismiss = { showCurrentDialog = false }
        )
    }

    if (showManualDialog) {
        ManualWaypointDialog(
            onConfirm = { name, code, latitude, longitude ->
                onAddManual(name, code, latitude, longitude)
                showManualDialog = false
            },
            onDismiss = { showManualDialog = false }
        )
    }

    editingWaypoint?.let { wp ->
        EditWaypointDialog(
            waypoint = wp,
            onConfirm = { updated ->
                onUpdateWaypoint(updated)
                editingWaypoint = null
            },
            onDismiss = { editingWaypoint = null }
        )
    }

    if (showProjectionDialog) {
        ProjectionDialog(
            baseName = waypoints.find { it.id == activeWaypointId }?.name,
            onConfirm = { name, bearing, distance ->
                val message = onProjectWaypoint(name, bearing, distance)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                showProjectionDialog = false
            },
            onDismiss = { showProjectionDialog = false }
        )
    }
}

@Composable
private fun TracksCard(
    state: GpsLocationState,
    tracks: List<Track>,
    activeTrackId: Long?,
    activeTrackProgress: Int,
    isRecording: Boolean,
    isRecordingPaused: Boolean,
    recordingPointCount: Int,
    onStartRecording: () -> Unit,
    onStopRecording: (String) -> String,
    onDefaultRecordingName: () -> String,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onReverseTrack: (Long) -> Unit,
    onDeleteTrack: (Long) -> Unit,
    onSetActiveTrack: (Long?) -> Unit
) {
    val context = LocalContext.current
    var showStopDialog by remember { mutableStateOf(false) }
    var trackName by remember { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("tracks_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Explore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Tracks (${tracks.size})",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isRecording) {
                Surface(
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .background(
                                        if (isRecordingPaused) Color(0xFF64748B) else Color(0xFFEF4444),
                                        CircleShape
                                    )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (isRecordingPaused) {
                                    "Aufzeichnung pausiert ($recordingPointCount Punkte)"
                                } else {
                                    "Aufzeichnung läuft … ($recordingPointCount Punkte)"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(
                                onClick = {
                                    if (isRecordingPaused) onResumeRecording() else onPauseRecording()
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(if (isRecordingPaused) "Fortsetzen" else "Pause")
                            }
                            Button(
                                onClick = {
                                    trackName = onDefaultRecordingName()
                                    showStopDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Beenden")
                            }
                        }
                    }
                }
            } else {
                OutlinedButton(
                    onClick = onStartRecording,
                    modifier = Modifier.fillMaxWidth().testTag("start_recording_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Navigation,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Spur aufzeichnen")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val activeTrack = tracks.find { it.id == activeTrackId }
            if (activeTrack != null) {
                ActiveTrackSummary(
                    state = state,
                    track = activeTrack,
                    trackProgress = activeTrackProgress,
                    onStop = { onSetActiveTrack(null) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (tracks.isEmpty()) {
                Text(
                    text = "Noch keine Tracks importiert. GPX-Tracks und -Routen werden als zusammenhängende Linie übernommen (nicht als einzelne Wegpunkte).",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    tracks.forEach { trk ->
                        TrackRow(
                            track = trk,
                            isActive = trk.id == activeTrackId,
                            onClick = { onSetActiveTrack(if (trk.id == activeTrackId) null else trk.id) },
                            onReverse = { onReverseTrack(trk.id) },
                            onDelete = { onDeleteTrack(trk.id) }
                        )
                    }
                }
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text("Aufzeichnung beenden") },
            text = {
                Column {
                    Text(
                        text = "Name für den Track:",
                        fontSize = 12.sp,
                        color = Color(0xFFCBD5E1)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = trackName,
                        onValueChange = { trackName = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val msg = onStopRecording(trackName)
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        showStopDialog = false
                    }
                ) {
                    Text("Speichern")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

@Composable
private fun ActiveTrackSummary(
    state: GpsLocationState,
    track: Track,
    trackProgress: Int,
    onStop: () -> Unit
) {
    val lat = state.latitude
    val lon = state.longitude

    if (lat == null || lon == null) {
        Text(
            text = "Warte auf GPS-Fix, um dem Track zu folgen …",
            fontSize = 12.sp,
            color = Color(0xFFCBD5E1)
        )
        return
    }

    val idx = trackProgress.coerceIn(0, track.points.lastIndex)
    val target = track.points[idx]
    val distance = Navigation.distanceMeters(lat, lon, target.latitude, target.longitude)
    val bearing = Navigation.initialBearingDegrees(lat, lon, target.latitude, target.longitude)
    val arrowRotation = (bearing - state.compassHeading).toFloat()
    val remaining = track.remainingMeters(idx)
    val finished = idx == track.points.lastIndex && distance < 30.0

    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF0F1E42), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Richtung zum nächsten Trackpunkt",
                    tint = if (finished) SignalGreen else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(arrowRotation)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (finished) {
                        "Ziel erreicht"
                    } else {
                        formatDistance(distance)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (finished) SignalGreen else Color.White
                )
                Text(
                    text = "Richtung ${String.format(Locale.US, "%.0f°", bearing)} (${getCompassDirection(bearing.toFloat())})",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )
                Text(
                    text = "Punkt ${idx + 1} von ${track.points.size} · Rest ${formatDistance(remaining)}",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Track folgen beenden",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onStop() }
                )
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isActive: Boolean,
    onClick: () -> Unit,
    onReverse: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        color = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            Color(0xFF0F1E42)
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("track_row_${track.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Explore,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = track.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = Color.White
                )
                Text(
                    text = "${track.points.size} Punkte · ${formatDistance(track.lengthMeters)}",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            if (isActive) {
                Text(
                    text = "Folgen",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            IconButton(
                onClick = onReverse,
                modifier = Modifier.size(28.dp).testTag("reverse_track_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = "Track umkehren",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Track löschen",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

private fun formatDistance(meters: Double): String =
    if (meters < 1000.0) {
        String.format(Locale.US, "%.0f m", meters)
    } else {
        String.format(Locale.US, "%.2f km", meters / 1000.0)
    }

@Composable
private fun ActiveTargetSummary(
    state: GpsLocationState,
    waypoint: Waypoint,
    onOpenMap: () -> Unit,
    onStop: () -> Unit
) {
    val lat = state.latitude
    val lon = state.longitude
    var showHint by remember { mutableStateOf(false) }

    if (lat == null || lon == null) {
        Text(
            text = "Warte auf GPS-Fix, um Entfernung und Richtung zu berechnen …",
            fontSize = 12.sp,
            color = Color(0xFFCBD5E1)
        )
        return
    }

    val distance = Navigation.distanceMeters(lat, lon, waypoint.latitude, waypoint.longitude)
    val bearing = Navigation.initialBearingDegrees(lat, lon, waypoint.latitude, waypoint.longitude)
    val arrowRotation = (bearing - state.compassHeading).toFloat()

    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color(0xFF0F1E42), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = "Richtung zum Ziel",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(arrowRotation)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Aktives Ziel: ${waypoint.name}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = if (distance < 1000.0) {
                        String.format(Locale.US, "%.0f m", distance)
                    } else {
                        String.format(Locale.US, "%.2f km", distance / 1000.0)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "Richtung ${String.format(Locale.US, "%.0f°", bearing)} (${getCompassDirection(bearing.toFloat())})",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )
                if (!waypoint.hint.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (showHint) {
                        Text(
                            text = "Hint: ${WaypointCodec.rot13(waypoint.hint!!)}",
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    } else {
                        Text(
                            text = "Hint anzeigen",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showHint = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "In Karte öffnen",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenMap() }
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Ziel-Navigation beenden",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.clickable { onStop() }
                )
            }
        }
    }
}

@Composable
private fun WaypointRow(
    state: GpsLocationState,
    waypoint: Waypoint,
    isActive: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onToggleFound: () -> Unit,
    onOpenMap: () -> Unit,
    onDelete: () -> Unit
) {
    val lat = state.latitude
    val lon = state.longitude

    val distance = if (lat != null && lon != null) {
        Navigation.distanceMeters(lat, lon, waypoint.latitude, waypoint.longitude)
    } else null

    val bearing = if (lat != null && lon != null) {
        Navigation.initialBearingDegrees(lat, lon, waypoint.latitude, waypoint.longitude)
    } else null

    Surface(
        color = if (isActive) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        } else {
            Color(0xFF0F1E42)
        },
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("waypoint_row_${waypoint.id}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isActive) Icons.Default.Navigation else Icons.Default.Flag,
                contentDescription = null,
                tint = if (isActive) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8),
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = waypoint.name,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (waypoint.found) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Gefunden",
                            tint = SignalGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (waypoint.cacheType != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(cacheTypeEmoji(waypoint.cacheType), fontSize = 12.sp)
                    }
                    if (waypoint.difficulty != null && waypoint.terrain != null) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format(Locale.US, "D%.1f/T%.1f", waypoint.difficulty, waypoint.terrain),
                            fontSize = 10.sp,
                            color = Color(0xFFCBD5E1)
                        )
                    }
                }
                Text(
                    text = listOfNotNull(waypoint.code, waypoint.formatted).joinToString(" · "),
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
                if (distance != null && bearing != null) {
                    Text(
                        text = if (distance < 1000.0) {
                            String.format(Locale.US, "%.0f m · %.0f°", distance, bearing)
                        } else {
                            String.format(Locale.US, "%.2f km · %.0f°", distance / 1000.0, bearing)
                        },
                        fontSize = 10.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }

            IconButton(
                onClick = onOpenMap,
                modifier = Modifier.size(28.dp).testTag("open_map_${waypoint.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = "In Karte öffnen",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }

            IconButton(
                onClick = onToggleFound,
                modifier = Modifier.size(28.dp).testTag("toggle_found_${waypoint.id}")
            ) {
                Icon(
                    imageVector = if (waypoint.found) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                    contentDescription = if (waypoint.found) "Als nicht gefunden markieren" else "Als gefunden markieren",
                    tint = if (waypoint.found) SignalGreen else Color(0xFF64748B),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onEdit,
                modifier = Modifier.size(28.dp).testTag("edit_waypoint_${waypoint.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Bearbeiten",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = "Löschen",
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun AddWaypointDialog(
    onConfirm: (name: String, code: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Position speichern") },
        text = {
            Column {
                Text(
                    text = "Name für diesen Zielpunkt:",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("z. B. Parkplatz, Zelt, Aussichtspunkt") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Code / GC-Nummer (optional):",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    placeholder = { Text("z. B. GC12345") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, code) }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun ManualWaypointDialog(
    onConfirm: (name: String, code: String?, latitude: Double, longitude: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var coords by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zielpunkt manuell anlegen") },
        text = {
            Column {
                Text(
                    text = "Name für diesen Zielpunkt:",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text("z. B. Final, Stage 1") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Code / GC-Nummer (optional):",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    placeholder = { Text("z. B. GC12345") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Koordinaten:",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = coords,
                    onValueChange = { coords = it; error = null },
                    singleLine = true,
                    isError = error != null,
                    placeholder = { Text("z. B. 52.520008, 13.404954") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (error != null) {
                    Text(
                        text = error!!,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(
                        text = "Formate: Dezimal · N 52° 31.200 E 013° 24.297 · 52° 31' 12\" N",
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = Coordinates.parse(coords)
                if (parsed == null) {
                    error = "Koordinaten nicht erkannt. Bitte Dezimal, DDM oder DMS verwenden."
                } else {
                    onConfirm(name, code, parsed.latitude, parsed.longitude)
                }
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun EditWaypointDialog(
    waypoint: Waypoint,
    onConfirm: (Waypoint) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf(waypoint.name) }
    var code by remember { mutableStateOf(waypoint.code ?: "") }
    var coords by remember {
        mutableStateOf(String.format(Locale.US, "%.6f, %.6f", waypoint.latitude, waypoint.longitude))
    }
    var hint by remember { mutableStateOf(waypoint.hint ?: "") }
    var difficulty by remember { mutableStateOf(waypoint.difficulty?.toString() ?: "") }
    var terrain by remember { mutableStateOf(waypoint.terrain?.toString() ?: "") }
    var cacheType by remember { mutableStateOf(waypoint.cacheType ?: "") }
    var typeMenuExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Zielpunkt bearbeiten") },
        text = {
            Column {
                Text("Name:", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Code / GC-Nummer (optional):", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Koordinaten:", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = coords,
                    onValueChange = { coords = it; error = null },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Hint (optional):", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    singleLine = false,
                    placeholder = { Text("z. B. ROT13-verschlüsselter Hinweis") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = difficulty,
                        onValueChange = { difficulty = it },
                        singleLine = true,
                        label = { Text("Schwierigkeit", fontSize = 11.sp) },
                        placeholder = { Text("1.0–5.0") },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = terrain,
                        onValueChange = { terrain = it },
                        singleLine = true,
                        label = { Text("Gelände", fontSize = 11.sp) },
                        placeholder = { Text("1.0–5.0") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text("Cache-Typ (optional):", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { typeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (cacheType.isBlank()) "Typ wählen" else "${cacheTypeEmoji(cacheType)} $cacheType",
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Keiner") },
                            onClick = { cacheType = ""; typeMenuExpanded = false }
                        )
                        CACHE_TYPES.forEach { type ->
                            DropdownMenuItem(
                                text = { Text("${cacheTypeEmoji(type)} $type") },
                                onClick = { cacheType = type; typeMenuExpanded = false }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                if (error != null) {
                    Text(error!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                } else {
                    Text("Formate: Dezimal · DDM · DMS", fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = Coordinates.parse(coords)
                if (parsed == null) {
                    error = "Koordinaten nicht erkannt. Bitte Dezimal, DDM oder DMS verwenden."
                } else {
                    onConfirm(
                        waypoint.copy(
                            name = name.ifBlank { "Ziel" },
                            code = code.trim().ifBlank { null },
                            latitude = parsed.latitude,
                            longitude = parsed.longitude,
                            hint = hint.trim().ifBlank { null },
                            difficulty = difficulty.toDoubleOrNull()?.takeIf { it in 1.0..5.0 },
                            terrain = terrain.toDoubleOrNull()?.takeIf { it in 1.0..5.0 },
                            cacheType = cacheType.trim().ifBlank { null }
                        )
                    )
                }
            }) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

@Composable
private fun ProjectionDialog(
    baseName: String?,
    onConfirm: (name: String, bearingDegrees: Double, distanceMeters: Double) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    var bearing by remember { mutableStateOf("") }
    var distance by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Projektion") },
        text = {
            Column {
                if (baseName == null) {
                    Text(
                        text = "Aktiviere zuerst einen Zielpunkt als Basis.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text("Basis: $baseName", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Peilung (0–360°):", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bearing,
                        onValueChange = { bearing = it; error = null },
                        singleLine = true,
                        placeholder = { Text("z. B. 245") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Entfernung (m):", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = distance,
                        onValueChange = { distance = it; error = null },
                        singleLine = true,
                        placeholder = { Text("z. B. 120") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Name für neuen Zielpunkt (optional):", fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    if (error != null) {
                        Text(error!!, fontSize = 11.sp, color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = baseName != null,
                onClick = {
                    val b = bearing.toDoubleOrNull()
                    val d = distance.toDoubleOrNull()
                    if (b == null || b !in 0.0..360.0 || d == null || d <= 0.0) {
                        error = "Ungültige Eingabe. Peilung 0–360°, Entfernung > 0."
                    } else {
                        onConfirm(name, b, d)
                    }
                }
            ) {
                Text("Berechnen")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Abbrechen")
            }
        }
    )
}

private fun openInMap(context: Context, waypoint: Waypoint) {
    val uri = Uri.parse(
        "geo:${waypoint.latitude},${waypoint.longitude}" +
            "?q=${waypoint.latitude},${waypoint.longitude}(${Uri.encode(waypoint.name)})"
    )
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, uri))
    } catch (e: Exception) {
        Toast.makeText(context, "Keine Karten-App gefunden", Toast.LENGTH_SHORT).show()
    }
}

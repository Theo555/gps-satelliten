package de.gpsradar.satellites.ui.components

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
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
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.CACHE_TYPES
import de.gpsradar.satellites.model.Cache
import de.gpsradar.satellites.model.Coordinates
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.ImportResult
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.model.WaypointCodec
import de.gpsradar.satellites.model.cacheTypeEmoji
import de.gpsradar.satellites.ui.theme.CardNavy
import de.gpsradar.satellites.ui.theme.SignalGreen
import de.gpsradar.satellites.ui.toMessage
import java.util.Locale

@Composable
fun NavigationScreen(
    state: GpsLocationState,
    waypoints: List<Waypoint>,
    caches: List<Cache>,
    freeWaypoints: List<Waypoint>,
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
    onImportWaypoints: (Uri) -> ImportResult,
    onExportGpx: (Uri) -> Int,
    onExportJson: (Uri) -> Boolean,
    hintR13Map: Map<String, Boolean>,
    onToggleHintFormat: (String) -> Unit,
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
            caches = caches,
            freeWaypoints = freeWaypoints,
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
            onExportJson = onExportJson,
            hintR13Map = hintR13Map,
            onToggleHintFormat = onToggleHintFormat
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
    caches: List<Cache>,
    freeWaypoints: List<Waypoint>,
    activeWaypointId: Long?,
    onAddCurrentPosition: (name: String, code: String?) -> Unit,
    onAddManual: (name: String, code: String?, latitude: Double, longitude: Double) -> Unit,
    onUpdateWaypoint: (Waypoint) -> Unit,
    onProjectWaypoint: (name: String, bearingDegrees: Double, distanceMeters: Double) -> String,
    onToggleFound: (Long) -> Unit,
    onDeleteWaypoint: (Long) -> Unit,
    onSetActiveWaypoint: (Long?) -> Unit,
    onImportWaypoints: (Uri) -> ImportResult,
    onExportGpx: (Uri) -> Int,
    onExportJson: (Uri) -> Boolean,
    hintR13Map: Map<String, Boolean>,
    onToggleHintFormat: (String) -> Unit
) {
    val context = LocalContext.current

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            val result = onImportWaypoints(u)
            Toast.makeText(context, result.toMessage(context), Toast.LENGTH_LONG).show()
        }
    }

    val exportGpxLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let { u ->
            val count = onExportGpx(u)
            Toast.makeText(
                context,
                if (count > 0) {
                    context.resources.getQuantityString(R.plurals.export_files, count, count)
                } else {
                    context.getString(R.string.toast_export_failed)
                },
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { u ->
            val ok = onExportJson(u)
            Toast.makeText(
                context,
                if (ok) context.getString(R.string.toast_json_exported) else context.getString(R.string.toast_export_failed),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    var showCurrentDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showProjectionDialog by remember { mutableStateOf(false) }
    var editingWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    var expandedCacheId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteWaypointId by remember { mutableStateOf<Long?>(null) }
    val pendingDeleteWaypoint = pendingDeleteWaypointId?.let { id -> waypoints.find { it.id == id } }

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
                        text = stringResource(R.string.nav_waypoints_title, waypoints.size),
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
                        contentDescription = stringResource(R.string.nav_add_waypoint_cd),
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
                    hintR13Map = hintR13Map,
                    onToggleHintFormat = onToggleHintFormat,
                    onOpenMap = { openInMap(context, active) },
                    onStop = { onSetActiveWaypoint(null) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (waypoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.nav_no_waypoints),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (caches.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.nav_section_caches, caches.size))
                        caches.forEach { cache ->
                            CacheRow(
                                state = state,
                                cache = cache,
                                expanded = expandedCacheId == cache.id,
                                activeWaypointId = activeWaypointId,
                                onToggleExpand = {
                                    expandedCacheId = if (expandedCacheId == cache.id) null else cache.id
                                },
                                onSetActiveWaypoint = onSetActiveWaypoint,
                                onEditWaypoint = { editingWaypoint = it },
                                onToggleFound = onToggleFound,
                                onOpenMap = { openInMap(context, it) },
                                onDeleteWaypoint = { id -> pendingDeleteWaypointId = id }
                            )
                        }
                    }
                    if (freeWaypoints.isNotEmpty()) {
                        SectionHeader(stringResource(R.string.nav_section_other, freeWaypoints.size))
                        freeWaypoints.forEach { wp ->
                            WaypointRow(
                                state = state,
                                waypoint = wp,
                                isActive = wp.id == activeWaypointId,
                                onClick = { onSetActiveWaypoint(wp.id) },
                                onEdit = { editingWaypoint = wp },
                                onToggleFound = { onToggleFound(wp.id) },
                                onOpenMap = { openInMap(context, wp) },
                                onDelete = { pendingDeleteWaypointId = wp.id }
                            )
                        }
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
                Text(stringResource(R.string.btn_save_position))
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
                Text(stringResource(R.string.btn_enter_coordinates))
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
                Text(stringResource(R.string.btn_projection))
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.nav_file_transfer),
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
                Text(stringResource(R.string.btn_import))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { exportGpxLauncher.launch(null) },
                    modifier = Modifier.weight(1f).testTag("export_gpx_btn")
                ) {
                    Text(stringResource(R.string.btn_export_gpx), fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { exportJsonLauncher.launch("gps_zielpunkte.json") },
                    modifier = Modifier.weight(1f).testTag("export_json_btn")
                ) {
                    Text(stringResource(R.string.btn_export_json), fontSize = 12.sp)
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

    pendingDeleteWaypoint?.let { wp ->
        AlertDialog(
            onDismissRequest = { pendingDeleteWaypointId = null },
            title = { Text(stringResource(R.string.dlg_delete_waypoint_title)) },
            text = {
                Text(stringResource(R.string.dlg_delete_waypoint_text, wp.name))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteWaypoint(wp.id)
                        pendingDeleteWaypointId = null
                    }
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteWaypointId = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
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
    var pendingDeleteTrack by remember { mutableStateOf<Track?>(null) }

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
                        text = stringResource(R.string.nav_tracks_title, tracks.size),
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
                                    pluralStringResource(
                                        R.plurals.track_paused,
                                        recordingPointCount,
                                        recordingPointCount
                                    )
                                } else {
                                    pluralStringResource(
                                        R.plurals.track_recording,
                                        recordingPointCount,
                                        recordingPointCount
                                    )
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
                                Text(if (isRecordingPaused) stringResource(R.string.btn_resume) else stringResource(R.string.btn_pause))
                            }
                            Button(
                                onClick = {
                                    trackName = onDefaultRecordingName()
                                    showStopDialog = true
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(stringResource(R.string.btn_stop))
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
                    Text(stringResource(R.string.btn_start_recording))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val activeTrack = tracks.find { it.id == activeTrackId }
            if (activeTrack != null) {
                ActiveTrackSummary(
                    state = state,
                    track = activeTrack,
                    trackProgress = activeTrackProgress,
                    onStop = { onSetActiveTrack(null) },
                    onReverse = { onReverseTrack(activeTrack.id) }
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (tracks.isEmpty()) {
                Text(
                    text = stringResource(R.string.nav_no_tracks),
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
                            onDelete = { pendingDeleteTrack = trk }
                        )
                    }
                }
            }
        }
    }

    if (showStopDialog) {
        AlertDialog(
            onDismissRequest = { showStopDialog = false },
            title = { Text(stringResource(R.string.dlg_stop_recording_title)) },
            text = {
                Column {
                    Text(
                        text = stringResource(R.string.dlg_track_name),
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
                    Text(stringResource(R.string.btn_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStopDialog = false }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }

    pendingDeleteTrack?.let { track ->
        AlertDialog(
            onDismissRequest = { pendingDeleteTrack = null },
            title = { Text(stringResource(R.string.dlg_delete_track_title)) },
            text = {
                Text(
                    pluralStringResource(
                        R.plurals.dlg_delete_track_text,
                        track.points.size,
                        track.name,
                        track.points.size
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteTrack(track.id)
                        pendingDeleteTrack = null
                    }
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteTrack = null }) {
                    Text(stringResource(R.string.btn_cancel))
                }
            }
        )
    }
}

/**
 * Zeigt die aktuelle Verfolgungsrichtung und kehrt sie beim Antippen um.
 * Anzeige und Auslöser sind bewusst dasselbe Element — ein separater
 * "Richtung umkehren"-Knopf wäre eine zweite Darstellung desselben Zustands.
 */
@Composable
private fun DirectionToggle(reversed: Boolean, onToggle: () -> Unit) {
    val accent = if (reversed) Color(0xFFFBBF24) else SignalGreen
    val reverseDescription = if (reversed) {
        stringResource(R.string.cd_reverse_to_start)
    } else {
        stringResource(R.string.cd_reverse_to_end)
    }
    Box(
        modifier = Modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(8.dp))
            .clickable(role = Role.Button, onClick = onToggle)
            .semantics {
                contentDescription = reverseDescription
            }
            .testTag("reverse_active_track_btn"),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = accent.copy(alpha = 0.16f),
            shape = RoundedCornerShape(6.dp),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.45f))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.SwapVert,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (reversed) stringResource(R.string.label_start) else stringResource(R.string.label_end),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun ActiveTrackSummary(
    state: GpsLocationState,
    track: Track,
    trackProgress: Int,
    onStop: () -> Unit,
    onReverse: () -> Unit
) {
    val lat = state.latitude
    val lon = state.longitude

    if (lat == null || lon == null) {
        Text(
            text = stringResource(R.string.nav_wait_fix_track),
            fontSize = 12.sp,
            color = Color(0xFFCBD5E1)
        )
        return
    }

    val idx = trackProgress.coerceIn(0, track.orderedPoints.lastIndex)
    val target = track.orderedPoints[idx]
    val distance = Navigation.distanceMeters(lat, lon, target.latitude, target.longitude)
    val bearing = Navigation.initialBearingDegrees(lat, lon, target.latitude, target.longitude)
    val arrowRotation = (bearing - state.compassHeading).toFloat()
    val remaining = track.remainingMeters(idx)
    val finished = idx == track.orderedPoints.lastIndex && distance < 30.0

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
                    contentDescription = stringResource(R.string.cd_dir_next_point),
                    tint = if (finished) SignalGreen else MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(arrowRotation)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = track.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.weight(1f)
                    )
                    DirectionToggle(reversed = track.reversed, onToggle = onReverse)
                }
                Text(
                    text = if (finished) {
                        stringResource(R.string.track_reached)
                    } else {
                        formatDistance(distance)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (finished) SignalGreen else Color.White
                )
                Text(
                    text = stringResource(
                        R.string.nav_direction_fmt,
                        String.format(Locale.US, "%.0f°", bearing),
                        getCompassDirection(LocalContext.current.resources, bearing.toFloat())
                    ),
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )
                Text(
                    text = stringResource(
                        R.string.nav_track_progress,
                        idx + 1,
                        track.points.size,
                        if (track.reversed) stringResource(R.string.target_start) else stringResource(R.string.target_end),
                        formatDistance(remaining)
                    ),
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )

                Spacer(modifier = Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .minimumInteractiveComponentSize()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(role = Role.Button, onClick = onStop)
                        .testTag("stop_active_track_btn"),
                    contentAlignment = Alignment.CenterStart
                ) {
                    Text(
                        text = stringResource(R.string.btn_stop_following),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    isActive: Boolean,
    onClick: () -> Unit,
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
                    text = pluralStringResource(
                        R.plurals.track_meta,
                        track.points.size,
                        track.points.size,
                        formatDistance(track.lengthMeters),
                        if (track.reversed) stringResource(R.string.label_to_start) else stringResource(R.string.label_to_end)
                    ),
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            if (isActive) {
                Text(
                    text = stringResource(R.string.label_following),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            // Die Richtungsumkehr bleibt der Karte "Aktives Ziel" vorbehalten —
            // dort ist der Zustand sichtbar. Hier nur Löschen, dafür mit voller
            // Trefferfläche und Rückfrage.
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_track_${track.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.cd_delete_track),
                    tint = Color(0xFF94A3B8),
                    modifier = Modifier.size(18.dp)
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
    hintR13Map: Map<String, Boolean>,
    onToggleHintFormat: (String) -> Unit,
    onOpenMap: () -> Unit,
    onStop: () -> Unit
) {
    val lat = state.latitude
    val lon = state.longitude
    var showHint by remember(waypoint.id) { mutableStateOf(false) }
    val hintKey = waypoint.code?.takeIf { it.isNotBlank() } ?: waypoint.name

    if (lat == null || lon == null) {
        Text(
            text = stringResource(R.string.nav_wait_fix_target),
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
                    contentDescription = stringResource(R.string.cd_dir_target),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(arrowRotation)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.nav_active_target, waypoint.name),
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
                    text = stringResource(
                        R.string.nav_direction_fmt,
                        String.format(Locale.US, "%.0f°", bearing),
                        getCompassDirection(LocalContext.current.resources, bearing.toFloat())
                    ),
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )
                if (!waypoint.hint.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (showHint) {
                        val isR13 = hintR13Map[hintKey] ?: false
                        val hintText = if (isR13) WaypointCodec.rot13(waypoint.hint!!) else waypoint.hint!!
                        Text(
                            text = stringResource(R.string.hint_prefix_fmt, hintText),
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.hint_plain),
                                fontSize = 11.sp,
                                fontWeight = if (isR13) FontWeight.Normal else FontWeight.Bold,
                                color = if (isR13) Color(0xFF94A3B8) else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable { if (isR13) onToggleHintFormat(hintKey) }
                            )
                            Text(
                                text = " · ",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Text(
                                text = stringResource(R.string.hint_rot13),
                                fontSize = 11.sp,
                                fontWeight = if (isR13) FontWeight.Bold else FontWeight.Normal,
                                color = if (isR13) MaterialTheme.colorScheme.primary else Color(0xFF94A3B8),
                                modifier = Modifier.clickable { if (!isR13) onToggleHintFormat(hintKey) }
                            )
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.btn_show_hint),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showHint = true }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.btn_open_map),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable { onOpenMap() }
                )

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.btn_stop_target_nav),
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
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = Color(0xFFCBD5E1),
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun CacheRow(
    state: GpsLocationState,
    cache: Cache,
    expanded: Boolean,
    activeWaypointId: Long?,
    onToggleExpand: () -> Unit,
    onSetActiveWaypoint: (Long) -> Unit,
    onEditWaypoint: (Waypoint) -> Unit,
    onToggleFound: (Long) -> Unit,
    onOpenMap: (Waypoint) -> Unit,
    onDeleteWaypoint: (Long) -> Unit
) {
    val primary = cache.primary
    val lat = state.latitude
    val lon = state.longitude
    val distance = if (primary != null && lat != null && lon != null) {
        Navigation.distanceMeters(lat, lon, primary.latitude, primary.longitude)
    } else null

    Column(modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = if (cache.waypoints.any { it.id == activeWaypointId }) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
            } else {
                Color(0xFF0F1E42)
            },
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggleExpand)
                .testTag("cache_row_${cache.id}")
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(cacheTypeEmoji(cache.cacheType), fontSize = 16.sp)
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = cache.name,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White
                        )
                        if (cache.archived) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.cache_archived),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFF87171)
                            )
                        }
                    }
                    val details = buildList {
                        cache.code?.let { add(it) }
                        if (cache.difficulty != null && cache.terrain != null) {
                            add(String.format(Locale.US, "D%.1f/T%.1f", cache.difficulty, cache.terrain))
                        }
                        if (cache.childCount > 1) add("${cache.childCount} Wegpunkte")
                        distance?.let { add(formatDistance(it)) }
                    }
                    Text(
                        text = details.joinToString(" · "),
                        fontSize = 10.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (expanded) "Zuklappen" else "Aufklappen",
                    tint = Color(0xFF94A3B8)
                )
            }
        }

        if (expanded) {
            Spacer(modifier = Modifier.height(4.dp))
            Column(
                modifier = Modifier.padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                cache.waypoints.forEach { wp ->
                    WaypointRow(
                        state = state,
                        waypoint = wp,
                        isActive = wp.id == activeWaypointId,
                        onClick = { onSetActiveWaypoint(wp.id) },
                        onEdit = { onEditWaypoint(wp) },
                        onToggleFound = { onToggleFound(wp.id) },
                        onOpenMap = { onOpenMap(wp) },
                        onDelete = { onDeleteWaypoint(wp.id) }
                    )
                }
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
                            contentDescription = stringResource(R.string.cd_found),
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
                    contentDescription = stringResource(R.string.btn_open_map),
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
                    contentDescription = if (waypoint.found) {
                        stringResource(R.string.cd_mark_not_found)
                    } else {
                        stringResource(R.string.cd_mark_found)
                    },
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
                    contentDescription = stringResource(R.string.cd_edit),
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
                    contentDescription = stringResource(R.string.btn_delete),
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
        title = { Text(stringResource(R.string.btn_save_position)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dlg_waypoint_name),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ph_parking)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dlg_waypoint_code),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ph_gc)) },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name, code) }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
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
    val errorCoordinates = stringResource(R.string.error_coordinates)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dlg_manual_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.dlg_waypoint_name),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ph_final_stage)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dlg_waypoint_code),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.ph_gc)) },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.dlg_coordinates),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = coords,
                    onValueChange = { coords = it; error = null },
                    singleLine = true,
                    isError = error != null,
                    placeholder = { Text(stringResource(R.string.ph_coordinates)) },
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
                        text = stringResource(R.string.dlg_formats_examples),
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
                    error = errorCoordinates
                } else {
                    onConfirm(name, code, parsed.latitude, parsed.longitude)
                }
            }) {
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
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
    val errorCoordinates = stringResource(R.string.error_coordinates)
    val defaultWaypointName = stringResource(R.string.default_waypoint_name)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dlg_edit_title)) },
        text = {
            Column {
                Text(stringResource(R.string.dlg_name), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.dlg_waypoint_code), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.dlg_coordinates), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = coords,
                    onValueChange = { coords = it; error = null },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.dlg_hint), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = hint,
                    onValueChange = { hint = it },
                    singleLine = false,
                    placeholder = { Text(stringResource(R.string.ph_hint)) },
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
                        label = { Text(stringResource(R.string.label_difficulty), fontSize = 11.sp) },
                        placeholder = { Text(stringResource(R.string.ph_rating)) },
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = terrain,
                        onValueChange = { terrain = it },
                        singleLine = true,
                        label = { Text(stringResource(R.string.label_terrain), fontSize = 11.sp) },
                        placeholder = { Text(stringResource(R.string.ph_rating)) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(stringResource(R.string.dlg_cache_type), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedButton(
                        onClick = { typeMenuExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (cacheType.isBlank()) {
                                stringResource(R.string.cache_type_choose)
                            } else {
                                "${cacheTypeEmoji(cacheType)} $cacheType"
                            },
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }
                    DropdownMenu(
                        expanded = typeMenuExpanded,
                        onDismissRequest = { typeMenuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.cache_type_none)) },
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
                    Text(stringResource(R.string.dlg_formats_short), fontSize = 10.sp, color = Color(0xFF94A3B8))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val parsed = Coordinates.parse(coords)
                if (parsed == null) {
                    error = errorCoordinates
                } else {
                    onConfirm(
                        waypoint.copy(
                            name = name.ifBlank { defaultWaypointName },
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
                Text(stringResource(R.string.btn_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
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
    val errorProjection = stringResource(R.string.error_projection)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.dlg_projection_title)) },
        text = {
            Column {
                if (baseName == null) {
                    Text(
                        text = stringResource(R.string.proj_no_base),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    Text(stringResource(R.string.proj_base, baseName), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.proj_bearing), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = bearing,
                        onValueChange = { bearing = it; error = null },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.ph_bearing)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.proj_distance), fontSize = 12.sp, color = Color(0xFFCBD5E1))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = distance,
                        onValueChange = { distance = it; error = null },
                        singleLine = true,
                        placeholder = { Text(stringResource(R.string.ph_distance)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.proj_new_name), fontSize = 12.sp, color = Color(0xFFCBD5E1))
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
                        error = errorProjection
                    } else {
                        onConfirm(name, b, d)
                    }
                }
            ) {
                Text(stringResource(R.string.btn_calculate))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.btn_cancel))
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
        Toast.makeText(context, context.getString(R.string.toast_no_map_app), Toast.LENGTH_SHORT).show()
    }
}

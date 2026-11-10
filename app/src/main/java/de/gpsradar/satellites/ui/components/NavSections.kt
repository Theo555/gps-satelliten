package de.gpsradar.satellites.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.ImportResult
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.ui.reportLines
import de.gpsradar.satellites.ui.toMessage
import kotlinx.coroutines.launch
@Composable
internal fun FilesSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    scrollState: ScrollState,
    onImportWaypoints: (Uri) -> ImportResult,
    onExportGpx: (Uri) -> Int,
    onExportJson: (Uri) -> Boolean
) {
    val context = LocalContext.current

    // Nur gesetzt, wenn beim Import Einträge übersprungen wurden – dann reicht
    // ein Toast nicht, weil Android ihn auf zwei Zeilen begrenzt.
    var importReport by remember { mutableStateOf<ImportResult?>(null) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { u ->
            val result = onImportWaypoints(u)
            if (result.skipped.isEmpty()) {
                Toast.makeText(context, result.toMessage(context), Toast.LENGTH_LONG).show()
            } else {
                importReport = result
            }
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

    NavSection(
        sectionId = SECTION_FILES,
        title = stringResource(R.string.nav_section_files),
        expanded = expanded,
        scrollState = scrollState,
        onToggle = onToggle
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedActionButton(
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
                OutlinedActionButton(
                    onClick = { exportGpxLauncher.launch(null) },
                    modifier = Modifier.weight(1f).testTag("export_gpx_btn")
                ) {
                    Text(stringResource(R.string.btn_export_gpx), fontSize = 12.sp)
                }
                OutlinedActionButton(
                    onClick = { exportJsonLauncher.launch("gps_zielpunkte.json") },
                    modifier = Modifier.weight(1f).testTag("export_json_btn")
                ) {
                    Text(stringResource(R.string.btn_export_json), fontSize = 12.sp)
                }
            }
        }
    }

    // Import mit übersprungenen Einträgen: Zusammenfassung plus Begründung.
    importReport?.let { report ->
        val lines = report.reportLines(context)
        AlertDialog(
            onDismissRequest = { importReport = null },
            title = { Text(stringResource(R.string.import_dialog_title)) },
            text = {
                Column {
                    Text(
                        text = lines.first(),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White
                    )
                    if (lines.size > 1) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = stringResource(R.string.import_dialog_skipped),
                            fontSize = 12.sp,
                            color = Color(0xFF94A3B8)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        lines.drop(1).forEach { reason ->
                            Text(
                                text = "• $reason",
                                fontSize = 13.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { importReport = null }) {
                    Text(stringResource(R.string.btn_close))
                }
            }
        )
    }
}


@Composable
internal fun TracksSection(
    tracks: List<Track>,
    activeTrackId: Long?,
    isRecording: Boolean,
    isRecordingPaused: Boolean,
    recordingPointCount: Int,
    onStartRecording: () -> Unit,
    onStopRecording: (String) -> String,
    onDefaultRecordingName: () -> String,
    onPauseRecording: () -> Unit,
    onResumeRecording: () -> Unit,
    onDiscardRecording: () -> Unit,
    onDeleteTrack: (Long) -> Unit,
    onSetActiveTrack: (Long?) -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
    scrollState: ScrollState
) {
    val context = LocalContext.current
    var showStopDialog by remember { mutableStateOf(false) }
    var trackName by remember { mutableStateOf("") }
    var pendingDeleteTrack by remember { mutableStateOf<Track?>(null) }

    // Zugeklappt bleibt eine laufende Aufzeichnung sichtbar: Punktzahl plus Stopp.
    val recordingBadge: (@Composable () -> Unit)? = if (isRecording && !expanded) {
        {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            if (isRecordingPaused) Color(0xFF64748B) else Color(0xFFEF4444),
                            CircleShape
                        )
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = pluralStringResource(
                        R.plurals.nav_recording_points,
                        recordingPointCount,
                        recordingPointCount
                    ),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
                IconButton(
                    onClick = {
                        trackName = onDefaultRecordingName()
                        showStopDialog = true
                    },
                    modifier = Modifier.size(32.dp).testTag("stop_recording_btn")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = stringResource(R.string.btn_stop),
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
    } else {
        null
    }

    NavSection(
        sectionId = SECTION_TRACKS,
        title = stringResource(R.string.nav_tracks_title, tracks.size),
        expanded = expanded,
        scrollState = scrollState,
        onToggle = onToggle,
        trailing = recordingBadge
    ) {
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
                            OutlinedActionButton(
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
                OutlinedActionButton(
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
                Button(
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
                // Fruher hiess dieser Knopf "Abbrechen", behielt die Aufzeichnung
                // aber bei. Jetzt klare Beschriftung plus echtes Verwerfen.
                Row {
                    TextButton(
                        onClick = {
                            onDiscardRecording()
                            showStopDialog = false
                        }
                    ) {
                        Text(stringResource(R.string.btn_discard))
                    }
                    TextButton(onClick = { showStopDialog = false }) {
                        Text(stringResource(R.string.btn_continue_recording))
                    }
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
                Button(
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


@Composable
internal fun TrackRow(
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

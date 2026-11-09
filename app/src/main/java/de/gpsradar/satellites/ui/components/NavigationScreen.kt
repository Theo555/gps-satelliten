package de.gpsradar.satellites.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.Cache
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.ImportResult
import de.gpsradar.satellites.model.ArrivalNotice
import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.ui.theme.CardNavy
import kotlinx.coroutines.launch
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
    arrivalNotice: ArrivalNotice?,
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
    onDiscardRecording: () -> Unit,
    onReverseTrack: (Long) -> Unit,
    expandedSection: String?,
    onExpandedSectionChange: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // ---- Dialoge und Auswahl-Zustand (bleiben beim Auf-/Zuklappen erhalten) ----
    var showCurrentDialog by remember { mutableStateOf(false) }
    var showManualDialog by remember { mutableStateOf(false) }
    var showProjectionDialog by remember { mutableStateOf(false) }
    var editingWaypoint by remember { mutableStateOf<Waypoint?>(null) }
    var expandedCacheId by remember { mutableStateOf<String?>(null) }
    var pendingDeleteWaypointId by remember { mutableStateOf<Long?>(null) }
    val pendingDeleteWaypoint = pendingDeleteWaypointId?.let { id -> waypoints.find { it.id == id } }

    val activeWaypoint = waypoints.find { it.id == activeWaypointId }
    val activeTrack = tracks.find { it.id == activeTrackId }

    // Ziel auswählen heißt: Abteilung schließen und zurück zur aktiven Karte.
    val focusActiveCard: () -> Unit = {
        onExpandedSectionChange(null)
        scope.launch { scrollState.animateScrollTo(0) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // 1. Aktive Karte – Ziel oder Track, immer offen
        ActiveTargetCard(
            state = state,
            waypoint = activeWaypoint,
            track = activeTrack,
            trackProgress = activeTrackProgress,
            arrivalNotice = arrivalNotice,
            hintR13Map = hintR13Map,
            onToggleHintFormat = onToggleHintFormat,
            onOpenMap = { activeWaypoint?.let { openInMap(context, it) } },
            onStopWaypoint = { onSetActiveWaypoint(null) },
            onStopTrack = { onSetActiveTrack(null) },
            onReverseTrack = { activeTrack?.let { onReverseTrack(it.id) } }
        )

        // 2. Symbolleiste: die drei häufigen Aktionen mit einem Tipp
        NavToolbar(
            onSavePosition = { showCurrentDialog = true },
            onEnterCoordinates = { showManualDialog = true },
            onProjection = { showProjectionDialog = true }
        )

        // 3. Vier Abteilungen, davon höchstens eine offen
        val cachesExpanded = expandedSection == SECTION_CACHES
        val waypointsExpanded = expandedSection == SECTION_WAYPOINTS

        NavSection(
            sectionId = SECTION_CACHES,
            title = stringResource(R.string.nav_section_caches, caches.size),
            expanded = cachesExpanded,
            scrollState = scrollState,
            onToggle = { onExpandedSectionChange(if (cachesExpanded) null else SECTION_CACHES) }
        ) {
            if (caches.isEmpty()) {
                Text(
                    text = stringResource(R.string.nav_no_waypoints),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            } else {
                caches.forEach { cache ->
                    CacheRow(
                        state = state,
                        cache = cache,
                        expanded = expandedCacheId == cache.id,
                        activeWaypointId = activeWaypointId,
                        onToggleExpand = {
                            expandedCacheId = if (expandedCacheId == cache.id) null else cache.id
                        },
                        onSetActiveWaypoint = { id ->
                            onSetActiveWaypoint(id)
                            focusActiveCard()
                        },
                        onEditWaypoint = { editingWaypoint = it },
                        onToggleFound = onToggleFound,
                        onOpenMap = { openInMap(context, it) },
                        onDeleteWaypoint = { id -> pendingDeleteWaypointId = id }
                    )
                }
            }
        }

        NavSection(
            sectionId = SECTION_WAYPOINTS,
            title = stringResource(R.string.nav_section_other, freeWaypoints.size),
            expanded = waypointsExpanded,
            scrollState = scrollState,
            onToggle = { onExpandedSectionChange(if (waypointsExpanded) null else SECTION_WAYPOINTS) }
        ) {
            if (freeWaypoints.isEmpty()) {
                Text(
                    text = stringResource(R.string.nav_no_waypoints),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            } else {
                freeWaypoints.forEach { wp ->
                    WaypointRow(
                        state = state,
                        waypoint = wp,
                        isActive = wp.id == activeWaypointId,
                        onClick = {
                            onSetActiveWaypoint(wp.id)
                            focusActiveCard()
                        },
                        onEdit = { editingWaypoint = wp },
                        onToggleFound = { onToggleFound(wp.id) },
                        onOpenMap = { openInMap(context, wp) },
                        onDelete = { pendingDeleteWaypointId = wp.id }
                    )
                }
            }
        }

        TracksSection(
            tracks = tracks,
            activeTrackId = activeTrackId,
            isRecording = isRecording,
            isRecordingPaused = isRecordingPaused,
            recordingPointCount = recordingPointCount,
            onStartRecording = onStartRecording,
            onStopRecording = onStopRecording,
            onDefaultRecordingName = onDefaultRecordingName,
            onPauseRecording = onPauseRecording,
            onResumeRecording = onResumeRecording,
            onDiscardRecording = onDiscardRecording,
            onDeleteTrack = onDeleteTrack,
            onSetActiveTrack = { id ->
                onSetActiveTrack(id)
                if (id != null) focusActiveCard()
            },
            expanded = expandedSection == SECTION_TRACKS,
            onToggle = {
                onExpandedSectionChange(if (expandedSection == SECTION_TRACKS) null else SECTION_TRACKS)
            },
            scrollState = scrollState
        )

        FilesSection(
            expanded = expandedSection == SECTION_FILES,
            onToggle = {
                onExpandedSectionChange(if (expandedSection == SECTION_FILES) null else SECTION_FILES)
            },
            scrollState = scrollState,
            onImportWaypoints = onImportWaypoints,
            onExportGpx = onExportGpx,
            onExportJson = onExportJson
        )

        Spacer(modifier = Modifier.height(16.dp))
    }

    // ---- Dialoge: über den Abteilungen, damit Zuklappen sie nicht verliert ----
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
            baseName = activeWaypoint?.name,
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
            text = { Text(stringResource(R.string.dlg_delete_waypoint_text, wp.name)) },
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

/** Bezeichner der vier Abteilungen im Nav-Tab. Nur einer ist offen (`null` = alle zu). */
internal const val SECTION_CACHES = "caches"
internal const val SECTION_WAYPOINTS = "waypoints"
internal const val SECTION_TRACKS = "tracks"
internal const val SECTION_FILES = "files"


/** Die drei häufigen Aktionen, direkt unter der aktiven Karte. */
@Composable
internal fun NavToolbar(
    onSavePosition: () -> Unit,
    onEnterCoordinates: () -> Unit,
    onProjection: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ToolbarButton(
            icon = Icons.Default.AddLocation,
            label = stringResource(R.string.btn_save_position),
            onClick = onSavePosition,
            modifier = Modifier.weight(1f).testTag("set_waypoint_btn")
        )
        ToolbarButton(
            icon = Icons.Default.Flag,
            label = stringResource(R.string.btn_enter_coordinates),
            onClick = onEnterCoordinates,
            modifier = Modifier.weight(1f).testTag("manual_waypoint_btn")
        )
        ToolbarButton(
            icon = Icons.Default.Explore,
            label = stringResource(R.string.nav_tool_projection),
            onClick = onProjection,
            modifier = Modifier.weight(1f).testTag("projection_btn")
        )
    }
}


/**
 * Symbol mit Beschriftung darunter.
 *
 * Alle drei Kacheln sind gleich hoch: die Zeile bekommt die Höhe der höchsten
 * Kachel, und jede füllt sie aus ([Modifier.fillMaxHeight]). Ohne das wäre die
 * Kachel mit einzeiliger Beschriftung niedriger als die mit zweizeiliger.
 */
@Composable
internal fun ToolbarButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = CardNavy,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Color(0xFF243447)),
        modifier = modifier.fillMaxHeight().clickable(role = Role.Button, onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 2,
                color = Color.White
            )
        }
    }
}


/**
 * Eine einklappbare Abteilung: Kopf mit Zähler, Inhalt nur wenn offen.
 * Beim Aufklappen scrollt der Kopf nach oben – sonst wirkt der Tipp folgenlos,
 * wenn die Abteilung knapp unter dem Bildschirmrand liegt.
 */
@Composable
internal fun NavSection(
    sectionId: String,
    title: String,
    expanded: Boolean,
    scrollState: ScrollState,
    onToggle: () -> Unit,
    trailing: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    var headerTop by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()

    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("section_$sectionId")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { headerTop = it.positionInParent().y.toInt() }
                    .clickable(role = Role.Button) {
                        onToggle()
                        if (!expanded) scope.launch { scrollState.animateScrollTo(headerTop) }
                    }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.weight(1f)
                )
                trailing?.invoke()
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = stringResource(
                        if (expanded) R.string.nav_section_close else R.string.nav_section_open
                    ),
                    tint = Color(0xFFCBD5E1),
                    modifier = Modifier.size(22.dp)
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(start = 14.dp, end = 14.dp, bottom = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    content()
                }
            }
        }
    }
}

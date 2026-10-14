package de.gpsradar.satellites.ui.components

import android.content.ClipData
import android.content.ClipboardManager
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.model.CACHE_TYPES
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.Coordinates
import de.gpsradar.satellites.model.FixType
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.model.WaypointCodec
import de.gpsradar.satellites.model.cacheTypeEmoji
import de.gpsradar.satellites.ui.theme.CardNavy
import de.gpsradar.satellites.ui.theme.SignalGreen
import de.gpsradar.satellites.ui.theme.SignalOrange
import de.gpsradar.satellites.ui.theme.SignalRed
import de.gpsradar.satellites.ui.theme.SignalYellow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PositionFixScreen(
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
    onExportJson: (Uri) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Fix Status Card
        FixStatusCard(state = state)

        // Coordinates Card
        CoordinatesCard(state = state, onCopyCoordinates = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val text = "${state.formattedCoordinatesDecimal}\n${state.formattedCoordinatesDms}\n${state.formattedCoordinatesDdm}"
            clipboard.setPrimaryClip(ClipData.newPlainText("GPS Position", text))
            Toast.makeText(context, "Koordinaten in die Zwischenablage kopiert", Toast.LENGTH_SHORT).show()
        })

        // Dynamics Grid (Altitude, Accuracy, Speed, Bearing)
        DynamicsGrid(state = state)

        // DOP Accuracy Factors
        DopCard(state = state)

        // Waypoint Navigation
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

        // Constellations Summary Card
        ConstellationsBreakdownCard(state = state)

        // Time and Sensor Info Card
        TimeAndSensorCard(state = state)

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun FixStatusCard(state: GpsLocationState) {
    val fixColor = when (state.fixType) {
        FixType.FIX_3D -> SignalGreen
        FixType.FIX_2D -> SignalYellow
        FixType.NO_FIX -> if (state.satellitesInView > 0) SignalOrange else SignalRed
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("fix_status_card")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = fixColor.copy(alpha = 0.2f),
                    shape = CircleShape,
                    modifier = Modifier.size(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.GpsFixed,
                            contentDescription = null,
                            tint = fixColor,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = state.fixType.label,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = fixColor
                    )
                    Text(
                        text = "${state.satellitesInFix} von ${state.satellitesInView} Satelliten verwendet",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFCBD5E1)
                    )
                }
            }

            // TTFF pill
            state.ttffSeconds?.let { ttff ->
                Surface(
                    color = Color(0xFF0F1E42),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "TTFF: ${ttff}s",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoordinatesCard(
    state: GpsLocationState,
    onCopyCoordinates: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("coordinates_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Geografische Position",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (state.latitude != null && state.longitude != null) {
                    IconButton(
                        onClick = onCopyCoordinates,
                        modifier = Modifier.size(32.dp).testTag("copy_coords_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Kopieren",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Decimal format
            Text(
                text = "Dezimalgrad:",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
            Text(
                text = state.formattedCoordinatesDecimal,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            // DMS format
            Text(
                text = "Grad / Minuten / Sekunden (GMS):",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
            Text(
                text = state.formattedCoordinatesDms,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // DDM format (geocaching standard)
            Text(
                text = "Grad Dezimalminuten (DDM):",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
            Text(
                text = state.formattedCoordinatesDdm,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}

@Composable
private fun DynamicsGrid(state: GpsLocationState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Altitude
        DynamicTile(
            modifier = Modifier.weight(1f),
            title = "Höhe (MSL)",
            value = state.altitudeMeters?.let { String.format(Locale.US, "%.1f m", it) } ?: "—",
            subtitle = state.altitudeMeters?.let { String.format(Locale.US, "%.0f ft", it * 3.28084) } ?: "—",
            icon = Icons.Default.Height,
            tint = Color(0xFF38BDF8)
        )

        // Accuracy
        DynamicTile(
            modifier = Modifier.weight(1f),
            title = "Genauigkeit",
            value = state.accuracyMeters?.let { String.format(Locale.US, "±%.1f m", it) } ?: "—",
            subtitle = if (state.accuracyMeters != null) {
                if (state.accuracyMeters < 3f) "Hervorragend" else if (state.accuracyMeters < 8f) "Gut" else "Mäßig"
            } else "—",
            icon = Icons.Default.Public,
            tint = SignalGreen
        )
    }

    Spacer(modifier = Modifier.height(2.dp))

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Speed
        DynamicTile(
            modifier = Modifier.weight(1f),
            title = "Tempo",
            value = state.speedKmh?.let { String.format(Locale.US, "%.1f km/h", it) } ?: "0.0 km/h",
            subtitle = state.speedKmh?.let { String.format(Locale.US, "%.1f kn", it / 1.852f) } ?: "0.0 kn",
            icon = Icons.Default.Speed,
            tint = SignalYellow
        )

        // Bearing / Compass
        val heading = state.bearingDegrees ?: state.compassHeading
        DynamicTile(
            modifier = Modifier.weight(1f),
            title = "Kurs / Peilung",
            value = String.format(Locale.US, "%.0f°", heading),
            subtitle = getCompassDirection(heading),
            icon = Icons.Default.Explore,
            tint = Color(0xFFE040FB)
        )
    }
}

@Composable
private fun DynamicTile(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(16.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = subtitle,
                fontSize = 11.sp,
                color = Color(0xFFCBD5E1)
            )
        }
    }
}

@Composable
private fun ConstellationsBreakdownCard(state: GpsLocationState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("constellations_breakdown_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Satellitensysteme (GNSS-Status)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))

            Constellation.entries.filter { it != Constellation.UNKNOWN }.forEach { constellation ->
                val inView = state.satellites.count { it.constellation == constellation }
                val inFix = state.satellites.count { it.constellation == constellation && it.usedInFix }

                if (inView > 0) {
                    Column(modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(constellation.flagEmoji, fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = constellation.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "$inFix im Fix / $inView in Sicht",
                                fontSize = 11.sp,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { if (inView > 0) inFix.toFloat() / inView.toFloat() else 0f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = constellation.badgeColor,
                            trackColor = Color(0xFF0F1E42)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimeAndSensorCard(state: GpsLocationState) {
    val dateFormat = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.GERMANY)
    val gpsTimeStr = state.timeMillis?.let { dateFormat.format(Date(it)) } ?: "—"
    val lastUpdateStr = dateFormat.format(Date(state.lastUpdateTime))

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("GPS Zeit:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Text(gpsTimeStr, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Letzte Aktualisierung:", fontSize = 11.sp, color = Color(0xFF94A3B8))
                Text(lastUpdateStr, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun DopCard(state: GpsLocationState) {
    val dop = state.dop

    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("dop_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Genauigkeitsfaktoren (DOP)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (dop.hdop == null && dop.pdop == null) {
                Text(
                    text = "Kein Fix – DOP erst bei ausreichend Satelliten verfügbar",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DopTile("HDOP (horizontal)", dop.hdop, good = 1.0, warn = 2.0, modifier = Modifier.weight(1f))
                    DopTile("VDOP (vertikal)", dop.vdop, good = 1.0, warn = 2.0, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DopTile("PDOP (Position)", dop.pdop, good = 3.0, warn = 5.0, modifier = Modifier.weight(1f))
                    DopTile("GDOP (Gesamt)", dop.gdop, good = 4.0, warn = 6.0, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DopTile(
    label: String,
    value: Double?,
    good: Double,
    warn: Double,
    modifier: Modifier = Modifier
) {
    val color = when {
        value == null -> Color.Gray
        value <= good -> SignalGreen
        value <= warn -> SignalYellow
        value <= warn * 2 -> SignalOrange
        else -> SignalRed
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F1E42)),
        shape = RoundedCornerShape(10.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 10.sp, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value?.let { String.format(Locale.US, "%.2f", it) } ?: "—",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
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
                    onOpenMap = { openInMap(context, active) }
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
private fun ActiveTargetSummary(
    state: GpsLocationState,
    waypoint: Waypoint,
    onOpenMap: () -> Unit
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

private fun getCompassDirection(deg: Float): String {
    val normalized = (deg % 360f + 360f) % 360f
    return when {
        normalized >= 337.5f || normalized < 22.5f -> "N (Nord)"
        normalized in 22.5f..67.5f -> "NO (Nordost)"
        normalized in 67.5f..112.5f -> "O (Ost)"
        normalized in 112.5f..157.5f -> "SO (Südost)"
        normalized in 157.5f..202.5f -> "S (Süd)"
        normalized in 202.5f..247.5f -> "SW (Südwest)"
        normalized in 247.5f..292.5f -> "W (West)"
        else -> "NW (Nordwest)"
    }
}

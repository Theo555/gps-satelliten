package de.gpsradar.satellites.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.widget.Toast
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Height
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.FixType
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.ui.theme.CardNavy
import de.gpsradar.satellites.ui.theme.SignalGreen
import de.gpsradar.satellites.ui.theme.SignalOrange
import de.gpsradar.satellites.ui.theme.SignalRed
import de.gpsradar.satellites.ui.theme.SignalYellow
import java.text.DateFormat
import java.util.Date
import java.util.Locale

@Composable
fun PositionFixScreen(
    state: GpsLocationState,
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
        CoordinatesCard(
            state = state,
            onCopyCoordinates = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val text = "${state.formattedCoordinatesDecimal}\n${state.formattedCoordinatesDms}\n${state.formattedCoordinatesDdm}"
                clipboard.setPrimaryClip(ClipData.newPlainText("GPS Position", text))
                Toast.makeText(context, context.getString(R.string.toast_copied), Toast.LENGTH_SHORT).show()
            },
            onSharePosition = {
                val lat = state.latitude ?: return@CoordinatesCard
                val lon = state.longitude ?: return@CoordinatesCard
                val text = context.getString(
                    R.string.share_position_text,
                    state.formattedCoordinatesDecimal,
                    lat.toString(),
                    lon.toString()
                )
                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, text)
                }
                context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.share_chooser)))
            }
        )

        // Dynamics Grid (Altitude, Accuracy, Speed, Bearing)
        DynamicsGrid(state = state)

        // DOP Accuracy Factors
        DopCard(state = state)

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
                        text = stringResource(state.fixType.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = fixColor
                    )
                    Text(
                        text = stringResource(
                            R.string.pos_used_sats,
                            state.satellitesInFix,
                            state.satellitesInView
                        ),
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
                            text = stringResource(R.string.label_ttff, ttff),
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
    onCopyCoordinates: () -> Unit,
    onSharePosition: () -> Unit
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
                        text = stringResource(R.string.pos_geo_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                if (state.latitude != null && state.longitude != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = onCopyCoordinates,
                            modifier = Modifier.size(32.dp).testTag("copy_coords_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentCopy,
                                contentDescription = stringResource(R.string.btn_copy),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = onSharePosition,
                            modifier = Modifier.size(32.dp).testTag("share_coords_btn")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = stringResource(R.string.btn_share),
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Decimal format
            Text(
                text = stringResource(R.string.coord_decimal),
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
                text = stringResource(R.string.coord_dms),
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
                text = stringResource(R.string.coord_ddm),
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
            title = stringResource(R.string.pos_altitude),
            value = state.altitudeMeters?.let { String.format(Locale.US, "%.1f m", it) } ?: "—",
            subtitle = state.altitudeMeters?.let { String.format(Locale.US, "%.0f ft", it * 3.28084) } ?: "—",
            icon = Icons.Default.Height,
            tint = Color(0xFF38BDF8)
        )

        // Accuracy
        DynamicTile(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.accuracy),
            value = state.accuracyMeters?.let { String.format(Locale.US, "±%.1f m", it) } ?: "—",
            subtitle = if (state.accuracyMeters != null) {
                if (state.accuracyMeters < 3f) {
                    stringResource(R.string.quality_excellent)
                } else if (state.accuracyMeters < 8f) {
                    stringResource(R.string.quality_good)
                } else {
                    stringResource(R.string.quality_moderate)
                }
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
            title = stringResource(R.string.pos_speed),
            value = state.speedKmh?.let { String.format(Locale.US, "%.1f km/h", it) } ?: "0.0 km/h",
            subtitle = state.speedKmh?.let { String.format(Locale.US, "%.1f kn", it / 1.852f) } ?: "0.0 kn",
            icon = Icons.Default.Speed,
            tint = SignalYellow
        )

        // Bearing / Compass
        val heading = state.bearingDegrees ?: state.compassHeading
        val res = LocalContext.current.resources
        DynamicTile(
            modifier = Modifier.weight(1f),
            title = stringResource(R.string.pos_heading),
            value = String.format(Locale.US, "%.0f°", heading),
            subtitle = getCompassDirection(res, heading),
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
                text = stringResource(R.string.pos_gnss_title),
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
                                text = stringResource(R.string.pos_fix_view, inFix, inView),
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
    // Datum/Uhrzeit in der Sprache und Schreibweise des Geräts
    val dateFormat = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.MEDIUM)
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
                Text(stringResource(R.string.pos_gps_time), fontSize = 11.sp, color = Color(0xFF94A3B8))
                Text(gpsTimeStr, fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Medium)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(stringResource(R.string.pos_last_update), fontSize = 11.sp, color = Color(0xFF94A3B8))
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
                text = stringResource(R.string.pos_dop_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (dop.hdop == null && dop.pdop == null) {
                Text(
                    text = stringResource(R.string.pos_dop_nofix),
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DopTile(stringResource(R.string.dop_hdop), dop.hdop, good = 1.0, warn = 2.0, modifier = Modifier.weight(1f))
                    DopTile(stringResource(R.string.dop_vdop), dop.vdop, good = 1.0, warn = 2.0, modifier = Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DopTile(stringResource(R.string.dop_pdop), dop.pdop, good = 3.0, warn = 5.0, modifier = Modifier.weight(1f))
                    DopTile(stringResource(R.string.dop_gdop), dop.gdop, good = 4.0, warn = 6.0, modifier = Modifier.weight(1f))
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

internal fun getCompassDirection(res: Resources, deg: Float): String {
    val normalized = (deg % 360f + 360f) % 360f
    return when {
        normalized >= 337.5f || normalized < 22.5f -> res.getString(R.string.dir_full_n)
        normalized in 22.5f..67.5f -> res.getString(R.string.dir_full_ne)
        normalized in 67.5f..112.5f -> res.getString(R.string.dir_full_e)
        normalized in 112.5f..157.5f -> res.getString(R.string.dir_full_se)
        normalized in 157.5f..202.5f -> res.getString(R.string.dir_full_s)
        normalized in 202.5f..247.5f -> res.getString(R.string.dir_full_sw)
        normalized in 247.5f..292.5f -> res.getString(R.string.dir_full_w)
        else -> res.getString(R.string.dir_full_nw)
    }
}

package de.gpsradar.satellites.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.Cache
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.model.cacheTypeEmoji
import de.gpsradar.satellites.ui.theme.SignalGreen
import java.util.Locale
@Composable
internal fun CacheRow(
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
internal fun WaypointRow(
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

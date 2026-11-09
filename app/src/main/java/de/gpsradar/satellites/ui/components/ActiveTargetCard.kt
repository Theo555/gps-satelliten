package de.gpsradar.satellites.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.ArrivalNotice
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.TrackEndAlert
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.model.WaypointCodec
import de.gpsradar.satellites.ui.theme.CardNavy
import de.gpsradar.satellites.ui.theme.SignalGreen
import java.util.Locale
/** Vorausschau entlang der Track-Linie für den Folgen-Pfeil (glättet Punktrauschen). */
private const val TRACK_COURSE_LOOKAHEAD_M = 15.0

/**
 * Ziel oder Track, das gerade verfolgt wird – immer oben und immer offen.
 * Damit bleibt der Hauptweg (Ziel wählen, loslaufen) bei einem einzigen Tipp.
 */
@Composable
internal fun ActiveTargetCard(
    state: GpsLocationState,
    waypoint: Waypoint?,
    track: Track?,
    trackProgress: Int,
    arrivalNotice: ArrivalNotice?,
    hintR13Map: Map<String, Boolean>,
    onToggleHintFormat: (String) -> Unit,
    onOpenMap: () -> Unit,
    onStopWaypoint: () -> Unit,
    onStopTrack: () -> Unit,
    onReverseTrack: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("active_target_card")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Navigation,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.nav_active_target_title),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }
            // Gleicher Hinweis wie im Radar: Beim Folgen schaut man hierher,
            // nicht auf die Nadel-Seite.
            if (state.compassSuspect) {
                Spacer(modifier = Modifier.height(6.dp))
                CompassWarningLine(state = state, tag = "nav_compass_hint")
            }
            Spacer(modifier = Modifier.height(10.dp))
            if (waypoint == null && track == null) {
                Text(
                    text = stringResource(R.string.nav_no_active_target),
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            } else {
                waypoint?.let {
                    ActiveTargetSummary(
                        state = state,
                        waypoint = it,
                        hintR13Map = hintR13Map,
                        onToggleHintFormat = onToggleHintFormat,
                        onOpenMap = onOpenMap,
                        onStop = onStopWaypoint
                    )
                }
                if (waypoint != null && track != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                track?.let {
                    ActiveTrackSummary(
                        state = state,
                        track = it,
                        trackProgress = trackProgress,
                        onStop = onStopTrack,
                        onReverse = onReverseTrack
                    )
                }
            }
            // Ankunftsmeldung: nennt den Umkreis, innerhalb dessen die App
            // „angekommen" belegen kann. Verschwindet von selbst.
            arrivalNotice?.let { notice ->
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = SignalGreen.copy(alpha = 0.16f),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("arrival_notice")
                ) {
                    Text(
                        text = stringResource(
                            R.string.nav_arrival_notice_fmt,
                            formatDistance(notice.radiusMeters)
                        ),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SignalGreen
                    )
                }
            }
        }
    }
}


/**
 * Zeigt die aktuelle Verfolgungsrichtung und kehrt sie beim Antippen um.
 * Anzeige und Auslöser sind bewusst dasselbe Element — ein separater
 * "Richtung umkehren"-Knopf wäre eine zweite Darstellung desselben Zustands.
 */
@Composable
internal fun DirectionToggle(reversed: Boolean, onToggle: () -> Unit) {
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
internal fun ActiveTrackSummary(
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
    // Pfeil entlang der Track-Linie (15 m Vorausschau), nicht auf den einzelnen
    // Punkt 25-30 m voraus: schneidet sonst Kurven (gemessen: bis 56° daneben).
    val course = track.courseDegrees(idx, TRACK_COURSE_LOOKAHEAD_M)
        ?: Navigation.initialBearingDegrees(lat, lon, target.latitude, target.longitude)
    val arrowRotation = (course - state.headingReferenceDegrees).toFloat()
    val remaining = track.remainingMeters(idx)
    // Angezeigt wird die Reststrecke bis zum Ende der Richtung – die Zahl, die
    // der Endalarm ebenfalls benutzt. Vorher stand dort der Abstand zum
    // aktuellen Track-Punkt; der Zeiger läuft aber bis zu 25 m vor
    // (TRACK_ADVANCE_THRESHOLD_M), deshalb pendelte die Zahl beim Gehen
    // zwischen rund 18 und 25 m, mit GPS-Streuung 20 bis 35 m (an einem
    // 891-Punkte-Track nachgerechnet). Der Abstand zum Zielpunkt wird addiert,
    // weil der Zeiger vor der eigenen Position liegt.
    val restMeters = remaining + distance
    // Dieselbe Grenze wie der Ankunftsalarm: mindestens 30 m, bei schlechtem
    // Empfang so weit wie die Genauigkeit. Sonst stünde „Ziel erreicht" da,
    // ohne dass der Alarm je käme.
    val finished = idx == track.orderedPoints.lastIndex &&
        distance < TrackEndAlert.radiusMeters(state.accuracyMeters)

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
                        formatDistance(restMeters)
                    },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (finished) SignalGreen else Color.White
                )
                Text(
                    text = stringResource(
                        R.string.nav_direction_fmt,
                        String.format(Locale.US, "%.0f°", course),
                        getCompassDirection(LocalContext.current.resources, course.toFloat())
                    ),
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1)
                )
                Text(
                    text = stringResource(
                        R.string.nav_track_progress,
                        idx + 1,
                        track.points.size,
                        if (track.reversed) stringResource(R.string.target_start) else stringResource(R.string.target_end)
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


internal fun formatDistance(meters: Double): String =
    if (meters < 1000.0) {
        String.format(Locale.US, "%.0f m", meters)
    } else {
        String.format(Locale.US, "%.2f km", meters / 1000.0)
    }


@Composable
internal fun ActiveTargetSummary(
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
    var showDescription by remember(waypoint.id) { mutableStateOf(false) }
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
    val arrowRotation = (bearing - state.headingReferenceDegrees).toFloat()

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

                val description = waypoint.description?.takeIf { it.isNotBlank() }
                if (description != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    if (showDescription) {
                        Text(
                            text = description,
                            fontSize = 12.sp,
                            color = Color(0xFFCBD5E1)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = stringResource(R.string.nav_hide_description),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showDescription = false }
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.nav_show_description),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.clickable { showDescription = true }
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

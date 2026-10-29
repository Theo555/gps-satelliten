package de.gpsradar.satellites.ui.components

import android.graphics.Paint
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CompassCalibration
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.SatelliteInfo
import de.gpsradar.satellites.ui.theme.AccentCyan
import de.gpsradar.satellites.ui.theme.DeepNavy
import de.gpsradar.satellites.ui.theme.RadarGrid
import de.gpsradar.satellites.ui.theme.SignalGreen
import java.util.Locale
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

@Composable
fun RadarSkyView(
    state: GpsLocationState,
    satellites: List<SatelliteInfo>,
    selectedFilter: Constellation?,
    onFilterSelected: (Constellation?) -> Unit,
    onToggleHeadingUp: () -> Unit,
    onSatelliteClick: (SatelliteInfo) -> Unit,
    targetBearing: Float? = null,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "radar_sweep")
    val sweepAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sweep_angle"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Controls Row: Orientation toggle & live heading
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onToggleHeadingUp,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.testTag("orientation_mode_btn")
            ) {
                Icon(
                    imageVector = if (state.isHeadingUp) Icons.Default.CompassCalibration else Icons.Default.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (state.isHeadingUp) {
                        stringResource(R.string.orientation_heading_up, state.compassHeading.toInt())
                    } else {
                        stringResource(R.string.orientation_north_up)
                    },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(SignalGreen, CircleShape)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.label_zenith),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Kleiner Hinweis, wenn der Kompass unzuverlässig ist (Sensorgenauigkeit
        // oder Feldbetrag außerhalb des Erwartungsbereichs).
        if (state.compassSuspect) {
            Text(
                text = stringResource(R.string.compass_unreliable),
                fontSize = 11.sp,
                color = Color(0xFFF59E0B)
            )
        }

        // Radar Canvas Box
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            val minDim = minOf(maxWidth, maxHeight)
            val rotationDeg = if (state.isHeadingUp) state.compassHeading else 0f

            RadarCanvas(
                modifier = Modifier
                    .size(minDim)
                    .aspectRatio(1f)
                    .testTag("radar_sky_canvas"),
                satellites = satellites,
                rotationDegrees = rotationDeg,
                isHeadingUp = state.isHeadingUp,
                sweepAngle = sweepAngle,
                onSatelliteClick = onSatelliteClick,
                targetBearing = targetBearing
            )
        }

        // Constellation Filter Bar
        ConstellationFilterRow(
            selectedFilter = selectedFilter,
            onFilterSelected = onFilterSelected,
            allSatellites = state.satellites
        )

        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun RadarCanvas(
    modifier: Modifier,
    satellites: List<SatelliteInfo>,
    rotationDegrees: Float,
    isHeadingUp: Boolean,
    sweepAngle: Float,
    onSatelliteClick: (SatelliteInfo) -> Unit,
    targetBearing: Float? = null
) {
    val gridColor = RadarGrid
    val textPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 28f
            color = android.graphics.Color.WHITE
            isFakeBoldText = true
        }
    }

    val labelPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 24f
            color = android.graphics.Color.argb(200, 148, 163, 184)
            isFakeBoldText = true
        }
    }

    val northPaint = remember {
        Paint().apply {
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
            textSize = 28f
            color = android.graphics.Color.argb(255, 0, 229, 255)
            isFakeBoldText = true
        }
    }

    // Map to store satellite positions for tap detection
    val satPositions = remember { ArrayList<Pair<Offset, SatelliteInfo>>() }

    Canvas(
        modifier = modifier.pointerInput(satellites, rotationDegrees) {
            detectTapGestures { tapOffset ->
                var closestSat: SatelliteInfo? = null
                var minDistance = Float.MAX_VALUE
                val touchThreshold = 36.dp.toPx()

                for ((pos, sat) in satPositions) {
                    val dx = tapOffset.x - pos.x
                    val dy = tapOffset.y - pos.y
                    val dist = sqrt(dx * dx + dy * dy)
                    if (dist < touchThreshold && dist < minDistance) {
                        minDistance = dist
                        closestSat = sat
                    }
                }

                closestSat?.let { onSatelliteClick(it) }
            }
        }
    ) {
        satPositions.clear()

        val center = Offset(size.width / 2f, size.height / 2f)
        val outerRadius = (size.minDimension / 2f) - 28.dp.toPx()

        // Background dark radar disc
        drawCircle(
            color = DeepNavy,
            radius = outerRadius,
            center = center
        )

        // Radar grid rings: Horizon (0° elev), 30° elev, 60° elev
        // r = outerRadius * (1 - elev/90)
        val r0 = outerRadius // 0 deg
        val r30 = outerRadius * (2f / 3f) // 30 deg
        val r60 = outerRadius * (1f / 3f) // 60 deg

        val dashedEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)

        // Outer ring (Horizon 0°)
        drawCircle(
            color = AccentCyan.copy(alpha = 0.8f),
            radius = r0,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // 30° elevation ring
        drawCircle(
            color = AccentCyan.copy(alpha = 0.35f),
            radius = r30,
            center = center,
            style = Stroke(width = 1.2.dp.toPx(), pathEffect = dashedEffect)
        )

        // 60° elevation ring
        drawCircle(
            color = AccentCyan.copy(alpha = 0.35f),
            radius = r60,
            center = center,
            style = Stroke(width = 1.2.dp.toPx(), pathEffect = dashedEffect)
        )

        // Center Zenith Crosshair
        drawLine(
            color = AccentCyan.copy(alpha = 0.5f),
            start = Offset(center.x - 12.dp.toPx(), center.y),
            end = Offset(center.x + 12.dp.toPx(), center.y),
            strokeWidth = 1.5.dp.toPx()
        )
        drawLine(
            color = AccentCyan.copy(alpha = 0.5f),
            start = Offset(center.x, center.y - 12.dp.toPx()),
            end = Offset(center.x, center.y + 12.dp.toPx()),
            strokeWidth = 1.5.dp.toPx()
        )

        // Draw crosshair axes with rotation
        val cardinalAngleRad = -rotationDegrees * (PI.toFloat() / 180f)

        // Primary axes (N-S and E-W)
        for (angleDeg in listOf(0f, 90f, 180f, 270f)) {
            val effAngle = (angleDeg - rotationDegrees) * (PI.toFloat() / 180f)
            val endX = center.x + r0 * sin(effAngle)
            val endY = center.y - r0 * cos(effAngle)
            drawLine(
                color = AccentCyan.copy(alpha = 0.3f),
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 1.dp.toPx()
            )
        }

        // Secondary diagonal axes (45°, 135°, 225°, 315°)
        for (angleDeg in listOf(45f, 135f, 225f, 315f)) {
            val effAngle = (angleDeg - rotationDegrees) * (PI.toFloat() / 180f)
            val endX = center.x + r0 * sin(effAngle)
            val endY = center.y - r0 * cos(effAngle)
            drawLine(
                color = AccentCyan.copy(alpha = 0.15f),
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashedEffect
            )
        }

        // Animated subtle radar sweep line
        val effSweepAngle = (sweepAngle - rotationDegrees) * (PI.toFloat() / 180f)
        val sweepEndX = center.x + r0 * sin(effSweepAngle)
        val sweepEndY = center.y - r0 * cos(effSweepAngle)
        drawLine(
            color = AccentCyan.copy(alpha = 0.4f),
            start = center,
            end = Offset(sweepEndX, sweepEndY),
            strokeWidth = 1.5.dp.toPx()
        )

        // Elevation angle text labels along North-South axis
        drawContext.canvas.nativeCanvas.drawText("60°", center.x + 8f, center.y - r60 + 10f, labelPaint)
        drawContext.canvas.nativeCanvas.drawText("30°", center.x + 8f, center.y - r30 + 10f, labelPaint)

        // Cardinal direction labels: N, E, S, W
        val cardinalLabels = listOf(
            Triple(0f, "N", northPaint),
            Triple(90f, "O", labelPaint),
            Triple(180f, "S", labelPaint),
            Triple(270f, "W", labelPaint)
        )

        val labelRadius = outerRadius + 18.dp.toPx()
        for ((deg, label, paint) in cardinalLabels) {
            val effAngle = (deg - rotationDegrees) * (PI.toFloat() / 180f)
            val lx = center.x + labelRadius * sin(effAngle)
            val ly = center.y - labelRadius * cos(effAngle) + 8f
            drawContext.canvas.nativeCanvas.drawText(label, lx, ly, paint)
        }

        // Plot Satellites
        for (sat in satellites) {
            // Elevation: 0 to 90 degrees -> r from outerRadius to 0
            val elev = sat.elevationDegrees.coerceIn(0f, 90f)
            val r = outerRadius * (1f - (elev / 90f))
            // Azimuth with rotation
            val effAngle = (sat.azimuthDegrees - rotationDegrees) * (PI.toFloat() / 180f)
            val sx = center.x + r * sin(effAngle)
            val sy = center.y - r * cos(effAngle)

            val satPos = Offset(sx, sy)
            satPositions.add(satPos to sat)

            val badgeColor = sat.constellation.badgeColor
            val satRadius = 13.dp.toPx()

            // If used in fix: draw pulsing outer ring / halo
            if (sat.usedInFix) {
                drawCircle(
                    color = SignalGreen.copy(alpha = 0.35f),
                    radius = satRadius + 4.dp.toPx(),
                    center = satPos
                )
                drawCircle(
                    color = SignalGreen,
                    radius = satRadius + 1.5.dp.toPx(),
                    center = satPos,
                    style = Stroke(width = 2.dp.toPx())
                )
            } else {
                drawCircle(
                    color = Color.White.copy(alpha = 0.25f),
                    radius = satRadius + 1.dp.toPx(),
                    center = satPos,
                    style = Stroke(width = 1.dp.toPx())
                )
            }

            // Satellite body circle
            drawCircle(
                color = if (sat.usedInFix) badgeColor else badgeColor.copy(alpha = 0.55f),
                radius = satRadius,
                center = satPos
            )

            // SVID text
            val svidText = String.format(Locale.US, "%02d", sat.svid % 100)
            textPaint.color = if (badgeColor == Color(0xFFFFD600) || badgeColor == Color(0xFF00E676)) {
                android.graphics.Color.BLACK
            } else {
                android.graphics.Color.WHITE
            }
            drawContext.canvas.nativeCanvas.drawText(svidText, sx, sy + 10f, textPaint)
        }

        // Zielrichtungs-Pfeil (aktives Ziel)
        targetBearing?.let { bearing ->
            val effAngle = (bearing - rotationDegrees) * (PI.toFloat() / 180f)
            val dirX = sin(effAngle)
            val dirY = -cos(effAngle)
            val tipX = center.x + outerRadius * dirX
            val tipY = center.y + outerRadius * dirY
            val tailX = center.x + (outerRadius * 0.55f) * dirX
            val tailY = center.y + (outerRadius * 0.55f) * dirY

            val arrowColor = Color(0xFFFFB300)
            drawLine(
                color = arrowColor,
                start = Offset(tailX, tailY),
                end = Offset(tipX, tipY),
                strokeWidth = 3.dp.toPx()
            )

            val wingLen = 12.dp.toPx()
            val wingSpread = 6.dp.toPx()
            val perpX = cos(effAngle)
            val perpY = sin(effAngle)
            drawLine(
                color = arrowColor,
                start = Offset(tipX, tipY),
                end = Offset(tipX - wingLen * dirX + wingSpread * perpX, tipY - wingLen * dirY + wingSpread * perpY),
                strokeWidth = 3.dp.toPx()
            )
            drawLine(
                color = arrowColor,
                start = Offset(tipX, tipY),
                end = Offset(tipX - wingLen * dirX - wingSpread * perpX, tipY - wingLen * dirY - wingSpread * perpY),
                strokeWidth = 3.dp.toPx()
            )
        }

        // If Heading-Up mode is active: Draw compass arrow at top
        if (isHeadingUp) {
            val arrowPath = Path().apply {
                moveTo(center.x, center.y - outerRadius - 16.dp.toPx())
                lineTo(center.x - 7.dp.toPx(), center.y - outerRadius - 6.dp.toPx())
                lineTo(center.x + 7.dp.toPx(), center.y - outerRadius - 6.dp.toPx())
                close()
            }
            drawPath(path = arrowPath, color = AccentCyan)
        }
    }
}

@Composable
private fun ConstellationFilterRow(
    selectedFilter: Constellation?,
    onFilterSelected: (Constellation?) -> Unit,
    allSatellites: List<SatelliteInfo>
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        // "Alle" filter
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterSelected(null) },
            label = { Text(stringResource(R.string.filter_all, allSatellites.size), fontSize = 12.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = Color.Black
            ),
            modifier = Modifier.testTag("filter_all")
        )

        // Filter chips for each active constellation
        Constellation.entries.filter { it != Constellation.UNKNOWN }.forEach { constellation ->
            val count = allSatellites.count { it.constellation == constellation }
            val inFixCount = allSatellites.count { it.constellation == constellation && it.usedInFix }
            if (count > 0 || selectedFilter == constellation) {
                FilterChip(
                    selected = selectedFilter == constellation,
                    onClick = {
                        onFilterSelected(if (selectedFilter == constellation) null else constellation)
                    },
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .background(constellation.badgeColor, CircleShape)
                        )
                    },
                    label = {
                        Text(
                            "${constellation.shortName} ($inFixCount/$count)",
                            fontSize = 11.sp
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = constellation.badgeColor.copy(alpha = 0.25f),
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("filter_${constellation.shortName}")
                )
            }
        }
    }
}

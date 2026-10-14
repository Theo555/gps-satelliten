package de.gpsradar.satellites.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.SignalCellularAlt
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.SatelliteInfo
import de.gpsradar.satellites.ui.theme.CardNavy
import de.gpsradar.satellites.ui.theme.SignalGreen
import de.gpsradar.satellites.ui.theme.SignalOrange
import de.gpsradar.satellites.ui.theme.SignalYellow
import java.util.Locale

@Composable
fun SignalBarChart(
    state: GpsLocationState,
    satellites: List<SatelliteInfo>,
    selectedFilter: Constellation?,
    onFilterSelected: (Constellation?) -> Unit,
    onSatelliteClick: (SatelliteInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Summary Metrics Card
        SignalSummaryCard(satellites = state.satellites)

        // Constellation Filter Chips
        SignalFilterRow(
            selectedFilter = selectedFilter,
            onFilterSelected = onFilterSelected,
            allSatellites = state.satellites
        )

        // Bar Chart Container
        Card(
            colors = CardDefaults.cardColors(containerColor = CardNavy),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("signal_chart_card")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                // Header & Legend
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SignalCellularAlt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Signalstärke C/N₀ (dB-Hz)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    // Quality color legend
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LegendDot(color = SignalGreen, label = ">35")
                        LegendDot(color = SignalYellow, label = "25-35")
                        LegendDot(color = SignalOrange, label = "<25")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (satellites.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Keine Satelliten für diesen Filter verfügbar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF94A3B8)
                        )
                    }
                } else {
                    // Scrollable vertical bars
                    val scrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .horizontalScroll(scrollState)
                            .padding(horizontal = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        satellites.forEach { sat ->
                            SatelliteSignalBar(
                                sat = sat,
                                onClick = { onSatelliteClick(sat) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SatelliteSignalBar(
    sat: SatelliteInfo,
    onClick: () -> Unit
) {
    val maxDb = 50f
    val fraction = (sat.cn0DbHz / maxDb).coerceIn(0.02f, 1f)
    val animatedFraction by animateFloatAsState(
        targetValue = fraction,
        animationSpec = tween(durationMillis = 400),
        label = "bar_height"
    )

    val barColor = when {
        sat.cn0DbHz >= 35f -> SignalGreen
        sat.cn0DbHz >= 25f -> SignalYellow
        sat.cn0DbHz > 0f -> SignalOrange
        else -> Color.Gray
    }

    Column(
        modifier = Modifier
            .width(42.dp)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp)
            .testTag("sat_bar_${sat.svid}"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom
    ) {
        // Value text on top
        Text(
            text = String.format(Locale.US, "%.0f", sat.cn0DbHz),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (sat.usedInFix) SignalGreen else Color.White
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Vertical Bar Canvas
        Box(
            modifier = Modifier
                .width(26.dp)
                .height(180.dp)
                .background(Color(0xFF0F1E42), RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            // Fill portion
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height((180 * animatedFraction).dp)
                    .background(
                        color = if (sat.usedInFix) barColor else barColor.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp)
                    ),
                contentAlignment = Alignment.TopCenter
            ) {
                if (sat.usedInFix) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Im Fix",
                        tint = Color.Black,
                        modifier = Modifier
                            .size(14.dp)
                            .padding(top = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // SVID pill
        Surface(
            color = sat.constellation.badgeColor,
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier.width(36.dp).height(20.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = String.format(Locale.US, "%02d", sat.svid % 100),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (sat.constellation == Constellation.BEIDOU || sat.constellation == Constellation.GALILEO) Color.Black else Color.White
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        // Constellation short name
        Text(
            text = sat.constellation.shortName,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFCBD5E1)
        )
    }
}

@Composable
private fun SignalSummaryCard(satellites: List<SatelliteInfo>) {
    val strongCount = satellites.count { it.cn0DbHz >= 35f }
    val mediumCount = satellites.count { it.cn0DbHz in 25f..34.99f }
    val weakCount = satellites.count { it.cn0DbHz < 25f }
    val inFixCount = satellites.count { it.usedInFix }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth().testTag("signal_summary_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            MetricPill(label = "Stark (>35)", value = "$strongCount", color = SignalGreen)
            MetricPill(label = "Mittel (25-35)", value = "$mediumCount", color = SignalYellow)
            MetricPill(label = "Schwach (<25)", value = "$weakCount", color = SignalOrange)
            MetricPill(label = "Im Fix", value = "$inFixCount", color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun MetricPill(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = color
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = Color(0xFFCBD5E1))
    }
}

@Composable
private fun SignalFilterRow(
    selectedFilter: Constellation?,
    onFilterSelected: (Constellation?) -> Unit,
    allSatellites: List<SatelliteInfo>
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FilterChip(
            selected = selectedFilter == null,
            onClick = { onFilterSelected(null) },
            label = { Text("Alle (${allSatellites.size})", fontSize = 11.sp) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primary,
                selectedLabelColor = Color.Black
            )
        )

        Constellation.entries.filter { it != Constellation.UNKNOWN }.forEach { constellation ->
            val count = allSatellites.count { it.constellation == constellation }
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
                    label = { Text("${constellation.shortName} ($count)", fontSize = 11.sp) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = constellation.badgeColor.copy(alpha = 0.25f),
                        selectedLabelColor = Color.White
                    )
                )
            }
        }
    }
}

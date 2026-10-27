package de.gpsradar.satellites.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.SatelliteInfo
import de.gpsradar.satellites.ui.SatelliteSort
import de.gpsradar.satellites.ui.theme.CardNavy
import de.gpsradar.satellites.ui.theme.SignalGreen
import de.gpsradar.satellites.ui.theme.SignalOrange
import de.gpsradar.satellites.ui.theme.SignalYellow
import java.util.Locale

@Composable
fun SatelliteListScreen(
    state: GpsLocationState,
    satellites: List<SatelliteInfo>,
    selectedFilter: Constellation?,
    selectedSort: SatelliteSort,
    onFilterSelected: (Constellation?) -> Unit,
    onSortSelected: (SatelliteSort) -> Unit,
    onSatelliteClick: (SatelliteInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var sortMenuExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Sort & Filter Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = pluralStringResource(
                    R.plurals.list_count,
                    satellites.size,
                    satellites.size,
                    state.satellitesInFix
                ),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            // Sort Menu Button
            Box {
                OutlinedButton(
                    onClick = { sortMenuExpanded = true },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("sort_menu_btn")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = selectedSort.label,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = sortMenuExpanded,
                    onDismissRequest = { sortMenuExpanded = false }
                ) {
                    SatelliteSort.entries.forEach { sortOption ->
                        DropdownMenuItem(
                            text = { Text(sortOption.label) },
                            onClick = {
                                onSortSelected(sortOption)
                                sortMenuExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Constellation Filter Chips
        val filterScrollState = rememberScrollState()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(filterScrollState),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = selectedFilter == null,
                onClick = { onFilterSelected(null) },
                label = { Text(stringResource(R.string.filter_all, state.satellites.size), fontSize = 11.sp) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = Color.Black
                )
            )

            Constellation.entries.filter { it != Constellation.UNKNOWN }.forEach { constellation ->
                val count = state.satellites.count { it.constellation == constellation }
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

        // Satellite List
        if (satellites.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.list_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .testTag("satellites_lazy_list"),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(satellites, key = { "${it.constellation.name}-${it.svid}" }) { sat ->
                    SatelliteItemCard(
                        sat = sat,
                        onClick = { onSatelliteClick(sat) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SatelliteItemCard(
    sat: SatelliteInfo,
    onClick: () -> Unit
) {
    val barColor = when {
        sat.cn0DbHz >= 35f -> SignalGreen
        sat.cn0DbHz >= 25f -> SignalYellow
        sat.cn0DbHz > 0f -> SignalOrange
        else -> Color.Gray
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("satellite_card_${sat.svid}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Header (ID, Constellation, Fix status badge)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = sat.constellation.badgeColor,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = String.format(Locale.US, "%02d", sat.svid % 100),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (sat.constellation == Constellation.BEIDOU || sat.constellation == Constellation.GALILEO) Color.Black else Color.White
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = sat.constellation.flagEmoji,
                                fontSize = 14.sp
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = sat.constellation.displayName,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            text = sat.carrierFrequencyLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFF94A3B8)
                        )
                    }
                }

                // Fix status pill
                if (sat.usedInFix) {
                    Surface(
                        color = SignalGreen.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = SignalGreen,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.badge_fix_upper),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = SignalGreen
                            )
                        }
                    }
                } else {
                    Surface(
                        color = Color(0xFF334155),
                        shape = RoundedCornerShape(20.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.badge_view_upper),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFF94A3B8),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Row 2: Signal Bar with exact dB-Hz
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_cn0),
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1),
                    modifier = Modifier.width(40.dp)
                )
                LinearProgressIndicator(
                    progress = { (sat.cn0DbHz / 50f).coerceIn(0f, 1f) },
                    modifier = Modifier
                        .weight(1f)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = barColor,
                    trackColor = Color(0xFF0F1E42),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = String.format(Locale.US, "%.1f dB", sat.cn0DbHz),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = barColor,
                    modifier = Modifier.width(55.dp)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Row 3: Angles and Ephemeris / Almanac status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = String.format(Locale.US, "Elev: %.0f°", sat.elevationDegrees),
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1)
                    )
                    Text(
                        text = String.format(Locale.US, "Azim: %.0f°", sat.azimuthDegrees),
                        fontSize = 11.sp,
                        color = Color(0xFFCBD5E1)
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    DataStatusTag(label = "EPH", active = sat.hasEphemeris)
                    DataStatusTag(label = "ALM", active = sat.hasAlmanac)
                }
            }
        }
    }
}

@Composable
private fun DataStatusTag(label: String, active: Boolean) {
    Surface(
        color = if (active) Color(0xFF0369A1).copy(alpha = 0.5f) else Color(0xFF1E293B),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = if (active) Color(0xFF7DD3FC) else Color(0xFF64748B),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

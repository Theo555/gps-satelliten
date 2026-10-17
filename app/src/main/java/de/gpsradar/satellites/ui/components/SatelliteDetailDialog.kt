package de.gpsradar.satellites.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.GnssNavMessage
import de.gpsradar.satellites.model.SatelliteInfo
import de.gpsradar.satellites.ui.theme.CardNavy
import de.gpsradar.satellites.ui.theme.DeepNavy
import de.gpsradar.satellites.ui.theme.SignalGreen
import de.gpsradar.satellites.ui.theme.SignalOrange
import de.gpsradar.satellites.ui.theme.SignalYellow
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SatelliteDetailDialog(
    satellite: SatelliteInfo,
    navigationMessages: List<GnssNavMessage>,
    supportsNavigationMessages: Boolean,
    onDismiss: () -> Unit
) {
    var showRawData by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            colors = CardDefaults.cardColors(containerColor = DeepNavy),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
                .testTag("satellite_detail_dialog")
        ) {
            Column(
                modifier = Modifier.padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = satellite.constellation.badgeColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = String.format(Locale.US, "%02d", satellite.svid % 100),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (satellite.constellation == Constellation.BEIDOU || satellite.constellation == Constellation.GALILEO) Color.Black else Color.White
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(satellite.constellation.flagEmoji, fontSize = 16.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = satellite.constellation.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Text(
                                text = "Satelliten-ID: PRN #${satellite.svid}",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Schließen",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                }

                // Signal Quality Section
                val barColor = when {
                    satellite.cn0DbHz >= 35f -> SignalGreen
                    satellite.cn0DbHz >= 25f -> SignalYellow
                    satellite.cn0DbHz > 0f -> SignalOrange
                    else -> Color.Gray
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Signalstärke (C/N₀)",
                                fontSize = 12.sp,
                                color = Color(0xFF94A3B8)
                            )
                            Surface(
                                color = barColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text(
                                    text = "${satellite.signalQuality.label} (${String.format(Locale.US, "%.1f dB-Hz", satellite.cn0DbHz)})",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = barColor,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { (satellite.cn0DbHz / 50f).coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = barColor,
                            trackColor = Color(0xFF0F1E42)
                        )
                    }
                }

                // Angles and Navigation Fix Grid
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        label = "Höhenwinkel",
                        value = String.format(Locale.US, "%.1f°", satellite.elevationDegrees),
                        hint = if (satellite.elevationDegrees > 45f) "Hoch am Himmel" else "Nahe Horizont"
                    )
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        label = "Azimut (Richtung)",
                        value = String.format(Locale.US, "%.1f°", satellite.azimuthDegrees),
                        hint = getAzimuthCompass(satellite.azimuthDegrees)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        label = "Positions-Fix",
                        value = if (satellite.usedInFix) "Im Fix verwendet" else "Nur in Sicht",
                        hint = if (satellite.usedInFix) "Trägt zur Positionsberechnung bei" else "Signal zu schwach oder keine Ephemeride"
                    )
                    DetailCard(
                        modifier = Modifier.weight(1f),
                        label = "Trägerfrequenz",
                        value = satellite.carrierFrequencyLabel,
                        hint = if (satellite.carrierFrequencyHz != null) "Multi-Frequenz fähig" else "Standardband"
                    )
                }

                // Ephemeris & Almanac Status Row
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardNavy),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = supportsNavigationMessages) { showRawData = true }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            OrbitDataIndicator(
                                name = "Ephemeriden-Daten",
                                hasData = satellite.hasEphemeris
                            )
                            OrbitDataIndicator(
                                name = "Almanach-Daten",
                                hasData = satellite.hasAlmanac
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = when {
                                !supportsNavigationMessages -> "Gerät liefert keine GNSS-Rohdaten"
                                navigationMessages.isEmpty() -> "Rohdaten ansehen ⌄"
                                else -> "Rohdaten ansehen (${navigationMessages.size}) ⌄"
                            },
                            fontSize = 10.sp,
                            color = if (supportsNavigationMessages) Color(0xFF60A5FA) else Color(0xFF64748B),
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Schließen")
                }
            }
        }
    }

    if (showRawData) {
        NavigationRawDataSheet(
            satellite = satellite,
            messages = navigationMessages,
            onDismiss = { showRawData = false }
        )
    }
}

@Composable
private fun DetailCard(
    label: String,
    value: String,
    hint: String,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 11.sp, color = Color(0xFF94A3B8))
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = Color.White)
            Text(hint, fontSize = 10.sp, color = Color(0xFFCBD5E1), maxLines = 1)
        }
    }
}

@Composable
private fun OrbitDataIndicator(name: String, hasData: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(if (hasData) SignalGreen else Color.Gray, CircleShape)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = if (hasData) "Vorhanden" else "Fehlt",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (hasData) SignalGreen else Color.Gray
            )
        }
        Text(name, fontSize = 10.sp, color = Color(0xFF94A3B8))
    }
}

private fun getAzimuthCompass(deg: Float): String {
    val normalized = (deg % 360f + 360f) % 360f
    return when {
        normalized in 337.5f..360f || normalized < 22.5f -> "Nord"
        normalized in 22.5f..67.5f -> "Nordost"
        normalized in 67.5f..112.5f -> "Ost"
        normalized in 112.5f..157.5f -> "Südost"
        normalized in 157.5f..202.5f -> "Süd"
        normalized in 202.5f..247.5f -> "Südwest"
        normalized in 247.5f..292.5f -> "West"
        else -> "Nordwest"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NavigationRawDataSheet(
    satellite: SatelliteInfo,
    messages: List<GnssNavMessage>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = DeepNavy
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = "GNSS-Rohdaten",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Text(
                text = "Navigationsnachrichten für ${satellite.identifier} (Ephemeride/Almanach)",
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(12.dp))

            if (messages.isEmpty()) {
                Text(
                    text = "Noch keine Navigationsnachricht empfangen. Die Daten treffen unregelmäßig ein (je Satellit alle paar Sekunden bis Minuten).",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(messages) { msg ->
                        NavigationMessageRow(msg)
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationMessageRow(msg: GnssNavMessage) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = msg.typeLabel,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = msg.statusLabel,
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Message ${msg.messageId} · Submessage ${msg.submessageId} · ${msg.constellation.shortName}-${msg.svid}",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = msg.dataHex,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFF60A5FA),
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

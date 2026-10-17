package de.gpsradar.satellites.ui.components

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.MultipathState
import de.gpsradar.satellites.model.RawClockSnapshot
import de.gpsradar.satellites.model.RawMeasurement
import de.gpsradar.satellites.ui.theme.CardNavy
import de.gpsradar.satellites.ui.theme.SignalGreen
import de.gpsradar.satellites.ui.theme.SignalOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MeasurementScreen(
    measurements: List<RawMeasurement>,
    clock: RawClockSnapshot?
) {
    var bandFilter by remember { mutableStateOf<String?>(null) }

    val filtered = measurements.filter { m ->
        bandFilter == null || m.bandShort == bandFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        MeasurementHeader(clock = clock, count = measurements.size)

        Spacer(modifier = Modifier.height(10.dp))

        BandFilterRow(selected = bandFilter, onSelect = { bandFilter = it })

        Spacer(modifier = Modifier.height(8.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Keine Roh-Messungen. Warte auf GNSS-Signale …",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filtered, key = { "${it.svid}-${it.bandShort}" }) { m ->
                    MeasurementRow(m)
                }
            }
        }
    }
}

@Composable
private fun MeasurementHeader(clock: RawClockSnapshot?, count: Int) {
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
                    text = "Roh-Messungen",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "$count Signalpfade",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            if (clock != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val time = SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(Date(clock.receivedAtMillis))
                Text(
                    text = "Empfangszeit $time · Uhr-Drift ${String.format(Locale.US, "%.3f", clock.driftNsPerSec)} ns/s · Leap-Second ${clock.leapSecond} s",
                    fontSize = 10.sp,
                    color = Color(0xFFCBD5E1)
                )
            }
        }
    }
}

@Composable
private fun BandFilterRow(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BandChip(label = "Alle", active = selected == null) { onSelect(null) }
        BandChip(label = "L1", active = selected == "L1") { onSelect("L1") }
        BandChip(label = "L5", active = selected == "L5") { onSelect("L5") }
    }
}

@Composable
private fun BandChip(label: String, active: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (active) Color(0xFF1E3A5F) else CardNavy,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.clickable(onClick = onClick)
    ) {
        Text(
            text = label,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
            color = if (active) Color.White else Color(0xFF94A3B8),
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun MeasurementRow(m: RawMeasurement) {
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SatelliteBadge(m)
                    Spacer(modifier = Modifier.width(6.dp))
                    BandBadge(m.bandShort)
                }
                Text(
                    text = "${String.format(Locale.US, "%.1f", m.cn0DbHz)} dB-Hz",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = cn0Color(m.cn0DbHz)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                MeasureValue(
                    label = "Pseudorange",
                    value = m.pseudorangeMeters?.let { String.format(Locale.GERMANY, "%.3f km", it / 1000.0) } ?: "–"
                )
                MeasureValue(
                    label = "Doppler",
                    value = String.format(Locale.US, "%.1f m/s", m.dopplerMetersPerSec)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MeasureValue(
                    label = "Trägerphase",
                    value = m.carrierPhaseMeters?.let { String.format(Locale.GERMANY, "%.1f m", it) } ?: "–"
                )
                MultipathLabel(m.multipath)
            }
        }
    }
}

@Composable
private fun SatelliteBadge(m: RawMeasurement) {
    val textColor = if (m.constellation == Constellation.BEIDOU || m.constellation == Constellation.GALILEO) Color.Black else Color.White
    Surface(
        color = m.constellation.badgeColor,
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = String.format(Locale.US, "%02d", m.svid % 100),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun BandBadge(short: String) {
    Surface(
        color = bandColor(short).copy(alpha = 0.2f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Text(
            text = short,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = bandColor(short),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun MeasureValue(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = Color(0xFF94A3B8))
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
    }
}

@Composable
private fun MultipathLabel(state: MultipathState) {
    val (text, color) = when (state) {
        MultipathState.DETECTED -> "⚠ Multipfad" to SignalOrange
        MultipathState.NOT_DETECTED -> "✓ kein Multipfad" to SignalGreen
        MultipathState.UNKNOWN -> "Multipfad ?" to Color(0xFF94A3B8)
    }
    Text(text = text, fontSize = 10.sp, color = color)
}

private fun cn0Color(cn0: Float): Color = when {
    cn0 >= 35f -> SignalGreen
    cn0 >= 25f -> Color(0xFFFFD54F)
    cn0 > 0f -> SignalOrange
    else -> Color(0xFF94A3B8)
}

private fun bandColor(short: String): Color = when (short) {
    "L5", "E5a" -> SignalGreen
    "L1", "E1" -> Color(0xFF60A5FA)
    "L2" -> Color(0xFFC084FC)
    "E5b" -> Color(0xFF2DD4BF)
    else -> Color(0xFF94A3B8)
}

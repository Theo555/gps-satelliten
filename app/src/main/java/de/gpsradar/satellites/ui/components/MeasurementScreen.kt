package de.gpsradar.satellites.ui.components

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import android.content.res.Resources
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.BufferStats
import de.gpsradar.satellites.model.CapturePhase
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.CsvProfile
import de.gpsradar.satellites.model.MultipathState
import de.gpsradar.satellites.model.RawClockSnapshot
import de.gpsradar.satellites.model.RawMeasurement
import de.gpsradar.satellites.ui.theme.CardNavy
import de.gpsradar.satellites.ui.theme.SignalGreen
import de.gpsradar.satellites.ui.theme.SignalOrange
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

@Composable
fun MeasurementScreen(
    measurements: List<RawMeasurement>,
    clock: RawClockSnapshot?,
    capturePhase: CapturePhase,
    bufferStats: BufferStats,
    captureEndsAtMillis: Long,
    onExportCsv: (Uri, CsvProfile) -> Boolean,
    onStartCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    onDiscardCapture: () -> Unit
) {
    var bandFilter by remember { mutableStateOf<String?>(null) }
    var showExportDialog by remember { mutableStateOf(false) }
    var showQualityDetails by remember { mutableStateOf(false) }

    val filtered = measurements.filter { m ->
        bandFilter == null || m.bandShort == bandFilter
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        CompactHeader(
            count = measurements.size,
            clock = clock,
            onOpenExport = { showExportDialog = true }
        )

        QualityLine(
            measurements = measurements,
            expanded = showQualityDetails,
            onToggle = { showQualityDetails = !showQualityDetails }
        )

        CaptureStatusLine(
            phase = capturePhase,
            stats = bufferStats,
            captureEndsAtMillis = captureEndsAtMillis,
            onOpenExport = { showExportDialog = true }
        )

        Spacer(modifier = Modifier.height(4.dp))

        BandFilterRow(selected = bandFilter, onSelect = { bandFilter = it })

        Spacer(modifier = Modifier.height(6.dp))

        if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.meas_empty),
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filtered, key = { "${it.svid}-${it.bandShort}" }) { m ->
                    MeasurementRow(m)
                }
            }
        }
    }

    if (showExportDialog) {
        ExportDialog(
            phase = capturePhase,
            stats = bufferStats,
            captureEndsAtMillis = captureEndsAtMillis,
            onExportCsv = onExportCsv,
            onStartCapture = onStartCapture,
            onCancelCapture = onCancelCapture,
            onDiscardCapture = onDiscardCapture,
            onDismiss = { showExportDialog = false }
        )
    }
}

@Composable
private fun CompactHeader(count: Int, clock: RawClockSnapshot?, onOpenExport: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.meas_title),
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = pluralStringResource(R.plurals.meas_paths, count, count),
            fontSize = 11.sp,
            color = Color(0xFF94A3B8)
        )
        if (clock != null) {
            Spacer(modifier = Modifier.width(8.dp))
            val time = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(clock.receivedAtMillis))
            Text(text = time, fontSize = 11.sp, color = Color(0xFF94A3B8))
        }
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onOpenExport, modifier = Modifier.size(34.dp)) {
            Icon(
                imageVector = Icons.Default.FileDownload,
                contentDescription = stringResource(R.string.meas_export_cd),
                tint = Color(0xFF60A5FA),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * Qualitäts-Zusammenfassung in einer Zeile; Details erst auf Antippen.
 * Hält den Messung-Tab schlank, ohne Information zu verlieren.
 */
@Composable
private fun QualityLine(
    measurements: List<RawMeasurement>,
    expanded: Boolean,
    onToggle: () -> Unit
) {
    if (measurements.isEmpty()) return

    val total = measurements.size
    val tow = measurements.count { it.towKnown }
    val msec = measurements.count { it.msecAmbiguous }
    val lock = measurements.count { it.codeLock }
    val withState = measurements.count { it.stateFlags != 0 }
    val phaseOk = measurements.count { it.phaseValid && it.carrierPhaseMeters != null }
    val uncertainties = measurements.mapNotNull { it.carrierPhaseUncertaintyMeters }

    val allGood = lock == total && tow == total && msec == 0
    val summary = if (withState == 0) {
        stringResource(R.string.meas_quality_none)
    } else {
        stringResource(
            R.string.meas_quality_fmt,
            lock, total, tow, total, msec, phaseOk, total
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggle)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = summary,
                fontSize = 10.sp,
                color = when {
                    withState == 0 -> Color(0xFF94A3B8)
                    allGood -> SignalGreen
                    else -> SignalOrange
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (expanded) "▾" else "▸",
                fontSize = 10.sp,
                color = Color(0xFF94A3B8),
                modifier = Modifier.padding(start = 4.dp)
            )
        }
        if (expanded) {
            Spacer(modifier = Modifier.height(2.dp))
            if (withState < total) {
                Text(
                    text = stringResource(R.string.meas_state_bits, withState, total) +
                        if (withState == 0) stringResource(R.string.meas_state_bits_none) else "",
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            if (uncertainties.isNotEmpty()) {
                Text(
                    text = stringResource(
                        R.string.meas_phase_unc,
                        String.format(Locale.US, "%.2f", uncertainties.average())
                    ),
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8)
                )
            }
            Text(
                text = stringResource(R.string.meas_phase_note),
                fontSize = 10.sp,
                color = Color(0xFF94A3B8)
            )
        }
    }
}

/**
 * Export und Aufzeichnung liegen in einem Dialog statt in einer Dauer-Karte –
 * so bleibt der Platz für die Messliste.
 */
@Composable
private fun ExportDialog(
    phase: CapturePhase,
    stats: BufferStats,
    captureEndsAtMillis: Long,
    onExportCsv: (Uri, CsvProfile) -> Boolean,
    onStartCapture: () -> Unit,
    onCancelCapture: () -> Unit,
    onDiscardCapture: () -> Unit,
    onDismiss: () -> Unit
) {
    var profile by remember { mutableStateOf(CsvProfile.UNIVERSAL) }
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            val ok = onExportCsv(uri, profile)
            Toast.makeText(
                context,
                if (ok) "CSV exportiert" else "CSV-Export fehlgeschlagen",
                Toast.LENGTH_SHORT
            ).show()
            onDismiss()
        }
    }

    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(phase) {
        while (phase == CapturePhase.CAPTURING) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    fun launchExport() {
        val suffix = if (profile == CsvProfile.UNIVERSAL) "universal" else "excel"
        launcher.launch("rohdaten_$suffix.csv")
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(14.dp), color = CardNavy) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.meas_csv_export),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = statusText(LocalContext.current.resources, phase, stats, captureEndsAtMillis, now),
                    fontSize = 11.sp,
                    color = if (phase == CapturePhase.CAPTURING) SignalGreen else Color(0xFF94A3B8)
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    BandChip(label = stringResource(R.string.profile_universal), active = profile == CsvProfile.UNIVERSAL) {
                        profile = CsvProfile.UNIVERSAL
                    }
                    BandChip(label = stringResource(R.string.profile_excel_de), active = profile == CsvProfile.EXCEL_DE) {
                        profile = CsvProfile.EXCEL_DE
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                when (phase) {
                    CapturePhase.CAPTURING -> {
                        OutlinedButton(onClick = onCancelCapture, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.btn_cancel_record))
                        }
                    }

                    CapturePhase.FINISHED -> {
                        Button(
                            onClick = ::launchExport,
                            enabled = stats.rows > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.btn_export)) }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(onClick = onDiscardCapture, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.btn_discard))
                        }
                    }

                    CapturePhase.IDLE -> {
                        Button(
                            onClick = ::launchExport,
                            enabled = stats.rows > 0,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text(stringResource(R.string.btn_export)) }
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedButton(onClick = onStartCapture, modifier = Modifier.fillMaxWidth()) {
                            Text(stringResource(R.string.btn_record_5min))
                        }
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.btn_close), fontSize = 12.sp)
                }
            }
        }
    }
}

/** Schmale Statuszeile, die nur während oder nach einer Aufzeichnung erscheint. */
@Composable
private fun CaptureStatusLine(
    phase: CapturePhase,
    stats: BufferStats,
    captureEndsAtMillis: Long,
    onOpenExport: () -> Unit
) {
    if (phase == CapturePhase.IDLE) return

    val context = LocalContext.current
    var now by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(phase) {
        while (phase == CapturePhase.CAPTURING) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    LaunchedEffect(phase) {
        if (phase == CapturePhase.FINISHED) {
            Toast.makeText(context, context.getString(R.string.toast_record_done), Toast.LENGTH_SHORT).show()
        }
    }

    val text = when (phase) {
        CapturePhase.CAPTURING -> {
            val remaining = ((captureEndsAtMillis - now) / 1000).coerceAtLeast(0).toInt()
            val clock = "${remaining / 60}:${(remaining % 60).toString().padStart(2, '0')}"
            "● " + stringResource(R.string.meas_recording, clock) + " · " +
                stringResource(R.string.meas_stats, stats.epochs, stats.rows)
        }

        CapturePhase.FINISHED ->
            "● " + stringResource(R.string.meas_recording_done) + " · " +
                stringResource(R.string.meas_stats, stats.epochs, stats.rows) + " – " +
                stringResource(R.string.meas_tap_export)

        CapturePhase.IDLE -> ""
    }

    Text(
        text = text,
        fontSize = 10.sp,
        color = if (phase == CapturePhase.CAPTURING) SignalGreen else Color(0xFF60A5FA),
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpenExport)
    )
}

private fun statusText(res: Resources, phase: CapturePhase, stats: BufferStats, endsAtMillis: Long, nowMillis: Long): String =
    when (phase) {
        CapturePhase.IDLE ->
            res.getString(R.string.meas_export_hint) + " · " +
                res.getString(R.string.meas_stats, stats.epochs, stats.rows)

        CapturePhase.CAPTURING -> {
            val remaining = ((endsAtMillis - nowMillis) / 1000).coerceAtLeast(0).toInt()
            val minutes = remaining / 60
            val seconds = (remaining % 60).toString().padStart(2, '0')
            res.getString(R.string.meas_recording, "$minutes:$seconds") + " · " +
                res.getString(R.string.meas_stats, stats.epochs, stats.rows)
        }

        CapturePhase.FINISHED ->
            res.getString(R.string.meas_recording_done) + " · " +
                res.getString(R.string.meas_stats, stats.epochs, stats.rows)
    }

@Composable
private fun BandFilterRow(selected: String?, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BandChip(label = stringResource(R.string.filter_all_short), active = selected == null) { onSelect(null) }
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
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)) {
            // Zeile 1: Satellit, Band, Pseudorange, C/N0
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                SatelliteBadge(m)
                Spacer(modifier = Modifier.width(6.dp))
                BandBadge(m.bandShort)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = m.pseudorangeMeters
                        ?.let { String.format(Locale.getDefault(), "%.3f km", it / 1000.0) } ?: "–",
                    fontSize = 11.sp,
                    color = Color(0xFFCBD5E1),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${String.format(Locale.US, "%.1f", m.cn0DbHz)} dB-Hz",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = cn0Color(m.cn0DbHz)
                )
            }
            // Zeile 2: Doppler, Trägerphase, Hinweis-Marker
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = buildString {
                        append("Dop ")
                        append(String.format(Locale.US, "%.1f", m.dopplerMetersPerSec))
                        append(" m/s · Ph ")
                        append(
                            m.carrierPhaseMeters?.let { phase ->
                                if (kotlin.math.abs(phase) >= 1_000_000.0) {
                                    String.format(Locale.getDefault(), "%.0f m", phase)
                                } else {
                                    String.format(Locale.getDefault(), "%.1f m", phase)
                                }
                            } ?: "–"
                        )
                    },
                    fontSize = 10.sp,
                    color = Color(0xFF94A3B8),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (m.multipath == MultipathState.DETECTED) FlagText("⚠ MP")
                if (!m.codeLock) FlagText("Lock")
                if (m.msecAmbiguous) FlagText("ms")
                if (!m.towKnown) FlagText("TOW")
            }
        }
    }
}

@Composable
private fun FlagText(text: String) {
    Text(
        text = text,
        fontSize = 9.sp,
        fontWeight = FontWeight.SemiBold,
        color = SignalOrange,
        modifier = Modifier.padding(start = 5.dp)
    )
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

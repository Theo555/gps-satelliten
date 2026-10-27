package de.gpsradar.satellites.ui.components

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.GpsOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Science
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.FixType
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.ui.theme.CardNavy
import de.gpsradar.satellites.ui.theme.SignalGreen
import de.gpsradar.satellites.ui.theme.SignalOrange
import de.gpsradar.satellites.ui.theme.SignalRed
import de.gpsradar.satellites.ui.theme.SignalYellow
import java.util.Locale

@Composable
fun PermissionAndStatusBanner(
    state: GpsLocationState,
    onRequestPermission: () -> Unit,
    onToggleDemoMode: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Missing Location Permission Card
        if (!state.hasPermission) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("permission_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.LocationOn,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.permission_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.permission_rationale),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth().testTag("grant_permission_btn")
                    ) {
                        Text(stringResource(R.string.btn_grant_permission), color = Color.White)
                    }
                }
            }
        }

        // GPS Disabled on Device Card
        if (state.hasPermission && !state.isGpsEnabled && !state.isDemoMode) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardNavy),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth().testTag("gps_disabled_card")
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.GpsOff,
                        contentDescription = null,
                        tint = SignalOrange,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.gps_disabled_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = stringResource(R.string.gps_disabled_desc),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFCBD5E1),
                            maxLines = 2
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                        },
                        modifier = Modifier.testTag("open_settings_btn")
                    ) {
                        Text(stringResource(R.string.btn_activate), fontSize = 12.sp)
                    }
                }
            }
        }

        // Demo Mode Banner – bewusst auffällig: hier sind keine echten Daten
        if (state.isDemoMode) {
            Surface(
                color = SignalOrange,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("demo_mode_banner")
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Science,
                            contentDescription = null,
                            tint = CardNavy,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.demo_banner_title),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = CardNavy
                        )
                    }
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.demo_banner_text),
                        style = MaterialTheme.typography.labelSmall,
                        color = CardNavy
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = onToggleDemoMode,
                        modifier = Modifier.height(32.dp).testTag("switch_to_real_gps_btn"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        Text(stringResource(R.string.switch_to_real), fontSize = 11.sp)
                    }
                }
            }
        }

        // Live Telemetry Summary Strip
        TelemetryQuickStrip(state = state)
    }
}

@Composable
private fun TelemetryQuickStrip(state: GpsLocationState) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth().testTag("telemetry_quick_strip")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Fix status badge
            val fixColor = when (state.fixType) {
                FixType.FIX_3D -> SignalGreen
                FixType.FIX_2D -> SignalYellow
                FixType.NO_FIX -> if (state.satellitesInView > 0) SignalOrange else SignalRed
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (state.fixType != FixType.NO_FIX) Icons.Default.GpsFixed else Icons.Default.GpsOff,
                    contentDescription = null,
                    tint = fixColor,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = state.fixType.label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = fixColor
                )
            }

            // Satellites count
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.label_sats),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${state.satellitesInFix}/${state.satellitesInView}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Average C/N0
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.label_avg_snr),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = String.format(Locale.US, "%.1f dB", state.avgCn0),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (state.avgCn0 >= 30f) SignalGreen else if (state.avgCn0 >= 20f) SignalYellow else SignalOrange
                )
            }

            // Accuracy
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.label_accuracy_short),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = state.accuracyMeters?.let { String.format(Locale.US, "±%.1fm", it) } ?: "—",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

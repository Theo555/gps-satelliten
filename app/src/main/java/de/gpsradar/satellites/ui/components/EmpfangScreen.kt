package de.gpsradar.satellites.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.CapabilityState
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.ui.EmpfangAbility
import de.gpsradar.satellites.ui.EmpfangSummary
import de.gpsradar.satellites.ui.theme.AccentCyan
import de.gpsradar.satellites.ui.theme.CardNavy

/**
 * „Empfang" — was dieses Gerät kann und welche Bahndaten es gesammelt hat.
 *
 * Bewusst flach gehalten: Zahlen und Ja/Nein, keine Bahnelemente. Wer mehr
 * will, nimmt den Export; die technischen Details bleiben dem Debug-Build
 * vorbehalten. Die Ansicht stellt nur dar, was [EmpfangSummary] liefert — ein
 * späteres Vertiefen braucht deshalb keine Änderung an der Darstellung.
 */
@Composable
fun EmpfangScreen(summary: EmpfangSummary) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp)
            .testTag("empfang_screen")
    ) {
        EmpfangCard(stringResource(R.string.empfang_section_device)) {
            if (summary.abilities.isEmpty()) {
                Zeile(
                    label = stringResource(R.string.empfang_no_abilities),
                    value = "–"
                )
            } else {
                summary.abilities.forEach { item ->
                    Zeile(
                        label = abilityLabel(item.ability),
                        value = stateText(item.state)
                    )
                }
                if (summary.hasUnclearAbilities) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = stringResource(R.string.empfang_unknown_hint),
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        EmpfangCard(stringResource(R.string.empfang_section_orbits)) {
            if (!summary.hasOrbitData) {
                Zeile(
                    label = stringResource(R.string.empfang_no_orbits),
                    value = "–"
                )
            } else {
                summary.systems.forEach { counts ->
                    val hatDaten = counts.ephemerides > 0 || counts.almanac > 0
                    Zeile(
                        label = counts.constellation.shortName,
                        value = if (hatDaten) {
                            pluralStringResource(
                                R.plurals.empfang_ephemerides,
                                counts.ephemerides,
                                counts.ephemerides
                            ) + " · " + pluralStringResource(
                                R.plurals.empfang_almanac,
                                counts.almanac,
                                counts.almanac
                            )
                        } else {
                            "–"
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        EmpfangCard(stringResource(R.string.empfang_section_corrections)) {
            Zeile(
                label = stringResource(R.string.empfang_ionosphere),
                value = systemList(summary.ionosphereSystems)
            )
            Zeile(
                label = stringResource(R.string.empfang_utc),
                value = systemList(summary.utcSystems)
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.empfang_iono_note),
                fontSize = 11.sp,
                color = Color(0xFF94A3B8)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = stringResource(R.string.empfang_hint),
            fontSize = 11.sp,
            color = Color(0xFF94A3B8)
        )
    }
}

/** Kurzname einer Systemliste; „–“, wenn keine Korrektur empfangen wurde. */
@Composable
private fun systemList(systems: List<Constellation>): String =
    if (systems.isEmpty()) "–" else systems.joinToString(" · ") { it.shortName }

@Composable
private fun abilityLabel(ability: EmpfangAbility): String = when (ability) {
    EmpfangAbility.RAW_MEASUREMENTS -> stringResource(R.string.empfang_ability_raw)
    EmpfangAbility.CARRIER_PHASE -> stringResource(R.string.empfang_ability_phase)
    EmpfangAbility.NAVIGATION_MESSAGES -> stringResource(R.string.empfang_ability_nav)
    EmpfangAbility.ANTENNA -> stringResource(R.string.empfang_ability_antenna)
}

/** Ja/Nein/Unklar wie im Messung-Tab — dieselben Texte. */
@Composable
private fun stateText(state: CapabilityState): String = when (state) {
    CapabilityState.SUPPORTED -> stringResource(R.string.meas_caps_yes)
    CapabilityState.UNSUPPORTED -> stringResource(R.string.meas_caps_no)
    CapabilityState.UNKNOWN -> stringResource(R.string.meas_caps_unknown)
}

@Composable
private fun Zeile(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 13.sp, color = Color(0xFFE2E8F0))
        Text(
            text = value,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = AccentCyan
        )
    }
}

@Composable
private fun EmpfangCard(title: String, content: @Composable () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CardNavy),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            content()
        }
    }
}

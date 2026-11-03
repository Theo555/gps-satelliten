package de.gpsradar.satellites.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.CompassIssue
import de.gpsradar.satellites.model.GpsLocationState

/** Bernstein – dieselbe Farbe wie die übrigen Kompass-Hinweise. */
val CompassWarningColor = Color(0xFFF59E0B)

/**
 * Zeile „Kompass unsicher" mit Grund und gemessenem Feldbetrag.
 *
 * Steht an zwei Stellen: im Radar, wo die Nadel ist, und seit dem 03.11. auch
 * in der Karte „Aktives Ziel" auf der Nav-Seite. Beim Folgen eines Tracks sieht
 * man auf die Nav-Seite – der Hinweis war dort unsichtbar, obwohl der Pfeil
 * genau dort benutzt wird.
 *
 * Zeigt nichts, wenn der Kompass in Ordnung ist; Aufrufer können die Zeile
 * also unbedingt einbauen und nur den Platz drumherum bedingt reservieren.
 */
@Composable
fun CompassWarningLine(
    state: GpsLocationState,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    tag: String? = null
) {
    if (!state.compassSuspect) return
    val fieldMicroTesla = state.compassFieldMicroTesla
    val hint = when {
        state.compassIssue == CompassIssue.FIELD_TOO_STRONG && fieldMicroTesla != null ->
            stringResource(R.string.compass_issue_field_strong, fieldMicroTesla)
        state.compassIssue == CompassIssue.FIELD_TOO_WEAK && fieldMicroTesla != null ->
            stringResource(R.string.compass_issue_field_weak, fieldMicroTesla)
        else -> stringResource(R.string.compass_unreliable)
    }
    Text(
        text = hint,
        fontSize = 11.sp,
        color = CompassWarningColor,
        modifier = modifier
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .then(if (tag != null) Modifier.testTag(tag) else Modifier)
            .padding(vertical = 2.dp)
    )
}

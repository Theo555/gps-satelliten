package de.gpsradar.satellites.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Umrandeter Nebenknopf in der Optik vor Material 3 1.4.
 *
 * Der Sprung auf Material 3 1.4 (10.11.2026) hat die Standardfarben von
 * [OutlinedButton] auf `onSurfaceVariant`/`outline` umgestellt: Text und Rahmen
 * wurden grau statt in der Akzentfarbe, und damit waren „Spur aufzeichnen",
 * „Importieren" oder die Filter auf den ersten Blick die unauffälligsten
 * Elemente ihres Bildschirms.
 *
 * Diese Hülle setzt die alten Werte wieder fest – Akzentfarbe als Text,
 * Gitterfarbe als Rahmen –, damit alle Nebenknöpfe gleich aussehen und die
 * Screenshots in README und fastlane gültig bleiben. Ein Ort statt 13.
 *
 * Nur die Farben sind festgelegt; Form, Innenabstand und Größe kommen
 * unverändert von Material.
 */
@Composable
internal fun OutlinedActionButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: Shape = ButtonDefaults.shape,
    contentPadding: PaddingValues = ButtonDefaults.ContentPadding,
    content: @Composable RowScope.() -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = shape,
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.primary
        ),
        border = if (enabled) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        } else {
            // So sah der Rahmen im deaktivierten Zustand vor 1.4 aus.
            BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
        },
        contentPadding = contentPadding,
        content = content
    )
}

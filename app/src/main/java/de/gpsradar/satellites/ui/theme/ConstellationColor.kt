package de.gpsradar.satellites.ui.theme

import androidx.compose.ui.graphics.Color
import de.gpsradar.satellites.model.Constellation

/**
 * Hausfarbe je Konstellation.
 *
 * Sie gehört zur Darstellung, nicht zum Modell. [Constellation] trägt nur, was
 * das System liefert (Typ-Kennung, Kürzel, Name, Flagge) und kommt ohne Compose
 * aus — so bleibt das Modell in gewöhnlichen Unit-Tests ohne UI-Abhängigkeit
 * benutzbar, und `tools/check-architecture.sh` kann das prüfen.
 *
 * Die Werte sind unverändert die, die vorher im Enum standen.
 */
val Constellation.color: Color
    get() = when (this) {
        Constellation.GPS -> Color(0xFF00B0FF)
        Constellation.GLONASS -> Color(0xFFFF5252)
        Constellation.GALILEO -> Color(0xFF00E676)
        Constellation.BEIDOU -> Color(0xFFFFD600)
        Constellation.QZSS -> Color(0xFFE040FB)
        Constellation.SBAS -> Color(0xFFFF9100)
        Constellation.UNKNOWN -> Color(0xFF90A4AE)
    }

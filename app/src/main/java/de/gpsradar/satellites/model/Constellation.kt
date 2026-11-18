package de.gpsradar.satellites.model

import androidx.core.location.GnssStatusCompat

/**
 * Die Konstellationen, die Android meldet.
 *
 * Bewusst **ohne** Farbe: Die Hausfarbe je System ist Darstellung und steht in
 * `ui/theme/ConstellationColor.kt`. Damit bleibt dieses Modell frei von Compose
 * und in gewöhnlichen Unit-Tests benutzbar (geprüft von
 * `tools/check-architecture.sh`).
 */
enum class Constellation(
    val typeId: Int,
    val displayName: String,
    val shortName: String,
    val flagEmoji: String
) {
    GPS(GnssStatusCompat.CONSTELLATION_GPS, "GPS (USA)", "GPS", "🇺🇸"),
    GLONASS(GnssStatusCompat.CONSTELLATION_GLONASS, "GLONASS (RU)", "GLO", "🇷🇺"),
    GALILEO(GnssStatusCompat.CONSTELLATION_GALILEO, "Galileo (EU)", "GAL", "🇪🇺"),
    BEIDOU(GnssStatusCompat.CONSTELLATION_BEIDOU, "BeiDou (CN)", "BDS", "🇨🇳"),
    QZSS(GnssStatusCompat.CONSTELLATION_QZSS, "QZSS (JP)", "QZS", "🇯🇵"),
    SBAS(GnssStatusCompat.CONSTELLATION_SBAS, "SBAS (WAAS/EGNOS)", "SBAS", "🛰️"),
    UNKNOWN(GnssStatusCompat.CONSTELLATION_UNKNOWN, "Unbekannt", "UNK", "🌐");

    companion object {
        fun fromTypeId(typeId: Int): Constellation {
            return entries.find { it.typeId == typeId } ?: UNKNOWN
        }
    }
}

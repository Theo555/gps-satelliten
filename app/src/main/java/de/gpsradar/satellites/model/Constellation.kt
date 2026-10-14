package de.gpsradar.satellites.model

import androidx.compose.ui.graphics.Color
import androidx.core.location.GnssStatusCompat

enum class Constellation(
    val typeId: Int,
    val displayName: String,
    val shortName: String,
    val badgeColor: Color,
    val flagEmoji: String
) {
    GPS(GnssStatusCompat.CONSTELLATION_GPS, "GPS (USA)", "GPS", Color(0xFF00B0FF), "🇺🇸"),
    GLONASS(GnssStatusCompat.CONSTELLATION_GLONASS, "GLONASS (RU)", "GLO", Color(0xFFFF5252), "🇷🇺"),
    GALILEO(GnssStatusCompat.CONSTELLATION_GALILEO, "Galileo (EU)", "GAL", Color(0xFF00E676), "🇪🇺"),
    BEIDOU(GnssStatusCompat.CONSTELLATION_BEIDOU, "BeiDou (CN)", "BDS", Color(0xFFFFD600), "🇨🇳"),
    QZSS(GnssStatusCompat.CONSTELLATION_QZSS, "QZSS (JP)", "QZS", Color(0xFFE040FB), "🇯🇵"),
    SBAS(GnssStatusCompat.CONSTELLATION_SBAS, "SBAS (WAAS/EGNOS)", "SBAS", Color(0xFFFF9100), "🛰️"),
    UNKNOWN(GnssStatusCompat.CONSTELLATION_UNKNOWN, "Unbekannt", "UNK", Color(0xFF90A4AE), "🌐");

    companion object {
        fun fromTypeId(typeId: Int): Constellation {
            return entries.find { it.typeId == typeId } ?: UNKNOWN
        }
    }
}

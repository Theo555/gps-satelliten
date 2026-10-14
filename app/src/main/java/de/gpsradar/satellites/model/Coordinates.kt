package de.gpsradar.satellites.model

import java.util.Locale
import kotlin.math.abs

/**
 * Einheitliche Koordinaten-Formatierung und -Parsing.
 *
 * Unterstützte Eingabeformate beim Parsen:
 *  - Dezimal:      "52.520008, 13.404954" oder "52.520008 13.404954"
 *  - DDM:          "N 52° 31.200 E 013° 24.297" oder "52° 31.200' N, 13° 24.297' E"
 *  - DMS:          "52° 31' 12.0\" N 13° 24' 17.8\" E"
 * Negative Werte und Richtungsbuchstaben (N/S/E/W/O) werden unterstützt.
 */
object Coordinates {

    data class LatLon(val latitude: Double, val longitude: Double)

    // ---------- Formatierung ----------

    fun formatDecimal(lat: Double, lon: Double): String {
        val latDir = if (lat >= 0) "N" else "S"
        val lonDir = if (lon >= 0) "E" else "W"
        return String.format(Locale.US, "%.6f° %s, %.6f° %s", abs(lat), latDir, abs(lon), lonDir)
    }

    fun formatDms(lat: Double, lon: Double): String =
        "${toDms(lat, isLatitude = true)}, ${toDms(lon, isLatitude = false)}"

    fun formatDdm(lat: Double, lon: Double): String =
        "${toDdm(lat, isLatitude = true)}, ${toDdm(lon, isLatitude = false)}"

    private fun toDms(value: Double, isLatitude: Boolean): String {
        val direction = if (isLatitude) {
            if (value >= 0) "N" else "S"
        } else {
            if (value >= 0) "E" else "W"
        }
        val a = abs(value)
        val degrees = a.toInt()
        val minutes = ((a - degrees) * 60).toInt()
        val seconds = (a - degrees - minutes / 60.0) * 3600.0
        return String.format(Locale.GERMANY, "%d° %02d' %04.1f\" %s", degrees, minutes, seconds, direction)
    }

    private fun toDdm(value: Double, isLatitude: Boolean): String {
        val direction = if (isLatitude) {
            if (value >= 0) "N" else "S"
        } else {
            if (value >= 0) "E" else "W"
        }
        val a = abs(value)
        val degrees = a.toInt()
        val minutes = (a - degrees) * 60.0
        return String.format(Locale.US, "%d° %06.3f' %s", degrees, minutes, direction)
    }

    // ---------- Parsen ----------

    private val DMS_REGEX = Regex(
        """([NS])?\s*(\d{1,3})\s*°\s*(\d{1,2})\s*['′]\s*(\d{1,2}(?:\.\d+)?)\s*["″]?\s*([NS])?\s*[,;\s]+\s*([EWO])?\s*(\d{1,3})\s*°\s*(\d{1,2})\s*['′]\s*(\d{1,2}(?:\.\d+)?)\s*["″]?\s*([EWO])?""",
        RegexOption.IGNORE_CASE
    )

    private val DDM_REGEX = Regex(
        """([NS])?\s*(\d{1,3})\s*°\s*(\d{1,2}(?:\.\d+)?)\s*['′]?\s*([NS])?\s*[,;\s]+\s*([EWO])?\s*(\d{1,3})\s*°\s*(\d{1,2}(?:\.\d+)?)\s*['′]?\s*([EWO])?""",
        RegexOption.IGNORE_CASE
    )

    private val DECIMAL_REGEX = Regex(
        """([NS])?\s*(-?\d{1,3}(?:\.\d+)?)\s*°?\s*([NS])?\s*[,;\s]+\s*([EWO])?\s*(-?\d{1,3}(?:\.\d+)?)\s*°?\s*([EWO])?""",
        RegexOption.IGNORE_CASE
    )

    fun parse(input: String): LatLon? {
        if (input.isBlank()) return null
        val s = normalize(input)
        if (s.isEmpty()) return null

        DMS_REGEX.find(s)?.let { m ->
            val g = m.groupValues
            val lat = g[2].toDouble() + g[3].toDouble() / 60.0 + g[4].toDouble() / 3600.0
            val lon = g[7].toDouble() + g[8].toDouble() / 60.0 + g[9].toDouble() / 3600.0
            val latDir = g[1].ifBlank { null } ?: g[5].ifBlank { null }
            val lonDir = g[6].ifBlank { null } ?: g[10].ifBlank { null }
            return build(signed(lat, latDir, isLatitude = true), signed(lon, lonDir, isLatitude = false))
        }

        DDM_REGEX.find(s)?.let { m ->
            val g = m.groupValues
            val lat = g[2].toDouble() + g[3].toDouble() / 60.0
            val lon = g[6].toDouble() + g[7].toDouble() / 60.0
            val latDir = g[1].ifBlank { null } ?: g[4].ifBlank { null }
            val lonDir = g[5].ifBlank { null } ?: g[8].ifBlank { null }
            return build(signed(lat, latDir, isLatitude = true), signed(lon, lonDir, isLatitude = false))
        }

        DECIMAL_REGEX.find(s)?.let { m ->
            val g = m.groupValues
            val latDir = g[1].ifBlank { null } ?: g[3].ifBlank { null }
            val lonDir = g[4].ifBlank { null } ?: g[6].ifBlank { null }
            return build(
                signed(g[2].toDouble(), latDir, isLatitude = true),
                signed(g[5].toDouble(), lonDir, isLatitude = false)
            )
        }

        return null
    }

    private fun normalize(input: String): String =
        input.trim()
            .uppercase(Locale.US)
            .replace(';', ',')
            .replace('\u00B0', '°')
            .replace('\u2032', '\'')
            .replace('\u2019', '\'')
            .replace('\u2033', '"')
            .replace('\u201D', '"')

    private fun signed(value: Double, dir: String?, isLatitude: Boolean): Double {
        val southOrWest = if (isLatitude) dir == "S" else (dir == "W" || dir == "O")
        return when {
            southOrWest -> -abs(value)
            dir == null -> value
            else -> abs(value)
        }
    }

    private fun build(lat: Double, lon: Double): LatLon? =
        if (lat in -90.0..90.0 && lon in -180.0..180.0) LatLon(lat, lon) else null
}

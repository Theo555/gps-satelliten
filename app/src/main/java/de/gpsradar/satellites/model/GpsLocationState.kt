package de.gpsradar.satellites.model

import java.util.Locale

enum class FixType(val label: String) {
    NO_FIX("Kein Fix"),
    FIX_2D("2D Fix"),
    FIX_3D("3D Fix")
}

data class GpsLocationState(
    val hasPermission: Boolean = false,
    val isGpsEnabled: Boolean = true,
    val isDemoMode: Boolean = false,
    val fixType: FixType = FixType.NO_FIX,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val altitudeMeters: Double? = null,
    val speedKmh: Float? = null,
    val bearingDegrees: Float? = null,
    val accuracyMeters: Float? = null,
    val timeMillis: Long? = null,
    val satellites: List<SatelliteInfo> = emptyList(),
    val compassHeading: Float = 0f, // 0..360 from device orientation sensor
    val isHeadingUp: Boolean = false, // false = North-Up, true = Heading-Up
    val ttffSeconds: Int? = null, // Time To First Fix in seconds
    val lastUpdateTime: Long = System.currentTimeMillis()
) {
    val satellitesInView: Int
        get() = satellites.size

    val satellitesInFix: Int
        get() = satellites.count { it.usedInFix }

    val avgCn0: Float
        get() = if (satellites.isNotEmpty()) {
            val active = satellites.filter { it.cn0DbHz > 0 }
            if (active.isNotEmpty()) active.map { it.cn0DbHz }.average().toFloat() else 0f
        } else 0f

    val maxCn0: Float
        get() = satellites.maxOfOrNull { it.cn0DbHz } ?: 0f

    val dop: DopCalculator.DopValues
        get() = DopCalculator.calculate(satellites)

    val formattedCoordinatesDecimal: String
        get() {
            if (latitude == null || longitude == null) return "— , —"
            return Coordinates.formatDecimal(latitude, longitude)
        }

    val formattedCoordinatesDms: String
        get() {
            if (latitude == null || longitude == null) return "—"
            return Coordinates.formatDms(latitude, longitude)
        }

    val formattedCoordinatesDdm: String
        get() {
            if (latitude == null || longitude == null) return "—"
            return Coordinates.formatDdm(latitude, longitude)
        }

    private fun toDms(value: Double, isLatitude: Boolean): String {
        val direction = if (isLatitude) {
            if (value >= 0) "N" else "S"
        } else {
            if (value >= 0) "O" else "W"
        }
        val abs = kotlin.math.abs(value)
        val degrees = abs.toInt()
        val minutes = ((abs - degrees) * 60).toInt()
        val seconds = ((abs - degrees - minutes / 60.0) * 3600.0)
        return String.format(Locale.GERMANY, "%d° %02d\' %04.1f\" %s", degrees, minutes, seconds, direction)
    }
}

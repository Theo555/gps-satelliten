package de.gpsradar.satellites.model

import java.util.Locale

data class SatelliteInfo(
    val svid: Int,
    val constellation: Constellation,
    val cn0DbHz: Float,
    val elevationDegrees: Float,
    val azimuthDegrees: Float,
    val hasEphemeris: Boolean = false,
    val hasAlmanac: Boolean = false,
    val usedInFix: Boolean = false,
    val carrierFrequencyHz: Float? = null
) {
    val identifier: String
        get() = "${constellation.shortName}-$svid"

    val carrierFrequencyLabel: String
        get() {
            if (carrierFrequencyHz == null || carrierFrequencyHz <= 0f) return "L1 / Standard"
            val mhz = carrierFrequencyHz / 1_000_000.0f
            return when {
                mhz in 1574.0f..1577.0f -> if (constellation == Constellation.GALILEO) "E1 (1575.42 MHz)" else "L1 (1575.42 MHz)"
                mhz in 1175.0f..1178.0f -> if (constellation == Constellation.GALILEO) "E5a (1176.45 MHz)" else "L5 (1176.45 MHz)"
                mhz in 1226.0f..1229.0f -> "L2 (1227.60 MHz)"
                mhz in 1559.0f..1610.0f -> "G1 / B1 (1561-1602 MHz)"
                mhz in 1206.0f..1208.0f -> "E5b (1207.14 MHz)"
                else -> String.format(Locale.US, "%.2f MHz", mhz)
            }
        }

    val signalQuality: SignalQuality
        get() = when {
            cn0DbHz >= 35f -> SignalQuality.STRONG
            cn0DbHz >= 24f -> SignalQuality.MEDIUM
            cn0DbHz > 0f -> SignalQuality.WEAK
            else -> SignalQuality.NONE
        }
}

enum class SignalQuality(val label: String) {
    STRONG("Stark"),
    MEDIUM("Mittel"),
    WEAK("Schwach"),
    NONE("Kein Signal")
}

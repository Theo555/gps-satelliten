package de.gpsradar.satellites.model

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
        get() = gnssBandLabel(constellation, carrierFrequencyHz)

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

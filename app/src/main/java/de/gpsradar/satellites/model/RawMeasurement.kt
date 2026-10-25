package de.gpsradar.satellites.model

import java.util.Locale

/** Multipfad-Zustand einer GNSS-Messung. */
enum class MultipathState(val label: String) {
    UNKNOWN("Unbekannt"),
    DETECTED("Erkannt"),
    NOT_DETECTED("Keine")
}

/**
 * Eine Roh-Messung (ein Signalpfad = Satellit × Frequenzband).
 *
 * Nur primitive Felder → stabil für Compose.
 */
data class RawMeasurement(
    val svid: Int,
    val constellation: Constellation,
    val carrierFrequencyHz: Float,
    val bandLabel: String,
    val bandShort: String,
    val cn0DbHz: Float,
    val pseudorangeMeters: Double?,
    val dopplerMetersPerSec: Double,
    val carrierPhaseMeters: Double?,
    val phaseValid: Boolean,
    val multipath: MultipathState,
    val receivedAtMillis: Long,
    // --- Qualitätsmerkmale der Messung ---
    /** Rohwert von GnssMeasurement.getState(); 0 = nicht geliefert. */
    val stateFlags: Int = 0,
    /** TOW bekannt – Voraussetzung für brauchbare Pseudoranges. */
    val towKnown: Boolean = false,
    /** Millisekunden-Mehrdeutigkeit – Pseudorange damit unbrauchbar. */
    val msecAmbiguous: Boolean = false,
    /** Code-Lock vorhanden. */
    val codeLock: Boolean = false,
    /** Unsicherheit der Trägerphase in Metern; null = nicht geliefert. */
    val carrierPhaseUncertaintyMeters: Double? = null,
    // --- Rohfelder für den CSV-Export ---
    /** receivedSvTimeNanos (Sendezeit innerhalb der Woche); null = nicht geliefert. */
    val tTxRawNanos: Long? = null,
    /** 1-Sigma-Unsicherheit der Sendezeit in ns; null = nicht gemeldet (<= 0). */
    val svTimeUncertaintyNanos: Long? = null,
    /** Rohwert von accumulatedDeltaRangeState; 0 = nicht geliefert. */
    val adrState: Int = 0
)

/** Empfänger-Uhr (GnssClock) zum Zeitpunkt der letzten Messung. */
data class RawClockSnapshot(
    val timeNanos: Long,
    val fullBiasNanos: Long,
    val biasNanos: Double,
    val driftNsPerSec: Double,
    val leapSecond: Int,
    val receivedAtMillis: Long
)

/** Vollständiges Band-Label, z. B. "L5 (1176.45 MHz)". */
fun gnssBandLabel(constellation: Constellation, carrierFrequencyHz: Float?): String {
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

/** Kurzes Band-Label für Badges, z. B. "L5". */
fun gnssBandShort(constellation: Constellation, carrierFrequencyHz: Float?): String {
    if (carrierFrequencyHz == null || carrierFrequencyHz <= 0f) return "?"
    val mhz = carrierFrequencyHz / 1_000_000.0f
    return when {
        mhz in 1574.0f..1577.0f -> if (constellation == Constellation.GALILEO) "E1" else "L1"
        mhz in 1175.0f..1178.0f -> if (constellation == Constellation.GALILEO) "E5a" else "L5"
        mhz in 1226.0f..1229.0f -> "L2"
        mhz in 1559.0f..1610.0f -> if (constellation == Constellation.GLONASS) "G1" else "B1"
        mhz in 1206.0f..1208.0f -> "E5b"
        else -> String.format(Locale.US, "%.0f", mhz)
    }
}

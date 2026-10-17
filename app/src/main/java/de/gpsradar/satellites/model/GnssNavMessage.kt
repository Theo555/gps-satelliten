package de.gpsradar.satellites.model

/**
 * Eine dekodierte GNSS-Navigationsnachricht (Rohdaten hinter Ephemeride/Almanach).
 *
 * Nur Primitive + [Constellation] → stabil für Compose.
 */
data class GnssNavMessage(
    val svid: Int,
    val constellation: Constellation,
    val typeLabel: String,
    val statusLabel: String,
    val messageId: Int,
    val submessageId: Int,
    val dataHex: String,
    val receivedAtMillis: Long
)

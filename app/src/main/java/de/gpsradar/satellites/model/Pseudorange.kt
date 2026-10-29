package de.gpsradar.satellites.model

import kotlin.math.floor

/**
 * Umrechnung der GNSS-Zeitstempel in eine Pseudorange.
 *
 * Der Fallstrick: `GnssClock.timeNanos` und `fullBiasNanos` liegen bei etwa
 * 1,5 · 10^18 ns. Ein [Double] kann diese Größe nur auf 256 ns genau darstellen
 * — das sind 77 m. Wird die Differenz in [Double] gebildet, wandert also ein
 * Fehler von bis zu ±38 m in die Pseudorange, obwohl sie im CSV mit
 * Millimeterstellen ausgegeben wird.
 *
 * Deshalb werden die großen Terme hier in [Long] verrechnet. Nur die kleine
 * Differenz (Größenordnung 10^8 ns) und der sub-Nanosekunden-Bias gehen nach
 * [Double].
 */
object Pseudorange {

    const val SPEED_OF_LIGHT = 299_792_458.0
    const val WEEK_NANOS = 604_800_000_000_000.0

    /** Unterhalb gilt die Pseudorange als unplausibel (Empfänger noch nicht eingeschwungen). */
    const val MIN_METERS = 5_000_000.0

    /** Oberhalb gilt die Pseudorange als unplausibel (kein realer Signalweg). */
    const val MAX_METERS = 30_000_000.0

    /**
     * Wochenversatz in Nanosekunden, mit dem `receivedSvTimeNanos` auf dieselbe
     * Zeitbasis wie `timeNanos` gehoben wird. Das Ergebnis ist ein Vielfaches
     * einer Woche und damit auch als [Double] exakt darstellbar.
     */
    fun weekOffsetNanos(fullBiasNanos: Long): Long =
        (floor(-fullBiasNanos.toDouble() / WEEK_NANOS) * WEEK_NANOS).toLong()

    /**
     * Pseudorange in Metern oder `null`, wenn die Messung keine brauchbare
     * Sendezeit hat oder das Ergebnis unplausibel ist.
     *
     * @param tRxNanos Empfangszeit in GNSS-Zeit, also `timeNanos - fullBiasNanos`
     * @param biasNanos Sub-Nanosekunden-Anteil des Empfängeruhrenfehlers
     */
    fun meters(
        tRxNanos: Long,
        receivedSvTimeNanos: Long,
        weekOffsetNanos: Long,
        biasNanos: Double
    ): Double? {
        if (receivedSvTimeNanos == Long.MIN_VALUE || biasNanos.isNaN()) return null
        val tTxNanos = receivedSvTimeNanos + weekOffsetNanos
        val elapsedNanos = (tRxNanos - tTxNanos).toDouble() - biasNanos
        val meters = elapsedNanos * 1e-9 * SPEED_OF_LIGHT
        return if (meters in MIN_METERS..MAX_METERS) meters else null
    }
}

package de.gpsradar.satellites.model

/**
 * Begründung, warum eine Pseudorange brauchbar ist oder nicht.
 *
 * Vorher schrieb der Export bei einer verworfenen Pseudorange einfach eine leere
 * Zelle. Dadurch sah eine falsche Zeitbasis monatelang wie eine Gerätegrenze
 * aus. Der Status macht den Grund sichtbar.
 */
enum class PseudorangeStatus(val token: String) {
    /** Brauchbar, der Wert steht in [PseudorangeResult.meters]. */
    OK("ok"),

    /** Empfängeruhr ohne `fullBiasNanos`/`biasNanos` – ohne Uhr keine Pseudorange. */
    NO_CLOCK("no_clock"),

    /** `receivedSvTimeNanos` unbekannt (`Long.MIN_VALUE`) – keine Sendezeit. */
    NO_TIME("no_time"),

    /** Laufzeit außerhalb 5 000…30 000 km – Zeitbasis falsch oder Signalweg unmöglich. */
    IMPLAUSIBLE("implausible")
}

data class PseudorangeResult(val meters: Double?, val status: PseudorangeStatus)

/**
 * Umrechnung der GNSS-Zeitstempel in eine Pseudorange.
 *
 * Zwei Fallstricke, die hier beide behandelt werden:
 *
 * 1. **Genauigkeit:** `GnssClock.timeNanos` und `fullBiasNanos` liegen bei etwa
 *    1,5 · 10^18 ns. Ein [Double] löst dort nur 256 ns auf (= 77 m). Deshalb
 *    werden die großen Terme in [Long] verrechnet; nur die kleine Differenz
 *    (Größenordnung 10^8 ns) und der sub-Nanosekunden-Bias gehen nach [Double].
 *
 * 2. **Zeitbasis:** `GnssMeasurement.getReceivedSvTimeNanos()` ist laut
 *    Android-Doku „relative to the beginning of the system week for all
 *    constellations except for Glonass where it is relative to the beginning of
 *    the Glonass system day". Jede Konstellation zählt also in ihrer **eigenen**
 *    Zeit, und GLONASS zusätzlich ab dem Systemtag statt ab der Systemwoche.
 *    Wer blind einen GPS-Wochenversatz addiert, erhält für BeiDou und GLONASS
 *    Kilometerwerte jenseits jeder Plausibilität – genau das war der Fehler bis
 *    1.3.12 (dort standen BeiDou und GLONASS im Export dauerhaft leer).
 */
object Pseudorange {

    const val SPEED_OF_LIGHT = 299_792_458.0

    /** Bezugsgröße für alle Konstellationen außer GLONASS. */
    const val WEEK_NANOS = 604_800_000_000_000L

    /** Bezugsgröße der GLONASS-Zeit: Systemtag, nicht Systemwoche. */
    const val DAY_NANOS = 86_400_000_000_000L

    /**
     * BeiDou-Zeit (BDT) läuft konstant 14 s hinter der GPS-Zeit; BDT kennt keine
     * Schaltsekunden (Epoche 2006-01-01). Der Wert ist eine Konstante, keine Tabelle.
     */
    const val BEIDOU_OFFSET_NANOS = 14_000_000_000L

    /** GLONASS-Zeit ist UTC(SU) + 3 h; UTC(SU) folgt UTC ohne eigenen Versatz. */
    const val GLONASS_OFFSET_NANOS = 10_800_000_000_000L

    /**
     * GPS − UTC in Sekunden, gültig seit 2017-01-01 (seitdem keine Schaltsekunde
     * mehr). Rückfall, weil das S20 FE über `GnssClock.getLeapSecond()` nichts
     * meldet; die Gegenprobe aus den Rohdaten ergab genau 18 s.
     */
    const val FALLBACK_LEAP_SECONDS = 18

    /** Unterhalb gilt die Pseudorange als unplausibel (Empfänger noch nicht eingeschwungen). */
    const val MIN_METERS = 5_000_000.0

    /**
     * Oberhalb gilt die Pseudorange als unplausibel (kein realer Signalweg).
     *
     * 45 000 km statt der früheren 30 000 km, weil geostationäre Satelliten
     * realistisch bei 36 000–41 000 km liegen – BeiDou B1I meldet solche (GEO),
     * ein echter Ausreißer im Rohdatenexport vom 10.11. lag bei 39 900 km. Eine
     * falsche Zeitbasis liegt dagegen um Sekunden daneben (≥ 300 000 km), wird
     * also weiterhin erkannt.
     */
    const val MAX_METERS = 45_000_000.0

    /** Volle Epoche, ab der `receivedSvTimeNanos` zählt. */
    fun frameNanos(constellation: Constellation): Long =
        if (constellation == Constellation.GLONASS) DAY_NANOS else WEEK_NANOS

    /** Schaltsekunden zwischen UTC und GPS; unbrauchbare Geräteangabe → Rückfallwert. */
    fun leapSeconds(leapSecond: Int): Int =
        if (leapSecond in 1..60) leapSecond else FALLBACK_LEAP_SECONDS

    /**
     * Versatz der Zeitbasis auf GPS-Zeit, **ohne** die volle Epoche. Für GPS,
     * Galileo, QZSS, SBAS und NavIC ist er null (GST liegt Nanosekunden neben GPS).
     */
    fun timeScaleOffsetNanos(constellation: Constellation, leapSecond: Int): Long =
        when (constellation) {
            Constellation.BEIDOU -> BEIDOU_OFFSET_NANOS
            // GLONASS → UTC (Moskauer Zeit abziehen) → GPS (Schaltsekunden addieren)
            Constellation.GLONASS ->
                -GLONASS_OFFSET_NANOS + leapSeconds(leapSecond) * 1_000_000_000L
            else -> 0L
        }

    /**
     * Absolute Sendezeit in GPS-Zeitbasis.
     *
     * Die volle Epoche wird **nicht** aus `fullBiasNanos` geraten, sondern so
     * gewählt, dass die Sendezeit möglichst nah an der Empfangszeit liegt. Damit
     * ist die Rechnung unabhängig von Wochenrändern und gerät nicht daneben, wenn
     * die Uhr des Empfängers springt.
     */
    fun absoluteTransmitNanos(
        constellation: Constellation,
        receivedSvTimeNanos: Long,
        tRxNanos: Long,
        leapSecond: Int
    ): Long? {
        if (receivedSvTimeNanos == Long.MIN_VALUE) return null
        val frame = frameNanos(constellation)
        val offset = timeScaleOffsetNanos(constellation, leapSecond)
        val periods = (tRxNanos - receivedSvTimeNanos - offset + frame / 2).floorDiv(frame)
        return receivedSvTimeNanos + periods * frame + offset
    }

    /**
     * Pseudorange in Metern oder `null` samt Begründung.
     *
     * @param tRxNanos Empfangszeit in GNSS-Zeit, also `timeNanos - fullBiasNanos`
     * @param biasNanos Sub-Nanosekunden-Anteil des Empfängeruhrenfehlers
     * @param leapSecond Schaltsekunden laut `GnssClock.getLeapSecond()`; 0 = unbekannt
     */
    fun meters(
        constellation: Constellation,
        tRxNanos: Long,
        receivedSvTimeNanos: Long,
        biasNanos: Double,
        leapSecond: Int = 0
    ): PseudorangeResult {
        if (biasNanos.isNaN()) return PseudorangeResult(null, PseudorangeStatus.NO_CLOCK)
        val tTxNanos = absoluteTransmitNanos(constellation, receivedSvTimeNanos, tRxNanos, leapSecond)
            ?: return PseudorangeResult(null, PseudorangeStatus.NO_TIME)
        val elapsedNanos = (tRxNanos - tTxNanos).toDouble() - biasNanos
        val meters = elapsedNanos * 1e-9 * SPEED_OF_LIGHT
        return if (meters >= MIN_METERS && meters <= MAX_METERS) {
            PseudorangeResult(meters, PseudorangeStatus.OK)
        } else {
            PseudorangeResult(null, PseudorangeStatus.IMPLAUSIBLE)
        }
    }
}

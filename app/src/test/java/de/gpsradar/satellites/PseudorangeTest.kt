package de.gpsradar.satellites

import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.Pseudorange
import de.gpsradar.satellites.model.PseudorangeStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Die Pseudorange wird in [Long] gerechnet, weil `timeNanos` und
 * `fullBiasNanos` bei ~1,5 · 10^18 ns liegen und ein [Double] dort nur
 * 256 ns auflöst (= 77 m).
 *
 * Zweiter Schwerpunkt: jede Konstellation zählt in ihrer eigenen Zeitbasis, und
 * GLONASS zusätzlich ab dem Systemtag statt ab der Systemwoche. Diese Tests
 * halten die Versätze und ihre Gegenprobe aus echten Rohdaten fest.
 */
class PseudorangeTest {

    // Echte Werte aus einem Export des S20 FE (Android 13), GPS/E1.
    private val timeNanos = 14_105_000_000L
    private val fullBiasNanos = -1_477_297_463_522_289_150L
    private val receivedSvTimeNanos = 375_877_552_911_062L
    private val tTxNanosFromCsv = 1_477_297_477_552_911_062L

    private val tRxNanos = timeNanos - fullBiasNanos

    /** Plausible Signallaufzeit für einen Satelliten in ~23 400 km Entfernung. */
    private val flightNanos = 78_000_000L
    private val flightMeters = flightNanos * 1e-9 * Pseudorange.SPEED_OF_LIGHT

    /**
     * Baut eine Roh-Sendezeit, die [flightNanos] vor dem Empfang liegt -- also so,
     * wie das Gerät sie für die jeweilige Konstellation liefern würde.
     */
    private fun rawFor(
        constellation: Constellation,
        periods: Long = 2442L,
        flightNanos: Long = this.flightNanos,
        leapSecond: Int = 18
    ): Long {
        val tTx = tRxNanos - flightNanos
        val offset = Pseudorange.timeScaleOffsetNanos(constellation, leapSecond)
        return tTx - offset - periods * Pseudorange.frameNanos(constellation)
    }

    @Test
    fun gpsUndGalileoRechnenWieBisher() {
        // Die nächstgelegene Woche ist hier genau der Wert, den das CSV zeigte.
        val gps = Pseudorange.meters(Constellation.GPS, tRxNanos, receivedSvTimeNanos, 0.0, 18)
        assertEquals(PseudorangeStatus.OK, gps.status)
        assertEquals(22_297_989.823, gps.meters!!, 0.001)
        assertEquals(
            tTxNanosFromCsv,
            Pseudorange.absoluteTransmitNanos(Constellation.GPS, receivedSvTimeNanos, tRxNanos, 18)
        )

        val gal = Pseudorange.meters(Constellation.GALILEO, tRxNanos, rawFor(Constellation.GALILEO), 0.0, 18)
        assertEquals(PseudorangeStatus.OK, gal.status)
        assertEquals(flightMeters, gal.meters!!, 0.001)
    }

    @Test
    fun beidouLiegtVierzehnSekundenHinterGps() {
        // Gegenprobe aus dem Export: BeiDou lag 14,084 s daneben, davon 14 s Zeitbasis.
        val result = Pseudorange.meters(Constellation.BEIDOU, tRxNanos, rawFor(Constellation.BEIDOU), 0.0, 18)
        assertEquals(PseudorangeStatus.OK, result.status)
        assertEquals(flightMeters, result.meters!!, 0.001)
    }

    @Test
    fun glonassZaehltAbDemSystemtagUndDreiStundenZurueck() {
        assertEquals(Pseudorange.DAY_NANOS, Pseudorange.frameNanos(Constellation.GLONASS))
        assertEquals(Pseudorange.WEEK_NANOS, Pseudorange.frameNanos(Constellation.GPS))
        // Moskauer Zeit minus 3 h, danach Schaltsekunden auf GPS-Zeit.
        val offset = Pseudorange.timeScaleOffsetNanos(Constellation.GLONASS, 18)
        assertEquals(-10_800_000_000_000L + 18_000_000_000L, offset)

        val result = Pseudorange.meters(Constellation.GLONASS, tRxNanos, rawFor(Constellation.GLONASS), 0.0, 18)
        assertEquals(PseudorangeStatus.OK, result.status)
        assertEquals(flightMeters, result.meters!!, 0.001)
    }

    @Test
    fun glonassOhneSchaltsekundeNutztDenRueckfall() {
        // Das S20 FE meldet keine Schaltsekunde (Int.MIN_VALUE); 0 heisst ebenfalls unbekannt.
        for (leap in intArrayOf(0, Int.MIN_VALUE)) {
            val result = Pseudorange.meters(
                Constellation.GLONASS, tRxNanos, rawFor(Constellation.GLONASS, leapSecond = 18), 0.0, leap
            )
            assertEquals("leapSecond=$leap", PseudorangeStatus.OK, result.status)
            assertEquals(flightMeters, result.meters!!, 0.001)
        }
    }

    @Test
    fun falscheZeitbasisWirdAlsUnplausibelGemeldet() {
        // So sah der Fehler bis 1.3.12 aus: BeiDou-Rohwert ohne die 14 s behandelt.
        val bdsRohwert = rawFor(Constellation.BEIDOU)
        val falsch = Pseudorange.meters(Constellation.GPS, tRxNanos, bdsRohwert, 0.0, 18)
        assertEquals(PseudorangeStatus.IMPLAUSIBLE, falsch.status)
        assertNull(falsch.meters)
    }

    @Test
    fun volleEpocheIstBeliebig() {
        for (constellation in listOf(Constellation.GPS, Constellation.BEIDOU, Constellation.GLONASS)) {
            for (periods in 0L..4L) {
                val result = Pseudorange.meters(
                    constellation, tRxNanos, rawFor(constellation, periods = periods), 0.0, 18
                )
                assertEquals("$constellation/$periods", flightMeters, result.meters!!, 0.001)
            }
        }
    }

    @Test
    fun unbekannteSendezeitLiefertNoTime() {
        val result = Pseudorange.meters(Constellation.GPS, tRxNanos, Long.MIN_VALUE, 0.0, 18)
        assertEquals(PseudorangeStatus.NO_TIME, result.status)
        assertNull(result.meters)
    }

    @Test
    fun unbrauchbarerBiasLiefertNoClock() {
        val result = Pseudorange.meters(Constellation.GPS, tRxNanos, receivedSvTimeNanos, Double.NaN, 18)
        assertEquals(PseudorangeStatus.NO_CLOCK, result.status)
        assertNull(result.meters)
    }

    @Test
    fun doubleRechnungWaereUmMeterFalsch() {
        // So wurde es vorher gerechnet: alles in Double, inklusive der großen Terme.
        val alt = (timeNanos.toDouble() - fullBiasNanos.toDouble() - 0.0 -
            tTxNanosFromCsv.toDouble()) * 1e-9 * Pseudorange.SPEED_OF_LIGHT
        val neu = Pseudorange.meters(Constellation.GPS, tRxNanos, receivedSvTimeNanos, 0.0, 18).meters!!
        // Der alte Weg lag je nach Uhrzustand bis zu ~38 m daneben.
        assertTrue("Abweichung zu klein: ${abs(alt - neu)} m", abs(alt - neu) > 1.0)
    }

    @Test
    fun errorBiasGehtNichtVerloren() {
        val ohne = Pseudorange.meters(Constellation.GPS, tRxNanos, rawFor(Constellation.GPS), 0.0, 18).meters!!
        val mit = Pseudorange.meters(Constellation.GPS, tRxNanos, rawFor(Constellation.GPS), 2.5, 18).meters!!
        // 2,5 ns Bias entsprechen rund 0,75 m.
        assertEquals(0.75, ohne - mit, 0.01)
    }

    @Test
    fun geoSatellitInVierzigTausendKilometernIstPlausibel() {
        // BeiDou S8 lag nach der Zeitbasiskorrektur bei 39 900 km (GEO-typisch).
        val result = Pseudorange.meters(
            Constellation.BEIDOU, tRxNanos, rawFor(Constellation.BEIDOU, flightNanos = 133_000_000L), 0.0, 18
        )
        assertEquals(PseudorangeStatus.OK, result.status)
        assertEquals(133_000_000L * 1e-9 * Pseudorange.SPEED_OF_LIGHT, result.meters!!, 0.001)
    }

    @Test
    fun statusTokensSindStabil() {
        // Diese Kürzel stehen im CSV-Kopf als Vertrag mit dem Auswertewerkzeug.
        assertEquals("ok", PseudorangeStatus.OK.token)
        assertEquals("no_clock", PseudorangeStatus.NO_CLOCK.token)
        assertEquals("no_time", PseudorangeStatus.NO_TIME.token)
        assertEquals("implausible", PseudorangeStatus.IMPLAUSIBLE.token)
    }
}

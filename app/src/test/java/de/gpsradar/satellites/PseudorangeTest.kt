package de.gpsradar.satellites

import de.gpsradar.satellites.model.Pseudorange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Die Pseudorange wird in [Long] gerechnet, weil `timeNanos` und
 * `fullBiasNanos` bei ~1,5 · 10^18 ns liegen und ein [Double] dort nur
 * 256 ns auflöst (= 77 m). Diese Tests halten das fest.
 */
class PseudorangeTest {

    // Echte Werte aus einem Export des S20 FE (Android 13).
    private val timeNanos = 14_105_000_000L
    private val fullBiasNanos = -1_477_297_463_522_289_150L
    private val receivedSvTimeNanos = 375_877_552_911_062L
    private val tTxNanosFromCsv = 1_477_297_477_552_911_062L

    private val tRxNanos = timeNanos - fullBiasNanos
    private val weekOffset = Pseudorange.weekOffsetNanos(fullBiasNanos)

    @Test
    fun wochenversatzPasstZurSendezeitAusDemCsv() {
        assertEquals(tTxNanosFromCsv, receivedSvTimeNanos + weekOffset)
    }

    @Test
    fun wochenversatzIstVielfachesEinerWoche() {
        assertEquals(0L, weekOffset % Pseudorange.WEEK_NANOS.toLong())
        assertTrue(weekOffset > 0)
    }

    @Test
    fun pseudorangeStimmtAufMillimeterMitDerGanzzahlrechnung() {
        val meters = Pseudorange.meters(tRxNanos, receivedSvTimeNanos, weekOffset, 0.0)!!
        // (timeNanos - fullBiasNanos - tTxNanos) · 1e-9 · c, exakt in Nanosekunden
        val elapsedNanos = timeNanos - fullBiasNanos - tTxNanosFromCsv
        val expected = elapsedNanos * 1e-9 * Pseudorange.SPEED_OF_LIGHT
        assertEquals(expected, meters, 0.001)
        assertEquals(22_297_989.823, meters, 0.001)
    }

    @Test
    fun doubleRechnungWaereUmMeterFalsch() {
        // So wurde es vorher gerechnet: alles in Double, inklusive der großen Terme.
        val alt = (timeNanos.toDouble() - fullBiasNanos.toDouble() - 0.0 -
            tTxNanosFromCsv.toDouble()) * 1e-9 * Pseudorange.SPEED_OF_LIGHT
        val neu = Pseudorange.meters(tRxNanos, receivedSvTimeNanos, weekOffset, 0.0)!!
        // Der alte Weg lag je nach Uhrzustand bis zu ~38 m daneben.
        assertTrue("Abweichung zu klein: ${abs(alt - neu)} m", abs(alt - neu) > 1.0)
    }

    @Test
    fun unbekannteSendezeitLiefertNichts() {
        assertNull(
            Pseudorange.meters(tRxNanos, Long.MIN_VALUE, weekOffset, 0.0)
        )
    }

    @Test
    fun unbrauchbarerBiasLiefertNichts() {
        assertNull(
            Pseudorange.meters(tRxNanos, receivedSvTimeNanos, weekOffset, Double.NaN)
        )
    }

    @Test
    fun unplausibleEntfernungLiefertNichts() {
        // Sendezeit 2 s vor Empfang -> 600 000 km, also außerhalb des Bereichs.
        assertNull(
            Pseudorange.meters(
                tRxNanos,
                tRxNanos - weekOffset - 2_000_000_000L,
                weekOffset,
                0.0
            )
        )
    }

    @Test
    fun errorBiasGehtNichtVerloren() {
        val ohne = Pseudorange.meters(tRxNanos, receivedSvTimeNanos, weekOffset, 0.0)!!
        val mit = Pseudorange.meters(tRxNanos, receivedSvTimeNanos, weekOffset, 2.5)!!
        // 2,5 ns Bias entsprechen rund 0,75 m.
        assertEquals(0.75, ohne - mit, 0.01)
    }
}

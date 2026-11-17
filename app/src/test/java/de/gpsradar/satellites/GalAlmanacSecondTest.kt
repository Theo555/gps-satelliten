package de.gpsradar.satellites

import de.gpsradar.satellites.model.GalLnav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Galileo-Almanachswörter 9 und 10 gegen einen echten Mitschnitt.
 *
 * Aufnahme vom 17.11.2026 am Südfenster: C18 sendet Worttyp 9 und zwei
 * Sekunden später Worttyp 10, beide mit IODa 9.
 *
 * ## Was dabei herauskommt
 *
 * Worttyp 9 vervollständigt den Satz für **SVID2** (Bezugszeit, M₀ und Uhr) und
 * beginnt den Satz für **SVID3** (Δ√A, e, ω, δi). Worttyp 10 liefert den Rest
 * für SVID3. Dessen Bezugszeit steht aber in **keinem** der beiden Wörter —
 * deshalb bleibt ein SVID3-Satz unvollständig und wird nicht veröffentlicht.
 */
class GalAlmanacSecondTest {

    /** Worttyp 9 von C18: Rest für SVID2, Anfang für SVID3 (hier C06). */
    private fun worttyp9(): String = GalLnav.contentBits(
        byteArrayOf(
            0x09, 0x95.toByte(), 0x59, 0x1F, 0x29, 0x00, 0x29, 0x00,
            0x08, 0x0C, 0x00, 0xB0.toByte(), 0x4E, 0x92.toByte(), 0xE1.toByte(),
            0xFB.toByte(), 0x80.toByte(), 0x00, 0x00, 0x00, 0x00, 0x0A, 0xAA.toByte(),
            0xAA.toByte(), 0x92.toByte(), 0x20, 0x38, 0x80.toByte(), 0x40
        )
    )!!

    /** Worttyp 10 von C18: Rest für SVID3. */
    private fun worttyp10(): String = GalLnav.contentBits(
        byteArrayOf(
            0x0A, 0x91.toByte(), 0xE6.toByte(), 0x8F.toByte(), 0xE0.toByte(), 0x6E,
            0x5B, 0xFE.toByte(), 0x8F.toByte(), 0xFF.toByte(), 0xF0.toByte(), 0x00,
            0x08, 0xFF.toByte(), 0x2C, 0xC0.toByte(), 0xD0.toByte(), 0x00, 0x00,
            0x00, 0x00, 0x0A, 0xAA.toByte(), 0xAA.toByte(), 0x96.toByte(),
            0x0E, 0xD6.toByte(), 0x82.toByte(), 0xB0.toByte()
        )
    )!!

    @Test
    fun bezugUndUhrFuerDenZweitenSatelliten() {
        // Der Satellit steht in Worttyp 8, nicht hier — der Aufrufer kennt ihn.
        val uhr = GalLnav.almanacSecondClock(worttyp9(), svid2 = 11)!!

        assertEquals(11, uhr.svid)
        assertEquals(9, uhr.iodA)
        assertEquals(1, uhr.weekBits)
        assertEquals(207_000, uhr.referenceSeconds)
        assertEquals(0.76478529656, uhr.meanAnomaly!!, 1e-11)
        assertEquals(7.8201294e-05, uhr.clockBias!!, 1e-12)
    }

    @Test
    fun bahnBeginnFuerDenDrittenSatelliten() {
        val bahn = GalLnav.almanacThird(worttyp9())!!

        assertEquals(6, bahn.svid)
        // √A_nominal = 5440,588; dazu Δ√A = 0,0215.
        assertEquals(5440.609687869177, bahn.sqrtA!!, 1e-9)
        assertEquals(0.0005950927734375, bahn.eccentricity!!, 1e-15)
        assertEquals(1.8011810663757324, bahn.argumentOfPerigee!!, 1e-10)
        assertEquals(55.209, Math.toDegrees(bahn.inclination!!), 0.01)
    }

    @Test
    fun restFuerDenDrittenSatelliten() {
        val rest = GalLnav.almanacFourth(worttyp10(), svid3 = 6)!!

        assertEquals(6, rest.svid)
        assertEquals(0.746281653306, rest.ascendingNode!!, 1e-11)
        assertEquals(-5.8516723170686385e-09, rest.nodeRate!!, 1e-20)
        assertEquals(1.354217414305, rest.meanAnomaly!!, 1e-11)
        assertEquals(-3.528594970703125e-04, rest.clockBias!!, 1e-15)
    }

    /**
     * Die Bezugszeit von SVID3 steht weder in Worttyp 9 noch in 10 — der Satz
     * bleibt deshalb unvollständig. lieber gar nichts veröffentlichen als eine
     * Zeit zu erfinden.
     */
    @Test
    fun dritterSatellitBleibtOhneBezugszeit() {
        val teil1 = GalLnav.almanacThird(worttyp9())!!
        val teil2 = GalLnav.almanacFourth(worttyp10(), svid3 = teil1.svid)!!

        val zusammen = teil1.merge(teil2)
        assertFalse(zusammen.isComplete)
        assertTrue(zusammen.sqrtA != null)
        assertTrue(zusammen.ascendingNode != null)
        assertEquals(null, zusammen.referenceSeconds)
    }
}

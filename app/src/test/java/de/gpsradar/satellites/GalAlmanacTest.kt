package de.gpsradar.satellites

import de.gpsradar.satellites.model.GalAlmanac
import de.gpsradar.satellites.model.GalLnav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft den Galileo-Almanach gegen echte Meldungen.
 *
 * Aufnahme vom 17.11.2026, S20 FE am Südfenster: C05 sendet den Almanach von
 * **C13** — Worttyp 7 (Bahn) und, zwei Sekunden später, Worttyp 8 (Uhr).
 *
 * ## Woran die Bitlagen erkannt wurden
 *
 * Nicht an einer Referenzdatei, sondern an der Physik: √A muss bei Galileo um
 * 5440,6 liegen (A_nominal 29 600 000 m, Abweichung als Δ√A übertragen), die
 * Bahnneigung um 56° (Nominalwert plus δi), und t_oa muss ein Vielfaches von
 * 600 s sein. Ein falscher Bitversatz trifft das alles nicht gleichzeitig.
 *
 * Gegenprobe: Galileo-Ephemeriden aus derselben Aufnahme liegen bei
 * √A ≈ 5440,6 — der Almanach trifft das auf 0,01 √m.
 */
class GalAlmanacTest {

    /** Worttyp 7: Bahnsatz für SVID1 (hier C13), Uhr folgt im nächsten Wort. */
    private fun worttyp7(): ByteArray = byteArrayOf(
        0x07, 0xD5.toByte(), 0x5D, 0x34, 0x01, 0x80.toByte(), 0x63, 0x35,
        0xE8.toByte(), 0x31, 0xE4.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x18,
        0x2D, 0x15, 0x50, 0x00, 0x00, 0x00, 0x00, 0x0D, 0x29, 0x8E.toByte(),
        0x16, 0x3D, 0x55, 0x00, 0x40
    )

    /** Worttyp 8: Uhr für C13 und Bahn für SVID2 (hier nicht belegt). */
    private fun worttyp8(): ByteArray = byteArrayOf(
        0x08, 0xDF.toByte(), 0xFD.toByte(), 0xB0.toByte(), 0x00, 0x00, 0x0A,
        0xAA.toByte(), 0xAA.toByte(), 0xAA.toByte(), 0xAA.toByte(), 0xAA.toByte(),
        0xAA.toByte(), 0xAA.toByte(), 0xAA.toByte(), 0xAA.toByte(), 0xA0.toByte(),
        0x00, 0x00, 0x00, 0x00, 0x04, 0x21, 0xEF.toByte(), 0x53, 0x27, 0xA7.toByte(),
        0xB2.toByte(), 0xB0.toByte()
    )

    private fun bitsOf(data: ByteArray): String = GalLnav.contentBits(data)!!

    @Test
    fun echterMitschnittWirdGelesen() {
        val a = GalLnav.almanacReference(bitsOf(worttyp7()))!!

        // Der sendende Satellit ist C05, der Almanach gehört C13.
        assertEquals(13, a.svid)
        assertEquals(13, a.iodA)
        assertEquals(1, a.weekBits)
        assertEquals(209_400, a.referenceSeconds)

        // √A_nominal = 5441,18; Galileo liegt bei 5440,6.
        assertEquals(5440.61, a.sqrtA!!, 0.02)
        assertEquals(0.000366, a.eccentricity!!, 1e-5)
        // 56° Nominalwert plus δi.
        assertEquals(57.09, Math.toDegrees(a.inclination!!), 0.02)
    }

    @Test
    fun bahnAlleinIstNochNichtVollstaendig() {
        val a = GalLnav.almanacReference(bitsOf(worttyp7()))!!

        assertFalse(a.isComplete)
        assertNull(a.clockBias)
        assertNull(a.toAlmanac(week = 3500))
    }

    @Test
    fun uhrAusDemFolgewortVervollstaendigtDenSatz() {
        val bahn = GalLnav.almanacReference(bitsOf(worttyp7()))!!
        val uhr = GalLnav.almanacClock(bitsOf(worttyp8()), svid = bahn.svid)!!

        assertEquals(13, uhr.svid)
        assertEquals(-7.05718994140625e-05, uhr.clockBias!!, 1e-15)

        val komplett = bahn.merge(uhr)
        assertTrue(komplett.isComplete)

        val satz = komplett.toAlmanac(week = 3500)!!
        assertEquals("GAL", satz.system)
        assertEquals(13, satz.svid)
        assertEquals(3500, satz.week)
        assertEquals(209_400, satz.referenceSeconds)
        assertEquals(5440.61, satz.sqrtA, 0.02)
        assertEquals(GalAlmanac.HEALTH_UNKNOWN, satz.health)
    }

    /**
     * Im Mitschnitt trägt Worttyp 8 an dieser Stelle keinen zweiten Satelliten
     * (`SVID2 = 0`). Das ist keine Fehlfunktion, sondern die Belegung dieses
     * Rahmens — der Dekoder darf daraus nichts machen.
     */
    @Test
    fun zweiterSatzFehltInDiesemRahmen() {
        assertNull(GalLnav.almanacSecond(bitsOf(worttyp8())))
    }

    /** Zusammenführen überschreibt nichts, was schon da ist. */
    @Test
    fun mergeErgaenztNurFehlendes() {
        val bahn = GalLnav.almanacReference(bitsOf(worttyp7()))!!
        val uhr = GalLnav.almanacClock(bitsOf(worttyp8()), svid = bahn.svid)!!

        val einmal = bahn.merge(uhr)
        val nochmal = einmal.merge(uhr)

        assertEquals(einmal, nochmal)
        assertEquals(bahn.sqrtA!!, einmal.sqrtA!!, 0.0)
        assertEquals(uhr.clockBias!!, einmal.clockBias!!, 0.0)
    }
}

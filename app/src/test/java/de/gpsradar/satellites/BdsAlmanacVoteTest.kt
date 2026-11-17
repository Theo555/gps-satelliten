package de.gpsradar.satellites

import de.gpsradar.satellites.gps.NavEphemerisStore
import de.gpsradar.satellites.model.BdsLnav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Mehrheitsentscheidung beim BeiDou-Almanach.
 *
 * Anlass: Im Mitschnitt vom 17.11.2026 hatten nur 6 von 10 Almanachsmeldungen
 * eine gültige Seitenkennung, und dieselbe Seite kam zweimal mit verschiedenen
 * Werten. Eine einzelne Meldung ist deshalb kein Beleg — erst wenn derselbe
 * Satz zweimal gleich ankommt, wird er veröffentlicht.
 */
class BdsAlmanacVoteTest {

    /** Echte Meldung: Unterrahmen 4, Seite 3 (Almanach von C03), gesendet von C12. */
    private fun seiteDrei(): String {
        val roh = intArrayOf(
            0x38, 0x90, 0x43, 0x20, 0x1D, 0xF8, 0x0F, 0x12, 0x0A, 0xEB,
            0xBD, 0x97, 0x00, 0x00, 0x00, 0x00, 0x03, 0x88, 0x10, 0xB5,
            0x10, 0x1F, 0x68, 0x8D, 0xC0, 0x1A, 0xF3, 0x5E, 0xFF, 0xF3,
            0x8F, 0xCE, 0x18, 0x4E, 0x6D, 0xAD, 0x36, 0xD5, 0xF3, 0x55
        )
        return BdsLnav.dataBits(ByteArray(40) { roh[it].toByte() })!!
    }

    /** 17.11.2026, 10:20 UTC — nur für die Wochenableitung nötig. */
    private val empfang = 1_763_384_400_000L

    @Test
    fun eineEinzelneMeldungGenuegtNicht() {
        val speicher = NavEphemerisStore()

        assertFalse(speicher.bdsAlmanac(seiteDrei(), empfang))
        assertTrue(speicher.snapshot().almanac.isEmpty())
    }

    @Test
    fun zweiteGleicheMeldungVeroeffentlicht() {
        val speicher = NavEphemerisStore()

        speicher.bdsAlmanac(seiteDrei(), empfang)
        assertTrue(speicher.bdsAlmanac(seiteDrei(), empfang))

        val almanach = speicher.snapshot().almanac
        assertEquals(1, almanach.size)
        val satz = almanach.single()
        assertEquals("BDS", satz.system)
        assertEquals(3, satz.svid)
        assertEquals(495_616, satz.referenceSeconds)
        assertEquals(6493.46728515625, satz.sqrtA, 1e-9)
        // Die Woche steht nicht auf der Seite; der Sammler leitet sie aus der
        // Empfangszeit ab und akzeptiert dafür nur ganze Wochen.
        assertTrue(satz.week > 0)
    }

    /** Dieselbe Meldung ein drittes Mal ändert nichts mehr. */
    @Test
    fun weitereMeldungenAendernDenSatzNicht() {
        val speicher = NavEphemerisStore()

        repeat(2) { speicher.bdsAlmanac(seiteDrei(), empfang) }
        val vorher = speicher.snapshot().almanac.single()

        assertFalse(speicher.bdsAlmanac(seiteDrei(), empfang))
        assertEquals(vorher, speicher.snapshot().almanac.single())
    }
}

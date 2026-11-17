package de.gpsradar.satellites

import de.gpsradar.satellites.model.BdsAlmanac
import de.gpsradar.satellites.model.BdsLnav
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft den BeiDou-Almanach gegen einen echten Mitschnitt.
 *
 * Aufnahme vom 17.11.2026, S20 FE auf dem Ladegerät, innen: Unterrahmen 4,
 * Seite 3 — C12 sendet den Almanach von **C03**. Die Bitlagen stammen aus der
 * ICD-Abbildung „Bit allocation for subframe 4、5 (300 bits)" (BDS-SIS-ICD-B1I
 * 3.0, PDF-Seite 41) und den Paragraphen 5.2.4.13–15, **nicht** aus RTKLIB.
 *
 * ## Warum die Lücken in den Feldpositionen
 *
 * Wort 1 trägt 26 Datenbits und 4 Paritätsbits, die Wörter 2–10 dagegen 22
 * Datenbits und 8 Paritätsbits. Felder, die über eine Wortgrenze laufen, werden
 * deshalb in zwei Teilen gelesen (√A als 2+22 Bit). Wer gleichmäßig 26+4
 * annimmt, verschiebt alles ab Bit 53 — das war der frühere „3-Bit-Versatz".
 *
 * ## Gegenprobe
 *
 * Zu diesem Satz gibt es keine zweite Quelle; die Prüfung leistet die Physik:
 * √A = 6493,47 ist der geostationäre Bahnradius (a = 42 164 km), √A = 5283
 * wäre IGSO/MEO. Dazwischen gibt es bei BeiDou nichts.
 */
class BdsAlmanacTest {

    /** Echte Meldung: Unterrahmen 4, Seite 3, gesendet von C12. */
    private fun seiteDrei(): String {
        val roh = intArrayOf(
            0x38, 0x90, 0x43, 0x20, 0x1D, 0xF8, 0x0F, 0x12, 0x0A, 0xEB,
            0xBD, 0x97, 0x00, 0x00, 0x00, 0x00, 0x03, 0x88, 0x10, 0xB5,
            0x10, 0x1F, 0x68, 0x8D, 0xC0, 0x1A, 0xF3, 0x5E, 0xFF, 0xF3,
            0x8F, 0xCE, 0x18, 0x4E, 0x6D, 0xAD, 0x36, 0xD5, 0xF3, 0x55
        )
        return BdsLnav.dataBits(ByteArray(40) { roh[it].toByte() })!!
    }

    @Test
    fun echterMitschnittWirdGelesen() {
        val a = BdsLnav.almanac(seiteDrei())!!

        // Seite 3 gehoert zu SV 3, nicht zum sendenden C12.
        assertEquals(3, a.svid)
        assertEquals(4, a.subframe)
        assertEquals(3, a.page)

        assertEquals(6493.46728515625, a.sqrtA, 1e-9)
        assertEquals(0.0004792213439941406, a.eccentricity, 1e-15)
        assertEquals(7.789746188481772e-05, a.deltaInclination, 1e-15)
        assertEquals(0.3467040010189939, a.ascendingNode, 1e-12)
        assertEquals(1.5099067270929496, a.argumentOfPerigee, 1e-12)
        assertEquals(-0.8416296446814945, a.meanAnomaly, 1e-12)
        assertEquals(-5.714523747137343e-10, a.nodeRate, 1e-20)

        // toa steht als Vielfaches von 4096 s im Satz.
        assertEquals(495_616, a.referenceSeconds)

        assertEquals(0.0, a.clockBias, 0.0)
        assertEquals(0.0, a.clockRate, 0.0)
    }

    /** C03 ist geostationär — dort gilt der Bezugswert i₀ = 0. */
    @Test
    fun geoHatKeinenInklinationsbezug() {
        val a = BdsLnav.almanac(seiteDrei())!!

        assertTrue(a.isGeostationary)
        assertEquals(a.deltaInclination, a.inclination, 0.0)
        // δi der GEO-Almanache liegt im Bogenmaß bei wenigen 10⁻⁵ rad.
        assertEquals(7.789746188481772e-05, a.inclination, 1e-15)
    }

    @Test
    fun meoBekommtDenBezugswertDazu() {
        val meo = BdsAlmanac(
            svid = 10, subframe = 4, page = 10,
            sqrtA = 5282.9, eccentricity = 0.001, deltaInclination = 0.05,
            ascendingNode = 0.5, argumentOfPerigee = 0.5, meanAnomaly = 0.5,
            nodeRate = -2.0e-9, referenceSeconds = 4096,
            clockBias = 0.0, clockRate = 0.0
        )

        assertFalse(meo.isGeostationary)
        assertEquals(0.05 + 0.30 * PI, meo.inclination, 1e-15)
        // 0,30 Halbkreise = 54°, dazu die Abweichung von 0,05 rad ≈ 2,9°.
        assertEquals(56.87, Math.toDegrees(meo.inclination), 0.02)
    }

    /** Die Sollwerte müssen unabhängig von einer Referenzdatei prüfbar sein. */
    @Test
    fun unsinnigeWerteWerdenVerworfen() {
        val basis = BdsAlmanac(
            svid = 7, subframe = 4, page = 7,
            sqrtA = 5282.9, eccentricity = 0.001, deltaInclination = 0.0,
            ascendingNode = 0.0, argumentOfPerigee = 0.0, meanAnomaly = 0.0,
            nodeRate = 0.0, referenceSeconds = 4096,
            clockBias = 0.0, clockRate = 0.0
        )
        assertTrue(basis.isPlausible)

        // √A zwischen den beiden Bahnradien gibt es bei BeiDou nicht.
        assertFalse(basis.copy(sqrtA = 1000.0).isPlausible)
        // Exzentrizität über dem Maximum des Feldes (17 Bit, höchstens 0,0625).
        assertFalse(basis.copy(eccentricity = 0.08).isPlausible)
        // toa ist immer ein Vielfaches von 4096 s.
        assertFalse(basis.copy(referenceSeconds = 5000).isPlausible)
        // Winkel dürfen π nicht überschreiten.
        assertFalse(basis.copy(meanAnomaly = 4.0).isPlausible)
    }

    /** Unterrahmen 1–3 tragen keine Almanach-Seite. */
    @Test
    fun andereUnterraeumenLiefernNichts() {
        val bits = seiteDrei().toCharArray()
        // FraID (Bit 16–18, 0-basiert 15–17) auf Unterrahmen 2 setzen.
        bits[15] = '0'; bits[16] = '1'; bits[17] = '0'
        assertNull(BdsLnav.almanac(String(bits)))
    }

    /** Der gemeinsame Almanach-Typ bekommt System, Woche und „Gesundheit unbekannt". */
    @Test
    fun ueberfuehrungInDenGemeinsamenTyp() {
        val a = BdsLnav.almanac(seiteDrei())!!
        val gemeinsam = a.toAlmanac(week = 3500)

        assertEquals("BDS", gemeinsam.system)
        assertEquals(3, gemeinsam.svid)
        assertEquals(3500, gemeinsam.week)
        assertEquals(495_616, gemeinsam.referenceSeconds)
        assertEquals(a.sqrtA, gemeinsam.sqrtA, 0.0)
        assertEquals(a.inclination, gemeinsam.inclination, 0.0)
        // Die Almanach-Seite traegt keine Gesundheitsangabe.
        assertEquals(BdsAlmanac.HEALTH_UNKNOWN, gemeinsam.health)
    }
}

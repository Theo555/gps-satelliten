package de.gpsradar.satellites

import de.gpsradar.satellites.model.GpsLnav
import kotlin.math.PI

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft den GPS-Almanach gegen einen echten Mitschnitt.
 *
 * Aufnahme vom 16.11.2026, S20 FE, Süd-Fensterbank: Unterrahmen 5, Seiten
 * 17–20 (SV 17–20). Die Feldlagen stammen aus Figur 20-1, Blatt 4 von 11, die
 * Skalen aus Tabelle 20-VI — nicht aus RTKLIB, dessen `decode_alm_sat()` bei
 * Bit 50 beginnt und damit die SV-Kennung mitliest.
 *
 * ## Gegenprobe
 *
 * Die Ephemeride derselben Satelliten aus derselben Aufnahme liefert für
 * G17: √A = 5153,7439, e = 1,26142 · 10⁻², i₀ = 0,956010 rad, Ω₀ = −2,56603 rad,
 * ω = −1,04807 rad. Der Almanach (eine Kurzfassung derselben Bahn) trifft das
 * auf 0,06 m in √A, 1,3 · 10⁻⁵ in e und unter 0,1° in den Winkeln — nur M₀
 * weicht ab, weil beide Sätze zu verschiedenen Zeiten passen.
 */
class GpsAlmanacTest {

    /** Echte Meldung: Unterrahmen 5, Seite 17, aus dem Mitschnitt vom 16.11.2026. */
    private fun almanachSeite17(): String {
        val roh = intArrayOf(
            34, 193, 141, 13, 10, 153, 205, 119, 20, 89, 209, 215, 14, 66, 51, 148,
            63, 80, 0, 15, 40, 67, 96, 193, 37, 218, 183, 143, 53, 84, 84, 96,
            47, 57, 53, 168, 56, 255, 250, 224
        )
        return GpsLnav.dataBits(ByteArray(40) { roh[it].toByte() })!!
    }

    @Test
    fun echtemitschnittWirdGelesen() {
        val a = GpsLnav.almanac(almanachSeite17())!!
        assertEquals("GPS", a.system)
        assertEquals(17, a.svid)
        // toa ist ein Vielfaches von 4096 s. Derselbe Satz kam im Mitschnitt
        // auch mit toa = 319 488 s — ein neuer Almanach-Bezug während der
        // Aufnahme; im Sammler gewinnt der jüngere Satz.
        assertEquals(233_472, a.referenceSeconds)
        assertEquals(5153.6890, a.sqrtA, 0.0001)
        assertEquals(0.012607098, a.eccentricity, 1e-9)
        // i0 = 0,30 halbe Kreise + Korrektur.
        assertEquals(54.7738, grad(a.inclination), 0.002)
        assertEquals(-147.0692, grad(a.ascendingNode), 0.002)
        assertEquals(-60.0221, grad(a.argumentOfPerigee), 0.002)
        assertEquals(-94.3680, grad(a.meanAnomaly), 0.002)
        assertEquals(-0.0398, grad(a.nodeRate) * 86400, 0.001)
        assertEquals(-2.1458e-04, a.clockBias, 1e-8)
        assertEquals(-2.183e-11, a.clockRate, 1e-13)
        assertEquals(0, a.health)
        assertTrue(a.isHealthy)
        assertTrue(a.isPlausible)
        // Große Halbachse rund 26 560 km.
        assertEquals(26_556_000.0, a.semiMajorAxis, 20_000.0)
    }

    @Test
    fun ephemerideBestaetigtDieBahn() {
        val a = GpsLnav.almanac(almanachSeite17())!!
        // Werte aus dem RINEX-Datensatz G17 derselben Aufnahme.
        assertEquals(5153.7439, a.sqrtA, 0.10)
        assertEquals(0.0126142, a.eccentricity, 2e-5)
        assertEquals(0.956010 * 180 / PI, grad(a.inclination), 0.01)
        assertEquals(-2.56603 * 180 / PI, grad(a.ascendingNode), 0.15)
        assertEquals(-1.04807 * 180 / PI, grad(a.argumentOfPerigee), 0.15)
    }

    @Test
    fun ionospharenseiteIstKeinAlmanach() {
        // Unterrahmen 4, Seite 18 (Kennung 56) trägt Ionosphäre und UTC.
        val b = CharArray(240) { '0' }
        setze(b, 0, 8, 0b10001011)
        setze(b, 43, 3, 4)
        setze(b, 48, 2, 1)
        setze(b, 50, 6, 56)
        assertNull(GpsLnav.almanac(GpsLnav.dataBits(bytes(b))!!))
    }

    @Test
    fun unterrahmen5Seite25IstKeinAlmanach() {
        // Seite 25 trägt die Gesundheitsübersicht und den Bezug für alle Seiten.
        val b = CharArray(240) { '0' }
        setze(b, 0, 8, 0b10001011)
        setze(b, 43, 3, 5)
        setze(b, 48, 2, 2)
        setze(b, 50, 6, 51)
        assertNull(GpsLnav.almanac(GpsLnav.dataBits(bytes(b))!!))
    }

    @Test
    fun gesunderSatzWirdAlsPlausibelErkannt() {
        val a = GpsLnav.almanac(almanachSeite17())!!
        // Ein verdrehter Satz (falsche Skala) fällt durch die Plausibilität.
        assertFalse(a.copy(sqrtA = 90.0).isPlausible)
        assertFalse(a.copy(eccentricity = 0.5).isPlausible)
        assertFalse(a.copy(health = 1).isHealthy)
    }

    private fun bytes(content: CharArray): ByteArray {
        val text = String(content)
        val bits = StringBuilder()
        for (g in 0 until 10) {
            bits.append("00").append(text, g * 24, g * 24 + 24).append("000000")
        }
        return ByteArray(40) { i -> bits.substring(i * 8, i * 8 + 8).toInt(2).toByte() }
    }

    private fun setze(bits: CharArray, pos: Int, breite: Int, wert: Int) {
        val muster = Integer.toBinaryString(wert and ((1 shl breite) - 1)).padStart(breite, '0')
        for (i in 0 until breite) bits[pos + i] = muster[i]
    }

    /** Bogenmaß in Grad. */
    private fun grad(rad: Double): Double = rad * 180.0 / PI
}

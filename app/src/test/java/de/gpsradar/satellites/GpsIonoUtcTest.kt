package de.gpsradar.satellites

import de.gpsradar.satellites.model.GpsLnav
import de.gpsradar.satellites.model.NavCorrections
import de.gpsradar.satellites.model.RinexNav
import kotlin.math.pow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft Ionosphäre und UTC aus **GPS-Unterrahmen 4, Seite 18**.
 *
 * Anders als bei den anderen Systemen gibt es hier noch keinen echten
 * Mitschnitt: Der Rohbit-Mitschnitt schrieb GPS bisher nicht mit, und beim
 * ersten Versuch war der Bildschirm des Geräts gesperrt (dann liefert der
 * Empfänger gar nichts). Der Test baut die Meldung deshalb aus der in
 * IS-GPS-200, §20.3.3.5.1.7/8 beschriebenen Belegung zusammen und prüft, dass
 * der Dekoder genau diese Felder wiederfindet.
 *
 * **Noch offen:** die Gegenprobe am Gerät. Sie muss zeigen, dass alle
 * Satelliten dieselben acht Koeffizienten liefern (systemweit) und dass die
 * Schaltsekunde 18 ist.
 */
class GpsIonoUtcTest {

    /** 240 Datenbits auf die 40 Byte der Meldung verteilen (2 Füllbits, 6 Parität je Wort). */
    private fun gpsBytes(content: String): ByteArray {
        require(content.length == 240) { "240 Datenbits erwartet" }
        val bits = StringBuilder()
        for (g in 0 until 10) {
            bits.append("00").append(content, g * 24, g * 24 + 24).append("000000")
        }
        return ByteArray(40) { i -> bits.substring(i * 8, i * 8 + 8).toInt(2).toByte() }
    }

    /** Bitmuster schreiben (vorzeichenbehaftete Werte als Zweierkomplement). */
    private fun setze(bits: CharArray, pos: Int, breite: Int, wert: Long) {
        val muster = java.lang.Long.toBinaryString(wert and ((1L shl breite) - 1))
            .padStart(breite, '0')
        for (i in 0 until breite) bits[pos + i] = muster[i]
    }

    private fun seite18(): String {
        val b = CharArray(240) { '0' }
        setze(b, 0, 8, 0b10001011)         // Präambel, sonst wird die Meldung verworfen
        setze(b, 43, 3, 4)                 // Unterrahmen 4
        setze(b, 48, 2, 1)                 // Data-ID 1 (LNAV)
        setze(b, 50, 6, 56)                // Seite 18 hat die Kennung 56 (Tabelle 20-V)
        setze(b, 56, 8, 30)                // alpha0 = 30 * 2^-30
        setze(b, 64, 8, -14)               // alpha1
        setze(b, 72, 8, 41)                // alpha2
        setze(b, 80, 8, -1)                // alpha3
        setze(b, 88, 8, 75)                // beta0 = 75 * 2^11
        setze(b, 96, 8, -20)               // beta1
        setze(b, 104, 8, 16)               // beta2
        setze(b, 112, 8, 5)                // beta3
        // A1 steht in Wort 6, A0 erst in Wort 7/8 (Figur 20-1, Blatt 8).
        setze(b, 120, 24, 40)              // A1 = 40 * 2^-50
        setze(b, 144, 32, -9)              // A0 = -9 * 2^-30 (obere 24 + untere 8 Bit)
        setze(b, 176, 8, 78)               // tot in Schritten von 4096 s
        setze(b, 184, 8, 141)              // WNt (2445 mod 256)
        setze(b, 192, 8, 18)               // Schaltsekunde
        setze(b, 200, 8, 137)              // WNLSF
        setze(b, 208, 8, 7)                // DN
        setze(b, 216, 8, 18)               // künftige Schaltsekunde
        return String(b)
    }

    @Test
    fun seite18WirdErkannt() {
        val bits = GpsLnav.dataBits(gpsBytes(seite18()))!!
        assertTrue(GpsLnav.isIonoUtcPage(bits))
        assertEquals(4, GpsLnav.subframeNumber(bits))
        // Nicht 18: die Seitenkennung ist 48 + Seitennummer.
        assertEquals(56, GpsLnav.pageNumber(bits))
        assertEquals(1, GpsLnav.dataId(bits))
    }

    @Test
    fun unterrahmen5Seite18IstAlmanachUndKeineKorrektur() {
        val b = CharArray(240) { '0' }
        setze(b, 0, 8, 0b10001011)
        setze(b, 43, 3, 5)
        setze(b, 48, 2, 1)
        setze(b, 50, 6, 18)      // Unterrahmen 5, Seite 18 = Almanach von SV 18
        val bits = GpsLnav.dataBits(gpsBytes(String(b)))!!
        assertFalse(GpsLnav.isIonoUtcPage(bits))
        assertNull(GpsLnav.utc(bits))
    }

    @Test
    fun andereSeiteWirdAbgelehnt() {
        val b = CharArray(240) { '0' }
        setze(b, 0, 8, 0b10001011)
        setze(b, 43, 3, 4)
        setze(b, 48, 2, 1)
        setze(b, 50, 6, 12)      // Seite 12 ist Almanach
        val bits = GpsLnav.dataBits(gpsBytes(String(b)))!!
        assertFalse(GpsLnav.isIonoUtcPage(bits))
        assertNull(GpsLnav.ionosphere(bits))
        assertNull(GpsLnav.utc(bits))
    }

    @Test
    fun ionosphaereWirdGelesen() {
        val iono = GpsLnav.ionosphere(GpsLnav.dataBits(gpsBytes(seite18()))!!)!!
        assertEquals(30 * 2.0.pow(-30), iono.alpha[0], 1e-24)
        assertEquals(-14 * 2.0.pow(-30), iono.alpha[1], 1e-24)
        assertEquals(41 * 2.0.pow(-30), iono.alpha[2], 1e-24)
        assertEquals(-1 * 2.0.pow(-30), iono.alpha[3], 1e-24)
        assertEquals(75.0 * 2048, iono.beta[0], 1e-9)
        assertEquals(-20.0 * 2048, iono.beta[1], 1e-9)
        assertEquals(16.0 * 2048, iono.beta[2], 1e-9)
        assertEquals(5.0 * 2048, iono.beta[3], 1e-9)
        assertTrue(iono.isUsable)
    }

    @Test
    fun utcWirdGelesen() {
        val utc = GpsLnav.utc(GpsLnav.dataBits(gpsBytes(seite18()))!!)!!
        assertEquals(-9 * 2.0.pow(-30), utc.a0, 1e-24)
        assertEquals(40 * 2.0.pow(-50), utc.a1, 1e-24)
        assertEquals(18, utc.deltaLs)
        assertEquals(78 * 4096, utc.referenceSeconds)
        assertEquals(141, utc.weekNumber)
        assertEquals(137, utc.weekNumberLsf)
        assertEquals(7, utc.dayNumber)
        assertEquals(18, utc.deltaLsf)
    }

    /**
     * Mitschnitt vom 16.11.2026, PRN 13, Unterrahmen 4 Seite 18 — echte Bytes.
     *
     * Die Werte gelten systemweit und waren bei allen sechs empfangenen
     * Satelliten gleich (12, 13, 14, 15, 19, 20).
     */
    @Test
    fun echterMitschnittLiefertDieGeltendenWerte() {
        val roh = intArrayOf(
            34, 193, 141, 13, 10, 154, 76, 51, 30, 5, 191, 188, 63, 128, 207, 244,
            62, 191, 128, 61, 63, 255, 255, 234, 63, 255, 255, 234, 63, 211, 163,
            72, 4, 162, 65, 207, 4, 130, 71, 4
        )
        val bits = GpsLnav.dataBits(ByteArray(40) { roh[it].toByte() })!!
        val utc = GpsLnav.utc(bits)!!
        // A0 und A1 sind je ein LSB negativ — das übliche Rauschmuster.
        assertEquals(-1 * 2.0.pow(-30), utc.a0, 1e-24)
        assertEquals(-1 * 2.0.pow(-50), utc.a1, 1e-24)
        assertEquals(18, utc.deltaLs)
        assertEquals(319_488, utc.referenceSeconds)
        assertEquals(141, utc.weekNumber)
        assertEquals(137, utc.weekNumberLsf)
        assertEquals(7, utc.dayNumber)
        assertEquals(18, utc.deltaLsf)
        // Dieselbe Seitenkennung, die die App vorher verworfen hat.
        assertEquals(56, GpsLnav.pageNumber(bits))
    }

    @Test
    fun kopfzeileSchreibtDieSchaltsekunde() {
        val utc = GpsLnav.utc(GpsLnav.dataBits(gpsBytes(seite18()))!!)!!
        val datei = RinexNav.write(
            gps = emptyList(),
            corrections = NavCorrections(gpsUtc = utc)
        )
        val zeile = datei.lines().single { it.endsWith("LEAP SECONDS") }
        // Feste Spalten, wie sie RTKLIB liest (0/6/12/18) — nicht frei formatiert.
        assertEquals(18, zeile.substring(0, 6).trim().toInt())
        assertEquals(18, zeile.substring(6, 12).trim().toInt())
        assertEquals(137, zeile.substring(12, 18).trim().toInt())
        assertEquals(7, zeile.substring(18, 24).trim().toInt())
        // Leeres Kuerzel = GPS; bei nur einem Satz darf es keinen zweiten geben.
        assertEquals(1, datei.lines().count { it.endsWith("LEAP SECONDS") })
    }

    @Test
    fun beiDouVerdraengtDieGpsSchaltsekundeNicht() {
        val gps = GpsLnav.utc(GpsLnav.dataBits(gpsBytes(seite18()))!!)!!
        // BeiDou zaehlt ab 2006 und meldet deshalb 4 s statt 18 s.
        val bds = gps.copy(deltaLs = 4)
        val datei = RinexNav.write(
            gps = emptyList(),
            corrections = NavCorrections(gpsUtc = gps, bdsUtc = bds)
        )
        val zeilen = datei.lines().filter { it.endsWith("LEAP SECONDS") }
        assertEquals(1, zeilen.size)
        assertEquals(18, zeilen[0].substring(0, 6).trim().toInt())
    }
}

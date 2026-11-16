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
        setze(b, 50, 6, 18)                // Seite 18
        setze(b, 56, 8, 30)                // alpha0 = 30 * 2^-30
        setze(b, 64, 8, -14)               // alpha1
        setze(b, 72, 8, 41)                // alpha2
        setze(b, 80, 8, -1)                // alpha3
        setze(b, 88, 8, 75)                // beta0 = 75 * 2^11
        setze(b, 96, 8, -20)               // beta1
        setze(b, 104, 8, 16)               // beta2
        setze(b, 112, 8, 5)                // beta3
        setze(b, 120, 24, 0)               // A0 obere 24 Bit
        setze(b, 144, 8, 0)                // A0 untere 8 Bit
        setze(b, 152, 16, 0)               // A1 obere 16 Bit
        setze(b, 168, 8, 0)                // A1 untere 8 Bit
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
        assertEquals(18, GpsLnav.pageNumber(bits))
        assertEquals(1, GpsLnav.dataId(bits))
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
        assertEquals(0.0, utc.a0, 0.0)
        assertEquals(0.0, utc.a1, 0.0)
        assertEquals(18, utc.deltaLs)
        assertEquals(78 * 4096, utc.referenceSeconds)
        assertEquals(141, utc.weekNumber)
        assertEquals(137, utc.weekNumberLsf)
        assertEquals(7, utc.dayNumber)
        assertEquals(18, utc.deltaLsf)
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

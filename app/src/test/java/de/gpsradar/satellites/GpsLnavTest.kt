package de.gpsradar.satellites

import de.gpsradar.satellites.model.GpsEphemeris
import de.gpsradar.satellites.model.GpsLnav
import de.gpsradar.satellites.model.RinexNav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft den Dekoder für GPS-Navigationsnachrichten gegen **echte** Mitschnitte.
 *
 * Die 40-Byte-Meldungen unten sind am 14.11.2026 mit dem Note 9 (Android 10)
 * aufgenommen worden, alle mit `status = 1` (Parität in Ordnung). Die
 * Sollwerte stammen aus dem Vergleich mit der BRDC des Tages: i0, e und sqrtA
 * stimmen auf 10⁻⁷, die Abweichungen bei Ω0 und M0 entsprechen genau dem
 * Zeitunterschied der Ephemeridensätze (der Satellit sendete einen Satz mit
 * toe = 14:00, die BRDC enthielt 12:00).
 *
 * Zusätzlich wird die erzeugte RINEX-Zeile Zeichen für Zeichen mit einer Datei
 * verglichen, die RTKLIB selbst eingelesen und nachgerechnet hat: daraus ergab
 * sich ein Satellitenort mit 0,78 m Abstand zur BRDC. Wer hier etwas an den
 * Spaltenbreiten ändert, bricht diese Kette.
 */
class GpsLnavTest {

    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun bits(text: String): String {
        val b = GpsLnav.dataBits(hex(text))
        assertNotNull("Rohdaten nicht dekodierbar", b)
        return b!!
    }

    // --- Mitschnitte vom 14.11.2026 -------------------------------------

    private val svid10U1 =
        "22C18C062DCFA98018C400201B182D4E1226B4442FE78A7D221F015D0722B7880000" +
            "16FE30166650"
    private val svid10U2 =
        "22C18C062DCF2A74073EC48C0A7AF4EE0B87FD433F0D41B60029450801C5A855034C" +
            "E34C22B79644"
    private val svid10U3 =
        "22C18C062DCF4B04001DFC241339D18A001D4A3C24D4BCF20A4A69062476FFBB3FEA" +
            "755307002EA4"
    private val svid17U2 =
        "22C18C06ADE28AF8178256E70BBB1EC826CEAC650216018E1DB3FE1604A8687B037E" +
            "0FDB22B79FA0"
    private val svid17U3 =
        "22C18C062DE2AB50000FE70C1A6A4028000E498D3D03C927067735410FDBFCD63FEB" +
            "309B17BCE88C"

    @Test
    fun unterrahmenNummerUndZeitWerdenErkannt() {
        assertEquals(1, GpsLnav.subframeNumber(bits(svid10U1)))
        assertEquals(2, GpsLnav.subframeNumber(bits(svid10U2)))
        assertEquals(3, GpsLnav.subframeNumber(bits(svid10U3)))
        assertEquals(2, GpsLnav.subframeNumber(bits(svid17U2)))
        assertEquals(3, GpsLnav.subframeNumber(bits(svid17U3)))
        // 12:21:42 GPS-Zeit am 14.11.2026
        assertEquals(562_902L, GpsLnav.secondsOfWeek(bits(svid10U2)))
    }

    @Test
    fun uhrWirdAusUnterrahmen1Gelesen() {
        val uhr = GpsLnav.clock(bits(svid10U1))
        assertNotNull(uhr)
        uhr!!
        assertEquals(396, uhr.week) // im Signal nur 10 Bit breit
        assertEquals(0, uhr.ura)
        assertEquals(0, uhr.health)
        assertEquals(28, uhr.iodc)
        assertEquals(568_800, uhr.tocSeconds)
        assertEquals(-4.856111481786e-04, uhr.af0, 1e-15)
        assertEquals(1.034550223267e-11, uhr.af1, 1e-16)
        assertEquals(0.0, uhr.af2, 0.0)
        assertEquals(2.328306436539e-09, uhr.tgd, 1e-15)
    }

    @Test
    fun bahnTeilAStimmtMitDerBrdc() {
        val a = GpsLnav.orbitA(bits(svid10U2))
        assertNotNull(a)
        a!!
        assertEquals(28, a.iode)
        assertEquals(5153.650171279907, a.sqrtA, 1e-6)
        assertEquals(0.011723669711500406, a.eccentricity, 1e-12)
        assertEquals(-1.100044004379684, a.m0, 1e-9)
        assertEquals(-39.4375, a.crs, 0.0)
        assertEquals(3.832659645658176e-09, a.deltaN, 1e-15)
        assertEquals(-1.8086284399032593e-06, a.cuc, 1e-14)
        assertEquals(3.3788383007049563e-06, a.cus, 1e-14)
        assertEquals(568_800, a.toeSeconds)
        assertEquals(4.0, a.fitHours, 0.0)
    }

    @Test
    fun bahnTeilBStimmtMitDerBrdc() {
        val b = GpsLnav.orbitB(bits(svid10U3))
        assertNotNull(b)
        b!!
        assertEquals(28, b.iode) // gleich wie in Unterrahmen 2
        assertEquals(0.9958722178009, b.i0, 1e-9)
        assertEquals(-0.3853260594285, b.omega0, 1e-9)
        assertEquals(-2.244035630257, b.omega, 1e-9)
        assertEquals(329.28125, b.crc, 0.0)
        assertEquals(2.216547727585e-07, b.cic, 1e-15)
        assertEquals(2.1792948246e-07, b.cis, 1e-15)
        assertEquals(-7.878542458631e-09, b.omegaDot, 1e-15)
        assertEquals(1.642925577302e-11, b.idot, 1e-16)
    }

    @Test
    fun zweiterSatellitWirdEbenfallsRichtigGelesen() {
        val a = GpsLnav.orbitA(bits(svid17U2))!!
        val b = GpsLnav.orbitB(bits(svid17U3))!!
        assertEquals(94, a.iode)
        assertEquals(94, b.iode)
        assertEquals(5153.746213912964, a.sqrtA, 1e-6)
        assertEquals(0.012625216506421566, a.eccentricity, 1e-12)
        assertEquals(0.9560592, b.i0, 1e-6)
        assertEquals(-1.0492968, b.omega, 1e-6)
        assertEquals(206.875, b.crc, 0.0)
    }

    @Test
    fun unbrauchbareMeldungenWerdenAbgewiesen() {
        assertNull("39 Byte", GpsLnav.dataBits(ByteArray(39)))
        assertNull("41 Byte", GpsLnav.dataBits(ByteArray(41)))
        // Erste Byte beschädigt -> Präambel fehlt
        val kaputt = hex(svid10U1)
        kaputt[0] = 0x00
        assertNull(GpsLnav.dataBits(kaputt))
    }

    @Test
    fun widerspruechlicherIodeWirdNichtZusammengebaut() {
        val uhr = GpsLnav.clock(bits(svid10U1))!!
        val a = GpsLnav.orbitA(bits(svid10U2))!!
        val b = GpsLnav.orbitB(bits(svid10U3))!!
        assertNotNull(GpsEphemeris.of(10, uhr, a, b, 562_926))
        // Unterrahmen 3 des anderen Satelliten hat IODE 94 statt 28
        val fremd = GpsLnav.orbitB(bits(svid17U3))!!
        assertNull(GpsEphemeris.of(10, uhr, a, fremd, 562_926))
    }

    @Test
    fun wochenzahlWirdAufVollenWertGebracht() {
        assertEquals(2444, GpsLnav.expandWeek(396))
        assertEquals(2444, GpsLnav.expandWeek(2444))
        assertEquals(2445, GpsLnav.expandWeek(1421))
    }

    @Test
    fun rinexZeileEntsprichtDerVonRtklibGeprueftenDatei() {
        val satz = GpsEphemeris.of(
            10,
            GpsLnav.clock(bits(svid10U1))!!,
            GpsLnav.orbitA(bits(svid10U2))!!,
            GpsLnav.orbitB(bits(svid10U3))!!,
            GpsLnav.secondsOfWeek(bits(svid10U1)).toInt()
        )!!
        val erwartet = listOf(
            "G10 2026 11 14 14 00 00-4.856111481786E-04 1.034550223267E-11 0.000000000000E+00",
            "     2.800000000000E+01-3.943750000000E+01 3.832659645658E-09-1.100044004380E+00",
            "    -1.808628439903E-06 1.172366971150E-02 3.378838300705E-06 5.153650171280E+03",
            "     5.688000000000E+05 2.216547727585E-07-3.853260594285E-01 2.179294824600E-07",
            "     9.958722178009E-01 3.292812500000E+02-2.244035630257E+00-7.878542458631E-09",
            "     1.642925577302E-11 1.000000000000E+00 2.444000000000E+03 0.000000000000E+00",
            "     0.000000000000E+00 0.000000000000E+00 2.328306436539E-09 2.800000000000E+01",
            "     5.629260000000E+05 4.000000000000E+00 0.000000000000E+00 0.000000000000E+00"
        ).joinToString("\n") + "\n"

        assertEquals(erwartet, RinexNav.record(satz))
    }

    @Test
    fun kopfzeilenStehenAnDerRichtigenStelle() {
        val inhalt = RinexNav.write(emptyList())
        val zeilen = inhalt.lines().filter { it.isNotEmpty() }
        // Die Kennung steht rechtsbündig in Spalte 61-80 (hier 0-basiert ab 60).
        assertEquals("RINEX VERSION / TYPE", zeilen[0].substring(60).trim())
        assertEquals("END OF HEADER", zeilen[4].substring(60).trim())
        assertTrue("Kopfzeile zu kurz", zeilen.all { it.length == 80 })
    }
}

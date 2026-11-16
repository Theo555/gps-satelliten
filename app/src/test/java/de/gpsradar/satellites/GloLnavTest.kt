package de.gpsradar.satellites

import de.gpsradar.satellites.model.GloEphemeris
import kotlin.math.pow
import de.gpsradar.satellites.model.GloLnav
import de.gpsradar.satellites.model.RinexNav
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft den Dekoder für GLONASS-Navigationsstrings gegen **echte** Mitschnitte.
 *
 * Die 11-Byte-Meldungen unten sind am 14.11.2026 mit dem S20 FE aufgenommen
 * worden (17:05–17:10 Uhr Ortszeit = 16:05 UTC), alle mit `status = 1`
 * (Parität in Ordnung). Die Sollwerte stammen aus dem Vergleich mit der
 * BRDC-Datei des Tages:
 *
 * - **Vorzeichen und Betrag statt Zweierkomplement.** Mit Zweierkomplement
 *   ergäben die Positionsfelder bei vier der fünf Satelliten einen Orbitradius
 *   von 33 000–42 000 km; mit der richtigen Regel liegt er bei 25 509 km, wie es
 *   für GLONASS sein muss. Genau 2¹⁵ km Unterschied — das halbe Feld.
 * - **τn und γn** stimmen mit der BRDC überein (τn auf 5·10⁻⁹ s, γn auf die
 *   letzte Stufe 2⁻⁴⁰). Ein Fehler beim Vorzeichen fällt hier sofort auf, weil
 *   τn einer der wenigen Werte ist, die sich zwischen zwei Sätzen kaum ändern.
 * - **tb** (Gültigkeitsbeginn) ist Index 77 aus String 2, also 16:00 UTC. Die
 *   aus der BRDC integrierte Bahn trifft die dekodierten Werte an genau diesem
 *   Zeitpunkt auf unter 1 km — die Zeitrechnung stimmt damit auch.
 */
class GloLnavTest {

    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    private fun bits(text: String): String {
        val b = GloLnav.dataBits(hex(text))
        assertNotNull("Rohdaten nicht dekodierbar", b)
        return b!!
    }

    private fun utcMillis(year: Int, month: Int, day: Int, hour: Int, minute: Int, second: Int): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        cal.clear()
        cal.set(year, month - 1, day, hour, minute, second)
        return cal.timeInMillis
    }

    // --- Mitschnitte vom 14.11.2026, je Satellit Strings 1 bis 4 ---------

    private val sat1 = listOf(
        "084C7876200808A20A4660", "10CD04B7EA8033FBCE60D0",
        "1C00B5837E148C1CF62CA0", "2465756A00000230642EC8"
    )
    private val sat2 = listOf(
        "084C70FF2704A3DF4E7F28", "10CD052E9DD86AE08A85A0",
        "180034695694561AFBB9B0", "2405F1A606E08230644D58"
    )
    private val sat11 = listOf(
        "084C58810A3C4794EF3C40", "10CD0170F1F82968D2A948",
        "1C00B0722C0C95CAE9AC50", "2001336300000130656CC8"
    )
    private val sat19 = listOf(
        "084C5D899364E1A3A6A0F8", "10CD04524ED04ABEB270E0",
        "1C013009F99C166A159880", "24270F0200000430666978"
    )
    private val sat20 = listOf(
        "084C7D05920C508140D750", "10CD0447E8940AEFD18030",
        "1C00B152898C8F1DF619D8", "247EA26200000530668C58"
    )

    private val alleSaetze = listOf(1 to sat1, 2 to sat2, 11 to sat11, 19 to sat19, 20 to sat20)

    @Test
    fun stringNummerStimmtMitDerMeldungUeberein() {
        for ((_, saetze) in alleSaetze) {
            saetze.forEachIndexed { index, hexText ->
                // index 0 ist String 1
                assertEquals(index + 1, GloLnav.stringId(bits(hexText)))
            }
        }
    }

    @Test
    fun achsenErgebenEineGlonassBahn() {
        for ((svid, saetze) in alleSaetze) {
            val x = GloLnav.axis(bits(saetze[0]))
            val y = GloLnav.axis(bits(saetze[1]))
            val z = GloLnav.axis(bits(saetze[2]))
            val radius = Math.sqrt(
                x.positionKm * x.positionKm + y.positionKm * y.positionKm +
                    z.positionKm * z.positionKm
            )
            // Nur mit Vorzeichen + Betrag: mit Zweierkomplement wären es
            // 33 000 bis 42 000 km.
            assertTrue(
                "R$svid: Bahnradius $radius km",
                radius in GloEphemeris.ORBIT_RADIUS_MIN_KM..GloEphemeris.ORBIT_RADIUS_MAX_KM
            )
            val speed = Math.sqrt(
                x.velocityKmPerSecond * x.velocityKmPerSecond +
                    y.velocityKmPerSecond * y.velocityKmPerSecond +
                    z.velocityKmPerSecond * z.velocityKmPerSecond
            )
            assertTrue("R$svid: Bahngeschwindigkeit $speed km/s", speed in 2.0..5.8)
        }
    }

    @Test
    fun negativesVorzeichenStehtImErstenBitDesFeldes() {
        // R19 hat ein negatives X. Zweierkomplement ergäbe -31089,4 km; der
        // echte Wert ist -1678,6 km (Betrag in den restlichen 26 Bit).
        val x = GloLnav.axis(bits(sat19[0]))
        assertEquals(-1678.603515625, x.positionKm, 1e-9)
        assertEquals(-3.074810028076172, x.velocityKmPerSecond, 1e-12)
        assertEquals(-2.7939677238464355e-09, x.accelerationKmPerSecond2, 0.0)
    }

    @Test
    fun uhrversatzStimmtMitDerBrdc() {
        // BRDC 14.11.2026: R19 führt 7,449556142092e-05 s als Uhrversatz; das
        // Signal trägt denselben Betrag mit umgekehrtem Vorzeichen (RINEX
        // schreibt -τn).
        val uhr = GloLnav.clock(bits(sat19[3]))
        assertEquals(-7.449556142092e-05, uhr.tauSeconds, 1e-8)
        assertEquals(0, uhr.ageOfDataDays)
        assertEquals(19, uhr.satelliteSlot)
        assertEquals(1049, uhr.dayNumber) // Tag im Vierjahreszyklus, 2026
    }

    @Test
    fun frequenzabweichungStimmtMitDerBrdc() {
        // BRDC-Feld 2 in Einheiten von 2⁻⁴⁰: R01 1, R02 0, R11 1, R19 2, R20 1.
        val erwartet = mapOf(1 to 1L, 2 to 0L, 11 to 1L, 19 to 2L, 20 to 1L)
        for ((svid, saetze) in alleSaetze) {
            val einheiten = Math.round(GloLnav.gamma(bits(saetze[2])) * 1_099_511_627_776.0)
            assertEquals("R$svid", erwartet[svid], einheiten)
        }
    }

    @Test
    fun gesundheitUndSlotWerdenGelesen() {
        for ((svid, saetze) in alleSaetze) {
            assertEquals(0, GloLnav.health(bits(saetze[1]))) // gesund laut BRDC
            assertEquals(svid, GloLnav.clock(bits(saetze[3])).satelliteSlot)
        }
    }

    @Test
    fun tbWirdInUtcUmgerechnet() {
        // Index 77 heißt 19:00 Moskauer Zeit = 16:00 UTC (1-basiert gezählt).
        assertEquals(77, GloLnav.tbIndex(bits(sat19[1])))
        assertEquals(57_600, GloLnav.tbSecondsUtc(77))
        assertEquals(0, GloLnav.tbSecondsUtc(13)) // 00:00 UTC
        assertEquals(85_500, GloLnav.tbSecondsUtc(12)) // 23:45 UTC
    }

    @Test
    fun epocheKommtAusTbUndEmpfangszeit() {
        val empfangen = utcMillis(2026, 11, 14, 16, 5, 30)
        assertEquals(
            utcMillis(2026, 11, 14, 16, 0, 0),
            GloLnav.epochMillis(GloLnav.tbSecondsUtc(77), empfangen)
        )
        // Kurz nach Mitternacht UTC gehört derselbe tb-Index zum Vortag.
        val kurzNachMitternacht = utcMillis(2026, 11, 15, 0, 20, 0)
        assertEquals(
            utcMillis(2026, 11, 14, 20, 45, 0),
            GloLnav.epochMillis(GloLnav.tbSecondsUtc(96), kurzNachMitternacht)
        )
    }

    @Test
    fun rahmenzeitWirdAusString1Gelesen() {
        // 19:05:30 Moskauer Zeit — der Satz gilt aber ab 16:00 UTC (tb).
        assertEquals(68_730, GloLnav.frameSeconds(bits(sat19[0])))
    }

    @Test
    fun unbrauchbareMeldungenWerdenAbgewiesen() {
        assertNull("10 Byte", GloLnav.dataBits(ByteArray(10)))
        assertNull("12 Byte", GloLnav.dataBits(ByteArray(12)))
        assertEquals(GloLnav.DATA_BITS, bits(sat19[0]).length)
    }

    @Test
    fun rinexSatzEntsprichtDemAufbauVonRtklib() {
        val e = GloEphemeris(
            satelliteSlot = 19,
            tbMillis = GloLnav.epochMillis(
                GloLnav.tbSecondsUtc(GloLnav.tbIndex(bits(sat19[1]))),
                utcMillis(2026, 11, 14, 16, 5, 30)
            ),
            frameSeconds = GloLnav.frameSeconds(bits(sat19[0])),
            tauSeconds = GloLnav.clock(bits(sat19[3])).tauSeconds,
            gamma = GloLnav.gamma(bits(sat19[2])),
            health = GloLnav.health(bits(sat19[1])),
            ageOfDataDays = GloLnav.clock(bits(sat19[3])).ageOfDataDays,
            frequencyChannel = null,
            x = GloLnav.axis(bits(sat19[0])),
            y = GloLnav.axis(bits(sat19[1])),
            z = GloLnav.axis(bits(sat19[2]))
        )
        assertTrue(e.hasPlausibleOrbit)

        val erwartet = listOf(
            "R19 2026 11 14 16 00 00 7.449835538864E-05 1.818989403546E-12 5.760000000000E+05",
            "    -1.678603515625E+03-3.074810028076E+00-2.793967723846E-09 0.000000000000E+00",
            "     1.100278808594E+04-6.430301666260E-01 9.313225746155E-10 0.000000000000E+00",
            "     2.295233740234E+04 7.792949676514E-02 0.000000000000E+00 0.000000000000E+00"
        ).joinToString("\n") + "\n"

        assertEquals(erwartet, RinexNav.gloRecord(e))
    }

    @Test
    fun unplausibleBahnLandetNichtInDerDatei() {
        val x = GloLnav.axis(bits(sat19[0]))
        val e = GloEphemeris(
            satelliteSlot = 19,
            tbMillis = utcMillis(2026, 11, 14, 16, 0, 0),
            frameSeconds = 68_430,
            tauSeconds = 0.0,
            gamma = 0.0,
            health = 0,
            ageOfDataDays = 0,
            frequencyChannel = null,
            // 200 000 km statt 19 100 km Höhe
            x = x.copy(positionKm = 200_000.0),
            y = GloLnav.axis(bits(sat19[1])),
            z = GloLnav.axis(bits(sat19[2]))
        )
        assertTrue(!e.hasPlausibleOrbit)
        assertTrue(RinexNav.write(emptyList(), listOf(e)).contains("END OF HEADER"))
        assertTrue(!RinexNav.write(emptyList(), listOf(e)).contains("R19"))
    }

    @Test
    fun gesundheitNimmtNurDasHoechsteBit() {
        // `Bn` belegt Bit 7–9 des Strings, aber die ICD (§4.4) sagt: „The user
        // navigation equipment analyzes the only one MSB of this word … does not
        // consider both second and third bits.“ Bis zum 16.11.2026 nahm die App
        // alle drei Bit — ein gesetztes Reservebit hätte eine Störung gemeldet.
        fun mitBn(muster: String): String {
            val b = StringBuilder("0".repeat(GloLnav.DATA_BITS))
            for (i in 0 until 3) b.setCharAt(6 + i, muster[i])
            return b.toString()
        }
        assertEquals(0, GloLnav.health(mitBn("000")))
        assertEquals(0, GloLnav.health(mitBn("001")))
        assertEquals(0, GloLnav.health(mitBn("011")))
        assertEquals(1, GloLnav.health(mitBn("100")))
        assertEquals(1, GloLnav.health(mitBn("111")))
        // Gegenprobe am echten Mitschnitt: Bn = 000.
        assertEquals(0, GloLnav.health(bits(sat1[1])))
    }

    /**
     * Zeitbezug GLONASS→UTC aus String 5.
     *
     * **Synthetisch**, weil der Rohbit-Mitschnitt GLONASS bisher auslässt: Die
     * Felder werden nach GLONASS-ICD 5.1, Tabelle 4.11 gesetzt (τc auf Bit
     * 38–69, N4 auf 32–36, NA auf 70–80) und der Dekoder muss sie wiederfinden.
     * Die Gegenprobe am Gerät steht noch aus.
     */
    @Test
    fun zeitbezugAusString5() {
        fun string5(
            tauC: String = "0".repeat(32),
            n4: String = "0".repeat(5),
            na: String = "0".repeat(11)
        ): String {
            val b = StringBuilder("0".repeat(GloLnav.DATA_BITS))
            // Stringnummer 5 an Bit 2–5 (unsere Helfer zählen ab ICD-Bit 1).
            b.setCharAt(1, '0'); b.setCharAt(2, '1'); b.setCharAt(3, '0'); b.setCharAt(4, '1')
            for (i in tauC.indices) b.setCharAt(37 + i, tauC[i])
            for (i in n4.indices) b.setCharAt(31 + i, n4[i])
            for (i in na.indices) b.setCharAt(69 + i, na[i])
            return b.toString()
        }

        // 2⁻³¹ s: Vorzeichenbit + 31 Betragsbits.
        val eins = "1".padStart(32, '0')          // +1 LSB
        val minus = "1" + "0".repeat(31)          // Vorzeichen gesetzt, Betrag 0
        val u = GloLnav.utc(string5(tauC = eins))!!
        assertEquals(2.0.pow(-31), u.a0, 1e-20)
        assertTrue(u.isUsable)

        // Vorzeichen und Betrag, nicht Zweierkomplement: eine gesetzte
        // Vorzeichenstelle bei Betrag 0 ergibt 0, nicht -2³¹.
        assertEquals(0.0, GloLnav.utc(string5(tauC = minus))!!.a0, 0.0)

        // Betrag 5 mit Vorzeichen → -5 · 2⁻³¹.
        val negFuenf = "1" + "0000000000000000000000000000101"
        assertEquals(-5 * 2.0.pow(-31), GloLnav.utc(string5(tauC = negFuenf))!!.a0, 1e-20)

        // NA = 700 (Tag im Vierjahresintervall), N4 bleibt ungenutzt.
        assertEquals(700, GloLnav.almanacDayNumber(string5(na = "01010111100")))

        // Andere Strings liefern nichts.
        val b = StringBuilder("0".repeat(GloLnav.DATA_BITS))
        b.setCharAt(1, '0'); b.setCharAt(4, '0')   // Stringnummer 0
        assertNull(GloLnav.utc(b.toString()))
    }
}

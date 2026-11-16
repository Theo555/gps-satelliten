package de.gpsradar.satellites

import de.gpsradar.satellites.model.BdsEphemeris
import de.gpsradar.satellites.model.BdsLnav
import de.gpsradar.satellites.model.RinexNav
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft den BeiDou-D1-Dekoder gegen **echte** Meldungen vom Sportplatz
 * (16.11.2026, 09:48–09:53 Uhr, S20 FE).
 *
 * Die Sollwerte sind nicht geschätzt: Sie stammen aus der BRDC-Datei desselben
 * Tages (`BRDC00WRD_S_20263200000_01D_MN.rnx`), und **alle 16 Bahn- und
 * Uhrwerte von fünf Satelliten stimmen bis zur letzten Stelle** damit überein.
 * Das ist der eigentliche Beweis für die Bitpositionen — ein Dekoder, der nur
 * plausible Zahlen liefert, wäre wertlos.
 *
 * Drei Fallen stecken in diesen Tests:
 *
 * - **Alle 30 Bit je Wort.** Wer wie bei GPS nur die 24 Datenbits nimmt,
 *   verschiebt jedes Feld hinter der ersten Wortgrenze. `toc` (Bit 73) und
 *   `sqrtA` (Bit 250) würden dann beide falsch — die Tests scheitern sofort.
 * - **Halb gefüllte Unterrahmen.** Der Empfänger liefert „Subframe 1"-Meldungen,
 *   deren Nutzteil ab etwa Bit 90 ein Füllmuster enthält. `a0` und `a1` lägen
 *   dann bei allen Satelliten gleich. Die Prüfung `toc == toe` sortiert sie aus
 *   ([halbGefuellterUnterrahmenWirdVerworfen]).
 * - **Winkel in Halbkreisen.** Der Faktor heißt in RTKLIB `SC2RAD` und ist **π**,
 *   nicht π/180. Ein erster Vergleichslauf lag deshalb bei `i0` um den Faktor
 *   180 daneben, obwohl alle Bitpositionen stimmten.
 */
class BdsLnavTest {

    private fun hex(text: String): ByteArray {
        val teile = text.trim().split(" ")
        return ByteArray(teile.size) { teile[it].toInt(16).toByte() }
    }

    /** Subframe 1–3 zu einem Datensatz verbinden, wie es der Sammler tut. */
    private fun satz(prn: Int, sf1: String, sf2: String, sf3: String): BdsEphemeris? {
        val b1 = BdsLnav.dataBits(hex(sf1)) ?: return null
        val b2 = BdsLnav.dataBits(hex(sf2)) ?: return null
        val b3 = BdsLnav.dataBits(hex(sf3)) ?: return null
        val clock = BdsLnav.clock(b1) ?: return null
        val a = BdsLnav.orbitA(b2) ?: return null
        val b = BdsLnav.orbitB(b3) ?: return null
        return BdsEphemeris.of(prn, clock, a, b, BdsLnav.secondsOfWeek(b1).toInt())
    }

    // C14, gesendet um 118 260 s der BeiDou-Woche 1089
    private val c14Sf1 = "38 90 11 CD 37 D0 10 F5 08 82 38 A9 10 13 C4 B7 3C 1E 0E 05 3B 08 " +
        "12 53 3E 50 EF F1 30 00 71 4B 05 98 C0 49 04 25 A1 21"
    private val c14Sf2 = "38 90 21 CF 37 E8 A0 64 12 F9 AC DC 1D 76 AF 72 07 F4 00 79 07 08 8F " +
        "0C 02 5B 01 91 04 90 FC D1 03 3A 51 4C 14 D7 44 B0"
    private val c14Sf3 = "38 90 31 C0 38 01 C2 B0 00 4F 83 80 19 A6 00 14 02 5F FD 99 29 65 FF " +
        "B2 2A 5F A5 F0 00 46 9A B2 1A 72 62 46 22 02 06 E7"

    // C22, gesendet um 118 140 s
    private val c22Sf1 = "38 90 11 CD 35 F0 10 45 08 82 38 A9 10 08 62 EB 06 1E 0E C5 3B 08 " +
        "12 53 3E 50 EF F1 30 00 1D 41 30 91 FF 7B 3F 58 01 63"
    private val c22Sf2 = "38 90 21 CF 36 08 96 E1 37 FA 1A 05 22 F9 8E 90 20 90 01 A3 06 2D 48 " +
        "6A 02 2C 71 82 08 F3 FC B3 1A CA 51 8C 14 5C 30 D6"
    private val c22Sf3 = "38 90 31 C0 36 21 C2 10 00 50 C9 D1 29 4D 00 46 01 0F FD 99 2C F2 00 " +
        "CD 0D C0 0C 57 00 5C 79 E9 04 31 19 42 16 E5 5A 69"

    // C36, gesendet um 118 140 s
    private val c36Sf1 = "38 90 11 CD 35 F0 10 45 08 82 38 A9 10 33 1C 70 31 1E 0E B5 3B 08 " +
        "12 53 3E 50 EF F1 30 00 0D 44 21 47 DF 85 3C B0 41 A4"
    private val c36Sf2 = "38 90 21 CF 36 08 B4 ED 19 00 20 8A 13 31 E0 AC 33 58 01 73 02 93 0A " +
        "4F 04 4A D0 61 2E F9 00 8F 01 FA 51 DC 14 B5 FC C7"
    private val c36Sf3 = "38 90 31 C0 36 21 C2 10 00 4D 04 8A 1B 79 80 D1 01 77 FD B9 2B 68 00 " +
        "F0 0A E1 1F A8 2A F0 5C 55 1F 57 5A C4 2F 15 FC 37"

    /** Ein „Subframe 1", dessen Nutzteil ab Bit 90 ein Füllmuster tragen. */
    private val c14Halb = "38 90 11 CD 37 D0 10 F5 08 82 38 A9 4B AD C0 DE 4B AD C0 DE 4B AD " +
        "C0 DE 4B AD C0 DE 4B AD C0 DE 4B AD C0 DE 4B AD C0 DE"

    @Test
    fun rahmenkennungUndSendezeit() {
        assertEquals(1, BdsLnav.frameId(BdsLnav.dataBits(hex(c14Sf1))!!))
        assertEquals(2, BdsLnav.frameId(BdsLnav.dataBits(hex(c14Sf2))!!))
        assertEquals(3, BdsLnav.frameId(BdsLnav.dataBits(hex(c14Sf3))!!))
        assertEquals(118260L, BdsLnav.secondsOfWeek(BdsLnav.dataBits(hex(c14Sf1))!!))
    }

    @Test
    fun c14StimmtMitDerBrdcUeberein() {
        val e = satz(14, c14Sf1, c14Sf2, c14Sf3)
        assertNotNull(e)
        e!!
        assertEquals(1089, e.clock.week)
        assertEquals(0, e.clock.health)
        assertEquals(1, e.clock.aode)
        assertEquals(115200, e.clock.tocSeconds)
        assertEquals(115200, e.toeSeconds)
        assertEquals(118260, e.transmissionSeconds)
        assertEquals(-2.2754748351871967e-04, e.clock.a0, 1e-18)
        assertEquals(7.543299318513164e-12, e.clock.a1, 1e-24)
        assertEquals(0.0, e.clock.a2, 0.0)
        // tgd1 == tgd2 steht in der BRDC selbst so — kein Dekodierfehler.
        assertEquals(3.16e-08, e.clock.tgd1, 1e-20)
        assertEquals(3.16e-08, e.clock.tgd2, 1e-20)
        assertEquals(5.3660827688872814e-05, e.orbitA.eccentricity, 1e-17)
        assertEquals(5282.651277542114, e.orbitA.sqrtA, 1e-9)
        assertEquals(-63.203125, e.orbitA.crs, 0.0)
        assertEquals(274.25, e.orbitA.crc, 0.0)
        assertEquals(-3.0170194804668427e-06, e.orbitA.cuc, 1e-18)
        assertEquals(4.492700099945068e-06, e.orbitA.cus, 1e-18)
        assertEquals(3.6637240373834367e-09, e.orbitA.deltaN, 1e-21)
        assertEquals(-0.996038147821148, e.orbitA.m0, 1e-12)
        assertEquals(0.9757748028343513, e.orbitB.i0, 1e-12)
        assertEquals(3.4924596548080444e-08, e.orbitB.cic, 1e-18)
        assertEquals(-8.102506399154663e-08, e.orbitB.cis, 1e-18)
        assertEquals(0.05415195786354885, e.orbitB.omega0, 1e-12)
        assertEquals(1.8730867947033976, e.orbitB.omega, 1e-12)
        assertEquals(-6.885286799832121e-09, e.orbitB.omegaDot, 1e-21)
        assertEquals(-6.500270762368741e-11, e.orbitB.idot, 1e-23)
        // Rund 27 900 km — BeiDou-MEO, keine GEO-Bahn.
        assertEquals(27906.4, e.orbitRadiusKm, 0.5)
    }

    @Test
    fun c22StimmtMitDerBrdcUeberein() {
        val e = satz(22, c22Sf1, c22Sf2, c22Sf3)
        assertNotNull(e)
        e!!
        assertEquals(4.5408483128994703e-04, e.clock.a0, 1e-18)
        assertEquals(-1.1937117960769683e-12, e.clock.a1, 1e-24)
        assertEquals(1.3400000000000001e-08, e.clock.tgd1, 1e-20)
        assertEquals(5.354071035981178e-04, e.orbitA.eccentricity, 1e-17)
        assertEquals(5282.636253356934, e.orbitA.sqrtA, 1e-9)
        assertEquals(0.9914139559364235, e.orbitB.i0, 1e-12)
        assertEquals(1.168213308270533, e.orbitA.m0, 1e-12)
        assertEquals(-57.3125, e.orbitA.crs, 0.0)
        assertEquals(291.796875, e.orbitA.crc, 0.0)
        assertEquals(0.07092583507714931, e.orbitB.omega0, 1e-12)
        assertEquals(0.8631947614694567, e.orbitB.omega, 1e-12)
        assertEquals(-6.7227800307729024e-09, e.orbitB.omegaDot, 1e-21)
        assertEquals(8.571785620706033e-12, e.orbitB.idot, 1e-24)
    }

    @Test
    fun c36StimmtMitDerBrdcUeberein() {
        val e = satz(36, c36Sf1, c36Sf2, c36Sf3)
        assertNotNull(e)
        e!!
        assertEquals(2.0629889331758022e-04, e.clock.a0, 1e-18)
        assertEquals(-6.023626042406249e-12, e.clock.a1, 1e-24)
        assertEquals(-2.07e-08, e.clock.tgd1, 1e-20)
        assertEquals(5.079221446067095e-04, e.orbitA.eccentricity, 1e-17)
        assertEquals(5282.647214889526, e.orbitA.sqrtA, 1e-9)
        assertEquals(0.9451444918046639, e.orbitB.i0, 1e-12)
        assertEquals(1.2546093440407802, e.orbitA.m0, 1e-12)
        assertEquals(2.147850206056641, e.orbitB.omega0, 1e-12)
        assertEquals(-0.507024470644352, e.orbitB.omega, 1e-12)
        assertEquals(4.123386041293798e-09, e.orbitA.deltaN, 1e-21)
        assertEquals(0.484375, e.orbitA.crs, 0.0)
        assertEquals(187.890625, e.orbitA.crc, 0.0)
    }

    @Test
    fun halbGefuellterUnterrahmenWirdVerworfen() {
        // Uhr lesbar, aber toc (115056) passt nicht zur Bahn (115200) — genau der
        // Fall, in dem ein Füllmuster sonst als Uhrversatz durchginge.
        val b1 = BdsLnav.dataBits(hex(c14Halb))!!
        val clock = BdsLnav.clock(b1)
        assertNotNull(clock)
        assertEquals(115056, clock!!.tocSeconds)
        // Der Wert, den alle Satelliten gemeinsam hätten:
        assertEquals(-9.737780783325434e-04, clock.a0, 1e-18)

        val b2 = BdsLnav.dataBits(hex(c14Sf2))!!
        val b3 = BdsLnav.dataBits(hex(c14Sf3))!!
        assertNull(
            BdsEphemeris.of(
                14, clock, BdsLnav.orbitA(b2)!!, BdsLnav.orbitB(b3)!!, 118260
            )
        )
    }

    @Test
    fun falscheLaengeUndFehlendePraembelWerdenAbgelehnt() {
        assertNull(BdsLnav.dataBits(ByteArray(38)))
        assertNull(BdsLnav.dataBits(ByteArray(0)))
        // GPS-Präambel im selben Raster: muss abgelehnt werden, sonst deutet der
        // BeiDou-Dekoder GPS-Meldungen als BeiDou.
        val gps = hex("8B 1F 2E 3D 4C 5B 6A 79 88 97 A6 B5 C4 D3 E2 F1 00 1F 2E 3D " +
            "4C 5B 6A 79 88 97 A6 B5 C4 D3 E2 F1 00 1F 2E 3D 4C 5B 6A")
        assertNull(BdsLnav.dataBits(gps))
    }

    @Test
    fun uhrUndBahnMuessenZumSelbenZeitpunktGehoeren() {
        val e = satz(22, c22Sf1, c22Sf2, c22Sf3)!!
        assertTrue(e.hasConsistentToc)
        assertEquals(e.clock.tocSeconds, e.toeSeconds)
        assertFalse(e.orbitRadiusKm < 20000.0 || e.orbitRadiusKm > 45000.0)
    }

    @Test
    fun rinexSatzHatAchtZeilenUndDieEpocheInBdz() {
        val e = satz(14, c14Sf1, c14Sf2, c14Sf3)!!
        val zeilen = RinexNav.bdsRecord(e).lines().filter { it.isNotEmpty() }
        assertEquals(8, zeilen.size)
        // Woche 1089, Sekunde 115 200 = 2026-11-16 08:00 in BeiDou-Zeit.
        assertEquals("C14 2026 11 16 08 00 00", zeilen[0].substring(0, 23))
        assertEquals(
            "C14 2026 11 16 08 00 00" + String.format(
                Locale.US, "%19.12E%19.12E%19.12E", e.clock.a0, e.clock.a1, e.clock.a2
            ),
            zeilen[0]
        )
        // Zeile 2: AODE, Crs, delN, M0
        assertTrue(zeilen[1].startsWith("    "))
        assertEquals(
            "    " + String.format(
                Locale.US, "%19.12E%19.12E%19.12E%19.12E",
                e.clock.aode.toDouble(), e.orbitA.crs, e.orbitA.deltaN, e.orbitA.m0
            ),
            zeilen[1]
        )
        // Zeile 6: iDot, Kennung, Woche, Reserve
        assertTrue(zeilen[5].contains(String.format(Locale.US, "%19.12E", 1089.0)))
        // Zeile 7: URA als Meterwert (Index 0 = 2,0 m), Gesundheit, TGD1, TGD2
        assertEquals(
            "    " + String.format(
                Locale.US, "%19.12E%19.12E%19.12E%19.12E",
                2.0, e.clock.health.toDouble(), e.clock.tgd1, e.clock.tgd2
            ),
            zeilen[6]
        )
        // Zeile 8: Sendezeit und AODC
        assertEquals(
            "    " + String.format(
                Locale.US, "%19.12E%19.12E%19.12E%19.12E",
                118260.0, e.clock.aode.toDouble(), 0.0, 0.0
            ),
            zeilen[7]
        )
    }

    @Test
    fun navDateiNenntBeidouImKopf() {
        val e = satz(14, c14Sf1, c14Sf2, c14Sf3)!!
        val datei = RinexNav.write(emptyList(), emptyList(), listOf(e))
        assertTrue(datei.contains("BDS"))
        assertTrue(datei.contains("C14 2026 11 16 08 00 00"))
        assertEquals(1, datei.lines().count { it.startsWith("C") })
    }
}

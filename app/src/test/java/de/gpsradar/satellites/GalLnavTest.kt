package de.gpsradar.satellites

import de.gpsradar.satellites.model.GalEphemeris
import de.gpsradar.satellites.model.GalLnav
import de.gpsradar.satellites.model.NavCorrections
import de.gpsradar.satellites.model.RinexNav
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft den Galileo-I/NAV-Dekoder gegen die BRDC desselben Tages.
 *
 * Die Bytefolgen sind echte Meldungen des S20 FE vom Sportplatz, 16.11.2026,
 * 09:49 Uhr, Satellit E07. Verglichen wird mit dem BRDC-Satz zu IODnav 68 und
 * toe 117000 aus `brdc320.rnx` — **alle** geprüften Werte stimmen dort bis zur
 * letzten Stelle.
 *
 * Der wichtigste Test ist [teilnahtWirdEntfernt]: Ohne die dort beschriebene
 * Korrektur stimmen zwar IODnav, Uhrzeit, M₀ und e, aber √A, Crs, i̇ und a₁
 * liegen daneben — genau das Bild, das den Fehler am 16.11. verraten hat.
 */
class GalLnavTest {

    private fun hex(text: String): ByteArray {
        val rein = text.replace(" ", "")
        return ByteArray(rein.length / 2) {
            rein.substring(it * 2, it * 2 + 2).toInt(16).toByte()
        }
    }

    // Worttyp 1–5 von E07 (je 29 Byte, Byte 0 ist der Worttyp)
    private val w1 = hex(
        "01 11 07 9E 0D 22 B4 A8 00 20 AB F0 AA 04 EB DA B7 E1 E0 F3 05 0A AA AA 97 9A 07 52 B0"
    )
    private val w2 = hex(
        "02 11 07 9D 8E 87 49 E2 43 57 C7 3A 85 D1 60 03 19 2C 88 37 C8 2A AA AA 91 19 F8 60 40"
    )
    private val w3 = hex(
        "03 11 3F F0 CE 48 73 BD 4F C3 67 C6 68 BC 6F 36 BD A0 A6 2F F8 1A AA AA 90 8E 74 82 F0"
    )
    private val w4 = hex(
        "04 11 07 00 11 FF FD 1E 7B FF 24 2E 57 FF E4 70 14 53 CC 92 F4 BA AA AA 9B 69 8C 32 B0"
    )
    private val w5 = hex(
        "05 38 9E AC 01 70 01 E0 88 0B 1A 39 B2 AA AA AA AB C1 34 94 3D 5A AA AA 9A 90 3D C0 40"
    )
    private val w6 = hex(
        "06 00 00 00 00 00 00 00 12 18 8D 89 E2 43 A6 C0 A3 94 67 B8 14 CA AA AA 9D 43 E7 C2 F0"
    )

    // Dieselbe Wortart von E30, aber mit IODnav 66 — für den Negativtest.
    private val w2Iod66 = hex(
        "02 10 9D 0B EF 2F 09 C3 A2 6E 88 F5 23 9C E0 00 D0 00 00 00 00 0A AA AA 97 3D 1F 90 40"
    )

    private fun inhalt(data: ByteArray): String =
        GalLnav.contentBits(data) ?: error("Nutzteil fehlt")

    @Test
    fun worttypStehtInByteNull() {
        assertEquals(1, GalLnav.wordType(w1))
        assertEquals(2, GalLnav.wordType(w2))
        assertEquals(3, GalLnav.wordType(w3))
        assertEquals(4, GalLnav.wordType(w4))
        assertEquals(5, GalLnav.wordType(w5))
    }

    @Test
    fun teilnahtWirdEntfernt() {
        // 29 Byte = 232 Bit, davon 8 Bit Worttyp und 2 Bit Teilnaht.
        assertEquals(222, inhalt(w1).length)
        // Der IODnav steht danach an Position 0: 68 passt zur BRDC.
        assertEquals(68, GalLnav.orbitA(inhalt(w1))!!.iodNav)
    }

    @Test
    fun zuKurzeMeldungWirdAbgelehnt() {
        assertNull(GalLnav.contentBits(ByteArray(16)))
        assertNotNull(GalLnav.contentBits(ByteArray(GalLnav.MIN_PAYLOAD_BYTES)))
    }

    @Test
    fun worttyp1StimmtMitBrmsUeberein() {
        val a = GalLnav.orbitA(inhalt(w1))!!
        assertEquals(68, a.iodNav)
        assertEquals(117_000, a.toeSeconds)
        assertEquals(0.3223953700898003, a.m0, 1e-12)
        assertEquals(0.00024926476180553436, a.eccentricity, 1e-15)
        assertEquals(5440.616901397705, a.sqrtA, 1e-9)
    }

    @Test
    fun worttyp2StimmtMitBrmsUeberein() {
        val b = GalLnav.orbitB(inhalt(w2))!!
        assertEquals(68, b.iodNav)
        assertEquals(0.7476456503280297, b.omega0, 1e-12)
        assertEquals(0.9703437297054962, b.i0, 1e-12)
        assertEquals(0.7096665765985929, b.omega, 1e-12)
        assertEquals(4.285892810353007e-12, b.idot, 1e-24)
    }

    @Test
    fun worttyp3LiefertBahnstoerungenUndSisa() {
        val c = GalLnav.orbitC(inhalt(w3))!!
        assertEquals(68, c.iodNav)
        assertEquals(-5.5570171863568695e-09, c.omegaDot, 1e-20)
        assertEquals(3.09084303173291e-09, c.deltaN, 1e-20)
        assertEquals(-5.127862095832825e-06, c.cuc, 1e-18)
        assertEquals(6.495043635368347e-06, c.cus, 1e-18)
        assertEquals(205.0625, c.crc, 1e-9)
        assertEquals(-112.40625, c.crs, 1e-9)
        // SISA steht im Signal als Index; die BRDC desselben Tages nennt 3,12 m.
        assertEquals(107, c.sisaIndex)
        assertEquals(3.12, GalLnav.sisaMetres(c.sisaIndex), 1e-12)
    }

    @Test
    fun worttyp4LiefertUhrUndEigeneKennung() {
        val k = GalLnav.clock(inhalt(w4))!!
        assertEquals(68, k.iodNav)
        assertEquals(7, k.svid)
        assertEquals(117_000, k.tocSeconds)
        assertEquals(-0.00010481791105121374, k.af0, 1e-18)
        assertEquals(-2.6290081223123707e-12, k.af1, 1e-24)
        assertEquals(0.0, k.af2, 0.0)
        assertEquals(3.166496753692627e-08, k.cic, 1e-20)
        assertEquals(-5.587935447692871e-09, k.cis, 1e-20)
    }

    @Test
    fun worttyp5LiefertGruppenlaufzeitenWocheUndSendezeit() {
        val h = GalLnav.health(inhalt(w5))!!
        assertEquals(3.4924596548080444e-09, h.bgdE5aE1, 1e-21)
        assertEquals(3.958120942115784e-09, h.bgdE5bE1, 1e-21)
        assertEquals(0, h.health)
        // Die Woche im Signal zählt 1024 tiefer als die GPS-Woche.
        assertEquals(2445, h.week)
        assertEquals(118_165, h.transmissionSeconds)
    }

    @Test
    fun vollstaendigerSatzAusFuenfWorttypen() {
        val eph = satz()!!
        assertEquals(7, eph.prn)
        assertEquals(68, eph.iodNav)
        assertEquals(117_000, eph.toeSeconds)
        assertEquals(2445, eph.week)
        // Galileo fliegt bei rund 29 600 km.
        assertEquals(29_600.0, eph.orbitRadiusKm, 5.0)
        assertTrue(eph.hasConsistentClockReference)
        assertTrue(eph.hasPlausibleOrbit)
    }

    @Test
    fun fremdeGenerationWirdVerworfen() {
        // Worttyp 2 von E30 gehört zu IODnav 66 und darf sich nicht mit den
        // Worttypen von E07 (68) zu einem Satz verbinden lassen.
        val eph = GalEphemeris.of(
            prn = 7,
            orbitA = GalLnav.orbitA(inhalt(w1))!!,
            orbitB = GalLnav.orbitB(inhalt(w2Iod66))!!,
            orbitC = GalLnav.orbitC(inhalt(w3))!!,
            clock = GalLnav.clock(inhalt(w4))!!,
            health = GalLnav.health(inhalt(w5))!!
        )
        assertNull(eph)
    }

    @Test
    fun fremdeKennungWirdVerworfen() {
        // Worttyp 4 nennt selbst E07; unter der Nummer 8 wäre der Satz falsch.
        assertNull(
            GalEphemeris.of(
                prn = 8,
                orbitA = GalLnav.orbitA(inhalt(w1))!!,
                orbitB = GalLnav.orbitB(inhalt(w2))!!,
                orbitC = GalLnav.orbitC(inhalt(w3))!!,
                clock = GalLnav.clock(inhalt(w4))!!,
                health = GalLnav.health(inhalt(w5))!!
            )
        )
    }

    @Test
    fun rinexSatzHatAchtZeilenUndDieRichtigenWerte() {
        val zeilen = RinexNav.galRecord(satz()!!).trimEnd('\n').split('\n')
        assertEquals(8, zeilen.size)
        assertTrue(zeilen[0].startsWith("E07 2026 11 16 08 30 00"))
        // Zeile 2: IODnav, Crs, deltaN, M0
        assertEquals(68.0, wert(zeilen[1], 0), 1e-9)
        assertEquals(-112.40625, wert(zeilen[1], 1), 1e-9)
        // Zeile 6: iDot, Datenquelle, Woche, Reserve
        assertEquals(512.0, wert(zeilen[5], 1), 0.0)
        assertEquals(2445.0, wert(zeilen[5], 2), 0.0)
        // Zeile 7: SISA in Metern, Gesundheit, BGD E5a/E1, BGD E5b/E1
        assertEquals(3.12, wert(zeilen[6], 0), 1e-12)
        assertEquals(0.0, wert(zeilen[6], 1), 0.0)
    }

    @Test
    fun kopfzeileNenntGalileo() {
        val datei = RinexNav.write(
            gps = emptyList(), gal = listOf(satz()!!)
        )
        assertTrue(datei.contains("M: Mixed"))
        assertTrue(datei.contains("GAL"))
    }

    @Test
    fun worttyp5TraegtAuchDieIonosphaere() {
        // Die ersten 41 Bit desselben Wortes sind die NeQuick-Koeffizienten.
        // Werte aus dem Mitschnitt; alle sechs Satelliten liefern dieselben.
        val iono = GalLnav.ionosphere(inhalt(w5))!!
        assertEquals(113.0, iono.ai0, 1e-12)
        assertEquals(-0.33203125, iono.ai1, 1e-12)
        assertEquals(0.000701904296875, iono.ai2, 1e-15)
        assertEquals(0, iono.disturbanceFlags)
    }

    @Test
    fun worttyp6TraegtDenUtcBezug() {
        val utc = GalLnav.utc(inhalt(w6))!!
        assertEquals(0.0, utc.a0, 0.0)
        assertEquals(0.0, utc.a1, 0.0)
        // Die geltende Schaltsekunde — derselbe Wert wie LEAP SECONDS in der
        // BRDC des Tages.
        assertEquals(18, utc.deltaLs)
        assertEquals(86_400, utc.referenceSeconds)
        assertEquals(141, utc.weekNumber)
        assertEquals(137, utc.weekNumberLsf)
        assertEquals(7, utc.dayNumber)
        assertEquals(18, utc.deltaLsf)
    }

    @Test
    fun kopfzeileNenntIonosphaereUndZeitbezug() {
        val datei = RinexNav.write(
            gps = emptyList(),
            gal = listOf(satz()!!),
            corrections = NavCorrections(
                galIono = GalLnav.ionosphere(inhalt(w5)),
                galUtc = GalLnav.utc(inhalt(w6))
            )
        )
        assertTrue(datei.contains("IONOSPHERIC CORR"))
        assertTrue(datei.contains("TIME SYSTEM CORR"))
        assertTrue(datei.contains("GAUT"))
        // Galileo hat drei Koeffizienten und das Kuerzel ohne Buchstaben.
        assertTrue(datei.contains("GAL   "))
    }

    @Test
    fun kopfzeileOhneKorrekturenBleibtSchlank() {
        val datei = RinexNav.write(gps = emptyList(), gal = listOf(satz()!!))
        assertTrue(!datei.contains("IONOSPHERIC CORR"))
        assertTrue(!datei.contains("TIME SYSTEM CORR"))
    }

    /** Ein Wert aus einer RINEX-Zeile: vier Werte zu je 19 Zeichen nach 4 Füllzeichen. */
    private fun wert(zeile: String, index: Int): Double =
        zeile.substring(4 + index * 19, 4 + (index + 1) * 19).trim().toDouble()

    private fun satz(): GalEphemeris? = GalEphemeris.of(
        prn = 7,
        orbitA = GalLnav.orbitA(inhalt(w1))!!,
        orbitB = GalLnav.orbitB(inhalt(w2))!!,
        orbitC = GalLnav.orbitC(inhalt(w3))!!,
        clock = GalLnav.clock(inhalt(w4))!!,
        health = GalLnav.health(inhalt(w5))!!
    )
}

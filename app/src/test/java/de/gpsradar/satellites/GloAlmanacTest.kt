package de.gpsradar.satellites

import de.gpsradar.satellites.model.GloLnav
import kotlin.math.abs
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft den GLONASS-Almanach.
 *
 * ## Herkunft der Bitlagen
 *
 * GLONASS-ICD 5.1, Tabelle 4.11 (Lage im String) und 4.9 (Breiten, Skalen).
 * RTKLIB dekodiert für GLONASS **keinen** Almanach, deshalb lag als zweite
 * Umsetzung gnss-sdr daneben (`GLONASS_L1_L2_CA.h`): alle zwölf Feldpositionen
 * stimmen auf das Bit.
 *
 * ## Warum trotz Geräteprüfung synthetische Strings
 *
 * Am Gerät geprüft wurde der **Inhalt** (S20 FE, 18.11.2026, Aufnahme im Büro
 * am Fenster — für die Werte selbst unerheblich, für Aussagen über die Ausbeute
 * nicht): `ΔT` ergibt für alle Satelliten 40 544 s Periode, `tλ`, `ω` und `Hn`
 * liegen in ihren Bereichen. Die Tests hier prüfen die **Mechanik**: Breiten,
 * Skalen, Vorzeichenbehandlung und das Zusammensetzen der beiden Hälften. Ein
 * Mitschnitt ist dafür zu grob — er zeigt keine Grenzwerte.
 */
class GloAlmanacTest {

    private fun leeresFeld(): CharArray = CharArray(GloLnav.DATA_BITS) { '0' }

    /** Wert MSB-first ab `pos` (1-basiert) einsetzen. */
    private fun setzen(feld: CharArray, pos: Int, breite: Int, wert: Long) {
        for (i in 0 until breite) {
            val bit = (wert shr (breite - 1 - i)) and 1L
            feld[pos - 1 + i] = if (bit == 1L) '1' else '0'
        }
    }

    /** Vorzeichen plus Betrag, wie GLONASS es überträgt. */
    private fun setzenMitVorzeichen(feld: CharArray, pos: Int, breite: Int, wert: Long) {
        feld[pos - 1] = if (wert < 0) '1' else '0'
        setzen(feld, pos + 1, breite - 1, abs(wert))
    }

    private fun kennung(svid: Int, lambda: Double, deltaI: Double, e: Double,
                        tauN: Long, gesund: Int): String {
        val f = leeresFeld()
        setzen(f, 2, 4, 6)              // Stringnummer 6 (gerade)
        setzen(f, 6, 1, gesund.toLong()) // Cn
        setzen(f, 7, 2, 1)               // Mn: GLONASS-M
        setzen(f, 9, 5, svid.toLong())   // nA
        setzenMitVorzeichen(f, 14, 10, tauN)
        setzenMitVorzeichen(f, 24, 21, Math.round(lambda / PI * (1 shl 20)))
        setzenMitVorzeichen(f, 45, 18, Math.round(deltaI / PI * (1 shl 20)))
        setzen(f, 63, 15, Math.round(e * (1 shl 20)))
        return String(f)
    }

    private fun bahn(kanal: Long, knotenSekunden: Long, deltaT: Long,
                     deltaTDot: Long, omega: Double): String {
        val f = leeresFeld()
        setzen(f, 2, 4, 7)               // Stringnummer 7 (ungerade)
        setzenMitVorzeichen(f, 6, 16, Math.round(omega / PI * (1 shl 15)))
        // Skalenfaktor 2⁻⁵ s heißt 32 Schritte je Sekunde.
        setzen(f, 22, 21, knotenSekunden * 32)
        // Skalenfaktor 2⁻⁹ s heißt 512 Schritte je Sekunde.
        setzenMitVorzeichen(f, 43, 22, deltaT * 512)
        setzenMitVorzeichen(f, 65, 7, deltaTDot)
        setzen(f, 72, 5, kanal)
        return String(f)
    }

    /** Rahmen 1–4 tragen je fünf Sätze, Rahmen 5 die Plätze 21–24. */
    @Test
    fun platznummerAusRahmenUndString() {
        assertEquals(1, GloLnav.almanacSlot(1, 6))
        assertEquals(1, GloLnav.almanacSlot(1, 7))
        assertEquals(5, GloLnav.almanacSlot(1, 14))
        assertEquals(5, GloLnav.almanacSlot(1, 15))
        assertEquals(6, GloLnav.almanacSlot(2, 6))
        assertEquals(21, GloLnav.almanacSlot(5, 6))
        assertEquals(24, GloLnav.almanacSlot(5, 12))

        // Rahmen 5, letztes Paar: dort stehen B1/B2/KP, kein Almanach.
        assertNull(GloLnav.almanacSlot(5, 14))
        assertNull(GloLnav.almanacSlot(5, 15))
        // Strings 1–5 sind keine Almanachstrings.
        assertNull(GloLnav.almanacSlot(1, 5))
    }

    /** Negative Kanäle: −1 bis −7 stehen als 31 bis 25 (Tabelle 4.10). */
    @Test
    fun frequenzkanalNachTabelle410() {
        assertEquals(-1, GloLnav.frequencyChannel(31))
        assertEquals(-7, GloLnav.frequencyChannel(25))
        assertEquals(0, GloLnav.frequencyChannel(0))
        assertEquals(13, GloLnav.frequencyChannel(13))
    }

    @Test
    fun geradeHaelfteLiefertKennung() {
        val teil = GloLnav.almanacIdent(kennung(12, 1.2, 0.01, 0.001, tauN = 5, gesund = 0))!!

        assertEquals(12, teil.svid)
        assertEquals(1.2, teil.longitude!!, 1e-5)
        assertEquals(0.01, teil.inclinationCorrection!!, 1e-5)
        assertEquals(0.001, teil.eccentricity!!, 1e-6)
        assertEquals(5.0 / 262_144.0, teil.clockCorrection!!, 1e-12)
        assertEquals(1, teil.satelliteType)
        assertEquals(0, teil.health)
        // Die Bahnwerte fehlen noch — sie stehen im ungeraden String.
        assertNull(teil.nodeSeconds)
        assertFalse(teil.isComplete)
    }

    @Test
    fun ungeradeHaelfteLiefertBahn() {
        val teil = GloLnav.almanacOrbit(
            bahn(kanal = 25, knotenSekunden = 3008, deltaT = -1024, deltaTDot = 3, omega = -2.0)
        )!!

        assertEquals(-7, teil.frequencyChannel)
        assertEquals(3008.0, teil.nodeSeconds!!, 1e-9)
        assertEquals(-1024.0, teil.draconianPeriodCorrection!!, 1e-9)
        assertEquals(3.0 / 16_384.0, teil.draconianPeriodRate!!, 1e-12)
        // Feldauflösung 2⁻¹⁵ Halbkreise — die Rundung ist Teil der Prüfung.
        assertEquals(-2.0, teil.argumentOfPerigee!!, 1e-4)
        // Die Platznummer steht nur in der anderen Hälfte.
        assertNull(teil.svid)
    }

    /** Beide Hälften ergeben einen vollständigen, plausiblen Satz. */
    @Test
    fun beideHaelftenErgebenEinenSatz() {
        val kennung = GloLnav.almanacIdent(kennung(12, 1.2, 0.01, 0.001, tauN = 5, gesund = 0))!!
        val bahn = GloLnav.almanacOrbit(
            bahn(kanal = 28, knotenSekunden = 3008, deltaT = -1024, deltaTDot = 3, omega = -2.0)
        )!!

        val satz = kennung.merge(bahn)

        assertEquals(12, satz.svid)
        assertEquals(-4, satz.frequencyChannel)
        assertTrue(satz.isComplete)
        assertTrue(satz.isPlausible)
        // 43200 − 1024 = 42176 s Draconische Periode.
        assertEquals(42_176.0, satz.draconianPeriodSeconds!!, 1e-9)
        // 63° plus Abweichung: 0,01 rad sind 0,573°.
        assertEquals(63.573, Math.toDegrees(satz.inclination!!), 0.01)
    }

    /** Ein verdrehter Satz wird abgewiesen, nicht angezeigt. */
    @Test
    fun unplausibleWerteWerdenAbgewiesen() {
        val kennung = GloLnav.almanacIdent(kennung(12, 1.2, 0.01, 0.001, tauN = 5, gesund = 0))!!
        // Knotenzeit 50 000 s — die ICD lässt höchstens 44 100 s zu (der Tag
        // reicht nicht weiter). Der Rohwert passt noch in seine 21 Bit, die
        // Zahl ist also dekodierbar, aber physikalisch unmöglich.
        val bahn = GloLnav.almanacOrbit(
            bahn(kanal = 28, knotenSekunden = 50_000, deltaT = -2656, deltaTDot = 3, omega = -2.0)
        )!!

        val satz = kennung.merge(bahn)
        assertTrue(satz.isComplete)
        assertFalse(satz.isPlausible)
    }

    /**
     * Die Draconische Periode selbst kann den Prüfbereich nicht verlassen:
     * `ΔT` ist ein 22-Bit-Vorzeichen-Betragsfeld mit 2⁻⁹ s, deckt also genau
     * ±3 600 s ab — dieselbe Grenze, die die ICD nennt. Ein Testfall dafür
     * wäre erlogen; geprüft wird deshalb die Knotenzeit (siehe oben).
     */
    @Test
    fun draconischePeriodeBleibtImFeldbereich() {
        val weit = GloLnav.almanacOrbit(
            bahn(kanal = 1, knotenSekunden = 1000, deltaT = 4_095, deltaTDot = 0, omega = 0.0)
        )!!
        assertEquals(43200.0 + 4095.0, weit.draconianPeriodSeconds!!, 1e-9)
    }

    /** Andere Strings liefern keine Almanachhälften. */
    @Test
    fun fremdeStringsLiefernNichts() {
        val f = leeresFeld()
        setzen(f, 2, 4, 5)   // String 5 trägt UTC, keinen Almanach
        val string5 = String(f)

        assertNull(GloLnav.almanacIdent(string5))
        assertNull(GloLnav.almanacOrbit(string5))
    }

    private fun bytes(text: String): ByteArray =
        text.split(' ').map { it.toInt(16).toByte() }.toByteArray()

    /**
     * Ein echtes Paar aus dem Rohbit-Mitschnitt — die Gegenprobe zu allen
     * synthetischen Tests.
     *
     * Aufnahme: S20 FE, 18.11.2026, Rahmen 3, Strings 6 und 7 (drinnen am
     * Bürofenster — die Werte sind bitgeprüft, nur die Ausbeute wäre draußen
     * höher). Rahmen 3 trägt die Plätze 11–15, das erste Paar gehört also
     * Platz 11 — und genau den nennt der gerade String selbst (`nA = 11`).
     * Beide Kennungen stimmen überein, der Satz ist damit vollständig und
     * plausibel.
     *
     * Die entscheidende Zahl ist [GloAlmanac.draconianPeriodSeconds]:
     * 43 200 − 2 655,5 = 40 544,5 s. GLONASS umläuft die Erde in 11 h 15 min
     * 44 s = 40 544 s. Ein einziger falscher Skalenfaktor verschiebt diese Zahl
     * um Größenordnungen.
     */
    @Test
    fun echtesPaarVomGeraetErgibtEinenSatz() {
        val ident = GloLnav.almanacIdent(
            GloLnav.dataBits(bytes("35 58 00 0E 7F 10 91 A0 0F 62 00"))!!
        )!!
        val bahn = GloLnav.almanacOrbit(
            GloLnav.dataBits(bytes("3F 58 DC D3 BA F4 BF 0B B8 01 50"))!!
        )!!

        assertEquals(11, ident.svid)
        assertEquals(0.1778968, ident.longitude!!, 1e-7)
        assertEquals(0.0279232, ident.inclinationCorrection!!, 1e-7)
        assertEquals(0.000469208, ident.eccentricity!!, 1e-9)
        assertEquals(0.0, ident.clockCorrection!!, 1e-12)
        assertEquals(1, ident.satelliteType)
        assertEquals(1, ident.health)

        assertEquals(0, bahn.frequencyChannel)
        assertEquals(39_543.34375, bahn.nodeSeconds!!, 1e-6)
        assertEquals(-2_655.5215, bahn.draconianPeriodCorrection!!, 1e-4)
        assertEquals(-0.001708984, bahn.draconianPeriodRate!!, 1e-9)
        assertEquals(-2.6287637, bahn.argumentOfPerigee!!, 1e-7)

        val satz = ident.merge(bahn)
        assertTrue(satz.isComplete)
        assertTrue(satz.isPlausible)
        assertEquals(40_544.4785, satz.draconianPeriodSeconds!!, 1e-4)
        // 63° + 1,60°.
        assertEquals(64.60, Math.toDegrees(satz.inclination!!), 0.01)
    }
}

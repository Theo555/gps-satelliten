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
 * ## Warum synthetische Strings
 *
 * Am Gerät ist der Almanach bisher nicht mitgeschnitten (der Rohbit-Mitschnitt
 * lässt GLONASS L1 C/A aus). Die Tests setzen die Felder an den Stellen, die
 * die ICD nennt, und prüfen Breite, Skala, Vorzeichenbehandlung und die
 * Zusammensetzung der beiden Hälften. Gegen eine echte Aufnahme prüfen lässt
 * sich das erst mit einem Mitschnitt der Strings 6–15.
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
        setzen(f, 22, 21, knotenSekunden / 32)
        setzenMitVorzeichen(f, 43, 22, deltaT / 512)
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
        assertEquals(3008, teil.nodeSeconds)
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
        // Draconische Periode 43200 + 20480 = 63680 s — weit außerhalb des
        // Bereichs, den die ICD zulässt (±3600 s um 43200).
        val bahn = GloLnav.almanacOrbit(
            bahn(kanal = 28, knotenSekunden = 3008, deltaT = 20_480, deltaTDot = 3, omega = -2.0)
        )!!

        val satz = kennung.merge(bahn)
        assertTrue(satz.isComplete)
        assertFalse(satz.isPlausible)
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
}

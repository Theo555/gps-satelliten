package de.gpsradar.satellites

import android.location.GnssNavigationMessage
import de.gpsradar.satellites.gps.navDumpLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft den Rohbit-Mitschnitt für die Systeme, die noch keinen Dekoder haben.
 *
 * Anlass: Für BeiDou und Galileo stammen die Feldpositionen aus RTKLIB und
 * GNSS-SDR und müssen gegen **echte** Bits geprüft werden — dafür braucht es die
 * Meldungen im Original. Der Mitschnitt ist also die Voraussetzung für die
 * nächsten beiden Dekoder.
 *
 * Zwei Eigenschaften sind wichtiger, als sie aussehen:
 *
 * - **GPS, GLONASS und Galileo werden nicht mitgeschrieben.** Ihre Meldungen
 *   kommen im Sekundentakt je Satellit; ein zehnminütiger Feldtest würde damit
 *   Tausende Zeilen schreiben, in denen das Gesuchte untergeht. Für alle drei
 *   gibt es inzwischen einen Dekoder.
 * - **Negative Bytes müssen als zweistelliges Hex erscheinen.** In Kotlin ist
 *   `Byte` vorzeichenbehaftet; ohne `and 0xFF` wird aus `0xAB` die Zeichenkette
 *   `FFFFFFAB` und der Mitschnitt ist unbrauchbar.
 */
class NavDumpTest {

    private fun hex(text: String): ByteArray =
        ByteArray(text.length / 2) { text.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    @Test
    fun gpsWirdNichtMitgeschrieben() {
        assertNull(
            navDumpLine(
                seq = 1, type = GnssNavigationMessage.TYPE_GPS_L1CA, svid = 24,
                messageId = 1, submessageId = 1, status = 1,
                data = hex("8B1F2E3D")
            )
        )
    }

    @Test
    fun glonassWirdNichtMitgeschrieben() {
        assertNull(
            navDumpLine(
                seq = 1, type = GnssNavigationMessage.TYPE_GLO_L1CA, svid = 19,
                messageId = 1, submessageId = 1, status = 1,
                data = hex("4F2A9C")
            )
        )
    }

    @Test
    fun beidouWirdMitgeschrieben() {
        val line = navDumpLine(
            seq = 7, type = GnssNavigationMessage.TYPE_BDS_D1, svid = 8,
            messageId = 3, submessageId = 3, status = 1,
            data = hex("AB01FF")
        )
        assertNotNull(line)
        assertEquals("n=7 type=BeiDou D1 svid=8 msg=3 sub=3 status=1 data=AB 01 FF", line)
    }

    @Test
    fun galileoAusgewerteteWorttypenBleibenDraussen() {
        // Seit dem 16.11.2026 gibt es einen I/NAV-Dekoder fuer die Worttypen
        // 1–6 (Bahn, Uhr, Iono, UTC); die wuerden die Ausgabe nur ueberschwemmen.
        assertNull(
            navDumpLine(
                seq = 8, type = GnssNavigationMessage.TYPE_GAL_I, svid = 11,
                messageId = 2, submessageId = 2, status = 1,
                data = hex("02FE")
            )
        )
    }

    /**
     * Die Almanachswörter 7–10 werden dagegen mitgeschrieben: Für sie gab es am
     * 17.11.2026 noch keinen Dekoder, und ohne sie war der Almanach im
     * Mitschnitt unsichtbar — obwohl das Gerät ihn sendet (6 Sats, 109
     * Meldungen in zehn Minuten).
     */
    @Test
    fun galileoAlmanachsworteWerdenMitgeschrieben() {
        val line = navDumpLine(
            seq = 9, type = GnssNavigationMessage.TYPE_GAL_I, svid = 5,
            messageId = 24, submessageId = 7, status = 1,
            data = hex("07D5")
        )

        assertNotNull(line)
        assertTrue(line!!.contains("type=Galileo I/NAV"))
        assertTrue(line.contains("sub=7"))
    }

    @Test
    fun ohneDatenKeineZeile() {
        assertNull(
            navDumpLine(
                seq = 1, type = GnssNavigationMessage.TYPE_BDS_D1, svid = 8,
                messageId = 1, submessageId = 1, status = 1, data = null
            )
        )
        assertNull(
            navDumpLine(
                seq = 1, type = GnssNavigationMessage.TYPE_BDS_D1, svid = 8,
                messageId = 1, submessageId = 1, status = 1, data = ByteArray(0)
            )
        )
    }

    @Test
    fun statusBleibtInDerZeile() {
        // Die Paritätsangabe entscheidet später, ob ein Bitmitschnitt überhaupt
        // verwertbar ist – sie darf nicht nur im Kopf des Lesers stehen.
        val line = navDumpLine(
            seq = 1, type = GnssNavigationMessage.TYPE_BDS_D1, svid = 8,
            messageId = 1, submessageId = 1,
            status = GnssNavigationMessage.STATUS_PARITY_REBUILT,
            data = hex("01")
        )
        assertEquals(2, GnssNavigationMessage.STATUS_PARITY_REBUILT)
        assertTrue(line!!.contains("status=2"))
    }
}

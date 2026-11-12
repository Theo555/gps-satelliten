package de.gpsradar.satellites

import de.gpsradar.satellites.model.ExportNames
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Der Zeitstempel im Dateinamen ist der einzige Teil eines Exports, den man
 * nachher noch sieht, ohne die Datei zu öffnen. Er muss deshalb sortierbar,
 * eindeutig und unabhängig von der Gerätesprache sein.
 *
 * Anlass (12.11.2026): Bis dahin hieß jeder Export `rohdaten_universal.csv`, und
 * der Dateidialog hängte „(1)", „(2)" an — aus dem Namen war nicht abzulesen,
 * wann die Aufnahme entstanden ist.
 */
class ExportNamesTest {

    @Test
    fun zeitstempelHatDieFormJahrMonatTagStundeMinuteSekunde() {
        val stamp = ExportNames.timestamp()

        assertEquals(15, stamp.length)
        assertTrue("erwartet 20261112_105712, war $stamp", Regex("""\d{8}_\d{6}""").matches(stamp))
    }

    @Test
    fun zeitstempelEntsprichtDemUebergebenenZeitpunkt() {
        // 2026-11-12 10:57:12 in Gerätezeit; die Zeitzone steckt im Date, deshalb
        // wird der erwartete Wert genauso gebildet statt fest hingeschrieben.
        val date = Date(1_763_000_000_000L)
        val expected = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(date)

        assertEquals(expected, ExportNames.timestamp(date))
    }

    @Test
    fun zweiExporteInDerselbenMinuteBleibenUnterscheidbar() {
        val a = ExportNames.timestamp(Date(1_763_000_000_000L))
        val b = ExportNames.timestamp(Date(1_763_000_001_000L))

        assertNotEquals(a, b)
    }
}

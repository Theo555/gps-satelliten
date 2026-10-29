package de.gpsradar.satellites

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.gpsradar.satellites.model.SkipReason
import de.gpsradar.satellites.model.WaypointCodec
import de.gpsradar.satellites.ui.reportLines
import de.gpsradar.satellites.ui.toMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Die Import-Meldungen entstanden früher als fertige deutsche Sätze im Modell.
 * Jetzt liefert der Codec nur noch Gründe (`SkipReason`), die Texte kommen aus
 * den Ressourcen – hier wird geprüft, dass beide Sprachen greifen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportTextsTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun gpxWith(waypointXml: String) = """
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.0" creator="Test" xmlns="http://www.topografix.com/GPX/1/0">
$waypointXml
</gpx>
"""

    @Test
    fun emptyFileReportsNothingFound() {
        val result = WaypointCodec.parse("<gpx version=\"1.0\" creator=\"Test\"></gpx>")
        assertEquals("No waypoints or tracks found in the file", result.toMessage(context))
    }

    @Test
    fun skippedWaypointIsReportedAsSingleReason() {
        val gpx = gpxWith("""  <wpt lat="999" lon="7.8"><name>Kaputt</name></wpt>""")
        val result = WaypointCodec.parse(gpx)
        assertEquals(listOf(SkipReason.WaypointWithoutCoordinates), result.skipped)
        assertEquals("Waypoint skipped – no valid coordinates", result.toMessage(context))
    }

    @Test
    @Config(sdk = [34], qualifiers = "de-rDE")
    fun germanLocaleUsesGermanTexts() {
        val gpx = gpxWith("""  <wpt lat="999" lon="7.8"><name>Kaputt</name></wpt>""")
        val result = WaypointCodec.parse(gpx)
        assertEquals("Wegpunkt ohne gültige Koordinaten übersprungen", result.toMessage(context))
    }

    @Test
    fun summaryCountsImportedEntries() {
        val gpx = gpxWith(
            """  <wpt lat="51.7" lon="7.8"><name>A</name></wpt>
  <wpt lat="51.8" lon="7.9"><name>B</name></wpt>
  <wpt lat="999" lon="7.8"><name>Kaputt</name></wpt>"""
        )
        val result = WaypointCodec.parse(gpx)
        // Der Toast bleibt kurz; der Grund steht im Dialog (reportLines, I8).
        assertEquals("2 waypoints imported, 1 skipped", result.toMessage(context))
        assertEquals(
            listOf("2 waypoints imported", "Waypoint skipped – no valid coordinates"),
            result.reportLines(context)
        )
    }

    @Test
    fun missingNameUsesFallbackFromParameter() {
        val gpx = gpxWith("""  <wpt lat="51.7" lon="7.8"></wpt>""")
        val result = WaypointCodec.parse(gpx, "Ziel")
        assertEquals("Ziel", result.waypoints.single().name)
    }
}

package de.gpsradar.satellites

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.gpsradar.satellites.model.ImportResult
import de.gpsradar.satellites.model.WaypointCodec
import de.gpsradar.satellites.ui.toMessage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Texte aus der Hardware-Testliste (A5–A9), geprüft in beiden Sprachen.
 *
 * Am Gerät lässt sich immer nur die eingestellte Systemsprache sehen; hier
 * laufen beide Varianten hintereinander und bleiben als Wächter erhalten.
 * Geprüft wird jeweils die Ressource, die auch die Oberfläche benutzt.
 */
private const val WAYPOINTS_GPX = """
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.0" creator="Test" xmlns="http://www.topografix.com/GPX/1/0">
  <wpt lat="51.100000" lon="7.100000"><name>A</name></wpt>
  <wpt lat="51.200000" lon="7.200000"><name>B</name></wpt>
  <wpt lat="51.300000" lon="7.300000"><name>C</name></wpt>
</gpx>
"""

private fun threeWaypointsMessage(context: Context): String =
    WaypointCodec.parse(WAYPOINTS_GPX, "Ziel").toMessage(context)

private fun emptyImportMessage(context: Context): String =
    ImportResult(emptyList(), emptyList(), emptyList(), emptyList()).toMessage(context)

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de")
class LocalizationGermanTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** A5: Meldung bei leerer/kaputter Datei. */
    @Test
    fun emptyImportMessageIsGerman() {
        assertEquals(
            "Keine Zielpunkte oder Tracks in der Datei gefunden",
            emptyImportMessage(context)
        )
    }

    /** A6: Einzahl und Mehrzahl für Caches und Zielpunkte. */
    @Test
    fun pluralsAreGerman() {
        val res = context.resources
        assertEquals("1 Cache", res.getQuantityString(R.plurals.import_part_caches, 1, 1))
        assertEquals("2 Caches", res.getQuantityString(R.plurals.import_part_caches, 2, 2))
        assertEquals("1 Zielpunkt", res.getQuantityString(R.plurals.import_part_waypoints, 1, 1))
        assertEquals("3 Zielpunkte", res.getQuantityString(R.plurals.import_part_waypoints, 3, 3))
        // Zusammengesetzt, wie es der Import tatsächlich meldet.
        assertEquals("3 Zielpunkte importiert", threeWaypointsMessage(context))
    }

    /** A7 und A8: Standardnamen für Track und Zielpunkt. */
    @Test
    fun defaultNamesAreGerman() {
        assertEquals("Ziel", context.getString(R.string.waypoint_default_name))
        assertEquals(
            "Aufzeichnung 2026-10-29 15:04",
            context.getString(R.string.track_default_name, "2026-10-29 15:04")
        )
    }

    /** A9: Vorlesetext des Gefunden-Knopfs. */
    @Test
    fun foundButtonDescriptionIsGerman() {
        assertEquals("Als gefunden markieren", context.getString(R.string.cd_mark_found))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "en")
class LocalizationEnglishTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun emptyImportMessageIsEnglish() {
        assertEquals(
            "No waypoints or tracks found in the file",
            emptyImportMessage(context)
        )
    }

    @Test
    fun pluralsAreEnglish() {
        val res = context.resources
        assertEquals("1 cache", res.getQuantityString(R.plurals.import_part_caches, 1, 1))
        assertEquals("2 caches", res.getQuantityString(R.plurals.import_part_caches, 2, 2))
        assertEquals("1 waypoint", res.getQuantityString(R.plurals.import_part_waypoints, 1, 1))
        assertEquals("3 waypoints", res.getQuantityString(R.plurals.import_part_waypoints, 3, 3))
        assertEquals("3 waypoints imported", threeWaypointsMessage(context))
    }

    @Test
    fun defaultNamesAreEnglish() {
        assertEquals("Waypoint", context.getString(R.string.waypoint_default_name))
        assertEquals(
            "Recording 2026-10-29 15:04",
            context.getString(R.string.track_default_name, "2026-10-29 15:04")
        )
    }

    @Test
    fun foundButtonDescriptionIsEnglish() {
        assertEquals("Mark as found", context.getString(R.string.cd_mark_found))
    }
}

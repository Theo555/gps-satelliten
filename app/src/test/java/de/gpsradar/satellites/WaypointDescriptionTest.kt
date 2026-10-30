package de.gpsradar.satellites

import de.gpsradar.satellites.model.WaypointCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Der `<desc>`-Eintrag einer GPX-Datei hat zwei Bedeutungen: bei Zusatzpunkten
 * einer Pocket Query den Hinweis des Owners („Parkplatz bei N51 …"), bei
 * manchen Dateien nur den GC-Code. Nur das Erste wird zur Beschreibung.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WaypointDescriptionTest {

    private fun gpx(waypoint: String) = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
  $waypoint
</gpx>
"""

    @Test
    fun ownerNoteBecomesDescription() {
        val gpx = gpx(
            """
  <wpt lat="51.690000" lon="7.890000">
    <name>PK01</name>
    <desc>Parkplatz bei N51 12.345 E007 12.345</desc>
    <type>Waypoint|Parking Area</type>
  </wpt>
"""
        )
        val wp = WaypointCodec.parse(gpx).waypoints.single()

        assertEquals("PK01", wp.name)
        assertNull(wp.code)
        assertTrue(
            "Beschreibung erwartet, war: ${wp.description}",
            wp.description?.startsWith("Parkplatz bei") == true
        )
    }

    @Test
    fun bareCodeIsNotADescription() {
        val gpx = gpx(
            """
  <wpt lat="51.690000" lon="7.890000">
    <name>Multi am Fluss</name>
    <desc>GC2TEST</desc>
    <type>Geocache|Multi-cache</type>
  </wpt>
"""
        )
        val wp = WaypointCodec.parse(gpx).waypoints.single()

        // Der Code steht hier nur im <desc>; er dient der Gruppierung, nicht als
        // Beschreibung.
        assertNull(wp.code)
        assertEquals("GC2TEST", wp.groupCacheId)
        assertNull("Code allein darf keine Beschreibung werden", wp.description)
    }

    @Test
    fun missingDescLeavesDescriptionEmpty() {
        val gpx = gpx(
            """
  <wpt lat="51.690000" lon="7.890000">
    <name>Ohne Hinweis</name>
  </wpt>
"""
        )
        val wp = WaypointCodec.parse(gpx).waypoints.single()

        assertNull(wp.description)
    }
}

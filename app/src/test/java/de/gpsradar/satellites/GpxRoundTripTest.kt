package de.gpsradar.satellites

import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.model.WaypointCodec
import de.gpsradar.satellites.model.groupWaypoints
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Import → Export → Import → Export muss strukturell identisch sein
 * (gleiche Gruppierung, gleiche Dateiaufteilung).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GpxRoundTripTest {

    private val multiGpx = """
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
  <wpt lat="51.712300" lon="7.895600">
    <name>GC_TEST_02 - Schlossrunde (Start)</name>
    <desc>Start des fiktiven Multi-Caches.</desc>
    <type>Multi-cache</type>
  </wpt>
  <wpt lat="51.710500" lon="7.893000">
    <name>Final</name>
    <desc>GC_TEST_02</desc>
    <type>Final Location</type>
  </wpt>
  <wpt lat="51.709800" lon="7.891500">
    <name>Stage 1</name>
    <desc>GC_TEST_02</desc>
    <type>Waypoint</type>
  </wpt>
  <wpt lat="51.716000" lon="7.899000">
    <name>Zuhause</name>
  </wpt>
  <trk>
    <name>Spaziergang</name>
    <trkseg>
      <trkpt lat="51.700000" lon="7.800000"><time>2026-01-01T10:00:00Z</time></trkpt>
      <trkpt lat="51.710000" lon="7.810000"><time>2026-01-01T10:05:00Z</time></trkpt>
    </trkseg>
  </trk>
</gpx>
"""

    @Test
    fun groupingByDescGcCode() {
        val result = WaypointCodec.parse(multiGpx)
        val grouping = groupWaypoints(result.waypoints)

        assertEquals(1, grouping.caches.size)
        assertEquals(3, grouping.caches[0].waypoints.size)
        assertEquals("GC_TEST_02", grouping.caches[0].code)
        assertEquals("Schlossrunde (Start)", grouping.caches[0].name)
        assertEquals(1, grouping.freeWaypoints.size)
        assertEquals("Zuhause", grouping.freeWaypoints[0].name)
    }

    @Test
    fun importExportRoundTripIsStructurallyIdentical() {
        val first = WaypointCodec.parse(multiGpx)
        val files1 = WaypointCodec.toGpxFiles(first.waypoints, first.tracks)

        assertEquals(
            listOf("cache_GC_TEST_02.gpx", "position_Zuhause.gpx", "track_Spaziergang.gpx"),
            files1.map { it.fileName }
        )

        // Alle exportierten Dateien erneut importieren.
        val waypoints = mutableListOf<Waypoint>()
        val tracks = mutableListOf<Track>()
        files1.forEach { f ->
            val r = WaypointCodec.parse(f.content)
            waypoints += r.waypoints
            tracks += r.tracks
        }

        val grouping = groupWaypoints(waypoints)
        assertEquals(1, grouping.caches.size)
        assertEquals(3, grouping.caches[0].waypoints.size)
        assertEquals("GC_TEST_02", grouping.caches[0].code)
        assertEquals(1, grouping.freeWaypoints.size)
        assertEquals("Zuhause", grouping.freeWaypoints[0].name)
        assertEquals(1, tracks.size)
        assertEquals("Spaziergang", tracks[0].name)

        // Zweiter Export muss identisch aufgeteilt sein.
        val files2 = WaypointCodec.toGpxFiles(waypoints, tracks)
        assertEquals(files1.map { it.fileName }, files2.map { it.fileName })
    }
}

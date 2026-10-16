package de.gpsradar.satellites

import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.TrackPoint
import de.gpsradar.satellites.model.WaypointCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackImportRoundTripTest {

    @Test
    fun parseGpxWithTrack() {
        val gpx = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="GPS Satelliten" xmlns="http://www.topografix.com/GPX/1/1">
  <trk>
    <name>Testtrack</name>
    <trkseg>
      <trkpt lat="52.520000" lon="13.404000"/>
      <trkpt lat="52.521000" lon="13.405000"/>
      <trkpt lat="52.522000" lon="13.406000"/>
    </trkseg>
  </trk>
</gpx>
"""
        val result = WaypointCodec.parse(gpx)
        assertEquals(1, result.tracks.size)
        assertEquals("Testtrack", result.tracks[0].name)
        assertEquals(3, result.tracks[0].points.size)
    }

    @Test
    fun roundTripTracks() {
        val tracks = listOf(
            Track(
                id = 1L,
                name = "Aufzeichnung 16.10.2026 12:00",
                points = listOf(
                    TrackPoint(52.520000, 13.404000),
                    TrackPoint(52.521000, 13.405000),
                    TrackPoint(52.522000, 13.406000)
                )
            )
        )
        val gpx = WaypointCodec.toGpx(emptyList(), tracks)
        val result = WaypointCodec.parse(gpx)
        assertTrue("Export sollte einen Track enthalten", result.tracks.isNotEmpty())
        assertEquals(3, result.tracks[0].points.size)
    }
}

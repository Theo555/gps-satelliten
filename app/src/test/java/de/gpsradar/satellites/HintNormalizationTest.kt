package de.gpsradar.satellites

import de.gpsradar.satellites.model.WaypointCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Geocaching.com schreibt bei fehlendem Hint wörtlich "none" in
 * `groundspeak:encoded_hints`. Solche Platzhalter dürfen nicht als Hint
 * in der App auftauchen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HintNormalizationTest {

    private fun gpxWithHint(hintXml: String) = """
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.0" creator="Test"
     xmlns="http://www.topografix.com/GPX/1/0"
     xmlns:groundspeak="http://www.groundspeak.com/cache/1/0">
  <wpt lat="51.712300" lon="7.895600">
    <name>GC_TEST_03</name>
    <groundspeak:cache id="1" archived="false">
      <groundspeak:name>Testcache mit Hint</groundspeak:name>
      <groundspeak:difficulty>2</groundspeak:difficulty>
      <groundspeak:terrain>1.5</groundspeak:terrain>
      $hintXml
    </groundspeak:cache>
  </wpt>
</gpx>
"""

    @Test
    fun placeholderNoneBecomesNoHint() {
        val result = WaypointCodec.parse(gpxWithHint("<groundspeak:encoded_hints>none</groundspeak:encoded_hints>"))
        assertEquals(1, result.waypoints.size)
        assertNull(result.waypoints[0].hint)
    }

    @Test
    fun placeholderNoneIsCaseInsensitive() {
        val result = WaypointCodec.parse(gpxWithHint("<groundspeak:encoded_hints>NONE</groundspeak:encoded_hints>"))
        assertNull(result.waypoints[0].hint)
    }

    @Test
    fun emptyHintBecomesNoHint() {
        val result = WaypointCodec.parse(gpxWithHint("<groundspeak:encoded_hints>   </groundspeak:encoded_hints>"))
        assertNull(result.waypoints[0].hint)
    }

    @Test
    fun realHintIsKept() {
        val result = WaypointCodec.parse(
            gpxWithHint("<groundspeak:encoded_hints>Unter der Bank</groundspeak:encoded_hints>")
        )
        assertEquals("Unter der Bank", result.waypoints[0].hint)
    }

    @Test
    fun jsonPlaceholderNoneBecomesNoHint() {
        val json = """[{"name":"Ziel","lat":51.7,"lon":7.8,"hint":"none"}]"""
        val result = WaypointCodec.parse(json)
        assertEquals(1, result.waypoints.size)
        assertNull(result.waypoints[0].hint)
    }

    @Test
    fun jsonRealHintIsKept() {
        val json = """[{"name":"Ziel","lat":51.7,"lon":7.8,"hint":"Unter der Bank"}]"""
        val result = WaypointCodec.parse(json)
        assertEquals("Unter der Bank", result.waypoints[0].hint)
    }
}

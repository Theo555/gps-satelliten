package de.gpsradar.satellites

import de.gpsradar.satellites.model.WaypointCodec
import de.gpsradar.satellites.model.groupWaypoints
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CacheGroupingTest {

    private val gpx = """
<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1"
     xmlns:groundspeak="http://www.groundspeak.com/cache/1/0/1">
  <wpt lat="51.708500" lon="7.889200">
    <name>Parkplatz Schloss (Test)</name>
    <desc>Fiktiver Parkplatz. Kein Cache – reiner Wegpunkt.</desc>
    <sym>Parking Area</sym>
    <type>Waypoint</type>
  </wpt>

  <wpt lat="51.712300" lon="7.895600">
    <name>GC_TEST_01 - Kanalufer Werries</name>
    <desc>Fiktiver Traditional am Kanal.</desc>
    <sym>Geocache</sym>
    <type>Traditional Cache</type>
    <groundspeak:cache id="999001" available="true" archived="false">
      <groundspeak:name>Kanalufer Werries (Test)</groundspeak:name>
      <groundspeak:placed_by>TestOwner</groundspeak:placed_by>
      <groundspeak:type>Traditional Cache</groundspeak:type>
      <groundspeak:container>Small</groundspeak:container>
      <groundspeak:difficulty>1.5</groundspeak:difficulty>
      <groundspeak:terrain>1.5</groundspeak:terrain>
      <groundspeak:encoded_hints>Hagre qrz Fgrva.</groundspeak:encoded_hints>
    </groundspeak:cache>
  </wpt>

  <wpt lat="51.709800" lon="7.891500">
    <name>GC_TEST_02 - Schlossrunde (Start)</name>
    <desc>Start des fiktiven Multi-Caches.</desc>
    <sym>Geocache</sym>
    <type>Multi-Cache</type>
    <groundspeak:cache id="999002" available="true" archived="false">
      <groundspeak:name>Schlossrunde Werries (Test-Multi)</groundspeak:name>
      <groundspeak:type>Multi-Cache</groundspeak:type>
      <groundspeak:container>Micro</groundspeak:container>
      <groundspeak:difficulty>2.0</groundspeak:difficulty>
      <groundspeak:terrain>2.0</groundspeak:terrain>
      <groundspeak:encoded_hints>Fgntr 1: Fpunh qve qnf Fpuvyq na.</groundspeak:encoded_hints>
    </groundspeak:cache>
  </wpt>

  <wpt lat="51.710500" lon="7.893000">
    <name>GC_TEST_02 - Final</name>
    <desc>Fiktives Final des Test-Multi-Caches.</desc>
    <sym>Geocache</sym>
    <type>Final Location</type>
    <groundspeak:cache id="999002" available="true" archived="false">
      <groundspeak:name>Schlossrunde Werries (Final)</groundspeak:name>
      <groundspeak:type>Multi-Cache</groundspeak:type>
      <groundspeak:container>Small</groundspeak:container>
      <groundspeak:encoded_hints>Hagre qrz Jhemryfgbpx.</groundspeak:encoded_hints>
    </groundspeak:cache>
  </wpt>

  <wpt lat="51.716000" lon="7.899000">
    <name>GC_TEST_05 - Alter Cache (archiviert)</name>
    <desc>Fiktiver archivierter Cache.</desc>
    <sym>Geocache</sym>
    <type>Traditional Cache</type>
    <groundspeak:cache id="999005" available="false" archived="true">
      <groundspeak:name>Alter Cache (Test, archiviert)</groundspeak:name>
      <groundspeak:type>Traditional Cache</groundspeak:type>
      <groundspeak:container>Regular</groundspeak:container>
      <groundspeak:difficulty>1.0</groundspeak:difficulty>
      <groundspeak:terrain>1.0</groundspeak:terrain>
    </groundspeak:cache>
  </wpt>
</gpx>
"""

    @Test
    fun parseGroupsCachesByGroundspeakId() {
        val result = WaypointCodec.parse(gpx)
        assertEquals(5, result.waypoints.size)

        val grouping = groupWaypoints(result.waypoints)
        assertEquals(3, grouping.caches.size)
        assertEquals(1, grouping.freeWaypoints.size)
        assertEquals("Parkplatz Schloss (Test)", grouping.freeWaypoints[0].name)
    }

    @Test
    fun multiCacheGroupsStartAndFinal() {
        val result = WaypointCodec.parse(gpx)
        val grouping = groupWaypoints(result.waypoints)

        val multi = grouping.caches.find { it.id == "999002" }
        assertNotNull(multi)
        assertEquals(2, multi!!.waypoints.size)
        // Primärer Wegpunkt = der Cache selbst (Multi-Cache), nicht das Final.
        assertEquals("Multi-cache", multi.cacheType)
        assertEquals("Schlossrunde Werries (Test-Multi)", multi.name)
        assertEquals("GC_TEST_02", multi.code)
    }

    @Test
    fun extractsCodeAndArchivedFlag() {
        val result = WaypointCodec.parse(gpx)
        val grouping = groupWaypoints(result.waypoints)

        val traditional = grouping.caches.find { it.id == "999001" }
        assertNotNull(traditional)
        assertEquals("GC_TEST_01", traditional!!.code)
        assertEquals("Kanalufer Werries (Test)", traditional.name)
        assertEquals(1.5, traditional.difficulty ?: 0.0, 0.001)

        val archived = grouping.caches.find { it.id == "999005" }
        assertNotNull(archived)
        assertTrue(archived!!.archived)
    }

    @Test
    fun freeWaypointHasNoGroupId() {
        val result = WaypointCodec.parse(gpx)
        val parking = result.waypoints.find { it.name == "Parkplatz Schloss (Test)" }
        assertNotNull(parking)
        assertNull(parking!!.groupCacheId)
        assertNull(parking.code)
    }

    @Test
    fun hintIsRot13Decoded() {
        val result = WaypointCodec.parse(gpx)
        val traditional = result.waypoints.find { it.code == "GC_TEST_01" }
        assertNotNull(traditional)
        assertEquals("Hagre qrz Fgrva.", traditional!!.hint)
        assertEquals("Unter dem Stein.", WaypointCodec.rot13(traditional.hint!!))
    }
}

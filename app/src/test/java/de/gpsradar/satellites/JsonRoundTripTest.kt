package de.gpsradar.satellites

import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.model.WaypointCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * JSON-Export und -Import müssen die Cache-Gruppierung erhalten.
 *
 * Vorher fehlte `groupCacheId` in der Datei: Ein Multi mit seinen Zusatzpunkten
 * kam als vier lose Zielpunkte zurück – ein stiller Datenverlust, der beim
 * Sichern und Wiederherstellen erst auffällt, wenn die Gruppe fehlt.
 *
 * Robolectric, weil `org.json` im reinen JVM-Test nur eine leere Hülle ist.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class JsonRoundTripTest {

    private fun waypoint(
        name: String,
        code: String? = null,
        group: String? = null,
        type: String? = null
    ) = Waypoint(
        id = name.hashCode().toLong(),
        name = name,
        latitude = 51.6908,
        longitude = 7.8923,
        code = code,
        cacheType = type,
        groupCacheId = group
    )

    private val multi = listOf(
        waypoint("Multi am Fluss", code = "GC12345", group = "GC12345", type = "Multi-cache"),
        waypoint("Parkplatz", group = "GC12345"),
        waypoint("Station 1", group = "GC12345"),
        waypoint("Freier Punkt")
    )

    /** Der Kern: aus vier Punkten müssen wieder eine Gruppe und ein Einzelner werden. */
    @Test
    fun cacheGroupSurvivesJsonRoundTrip() {
        val json = WaypointCodec.toJson(multi)
        val result = WaypointCodec.parse(json)

        assertEquals(1, result.caches.size)
        assertEquals(1, result.freeWaypoints.size)

        val cache = result.caches.first()
        assertEquals("GC12345", cache.code)
        assertEquals("Multi am Fluss", cache.name)
        assertEquals(3, cache.waypoints.size)
    }

    /** Zur Kontrolle: das Feld steht wirklich in der Datei. */
    @Test
    fun exportedJsonContainsGroupId() {
        val json = WaypointCodec.toJson(multi)
        assertTrue(json.contains("\"groupCacheId\""))
    }

    /**
     * Dateien von früher (ohne das Feld) müssen weiter lesbar sein – die Punkte
     * werden dann zu freien Zielpunkten, statt den Import abzubrechen.
     */
    @Test
    fun oldFilesWithoutGroupIdStillImport() {
        val json = """
            [{"name":"Alt","lat":51.6908,"lon":7.8923,"code":"GC999"}]
        """.trimIndent()
        val result = WaypointCodec.parse(json)

        assertEquals(1, result.freeWaypoints.size)
        assertTrue(result.caches.isEmpty())
        assertNotNull(result.freeWaypoints.first())
    }
}

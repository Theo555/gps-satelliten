package de.gpsradar.satellites

import de.gpsradar.satellites.model.SkipReason
import de.gpsradar.satellites.model.WaypointCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prüft den Import anhand echter Dateien aus `src/test/resources/import/`.
 *
 * Die Dateien sind bewusst als Dateien abgelegt und nicht als Textblock im
 * Test: So lässt sich dieselbe Datei auch von Hand auf ein Gerät kopieren
 * (Arbeitskopien mit Erwartungen liegen in `_austausch/Import-Testdateien/`).
 *
 * Die zwei großen Beispieldateien (500 Wegpunkte, 1000 Trackpunkte) werden
 * hier erzeugt, damit das Repo nicht unnötig wächst.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ImportFixturesTest {

    private fun load(name: String): String =
        javaClass.classLoader!!.getResourceAsStream("import/$name")
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("Fixture fehlt: import/$name")

    private fun parse(name: String) = WaypointCodec.parse(load(name), "Ziel")

    // ---------- A. Grundfälle ----------

    @Test
    fun wegpunkteEinfach() {
        val r = parse("01-wegpunkte-einfach.gpx")
        assertEquals(3, r.freeWaypoints.size)
        assertEquals(0, r.caches.size)
        assertEquals(0, r.tracks.size)
        assertEquals(0, r.skipped.size)
        assertEquals(listOf("Rathaus", "Bäckerei", "Bahnhof"), r.freeWaypoints.map { it.name })
    }

    @Test
    fun gpx10WirdErkannt() {
        assertEquals(2, parse("02-gpx-1.0-namespace.gpx").freeWaypoints.size)
    }

    @Test
    fun gpxOhneNamespaceWirdErkannt() {
        assertEquals(2, parse("03-gpx-ohne-namespace.gpx").freeWaypoints.size)
    }

    @Test
    fun routeWirdAlsTrackImportiert() {
        val r = parse("04-route.gpx")
        assertEquals(1, r.tracks.size)
        assertEquals("Rundweg Innenstadt", r.tracks[0].name)
        assertEquals(5, r.tracks[0].points.size)
    }

    @Test
    fun routeOhneNamenHeisstRoute() {
        val r = parse("05-route-ohne-namen.gpx")
        assertEquals(1, r.tracks.size)
        assertEquals("Route", r.tracks[0].name)
        assertEquals(2, r.tracks[0].points.size)
    }

    @Test
    fun trackMitHoeheUndZeit() {
        val r = parse("06-track-mit-hoehe-und-zeit.gpx")
        assertEquals(1, r.tracks.size)
        val pts = r.tracks[0].points
        assertEquals(4, pts.size)
        assertEquals(112.4, pts[0].elevationMeters!!, 0.001)
        // Alle vier Zeitstempel müssen lesbar sein: mit "Z", mit Bruchteil und ohne "Z".
        assertTrue(pts.all { it.timeMillis != null })
        assertTrue(pts.zipWithNext().all { (a, b) -> a.timeMillis!! < b.timeMillis!! })
        // Der vierte Punkt hat keine Höhe.
        assertNull(pts[3].elevationMeters)
    }

    @Test
    fun trackMitEinemPunktWirdUebersprungen() {
        val r = parse("07-track-zu-kurz.gpx")
        assertEquals(1, r.tracks.size)
        assertEquals("ZweiPunkte", r.tracks[0].name)
        assertTrue(
            r.skipped.any { it is SkipReason.TrackTooShort && it.name == "Einzelpunkt" }
        )
    }

    @Test
    fun trackpunktOhneKoordinatenWirdUebersprungen() {
        val r = parse("08-trackpunkt-ohne-koordinaten.gpx")
        assertEquals(1, r.tracks.size)
        assertEquals(2, r.tracks[0].points.size)
        assertTrue(r.skipped.contains(SkipReason.TrackPointWithoutCoordinates))
    }

    @Test
    fun wegpunktOhneKoordinatenWirdUebersprungen() {
        val r = parse("09-wegpunkt-ohne-koordinaten.gpx")
        assertEquals(listOf("Gut", "AuchGut"), r.freeWaypoints.map { it.name })
        assertEquals(2, r.skipped.count { it == SkipReason.WaypointWithoutCoordinates })
    }

    @Test(timeout = 10_000)
    fun abgeschnittenesXmlBehaeltVorherGelesenes() {
        val r = parse("10-kaputtes-xml.gpx")
        assertEquals(listOf("VorDemFehler", "AuchNochGut"), r.freeWaypoints.map { it.name })
        assertTrue(r.skipped.any { it is SkipReason.InvalidXml })
    }

    /**
     * Regression: endet die Datei mitten in einem Element, liefert der Parser
     * endlos END_DOCUMENT – ohne Abbruch hing der Import (ANR).
     */
    @Test(timeout = 10_000)
    fun abgeschnittenerTrackHaengtNicht() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <trk><name>Abgeschnitten</name><trkseg>
                <trkpt lat="51.69" lon="7.89"/><trkpt lat="51.70" lon="7.90"/>
        """.trimIndent()
        val r = WaypointCodec.parse(gpx, "Ziel")
        assertEquals(1, r.tracks.size)
        assertEquals(2, r.tracks[0].points.size)
    }

    /** Regression: Ende mitten im Namen eines Wegpunkts. */
    @Test(timeout = 10_000)
    fun abgeschnittenerWegpunktHaengtNicht() {
        val gpx = """
            <?xml version="1.0" encoding="UTF-8"?>
            <gpx version="1.1" xmlns="http://www.topografix.com/GPX/1/1">
              <wpt lat="51.69" lon="7.89"><name>Gut</name></wpt>
              <wpt lat="51.70" lon="7.90"><name>Abgeschnitten
        """.trimIndent()
        val r = WaypointCodec.parse(gpx, "Ziel")
        assertEquals(listOf("Gut"), r.freeWaypoints.map { it.name })
    }

    @Test
    fun leereDateiErgibtNichts() {
        val r = parse("11-leere-datei.gpx")
        assertTrue(r.waypoints.isEmpty() && r.tracks.isEmpty() && r.skipped.isEmpty())
    }

    @Test
    fun textStattGpxErgibtNichts() {
        val r = parse("12-kein-gpx-sondern-text.gpx")
        assertTrue(r.waypoints.isEmpty() && r.tracks.isEmpty() && r.skipped.isEmpty())
    }

    /** Regression: Notepad schreibt vor dem XML ein UTF-8-BOM. */
    @Test
    fun dateiMitBomWirdGelesen() {
        val r = parse("13-mit-bom.gpx")
        assertEquals(listOf("MitBOM", "Zweiter"), r.freeWaypoints.map { it.name })
    }

    @Test
    fun htmlStattGpxErgibtNichts() {
        val r = parse("14-html-statt-gpx.gpx")
        assertTrue(r.waypoints.isEmpty() && r.tracks.isEmpty())
    }

    // ---------- B. Geocaching ----------

    @Test
    fun cacheEinfach() {
        val r = parse("20-cache-einfach.gpx")
        assertEquals(1, r.caches.size)
        val c = r.caches[0]
        assertEquals("Alter Bahnhof", c.name)
        assertEquals("GC1ABCD", c.code)
        assertEquals(2.5, c.difficulty!!, 0.001)
        assertEquals(3.0, c.terrain!!, 0.001)
        assertEquals("Traditional Cache", c.cacheType)
        assertEquals("Unter der Bank, links.", c.hint)
        assertEquals(1, c.waypoints.size)
    }

    /** Regression: Kopf (groundspeak-id) und Zusatzpunkte (GC-Code) müssen gruppieren. */
    @Test
    fun cacheMitZusatzpunktenBleibtEineGruppe() {
        val r = parse("21-cache-mit-zusatzpunkten.gpx")
        assertEquals(1, r.caches.size)
        val c = r.caches[0]
        assertEquals("Multi am Fluss", c.name)
        assertEquals("GC2TEST", c.code)
        assertEquals(4, c.waypoints.size)
        assertEquals(
            listOf("Multi am Fluss", "Parkplatz", "Station 1", "Final"),
            c.waypoints.map { it.name }
        )
        // Der Kopf steht vorn, weil er als einziger einen Cache-Typ trägt.
        assertEquals("Multi-cache", c.waypoints[0].cacheType)
        // "none" als Hinweis darf nicht als Hinweis erscheinen.
        assertNull(c.hint)
    }

    @Test
    fun cacheArchiviertWirdMarkiert() {
        val r = parse("22-cache-archiviert.gpx")
        assertEquals(2, r.caches.size)
        val alt = r.caches.first { it.name == "Verwittert" }
        val neu = r.caches.first { it.name == "Frisch" }
        assertTrue(alt.archived)
        assertTrue(!neu.archived)
    }

    @Test
    fun hinweisPlatzhalterWirdVerworfen() {
        val r = parse("23-cache-hint-platzhalter.gpx")
        assertEquals(2, r.caches.size)
        assertNull(r.caches.first { it.name == "Ohne Hinweis" }.hint)
        assertEquals(
            "Nyyre Tngr fvaq tyrvpu.",
            r.caches.first { it.name == "Mit Hinweis" }.hint
        )
    }

    @Test
    fun gemischtePocketQuery() {
        val r = parse("24-pocketquery-gemischt.gpx")
        assertEquals(listOf("Am Kanal", "Rätselhaft", "Virtueller Blick"), r.caches.map { it.name })
        assertEquals(1, r.freeWaypoints.size)
        assertEquals("Treffpunkt Parkplatz", r.freeWaypoints[0].name)
        // Der Zusatzpunkt des Rätsels gehört zum Rätsel, nicht in eine eigene Gruppe.
        assertEquals(2, r.caches.first { it.name == "Rätselhaft" }.waypoints.size)
    }

    @Test
    fun alleCacheTypenWerdenUebernommen() {
        val r = parse("25-cache-typen.gpx")
        assertEquals(9, r.caches.size)
        val typen = r.caches.mapNotNull { it.cacheType }
        assertTrue(typen.contains("Traditional Cache"))
        assertTrue(typen.contains("Earthcache"))
        assertTrue(typen.contains("Event Cache"))
        // Unbekannter Typ bleibt erhalten, statt zu verschwinden.
        assertTrue(typen.contains("Giga-Event Cache"))
    }

    // ---------- C. Grenzfälle ----------

    @Test
    fun koordinatenGrenzen() {
        val r = parse("30-koordinaten-grenzen.gpx")
        assertEquals(4, r.freeWaypoints.size)
        assertEquals(2, r.skipped.count { it == SkipReason.WaypointWithoutCoordinates })
        assertTrue(r.freeWaypoints.any { it.latitude == 90.0 })
        assertTrue(r.freeWaypoints.any { it.longitude == -180.0 })
    }

    @Test
    fun nullIslandIstGueltig() {
        val r = parse("31-null-island.gpx")
        assertEquals(1, r.freeWaypoints.size)
        assertEquals(0.0, r.freeWaypoints[0].latitude, 0.0)
        assertEquals(0.0, r.freeWaypoints[0].longitude, 0.0)
    }

    @Test
    fun sonderzeichenUndLeereNamen() {
        val r = parse("32-namen-sonderzeichen.gpx")
        assertEquals(7, r.freeWaypoints.size)
        val namen = r.freeWaypoints.map { it.name }
        assertTrue(namen.contains("Weg & Steg <Test>"))
        assertTrue(namen.contains("Müllerplatz – Ümlaute"))
        assertTrue(namen.contains("Emoji 📍🚗 Test"))
        // Leerer Name fällt auf den übergebenen Ersatznamen zurück.
        assertTrue(namen.contains("Ziel"))
        // Leerzeichen am Rand werden entfernt.
        assertTrue(namen.contains("Rand mit Leerzeichen"))
        // Der lange Name bleibt vollständig (gekürzt wird erst in der Anzeige).
        assertTrue(namen.any { it.length > 190 })
    }

    @Test
    fun doppelteNamenBleibenGetrennt() {
        val r = parse("33-doppelte-namen.gpx")
        assertEquals(3, r.freeWaypoints.size)
        assertTrue(r.freeWaypoints.all { it.name == "Parkplatz" })
    }

    @Test
    fun vieleWegpunkte() {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        repeat(500) { i ->
            sb.append("  <wpt lat=\"51.6\" lon=\"7.8\"><name>Punkt ${i + 1}</name></wpt>\n")
        }
        sb.append("</gpx>\n")
        val r = WaypointCodec.parse(sb.toString(), "Ziel")
        assertEquals(500, r.freeWaypoints.size)
    }

    @Test
    fun langerTrack() {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        sb.append("  <trk><name>Lang</name><trkseg>\n")
        repeat(1000) { i ->
            sb.append("    <trkpt lat=\"51.69\" lon=\"7.89\"><ele>100</ele></trkpt>\n")
        }
        sb.append("  </trkseg></trk>\n</gpx>\n")
        val r = WaypointCodec.parse(sb.toString(), "Ziel")
        assertEquals(1, r.tracks.size)
        assertEquals(1000, r.tracks[0].points.size)
    }

    // ---------- D. JSON ----------

    @Test
    fun jsonExportFormat() {
        val r = parse("40-json-export-format.json")
        assertEquals(3, r.freeWaypoints.size)
        val cache = r.freeWaypoints.first { it.code == "GC1ABCD" }
        assertEquals("Alter Bahnhof", cache.name)
        assertEquals(2.5, cache.difficulty!!, 0.001)
        assertEquals("Traditional Cache", cache.cacheType)
        assertTrue(cache.found)
        // "none" wird auch im JSON als Hinweis verworfen.
        assertNull(r.freeWaypoints.first { it.code == "GC2TEST" }.hint)
    }

    @Test
    fun jsonMitWrapperObjekt() {
        assertEquals(2, parse("41-json-mit-wrapper.json").freeWaypoints.size)
    }

    @Test
    fun jsonMitFehlerhaftenEintraegen() {
        val r = parse("42-json-mit-fehlern.json")
        assertEquals(listOf("Gut", "AuchGut"), r.freeWaypoints.map { it.name })
        assertTrue(r.skipped.any { it is SkipReason.JsonEntryNotObject })
        assertTrue(
            r.skipped.any { it is SkipReason.JsonWaypointWithoutCoordinates && it.name == "OhneBreite" }
        )
        assertTrue(
            r.skipped.any { it is SkipReason.JsonWaypointWithoutCoordinates && it.name == "BreiteZuGross" }
        )
    }

    @Test
    fun kaputtesJson() {
        val r = parse("43-json-kaputt.json")
        assertTrue(r.waypoints.isEmpty())
        assertEquals(listOf(SkipReason.InvalidJson), r.skipped)
    }

    @Test
    fun leeresJson() {
        val r = parse("44-json-leer.json")
        assertTrue(r.waypoints.isEmpty() && r.skipped.isEmpty())
    }

    @Test
    fun jsonNurZahlen() {
        val r = parse("45-json-nur-zahlen.json")
        assertTrue(r.waypoints.isEmpty())
        assertEquals(3, r.skipped.count { it is SkipReason.JsonEntryNotObject })
    }

    // ---------- Rundlauf ----------

    @Test
    fun exportUndReimportBehaeltCacheGruppe() {
        val ursprung = parse("21-cache-mit-zusatzpunkten.gpx").caches[0]
        val datei = WaypointCodec.toGpxFiles(
            waypoints = ursprung.waypoints,
            tracks = emptyList()
        ).first { it.fileName.startsWith("cache_") }
        val wieder = WaypointCodec.parse(datei.content, "Ziel")
        assertEquals(1, wieder.caches.size)
        assertEquals("Multi am Fluss", wieder.caches[0].name)
        assertEquals(4, wieder.caches[0].waypoints.size)
        assertNotNull(wieder.caches[0].code)
    }

    /**
     * Ohne groundspeak-Block gingen beim Rundlauf D/T, Hint und die
     * Archiv-Markierung verloren – der Import liest sie nur dort.
     */
    @Test
    fun exportUndReimportBehaeltWertungUndHint() {
        val ursprung = parse("22-cache-archiviert.gpx").caches[0]
        val vorher = ursprung.waypoints.first { it.cacheType != null }
        assertNotNull(vorher.difficulty)

        val datei = WaypointCodec.toGpxFiles(
            waypoints = ursprung.waypoints,
            tracks = emptyList()
        ).first { it.fileName.startsWith("cache_") }
        val wieder = WaypointCodec.parse(datei.content, "Ziel").caches[0]
        val nachher = wieder.waypoints.first { it.cacheType != null }

        assertEquals(vorher.difficulty, nachher.difficulty)
        assertEquals(vorher.terrain, nachher.terrain)
        assertEquals(vorher.hint, nachher.hint)
        assertEquals(vorher.archived, nachher.archived)
    }
}

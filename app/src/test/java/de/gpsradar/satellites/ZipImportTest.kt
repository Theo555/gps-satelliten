package de.gpsradar.satellites

import de.gpsradar.satellites.model.SkipReason
import de.gpsradar.satellites.model.WaypointCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * ZIP-Import: Opencaching liefert Suchergebnisse als Archiv mit mehreren
 * GPX-Dateien. Erwartet wird: alles wird gelesen, nichts kollidiert, fremde
 * Einträge (Bilder, __MACOSX) werden ignoriert.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ZipImportTest {

    private fun gpx(name: String, lat: Double) = """<?xml version="1.0" encoding="UTF-8"?>
<gpx version="1.1" creator="Test" xmlns="http://www.topografix.com/GPX/1/1">
  <wpt lat="$lat" lon="7.890000">
    <name>$name</name>
  </wpt>
</gpx>
"""

    private fun zipOf(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    @Test
    fun readsAllGpxFilesFromArchive() {
        val zip = zipOf(
            "PK01.gpx" to gpx("Parkplatz", 51.6901).toByteArray(),
            "OC1234.gpx" to gpx("Dose am Fluss", 51.6902).toByteArray()
        )

        val result = WaypointCodec.parseBytes(zip)

        assertEquals(listOf("Parkplatz", "Dose am Fluss"), result.waypoints.map { it.name })
        assertEquals("Kennungen müssen eindeutig sein", 2, result.waypoints.map { it.id }.toSet().size)
        assertTrue(result.skipped.isEmpty())
    }

    @Test
    fun ignoresNonDataEntries() {
        val zip = zipOf(
            "__MACOSX/._PK01.gpx" to byteArrayOf(0, 1, 2, 3),
            "PK01.gpx" to gpx("Parkplatz", 51.6901).toByteArray(),
            "hinweis.txt" to "kein Inhalt für den Import".toByteArray(),
            "bild.jpg" to byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte())
        )

        val result = WaypointCodec.parseBytes(zip)

        assertEquals(1, result.waypoints.size)
        assertEquals("Parkplatz", result.waypoints[0].name)
    }

    @Test
    fun brokenArchiveIsReportedNotThrown() {
        // Gültige ZIP-Signatur, danach Müll.
        val broken = byteArrayOf(0x50, 0x4B, 0x03, 0x04) + ByteArray(32) { 0x7F }

        val result = WaypointCodec.parseBytes(broken)

        assertTrue(result.waypoints.isEmpty())
        assertEquals(listOf<SkipReason>(SkipReason.ArchiveUnreadable), result.skipped)
    }

    @Test
    fun plainGpxStillWorksWithoutArchive() {
        val result = WaypointCodec.parseBytes(gpx("Einzeln", 51.69).toByteArray())

        assertEquals(1, result.waypoints.size)
        assertNull(result.skipped.firstOrNull())
    }

    @Test
    fun zipDetectionUsesSignature() {
        assertTrue(WaypointCodec.isZip(byteArrayOf(0x50, 0x4B, 0x03, 0x04)))
        assertTrue(!WaypointCodec.isZip(gpx("X", 51.69).toByteArray()))
        assertTrue(!WaypointCodec.isZip(ByteArray(0)))
    }
}

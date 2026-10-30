package de.gpsradar.satellites

import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.TrackPoint
import de.gpsradar.satellites.model.TrackStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackStoreTest {

    @get:Rule
    val temp = TemporaryFolder()

    private var indexJson: String? = null
    private var indexWrites = 0

    private fun store(idClock: () -> Long = { 1_000L }): TrackStore = TrackStore(
        dir = File(temp.root, "tracks"),
        readIndex = { indexJson },
        writeIndex = { json ->
            indexJson = json
            indexWrites++
        },
        nowMillis = idClock
    )

    private fun track(id: Long, name: String = "T$id", reversed: Boolean = false) = Track(
        id = id,
        name = name,
        points = listOf(
            TrackPoint(52.52, 13.404, elevationMeters = 34.5, timeMillis = 1_700_000_000_000L),
            TrackPoint(52.53, 13.405)
        ),
        reversed = reversed
    )

    private fun trackFiles() = File(temp.root, "tracks").listFiles { f -> f.name.endsWith(".json") }!!.toList()

    @Test
    fun addAndLoadKeepAllFields() {
        val s = store()
        val stored = s.add(track(7, name = "Waldweg", reversed = true))

        assertEquals(7L, stored.id)
        val loaded = s.loadAll()
        assertEquals(1, loaded.size)
        assertEquals("Waldweg", loaded[0].name)
        assertTrue(loaded[0].reversed)
        assertEquals(2, loaded[0].points.size)
        assertEquals(34.5, loaded[0].points[0].elevationMeters!!, 0.0)
        assertEquals(1_700_000_000_000L, loaded[0].points[0].timeMillis)
        assertNull(loaded[0].points[1].elevationMeters)
        assertNull(loaded[0].points[1].timeMillis)
        assertEquals(1, trackFiles().size)
    }

    @Test
    fun orderFollowsInsertion() {
        val s = store()
        s.add(track(3))
        s.add(track(1))
        s.add(track(2))
        assertEquals(listOf(3L, 1L, 2L), s.loadAll().map { it.id })
    }

    @Test
    fun updateWritesOnlyThatTrack() {
        val s = store()
        s.add(track(1))
        s.add(track(2))
        val writesBefore = indexWrites
        s.update(track(1, reversed = true))

        assertEquals(writesBefore, indexWrites) // Index bleibt unberührt
        assertEquals(listOf(1L, 2L), s.loadAll().map { it.id })
        assertTrue(s.loadAll().first { it.id == 1L }.reversed)
        assertFalse(s.loadAll().first { it.id == 2L }.reversed)
    }

    @Test
    fun deleteRemovesFileAndIndexEntry() {
        val s = store()
        s.add(track(1))
        s.add(track(2))
        s.delete(1)

        assertEquals(listOf(2L), s.loadAll().map { it.id })
        assertFalse(File(temp.root, "tracks/track_1.json").exists())
        assertTrue(File(temp.root, "tracks/track_2.json").exists())
    }

    @Test
    fun duplicateIdDoesNotOverwriteExistingTrack() {
        var clock = 5_000L
        val s = store { clock++ }
        val first = s.add(track(42, name = "Erster"))
        val second = s.add(track(42, name = "Zweiter"))

        assertEquals(42L, first.id)
        assertEquals(5_000L, second.id) // freie Kennung statt Überschreiben
        assertEquals(listOf("Erster", "Zweiter"), s.loadAll().map { it.name })
        assertEquals(2, trackFiles().size)
    }

    @Test
    fun migrationMovesLegacyTracksToFiles() {
        indexJson = null
        val legacy = legacyJson(
            track(11, name = "Hin", reversed = true),
            track(12, name = "Zurück")
        )
        var cleared = false
        val s = store()
        s.migrateLegacy(legacy) { cleared = true }

        assertTrue("alte Ablage darf erst nach Erfolg weg", cleared)
        assertNotNull(indexJson)
        assertEquals(listOf(11L, 12L), s.loadAll().map { it.id })
        assertTrue(s.loadAll()[0].reversed)
        assertEquals(2, trackFiles().size)
    }

    @Test
    fun migrationRunsOnlyOnce() {
        val s = store()
        s.migrateLegacy(legacyJson(track(1))) {}
        val countAfterFirst = trackFiles().size

        var cleared = false
        s.migrateLegacy(legacyJson(track(1))) { cleared = true }

        assertEquals(countAfterFirst, trackFiles().size) // nichts doppelt
        assertEquals(1, s.loadAll().size)
        assertTrue("Aufräumen der alten Ablage bleibt erlaubt", cleared)
    }

    @Test
    fun brokenLegacyKeepsOldStorage() {
        indexJson = null
        var cleared = false
        val s = store()
        s.migrateLegacy("{kaputt") { cleared = true }

        assertFalse("bei defektem Inhalt nichts löschen", cleared)
        assertNull(indexJson)
        assertTrue(s.loadAll().isEmpty())
    }

    @Test
    fun brokenFileIsSkippedOthersSurvive() {
        val s = store()
        s.add(track(1))
        s.add(track(2))
        File(temp.root, "tracks/track_1.json").writeText("{kaputt")

        assertEquals(listOf(2L), s.loadAll().map { it.id })
    }

    @Test
    fun emptyStoreLoadsNothing() {
        assertTrue(store().loadAll().isEmpty())
        assertNull(indexJson)
    }

    @Test
    fun legacyWithoutTracksStillClears() {
        indexJson = null
        var cleared = false
        store().migrateLegacy("[]") { cleared = true }
        assertTrue(cleared)
    }

    /** Baut die alte Sammel-Ablage (ein JSON-String) aus den Tracks nach. */
    private fun legacyJson(vararg tracks: Track): String {
        val entries = tracks.joinToString(",") { t ->
            val points = t.points.joinToString(",") { p ->
                buildString {
                    append("{\"lat\":${p.latitude},\"lon\":${p.longitude}")
                    p.elevationMeters?.let { append(",\"ele\":$it") }
                    p.timeMillis?.let { append(",\"time\":$it") }
                    append("}")
                }
            }
            "{\"id\":${t.id},\"name\":\"${t.name}\",\"points\":[$points],\"reversed\":${t.reversed}}"
        }
        return "[$entries]"
    }
}

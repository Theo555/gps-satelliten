package de.gpsradar.satellites

import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackDirectionTest {

    private val points = listOf(
        TrackPoint(0.0, 0.0),
        TrackPoint(0.0, 0.01),
        TrackPoint(0.0, 0.02)
    )

    @Test
    fun orderedPointsRespectReversedFlag() {
        val fwd = Track(id = 1L, name = "T", points = points)
        val rev = fwd.copy(reversed = true)

        assertEquals(points, fwd.orderedPoints)
        assertEquals(points.reversed(), rev.orderedPoints)
    }

    @Test
    fun nearestIndexUsesDirection() {
        val fwd = Track(id = 1L, name = "T", points = points)
        val rev = fwd.copy(reversed = true)

        // 0.019 liegt am dritten Punkt: vorwärts Index 2, rückwärts Index 0.
        assertEquals(2, fwd.nearestIndex(0.0, 0.019))
        assertEquals(0, rev.nearestIndex(0.0, 0.019))
    }

    @Test
    fun remainingMetersIndependentOfDirection() {
        val fwd = Track(id = 1L, name = "T", points = points)
        val rev = fwd.copy(reversed = true)

        assertEquals(fwd.remainingMeters(0), rev.remainingMeters(0), 1e-9)
        assertEquals(fwd.lengthMeters, rev.lengthMeters, 1e-9)
    }

    private fun eastWestTrack(): Track {
        val pts = (0..20).map { i ->
            val (la, lo) = Navigation.project(0.0, 0.0, 90.0, i * 5.0)
            TrackPoint(la, lo)
        }
        return Track(id = 1L, name = "T", points = pts)
    }

    @Test
    fun courseDegreesFollowsStraightLine() {
        val course = eastWestTrack().courseDegrees(0, lookAheadMeters = 15.0)
        assertEquals(90.0, course ?: -1.0, 1e-6)
    }

    @Test
    fun courseDegreesStaysOnLineApproachingCorner() {
        // 10 Punkte nach Osten (5 m Abstand), dann Knick nach Norden.
        val (cornerLat, cornerLon) = Navigation.project(0.0, 0.0, 90.0, 45.0)
        val pts = mutableListOf<TrackPoint>()
        for (i in 0..9) {
            val (la, lo) = Navigation.project(0.0, 0.0, 90.0, i * 5.0)
            pts += TrackPoint(la, lo)
        }
        for (i in 1..9) {
            val (la, lo) = Navigation.project(cornerLat, cornerLon, 0.0, i * 5.0)
            pts += TrackPoint(la, lo)
        }
        val track = Track(id = 1L, name = "T", points = pts)

        // Weit vor der Ecke bleibt der Kurs auf dem Ostsegment.
        assertEquals(90.0, track.courseDegrees(0, lookAheadMeters = 15.0) ?: -1.0, 1e-6)
        // Kurz vor der Ecke bleibt er zwischen Ost und Nord (kein Sprung nach Süd/West).
        val near = track.courseDegrees(8, lookAheadMeters = 15.0) ?: -1.0
        assertTrue("Kurs bei 8: $near", near >= 0.0 && near <= 90.0)
        // Ab der Ecke zeigt er nach Norden.
        assertEquals(0.0, track.courseDegrees(10, lookAheadMeters = 15.0) ?: -1.0, 1e-6)
    }

    @Test
    fun courseDegreesRespectsReverseFlag() {
        val rev = eastWestTrack().copy(reversed = true)
        assertEquals(270.0, rev.courseDegrees(0, lookAheadMeters = 15.0) ?: -1.0, 1e-6)
    }

    @Test
    fun courseDegreesNullForDegenerateTrack() {
        assertNull(Track(id = 1L, name = "T", points = emptyList()).courseDegrees(0, 15.0))
        assertNull(Track(id = 1L, name = "T", points = listOf(TrackPoint(0.0, 0.0))).courseDegrees(0, 15.0))
    }
}

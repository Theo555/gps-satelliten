package de.gpsradar.satellites

import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.TrackPoint
import org.junit.Assert.assertEquals
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
}

package de.gpsradar.satellites

import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.TrackRecorder
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Aufzeichnung ohne Android: Positionsfluss rein, Punkte raus.
 * Die Schwellen sind bewusst klein gehalten (0,0001° Breite ≈ 11 m).
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TrackRecorderTest {

    private fun state(lat: Double?, lon: Double?, time: Long = 0L) = GpsLocationState(
        latitude = lat,
        longitude = lon,
        altitudeMeters = 100.0,
        timeMillis = time
    )

    private fun TestScope.recorder(): TrackRecorder = TrackRecorder(backgroundScope, minDistanceMeters = 5.0)

    @Test
    fun firstPointImmediatelyThenOnlyAfterMinDistance() = runTest {
        val positions = MutableStateFlow(state(51.00000, 7.00000))
        val recorder = recorder()

        recorder.start(positions)
        runCurrent()
        assertEquals(1, recorder.points.value.size)

        positions.value = state(51.00000, 7.00000) // gleiche Stelle
        runCurrent()
        assertEquals(1, recorder.points.value.size)

        positions.value = state(51.00002, 7.00000) // ~2 m, zu nah
        runCurrent()
        assertEquals(1, recorder.points.value.size)

        positions.value = state(51.00010, 7.00000) // ~11 m, weit genug
        runCurrent()
        assertEquals(2, recorder.points.value.size)
    }

    @Test
    fun pointKeepsElevationAndTime() = runTest {
        val recorder = recorder()
        recorder.start(MutableStateFlow(state(51.0, 7.0, time = 1_700_000_000_000L)))
        runCurrent()

        val p = recorder.points.value.single()
        assertEquals(100.0, p.elevationMeters!!, 0.0)
        assertEquals(1_700_000_000_000L, p.timeMillis)
    }

    @Test
    fun stateWithoutPositionIsIgnored() = runTest {
        val recorder = recorder()
        recorder.start(MutableStateFlow(state(null, null)))
        runCurrent()
        assertTrue(recorder.points.value.isEmpty())
        assertTrue(recorder.isRecording.value)
    }

    @Test
    fun pauseStopsCollectingAndResumeContinues() = runTest {
        val positions = MutableStateFlow(state(51.0, 7.0))
        val recorder = recorder()
        recorder.start(positions)
        runCurrent()

        recorder.pause()
        assertTrue(recorder.isPaused.value)
        positions.value = state(51.00100, 7.0) // deutlich weiter
        runCurrent()
        assertEquals(1, recorder.points.value.size)

        recorder.resume()
        runCurrent()
        assertFalse(recorder.isPaused.value)
        assertEquals(2, recorder.points.value.size) // aktuelle Position wird nachgeholt
    }

    @Test
    fun stopReturnsPointsAndResetsEverything() = runTest {
        val positions = MutableStateFlow(state(51.0, 7.0))
        val recorder = recorder()
        recorder.start(positions)
        runCurrent()
        positions.value = state(51.00100, 7.0)
        runCurrent()

        val collected = recorder.stop()

        assertEquals(2, collected.size)
        assertFalse(recorder.isRecording.value)
        assertFalse(recorder.isPaused.value)
        assertTrue(recorder.points.value.isEmpty())
    }

    @Test
    fun discardDropsPointsWithoutReturningThem() = runTest {
        val recorder = recorder()
        recorder.start(MutableStateFlow(state(51.0, 7.0)))
        runCurrent()

        recorder.discard()

        assertFalse(recorder.isRecording.value)
        assertTrue(recorder.points.value.isEmpty())
    }

    @Test
    fun secondStartKeepsAlreadyCollectedPoints() = runTest {
        val positions = MutableStateFlow(state(51.0, 7.0))
        val recorder = recorder()
        recorder.start(positions)
        runCurrent()

        recorder.start(MutableStateFlow(state(52.0, 8.0))) // versehentlicher Doppelstart
        runCurrent()

        assertEquals(1, recorder.points.value.size)
        assertEquals(51.0, recorder.points.value.single().latitude, 0.0)
    }

    @Test
    fun resumeWithoutStartDoesNothing() = runTest {
        val recorder = recorder()
        recorder.resume()
        runCurrent()
        assertFalse(recorder.isRecording.value)
        assertTrue(recorder.points.value.isEmpty())
    }

    @Test
    fun pauseTwiceAndResumeTwiceAreHarmless() = runTest {
        val positions = MutableStateFlow(state(51.0, 7.0))
        val recorder = recorder()
        recorder.start(positions)
        runCurrent()

        recorder.pause()
        recorder.pause()
        recorder.resume()
        recorder.resume()
        runCurrent()

        assertEquals(1, recorder.points.value.size)
    }
}

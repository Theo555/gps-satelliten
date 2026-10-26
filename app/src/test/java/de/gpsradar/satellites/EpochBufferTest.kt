package de.gpsradar.satellites

import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.EpochBuffer
import de.gpsradar.satellites.model.MultipathState
import de.gpsradar.satellites.model.RawClockSnapshot
import de.gpsradar.satellites.model.RawEpoch
import de.gpsradar.satellites.model.RawMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EpochBufferTest {

    private val clock = RawClockSnapshot(
        timeNanos = 1L, fullBiasNanos = 1L, biasNanos = 0.0,
        driftNsPerSec = 0.0, leapSecond = 18, receivedAtMillis = 1L
    )

    private fun row(svid: Int) = RawMeasurement(
        svid = svid,
        constellation = Constellation.GPS,
        carrierFrequencyHz = 1575420000f,
        bandLabel = "L1",
        bandShort = "L1",
        cn0DbHz = 30f,
        pseudorangeMeters = null,
        dopplerMetersPerSec = 0.0,
        carrierPhaseMeters = null,
        phaseValid = false,
        multipath = MultipathState.UNKNOWN,
        receivedAtMillis = 1L
    )

    private fun epoch(atMillis: Long, rows: Int = 1) =
        RawEpoch(atMillis, clock, (1..rows).map { row(it) })

    @Test
    fun dropsEpochsOlderThanWindow() {
        val buffer = EpochBuffer(maxAgeMillis = 60_000)
        buffer.add(epoch(0))
        buffer.add(epoch(30_000))
        buffer.add(epoch(61_000))

        assertEquals(2, buffer.epochCount)
        assertEquals(2, buffer.rowCount)
        assertEquals(30_000L, buffer.snapshot().first().receivedAtMillis)
    }

    @Test
    fun keepsEverythingWhileRecording() {
        val buffer = EpochBuffer(maxAgeMillis = 60_000)
        buffer.keepAll = true
        buffer.add(epoch(0))
        buffer.add(epoch(120_000))
        buffer.add(epoch(240_000))

        assertEquals(3, buffer.epochCount)
    }

    @Test
    fun freezesAfterCapture() {
        val buffer = EpochBuffer(maxAgeMillis = 60_000)
        buffer.keepAll = true
        buffer.add(epoch(0))
        buffer.freeze()
        buffer.add(epoch(1_000))

        assertTrue(buffer.isFrozen)
        assertEquals(1, buffer.epochCount)
    }

    @Test
    fun clearResumesRingBufferMode() {
        val buffer = EpochBuffer(maxAgeMillis = 60_000)
        buffer.add(epoch(0))
        buffer.freeze()
        buffer.clear()
        buffer.add(epoch(10_000))

        assertFalse(buffer.isFrozen)
        assertFalse(buffer.keepAll)
        assertEquals(1, buffer.epochCount)
    }

    @Test
    fun respectsHardRowLimit() {
        val buffer = EpochBuffer(maxAgeMillis = 60_000, maxRows = 5)
        buffer.keepAll = true
        buffer.add(epoch(0, rows = 2))
        buffer.add(epoch(1_000, rows = 2))
        buffer.add(epoch(2_000, rows = 2))

        // Obergrenze 5 Zeilen: die älteste Epoche fällt weg, 4 Zeilen bleiben.
        assertEquals(4, buffer.rowCount)
        assertEquals(2, buffer.epochCount)
    }

    @Test
    fun ignoresEmptyEpochs() {
        val buffer = EpochBuffer()
        buffer.add(RawEpoch(0L, clock, emptyList()))

        assertEquals(0, buffer.epochCount)
    }
}

package de.gpsradar.satellites

import de.gpsradar.satellites.model.ArrivalAlert
import org.junit.Assert.assertEquals
import org.junit.Test

/** Schwelle des Ankunftsalarms – reine Rechnung, ohne Standort. */
class ArrivalAlertTest {

    /** Guter Empfang: 5 m bleiben 5 m. */
    @Test
    fun goodAccuracyKeepsMinimumRadius() {
        assertEquals(5.0, ArrivalAlert.thresholdMeters(4f), 0.001)
        assertEquals(5.0, ArrivalAlert.thresholdMeters(2f), 0.001)
    }

    /** Im Wald: die Schwelle folgt der Unsicherheit, statt sie zu ignorieren. */
    @Test
    fun poorAccuracyWidensTheRadius() {
        assertEquals(12.0, ArrivalAlert.thresholdMeters(12f), 0.001)
        assertEquals(20.0, ArrivalAlert.thresholdMeters(20f), 0.001)
    }

    /** Ein einzelner Ausreißer darf nicht „angekommen" melden. */
    @Test
    fun veryPoorAccuracyIsCapped() {
        assertEquals(25.0, ArrivalAlert.thresholdMeters(60f), 0.001)
    }

    /** Ohne Genauigkeitsangabe gilt der vorsichtige Mindestradius. */
    @Test
    fun missingAccuracyFallsBackToMinimum() {
        assertEquals(5.0, ArrivalAlert.thresholdMeters(null), 0.001)
    }

    /** Der Alarm schärft sich erst wieder, wenn man deutlich weg ist. */
    @Test
    fun resetHappensAtTwiceTheRadius() {
        assertEquals(10.0, ArrivalAlert.resetDistanceMeters(5.0), 0.001)
        assertEquals(24.0, ArrivalAlert.resetDistanceMeters(12.0), 0.001)
    }
}

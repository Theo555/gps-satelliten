package de.gpsradar.satellites

import de.gpsradar.satellites.model.ArrivalAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Schwelle des Ankunftsalarms – reine Rechnung, ohne Standort. */
class ArrivalAlertTest {

    /** Der Radius ist die Genauigkeit plus ein fester Zuschlag. */
    @Test
    fun radiusFollowsAccuracyPlusMargin() {
        assertEquals(5.0, ArrivalAlert.thresholdMeters(3f), 0.001)
        assertEquals(7.0, ArrivalAlert.thresholdMeters(5f), 0.001)
        assertEquals(8.0, ArrivalAlert.thresholdMeters(6f), 0.001)
        assertEquals(22.0, ArrivalAlert.thresholdMeters(20f), 0.001)
    }

    /** Gute Genauigkeit heißt strenge Schwelle – kein Mindestabstand. */
    @Test
    fun goodAccuracyDemandsCloserApproach() {
        assertEquals(4.0, ArrivalAlert.thresholdMeters(2f), 0.001)
        assertTrue(ArrivalAlert.thresholdMeters(2f) < ArrivalAlert.thresholdMeters(15f))
    }

    /** Keine Obergrenze: was das Gerät nicht weiß, darf die App nicht behaupten. */
    @Test
    fun poorAccuracyIsNotCapped() {
        assertEquals(42.0, ArrivalAlert.thresholdMeters(40f), 0.001)
        assertEquals(62.0, ArrivalAlert.thresholdMeters(60f), 0.001)
    }

    /** Ohne Genauigkeitsangabe gilt der vorsichtige Mindestradius. */
    @Test
    fun missingAccuracyFallsBackToMinimum() {
        assertEquals(5.0, ArrivalAlert.thresholdMeters(null), 0.001)
    }

    /**
     * Zurückgesetzt wird erst deutlich später – beim Suchen am Ziel bewegt man
     * sich im Grenzbereich, sonst käme die Meldung im Sekundentakt.
     */
    @Test
    fun resetHappensAtTwiceTheRadiusButNotBelow30() {
        assertEquals(30.0, ArrivalAlert.resetDistanceMeters(5.0), 0.001)
        assertEquals(30.0, ArrivalAlert.resetDistanceMeters(7.0), 0.001)
        assertEquals(40.0, ArrivalAlert.resetDistanceMeters(20.0), 0.001)
        assertEquals(120.0, ArrivalAlert.resetDistanceMeters(60.0), 0.001)
    }
}

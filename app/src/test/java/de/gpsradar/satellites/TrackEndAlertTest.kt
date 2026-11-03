package de.gpsradar.satellites

import de.gpsradar.satellites.model.TrackEndAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ankunftsalarm am Track-Ende – reine Rechnung, ohne Standort. */
class TrackEndAlertTest {

    /** Guter Empfang: es bleibt beim Radius der Anzeige. */
    @Test
    fun goodAccuracyKeepsTheMinimumRadius() {
        assertEquals(30.0, TrackEndAlert.radiusMeters(null), 0.001)
        assertEquals(30.0, TrackEndAlert.radiusMeters(3f), 0.001)
        assertEquals(30.0, TrackEndAlert.radiusMeters(20f), 0.001)
    }

    /** Im Wald: der Radius folgt der Unsicherheit, statt sie zu ignorieren. */
    @Test
    fun poorAccuracyWidensTheRadius() {
        assertEquals(38.0, TrackEndAlert.radiusMeters(38f), 0.001)
        assertEquals(50.0, TrackEndAlert.radiusMeters(50f), 0.001)
    }

    /** Ein unbrauchbarer Fix darf nicht aus 100 Metern Entfernung melden. */
    @Test
    fun veryPoorAccuracyIsCapped() {
        assertEquals(50.0, TrackEndAlert.radiusMeters(300f), 0.001)
    }

    /** Der Rücksetzwert liegt bei der doppelten Schwelle. */
    @Test
    fun resetHappensAtTwiceTheRadius() {
        assertEquals(60.0, TrackEndAlert.armMeters(30.0), 0.001)
        assertEquals(80.0, TrackEndAlert.armMeters(40.0), 0.001)
    }

    /** Auf dem Weg: weit weg vom Ende, der Alarm wird scharf gestellt. */
    @Test
    fun farFromTheEndArmsTheAlarm() {
        val r = TrackEndAlert.update(800.0, null, armed = false, alerted = false)
        assertTrue(r.armed)
        assertFalse(r.alerted)
        assertFalse(r.trigger)
    }

    /** Am Ende angekommen: einmal melden. */
    @Test
    fun approachingTheEndTriggersOnce() {
        val r = TrackEndAlert.update(12.0, null, armed = true, alerted = false)
        assertTrue(r.trigger)
        assertTrue(r.alerted)
    }

    /** Wer stehen bleibt, bekommt die Meldung nicht im Sekundentakt. */
    @Test
    fun secondFixAtTheEndStaysQuiet() {
        val r = TrackEndAlert.update(12.0, null, armed = true, alerted = true)
        assertFalse(r.trigger)
    }

    /** Bei schlechtem Empfang meldet er entsprechend früher. */
    @Test
    fun poorAccuracyTriggersEarlier() {
        // 40 m Genauigkeit -> Radius 40 m. Bei 35 m Reststrecke wäre der
        // Mindestradius von 30 m noch nicht erreicht.
        val exact = TrackEndAlert.update(35.0, null, armed = true, alerted = false)
        assertFalse(exact.trigger)

        val noisy = TrackEndAlert.update(35.0, 40f, armed = true, alerted = false)
        assertTrue(noisy.trigger)
    }

    /**
     * Start am Track-Ende: Die Reststrecke ist sofort klein, der Alarm war aber
     * nie scharf – er darf nicht grundlos losgehen.
     */
    @Test
    fun startAtTheEndDoesNotAlarm() {
        var r = TrackEndAlert.update(4.0, null, armed = false, alerted = false)
        assertFalse(r.trigger)
        assertFalse(r.armed)

        // Weggehen schärft ihn, zurückkommen meldet dann.
        r = TrackEndAlert.update(300.0, null, armed = r.armed, alerted = r.alerted)
        assertTrue(r.armed)
        r = TrackEndAlert.update(10.0, null, armed = r.armed, alerted = r.alerted)
        assertTrue(r.trigger)
    }

    /** Genau auf der Schwelle gilt das Ende noch nicht als erreicht. */
    @Test
    fun radiusIsExclusive() {
        val r = TrackEndAlert.update(30.0, null, armed = true, alerted = false)
        assertFalse(r.trigger)
    }

    /** Zwischen Schwelle und Sperre bleibt die Meldung aus. */
    @Test
    fun betweenRadiusAndArmIsQuiet() {
        val r = TrackEndAlert.update(45.0, null, armed = true, alerted = false)
        assertFalse(r.trigger)
        assertTrue(r.armed)
    }

    /** Weggehen und wiederkommen darf erneut melden. */
    @Test
    fun comingBackAfterAlarmTriggersAgain() {
        val far = TrackEndAlert.update(1200.0, null, armed = true, alerted = true)
        assertTrue(far.armed)
        assertFalse(far.alerted)

        val back = TrackEndAlert.update(15.0, null, armed = far.armed, alerted = far.alerted)
        assertTrue(back.trigger)
    }
}

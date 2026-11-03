package de.gpsradar.satellites

import de.gpsradar.satellites.model.TrackEndAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ankunftsalarm am Track-Ende – reine Rechnung, ohne Standort. */
class TrackEndAlertTest {

    /** Guter Empfang: es bleibt bei Genauigkeit plus Zuschlag. */
    @Test
    fun radiusFollowsAccuracy() {
        assertEquals(7.0, TrackEndAlert.radiusMeters(5f), 0.001)
        assertEquals(22.0, TrackEndAlert.radiusMeters(20f), 0.001)
        assertEquals(5.0, TrackEndAlert.radiusMeters(null), 0.001)
    }

    /** Kein Deckel – dieselbe Regel wie beim Zielpunkt. */
    @Test
    fun poorAccuracyIsNotCapped() {
        assertEquals(42.0, TrackEndAlert.radiusMeters(40f), 0.001)
    }

    /** Zurückgesetzt wird erst bei mindestens 30 m Abstand. */
    @Test
    fun resetHappensAtLeast30MetersAway() {
        assertEquals(30.0, TrackEndAlert.armMeters(7.0), 0.001)
        assertEquals(44.0, TrackEndAlert.armMeters(22.0), 0.001)
    }

    /** Auf dem Weg: weit weg vom Ende, der Alarm wird scharf gestellt. */
    @Test
    fun farFromTheEndArmsTheAlarm() {
        val r = TrackEndAlert.update(800.0, null, armed = false, alerted = false)
        assertTrue(r.armed)
        assertFalse(r.alerted)
        assertFalse(r.trigger)
    }

    /** Ein einzelner Fix unter der Schwelle reicht noch nicht. */
    @Test
    fun oneFixBelowRadiusStaysQuiet() {
        val r = TrackEndAlert.update(4.0, null, armed = true, alerted = false)
        assertFalse(r.trigger)
        assertEquals(1, r.hits)
    }

    /** Zwei Messungen in Folge: jetzt melden. */
    @Test
    fun secondFixTriggers() {
        val r = TrackEndAlert.update(4.0, null, armed = true, alerted = false, hits = 1)
        assertTrue(r.trigger)
        assertTrue(r.alerted)
    }

    /** Ein Ausrutscher über die Schwelle setzt den Zähler zurück. */
    @Test
    fun aFixOutsideResetsTheCounter() {
        val r = TrackEndAlert.update(9.0, null, armed = true, alerted = false, hits = 1)
        assertFalse(r.trigger)
        assertEquals(0, r.hits)
    }

    /** Wer stehen bleibt, bekommt die Meldung nicht im Sekundentakt. */
    @Test
    fun stayingAtTheEndStaysQuiet() {
        val r = TrackEndAlert.update(4.0, null, armed = true, alerted = true, hits = 2)
        assertFalse(r.trigger)
    }

    /** Bei schlechtem Empfang meldet er entsprechend früher. */
    @Test
    fun poorAccuracyTriggersEarlier() {
        // 40 m Genauigkeit -> Radius 42 m. Bei 35 m Reststrecke wäre der
        // Mindestradius von 5 m noch nicht erreicht – hier aber schon, und
        // zwar bei der zweiten Messung in Folge.
        val exact = TrackEndAlert.update(35.0, null, armed = true, alerted = false, hits = 1)
        assertFalse(exact.trigger)

        val noisy = TrackEndAlert.update(35.0, 40f, armed = true, alerted = false, hits = 1)
        assertTrue(noisy.trigger)
    }

    /** Start am Track-Ende: Die Reststrecke ist sofort klein, der Alarm war aber
     * nie scharf – er darf nicht grundlos losgehen.
     */
    @Test
    fun startAtTheEndDoesNotAlarm() {
        var r = TrackEndAlert.update(4.0, null, armed = false, alerted = false)
        assertFalse(r.trigger)
        assertFalse(r.armed)

        // Weggehen schärft ihn, zurückkommen meldet dann.
        r = TrackEndAlert.update(300.0, null, armed = r.armed, alerted = r.alerted, hits = r.hits)
        assertTrue(r.armed)
        r = TrackEndAlert.update(3.0, null, armed = r.armed, alerted = r.alerted, hits = 1)
        assertTrue(r.trigger)
    }

    /** Genau auf der Schwelle gilt das Ende noch nicht als erreicht. */
    @Test
    fun radiusIsExclusive() {
        val r = TrackEndAlert.update(5.0, null, armed = true, alerted = false)
        assertFalse(r.trigger)
    }

    /** Zwischen Schwelle und Sperre bleibt die Meldung aus. */
    @Test
    fun betweenRadiusAndArmIsQuiet() {
        val r = TrackEndAlert.update(12.0, null, armed = true, alerted = false)
        assertFalse(r.trigger)
        assertTrue(r.armed)
    }

    /** Weggehen und wiederkommen darf erneut melden. */
    @Test
    fun comingBackAfterAlarmTriggersAgain() {
        val far = TrackEndAlert.update(1200.0, null, armed = true, alerted = true, hits = 2)
        assertTrue(far.armed)
        assertFalse(far.alerted)

        val back = TrackEndAlert.update(3.0, null, armed = far.armed, alerted = far.alerted, hits = 1)
        assertTrue(back.trigger)
    }
}

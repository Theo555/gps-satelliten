package de.gpsradar.satellites

import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Vorrücken im Track. Der Kern: Bei Tempo darf der Zeiger mehrere Punkte
 * springen, sonst bleibt er bei schneller Fahrt für immer zurück (Fund vom
 * 03.11., Autofahrt mit 38–47 km/h).
 */
class TrackAdvanceTest {

    /** Gerade Linie nach Norden, Punkte ~10 m auseinander (0,00009° Breite). */
    private fun straightTrack(count: Int): Track = Track(
        id = 1L,
        name = "Test",
        points = (0 until count).map {
            TrackPoint(latitude = 51.0 + it * 0.00009, longitude = 7.0)
        }
    )

    private fun advance(track: Track, from: Int, index: Int, maxJump: Double = 400.0): Int {
        val p = track.points[index]
        return track.advanceIndex(
            fromIndex = from,
            latitude = p.latitude,
            longitude = p.longitude,
            thresholdMeters = 25.0,
            maxJumpMeters = maxJump
        )
    }

    /** Steht man auf einem Punkt, rückt der Zeiger genau dorthin. */
    @Test
    fun positionOnAPointAdvancesToIt() {
        val t = straightTrack(50)
        assertEquals(10, advance(t, from = 8, index = 10))
    }

    /** Bei Tempo: 40 Punkte Rückstand werden in einem Satz aufgeholt. */
    @Test
    fun fastTravelCatchesUpSeveralPointsAtOnce() {
        val t = straightTrack(200)
        // Der Zeiger steht bei Punkt 10, die Position ist schon bei Punkt 50.
        assertEquals(50, advance(t, from = 10, index = 50))
    }

    /** Es geht nie rückwärts, auch nicht nach einem Neustart der Ortung. */
    @Test
    fun neverMovesBackwards() {
        val t = straightTrack(100)
        assertEquals(40, advance(t, from = 40, index = 12))
    }

    /** Weit weg von der Linie: der Zeiger bleibt stehen. */
    @Test
    fun farFromTheLineKeepsTheIndex() {
        val t = straightTrack(50)
        val p = t.points[20]
        val moved = t.advanceIndex(
            fromIndex = 18,
            latitude = p.latitude,
            longitude = p.longitude + 0.003, // ~200 m seitlich daneben
            thresholdMeters = 25.0,
            maxJumpMeters = 400.0
        )
        assertEquals(18, moved)
    }

    /** Die Sprungweite ist begrenzt – eine große Schleife wird nicht übersprungen. */
    @Test
    fun jumpIsLimitedToTheConfiguredDistance() {
        val t = straightTrack(400)
        // Position bei Punkt 48, Zeiger bei Punkt 10 (380 m Rückstand).
        // Mit 400 m Sprungweite wird sie erreicht …
        assertEquals(48, advance(t, from = 10, index = 48, maxJump = 400.0))
        // … mit 200 m liegt kein Punkt der Linie mehr in Reichweite.
        assertEquals(10, advance(t, from = 10, index = 48, maxJump = 200.0))
    }

    /** Am Ende angekommen: der Index bleibt auf dem letzten Punkt. */
    @Test
    fun staysAtLastPoint() {
        val t = straightTrack(30)
        assertEquals(29, advance(t, from = 29, index = 29))
    }
}

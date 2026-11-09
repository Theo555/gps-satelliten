package de.gpsradar.satellites

import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.TrackPoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Wächter gegen quadratischen Aufwand in [Track].
 *
 * Hintergrund: `orderedPoints` war eine berechnete Eigenschaft, die bei jedem
 * Zugriff `points.reversed()` ausführte – also eine neue Liste baute. In
 * `remainingMeters()` stand dieser Zugriff in der Schleife, damit wurde der
 * Aufwand quadratisch: bei 891 Punkten rund 4 Millionen kopierte Punkte pro
 * Aufruf. Die Funktion läuft bei jeder Positionsmeldung (15 Hz) und bei jedem
 * Neuzeichnen der Statuskarte, das Gerät fror deshalb ein. Ein Track mit 63
 * Punkten war unauffällig (rund 20 000 Kopien).
 *
 * Der Test zählt die Lesezugriffe auf die Punktliste und verlangt lineares
 * Verhalten – unabhängig von der Rechengeschwindigkeit der Maschine.
 */
class TrackCostTest {

    /** Liste, die mitzählt, wie oft auf sie zugegriffen wird. */
    private class CountingList<T>(private val inner: List<T>) : AbstractList<T>() {
        var reads = 0
            private set

        override val size: Int
            get() {
                reads++
                return inner.size
            }

        override fun get(index: Int): T {
            reads++
            return inner[index]
        }
    }

    private fun longTrack(n: Int, reversed: Boolean): Pair<Track, CountingList<TrackPoint>> {
        val pts = (0 until n).map { i -> TrackPoint(51.0 + i * 1e-5, 7.9 + i * 1e-5) }
        val counting = CountingList(pts)
        return Track(id = 1L, name = "T", points = counting, reversed = reversed) to counting
    }

    /** Anzahl Listen-Zugriffe für einen Kaltaufruf von `remainingMeters(0)`. */
    private fun readsFor(n: Int): Int {
        val (track, counting) = longTrack(n, reversed = true)
        track.remainingMeters(0)
        return counting.reads
    }

    @Test
    fun remainingMetersStaysLinear() {
        val small = readsFor(400)
        val big = readsFor(1600)

        // Viermal so viele Punkte dürfen höchstens etwa viermal so viele
        // Zugriffe kosten. Vor dem Fix waren es rund 16-mal so viele
        // (quadratisch): 891 Punkte brauchten 9 529 236 Zugriffe.
        assertTrue(
            "Zugriffe 400 Punkte: $small, 1600 Punkte: $big",
            big <= 5 * small
        )
    }

    @Test
    fun singlePassOverTheTrackIsEnough() {
        val (track, counting) = longTrack(891, reversed = true)

        track.remainingMeters(0)
        track.lengthMeters
        val first = counting.reads
        // Weitere Aufrufe (bei jeder Positionsmeldung) dürfen nichts mehr kosten.
        repeat(50) {
            track.remainingMeters(0)
            track.lengthMeters
        }

        assertEquals("Zugriffe beim zweiten Durchlauf", first, counting.reads)
    }

    @Test
    fun remainingMetersCountsToTheEndInBothDirections() {
        val n = 63
        val (fwd, _) = longTrack(n, reversed = false)
        val (rev, _) = longTrack(n, reversed = true)

        assertEquals(fwd.lengthMeters, fwd.remainingMeters(0), 1e-6)
        assertEquals(rev.lengthMeters, rev.remainingMeters(0), 1e-6)
        // Ein Schritt vor dem Ende bleibt ein Punktabstand übrig.
        assertTrue(fwd.remainingMeters(fwd.points.lastIndex) == 0.0)
        assertTrue(rev.remainingMeters(rev.points.lastIndex) == 0.0)
        // Aus der Mitte ist die Reststrecke echt kleiner als die Gesamtlänge.
        val half = fwd.remainingMeters(n / 2)
        assertTrue("Rest $half von ${fwd.lengthMeters}", half > 0.0 && half < fwd.lengthMeters)
    }
}

package de.gpsradar.satellites

import de.gpsradar.satellites.model.CompassWalk
import de.gpsradar.satellites.model.WalkSample
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Rechenwege des Geh-Tests – ohne Sensor, ohne Datei. */
class CompassWalkTest {

    private fun sample(
        shown: Float,
        magnetic: Float? = null,
        course: Float? = null
    ) = WalkSample(
        timeMillis = 0L,
        rawDegrees = shown,
        shownDegrees = shown,
        magneticDegrees = magnetic,
        courseDegrees = course,
        fieldMicroTesla = 48f,
        tiltDegrees = 0f
    )

    /** Über die 360°-Grenze hinweg darf das Mittel nicht auf 180° fallen. */
    @Test
    fun circularMeanWrapsAroundNorth() {
        val mean = CompassWalk.circularMean(listOf(358f, 359f, 1f, 2f))!!
        assertTrue("Mittel sollte nahe 0° liegen, war $mean", mean < 1f || mean > 359f)
    }

    @Test
    fun circularMeanReturnsNullForOppositeValues() {
        assertNull(CompassWalk.circularMean(listOf(0f, 180f)))
        assertNull(CompassWalk.circularMean(emptyList()))
    }

    @Test
    fun shortestDeltaUsesSignedShortestWay() {
        assertEquals(10f, CompassWalk.shortestDelta(20f, 10f), 0.001f)
        assertEquals(-10f, CompassWalk.shortestDelta(10f, 20f), 0.001f)
        assertEquals(10f, CompassWalk.shortestDelta(5f, 355f), 0.001f)
        assertEquals(-10f, CompassWalk.shortestDelta(355f, 5f), 0.001f)
    }

    /**
     * Der eigentliche Zweck: Rechnet die Plattform die Missweisung schon ein,
     * steht in den Daten 0°. Rechnet sie es nicht, steht dort der Aufschlag.
     */
    @Test
    fun shownVersusMagneticReportsTheOffset() {
        val samples = listOf(
            sample(shown = 263.83f, magnetic = 260f),
            sample(shown = 33.83f, magnetic = 30f),
            sample(shown = 183.83f, magnetic = 180f)
        )
        assertEquals(3.83f, CompassWalk.shownVsMagnetic(samples)!!, 0.05f)
    }

    @Test
    fun shownVersusMagneticIsZeroWhenNothingIsAdded() {
        val samples = listOf(sample(shown = 100f, magnetic = 100f), sample(101f, 101f))
        assertEquals(0f, CompassWalk.shownVsMagnetic(samples)!!, 0.001f)
    }

    /** Ohne GPS-Kurs darf nichts herauskommen – 0° wäre eine erfundene Messung. */
    @Test
    fun shownVersusCourseNeedsACourse() {
        val withoutCourse = listOf(sample(shown = 100f, magnetic = 96f))
        assertNull(CompassWalk.shownVsCourse(withoutCourse))
        assertEquals(0, CompassWalk.courseSampleCount(withoutCourse))
    }

    @Test
    fun shownVersusCourseAveragesOnlyMovingSamples() {
        val samples = listOf(
            sample(shown = 264f, course = 260f),
            sample(shown = 100f, magnetic = 96f),
            sample(shown = 40f, course = 41f)
        )
        assertEquals(1.5f, CompassWalk.shownVsCourse(samples)!!, 0.05f)
        assertEquals(2, CompassWalk.courseSampleCount(samples))
    }

    /** Ein Sprung um 180° darf das Mittel nicht kippen, wenn er nur kurz ist. */
    @Test
    fun briefOutlierDoesNotDominateTheMean() {
        val samples = (0 until 20).map { sample(shown = 100f, course = 100f) } +
            sample(shown = 280f, course = 100f)
        val mean = CompassWalk.shownVsCourse(samples)!!
        assertTrue("Mittel sollte klein bleiben, war $mean", mean < 60f)
    }
}

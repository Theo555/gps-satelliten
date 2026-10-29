package de.gpsradar.satellites

import de.gpsradar.satellites.model.Navigation
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrowReferenceTest {

    @Test
    fun gpsCourseUsedWhenMoving() {
        assertEquals(
            90f,
            Navigation.arrowReferenceDegrees(speedMps = 1.2f, bearingDegrees = 90f, compassHeading = 200f, wasUsingGpsCourse = false),
            1e-6f
        )
    }

    @Test
    fun compassUsedWhenStanding() {
        assertEquals(
            200f,
            Navigation.arrowReferenceDegrees(speedMps = 0f, bearingDegrees = 90f, compassHeading = 200f, wasUsingGpsCourse = false),
            1e-6f
        )
    }

    @Test
    fun belowEnterThresholdUsesCompass() {
        assertEquals(
            200f,
            Navigation.arrowReferenceDegrees(speedMps = 0.7f, bearingDegrees = 90f, compassHeading = 200f, wasUsingGpsCourse = false),
            1e-6f
        )
    }

    @Test
    fun hysteresisKeepsGpsCourseAboveExitThreshold() {
        assertEquals(
            90f,
            Navigation.arrowReferenceDegrees(speedMps = 0.6f, bearingDegrees = 90f, compassHeading = 200f, wasUsingGpsCourse = true),
            1e-6f
        )
    }

    @Test
    fun hysteresisFallsBackToCompassBelowExitThreshold() {
        assertEquals(
            200f,
            Navigation.arrowReferenceDegrees(speedMps = 0.4f, bearingDegrees = 90f, compassHeading = 200f, wasUsingGpsCourse = true),
            1e-6f
        )
    }

    @Test
    fun missingBearingFallsBackToCompass() {
        assertEquals(
            200f,
            Navigation.arrowReferenceDegrees(speedMps = 1.5f, bearingDegrees = null, compassHeading = 200f, wasUsingGpsCourse = false),
            1e-6f
        )
    }
}

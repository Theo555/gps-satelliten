package de.gpsradar.satellites

import de.gpsradar.satellites.model.CompassHealth
import de.gpsradar.satellites.model.CompassIssue
import org.junit.Assert.assertEquals
import org.junit.Test

/** Grenzen der Kompass-Bewertung, ohne Sensor und ohne Robolectric. */
class CompassHealthTest {

    /** Erdfeld in Deutschland – der Normalfall darf keine Warnung auslösen. */
    @Test
    fun germanFieldIsOk() {
        assertEquals(CompassIssue.NONE, CompassHealth.issue(48f, accuracyLow = false))
    }

    /** Büro mit Störquelle: 112,8 µT gemessen. */
    @Test
    fun strongFieldIsFlagged() {
        assertEquals(
            CompassIssue.FIELD_TOO_STRONG,
            CompassHealth.issue(112.8f, accuracyLow = false)
        )
    }

    /** Abgeschirmt, etwa zwischen Metall. */
    @Test
    fun weakFieldIsFlagged() {
        assertEquals(CompassIssue.FIELD_TOO_WEAK, CompassHealth.issue(18f, accuracyLow = false))
    }

    /** Grenzen selbst gehören noch zum Normalbereich (30 und 70 µT). */
    @Test
    fun boundsAreInclusive() {
        assertEquals(CompassIssue.NONE, CompassHealth.issue(30f, accuracyLow = false))
        assertEquals(CompassIssue.NONE, CompassHealth.issue(70f, accuracyLow = false))
    }

    /** Ohne genug Proben gibt es keinen Befund. */
    @Test
    fun noSamplesIsNoIssue() {
        assertEquals(CompassIssue.NONE, CompassHealth.issue(null, accuracyLow = false))
    }

    /** Meldet der Sensor selbst schlechte Genauigkeit, hat das Vorrang. */
    @Test
    fun accuracyWinsOverField() {
        assertEquals(CompassIssue.UNRELIABLE, CompassHealth.issue(48f, accuracyLow = true))
        assertEquals(CompassIssue.UNRELIABLE, CompassHealth.issue(null, accuracyLow = true))
    }
}

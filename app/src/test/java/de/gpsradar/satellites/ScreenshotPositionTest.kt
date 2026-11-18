package de.gpsradar.satellites

import de.gpsradar.satellites.model.ScreenshotPosition
import de.gpsradar.satellites.model.displayedPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Ersatz-Koordinate für Bildschirmaufnahmen.
 *
 * Der Schalter selbst ist nur im Debug-Build zu sehen (siehe
 * [AboutScreenshotTest]); hier geht es um die Werte und die Anwendung: Ein
 * Ersatzort muss dort liegen, wo er behauptet, und `null` darf die Messung nicht
 * anfassen.
 */
class ScreenshotPositionTest {

    /** Ein Knopf schaltet weiter: aus → drei Orte → aus. */
    @Test
    fun ringSchaltetWeiterUndZurueckAufAus() {
        assertEquals(ScreenshotPosition.BRANDENBURGER_TOR, ScreenshotPosition.next(null))
        assertEquals(
            ScreenshotPosition.DEUTSCHES_MUSEUM,
            ScreenshotPosition.next(ScreenshotPosition.BRANDENBURGER_TOR)
        )
        assertEquals(
            ScreenshotPosition.ATOMIUM,
            ScreenshotPosition.next(ScreenshotPosition.DEUTSCHES_MUSEUM)
        )
        assertNull(ScreenshotPosition.next(ScreenshotPosition.ATOMIUM))
    }

    /**
     * Die Orte müssen dort liegen, wo ihr Name sagt: Deutschland und Belgien
     * liegen zwischen 47° und 53° Nord und 4° bis 14° Ost. Ein Zahlendreher in
     * den Koordinaten fällt sonst erst auf der Aufnahme auf — und dann steht
     * eine Position im Atlantik auf dem Bildschirm.
     */
    @Test
    fun orteLiegenInMitteleuropa() {
        for (ort in ScreenshotPosition.entries) {
            assertTrue("Breite von ${ort.placeName}", ort.latitude in 47.0..53.5)
            assertTrue("Länge von ${ort.placeName}", ort.longitude in 4.0..14.0)
            assertTrue("Höhe von ${ort.placeName}", ort.altitudeMeters > 0.0)
        }
        assertEquals(
            ScreenshotPosition.entries.size,
            ScreenshotPosition.entries.map { it.placeName }.distinct().size
        )
    }

    /** Ohne Ersatz bleibt die Messung unangetastet — auch eine fehlende Höhe. */
    @Test
    fun ohneErsatzBleibtDieMessung() {
        val anzeige = displayedPosition(null, 51.6905, 7.8923, null)

        assertEquals(51.6905, anzeige.latitude, 1e-9)
        assertEquals(7.8923, anzeige.longitude, 1e-9)
        assertNull(anzeige.altitudeMeters)
        assertFalse(anzeige.replaced)
    }

    /** Mit Ersatz stehen genau die Werte des Ortes in der Anzeige. */
    @Test
    fun mitErsatzStehenDieWerteDesOrtes() {
        val ort = ScreenshotPosition.ATOMIUM
        val anzeige = displayedPosition(ort, 51.6905, 7.8923, 113.0)

        assertEquals(ort.latitude, anzeige.latitude, 1e-9)
        assertEquals(ort.longitude, anzeige.longitude, 1e-9)
        assertEquals(ort.altitudeMeters, anzeige.altitudeMeters!!, 1e-9)
        assertTrue(anzeige.replaced)
    }
}

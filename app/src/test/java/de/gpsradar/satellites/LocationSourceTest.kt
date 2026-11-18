package de.gpsradar.satellites

import de.gpsradar.satellites.gps.LocationSource
import de.gpsradar.satellites.gps.LocationSources
import de.gpsradar.satellites.gps.shouldDefer
import de.gpsradar.satellites.model.FixOrigin
import de.gpsradar.satellites.model.GpsLocationState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Naht für zusätzliche Ortsquellen und die Vorrangregel.
 *
 * Anlass war der Zwei-Zweig-Workflow: Die Netzwerkortung der persönlichen
 * Ausbaustufe lag mitten in `GpsDataManager` und machte bei jedem Merge
 * Handarbeit. Jetzt entscheidet eine Angabe in der Quelle
 * ([LocationSource.yieldsToGnss]) statt einer Verzweigung im Code — und der
 * öffentliche Build liefert schlicht keine Quelle.
 */
class LocationSourceTest {

    private val zelle = LocationSource(
        provider = "network",
        minTimeMillis = 2000L,
        minDistanceMeters = 2f
    )

    @Test
    fun derOeffentlicheBuildFragtNurGnss() {
        // Der Kern der Trennung: keine Quelle, kein Netzwerkzugriff.
        assertEquals(emptyList<LocationSource>(), LocationSources.create())
    }

    @Test
    fun ohneSatellitenOrtetDieZweiteQuelle() {
        assertFalse(zelle.shouldDefer(satellitesInFix = 0))
    }

    @Test
    fun beiLaufenderGnssOrtungSchweigtDieZweiteQuelle() {
        // Auch ein schwacher Fix mit drei Satelliten ist eine Messung und damit
        // besser als eine Funkzelle.
        assertTrue(zelle.shouldDefer(satellitesInFix = 3))
        assertTrue(zelle.shouldDefer(satellitesInFix = 9))
    }

    @Test
    fun eineQuelleOhneVorrangregelBleibtAktiv() {
        val eigenstaendig = zelle.copy(yieldsToGnss = false)
        assertFalse(eigenstaendig.shouldDefer(satellitesInFix = 9))
    }

    @Test
    fun derOeffentlicheZustandIstImmerGnss() {
        // Die Oberfläche darf sich auf diese Vorgabe verlassen: ohne zweite
        // Quelle steht dort nie eine fremde Herkunft.
        assertEquals(FixOrigin.GNSS, GpsLocationState().fixOrigin)
    }
}

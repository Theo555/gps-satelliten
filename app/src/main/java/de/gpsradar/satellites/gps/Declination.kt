package de.gpsradar.satellites.gps

import android.hardware.GeomagneticField
import android.util.Log

/**
 * Ortsmissweisung (Deklination) aus dem Weltmodell des Erdmagnetfelds.
 *
 * Der Kompass misst zum **magnetischen** Norden, der GPS-Kurs gilt für **wahres**
 * Norden. Ohne Ausgleich liegen beide systematisch auseinander – in Deutschland
 * um rund 3°, in anderen Regionen bis zu 20°. Da die App die beiden Quellen
 * umschaltet (Kurs beim Gehen, Kompass im Stand), müssen sie denselben Bezug
 * haben.
 *
 * Gerechnet wird mit [GeomagneticField] aus der Plattform: das Weltmodell ist
 * dort mitgeliefert, offline und ohne Berechtigung. Wie aktuell es ist, hängt
 * vom Android-Stand des Geräts ab – ein älteres Modell kostet Bruchteile eines
 * Grades pro Jahr.
 *
 * Das Objekt wird zwischengespeichert: Der Konstruktor wertet den
 * Koeffizientensatz aus und ist viel zu teuer für jedes Sensorereignis.
 */
class Declination {

    private var cachedLatitude = Double.NaN
    private var cachedLongitude = Double.NaN
    private var cachedDegrees = 0f

    /**
     * Deklination in Grad, positiv nach Osten.
     *
     * Ohne Position gibt es keine Aussage – dann 0, denn eine geratene
     * Korrektur wäre schlimmer als keine.
     */
    fun degreesAt(latitude: Double?, longitude: Double?, altitudeMeters: Double?, timeMillis: Long): Float {
        if (latitude == null || longitude == null) return 0f
        // Erst ab etwa einem Kilometer neu rechnen (0,01° ≈ 1,1 km). So bleibt
        // die Auswertung aus dem Sensorpfad heraus.
        if (
            !cachedLatitude.isNaN() &&
            kotlin.math.abs(latitude - cachedLatitude) < CACHE_GRID_DEGREES &&
            kotlin.math.abs(longitude - cachedLongitude) < CACHE_GRID_DEGREES
        ) {
            return cachedDegrees
        }
        return try {
            val field = GeomagneticField(
                latitude.toFloat(),
                longitude.toFloat(),
                (altitudeMeters ?: 0.0).toFloat(),
                timeMillis
            )
            cachedLatitude = latitude
            cachedLongitude = longitude
            cachedDegrees = field.declination
            Log.i(
                "Declination",
                "Deklination ${cachedDegrees}° bei ${latitude}/${longitude}"
            )
            cachedDegrees
        } catch (e: Exception) {
            // Kein Modell, keine Korrektur – aber bitte keine falsche Zahl.
            0f
        }
    }

    private companion object {
        const val CACHE_GRID_DEGREES = 0.01
    }
}

package de.gpsradar.satellites.model

/**
 * Ein Almanach-Satz: grobe Bahn und Uhr eines Satelliten.
 *
 * ## Wozu, wenn die Ephemeride genauer ist
 *
 * Der Almanach ist eine Kurzfassung derselben Bahn über einen viel längeren
 * Zeitraum (GPS lädt ihn etwa alle sechs Tage neu hoch, gültig bleibt er
 * Wochen). Damit lässt sich beantworten, **welcher Satellit wann an welchem Ort
 * steht**, ohne auf seine Ephemeride zu warten — die Ephemeride gilt nur vier
 * Stunden und wird ständig ersetzt.
 *
 * Für die Nachverarbeitung mit RTKLIB ist er **nicht** nötig: RTKLIB und RINEX
 * kennen keinen Almanach, gerechnet wird ausschließlich mit Ephemeriden. Er
 * steht hier als eigene Auswertung, damit vollständig ist, was das Gerät sendet.
 *
 * ## Einheiten
 *
 * Alle Winkel in **Radiant**, `sqrtA` in Wurzelmetern, Uhr in Sekunden. Die
 * Umrechnung aus den Signal-Einheiten (halbe Kreise, Skalenfaktoren) macht der
 * jeweilige Dekoder, nicht der Aufrufer.
 */
data class Almanac(
    /** Kurzname des Systems: „GPS“, „BDS“, „GAL“. */
    val system: String,
    /** Satellitennummer im System (PRN). */
    val svid: Int,
    /**
     * Bezugswoche. Steht **nicht** auf der Almanach-Seite selbst, sondern auf
     * der letzten Seite des Rahmens; der Sammler setzt sie aus der Empfangszeit.
     */
    val week: Int,
    /** Bezugszeit `toa` in Sekunden seit Wochenbeginn. */
    val referenceSeconds: Int,
    /** Wurzel der großen Halbachse (√m). */
    val sqrtA: Double,
    val eccentricity: Double,
    /** Bahnneigung, absolut (nicht die Abweichung von einem Bezugswert). */
    val inclination: Double,
    /** Rektaszension des aufsteigenden Knotens. */
    val ascendingNode: Double,
    val argumentOfPerigee: Double,
    val meanAnomaly: Double,
    /** Änderungsrate des aufsteigenden Knotens (rad/s). */
    val nodeRate: Double,
    /** Uhrversatz `af0` in Sekunden. */
    val clockBias: Double,
    /** Uhrgang `af1` in Sekunden je Sekunde. */
    val clockRate: Double,
    /** Gesundheitskennung, 0 = gesund. */
    val health: Int
) {
    val isHealthy: Boolean get() = health == 0

    /** Große Halbachse in Metern. */
    val semiMajorAxis: Double get() = sqrtA * sqrtA

    /**
     * Plausibel für einen Satelliten im mittleren Erdorbit?
     *
     * GPS liegt bei 26 560 km, Galileo bei 29 600 km, BeiDou bei 27 900 km
     * (GEO-Satelliten 42 000 km). Die Grenzen sind bewusst weit: Sie sollen
     * einen verdrehten Satz abweisen, nicht die Systeme unterscheiden.
     */
    val isPlausible: Boolean
        get() = sqrtA in 5_000.0..6_600.0 &&
            eccentricity >= 0.0 && eccentricity <= 0.03
}

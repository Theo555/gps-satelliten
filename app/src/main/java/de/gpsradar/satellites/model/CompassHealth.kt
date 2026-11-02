package de.gpsradar.satellites.model

/**
 * Grund, warum der Kompass als unsicher gilt.
 *
 * [UNRELIABLE] kommt vom Sensor selbst (Magnetometer meldet geringe Genauigkeit,
 * typisch für "nicht kalibriert"), die beiden Feld-Fälle werden aus dem
 * gemessenen Feldbetrag abgeleitet.
 */
enum class CompassIssue {
    NONE,
    UNRELIABLE,
    FIELD_TOO_WEAK,
    FIELD_TOO_STRONG
}

/**
 * Bewertet die Kompassgesundheit aus Messwerten.
 *
 * Bewusst eine reine Funktion ohne Sensoren: so sind die Grenzen ohne
 * Robolectric prüfbar (siehe CompassHealthTest).
 */
object CompassHealth {

    /**
     * Erdfeld liegt weltweit zwischen etwa 22 und 67 µT, in Deutschland um 48 µT.
     * Der Bereich 30–70 lässt Luft für Messfehler und Offline-Geräte ohne
     * Kalibrierung, ohne bei normalen Störungen sofort zu warnen.
     */
    const val FIELD_MIN_UT = 30f
    const val FIELD_MAX_UT = 70f

    /**
     * @param fieldMicroTesla gemittelter Feldbetrag, null solange zu wenige Proben vorliegen
     * @param accuracyLow true, wenn der Sensor selbst eine geringe Genauigkeit meldet
     */
    fun issue(fieldMicroTesla: Float?, accuracyLow: Boolean): CompassIssue = when {
        accuracyLow -> CompassIssue.UNRELIABLE
        fieldMicroTesla == null -> CompassIssue.NONE
        fieldMicroTesla < FIELD_MIN_UT -> CompassIssue.FIELD_TOO_WEAK
        fieldMicroTesla > FIELD_MAX_UT -> CompassIssue.FIELD_TOO_STRONG
        else -> CompassIssue.NONE
    }
}

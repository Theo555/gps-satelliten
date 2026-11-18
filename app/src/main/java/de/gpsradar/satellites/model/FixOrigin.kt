package de.gpsradar.satellites.model

/**
 * Woher die angezeigte Position stammt.
 *
 * Der öffentliche Build kennt nur [GNSS]: Der Standort kommt ausschließlich aus
 * dem Satellitenempfänger, es wird nichts an fremde Dienste geschickt. Ein
 * Ausbau, der eine zweite Ortsquelle anhängt (siehe
 * `de.gpsradar.satellites.gps.LocationSources`), kennzeichnet deren Meldungen
 * mit [ALTERNATIVE], damit sie in der Oberfläche nicht als GNSS-Messung
 * erscheinen.
 *
 * **Warum das im Modell steht:** Eine Position aus einer zweiten Quelle ist
 * keine Satellitenmessung. Wer sie als solche ausgibt, behauptet eine
 * Genauigkeit, die nicht gemessen wurde — deshalb trägt der Zustand die Herkunft
 * mit, statt sie in der Darstellung zu erraten (etwa an einer großen
 * Genauigkeitsangabe).
 */
enum class FixOrigin {
    /** Aus dem GNSS-Empfänger. */
    GNSS,

    /** Aus einer anderen Ortsquelle — keine GNSS-Messung. */
    ALTERNATIVE
}

package de.gpsradar.satellites.model

/**
 * Entscheidet, ob die zuletzt gemeldete Satellitenlage (Höhenwinkel, Azimut)
 * für den Rohdatenexport brauchbar ist.
 *
 * Warum das eine eigene Regel braucht: Die Lage kommt aus dem Satellitenstatus,
 * die Messwerte aus einem eigenen Datenstrom. Beide gehören zusammen, aber der
 * Status meldet sich nur, wenn sich die Satellitenliste ändert. Draußen steht
 * sie minutenlang still – und am 14.11.2026 auf dem Sportplatz meldete der
 * Empfänger nach 3:12 Minuten einmal „gestoppt", obwohl die Messungen mit
 * unverändertem C/N₀ weiterliefen. Danach kam kein neuer Status mehr, weil sich
 * nichts änderte: 109 von 300 Epochen hatten keine Lage, obwohl die Werte in
 * Ordnung waren.
 *
 * Deshalb gilt: Die zuletzt gemeldete Lage bleibt gültig, solange geortet wird.
 * Ein gemeldeter Stopp räumt nur die Anzeige auf. Die Frist ist eine Notbremse
 * gegen wirklich alte Winkel, keine Frischeprüfung.
 */
object ExportGeometry {

    /**
     * @param isTracking läuft die Ortung gerade?
     * @param isSimulated sind die Messwerte erfunden? Dann gibt es keine echte Lage.
     * @param hasGeometry wurde überhaupt schon eine Lage gemeldet?
     * @param ageMillis Alter der zuletzt gemeldeten Lage; negativ = unmöglich
     * @param maxAgeMillis Notbremse
     */
    fun usable(
        isTracking: Boolean,
        isSimulated: Boolean,
        hasGeometry: Boolean,
        ageMillis: Long,
        maxAgeMillis: Long
    ): Boolean {
        if (!isTracking || isSimulated) return false
        if (!hasGeometry) return false
        return ageMillis in 0..maxAgeMillis
    }
}

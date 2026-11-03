package de.gpsradar.satellites.model

/**
 * Wie nah man einem Ziel sein muss, damit der Ankunftsalarm auslöst.
 *
 * Ein fester Radius klingt gut, ist aber nur bei gutem Empfang sinnvoll: Im
 * Wald schwankt die gemeldete Position um 10 bis 20 Meter, dann steht man auf
 * dem Zielpunkt und der *berechnete* Abstand bleibt trotzdem darüber. Am 03.11.
 * ist genau das passiert – der Alarm blieb aus.
 *
 * Deshalb ist der Radius die gemeldete Genauigkeit selbst: Was das Gerät nicht
 * besser weiß, darf die App nicht besser behaupten. Bei ±6 m verlangt sie 6 m
 * Nähe, nicht 10 m – und bei ±40 m meldet sie aus 40 m, weil sie es nicht
 * genauer weiß. Die Zahl steht in der Meldung, damit sie nicht willkürlich
 * wirkt.
 *
 * Dazu ein fester Zuschlag von [MARGIN_M] Metern: Die Genauigkeit ist ein
 * 68-%-Wert, keine harte Grenze, und die Zielkoordinate selbst hat auch einen
 * Fehler (bei Cache-Koordinaten einige Meter). Ein Faktor statt eines
 * Zuschlags würde im Wald aus ±20 m gleich 30 m machen – das wäre zu früh.
 */
object ArrivalAlert {

    /**
     * Zuschlag auf die gemeldete Genauigkeit. Kein Prozentwert, weil der Fehler
     * der Zielkoordinate eine feste Größe ist.
     */
    const val MARGIN_M = 2.0

    /** Radius, wenn das Gerät gar keine Genauigkeit meldet. */
    const val DEFAULT_RADIUS_M = 5.0

    /** Kleinster Abstand, ab dem der Alarm wieder scharf wird. */
    const val MIN_RESET_M = 30.0

    /**
     * Radius in Metern für eine gemeldete Genauigkeit.
     *
     * Ohne Genauigkeitsangabe bleibt es beim Mindestradius – dann ist die Lage
     * unklar und die vorsichtige Annahme ist die ehrliche.
     */
    fun thresholdMeters(accuracyMeters: Float?): Double {
        val accuracy = accuracyMeters?.toDouble() ?: return DEFAULT_RADIUS_M
        return accuracy + MARGIN_M
    }

    /**
     * Rücksetzwert: ab hier gilt der Alarm wieder als „nicht ausgelöst".
     *
     * Mindestens [MIN_RESET_M], damit die Meldung beim Suchen am Ziel nicht im
     * Sekundentakt kommt – dort bewegt man sich genau im Grenzbereich.
     */
    fun resetDistanceMeters(thresholdMeters: Double): Double =
        maxOf(MIN_RESET_M, thresholdMeters * 2.0)
}

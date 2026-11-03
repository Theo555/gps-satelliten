package de.gpsradar.satellites.model

/**
 * Wie nah man einem Ziel sein muss, damit der Ankunftsalarm auslöst.
 *
 * Ein fester Radius von 5 Metern klingt gut, ist aber nur bei gutem Empfang
 * sinnvoll: Im Wald schwankt die gemeldete Position um 10 bis 20 Meter, dann
 * steht man auf dem Zielpunkt und der *berechnete* Abstand bleibt trotzdem
 * darüber. Am 03.11. ist genau das passiert – der Alarm blieb aus.
 *
 * Deshalb richtet sich der Radius nach der gemeldeten Genauigkeit: Was das
 * Gerät nicht besser weiß, darf die App nicht besser behaupten. Nach oben ist
 * die Schwelle gedeckelt, damit ein einzelner Ausreißer nach 40 Metern nicht
 * schon „angekommen" meldet.
 */
object ArrivalAlert {

    /** Kleinster Radius, auch bei perfektem Empfang. */
    const val MIN_RADIUS_M = 5.0

    /** Größter Radius, damit ein schlechter Fix nicht zu früh auslöst. */
    const val MAX_RADIUS_M = 25.0

    /**
     * Radius in Metern für eine gemeldete Genauigkeit.
     *
     * Ohne Genauigkeitsangabe bleibt es beim Mindestradius – dann ist die Lage
     * unklar und die vorsichtige Annahme ist die ehrliche.
     */
    fun thresholdMeters(accuracyMeters: Float?): Double {
        val accuracy = accuracyMeters?.toDouble() ?: MIN_RADIUS_M
        return accuracy.coerceIn(MIN_RADIUS_M, MAX_RADIUS_M)
    }

    /** Rücksetzwert: ab hier gilt der Alarm wieder als „nicht ausgelöst". */
    fun resetDistanceMeters(thresholdMeters: Double): Double = thresholdMeters * 2.0
}

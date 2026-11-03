package de.gpsradar.satellites.model

/**
 * Ankunftsalarm am Ende eines Tracks.
 *
 * Ein Track hat kein „Ziel" wie ein Zielpunkt, sondern ein Ende: den letzten
 * Punkt in der aktuellen Verfolgungsrichtung. Wer ihm folgt, will dort genauso
 * gewarnt werden – gedacht ist die App für Fußgänger und Radfahrer, im Auto ist
 * es ein Nebeneffekt.
 *
 * Sperre: Der Einstieg rastet bis zu 100 Meter entfernt auf den nächstgelegenen
 * Track-Punkt ein. Startet man einen Track direkt an seinem Ende, wäre die
 * Reststrecke sofort null und der Alarm ginge grundlos los. Deshalb wird er
 * erst scharf, wenn man zwischenzeitlich den Rücksetzabstand vor sich hatte.
 * Sehr kurze Tracks melden ihr Ende daher nicht – dort ist der Abstand ohnehin
 * jederzeit auf dem Bildschirm zu sehen.
 */
object TrackEndAlert {

    /**
     * Radius wie beim Zielpunkt: die gemeldete Genauigkeit plus Zuschlag.
     * Bewusst dieselbe Regel – sonst verlangt die App für dieselbe Aussage
     * („ich bin da") je nach Zielart unterschiedlich viel Nähe.
     */
    fun radiusMeters(accuracyMeters: Float?): Double =
        ArrivalAlert.thresholdMeters(accuracyMeters)

    /** Rücksetzwert: ab hier gilt der Alarm wieder als „nicht ausgelöst". */
    fun armMeters(radiusMeters: Double): Double =
        ArrivalAlert.resetDistanceMeters(radiusMeters)

    /**
     * Zustand nach einem Positionssatz.
     *
     * @param armed der Alarm ist scharf (man war schon weiter als der Rücksetzabstand weg)
     * @param alerted das Ende wurde bereits gemeldet
     * @param trigger jetzt melden – der Aufrufer vibriert einmal
     */
    data class Result(
        val armed: Boolean,
        val alerted: Boolean,
        val trigger: Boolean
    )

    /**
     * Rechnet den nächsten Zustand aus. [remainingMeters] ist die Reststrecke
     * entlang des Tracks von der aktuellen Position bis zum Ende.
     */
    fun update(
        remainingMeters: Double,
        accuracyMeters: Float?,
        armed: Boolean,
        alerted: Boolean
    ): Result {
        val radius = radiusMeters(accuracyMeters)
        if (remainingMeters > armMeters(radius)) {
            return Result(armed = true, alerted = false, trigger = false)
        }
        if (!armed || alerted) {
            return Result(armed = armed, alerted = alerted, trigger = false)
        }
        if (remainingMeters >= radius) {
            return Result(armed = true, alerted = false, trigger = false)
        }
        return Result(armed = true, alerted = true, trigger = true)
    }
}

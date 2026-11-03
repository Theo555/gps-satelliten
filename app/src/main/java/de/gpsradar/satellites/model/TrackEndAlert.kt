package de.gpsradar.satellites.model

/**
 * Ankunftsalarm am Ende eines Tracks.
 *
 * Ein Track hat kein „Ziel" wie ein Zielpunkt, sondern ein Ende: den letzten
 * Punkt in der aktuellen Verfolgungsrichtung. Wer ihm folgt, will dort genauso
 * gewarnt werden – gedacht ist die App für Fußgänger und Radfahrer, im Auto ist
 * es ein Nebeneffekt.
 *
 * Radius: mindestens [RADIUS_M] – das ist die Grenze, ab der die Oberfläche
 * „Ziel erreicht" anzeigt. Bei schlechtem Empfang wächst der Radius mit der
 * gemeldeten Genauigkeit, denn was das Gerät nicht besser weiß, darf die App
 * nicht besser behaupten (Lehre vom 03.11.: fester Radius und ±18 m
 * Genauigkeit → der Alarm blieb im Wald aus). Nach oben gedeckelt, damit ein
 * unbrauchbarer Fix nicht aus 100 Metern Entfernung meldet.
 *
 * Sperre: Der Einstieg rastet bis zu 100 Meter entfernt auf den nächstgelegenen
 * Track-Punkt ein. Startet man einen Track direkt an seinem Ende, wäre die
 * Reststrecke sofort null und der Alarm ginge grundlos los. Deshalb wird er
 * erst scharf, wenn man zwischenzeitlich die doppelte Reststrecke vor sich
 * hatte. Sehr kurze Tracks melden ihr Ende daher nicht – dort ist der Abstand
 * ohnehin jederzeit auf dem Bildschirm zu sehen.
 */
object TrackEndAlert {

    /** Kleinster Radius; zugleich die Grenze der Anzeige „Ziel erreicht". */
    const val RADIUS_M = 30.0

    /** Größter Radius, damit ein unbrauchbarer Fix nicht viel zu früh meldet. */
    const val MAX_RADIUS_M = 50.0

    /**
     * Radius in Metern für eine gemeldete Genauigkeit. Ohne Angabe bleibt es
     * beim Mindestradius – dann ist die Lage unklar und die vorsichtige
     * Annahme ist die ehrliche.
     */
    fun radiusMeters(accuracyMeters: Float?): Double {
        val accuracy = accuracyMeters?.toDouble() ?: 0.0
        return maxOf(RADIUS_M, accuracy).coerceAtMost(MAX_RADIUS_M)
    }

    /** Rücksetzwert: ab hier gilt der Alarm wieder als „nicht ausgelöst". */
    fun armMeters(radiusMeters: Double): Double = radiusMeters * 2.0

    /**
     * Zustand nach einem Positionssatz.
     *
     * @param armed der Alarm ist scharf (man war schon weiter als [ARM_M] weg)
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

package de.gpsradar.satellites.model

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ein Messpunkt der Geh-Aufzeichnung.
 *
 * Festgehalten werden drei Peilungen, die dasselbe Gerät zur selben Zeit
 * liefert, aber aus verschiedenen Quellen stammen:
 * - [rawDegrees] – Azimut des Rotationsvektors, so wie die Plattform ihn gibt
 * - [shownDegrees] – derselbe Wert mit der Ortsmissweisung, also das, was die
 *   App anzeigt
 * - [magneticDegrees] – Peilung aus Beschleunigungs- und Magnetfeldsensor,
 *   also ein reiner Magnetkompass ohne die Rechenwege der Plattform
 *
 * Der Vergleich von [shownDegrees] mit [magneticDegrees] beantwortet die Frage,
 * ob die Plattform die Missweisung schon selbst einrechnet. Der Vergleich mit
 * [courseDegrees] (GPS-Kurs, immer wahres Norden) zeigt, wo die Anzeige
 * tatsächlich liegt.
 */
data class WalkSample(
    val timeMillis: Long,
    val rawDegrees: Float,
    val shownDegrees: Float,
    val magneticDegrees: Float?,
    val courseDegrees: Float?,
    val fieldMicroTesla: Float,
    val tiltDegrees: Float,
)

/** Zustand der Geh-Aufzeichnung für die Oberfläche. */
data class CompassWalkState(
    val running: Boolean = false,
    val secondsLeft: Int = 0,
    val samples: Int = 0,
    val finished: Boolean = false,
    /** Mittelwert von (Anzeige − Magnetkompass) in Grad. */
    val shownVsMagneticDegrees: Float? = null,
    /** Mittelwert von (Anzeige − GPS-Kurs) in Grad, nur während der Bewegung. */
    val shownVsCourseDegrees: Float? = null,
    val courseSamples: Int = 0,
    /** Dateiname der Ablage im privaten App-Verzeichnis. */
    val fileName: String? = null,
)

/**
 * Reine Rechnung zur Geh-Aufzeichnung – ohne Sensoren, ohne Dateien, damit sie
 * prüfbar bleibt.
 */
object CompassWalk {

    /** Kürzeste Winkeldifferenz von [a] nach [b] in Grad (−180 … +180). */
    fun shortestDelta(a: Float, b: Float): Float {
        var d = (a - b) % 360f
        if (d > 180f) d -= 360f
        if (d < -180f) d += 360f
        return d
    }

    /**
     * Kreismittelwert in Grad. Ein arithmetisches Mittel über 359° und 1° ergäbe
     * 180° – hier kommt 0° heraus.
     */
    fun circularMean(values: List<Float>): Float? {
        if (values.isEmpty()) return null
        var sumSin = 0.0
        var sumCos = 0.0
        for (value in values) {
            val rad = Math.toRadians(value.toDouble())
            sumSin += sin(rad)
            sumCos += cos(rad)
        }
        if (abs(sumSin) < 1e-9 && abs(sumCos) < 1e-9) return null
        var degrees = Math.toDegrees(atan2(sumSin, sumCos)).toFloat()
        if (degrees < 0f) degrees += 360f
        return degrees
    }

    /** Mittlere Abweichung der Anzeige vom reinen Magnetkompass. */
    fun shownVsMagnetic(samples: List<WalkSample>): Float? = circularMean(
        samples.mapNotNull { sample ->
            sample.magneticDegrees?.let { shortestDelta(sample.shownDegrees, it) }
        }
    )

    /** Mittlere Abweichung der Anzeige vom GPS-Kurs – nur wo einer vorliegt. */
    fun shownVsCourse(samples: List<WalkSample>): Float? = circularMean(
        samples.mapNotNull { sample ->
            sample.courseDegrees?.let { shortestDelta(sample.shownDegrees, it) }
        }
    )

    /** Anzahl der Messpunkte, zu denen ein GPS-Kurs vorlag. */
    fun courseSampleCount(samples: List<WalkSample>): Int =
        samples.count { it.courseDegrees != null }
}

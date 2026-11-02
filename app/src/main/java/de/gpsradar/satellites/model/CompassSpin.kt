package de.gpsradar.satellites.model

import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sqrt

/** Ergebnis eines Drehtests. */
enum class SpinVerdict {
    /** Zu wenig Daten oder zu wenig gedreht – kein Befund. */
    INSUFFICIENT,
    OK,
    DOUBTFUL,
    CONTRADICTORY,
    /** Die Umgebung hat die Messung gestört – über das Gerät sagt das nichts. */
    ENVIRONMENT
}

/**
 * Zustand des Drehtests, wie ihn die Oberfläche braucht.
 *
 * [lastVerdict] und [lastDeviationDegrees] halten den vorigen Durchlauf, damit
 * der Nutzer sieht, ob eine Maßnahme (Hülle ab, 8er-Bewegung) gewirkt hat.
 */
data class CompassSpinState(
    val supported: Boolean = true,
    val running: Boolean = false,
    val finished: Boolean = false,
    val turnDegrees: Float = 0f,
    val samples: Int = 0,
    val magneticTurnDegrees: Float = 0f,
    val gyroTurnDegrees: Float = 0f,
    /** Größter winkelweiser Fehler des Durchlaufs, null solange kein Befund vorliegt. */
    val deviationDegrees: Float? = null,
    /** Herausgerechnete Nachlaufzeit des Rotationsvektors in Millisekunden. */
    val lagMillis: Int = 0,
    val verdict: SpinVerdict = SpinVerdict.INSUFFICIENT,
    val lastVerdict: SpinVerdict = SpinVerdict.INSUFFICIENT,
    val lastDeviationDegrees: Float? = null
)

/**
 * Vergleich von Magnetkompass und Kreisel während einer Drehung.
 *
 * Grundgedanke: Beide Sensoren sehen dieselbe Drehung, aber nur einer von
 * beiden kennt das Magnetfeld. Ein Hard- oder Soft-Iron-Versatz macht den
 * Magnetwinkel ungleichmäßig (in manchen Sektoren zu schnell, in anderen zu
 * langsam), der Kreisel nicht. Die Differenz der über die ganze Drehung
 * aufsummierten Winkel ist damit ein Maß für die Verzerrung – ohne externen
 * Bezug, ohne Sonne, ohne Korrekturwert.
 *
 * Bewusst ohne Zustand: die Summation macht der Aufrufer je Ereignis.
 */
object CompassSpin {

    /** Unter dieser gedrehten Strecke ist kein Urteil möglich. */
    const val MIN_TURN_DEGREES = 200f

    /** Unter so vielen Proben ist die Messung zu dünn. */
    const val MIN_SAMPLES = 20

    /** Android liefert die Kreiselrate in rad/s, gerechnet wird durchgehend in Grad. */
    private const val RAD_TO_DEG = 57.29578f

    // Gemessene Werte 02.11. (S20 FE): saubere Läufe 1,0° / 1,0° / 4,4°,
    // ein Magnet am Gerät 147°. Dazwischen ist reichlich Platz.
    const val OK_MAX_DEVIATION_DEGREES = 8f
    const val DOUBTFUL_MAX_DEVIATION_DEGREES = 20f

    /**
     * Azimutrate in Grad je Sekunde aus der Kreiselrate.
     *
     * [angularVelocity] kommt in rad/s (Android-Konvention), das Ergebnis ist
     * Grad je Sekunde. Die Rate wird auf die Senkrechte projiziert, damit auch
     * ein gekippt gehaltenes Gerät richtig gerechnet wird. Das Minus: positive
     * Drehung um die Hochachse ist nach Rechte-Hand-Regel gegen den
     * Uhrzeigersinn, der Azimut wächst aber im Uhrzeigersinn.
     */
    fun azimuthRate(gravity: FloatArray, angularVelocity: FloatArray): Float {
        val g = sqrt(
            gravity[0] * gravity[0] +
                gravity[1] * gravity[1] +
                gravity[2] * gravity[2]
        )
        if (g < 1f) return 0f // keine brauchbare Schwerkraftrichtung
        val aboutUp = (
            gravity[0] * angularVelocity[0] +
                gravity[1] * angularVelocity[1] +
                gravity[2] * angularVelocity[2]
            ) / g
        return -aboutUp * RAD_TO_DEG
    }

    /** Summe der Azimutänderungen über alle Proben, mit Rücksicht auf den 360°-Sprung. */
    fun magneticTurnDegrees(headings: List<Float>): Float {
        if (headings.size < 2) return 0f
        var sum = 0f
        for (i in 1 until headings.size) {
            sum += shortestDelta(headings[i - 1], headings[i])
        }
        return sum
    }

    /**
     * Eine Probe: gemessener Azimut und der gleichzeitig vom Kreisel
     * aufsummierte Drehwinkel.
     */
    data class SpinSample(
        val azimuthDegrees: Float,
        val gyroTurnDegrees: Float,
        val timeNanos: Long = 0L,
        /** Neigung des Geräts in Grad, 0 = flach liegend. */
        val tiltDegrees: Float = 0f,
        /** Gemessener Feldbetrag in µT zum selben Zeitpunkt. */
        val fieldMicroTesla: Float = 0f
    )

    /** Kreiselwinkel zu einem Zeitpunkt. */
    data class GyroTick(val timeNanos: Long, val turnDegrees: Float)

    /** Beste gefundene Zeitverschiebung samt verbleibendem Fehler. */
    data class LagFit(val lagNanos: Long, val deviationDegrees: Float)

    /** So weit darf der Rotationsvektor hinter dem Kreisel herlaufen. */
    const val MAX_LAG_NANOS = 250_000_000L
    private const val LAG_STEP_NANOS = 10_000_000L

    /** Strecke, die gegen die Hauptdrehrichtung läuft, ab der etwas stört. */
    const val MAX_COUNTER_ROTATION_DEGREES = 15f

    /**
     * Größter winkelweiser Abstand zwischen Magnet- und Kreiselwinkel.
     *
     * Das ist die eigentliche Messgröße. Der Vergleich der **Summen** taugt
     * nicht: Ein Hard-Iron-Versatz (Magnet in der Hülle, magnetisierte
     * Gehäuseteile) ist ein konstanter Vektor im Gerätesystem. Er macht die
     * Winkelmessung ungleichmäßig – in manchen Sektoren zu schnell, in anderen
     * zu langsam – aber über eine volle Umdrehung summiert er sich exakt zu
     * 360° auf. Nur der Verlauf zeigt den Fehler.
     *
     * Beide Reihen werden auf die erste Probe bezogen, damit ein Zeitversatz
     * von einem Sample nicht als Fehler zählt.
     */
    fun worstDeviationDegrees(samples: List<SpinSample>): Float {
        if (samples.size < 2) return 0f
        var magnetic = 0f
        var worst = 0f
        for (i in 1 until samples.size) {
            magnetic += shortestDelta(samples[i - 1].azimuthDegrees, samples[i].azimuthDegrees)
            val gyro = samples[i].gyroTurnDegrees - samples[0].gyroTurnDegrees
            val error = abs(magnetic - gyro)
            if (error > worst) worst = error
        }
        return worst
    }

    /** Kürzester Winkelabstand von [from] nach [to], Ergebnis in −180…180. */
    fun shortestDelta(from: Float, to: Float): Float {
        var diff = to - from
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f
        return diff
    }

    /**
     * Neigung des Geräts in Grad: Winkel zwischen Schwerkraft und Geräte-Hochachse.
     * 0° heißt flach liegend, 90° hochkant.
     *
     * Wichtig für den Drehtest: Der Azimut ist die Richtung der Geräte-Hochachse,
     * projiziert auf die Waagerechte. Wird das Gerät gekippt (Umgreifen, Kabel),
     * wandert diese Projektion – der Magnetazimut springt, obwohl gar nicht
     * gedreht wurde. Solche Proben dürfen nicht als Verzerrung zählen.
     */
    fun tiltDegrees(gravity: FloatArray): Float {
        val g = sqrt(
            gravity[0] * gravity[0] +
                gravity[1] * gravity[1] +
                gravity[2] * gravity[2]
        )
        if (g < 1f) return 0f
        val cos = (gravity[2] / g).coerceIn(-1f, 1f)
        return Math.toDegrees(acos(cos.toDouble())).toFloat()
    }

    /**
     * Strecke, die der Magnet entgegen der Gesamtdrehrichtung zurücklegt.
     * Bei einer gleichmäßigen Drehung ist das nahe null.
     */
    fun counterRotationDegrees(samples: List<SpinSample>): Float {
        if (samples.size < 2) return 0f
        var net = 0f
        val deltas = ArrayList<Float>(samples.size - 1)
        for (i in 1 until samples.size) {
            val delta = shortestDelta(samples[i - 1].azimuthDegrees, samples[i].azimuthDegrees)
            deltas.add(delta)
            net += delta
        }
        val sign = if (net < 0f) -1f else 1f
        var counter = 0f
        for (delta in deltas) {
            if (delta * sign < 0f) counter += abs(delta)
        }
        return counter
    }

    /** Streuung des Feldbetrags in Prozent des Mittelwerts. */
    fun fieldSpreadPercent(samples: List<SpinSample>): Float {
        val values = samples.map { it.fieldMicroTesla }.filter { it > 1f }
        if (values.size < 2) return 0f
        val mean = values.average().toFloat()
        if (mean <= 1f) return 0f
        return (values.max() - values.min()) / mean * 100f
    }

    /**
     * Stört die Umgebung die Messung?
     *
     * Maßgeblich ist der **Gegenlauf**: Springt der Magnetazimut während der
     * Drehung zurück, kann das Gerät nicht die Ursache sein – der Kreisel läuft
     * in solchen Momenten glatt weiter. Am Gerät gemessen: gestörter Lauf 41°,
     * sauberer Lauf 0,5°.
     *
     * Der Feldbetrag taugt dafür **nicht**: Ein sauberer Lauf auf der Hand hatte
     * 40 % Streuung bei nur 4,4° Abweichung – eine Änderung des senkrechten
     * Feldanteils hebt den Betrag, ohne die Richtung zu stören. Er wird nur
     * protokolliert, nicht bewertet.
     */
    fun isEnvironmentDisturbed(samples: List<SpinSample>): Boolean =
        counterRotationDegrees(samples) > MAX_COUNTER_ROTATION_DEGREES

    /**
     * Wie [worstDeviationDegrees], aber der Kreiselwinkel wird um [lagNanos]
     * früher abgelesen. Damit lässt sich die Nachlaufzeit des gefilterten
     * Rotationsvektors herausrechnen.
     */
    fun worstDeviationDegrees(
        samples: List<SpinSample>,
        ticks: List<GyroTick>,
        lagNanos: Long
    ): Float {
        if (samples.size < 2 || ticks.size < 2) return worstDeviationDegrees(samples)
        // Erst ab dem Zeitpunkt vergleichen, zu dem der Kreisel schon läuft.
        // Sonst zählt eine Drehung vor dem ersten Kreiselereignis nur magnetisch
        // und erscheint als riesiger Fehler – bei sehr schnellem Drehen gemessen:
        // 97°, weil die ersten paar hundert Millisekunden Kreisel fehlten.
        val firstTick = ticks.first().timeNanos
        val start = samples.indexOfFirst { it.timeNanos - lagNanos >= firstTick }
        if (start < 0 || samples.size - start < 2) return worstDeviationDegrees(samples)
        val reference = gyroTurnAt(ticks, samples[start].timeNanos - lagNanos)
            ?: return worstDeviationDegrees(samples)

        var magnetic = 0f
        var worst = 0f
        for (i in start + 1 until samples.size) {
            magnetic += shortestDelta(samples[i - 1].azimuthDegrees, samples[i].azimuthDegrees)
            val gyro = (gyroTurnAt(ticks, samples[i].timeNanos - lagNanos) ?: reference) - reference
            val error = abs(magnetic - gyro)
            if (error > worst) worst = error
        }
        return worst
    }

    /**
     * Sucht die Zeitverschiebung, die den winkelweisen Fehler minimiert.
     *
     * Der Rotationsvektor ist ein gefiltertes Signal und läuft dem Kreisel nach
     * – bei schnellem Drehen sieht das wie eine Verzerrung aus (an 20 °/s gemessen:
     * 1° langsam, 6° schnell). Eine reine Verschiebung lässt sich wegnehmen, eine
     * echte Verzerrung nicht: Hard Iron ist periodisch, nicht zeitlich versetzt.
     * Der hier gefundene Restwert ist deshalb der belastbare Ausschlag.
     */
    fun bestLagFit(samples: List<SpinSample>, ticks: List<GyroTick>): LagFit {
        // Beide Richtungen: der Rotationsvektor kann auch vorauslaufen, wenn der
        // Sensor seine Ereignisse gebündelt mit alten Zeitstempeln liefert.
        var best: LagFit? = null
        var lag = -MAX_LAG_NANOS
        while (lag <= MAX_LAG_NANOS) {
            val deviation = worstDeviationDegrees(samples, ticks, lag)
            if (best == null || deviation < best.deviationDegrees) best = LagFit(lag, deviation)
            lag += LAG_STEP_NANOS
        }
        return best ?: LagFit(0L, worstDeviationDegrees(samples))
    }

    /** Kreiselwinkel zum Zeitpunkt [timeNanos], zwischen zwei Proben linear interpoliert. */
    private fun gyroTurnAt(ticks: List<GyroTick>, timeNanos: Long): Float? {
        if (ticks.isEmpty()) return null
        if (timeNanos <= ticks.first().timeNanos) return ticks.first().turnDegrees
        if (timeNanos >= ticks.last().timeNanos) return ticks.last().turnDegrees
        var lo = 0
        var hi = ticks.size - 1
        while (hi - lo > 1) {
            val mid = (lo + hi) / 2
            if (ticks[mid].timeNanos <= timeNanos) lo = mid else hi = mid
        }
        val a = ticks[lo]
        val b = ticks[hi]
        val span = (b.timeNanos - a.timeNanos).toFloat()
        if (span <= 0f) return b.turnDegrees
        val fraction = (timeNanos - a.timeNanos).toFloat() / span
        return a.turnDegrees + (b.turnDegrees - a.turnDegrees) * fraction
    }

    /** Betrag der Abweichung zwischen beiden Drehungen, in Grad. */
    fun deviationDegrees(magneticTurnDegrees: Float, gyroTurnDegrees: Float): Float =
        abs(abs(magneticTurnDegrees) - abs(gyroTurnDegrees))

    /**
     * Urteil über einen Drehtest.
     *
     * Der Kreisel entscheidet, ob überhaupt gedreht wurde – er ist die
     * unmagnetische Quelle. Dreht der Magnet in die Gegenrichtung oder bleibt
     * er stehen, ist das ein Widerspruch und keine Frage der Toleranz. Der
     * Ausschlag kommt aus dem winkelweisen Fehler, nicht aus der Summe.
     */
    fun verdict(
        magneticTurnDegrees: Float,
        gyroTurnDegrees: Float,
        samples: Int,
        worstDeviationDegrees: Float = deviationDegrees(magneticTurnDegrees, gyroTurnDegrees),
        environmentDisturbed: Boolean = false
    ): SpinVerdict {
        if (samples < MIN_SAMPLES) return SpinVerdict.INSUFFICIENT
        if (abs(gyroTurnDegrees) < MIN_TURN_DEGREES) return SpinVerdict.INSUFFICIENT
        // Vor jeder Aussage über das Gerät: War der Ort überhaupt tauglich?
        if (environmentDisturbed) return SpinVerdict.ENVIRONMENT
        if (abs(magneticTurnDegrees) < MIN_TURN_DEGREES) return SpinVerdict.CONTRADICTORY
        if (magneticTurnDegrees * gyroTurnDegrees < 0f) return SpinVerdict.CONTRADICTORY

        return when {
            worstDeviationDegrees <= OK_MAX_DEVIATION_DEGREES -> SpinVerdict.OK
            worstDeviationDegrees <= DOUBTFUL_MAX_DEVIATION_DEGREES -> SpinVerdict.DOUBTFUL
            else -> SpinVerdict.CONTRADICTORY
        }
    }
}

package de.gpsradar.satellites

import de.gpsradar.satellites.model.CompassSpin
import de.gpsradar.satellites.model.SpinVerdict
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** Reine Rechenwege des Drehtests – ohne Sensor, ohne Robolectric. */
class CompassSpinTest {

    private val flatGravity = floatArrayOf(0f, 0f, 9.81f)

    /** Flach liegend, Drehung um die Hochachse: Azimut wächst im Uhrzeigersinn.
     *  Der Kreisel liefert rad/s – π/2 rad/s sind 90°/s. */
    @Test
    fun flatPhoneRightTurnIsNegativeGyroRate() {
        val rate = CompassSpin.azimuthRate(
            flatGravity,
            floatArrayOf(0f, 0f, (PI / 2).toFloat())
        )
        assertEquals(-90f, rate, 0.01f)
    }

    /** Gekippt: die Rate wird auf die Senkrechte projiziert, nicht auf die Geräteachse. */
    @Test
    fun tiltedPhoneProjectsOntoGravity() {
        // Gerät hochkant, +y zeigt nach unten, π/4 rad/s = 45°/s um die Senkrechte.
        val rate = CompassSpin.azimuthRate(
            floatArrayOf(0f, -9.81f, 0f),
            floatArrayOf(0f, (-PI / 4).toFloat(), 0f)
        )
        assertEquals(-45f, rate, 0.01f)
    }

    /** Eine volle Umdrehung muss auch als 360° ankommen – nicht als 2π. */
    @Test
    fun fullTurnIntegratesTo360Degrees() {
        // 18°/s über 20 Schritte à eine Sekunde.
        val rate = CompassSpin.azimuthRate(
            flatGravity,
            floatArrayOf(0f, 0f, (PI / 10).toFloat())
        )
        assertEquals(-18f, rate, 0.01f)
        assertEquals(360f, abs(rate) * 20f, 0.1f)
    }

    /** Ohne Schwerkraftrichtung gibt es keine sinnvolle Projektion. */
    @Test
    fun missingGravityGivesZero() {
        assertEquals(0f, CompassSpin.azimuthRate(floatArrayOf(0f, 0f, 0f), floatArrayOf(1f, 2f, 3f)), 1e-6f)
    }

    /** Vier Vierteldrehungen im Uhrzeigersinn ergeben 270°. */
    @Test
    fun magneticTurnSumsQuarterTurns() {
        val turn = CompassSpin.magneticTurnDegrees(listOf(0f, 90f, 180f, 270f))
        assertEquals(270f, turn, 1e-3f)
    }

    /** Über den Nordpunkt hinweg darf kein Rücksprung entstehen. */
    @Test
    fun magneticTurnCrossesNorthCorrectly() {
        assertEquals(20f, CompassSpin.magneticTurnDegrees(listOf(350f, 0f, 10f)), 1e-3f)
        assertEquals(-20f, CompassSpin.magneticTurnDegrees(listOf(10f, 0f, 350f)), 1e-3f)
    }

    /** Ein voller Kreis schließt sich: Summe 360°, keine Drift. */
    @Test
    fun fullCircleSumsTo360() {
        val headings = (0..36).map { (it * 10f) % 360f }
        assertEquals(360f, CompassSpin.magneticTurnDegrees(headings), 1e-3f)
    }

    /** Ein sauberer Durchlauf ist unauffällig. */
    @Test
    fun smallDeviationIsOk() {
        assertEquals(
            SpinVerdict.OK,
            CompassSpin.verdict(magneticTurnDegrees = 358f, gyroTurnDegrees = 360f, samples = 120)
        )
    }

    /** Mittlere Verzerrung: eingeschränkt, aber nicht widersprüchlich. */
    @Test
    fun mediumDeviationIsDoubtful() {
        assertEquals(
            SpinVerdict.DOUBTFUL,
            CompassSpin.verdict(magneticTurnDegrees = 350f, gyroTurnDegrees = 360f, samples = 120)
        )
    }

    /** Große Verzerrung ist ein Widerspruch. */
    @Test
    fun largeDeviationIsContradictory() {
        assertEquals(
            SpinVerdict.CONTRADICTORY,
            CompassSpin.verdict(magneticTurnDegrees = 300f, gyroTurnDegrees = 360f, samples = 120)
        )
    }

    /** Magnet dreht gegenläufig – das ist kein Toleranzthema. */
    @Test
    fun oppositeDirectionIsContradictory() {
        assertEquals(
            SpinVerdict.CONTRADICTORY,
            CompassSpin.verdict(magneticTurnDegrees = -360f, gyroTurnDegrees = 360f, samples = 120)
        )
    }

    /**
     * Gegenprobe zum Kern des Verfahrens: Ein Hard-Iron-Versatz (Magnet in der
     * Hülle) lässt die Winkelsumme bei genau 360° – die alte Messgröße hätte
     * den Fehler durchgewunken. Der winkelweise Vergleich sieht ihn.
     */
    @Test
    fun hardIronOffsetKeepsTotalButShowsWorstDeviation() {
        val samples = ArrayList<CompassSpin.SpinSample>()
        for (i in 0..36) {
            val phiDeg = i * 10.0
            val phi = Math.toRadians(phiDeg)
            // Erdfeld in Gerätekoordinaten: (−sin φ, cos φ). Dazu ein konstanter
            // Versatz von 0,5 Feldstärken in x-Richtung (Hard Iron).
            val mx = -sin(phi) + 0.5
            val my = cos(phi)
            var azimuth = Math.toDegrees(atan2(-mx, my))
            if (azimuth < 0) azimuth += 360.0
            samples.add(CompassSpin.SpinSample(azimuth.toFloat(), phiDeg.toFloat()))
        }

        val magnetic = CompassSpin.magneticTurnDegrees(samples.map { it.azimuthDegrees })
        assertEquals("Summe bleibt unauffällig", 360f, magnetic, 1f)

        val worst = CompassSpin.worstDeviationDegrees(samples)
        assertTrue("winkelweiser Fehler muss auffallen, war $worst", worst > 20f)
    }

    /** Ohne Versatz bleibt auch der winkelweise Fehler bei null. */
    @Test
    fun cleanRotationHasNoWorstDeviation() {
        val samples = (0..36).map {
            CompassSpin.SpinSample((it * 10f) % 360f, it * 10f)
        }
        assertEquals(0f, CompassSpin.worstDeviationDegrees(samples), 0.01f)
    }

    /** Drehung mit steigender Geschwindigkeit: φ(t) = 20·t² in Grad. */
    private fun rampAngle(seconds: Float): Float = 20f * seconds * seconds

    /**
     * Die Nachlaufzeit des Rotationsvektors darf nicht als Verzerrung zählen.
     * Am Gerät gemessen: langsames Drehen 1°, schnelles 6° – derselbe Sensor.
     */
    @Test
    fun sensorLagIsCompensated() {
        val lagMs = 100L
        val ticks = (0..4200 step 20).map {
            CompassSpin.GyroTick(it * 1_000_000L, rampAngle(it / 1000f))
        }
        // Der Azimut zeigt die Lage von vor 100 ms.
        val samples = (300..4200 step 60).map {
            CompassSpin.SpinSample(
                rampAngle((it - lagMs) / 1000f) % 360f,
                rampAngle(it / 1000f),
                it * 1_000_000L
            )
        }

        val raw = CompassSpin.worstDeviationDegrees(samples, ticks, 0L)
        assertTrue("ohne Ausgleich muss die Trägheit auffallen, war $raw", raw > 5f)

        val fit = CompassSpin.bestLagFit(samples, ticks)
        assertTrue("nach Ausgleich klein, war ${fit.deviationDegrees}", fit.deviationDegrees < 2f)
        val foundMs = fit.lagNanos / 1_000_000L
        assertTrue("Nachlauf gefunden: $foundMs ms statt $lagMs", abs(foundMs - lagMs) <= 20L)
    }

    /**
     * Gegenprobe: Eine echte Verzerrung ist periodisch und lässt sich durch
     * Verschieben nicht wegnehmen – sonst würde der Ausgleich den Fehler
     * wegdefinieren, den er finden soll.
     */
    @Test
    fun hardIronSurvivesLagCompensation() {
        val ticks = (0..4200 step 20).map {
            CompassSpin.GyroTick(it * 1_000_000L, rampAngle(it / 1000f))
        }
        val samples = (300..4200 step 60).map {
            val phi = rampAngle(it / 1000f)
            val rad = Math.toRadians(phi.toDouble())
            val mx = -sin(rad) + 0.5
            val my = cos(rad)
            var azimuth = Math.toDegrees(atan2(-mx, my))
            if (azimuth < 0) azimuth += 360.0
            CompassSpin.SpinSample(azimuth.toFloat(), phi, it * 1_000_000L)
        }

        val fit = CompassSpin.bestLagFit(samples, ticks)
        assertTrue(
            "Verzerrung darf nicht als Latenz verschwinden, war ${fit.deviationDegrees}",
            fit.deviationDegrees > 15f
        )
    }

    /**
     * Der Kreisel startet später als der Rotationsvektor. Die Drehung davor
     * darf nicht als Verzerrung zählen – am Gerät waren das bei einer schnellen
     * Umdrehung 97° „Fehler“, die keine waren.
     */
    @Test
    fun gyroStartingLateIsNotDistortion() {
        val ticks = (1000..4200 step 20).map {
            CompassSpin.GyroTick(it * 1_000_000L, rampAngle(it / 1000f))
        }
        val samples = (0..4200 step 60).map {
            CompassSpin.SpinSample(
                rampAngle(it / 1000f) % 360f,
                rampAngle(it / 1000f),
                it * 1_000_000L
            )
        }
        val fit = CompassSpin.bestLagFit(samples, ticks)
        assertTrue("Kreiselstart darf kein Fehler sein, war ${fit.deviationDegrees}", fit.deviationDegrees < 2f)
    }

    /** Neigung: flach 0°, hochkant 90°, auf dem Kopf 180°. */
    @Test
    fun tiltIsMeasuredFromGravity() {
        assertEquals(0f, CompassSpin.tiltDegrees(floatArrayOf(0f, 0f, 9.81f)), 0.1f)
        assertEquals(90f, CompassSpin.tiltDegrees(floatArrayOf(9.81f, 0f, 0f)), 0.1f)
        assertEquals(180f, CompassSpin.tiltDegrees(floatArrayOf(0f, 0f, -9.81f)), 0.1f)
        // Ohne Schwerkraft keine Aussage.
        assertEquals(0f, CompassSpin.tiltDegrees(floatArrayOf(0f, 0f, 0f)), 0.1f)
    }

    /** Ein sauberer Durchlauf im ruhigen Feld ist nicht gestört. */
    @Test
    fun cleanRunIsNotDisturbed() {
        val samples = (0..36).map {
            CompassSpin.SpinSample((it * 10f) % 360f, it * 10f, it.toLong(), 0f, 48f)
        }
        assertEquals(0f, CompassSpin.counterRotationDegrees(samples), 0.01f)
        assertEquals(0f, CompassSpin.fieldSpreadPercent(samples), 0.01f)
        assertTrue(!CompassSpin.isEnvironmentDisturbed(samples))
    }

    /** Wie im Büro gemessen: der Azimut springt einmal 20° gegen die Drehrichtung. */
    @Test
    fun counterRotationIsDetected() {
        val samples = (0..18).map { i ->
            val azimuth = if (i == 9) 60f else (i * 10f).toFloat()
            CompassSpin.SpinSample(azimuth % 360f, i * 10f, i.toLong(), 0f, 48f)
        }
        assertEquals(20f, CompassSpin.counterRotationDegrees(samples), 0.01f)
        assertTrue(CompassSpin.isEnvironmentDisturbed(samples))
    }

    /**
     * Ein Feldsprung allein belegt keine Störung: Am Gerät gemessen hatte ein
     * sauberer Lauf 40 % Streuung bei nur 4,4° Abweichung, weil sich nur der
     * senkrechte Feldanteil änderte.
     */
    @Test
    fun fieldJumpAloneIsNoProofOfDisturbance() {
        val samples = (0..10).map { i ->
            val field = if (i == 5) 90f else 48f
            CompassSpin.SpinSample((i * 30f) % 360f, i * 30f, i.toLong(), 0f, field)
        }
        assertTrue("Streuung wird erkannt", CompassSpin.fieldSpreadPercent(samples) > 20f)
        assertTrue("aber nicht als Störung gewertet", !CompassSpin.isEnvironmentDisturbed(samples))
    }

    /** Gestörter Ort schlägt jedes Geräteurteil: sonst meldet die Prüfung einen Defekt, den es nicht gibt. */
    @Test
    fun disturbedEnvironmentWinsOverContradiction() {
        assertEquals(
            SpinVerdict.ENVIRONMENT,
            CompassSpin.verdict(
                magneticTurnDegrees = 300f,
                gyroTurnDegrees = 360f,
                samples = 120,
                worstDeviationDegrees = 60f,
                environmentDisturbed = true
            )
        )
    }

    /** Zu wenig gedreht oder zu wenige Proben: kein Befund. */
    @Test
    fun tooLittleDataIsInsufficient() {
        assertEquals(
            SpinVerdict.INSUFFICIENT,
            CompassSpin.verdict(magneticTurnDegrees = 60f, gyroTurnDegrees = 62f, samples = 120)
        )
        assertEquals(
            SpinVerdict.INSUFFICIENT,
            CompassSpin.verdict(magneticTurnDegrees = 358f, gyroTurnDegrees = 360f, samples = 5)
        )
    }
}

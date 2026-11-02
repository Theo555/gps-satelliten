package de.gpsradar.satellites.gps

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import de.gpsradar.satellites.model.CompassHealth
import de.gpsradar.satellites.model.CompassSpin
import de.gpsradar.satellites.model.CompassSpinState
import de.gpsradar.satellites.model.CompassWalk
import de.gpsradar.satellites.model.CompassWalkState
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.SpinVerdict
import de.gpsradar.satellites.model.WalkSample
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

private const val TAG = "CompassSensorHub"

// Kompass-Überwachung: Proben für den Feldmittelwert und Anzeigeschwelle.
private const val FIELD_SAMPLES_MIN = 8
private const val FIELD_SAMPLES_MAX = 16
private const val FIELD_DISPLAY_STEP_UT = 1f

// Drehtest: eine volle Umdrehung ist das Ziel, danach bricht die Messung ab.
private const val SPIN_COMPLETE_DEGREES = 355f
private const val SPIN_TIMEOUT_MS = 45_000L

// Ablage der Drehtest-Läufe. Nur im privaten App-Verzeichnis, keine Übertragung.
private const val COMPASS_RUNS_FILE = "compass_runs.jsonl"
private const val MAX_COMPASS_RUNS = 20

// Geh-Test: Vergleich der Anzeige mit Magnetkompass und GPS-Kurs.
private const val WALK_DURATION_MS = 90_000L
private const val WALK_SAMPLE_INTERVAL_NS = 200_000_000L
private const val WALK_FILE_PREFIX = "compass_walk_"

/**
 * Kompass-Teil des Ortungsdienstes: Peilung aus den Sensoren, Überwachung der
 * Sensorqualität, Drehtest gegen den Kreisel und Ortsmissweisung.
 *
 * Eigene Datei, weil hier viel Messlogik steckt, die mit Satellitenempfang und
 * Positionsbestimmung nichts zu tun hat. Der Hub liest die Position aus
 * [state] (für die Missweisung) und schreibt Peilung und Befund dorthin
 * zurück. Die Referenzrichtung für die Pfeile kommt von [referenceDirection],
 * weil sie auch vom GPS-Kurs abhängt – und der gehört dem Aufrufer.
 */
class CompassSensorHub(
    context: Context,
    private val state: MutableStateFlow<GpsLocationState>,
    private val referenceDirection: () -> Float,
    private val courseDegrees: () -> Float?
) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager =
        appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private val rotationVectorSensor: Sensor?
    private val accelerometerSensor: Sensor?
    private val magneticSensor: Sensor?

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var hasAccelerometer = false
    private var hasMagnetometer = false
    private var smoothedHeading = 0f

    // Kompass-Überwachung (Sensorgenauigkeit + Feldbetrag).
    private var compassAccuracyLow = false
    private val recentFieldMagnitudes = ArrayDeque<Float>()

    // Ortsmissweisung: Sensor misst magnetisch, gerechnet wird auf wahres Norden.
    private val declination = Declination()

    // Drehtest: Magnet gegen Kreisel. Läuft nur auf Anforderung der Oberfläche.
    private val _spinState = MutableStateFlow(CompassSpinState())
    val spinState: StateFlow<CompassSpinState> = _spinState.asStateFlow()
    private var spinRunning = false
    private var spinSamples = ArrayList<CompassSpin.SpinSample>()
    private var spinGyroTicks = ArrayList<CompassSpin.GyroTick>()
    private var spinGyroTurn = 0f
    private var spinLastGyroTimestampNs = 0L
    private val spinGravity = FloatArray(3)
    private var spinHasGravity = false
    private val spinTimeout = Runnable { finishCompassSpin() }

    // Geh-Test: eigene Aufzeichnung, damit sich Anzeige, Magnetkompass und
    // GPS-Kurs im selben Zeitraster vergleichen lassen.
    private val _walkState = MutableStateFlow(CompassWalkState())
    val walkState: StateFlow<CompassWalkState> = _walkState.asStateFlow()
    private var walkRunning = false
    private var walkSamples = ArrayList<WalkSample>()
    private var walkEndsAtMillis = 0L
    private var walkLastSampleNs = 0L
    private val walkTicker = object : Runnable {
        override fun run() {
            if (!walkRunning) return
            val left = ((walkEndsAtMillis - System.currentTimeMillis()) / 1000L).toInt()
            _walkState.update { it.copy(secondsLeft = left.coerceAtLeast(0), samples = walkSamples.size) }
            if (left <= 0) {
                finishWalk()
            } else {
                mainHandler.postDelayed(this, 1000L)
            }
        }
    }

    init {
        rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        // Magnetometer (und als Ersatzpfad der Beschleunigungssensor) unabhängig
        // vom Rotationsvektor auflösen: das Magnetometer dient auch der
        // Feldbetrag-Überwachung.
        accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magneticSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
    }

    /** Zuletzt geglättete Peilung in Grad, bereits auf wahres Norden bezogen. */
    val headingDegrees: Float get() = smoothedHeading

    fun start() {
        if (rotationVectorSensor != null) {
            sensorManager?.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
            // Magnetometer zusätzlich registrieren – nur für die Feldbetrag-Überwachung.
            sensorManager?.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager?.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager?.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stop() {
        sensorManager?.unregisterListener(this)
        // Ein laufender Drehtest endet mit dem Sensorstopp ohne Ergebnis.
        if (spinRunning) {
            spinRunning = false
            mainHandler.removeCallbacks(spinTimeout)
            _spinState.update { it.copy(running = false) }
        }
        // Eine laufende Aufzeichnung dagegen wird gerettet, nicht verworfen.
        if (walkRunning) finishWalk()
    }

    /**
     * Startet den Drehtest. Ohne Kreisel gibt es keinen Test – dann meldet der
     * Zustand [CompassSpinState.supported] = false, statt still nichts zu tun.
     */
    fun startSpin() {
        val manager = sensorManager ?: return
        val gyro = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        if (gyro == null) {
            _spinState.update { it.copy(supported = false, running = false) }
            return
        }
        if (spinRunning) finishCompassSpin()

        spinSamples = ArrayList()
        spinGyroTicks = ArrayList()
        spinGyroTurn = 0f
        spinLastGyroTimestampNs = 0L
        spinHasGravity = false
        spinRunning = true

        // Vorigen Durchlauf als Vergleichswert aufheben – aber nur, wenn er ein
        // echtes Urteil hatte (eine Nullmessung vergleicht man nicht).
        val previous = _spinState.value
        val previousVerdict = previous.verdict
        val previousDeviation =
            if (previous.verdict != SpinVerdict.INSUFFICIENT) previous.deviationDegrees else null
        _spinState.update {
            it.copy(
                supported = true,
                running = true,
                finished = false,
                turnDegrees = 0f,
                samples = 0,
                magneticTurnDegrees = 0f,
                gyroTurnDegrees = 0f,
                deviationDegrees = null,
                verdict = SpinVerdict.INSUFFICIENT,
                lastVerdict = previousVerdict,
                lastDeviationDegrees = previousDeviation ?: it.lastDeviationDegrees
            )
        }

        manager.registerListener(this, gyro, SensorManager.SENSOR_DELAY_GAME)
        // Beschleunigungssensor nur für die Schwerkraftrichtung während des Tests.
        // Die Peilung rechnet er nicht mit, siehe computeHeadingFromAccAndMag().
        manager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
        mainHandler.postDelayed(spinTimeout, SPIN_TIMEOUT_MS)
    }

    /** Bricht den Drehtest ab, ohne ein Ergebnis zu veröffentlichen. */
    fun stopSpin() {
        if (!spinRunning) return
        spinRunning = false
        mainHandler.removeCallbacks(spinTimeout)
        unregisterSpinSensors()
        _spinState.update { it.copy(running = false) }
    }

    private fun finishCompassSpin() {
        if (!spinRunning) return
        spinRunning = false
        mainHandler.removeCallbacks(spinTimeout)
        unregisterSpinSensors()

        val magneticTurn = CompassSpin.magneticTurnDegrees(spinSamples.map { it.azimuthDegrees })
        val gyroTurn = spinGyroTurn
        // Ausschlaggebend ist der winkelweise Fehler nach Herausrechnen der
        // Sensorlatenz. Die Summe allein verdeckt einen Hard-Iron-Versatz
        // vollständig, und ohne Latenzausgleich hängt das Urteil davon ab,
        // wie schnell der Nutzer dreht (an 20 °/s gemessen: 1° gegen 6°).
        val fit = CompassSpin.bestLagFit(spinSamples, spinGyroTicks)
        val disturbed = CompassSpin.isEnvironmentDisturbed(spinSamples)
        val verdict = CompassSpin.verdict(
            magneticTurn,
            gyroTurn,
            spinSamples.size,
            fit.deviationDegrees,
            disturbed
        )
        Log.d(
            TAG,
            "Drehtest: Kreisel ${gyroTurn}°, Magnet ${magneticTurn}°, Nachlauf " +
                "${fit.lagNanos / 1_000_000} ms, Fehler ${fit.deviationDegrees}°, " +
                "Feldstreuung ${CompassSpin.fieldSpreadPercent(spinSamples)}%, " +
                "Gegenlauf ${CompassSpin.counterRotationDegrees(spinSamples)}°, " +
                "gestört $disturbed, Proben ${spinSamples.size}"
        )
        _spinState.update {
            it.copy(
                running = false,
                finished = true,
                turnDegrees = abs(gyroTurn),
                samples = spinSamples.size,
                magneticTurnDegrees = magneticTurn,
                gyroTurnDegrees = gyroTurn,
                deviationDegrees = fit.deviationDegrees,
                lagMillis = (fit.lagNanos / 1_000_000).toInt(),
                verdict = verdict
            )
        }
        // Erst nach der Aktualisierung ablegen – sonst steht im gespeicherten
        // Lauf noch das alte Urteil (Fehler am 02.11. genau so passiert).
        storeSpinRun()
    }

    /**
     * Legt den abgeschlossenen Drehtest im privaten App-Verzeichnis ab.
     *
     * Damit lassen sich Messungen an mehreren Orten sammeln und später
     * vergleichen – nötig, um „Gerät" von „dieser Ort" zu trennen. Die Datei
     * bleibt auf dem Gerät, wird nicht übertragen und ist auf die letzten
     * [MAX_COMPASS_RUNS] Läufe begrenzt.
     */
    private fun storeSpinRun() {
        runCatching {
            val series = JSONArray()
            val first = spinSamples.firstOrNull()?.timeNanos ?: 0L
            for (sample in spinSamples) {
                series.put(
                    JSONArray()
                        .put((sample.timeNanos - first) / 1_000_000)
                        .put(sample.tiltDegrees.toInt())
                        .put(sample.azimuthDegrees.toInt())
                        .put(sample.gyroTurnDegrees.toInt())
                        .put(sample.fieldMicroTesla.toInt())
                )
            }
            val entry = JSONObject()
                .put("ts", System.currentTimeMillis())
                .put("verdict", _spinState.value.verdict.name)
                .put("deviation", _spinState.value.deviationDegrees ?: -1f)
                .put("lagMs", _spinState.value.lagMillis)
                .put("counter", CompassSpin.counterRotationDegrees(spinSamples))
                .put("spread", CompassSpin.fieldSpreadPercent(spinSamples))
                .put("samples", spinSamples.size)
                .put("series", series)

            val file = File(appContext.filesDir, COMPASS_RUNS_FILE)
            val previous = if (file.exists()) {
                file.readLines().filter { it.isNotBlank() }
            } else {
                emptyList()
            }
            file.writeText((previous + entry.toString()).takeLast(MAX_COMPASS_RUNS).joinToString("\n"))
        }.onFailure { Log.w(TAG, "Drehtest konnte nicht abgelegt werden: ${it.message}") }
    }

    private fun unregisterSpinSensors() {
        val manager = sensorManager ?: return
        manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { manager.unregisterListener(this, it) }
        accelerometerSensor?.let { manager.unregisterListener(this, it) }
    }

    private fun publishSpinProgress() {
        _spinState.update {
            it.copy(turnDegrees = abs(spinGyroTurn), samples = spinSamples.size)
        }
    }

    /** Aktueller Feldbetrag aus dem letzten Magnetometerwert. */
    private fun currentFieldMicroTesla(): Float {
        if (!hasMagnetometer) return 0f
        return sqrt(
            magnetometerReading[0] * magnetometerReading[0] +
                magnetometerReading[1] * magnetometerReading[1] +
                magnetometerReading[2] * magnetometerReading[2]
        )
    }

    /** Ein Azimutwert ist eingetroffen – egal über welchen Pfad. */
    private fun onAzimuth(azimuth: Float, timeNanos: Long) {
        if (spinRunning) {
            // Azimut, Kreiselwinkel, Zeit, Neigung und Feldbetrag gehören zusammen.
            spinSamples.add(
                CompassSpin.SpinSample(
                    azimuth,
                    spinGyroTurn,
                    timeNanos,
                    CompassSpin.tiltDegrees(spinGravity),
                    currentFieldMicroTesla()
                )
            )
            publishSpinProgress()
        }
        val corrected = updateSmoothedHeading(azimuth)
        if (walkRunning) recordWalkSample(azimuth, corrected, timeNanos)
    }

    /**
     * Startet die Aufzeichnung für den Geh-Test.
     *
     * Ab hier wird fünfmal je Sekunde festgehalten, was die App anzeigt, was
     * ein reiner Magnetkompass sagt und welchen Kurs das GPS misst. Die
     * Aufzeichnung endet von selbst nach [WALK_DURATION_MS], damit sie nicht
     * vergessen werden kann.
     */
    fun startWalk() {
        if (walkRunning) return
        // Für die Magnetkompass-Spalte braucht es die Schwerkraftrichtung. Der
        // Beschleunigungssensor läuft sonst nicht mit (der Rotationsvektor
        // reicht für die Peilung) – deshalb nur für die Dauer des Tests.
        accelerometerSensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        walkSamples = ArrayList()
        walkLastSampleNs = 0L
        walkRunning = true
        walkEndsAtMillis = System.currentTimeMillis() + WALK_DURATION_MS
        _walkState.update {
            CompassWalkState(
                running = true,
                secondsLeft = (WALK_DURATION_MS / 1000L).toInt(),
                samples = 0,
                finished = false
            )
        }
        mainHandler.postDelayed(walkTicker, 1000L)
    }

    /** Beendet die Aufzeichnung vorzeitig und legt sie ab. */
    fun stopWalk() {
        if (walkRunning) finishWalk()
    }

    private fun finishWalk() {
        if (!walkRunning) return
        walkRunning = false
        mainHandler.removeCallbacks(walkTicker)
        // Nur abmelden, wenn ihn nicht gerade der Drehtest braucht.
        if (!spinRunning) {
            accelerometerSensor?.let { sensorManager?.unregisterListener(this, it) }
        }
        val samples = walkSamples
        val fileName = writeWalkFile(samples)
        _walkState.update {
            it.copy(
                running = false,
                finished = true,
                secondsLeft = 0,
                samples = samples.size,
                shownVsMagneticDegrees = CompassWalk.shownVsMagnetic(samples),
                shownVsCourseDegrees = CompassWalk.shownVsCourse(samples),
                courseSamples = CompassWalk.courseSampleCount(samples),
                fileName = fileName
            )
        }
    }

    private fun recordWalkSample(raw: Float, shown: Float, timeNanos: Long) {
        if (timeNanos - walkLastSampleNs < WALK_SAMPLE_INTERVAL_NS) return
        walkLastSampleNs = timeNanos
        walkSamples.add(
            WalkSample(
                timeMillis = timeNanos / 1_000_000L,
                rawDegrees = raw,
                shownDegrees = shown,
                magneticDegrees = magneticAzimuth(),
                courseDegrees = courseDegrees(),
                fieldMicroTesla = currentFieldMicroTesla(),
                tiltDegrees = CompassSpin.tiltDegrees(accelerometerReading)
            )
        )
    }

    /**
     * Peilung aus Beschleunigung und Magnetfeld – also ohne die Rechenwege der
     * Plattform. Weicht sie von [rawDegrees] um die Ortsmissweisung ab, rechnet
     * die Plattform die Missweisung bereits ein.
     */
    private fun magneticAzimuth(): Float? {
        if (!hasAccelerometer || !hasMagnetometer) return null
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        val ok = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            accelerometerReading,
            magnetometerReading
        )
        if (!ok) return null
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        if (azimuth < 0) azimuth += 360f
        return azimuth
    }

    /** Legt die Aufzeichnung als CSV im privaten App-Verzeichnis ab. */
    private fun writeWalkFile(samples: List<WalkSample>): String? {
        if (samples.isEmpty()) return null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val fileName = "$WALK_FILE_PREFIX$stamp.csv"
        val written = runCatching {
            val text = buildString {
                appendLine("# geraet = ${Build.MANUFACTURER} ${Build.MODEL}")
                appendLine("# android = ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
                appendLine(
                    "# zeit = " +
                        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
                )
                appendLine("# probes = ${samples.size}")
                appendLine("# spalten = t_ms,raw_deg,shown_deg,mag_deg,course_deg,field_ut,tilt_deg")
                appendLine("t_ms,raw_deg,shown_deg,mag_deg,course_deg,field_ut,tilt_deg")
                for (sample in samples) {
                    appendLine(
                        listOf(
                            sample.timeMillis.toString(),
                            formatAngle(sample.rawDegrees),
                            formatAngle(sample.shownDegrees),
                            sample.magneticDegrees?.let { formatAngle(it) } ?: "",
                            sample.courseDegrees?.let { formatAngle(it) } ?: "",
                            formatAngle(sample.fieldMicroTesla),
                            formatAngle(sample.tiltDegrees)
                        ).joinToString(",")
                    )
                }
            }
            File(appContext.filesDir, fileName).writeText(text)
        }.onFailure {
            Log.w(TAG, "Geh-Test konnte nicht abgelegt werden: ${it.message}")
        }.isSuccess
        return if (written) fileName else null
    }

    private fun formatAngle(value: Float): String = String.format(Locale.US, "%.2f", value)

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360f
            onAzimuth(azimuth, event.timestamp)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
            hasAccelerometer = true
            if (spinRunning) {
                System.arraycopy(event.values, 0, spinGravity, 0, 3)
                spinHasGravity = true
            }
            computeHeadingFromAccAndMag(event.timestamp)
        } else if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            if (!spinRunning) return
            if (spinLastGyroTimestampNs != 0L && spinHasGravity) {
                val dt = (event.timestamp - spinLastGyroTimestampNs) / 1_000_000_000f
                if (dt > 0f && dt < 0.25f) {
                    spinGyroTurn += CompassSpin.azimuthRate(spinGravity, event.values) * dt
                }
            }
            spinLastGyroTimestampNs = event.timestamp
            spinGyroTicks.add(CompassSpin.GyroTick(event.timestamp, spinGyroTurn))
            if (abs(spinGyroTurn) >= SPIN_COMPLETE_DEGREES) {
                finishCompassSpin()
            } else {
                publishSpinProgress()
            }
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
            hasMagnetometer = true
            val magnitude = sqrt(
                event.values[0] * event.values[0] +
                    event.values[1] * event.values[1] +
                    event.values[2] * event.values[2]
            )
            recentFieldMagnitudes.addLast(magnitude)
            while (recentFieldMagnitudes.size > FIELD_SAMPLES_MAX) recentFieldMagnitudes.removeFirst()
            updateCompassHealth()
            computeHeadingFromAccAndMag(event.timestamp)
        }
    }

    private fun computeHeadingFromAccAndMag(timeNanos: Long) {
        // Läuft der Rotationsvektor, bleibt er die einzige Peilquelle. Sonst
        // speisen zwei Wege denselben Wert und die Nadel zappelt.
        if (rotationVectorSensor != null) return
        if (!hasAccelerometer || !hasMagnetometer) return
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        val success = SensorManager.getRotationMatrix(
            rotationMatrix,
            inclinationMatrix,
            accelerometerReading,
            magnetometerReading
        )
        if (success) {
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360f
            onAzimuth(azimuth, timeNanos)
        }
    }

    private fun updateSmoothedHeading(newHeading: Float): Float {
        // Der Sensor liefert zum magnetischen Norden, der GPS-Kurs gilt für
        // wahres Norden. Die Deklination wird deshalb hier einmal addiert –
        // danach rechnet die ganze App in einem Bezugssystem.
        val corrected = (newHeading + declinationDegrees() + 360f) % 360f
        // Low pass filter with circular degree wrap-around
        var diff = corrected - smoothedHeading
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f
        smoothedHeading = (smoothedHeading + diff * 0.2f + 360f) % 360f

        state.update {
            it.copy(
                compassHeading = smoothedHeading,
                headingReferenceDegrees = referenceDirection()
            )
        }
        return corrected
    }

    /** Ortsmissweisung an der zuletzt bekannten Position (0 ohne Fix). */
    private fun declinationDegrees(): Float {
        val current = state.value
        return declination.degreesAt(
            current.latitude,
            current.longitude,
            current.altitudeMeters,
            System.currentTimeMillis()
        )
    }

    /** Wertet Sensorgenauigkeit und Feldbetrag aus und veröffentlicht den Befund samt Messwert. */
    private fun updateCompassHealth() {
        val fieldMicroTesla = recentFieldMagnitudes
            .takeIf { it.size >= FIELD_SAMPLES_MIN }
            ?.average()
            ?.toFloat()
        val issue = CompassHealth.issue(fieldMicroTesla, compassAccuracyLow)

        val current = state.value
        val previous = current.compassFieldMicroTesla
        // Feldwert nur übernehmen, wenn er sich sichtbar ändert – sonst flackert
        // die Anzeige mit jedem Sensorereignis.
        val fieldChanged = when {
            fieldMicroTesla == null -> false
            previous == null -> true
            else -> abs(previous - fieldMicroTesla) >= FIELD_DISPLAY_STEP_UT
        }
        if (issue != current.compassIssue || fieldChanged) {
            state.update {
                it.copy(
                    compassIssue = issue,
                    compassFieldMicroTesla = if (fieldChanged) fieldMicroTesla else previous
                )
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        if (sensor == null) return
        if (sensor.type == Sensor.TYPE_ROTATION_VECTOR || sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            // Nur eine echte Rückstufung zählt: SENSOR_STATUS_UNRELIABLE (-1) heißt
            // "keine Angabe", nicht "schlecht" – Samsung liefert für den
            // Rotationsvektor dauerhaft -1. Wer das mitzählt, hat auf diesen
            // Geräten eine Warnung, die nie wieder ausgeht.
            compassAccuracyLow = accuracy == SensorManager.SENSOR_STATUS_ACCURACY_LOW
            updateCompassHealth()
        }
    }
}

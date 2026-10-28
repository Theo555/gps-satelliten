package de.gpsradar.satellites.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssClock
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import de.gpsradar.satellites.model.BufferStats
import de.gpsradar.satellites.model.CapturePhase
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.EpochBuffer
import de.gpsradar.satellites.model.MultipathState
import de.gpsradar.satellites.model.RawClockSnapshot
import de.gpsradar.satellites.model.RawEpoch
import de.gpsradar.satellites.model.RawMeasurement
import de.gpsradar.satellites.model.gnssBandLabel
import de.gpsradar.satellites.model.gnssBandShort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.floor

private const val TAG = "RawGnssManager"
private const val MAX_REGISTER_ATTEMPTS = 5
private const val REGISTER_RETRY_MS = 2000L

// Wachhund: Der Empfänger kann registriert sein und trotzdem schweigen
// (z. B. nach einem Prozess-Neustart, während Standort/Status weiterlaufen).
// Dann erst neu registrieren, nach mehreren Fehlversuchen einen Hinweis
// für die Oberfläche setzen.
private const val STALL_CHECK_MS = 10_000L
private const val STALL_THRESHOLD_MS = 30_000L
private const val MAX_RECOVERY_ATTEMPTS = 4
private const val RECOVER_CHECK_MS = 15_000L

/**
 * Sammelt GNSS-Roh-Messungen (GnssMeasurements) — ein Eintrag pro Satellit × Frequenzband.
 */
class RawGnssManager(context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val handler = Handler(Looper.getMainLooper())

    /** true, solange ein Mess-Callback registriert ist. */
    private var registered = false
    private var registerAttempts = 0

    /** Zeitpunkt des letzten Mess-Ereignisses (elapsedRealtime); 0 = noch keins. */
    private var lastEventAt = 0L
    private var stallAttempts = 0
    private val stallWatchdog = Runnable { checkStall() }

    private val _streamStalled = MutableStateFlow(false)
    /** true, wenn registriert, aber seit >30 s keine Rohdaten ankommen. */
    val streamStalled: StateFlow<Boolean> = _streamStalled.asStateFlow()

    private val registerRetry = Runnable { registerIfPossible() }

    private val _supportsRawMeasurements = MutableStateFlow(false)
    val supportsRawMeasurements: StateFlow<Boolean> = _supportsRawMeasurements.asStateFlow()

    private val _measurements = MutableStateFlow<List<RawMeasurement>>(emptyList())
    val measurements: StateFlow<List<RawMeasurement>> = _measurements.asStateFlow()

    private val _clock = MutableStateFlow<RawClockSnapshot?>(null)
    val clock: StateFlow<RawClockSnapshot?> = _clock.asStateFlow()

    /** Letzte 60 s im Normalbetrieb, alles während einer Aufzeichnung. */
    private val buffer = EpochBuffer()

    private val _capturePhase = MutableStateFlow(CapturePhase.IDLE)
    val capturePhase: StateFlow<CapturePhase> = _capturePhase.asStateFlow()

    private val _bufferStats = MutableStateFlow(BufferStats(0, 0))
    val bufferStats: StateFlow<BufferStats> = _bufferStats.asStateFlow()

    private val callback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
            handleEvent(event)
        }

        override fun onStatusChanged(status: Int) {
            // Für die Anzeige nicht relevant.
        }
    }

    @SuppressLint("MissingPermission")
    fun start(hasPermission: Boolean) {
        if (!hasPermission) {
            stop()
            _supportsRawMeasurements.value = false
            return
        }
        val lm = locationManager
        if (lm == null) {
            _supportsRawMeasurements.value = false
            return
        }

        if (!registered) {
            registerAttempts = 0
            registerIfPossible()
        }

        // Ab Android 10 sagt die Plattform die Fähigkeit direkt. Der Rückgabewert
        // der Registrierung ist unzuverlässig: kurz nach dem Prozessstart oder
        // wenn die GNSS-Engine gerade steht, liefert er false – das Gerät kann
        // Rohdaten trotzdem. Deshalb nicht daran die Anzeige festmachen.
        _supportsRawMeasurements.value = capabilitySupported(lm) ?: registered

        // Wachhund: Registrierung klappt, aber der Empfänger kann trotzdem
        // schweigen. Erst neu registrieren, nach mehreren Fehlversuchen den
        // Hinweis in der Oberfläche setzen.
        lastEventAt = 0L
        stallAttempts = 0
        _streamStalled.value = false
        handler.removeCallbacks(stallWatchdog)
        handler.postDelayed(stallWatchdog, STALL_CHECK_MS)
    }

    /** Rohdaten-Fähigkeit laut Plattform (ab API 31); null, wenn nicht ermittelbar.
     *  Auf Android 10/11 verlangt der Aufruf LOCATION_HARDWARE – eine Signatur-
     *  Berechtigung, die normale Apps nicht bekommen. Dort entscheidet die
     *  Registrierung.
     */
    private fun capabilitySupported(lm: LocationManager): Boolean? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                lm.gnssCapabilities.hasMeasurements()
            } catch (e: Exception) {
                Log.d(TAG, "gnssCapabilities nicht lesbar", e)
                null
            }
        } else {
            null
        }

    @SuppressLint("MissingPermission")
    private fun registerIfPossible() {
        if (registered) return
        val lm = locationManager ?: return
        val ok = try {
            lm.registerGnssMeasurementsCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "Rohdaten-Callback abgelehnt", e)
            false
        }
        registered = ok
        if (ok) {
            registerAttempts = 0
            Log.i(TAG, "Rohdaten-Callback registriert")
        } else if (registerAttempts < MAX_REGISTER_ATTEMPTS) {
            registerAttempts++
            Log.w(TAG, "Rohdaten-Callback noch nicht möglich (Versuch $registerAttempts)")
            handler.postDelayed(registerRetry, REGISTER_RETRY_MS)
        }
    }

    fun stop() {
        handler.removeCallbacks(registerRetry)
        handler.removeCallbacks(stallWatchdog)
        registerAttempts = 0
        lastEventAt = 0L
        stallAttempts = 0
        _streamStalled.value = false
        if (registered) {
            try {
                locationManager?.unregisterGnssMeasurementsCallback(callback)
            } catch (e: Exception) {
                // ignore
            }
            registered = false
        }
        _measurements.value = emptyList()
        _clock.value = null
        // Aufzeichnung nicht verwerfen: einfrieren, damit z. B. ein Drehen des
        // Geräts (Activity wird neu erstellt) die gesammelten Daten nicht löscht.
        if (_capturePhase.value == CapturePhase.CAPTURING) {
            finishCapture()
        }
    }

    /** Aufzeichnung starten: ab jetzt bleibt alles stehen, bis sie endet. */
    fun startCapture() {
        buffer.clear()
        buffer.keepAll = true
        _capturePhase.value = CapturePhase.CAPTURING
        publishStats()
    }

    /** Zeit abgelaufen (oder vom Nutzer beendet): Puffer einfrieren. */
    fun finishCapture() {
        if (_capturePhase.value != CapturePhase.CAPTURING) return
        buffer.freeze()
        _capturePhase.value = CapturePhase.FINISHED
        publishStats()
    }

    /** Laufende Aufzeichnung abbrechen. */
    fun cancelCapture() = resetCapture()

    /** Fertige Aufzeichnung verwerfen, zurück in den Ringpuffer-Betrieb. */
    fun discardCapture() = resetCapture()

    private fun resetCapture() {
        buffer.clear()
        _capturePhase.value = CapturePhase.IDLE
        publishStats()
    }

    /** Aktueller Pufferinhalt für den Export. */
    fun epochSnapshot(): List<RawEpoch> = buffer.snapshot()

    private fun publishStats() {
        _bufferStats.value = BufferStats(buffer.epochCount, buffer.rowCount)
    }

    /** Wachhund-Prüfung: registriert, aber keine Daten? Erst neu registrieren,
     *  nach mehreren Fehlversuchen den Hinweis-Zustand setzen. Sobald wieder
     *  Ereignisse ankommen, setzt handleEvent alles zurück. */
    private fun checkStall() {
        if (!registered) return
        val now = SystemClock.elapsedRealtime()
        val silent = lastEventAt == 0L || now - lastEventAt > STALL_THRESHOLD_MS
        if (!silent) {
            _streamStalled.value = false
            handler.postDelayed(stallWatchdog, STALL_CHECK_MS)
            return
        }
        if (stallAttempts < MAX_RECOVERY_ATTEMPTS) {
            stallAttempts++
            val since = if (lastEventAt == 0L) "Start" else "${(now - lastEventAt) / 1000}s"
            Log.w(TAG, "Keine Rohdaten seit $since – Recover-Versuch $stallAttempts (neu registrieren)")
            tryRecover()
            handler.postDelayed(stallWatchdog, RECOVER_CHECK_MS)
        } else {
            _streamStalled.value = true
            Log.w(TAG, "Rohdaten bleiben aus – Hinweis in der Oberfläche anzeigen")
            handler.postDelayed(stallWatchdog, STALL_CHECK_MS * 6)
        }
    }

    /** Abmelden und neu registrieren, ohne die Fähigkeit zurückzustufen. */
    private fun tryRecover() {
        val lm = locationManager ?: return
        if (registered) {
            try {
                lm.unregisterGnssMeasurementsCallback(callback)
            } catch (_: Exception) {
                // ignore
            }
            registered = false
        }
        registerAttempts = 0
        registerIfPossible()
    }

    private fun handleEvent(event: GnssMeasurementsEvent) {
        lastEventAt = SystemClock.elapsedRealtime()
        _streamStalled.value = false
        val clock = event.clock
        val snapshot = clock.toSnapshot()
        _clock.value = snapshot

        // GnssClock.HAS_NO_FULL_BIAS = Long.MIN_VALUE ("unbekannt"); HAS_NO_BIAS = NaN.
        val clockUsable = clock.fullBiasNanos != Long.MIN_VALUE && !clock.biasNanos.isNaN()
        val tRxNanos = clock.timeNanos - clock.fullBiasNanos - clock.biasNanos
        val weekOffsetNanos = floor(-clock.fullBiasNanos.toDouble() / WEEK_NANOS) * WEEK_NANOS

        val list = event.measurements.mapNotNull { measurement ->
            measurement.toRawMeasurement(tRxNanos, weekOffsetNanos, clockUsable)
        }

        // Pro (SVID, Band) nur den letzten Wert behalten.
        val latest = list
            .groupBy { it.svid to it.carrierFrequencyHz }
            .values
            .map { it.last() }
            .sortedWith(compareByDescending<RawMeasurement> { it.cn0DbHz })

        _measurements.value = latest

        buffer.add(RawEpoch(System.currentTimeMillis(), snapshot, latest))
        publishStats()
    }

    private fun GnssMeasurement.toRawMeasurement(tRxNanos: Double, weekOffsetNanos: Double, clockUsable: Boolean): RawMeasurement? {
        val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasCarrierFrequencyHz()) {
            carrierFrequencyHz
        } else {
            0f
        }

        // GnssMeasurement.getConstellationType() gibt es seit API 24 (= minSdk) und ist
        // eindeutig. Die Frequenz-Heuristik kann L1/E1/QZSS/SBAS (alle 1575,42 MHz)
        // nicht trennen und dient deshalb nur als Notnagel bei CONSTELLATION_UNKNOWN.
        val constellation = Constellation.fromTypeId(constellationType)
            .let { if (it == Constellation.UNKNOWN) constellationForFreq(freq) else it }

        val tTxNanos = receivedSvTimeNanos + weekOffsetNanos
        // GnssMeasurement.HAS_NO_RECEIVED_SV_TIME_NANOS = Long.MIN_VALUE ("unbekannt").
        val pseudorange = if (clockUsable && receivedSvTimeNanos != Long.MIN_VALUE) {
            val meters = (tRxNanos - tTxNanos) * 1e-9 * SPEED_OF_LIGHT
            if (meters in PSEUDORANGE_MIN_M..PSEUDORANGE_MAX_M) meters else null
        } else {
            null
        }

        val adrState = accumulatedDeltaRangeState
        val phaseValid = (adrState and GnssMeasurement.ADR_STATE_VALID) != 0

        val multipath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (multipathIndicator) {
                GnssMeasurement.MULTIPATH_INDICATOR_DETECTED -> MultipathState.DETECTED
                GnssMeasurement.MULTIPATH_INDICATOR_NOT_DETECTED -> MultipathState.NOT_DETECTED
                else -> MultipathState.UNKNOWN
            }
        } else {
            MultipathState.UNKNOWN
        }

        // Seit API 34 als veraltet markiert, liefert aber auf allen
        // unterstuetzten Versionen weiterhin Werte.
        @Suppress("DEPRECATION")
        val phaseUncertainty =
            if (carrierPhaseUncertainty.isNaN()) null else carrierPhaseUncertainty

        // TOW: STATE_TOW_DECODED gibt es seit API 24, STATE_TOW_KNOWN erst ab
        // API 29. Auf aelteren Geraeten (z. B. Android 9) ist nur das erste Bit
        // aussagekraeftig.
        // GLONASS nutzt diese Bits nicht, sondern GLO_TOD_DECODED/GLO_TOD_KNOWN.
        val towDecoded = (state and GnssMeasurement.STATE_TOW_DECODED) != 0
        val towKnownFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (state and GnssMeasurement.STATE_TOW_KNOWN) != 0
        } else {
            false
        }
        val gloTodDecoded = (state and GnssMeasurement.STATE_GLO_TOD_DECODED) != 0
        val gloTodKnown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (state and GnssMeasurement.STATE_GLO_TOD_KNOWN) != 0
        } else {
            false
        }

        // Code-Lock: Galileo meldet ihn ueber ein eigenes Bit, nicht ueber
        // STATE_CODE_LOCK.
        val codeLocked = (state and GnssMeasurement.STATE_CODE_LOCK) != 0 ||
            (state and GnssMeasurement.STATE_GAL_E1BC_CODE_LOCK) != 0

        // Rohfelder für den CSV-Export: Sendezeit und ihre Unsicherheit.
        // GnssMeasurement.HAS_NO_RECEIVED_SV_TIME_NANOS = Long.MIN_VALUE.
        val tTxRawNanos = if (receivedSvTimeNanos != Long.MIN_VALUE) receivedSvTimeNanos else null
        val svTimeUncertaintyNanos = if (receivedSvTimeUncertaintyNanos > 0) receivedSvTimeUncertaintyNanos else null

        return RawMeasurement(
            svid = svid,
            constellation = constellation,
            carrierFrequencyHz = freq,
            bandLabel = gnssBandLabel(constellation, if (freq > 0f) freq else null),
            bandShort = gnssBandShort(constellation, if (freq > 0f) freq else null),
            cn0DbHz = cn0DbHz.toFloat(),
            pseudorangeMeters = pseudorange,
            dopplerMetersPerSec = pseudorangeRateMetersPerSecond,
            carrierPhaseMeters = accumulatedDeltaRangeMeters,
            phaseValid = phaseValid,
            multipath = multipath,
            receivedAtMillis = System.currentTimeMillis(),
            // Qualitätsmerkmale der Messung.
            stateFlags = state,
            towKnown = towDecoded || towKnownFlag || gloTodDecoded || gloTodKnown,
            msecAmbiguous = (state and GnssMeasurement.STATE_MSEC_AMBIGUOUS) != 0,
            codeLock = codeLocked,
            // HAS_NO_CARRIER_PHASE_UNCERTAINTY ist Double.NaN.
            carrierPhaseUncertaintyMeters = phaseUncertainty,
            tTxRawNanos = tTxRawNanos,
            svTimeUncertaintyNanos = svTimeUncertaintyNanos,
            adrState = adrState
        )
    }

    private fun GnssClock.toSnapshot(): RawClockSnapshot = RawClockSnapshot(
        timeNanos = timeNanos,
        fullBiasNanos = fullBiasNanos,
        biasNanos = biasNanos,
        driftNsPerSec = driftNanosPerSecond,
        leapSecond = leapSecond,
        receivedAtMillis = System.currentTimeMillis()
    )

    /** Grobe Konstellations-Zuordnung über die Trägerfrequenz (nur für API < 34). */
    private fun constellationForFreq(freqHz: Float): Constellation = when {
        freqHz in 1.598e9f..1.606e9f -> Constellation.GLONASS   // G1 1602 MHz
        freqHz in 1.560e9f..1.562e9f -> Constellation.BEIDOU     // B1 1561.098 MHz
        freqHz in 1.226e9f..1.229e9f -> Constellation.GPS        // L2 1227.60 MHz
        freqHz in 1.206e9f..1.208e9f -> Constellation.GALILEO    // E5b 1207.14 MHz
        else -> Constellation.UNKNOWN
    }

    private companion object {
        const val WEEK_NANOS = 604_800_000_000_000.0
        const val SPEED_OF_LIGHT = 299_792_458.0
        const val PSEUDORANGE_MIN_M = 5_000_000.0
        const val PSEUDORANGE_MAX_M = 30_000_000.0
    }
}

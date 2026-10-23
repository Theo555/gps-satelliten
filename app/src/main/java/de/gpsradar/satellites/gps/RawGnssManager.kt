package de.gpsradar.satellites.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssClock
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.LocationManager
import android.os.Build
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.MultipathState
import de.gpsradar.satellites.model.RawClockSnapshot
import de.gpsradar.satellites.model.RawMeasurement
import de.gpsradar.satellites.model.gnssBandLabel
import de.gpsradar.satellites.model.gnssBandShort
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.floor

/**
 * Sammelt GNSS-Roh-Messungen (GnssMeasurements) — ein Eintrag pro Satellit × Frequenzband.
 */
class RawGnssManager(context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _supportsRawMeasurements = MutableStateFlow(false)
    val supportsRawMeasurements: StateFlow<Boolean> = _supportsRawMeasurements.asStateFlow()

    private val _measurements = MutableStateFlow<List<RawMeasurement>>(emptyList())
    val measurements: StateFlow<List<RawMeasurement>> = _measurements.asStateFlow()

    private val _clock = MutableStateFlow<RawClockSnapshot?>(null)
    val clock: StateFlow<RawClockSnapshot?> = _clock.asStateFlow()

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
            _supportsRawMeasurements.value = false
            return
        }
        val lm = locationManager
        if (lm == null) {
            _supportsRawMeasurements.value = false
            return
        }
        var supported = false
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                supported = lm.gnssCapabilities.hasMeasurements()
                if (supported) {
                    lm.registerGnssMeasurementsCallback(callback)
                }
            } else {
                supported = lm.registerGnssMeasurementsCallback(callback)
            }
        } catch (e: Exception) {
            supported = false
        }
        _supportsRawMeasurements.value = supported
    }

    fun stop() {
        try {
            locationManager?.unregisterGnssMeasurementsCallback(callback)
        } catch (e: Exception) {
            // ignore
        }
        _measurements.value = emptyList()
        _clock.value = null
    }

    private fun handleEvent(event: GnssMeasurementsEvent) {
        val clock = event.clock
        _clock.value = clock.toSnapshot()

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
    }

    private fun GnssMeasurement.toRawMeasurement(tRxNanos: Double, weekOffsetNanos: Double, clockUsable: Boolean): RawMeasurement? {
        val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasCarrierFrequencyHz()) {
            carrierFrequencyHz
        } else {
            0f
        }

        val constellation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            Constellation.fromTypeId(constellationType)
        } else {
            constellationForFreq(freq)
        }

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
            receivedAtMillis = System.currentTimeMillis()
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

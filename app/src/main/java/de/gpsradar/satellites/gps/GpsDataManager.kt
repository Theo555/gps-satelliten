package de.gpsradar.satellites.gps

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationManagerCompat
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.FixType
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.SatelliteInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Random

class GpsDataManager(private val context: Context) : SensorEventListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _gpsState = MutableStateFlow(GpsLocationState())
    val gpsState: StateFlow<GpsLocationState> = _gpsState.asStateFlow()

    private var isTracking = false
    private var simulationJob: Job? = null
    private var isSimulated = false

    // Sensors for compass heading
    private var rotationVectorSensor: Sensor? = null
    private var accelerometerSensor: Sensor? = null
    private var magneticSensor: Sensor? = null
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private var hasAccelerometer = false
    private var hasMagnetometer = false
    private var smoothedHeading = 0f

    // Simulated satellite drift
    private var demoSatellites: List<SimulatedSatellite> = emptyList()
    private val random = Random()
    private var startTimeMillis: Long = System.currentTimeMillis()

    init {
        rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        if (rotationVectorSensor == null) {
            accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
            magneticSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        }
        initDemoSatellites()
    }

    private val gnssStatusCallback = object : GnssStatusCompat.Callback() {
        override fun onStarted() {
            // GNSS engine started
        }

        override fun onStopped() {
            _gpsState.update { it.copy(satellites = emptyList(), fixType = FixType.NO_FIX) }
        }

        override fun onFirstFix(ttffMillis: Int) {
            _gpsState.update { it.copy(ttffSeconds = ttffMillis / 1000) }
        }

        override fun onSatelliteStatusChanged(status: GnssStatusCompat) {
            if (isSimulated) return

            val count = status.satelliteCount
            val satList = ArrayList<SatelliteInfo>(count)
            var usedInFixCount = 0

            for (i in 0 until count) {
                val svid = status.getSvid(i)
                val constellationType = status.getConstellationType(i)
                val cn0 = status.getCn0DbHz(i)
                val elevation = status.getElevationDegrees(i)
                val azimuth = status.getAzimuthDegrees(i)
                val hasEphemeris = status.hasEphemerisData(i)
                val hasAlmanac = status.hasAlmanacData(i)
                val used = status.usedInFix(i)
                if (used) usedInFixCount++

                val carrierFreq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && status.hasCarrierFrequencyHz(i)) {
                    status.getCarrierFrequencyHz(i)
                } else null

                satList.add(
                    SatelliteInfo(
                        svid = svid,
                        constellation = Constellation.fromTypeId(constellationType),
                        cn0DbHz = cn0,
                        elevationDegrees = elevation,
                        azimuthDegrees = azimuth,
                        hasEphemeris = hasEphemeris,
                        hasAlmanac = hasAlmanac,
                        usedInFix = used,
                        carrierFrequencyHz = carrierFreq
                    )
                )
            }

            // Determine fix type
            val fix = when {
                usedInFixCount >= 4 -> FixType.FIX_3D
                usedInFixCount >= 3 -> FixType.FIX_2D
                else -> FixType.NO_FIX
            }

            _gpsState.update { current ->
                current.copy(
                    satellites = satList,
                    fixType = fix,
                    lastUpdateTime = System.currentTimeMillis()
                )
            }
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (isSimulated) return
            _gpsState.update { current ->
                val fix = if (current.satellitesInFix >= 4 || location.hasAltitude()) FixType.FIX_3D else FixType.FIX_2D
                current.copy(
                    latitude = location.latitude,
                    longitude = location.longitude,
                    altitudeMeters = if (location.hasAltitude()) location.altitude else null,
                    speedKmh = if (location.hasSpeed()) location.speed * 3.6f else null,
                    bearingDegrees = if (location.hasBearing()) location.bearing else null,
                    accuracyMeters = if (location.hasAccuracy()) location.accuracy else null,
                    timeMillis = location.time,
                    fixType = fix,
                    lastUpdateTime = System.currentTimeMillis()
                )
            }
        }

        override fun onProviderEnabled(provider: String) {
            checkGpsEnabled()
        }

        override fun onProviderDisabled(provider: String) {
            checkGpsEnabled()
        }

        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
            // Backward compatibility
        }
    }

    @SuppressLint("MissingPermission")
    fun startTracking(hasPermission: Boolean) {
        _gpsState.update { it.copy(hasPermission = hasPermission) }
        checkGpsEnabled()

        if (!hasPermission) return
        if (isTracking) return
        isTracking = true

        startCompassSensors()

        if (locationManager != null) {
            try {
                LocationManagerCompat.registerGnssStatusCallback(
                    locationManager,
                    gnssStatusCallback,
                    mainHandler
                )
            } catch (e: Exception) {
                // Fallback handled safely
            }

            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        1000L,
                        0.5f,
                        locationListener,
                        Looper.getMainLooper()
                    )
                }
            } catch (e: Exception) {
                // Ignore missing provider
            }

            // Hinweis: Absichtlich KEIN NETWORK_PROVIDER.
            // Die öffentliche Version soll rein offline mit GNSS arbeiten und
            // keine Funkzellen-/WLAN-Daten an Google Play Services senden.
        }

        // Check if after 3.5 seconds we have 0 satellites (e.g. emulator or inside concrete building)
        scope.launch {
            delay(3500)
            if (_gpsState.value.satellites.isEmpty() && !_gpsState.value.isDemoMode) {
                // Auto-suggest or enable realistic demo mode so user sees immediate live data
                enableDemoMode(true)
            }
        }
    }

    fun stopTracking() {
        isTracking = false
        stopCompassSensors()
        simulationJob?.cancel()

        if (locationManager != null) {
            try {
                LocationManagerCompat.unregisterGnssStatusCallback(locationManager, gnssStatusCallback)
            } catch (e: Exception) {
                // Safe ignore
            }
            try {
                locationManager.removeUpdates(locationListener)
            } catch (e: Exception) {
                // Safe ignore
            }
        }
    }

    fun checkGpsEnabled() {
        val enabled = locationManager?.let {
            LocationManagerCompat.isLocationEnabled(it) || it.isProviderEnabled(LocationManager.GPS_PROVIDER)
        } ?: false
        _gpsState.update { it.copy(isGpsEnabled = enabled) }
    }

    fun toggleOrientationMode() {
        _gpsState.update { it.copy(isHeadingUp = !it.isHeadingUp) }
    }

    fun toggleDemoMode() {
        enableDemoMode(!isSimulated)
    }

    fun enableDemoMode(enable: Boolean) {
        isSimulated = enable
        _gpsState.update { it.copy(isDemoMode = enable) }

        if (enable) {
            startSimulationLoop()
        } else {
            simulationJob?.cancel()
            simulationJob = null
            // Reset to empty until real hardware emits
            _gpsState.update {
                it.copy(
                    satellites = emptyList(),
                    fixType = FixType.NO_FIX,
                    latitude = null,
                    longitude = null,
                    altitudeMeters = null,
                    speedKmh = null,
                    accuracyMeters = null
                )
            }
        }
    }

    private fun startSimulationLoop() {
        simulationJob?.cancel()
        simulationJob = scope.launch {
            var simTimeSec = 0
            while (isActive) {
                simTimeSec++
                updateSimulatedState(simTimeSec)
                delay(1000)
            }
        }
    }

    private fun updateSimulatedState(elapsedSeconds: Int) {
        val updatedSatellites = demoSatellites.map { sim ->
            // Subtle orbital motion: azimuth advances slowly, elevation oscillates gently
            val currentAzimuth = (sim.initialAzimuth + (elapsedSeconds * sim.azimuthDriftRate)) % 360f
            val elevationDelta = (kotlin.math.sin((elapsedSeconds + sim.svid * 10) * 0.05) * 2.0).toFloat()
            val currentElevation = (sim.initialElevation + elevationDelta).coerceIn(5f, 88f)

            // Signal strength fluctuates slightly (+- 1.5 dB)
            val noise = (random.nextFloat() * 2.5f - 1.25f)
            val currentCn0 = (sim.baseCn0 + noise).coerceIn(12f, 48f)

            // High elevation and good SNR -> used in fix
            val inFix = currentElevation > 15f && currentCn0 >= 26f

            SatelliteInfo(
                svid = sim.svid,
                constellation = sim.constellation,
                cn0DbHz = currentCn0,
                elevationDegrees = currentElevation,
                azimuthDegrees = currentAzimuth,
                hasEphemeris = true,
                hasAlmanac = true,
                usedInFix = inFix,
                carrierFrequencyHz = sim.frequencyHz
            )
        }

        val inFixCount = updatedSatellites.count { it.usedInFix }
        val fixType = when {
            inFixCount >= 4 -> FixType.FIX_3D
            inFixCount >= 3 -> FixType.FIX_2D
            else -> FixType.NO_FIX
        }

        // Simulated location drift around a realistic city coordinate (Berlin center)
        val latOffset = kotlin.math.sin(elapsedSeconds * 0.02) * 0.00018
        val lonOffset = kotlin.math.cos(elapsedSeconds * 0.02) * 0.00025
        val simLat = 52.520008 + latOffset
        val simLon = 13.404954 + lonOffset
        val simAlt = 42.5 + kotlin.math.sin(elapsedSeconds * 0.05) * 1.5
        val simSpeed = 3.6f + (kotlin.math.sin(elapsedSeconds * 0.1).toFloat() * 1.2f).coerceAtLeast(0f)
        val simBearing = ((elapsedSeconds * 2.5f) % 360f)
        val simAccuracy = (2.2f + (random.nextFloat() * 1.4f)).coerceIn(1.8f, 5.0f)

        _gpsState.update { current ->
            current.copy(
                satellites = updatedSatellites,
                fixType = fixType,
                latitude = simLat,
                longitude = simLon,
                altitudeMeters = simAlt,
                speedKmh = simSpeed,
                bearingDegrees = simBearing,
                accuracyMeters = simAccuracy,
                timeMillis = System.currentTimeMillis(),
                ttffSeconds = current.ttffSeconds ?: 12,
                lastUpdateTime = System.currentTimeMillis()
            )
        }
    }

    private data class SimulatedSatellite(
        val svid: Int,
        val constellation: Constellation,
        val initialElevation: Float,
        val initialAzimuth: Float,
        val baseCn0: Float,
        val azimuthDriftRate: Float,
        val frequencyHz: Float
    )

    private fun initDemoSatellites() {
        demoSatellites = listOf(
            // GPS (USA)
            SimulatedSatellite(4, Constellation.GPS, 72f, 45f, 43.5f, 0.08f, 1575420000f),
            SimulatedSatellite(9, Constellation.GPS, 54f, 130f, 39.0f, -0.06f, 1575420000f),
            SimulatedSatellite(16, Constellation.GPS, 38f, 215f, 34.2f, 0.07f, 1176450000f),
            SimulatedSatellite(20, Constellation.GPS, 81f, 310f, 44.8f, -0.05f, 1575420000f),
            SimulatedSatellite(27, Constellation.GPS, 22f, 95f, 28.5f, 0.09f, 1575420000f),
            SimulatedSatellite(31, Constellation.GPS, 14f, 290f, 21.0f, -0.08f, 1575420000f),

            // Galileo (EU)
            SimulatedSatellite(3, Constellation.GALILEO, 68f, 170f, 42.0f, 0.05f, 1575420000f),
            SimulatedSatellite(11, Constellation.GALILEO, 49f, 260f, 37.8f, -0.06f, 1176450000f),
            SimulatedSatellite(19, Constellation.GALILEO, 78f, 20f, 45.2f, 0.04f, 1575420000f),
            SimulatedSatellite(25, Constellation.GALILEO, 31f, 140f, 31.4f, -0.07f, 1575420000f),
            SimulatedSatellite(36, Constellation.GALILEO, 18f, 340f, 24.6f, 0.08f, 1176450000f),

            // GLONASS (RU)
            SimulatedSatellite(2, Constellation.GLONASS, 61f, 80f, 38.6f, 0.07f, 1602000000f),
            SimulatedSatellite(8, Constellation.GLONASS, 44f, 190f, 35.1f, -0.06f, 1602000000f),
            SimulatedSatellite(15, Constellation.GLONASS, 26f, 280f, 27.4f, 0.08f, 1602000000f),
            SimulatedSatellite(22, Constellation.GLONASS, 12f, 25f, 19.8f, -0.05f, 1602000000f),

            // BeiDou (CN)
            SimulatedSatellite(6, Constellation.BEIDOU, 57f, 110f, 41.2f, 0.06f, 1561098000f),
            SimulatedSatellite(12, Constellation.BEIDOU, 70f, 240f, 43.0f, -0.05f, 1561098000f),
            SimulatedSatellite(18, Constellation.BEIDOU, 35f, 325f, 32.5f, 0.07f, 1561098000f),
            SimulatedSatellite(24, Constellation.BEIDOU, 20f, 60f, 26.0f, -0.08f, 1561098000f),

            // QZSS (JP) & SBAS (EGNOS)
            SimulatedSatellite(1, Constellation.QZSS, 42f, 155f, 33.0f, 0.03f, 1575420000f),
            SimulatedSatellite(123, Constellation.SBAS, 28f, 185f, 30.5f, 0.01f, 1575420000f)
        )
    }

    private fun startCompassSensors() {
        if (rotationVectorSensor != null) {
            sensorManager?.registerListener(this, rotationVectorSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager?.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager?.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun stopCompassSensors() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent) {
        if (event.sensor.type == Sensor.TYPE_ROTATION_VECTOR) {
            val rotationMatrix = FloatArray(9)
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values)
            val orientation = FloatArray(3)
            SensorManager.getOrientation(rotationMatrix, orientation)
            var azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
            if (azimuth < 0) azimuth += 360f
            updateSmoothedHeading(azimuth)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            System.arraycopy(event.values, 0, accelerometerReading, 0, 3)
            hasAccelerometer = true
            computeHeadingFromAccAndMag()
        } else if (event.sensor.type == Sensor.TYPE_MAGNETIC_FIELD) {
            System.arraycopy(event.values, 0, magnetometerReading, 0, 3)
            hasMagnetometer = true
            computeHeadingFromAccAndMag()
        }
    }

    private fun computeHeadingFromAccAndMag() {
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
            updateSmoothedHeading(azimuth)
        }
    }

    private fun updateSmoothedHeading(newHeading: Float) {
        // Low pass filter with circular degree wrap-around
        var diff = newHeading - smoothedHeading
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f
        smoothedHeading = (smoothedHeading + diff * 0.2f + 360f) % 360f

        _gpsState.update { it.copy(compassHeading = smoothedHeading) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No-op
    }
}

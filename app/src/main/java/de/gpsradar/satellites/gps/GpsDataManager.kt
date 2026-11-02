package de.gpsradar.satellites.gps

import android.annotation.SuppressLint
import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssNavigationMessage
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationManagerCompat
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.CompassHealth
import de.gpsradar.satellites.model.CompassSpin
import de.gpsradar.satellites.model.CompassSpinState
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.FixType
import de.gpsradar.satellites.model.GnssNavMessage
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.SatelliteInfo
import de.gpsradar.satellites.model.SpinVerdict
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import org.json.JSONArray
import org.json.JSONObject
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
import java.util.Locale
import java.util.Random

private const val TAG = "GpsDataManager"
private const val NAV_REGISTER_ATTEMPTS = 5
private const val NAV_REGISTER_RETRY_MS = 2000L

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

class GpsDataManager(private val context: Context) : SensorEventListener {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    /** true, solange die Navigationsnachrichten registriert sind. */
    private var navMessagesRegistered = false
    private var navRegisterAttempts = 0
    private val navRegisterRetry = Runnable { registerNavigationMessagesIfPossible() }

    private val _gpsState = MutableStateFlow(GpsLocationState())
    val gpsState: StateFlow<GpsLocationState> = _gpsState.asStateFlow()

    private val _navigationMessages = MutableStateFlow<List<GnssNavMessage>>(emptyList())
    val navigationMessages: StateFlow<List<GnssNavMessage>> = _navigationMessages.asStateFlow()

    private val _supportsNavigationMessages = MutableStateFlow(false)
    val supportsNavigationMessages: StateFlow<Boolean> = _supportsNavigationMessages.asStateFlow()

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

    // Pfeil-Referenz: GPS-Kurs bei Bewegung, Kompass im Stand (mit Hysterese).
    private var courseModeActive = false
    private var lastBearingDegrees: Float? = null

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

    // Simulated satellite drift
    private var demoSatellites: List<SimulatedSatellite> = emptyList()
    private val random = Random()
    private var startTimeMillis: Long = System.currentTimeMillis()

    init {
        rotationVectorSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)
        // Magnetometer (und als Ersatzpfad der Beschleunigungssensor) unabhängig
        // vom Rotationsvektor auflösen: das Magnetometer dient auch der
        // Feldbetrag-Überwachung.
        accelerometerSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magneticSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
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

    private val maxStoredNavMessages = 20

    private val navMessageCallback = object : GnssNavigationMessage.Callback() {        override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
            if (isSimulated) return
            _navigationMessages.update { current ->
                (current + navMessageFrom(event)).takeLast(maxStoredNavMessages)
            }
        }

        override fun onStatusChanged(status: Int) {
            // Für die Anzeige nicht relevant.
        }
    }

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            if (isSimulated) return
            // Pfeil-Referenz: bei ausreichender Bewegung übernimmt der GPS-Kurs,
            // im Stand der Kompass (Hysterese gegen Flackern an der Schwelle).
            val speedMps = if (location.hasSpeed()) location.speed else 0f
            if (courseModeActive && speedMps < Navigation.COURSE_SPEED_EXIT_MS) {
                courseModeActive = false
            } else if (!courseModeActive && speedMps >= Navigation.COURSE_SPEED_ENTER_MS) {
                courseModeActive = true
            }
            if (location.hasBearing()) lastBearingDegrees = location.bearing
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
                    headingReferenceDegrees = headingReferenceDegrees(),
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
                // Fehler beim Registrieren abgefangen.
            }

            registerNavigationMessages()

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

            // Rein offline: nur GNSS-Position verwenden.
        }

        // Kein automatischer Simulationsmodus: erfundene Satelliten und eine
        // perfekte Position dürfen nie als echte Messwerte erscheinen. Die
        // Simulation lässt sich nur bewusst über den Schalter einschalten.
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
            unregisterNavigationMessages()
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

    @SuppressLint("MissingPermission")
    private fun registerNavigationMessages() {
        val lm = locationManager
        if (lm == null) {
            _supportsNavigationMessages.value = false
            return
        }
        if (!navMessagesRegistered) {
            navRegisterAttempts = 0
            registerNavigationMessagesIfPossible()
        }

        // Ab Android 10 sagt die Plattform die Fähigkeit direkt; der Rückgabewert
        // der Registrierung ist kurz nach dem Start unzuverlässig.
        _supportsNavigationMessages.value =
            navigationMessagesCapable(lm) ?: navMessagesRegistered
    }

    private fun navigationMessagesCapable(lm: LocationManager): Boolean? =
        // Ab Android 12 für normale Apps abrufbar; davor verlangt der Aufruf
        // LOCATION_HARDWARE und wirft eine SecurityException.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                lm.gnssCapabilities.hasNavigationMessages()
            } catch (e: Exception) {
                Log.d(TAG, "gnssCapabilities nicht lesbar", e)
                null
            }
        } else {
            null
        }

    @SuppressLint("MissingPermission")
    private fun registerNavigationMessagesIfPossible() {
        if (navMessagesRegistered) return
        val lm = locationManager ?: return
        val ok = try {
            lm.registerGnssNavigationMessageCallback(navMessageCallback)
        } catch (e: Exception) {
            Log.w(TAG, "Navigationsnachrichten nicht registriert", e)
            false
        }
        navMessagesRegistered = ok
        if (ok) {
            navRegisterAttempts = 0
        } else if (navRegisterAttempts < NAV_REGISTER_ATTEMPTS) {
            navRegisterAttempts++
            Log.w(TAG, "Navigationsnachrichten noch nicht möglich (Versuch $navRegisterAttempts)")
            mainHandler.postDelayed(navRegisterRetry, NAV_REGISTER_RETRY_MS)
        }
    }

    private fun unregisterNavigationMessages() {
        mainHandler.removeCallbacks(navRegisterRetry)
        navRegisterAttempts = 0
        if (navMessagesRegistered) {
            try {
                locationManager?.unregisterGnssNavigationMessageCallback(navMessageCallback)
            } catch (e: Exception) {
                // ignore
            }
            navMessagesRegistered = false
        }
        _navigationMessages.value = emptyList()
    }

    private fun navMessageFrom(event: GnssNavigationMessage): GnssNavMessage {
        val type = event.type
        val constellation = constellationForNavType(type)
        val raw = event.data ?: ByteArray(0)
        val hex = raw.joinToString(" ") { String.format(Locale.US, "%02X", it.toInt() and 0xFF) }
        return GnssNavMessage(
            svid = event.svid,
            constellation = constellation,
            typeLabel = navTypeLabel(type),
            statusLabel = navStatusLabel(event.status),
            messageId = event.messageId,
            submessageId = event.submessageId,
            dataHex = hex,
            receivedAtMillis = System.currentTimeMillis()
        )
    }

    private fun constellationForNavType(type: Int): Constellation = when (type) {
        GnssNavigationMessage.TYPE_GPS_L1CA,
        GnssNavigationMessage.TYPE_GPS_L2CNAV,
        GnssNavigationMessage.TYPE_GPS_L5CNAV,
        GnssNavigationMessage.TYPE_GPS_CNAV2 -> Constellation.GPS
        GnssNavigationMessage.TYPE_GLO_L1CA -> Constellation.GLONASS
        GnssNavigationMessage.TYPE_BDS_D1,
        GnssNavigationMessage.TYPE_BDS_D2,
        GnssNavigationMessage.TYPE_BDS_CNAV1,
        GnssNavigationMessage.TYPE_BDS_CNAV2 -> Constellation.BEIDOU
        GnssNavigationMessage.TYPE_GAL_I,
        GnssNavigationMessage.TYPE_GAL_F -> Constellation.GALILEO
        GnssNavigationMessage.TYPE_QZS_L1CA -> Constellation.QZSS
        else -> Constellation.UNKNOWN
    }

    private fun navTypeLabel(type: Int): String = when (type) {
        GnssNavigationMessage.TYPE_GPS_L1CA -> "GPS L1 C/A"
        GnssNavigationMessage.TYPE_GPS_L2CNAV -> "GPS L2C"
        GnssNavigationMessage.TYPE_GPS_L5CNAV -> "GPS L5"
        GnssNavigationMessage.TYPE_GPS_CNAV2 -> "GPS CNAV-2"
        GnssNavigationMessage.TYPE_GLO_L1CA -> "GLONASS L1 C/A"
        GnssNavigationMessage.TYPE_BDS_D1 -> "BeiDou D1"
        GnssNavigationMessage.TYPE_BDS_D2 -> "BeiDou D2"
        GnssNavigationMessage.TYPE_BDS_CNAV1 -> "BeiDou CNAV-1"
        GnssNavigationMessage.TYPE_BDS_CNAV2 -> "BeiDou CNAV-2"
        GnssNavigationMessage.TYPE_GAL_I -> "Galileo I/NAV"
        GnssNavigationMessage.TYPE_GAL_F -> "Galileo F/NAV"
        GnssNavigationMessage.TYPE_QZS_L1CA -> "QZSS L1 C/A"
        GnssNavigationMessage.TYPE_IRN_L1 -> "IRNSS L1"
        GnssNavigationMessage.TYPE_IRN_L5 -> "IRNSS L5"
        GnssNavigationMessage.TYPE_IRN_L5CA -> "IRNSS L5 C/A"
        else -> "Typ 0x${Integer.toHexString(type)}"
    }

    private fun navStatusLabel(status: Int): String = when (status) {
        GnssNavigationMessage.STATUS_PARITY_PASSED -> context.getString(R.string.nav_status_parity_ok)
        GnssNavigationMessage.STATUS_PARITY_REBUILT -> context.getString(R.string.nav_status_parity_rebuilt)
        else -> "Status 0x${Integer.toHexString(status)}"
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
            // Magnetometer zusätzlich registrieren – nur für die Feldbetrag-Überwachung.
            sensorManager?.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            sensorManager?.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_UI)
            sensorManager?.registerListener(this, magneticSensor, SensorManager.SENSOR_DELAY_UI)
        }
    }

    private fun stopCompassSensors() {
        sensorManager?.unregisterListener(this)
        // Ein laufender Drehtest endet mit dem Sensorstopp ohne Ergebnis.
        if (spinRunning) {
            spinRunning = false
            mainHandler.removeCallbacks(spinTimeout)
            _spinState.update { it.copy(running = false) }
        }
    }

    /**
     * Startet den Drehtest. Ohne Kreisel gibt es keinen Test – dann meldet der
     * Zustand [CompassSpinState.supported] = false, statt still nichts zu tun.
     */
    fun startCompassSpin() {
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
    fun stopCompassSpin() {
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

            val file = File(context.applicationContext.filesDir, COMPASS_RUNS_FILE)
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
        updateSmoothedHeading(azimuth)
    }

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

    private fun updateSmoothedHeading(newHeading: Float) {
        // Der Sensor liefert zum magnetischen Norden, der GPS-Kurs gilt für
        // wahres Norden. Die Deklination wird deshalb hier einmal addiert –
        // danach rechnet die ganze App in einem Bezugssystem.
        val corrected = (newHeading + declinationDegrees() + 360f) % 360f
        // Low pass filter with circular degree wrap-around
        var diff = corrected - smoothedHeading
        while (diff < -180f) diff += 360f
        while (diff > 180f) diff -= 360f
        smoothedHeading = (smoothedHeading + diff * 0.2f + 360f) % 360f

        _gpsState.update {
            it.copy(
                compassHeading = smoothedHeading,
                headingReferenceDegrees = headingReferenceDegrees()
            )
        }
    }

    /** Ortsmissweisung an der zuletzt bekannten Position (0 ohne Fix). */
    private fun declinationDegrees(): Float {
        val state = _gpsState.value
        return declination.degreesAt(
            state.latitude,
            state.longitude,
            state.altitudeMeters,
            System.currentTimeMillis()
        )
    }

    /** Referenzrichtung für Pfeile: GPS-Kurs bei Bewegung, sonst Kompasskurs. */
    private fun headingReferenceDegrees(): Float {
        return if (courseModeActive && lastBearingDegrees != null) lastBearingDegrees!! else smoothedHeading
    }

    /** Wertet Sensorgenauigkeit und Feldbetrag aus und veröffentlicht den Befund samt Messwert. */
    private fun updateCompassHealth() {
        val fieldMicroTesla = recentFieldMagnitudes
            .takeIf { it.size >= FIELD_SAMPLES_MIN }
            ?.average()
            ?.toFloat()
        val issue = CompassHealth.issue(fieldMicroTesla, compassAccuracyLow)

        val current = _gpsState.value
        val previous = current.compassFieldMicroTesla
        // Feldwert nur übernehmen, wenn er sich sichtbar ändert – sonst flackert
        // die Anzeige mit jedem Sensorereignis.
        val fieldChanged = when {
            fieldMicroTesla == null -> false
            previous == null -> true
            else -> abs(previous - fieldMicroTesla) >= FIELD_DISPLAY_STEP_UT
        }
        if (issue != current.compassIssue || fieldChanged) {
            _gpsState.update {
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

package de.gpsradar.satellites.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.location.GnssStatusCompat
import androidx.core.location.LocationManagerCompat
import de.gpsradar.satellites.model.CompassSpinState
import de.gpsradar.satellites.model.CompassWalkState
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.ExportGeometry
import de.gpsradar.satellites.model.FixType
import de.gpsradar.satellites.model.GnssNavMessage
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.SatelliteInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Notbremse gegen eine Lage, die erkennbar nicht mehr zum Betrieb gehört.
 *
 * Normalerweise wird die Lage über [GpsDataManager.satellitesForExport] beim
 * Beenden der Ortung ungültig – eine Frist braucht es dafür nicht. Diese Grenze
 * fängt nur den unwahrscheinlichen Fall ab, dass der Status jahrelang stumm
 * bleibt, die Messungen aber weiterlaufen.
 */
private const val SAT_STATUS_MAX_AGE_MS = 30 * 60 * 1000L

/**
 * Verbindet die Ortung mit der Oberfläche: Satellitenstatus, Position und
 * Pfeilrichtung landen in [gpsState].
 *
 * Die eigentliche Arbeit steckt in eigenen Bausteinen, damit diese Datei
 * lesbar bleibt:
 * - [CompassSensorHub] – Peilung, Sensorqualität, Drehtest, Missweisung
 * - [DemoScenario] – erfundene Satelliten für den Simulationsmodus
 * - [GnssNavigationMessageReader] – Ephemeriden-Bits für die Anzeige
 * - [RawGnssManager] – Rohmessungen (eigener, unabhängiger Datenstrom)
 *
 * Hier bleibt nur, was diese Teile zusammenbringt: Registrierung beim
 * LocationManager und die Entscheidung, ob der GPS-Kurs oder der Kompass die
 * Pfeilrichtung vorgibt.
 */
class GpsDataManager(private val context: Context) {

    private val locationManager =
        context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(Dispatchers.Main + Job())

    private val _gpsState = MutableStateFlow(GpsLocationState())
    val gpsState: StateFlow<GpsLocationState> = _gpsState.asStateFlow()

    // Pfeil-Referenz: GPS-Kurs bei Bewegung, Kompass im Stand (mit Hysterese).
    private var courseModeActive = false
    private var lastBearingDegrees: Float? = null

    private val compass = CompassSensorHub(
        context = context,
        state = _gpsState,
        referenceDirection = { headingReferenceDegrees() },
        courseDegrees = { currentCourseDegrees() }
    )
    val spinState: StateFlow<CompassSpinState> = compass.spinState
    val compassWalkState: StateFlow<CompassWalkState> = compass.walkState

    private val navMessages = GnssNavigationMessageReader(
        context = context,
        locationManager = locationManager,
        mainHandler = mainHandler,
        isSimulated = { isSimulated }
    )
    val navigationMessages: StateFlow<List<GnssNavMessage>> = navMessages.messages
    val supportsNavigationMessages: StateFlow<Boolean> = navMessages.supports

    /** Vollständige Ephemeriden je Satellit (GPS und GLONASS, für die Navigationsdatei). */
    val navEphemerides: StateFlow<NavEphemerides> = navMessages.ephemerides

    private val demo = DemoScenario(scope = scope, state = _gpsState)

    private var isTracking = false
    private var isSimulated = false

    /**
     * Zuletzt gemeldete Satellitenlage – nur für den Rohdatenexport.
     *
     * Getrennt vom Anzeigezustand, weil ein gemeldeter Stopp dessen Liste leert,
     * die Winkel aber weiter gelten (siehe [ExportGeometry] und [onStopped]).
     */
    private var exportGeometry: List<SatelliteInfo> = emptyList()

    /** Zeitpunkt der zuletzt gemeldeten Lage (elapsedRealtime); 0 = noch keine. */
    private var exportGeometryAt = 0L

    private val gnssStatusCallback = object : GnssStatusCompat.Callback() {
        override fun onStarted() {
            // GNSS engine started
        }

        override fun onStopped() {
            // Für die Anzeige aufräumen – die exportierte Lage bleibt stehen.
            // Am 14.11.2026 auf dem Sportplatz meldete der Empfänger mitten in
            // einer Aufnahme „gestoppt", obwohl Messungen mit unverändertem
            // C/N₀ weiterliefen. Danach kam kein neuer Status mehr, weil sich
            // die Satellitenliste nicht änderte – 109 von 300 Epochen gingen
            // ohne Höhenwinkel raus. Die Altersgrenze in [satellitesForExport]
            // ist die Notbremse gegen wirklich alte Winkel.
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

                val carrierFreq =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && status.hasCarrierFrequencyHz(i)) {
                        status.getCarrierFrequencyHz(i)
                    } else {
                        null
                    }

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
            exportGeometry = satList
            exportGeometryAt = SystemClock.elapsedRealtime()
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
                val fix = if (current.satellitesInFix >= 4 || location.hasAltitude()) {
                    FixType.FIX_3D
                } else {
                    FixType.FIX_2D
                }
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

        compass.start()

        // Die Simulation läuft nicht von selbst weiter: stopTracking() bricht
        // sie beim Wechsel in den Hintergrund ab. Ohne diesen Neustart stünde
        // die Anzeige nach der Rückkehr still, weil echte Werte im
        // Simulationsmodus bewusst verworfen werden.
        if (isSimulated) demo.start()

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

            navMessages.register()

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
        compass.stop()
        demo.stop()

        if (locationManager != null) {
            try {
                LocationManagerCompat.unregisterGnssStatusCallback(locationManager, gnssStatusCallback)
            } catch (e: Exception) {
                // Safe ignore
            }
            navMessages.unregister()
            try {
                locationManager.removeUpdates(locationListener)
            } catch (e: Exception) {
                // Safe ignore
            }
        }
    }

    /**
     * Satellitenlage (Höhenwinkel, Azimut) für den Rohdatenexport.
     *
     * Die Lage stammt aus dem Satellitenstatus, die Messwerte aus einem eigenen
     * Datenstrom. Beide laufen nur, solange der Empfänger arbeitet.
     *
     * **Nicht** über das Alter des letzten Status entscheiden: Der Status kommt
     * nur, wenn sich die Satellitenliste ändert. Steht das Gerät still, meldet
     * sich der Empfänger minutenlang nicht – am 12.11. auf freiem Feld gemessen:
     * nach 128 Sekunden kam kein Status mehr, obwohl die Messungen weiterliefen.
     * Eine Frist von 5 s hat dadurch 172 von 300 Epochen die Lage gekostet, ohne
     * dass die Werte schlechter waren (C/N₀ der betroffenen Zeilen 39,1 gegen
     * 40,4 dBHz – kein Signalproblem). Eine stabile Satellitenliste bedeutet
     * zugleich, dass sich die Winkel kaum ändern.
     *
     * Am 14.11.2026 kam ein zweiter Fall dazu: Der Empfänger meldete mitten in
     * der Aufnahme „gestoppt", während er weiterlief. Deshalb entscheidet die
     * zuletzt gemeldete Lage und die Frist – nicht ein Kennzeichen, das ein
     * solcher Fehlalarm löschen könnte. Geliefert wird [exportGeometry], nicht
     * die Anzeigeliste (die räumt ein Stopp absichtlich auf).
     */
    fun satellitesForExport(maxAgeMillis: Long = SAT_STATUS_MAX_AGE_MS): List<SatelliteInfo> {
        val alter = if (exportGeometryAt == 0L) {
            Long.MAX_VALUE
        } else {
            SystemClock.elapsedRealtime() - exportGeometryAt
        }
        val nutzbar = ExportGeometry.usable(
            isTracking = isTracking,
            isSimulated = isSimulated,
            hasGeometry = exportGeometry.isNotEmpty(),
            ageMillis = alter,
            maxAgeMillis = maxAgeMillis
        )
        return if (nutzbar) exportGeometry else emptyList()
    }

    fun checkGpsEnabled() {
        val enabled = locationManager?.let {
            LocationManagerCompat.isLocationEnabled(it) ||
                it.isProviderEnabled(LocationManager.GPS_PROVIDER)
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
            demo.start()
        } else {
            demo.stop()
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

    fun startCompassSpin() {
        compass.startSpin()
    }

    fun stopCompassSpin() {
        compass.stopSpin()
    }

    fun startCompassWalk() {
        compass.startWalk()
    }

    fun stopCompassWalk() {
        compass.stopWalk()
    }

    /** GPS-Kurs, solange er die Pfeilrichtung vorgibt – sonst null. */
    private fun currentCourseDegrees(): Float? = if (courseModeActive) lastBearingDegrees else null

    /** Referenzrichtung für Pfeile: GPS-Kurs bei Bewegung, sonst Kompasskurs. */
    private fun headingReferenceDegrees(): Float {
        return if (courseModeActive && lastBearingDegrees != null) {
            lastBearingDegrees!!
        } else {
            compass.headingDegrees
        }
    }
}

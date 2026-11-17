package de.gpsradar.satellites.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.gpsradar.satellites.R
import de.gpsradar.satellites.readAppVersion
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import de.gpsradar.satellites.gps.GpsDataManager
import de.gpsradar.satellites.gps.NavEphemerides
import de.gpsradar.satellites.gps.RawGnssManager
import de.gpsradar.satellites.model.AlmanacFile
import de.gpsradar.satellites.model.Cache
import de.gpsradar.satellites.model.BufferStats
import de.gpsradar.satellites.model.CapturePhase
import de.gpsradar.satellites.model.CompassSpinState
import de.gpsradar.satellites.model.CompassWalkState
import de.gpsradar.satellites.model.CsvMeta
import de.gpsradar.satellites.model.CsvProfile
import de.gpsradar.satellites.model.DeviceCapabilities
import de.gpsradar.satellites.model.ArrivalAlert
import de.gpsradar.satellites.model.ArrivalNotice
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.FixType
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.GnssNavMessage
import de.gpsradar.satellites.model.ImportResult
import de.gpsradar.satellites.model.MeasurementCsv
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.RawClockSnapshot
import de.gpsradar.satellites.model.RawMeasurement
import de.gpsradar.satellites.model.ReceiverHealth
import de.gpsradar.satellites.model.RinexNav
import de.gpsradar.satellites.model.SatelliteInfo
import de.gpsradar.satellites.model.SkipReason
import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.TrackEndAlert
import de.gpsradar.satellites.model.TrackPoint
import de.gpsradar.satellites.model.TrackRecorder
import de.gpsradar.satellites.model.TrackStore
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.model.WaypointCodec
import de.gpsradar.satellites.model.groupWaypoints
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SatelliteSort(@StringRes val labelRes: Int) {
    SIGNAL_DESC(R.string.sort_signal),
    IN_FIX_FIRST(R.string.sort_in_fix),
    ELEVATION_DESC(R.string.sort_elevation),
    SVID_ASC(R.string.sort_svid)
}

private const val TRACK_ADVANCE_THRESHOLD_M = 25.0
private const val TRACK_SNAP_THRESHOLD_M = 100.0

/**
 * Wie weit der Fortschritt in einem Positionssatz aufholen darf.
 *
 * Mit einem einzigen Schritt (ein Punkt je Fix) käme man nur bis zur
 * Geschwindigkeit, die dem Punktabstand pro Sekunde entspricht – bei einer
 * Sekundenaufzeichnung rund 36 km/h. Am 03.11. fiel der Zeiger bei 47 km/h
 * zurück und blieb dort hängen. 400 m genügen, um selbst einen großen
 * Rückstand in wenigen Sekunden aufzuholen, und sind zu kurz, um eine Schleife
 * versehentlich zu überspringen.
 */
private const val TRACK_ADVANCE_MAX_JUMP_M = 400.0

/** Eigener Log-Tag: Der Ankunftsalarm ist sonst nicht nachvollziehbar. */
private const val TAG_ARRIVAL = "ArrivalAlert"

/** So lange bleibt die Meldung „Angekommen – Umkreis x m" auf dem Schirm. */
private const val ARRIVAL_NOTICE_MS = 6_000L

/** So lange bleibt eine Rohdaten-Aufzeichnung laufen; danach endet sie selbst und bietet den Export an. */
private const val RAW_CAPTURE_DURATION_MS = 5 * 60 * 1000L

/**
 * So lange fordert die App beim Neustart des Rohdatenkanals keine Position an.
 *
 * Der Empfänger lässt erst los, wenn eine Weile niemand etwas von ihm will –
 * genau das bewirkt auch „App schließen und warten". Eine Minute deckt sich mit
 * dem, was die App dem Nutzer ohnehin rät; kürzere Versuche (bis 25 s) haben am
 * S20 FE nicht zuverlässig gewirkt.
 */
private const val RAW_RESTART_WAIT_S = 60

/**
 * Ab dieser Stille gilt der Kanal als tot – aber **nur**, wenn seit dem Start
 * noch kein einziger Messsatz kam.
 *
 * Der erste Messsatz kommt 2–10 s nach der Registrierung. Bleibt er aus, hat
 * der Messkanal den Empfänger nicht erreicht (bekannter Gerätefehler), und
 * Warten hilft nicht. Kommt dagegen mitten im Betrieb nichts mehr, kann eine
 * kurze Funkloch-Phase dahinterstecken – dort bleibt der Wächter zuständig, der
 * deutlich geduldiger ist.
 */
private const val RAW_SILENT_LIMIT_MS = 20_000L

/** Abstand der Prüfung, solange der Mess-Tab offen ist. */
private const val RAW_SILENT_CHECK_MS = 5_000L

/**
 * Abstand der Prüfung, ob die Ortung hängt (Messungen laufen, Position nicht).
 * Fünf Sekunden genügen: Die Entscheidung selbst hängt an einer Zwei-Minuten-
 * Frist, nicht an der Taktung der Prüfung.
 */
private const val FIX_WATCH_INTERVAL_MS = 5_000L

/** Eigener Log-Tag: Der Neustart des Rohdatenkanals ist sonst nicht nachvollziehbar. */
private const val TAG_RAW = "RawChannel"

class GpsViewModel(application: Application) : AndroidViewModel(application) {

    private val gpsDataManager = GpsDataManager(application.applicationContext)
    // Die Messungen selbst enthalten keine Lage am Himmel – die kommt aus dem
    // Satellitenstatus und nur, solange er frisch ist.
    private val rawGnssManager = RawGnssManager(application.applicationContext) {
        gpsDataManager.satellitesForExport()
    }

    /** Kurzform für übersetzte Meldungen. */
    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    val rawGpsState: StateFlow<GpsLocationState> = gpsDataManager.gpsState
    val compassSpinState: StateFlow<CompassSpinState> = gpsDataManager.spinState
    val compassWalkState: StateFlow<CompassWalkState> = gpsDataManager.compassWalkState
    val supportsNavigationMessages: StateFlow<Boolean> = gpsDataManager.supportsNavigationMessages
    val navEphemerides: StateFlow<NavEphemerides> = gpsDataManager.navEphemerides
    val supportsRawMeasurements: StateFlow<Boolean> = rawGnssManager.supportsRawMeasurements
    /** Was dieses Gerät kann – Grundlage dafür, nichts Unmögliches anzubieten. */
    val deviceCapabilities: StateFlow<DeviceCapabilities> = rawGnssManager.capabilities
    val rawMeasurements: StateFlow<List<RawMeasurement>> = rawGnssManager.measurements
    val rawClock: StateFlow<RawClockSnapshot?> = rawGnssManager.clock
    val rawCapturePhase: StateFlow<CapturePhase> = rawGnssManager.capturePhase
    val rawBufferStats: StateFlow<BufferStats> = rawGnssManager.bufferStats
    val rawStreamStalled: StateFlow<Boolean> = rawGnssManager.streamStalled

    /** Ende der laufenden Aufzeichnung (Gerätezeit); 0 = keine Aufzeichnung. */
    val rawCaptureEndsAtMillis = MutableStateFlow(0L)
    private var rawCaptureJob: Job? = null

    /** Restzeit eines laufenden Kanal-Neustarts in Sekunden; 0 = er läuft nicht. */
    val rawRestartCountdown = MutableStateFlow(0)
    private var rawRestartJob: Job? = null

    /**
     * Ortung hängt: Es kommen Messungen, aber keine Position (Gerätefehler,
     * siehe [ReceiverHealth]). Der Hinweis erscheint in allen Reitern; neu
     * angefahren wird von selbst nur, wenn niemand navigiert.
     */
    val fixStalled = MutableStateFlow(false)
    private var fixWatchJob: Job? = null
    /** In diesem Ausfall wurde bereits neu angefahren; wird mit dem Fix zurückgesetzt. */
    private var autoFixRestartUsed = false
    /** Der Mess-Tab ist sichtbar – nur dort wird von selbst neu angefahren. */
    private var measurementTabVisible = false
    /** Läuft, solange der Mess-Tab offen ist und prüft, ob der Kanal noch liefert. */
    private var autoCheckJob: Job? = null
    /** Ein automatischer Neustart wurde bereits versucht; wird zurückgesetzt, sobald Daten fließen. */
    private var autoRestartUsed = false

    val selectedFilter = MutableStateFlow<Constellation?>(null)
    val selectedSort = MutableStateFlow(SatelliteSort.SIGNAL_DESC)
    val selectedSatellite = MutableStateFlow<SatelliteInfo?>(null)

    val selectedSatelliteNavMessages: StateFlow<List<GnssNavMessage>> = combine(
        gpsDataManager.navigationMessages,
        selectedSatellite
    ) { messages, satellite ->
        val s = satellite ?: return@combine emptyList()
        messages.filter { it.svid == s.svid && it.constellation == s.constellation }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val prefs = application.getSharedPreferences("gps_waypoints", Context.MODE_PRIVATE)

    val waypoints = MutableStateFlow<List<Waypoint>>(loadWaypoints())
    val activeWaypointId = MutableStateFlow<Long?>(
        prefs.getLong("active_waypoint_id", -1L).takeIf { it >= 0L }
    )

    init {
        // Ein aus den Einstellungen wiederhergestelltes Ziel muss den Alarm
        // wieder scharf stellen. Sonst zeigt die App nach einem Neustart ein
        // aktives Ziel an, dessen Ankunftsalarm nie bewaffnet wurde – er
        // schweigt dann für immer, ohne dass man es merkt. Am 03.11. genau so
        // passiert: Nach mehreren Neuinstallationen kam das Ziel aus den
        // Einstellungen, `setActiveWaypoint()` wurde nie aufgerufen.
        activeWaypointId.value?.let { startArrivalMonitoring(it) }

        // Rohdatenkanal überwachen: Kommen Messungen an, ist der Kanal gesund –
        // ein späterer Aussetzer darf dann wieder von selbst angefahren werden.
        // Meldet der Wächter einen Aussetzer, während der Mess-Tab offen ist,
        // wird sofort neu angefahren.
        viewModelScope.launch {
            rawMeasurements.collect { list ->
                if (list.isNotEmpty()) autoRestartUsed = false
            }
        }
        viewModelScope.launch {
            rawStreamStalled.collect { stalled ->
                if (stalled) autoRestartRawIfSilent()
            }
        }
    }

    /**
     * Tracks liegen als Einzeldateien in `files/tracks/`, der Index steht in
     * den Einstellungen. Die alte Sammel-Ablage (Schlüssel `tracks`) wird beim
     * ersten Start übernommen und danach entfernt.
     */
    private val trackStore = TrackStore(
        dir = File(application.filesDir, "tracks"),
        readIndex = { prefs.getString("track_index", null) },
        writeIndex = { json -> prefs.edit().putString("track_index", json).apply() }
    )

    val tracks = MutableStateFlow(loadInitialTracks())
    val activeTrackId = MutableStateFlow<Long?>(null)
    val activeTrackProgress = MutableStateFlow<Int>(0)

    /**
     * Welche Abteilung im Nav-Tab offen ist (`null` = alle zu). Wird gemerkt,
     * damit beim nächsten Öffnen derselbe Bereich wieder aufgeklappt ist.
     */
    val navSection = MutableStateFlow(prefs.getString("nav_section", null))

    fun setNavSection(section: String?) {
        navSection.value = section
        prefs.edit().putString("nav_section", section).apply()
    }

    // ---- Hint-Anzeige (Format je Hint, nur in-memory) ----
    val hintR13ByKey = MutableStateFlow<Map<String, Boolean>>(emptyMap())

    /** Wechselt die Anzeige eines Hints zwischen Klartext und ROT13 (Key = GC-Code bzw. Name). */
    fun toggleHintFormat(key: String?) {
        if (key.isNullOrBlank()) return
        val current = hintR13ByKey.value[key] ?: false // Default: Klartext
        hintR13ByKey.value = hintR13ByKey.value + (key to !current)
    }

    // Abgeleitete Sichten: Caches (gruppiert über groundspeak:cache id) und freie Wegpunkte.
    val caches: StateFlow<List<Cache>> = waypoints
        .map { list -> groupWaypoints(list).caches }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val freeWaypoints: StateFlow<List<Waypoint>> = waypoints
        .map { list -> groupWaypoints(list).freeWaypoints }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /**
     * Die Aufzeichnung selbst liegt in [TrackRecorder]; das ViewModel reicht nur
     * durch und speichert am Ende den fertigen Track.
     */
    private val trackRecorder = TrackRecorder(viewModelScope)
    val isRecording: StateFlow<Boolean> = trackRecorder.isRecording
    val isRecordingPaused: StateFlow<Boolean> = trackRecorder.isPaused
    val recordingPoints: StateFlow<List<TrackPoint>> = trackRecorder.points
    private var trackFollowJob: Job? = null
    private var arrivalJob: Job? = null
    private var arrivalAlerted = false

    /** Messungen in Folge unter der Schwelle; ein Ausrutscher setzt zurück. */
    private var arrivalHits = 0

    /** Zustand des Alarms am Track-Ende; getrennt vom Zielpunkt-Alarm. */
    private var trackEndArmed = false
    private var trackEndAlerted = false
    private var trackEndHits = 0

    /** Kurzmeldung nach dem Ankunftsalarm; null = nichts anzeigen. */
    val arrivalNotice = MutableStateFlow<ArrivalNotice?>(null)
    private var arrivalNoticeJob: Job? = null

    // Filtered and sorted satellite list
    val displayedSatellites: StateFlow<List<SatelliteInfo>> = combine(
        rawGpsState,
        selectedFilter,
        selectedSort
    ) { state, filter, sort ->
        var list = state.satellites
        if (filter != null) {
            list = list.filter { it.constellation == filter }
        }
        when (sort) {
            SatelliteSort.SIGNAL_DESC -> list.sortedByDescending { it.cn0DbHz }
            SatelliteSort.IN_FIX_FIRST -> list.sortedWith(
                compareByDescending<SatelliteInfo> { it.usedInFix }
                    .thenByDescending { it.cn0DbHz }
            )
            SatelliteSort.ELEVATION_DESC -> list.sortedByDescending { it.elevationDegrees }
            SatelliteSort.SVID_ASC -> list.sortedWith(
                compareBy<SatelliteInfo> { it.constellation.typeId }
                    .thenBy { it.svid }
            )
        }
    }.stateIn(
        scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Default),
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun startTracking(hasPermission: Boolean) {
        gpsDataManager.startTracking(hasPermission)
        rawGnssManager.start(hasPermission)
        startFixWatch()
    }

    fun stopTracking() {
        cancelRawRestartJob()
        // Die Beobachtung des Mess-Tabs gehoert zur sichtbaren App – im
        // Hintergrund darf kein Neustart anlaufen.
        autoCheckJob?.cancel()
        autoCheckJob = null
        stopFixWatch()
        gpsDataManager.stopTracking()
        rawGnssManager.stop()
    }

    /**
     * Beobachtet, ob die Ortung hängt: keine Position, aber laufende Messungen.
     *
     * Läuft nur, solange die App im Vordergrund ist (startTracking/stopTracking).
     * Ohne aktives Ziel oder Track wird nach zwei Minuten von selbst neu
     * angefahren; beim Navigieren bleibt es beim Hinweis, denn der Neustart
     * kostet eine Minute Position ([ReceiverHealth.mayRestartAutomatically]).
     */
    private fun startFixWatch() {
        if (fixWatchJob != null) return
        fixWatchJob = viewModelScope.launch {
            var ohneFixSeit = 0L
            var gemeldet = false
            while (true) {
                delay(FIX_WATCH_INTERVAL_MS)
                val stellung = rawGpsState.value
                val empfaengt = stellung.hasPermission && stellung.isGpsEnabled &&
                    !stellung.isDemoMode
                if (!empfaengt || stellung.fixType != FixType.NO_FIX) {
                    // Fix da (oder Ortung gar nicht gewollt): alles zurücksetzen.
                    ohneFixSeit = 0L
                    gemeldet = false
                    fixStalled.value = false
                    autoFixRestartUsed = false
                    continue
                }
                if (ohneFixSeit == 0L) ohneFixSeit = System.currentTimeMillis()
                val dauer = System.currentTimeMillis() - ohneFixSeit
                val haengt = supportsRawMeasurements.value &&
                    ReceiverHealth.fixStalled(dauer, rawGnssManager.silentMillis())
                if (haengt && !gemeldet) {
                    gemeldet = true
                    Log.i(
                        TAG_RAW,
                        "Ortung hängt: seit ${dauer / 1000}s kein Fix, Messungen laufen"
                    )
                }
                fixStalled.value = haengt
                if (!haengt) continue

                val navigiert = activeWaypointId.value != null || activeTrackId.value != null
                if (!ReceiverHealth.mayRestartAutomatically(navigiert, autoFixRestartUsed)) continue
                autoFixRestartUsed = true
                Log.i(TAG_RAW, "Kein Fix seit ${dauer / 1000}s trotz Messungen – Neustart")
                restartRawChannel()
            }
        }
    }

    private fun stopFixWatch() {
        fixWatchJob?.cancel()
        fixWatchJob = null
        fixStalled.value = false
    }

    /**
     * Fährt den Rohdatenkanal neu an.
     *
     * Bekannter Gerätefehler: Registriert sich die App, während der Empfänger
     * noch läuft, bleibt der Messkanal stumm, obwohl die Position weiterläuft.
     * Der Empfänger muss dafür einmal ganz loslassen – die App fordert deshalb
     * eine Minute lang nichts an und meldet sich danach neu an. Preis: so lange
     * steht auch die Position still, deshalb nur auf Anforderung oder im Mess-Tab.
     */
    fun restartRawChannel() {
        if (rawRestartJob != null) return
        if (!supportsRawMeasurements.value) return
        val permission = rawGpsState.value.hasPermission
        if (!permission) return
        Log.i(TAG_RAW, "Kanal wird neu angefahren (${RAW_RESTART_WAIT_S}s ohne Anforderung)")
        rawRestartJob = viewModelScope.launch {
            try {
                // Nicht über stopTracking(): das würde diesen Auftrag selbst abbrechen.
                gpsDataManager.stopTracking()
                rawGnssManager.stop()
                for (second in RAW_RESTART_WAIT_S downTo 1) {
                    rawRestartCountdown.value = second
                    delay(1_000L)
                }
                startTracking(permission)
                Log.i(TAG_RAW, "Kanal neu angefordert")
            } finally {
                rawRestartCountdown.value = 0
                rawRestartJob = null
            }
        }
    }

    /** Neustart abbrechen: Die Position kommt sofort zurück, die Rohdaten bleiben stumm. */
    fun cancelRawRestart() {
        if (rawRestartJob == null) return
        Log.i(TAG_RAW, "Neustart abgebrochen")
        cancelRawRestartJob()
        startTracking(rawGpsState.value.hasPermission)
    }

    private fun cancelRawRestartJob() {
        rawRestartJob?.let { job ->
            rawRestartJob = null
            job.cancel()
        }
        rawRestartCountdown.value = 0
    }

    /** Der Mess-Tab wird angezeigt (oder verlassen). */
    fun setMeasurementTabVisible(visible: Boolean) {
        measurementTabVisible = visible
        autoCheckJob?.cancel()
        autoCheckJob = null
        if (visible) {
            autoRestartRawIfSilent()
            if (rawRestartJob != null) return
            // Weiter beobachten: Der Kanal kann auch erst später stumm werden,
            // und der Wächter in RawGnssManager meldet erst nach seinen
            // Wiederholungen – zu spät für jemanden, der davor sitzt.
            autoCheckJob = viewModelScope.launch {
                while (true) {
                    delay(RAW_SILENT_CHECK_MS)
                    autoRestartRawIfSilent()
                    if (rawRestartJob != null) break
                }
            }
        } else if (rawRestartJob != null) {
            // Die Wartezeit ist nur hier vertretbar – wer die Seite verlässt,
            // will die Position offenbar zurück (Abbrechen-Knopf ist dann weg).
            Log.i(TAG_RAW, "Mess-Tab verlassen – Neustart abgebrochen")
            cancelRawRestart()
        }
    }

    /**
     * Fährt den Kanal von selbst an – aber nur im Mess-Tab: dort kostet die
     * Minute ohne Position nichts, während sie beim Navigieren stören würde.
     */
    private fun autoRestartRawIfSilent() {
        if (!measurementTabVisible) return
        if (rawRestartJob != null || autoRestartUsed) return
        if (!supportsRawMeasurements.value) return
        // Schneller Fall nur, wenn noch nie etwas kam: dann liegt der bekannte
        // Gerätefehler vor und Warten bringt nichts. Nach einem Aussetzer im
        // Betrieb entscheidet der geduldige Wächter.
        val neverDelivered = !rawGnssManager.hasDelivered()
        val silent = rawStreamStalled.value ||
            (neverDelivered && rawGnssManager.silentMillis() >= RAW_SILENT_LIMIT_MS)
        if (!silent) return
        autoRestartUsed = true
        Log.i(TAG_RAW, "Kanal still – automatischer Neustart")
        restartRawChannel()
    }

    fun onPermissionResult(granted: Boolean) {
        gpsDataManager.startTracking(granted)
        rawGnssManager.start(granted)
    }

    fun toggleOrientationMode() {
        gpsDataManager.toggleOrientationMode()
    }

    /** Startet den Kompass-Drehtest – reine Diagnose, keine Korrektur. */
    fun startCompassSpin() {
        gpsDataManager.startCompassSpin()
    }

    fun stopCompassSpin() {
        gpsDataManager.stopCompassSpin()
    }

    /** Startet die Aufzeichnung für den Geh-Test (Vergleich mit dem GPS-Kurs). */
    fun startCompassWalk() {
        gpsDataManager.startCompassWalk()
    }

    fun stopCompassWalk() {
        gpsDataManager.stopCompassWalk()
    }

    fun toggleDemoMode() {
        gpsDataManager.toggleDemoMode()
    }

    fun setFilter(constellation: Constellation?) {
        selectedFilter.value = constellation
    }

    fun setSort(sort: SatelliteSort) {
        selectedSort.value = sort
    }

    fun selectSatellite(satellite: SatelliteInfo?) {
        selectedSatellite.value = satellite
    }

    fun addWaypointAtCurrentPosition(name: String, code: String? = null) {
        val state = rawGpsState.value
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        addWaypoint(name, code, lat, lon)
    }

    fun addWaypoint(name: String, code: String?, latitude: Double, longitude: Double) {
        val wp = Waypoint(
            id = System.currentTimeMillis(),
            name = name.ifBlank { str(R.string.waypoint_default_name) },
            latitude = latitude,
            longitude = longitude,
            code = code?.trim()?.ifBlank { null }
        )
        waypoints.value = waypoints.value + wp
        persistWaypoints()
        setActiveWaypoint(wp.id)
    }

    fun deleteWaypoint(id: Long) {
        waypoints.value = waypoints.value.filterNot { it.id == id }
        if (activeWaypointId.value == id) {
            activeWaypointId.value = null
            cancelArrivalMonitoring()
            prefs.edit().remove("active_waypoint_id").apply()
        }
        persistWaypoints()
    }

    fun setActiveWaypoint(id: Long?) {
        clearArrivalNotice()
        activeWaypointId.value = id
        if (id != null) {
            activeTrackId.value = null
            cancelTrackFollowing()
            startArrivalMonitoring(id)
        } else {
            cancelArrivalMonitoring()
        }
        if (id == null) {
            prefs.edit().remove("active_waypoint_id").apply()
        } else {
            prefs.edit().putLong("active_waypoint_id", id).apply()
        }
    }

    fun setActiveTrack(id: Long?) {
        cancelTrackFollowing()
        resetTrackEndAlert()
        clearArrivalNotice()
        activeTrackId.value = id
        if (id != null) {
            activeWaypointId.value = null
            cancelArrivalMonitoring()
            prefs.edit().remove("active_waypoint_id").apply()

            // Startpunkt festlegen: nah am Track -> nächstgelegener Punkt, sonst Anfang.
            val track = tracks.value.find { it.id == id }
            activeTrackProgress.value = track?.let { snapStartIndex(it) } ?: 0
            startTrackFollowing(id)
        } else {
            activeTrackProgress.value = 0
        }
    }

    /**
     * Startindex in Verfolgungsrichtung: nächstgelegener Punkt, falls die
     * aktuelle Position nah genug an der Linie liegt, sonst Anfang (0).
     */
    private fun snapStartIndex(track: Track): Int {
        val pts = track.orderedPoints
        if (pts.isEmpty()) return 0
        val st = rawGpsState.value
        val lat = st.latitude
        val lon = st.longitude
        if (lat == null || lon == null) return 0
        val nearest = track.nearestIndex(lat, lon)
        val d = Navigation.distanceMeters(lat, lon, pts[nearest].latitude, pts[nearest].longitude)
        return if (d < TRACK_SNAP_THRESHOLD_M) nearest else 0
    }

    private fun startTrackFollowing(trackId: Long) {
        tracks.value.find { it.id == trackId }?.let { t ->
            Log.i(
                TAG_ARRIVAL,
                "Folgen: ${t.name}, ${t.orderedPoints.size} Punkte, " +
                    "${formatMeters(t.lengthMeters)} m, Start bei Punkt ${activeTrackProgress.value + 1} " +
                    "(${if (t.reversed) "Richtung Anfang" else "Richtung Ende"})"
            )
        }
        trackFollowJob = viewModelScope.launch {
            rawGpsState.collect { st ->
                if (activeTrackId.value != trackId) return@collect
                val track = tracks.value.find { it.id == trackId } ?: return@collect
                val lat = st.latitude ?: return@collect
                val lon = st.longitude ?: return@collect
                val pts = track.orderedPoints
                if (pts.size < 2) return@collect
                val idx = activeTrackProgress.value.coerceIn(0, pts.lastIndex)
                val next = track.advanceIndex(
                    fromIndex = idx,
                    latitude = lat,
                    longitude = lon,
                    thresholdMeters = TRACK_ADVANCE_THRESHOLD_M,
                    maxJumpMeters = TRACK_ADVANCE_MAX_JUMP_M
                )
                if (next != idx) {
                    activeTrackProgress.value = next
                }
                val target = pts[next]
                val d = Navigation.distanceMeters(lat, lon, target.latitude, target.longitude)
                checkTrackEnd(track, next, d, st.accuracyMeters)
            }
        }
    }

    /**
     * Meldet das Ende eines verfolgten Tracks hörbar – in beiden Richtungen,
     * denn das Ende ist immer der letzte Punkt der aktuellen Richtung. Schwelle
     * und Wortlaut entsprechen der Anzeige „Ziel erreicht".
     */
    private fun checkTrackEnd(track: Track, idx: Int, metersToTarget: Double, accuracy: Float?) {
        val remaining = track.remainingMeters(idx) + metersToTarget
        val wasArmed = trackEndArmed
        val result = TrackEndAlert.update(
            remainingMeters = remaining,
            accuracyMeters = accuracy,
            armed = trackEndArmed,
            alerted = trackEndAlerted,
            hits = trackEndHits
        )
        trackEndArmed = result.armed
        trackEndAlerted = result.alerted
        trackEndHits = result.hits
        if (!wasArmed && result.armed) {
            // Beleg dafür, dass der Alarm überhaupt bewaffnet war.
            Log.i(
                TAG_ARRIVAL,
                "Track-Ende scharf: ${track.name}, Rest ${formatMeters(remaining)} m " +
                    "(Schwelle ${formatMeters(TrackEndAlert.radiusMeters(accuracy))} m)"
            )
        }
        if (!result.trigger) return
        val radius = TrackEndAlert.radiusMeters(accuracy)
        Log.i(
            TAG_ARRIVAL,
            "Track-Ende erreicht: ${track.name}, Rest ${formatMeters(remaining)} m " +
                "(Schwelle ${formatMeters(radius)} m, " +
                "Genauigkeit ±${formatMeters(accuracy?.toDouble())} m)"
        )
        vibrateArrival()
        showArrivalNotice(radius)
    }

    private fun resetTrackEndAlert() {
        trackEndArmed = false
        trackEndAlerted = false
        trackEndHits = 0
    }

    /** Zeigt die Meldung einige Sekunden an; ein neuer Alarm verlängert sie. */
    private fun showArrivalNotice(radiusMeters: Double) {
        arrivalNoticeJob?.cancel()
        arrivalNotice.value = ArrivalNotice(radiusMeters, System.currentTimeMillis())
        arrivalNoticeJob = viewModelScope.launch {
            delay(ARRIVAL_NOTICE_MS)
            arrivalNotice.value = null
        }
    }

    private fun clearArrivalNotice() {
        arrivalNoticeJob?.cancel()
        arrivalNoticeJob = null
        arrivalNotice.value = null
    }

    private fun cancelTrackFollowing() {
        trackFollowJob?.cancel()
        trackFollowJob = null
    }

    private fun startArrivalMonitoring(waypointId: Long) {
        cancelArrivalMonitoring()
        arrivalAlerted = false
        arrivalHits = 0
        arrivalLogStart(waypointId)
        arrivalJob = viewModelScope.launch {
            rawGpsState.collect { st ->
                if (activeWaypointId.value != waypointId) return@collect
                val wp = waypoints.value.find { it.id == waypointId } ?: return@collect
                val lat = st.latitude ?: return@collect
                val lon = st.longitude ?: return@collect
                val d = Navigation.distanceMeters(lat, lon, wp.latitude, wp.longitude)
                // Die Schwelle folgt der gemeldeten Genauigkeit: Im Wald schwankt
                // die Position um 10–20 m, ein fester 5-m-Radius löst dann nie aus
                // (am 03.11. genau so passiert).
                val threshold = ArrivalAlert.thresholdMeters(st.accuracyMeters)
                // Erst zwei Messungen in Folge unter der Schwelle: ein einzelner
                // Glücksfix unter Bäumen würde sonst an falscher Stelle melden.
                arrivalHits = ArrivalAlert.nextHits(d, threshold, arrivalHits)
                if (ArrivalAlert.confirmed(arrivalHits) && !arrivalAlerted) {
                    arrivalAlerted = true
                    Log.i(
                        TAG_ARRIVAL,
                        "Ankunft bei ${wp.name}: ${formatMeters(d)} m " +
                            "(Schwelle ${formatMeters(threshold)} m, " +
                            "Genauigkeit ±${formatMeters(st.accuracyMeters?.toDouble())} m)"
                    )
                    vibrateArrival()
                    showArrivalNotice(threshold)
                } else if (d > ArrivalAlert.resetDistanceMeters(threshold)) {
                    arrivalAlerted = false
                }
            }
        }
    }

    /**
     * Schreibt einmal hin, dass ein Ziel scharf gestellt wurde. Damit lässt sich
     * später sagen, ob der Alarm überhaupt bewaffnet war — beim Feldtest am
     * 03.11. war das nicht mehr feststellbar.
     */
    private fun arrivalLogStart(waypointId: Long) {
        val wp = waypoints.value.find { it.id == waypointId } ?: return
        Log.i(
            TAG_ARRIVAL,
            "scharf fuer ${wp.name} (Schwelle = Genauigkeit + ${ArrivalAlert.MARGIN_M} m)"
        )
    }

    private fun formatMeters(value: Double?): String =
        if (value == null) "—" else String.format(Locale.US, "%.1f", value)

    private fun cancelArrivalMonitoring() {
        arrivalJob?.cancel()
        arrivalJob = null
    }

    private fun vibrateArrival() {
        val vibrator = getApplication<Application>().getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Drei Stöße statt eines kurzen Summers: Eine einzelne 500-ms-
            // Vibration ging am 03.11. im Auto unter (Motor, Halterung).
            val pattern = longArrayOf(0, 250, 150, 250, 150, 400)
            vibrator.vibrate(VibrationEffect.createWaveform(pattern, -1))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(500)
        }
    }

    fun reverseTrack(id: Long) {
        val idx = tracks.value.indexOfFirst { it.id == id }
        if (idx < 0) return
        val track = tracks.value[idx]
        tracks.value = tracks.value.toMutableList().also {
            it[idx] = track.copy(reversed = !track.reversed)
        }
        trackStore.update(tracks.value[idx])
        // Wird der Track gerade verfolgt, nahtlos am nächstgelegenen Punkt in
        // der neuen Richtung weiterfolgen (kein erneutes Antippen nötig).
        if (activeTrackId.value == id) {
            val updated = tracks.value[idx]
            cancelTrackFollowing()
            activeTrackProgress.value = snapStartIndex(updated)
            // Die Richtung dreht das Ziel um: Alarm neu bewaffnen.
            resetTrackEndAlert()
            startTrackFollowing(id)
        }
    }

    fun startRecording() {
        trackRecorder.start(rawGpsState)
    }

    fun pauseRecording() {
        trackRecorder.pause()
    }

    fun resumeRecording() {
        trackRecorder.resume()
    }

    /** Bricht die laufende Aufzeichnung ab, ohne einen Track anzulegen. */
    fun discardRecording() {
        trackRecorder.discard()
    }

    fun defaultRecordingName(): String =
        str(
            R.string.track_default_name,
            SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US).format(Date())
        )

    fun stopRecording(name: String): String {
        val points = trackRecorder.stop()
        if (points.size < 2) {
            return str(R.string.msg_track_too_short)
        }
        val track = Track(
            id = System.currentTimeMillis(),
            name = name.ifBlank { defaultRecordingName() },
            points = points
        )
        tracks.value = tracks.value + trackStore.add(track)
        return str(R.string.msg_track_saved, points.size)
    }

    fun deleteTrack(id: Long) {
        tracks.value = tracks.value.filterNot { it.id == id }
        if (activeTrackId.value == id) {
            activeTrackId.value = null
            cancelTrackFollowing()
            activeTrackProgress.value = 0
        }
        trackStore.delete(id)
    }

    fun updateWaypoint(updated: Waypoint) {
        waypoints.value = waypoints.value.map { wp ->
            if (wp.id == updated.id) updated else wp
        }
        persistWaypoints()
    }

    fun projectWaypoint(name: String, bearingDegrees: Double, distanceMeters: Double): String {
        val base = waypoints.value.find { it.id == activeWaypointId.value }
            ?: return str(R.string.msg_no_active_target)
        val (lat, lon) = Navigation.project(base.latitude, base.longitude, bearingDegrees, distanceMeters)
        val wp = Waypoint(
            id = System.currentTimeMillis(),
            name = name.ifBlank { "${base.name} + ${distanceMeters.toInt()} m" },
            latitude = lat,
            longitude = lon
        )
        waypoints.value = waypoints.value + wp
        persistWaypoints()
        setActiveWaypoint(wp.id)
        return str(R.string.msg_waypoint_projected)
    }

    fun toggleFound(id: Long) {
        val wasActive = activeWaypointId.value == id
        val updated = waypoints.value.map { wp ->
            if (wp.id == id) wp.copy(found = !wp.found) else wp
        }
        waypoints.value = updated
        persistWaypoints()

        // Nach dem Markieren als "gefunden" automatisch das nächste Ziel aktivieren.
        val nowFound = updated.find { it.id == id }?.found == true
        if (wasActive && nowFound) {
            val remaining = updated.filter { !it.found }
            val next = remaining.minByOrNull { wp ->
                val lat = rawGpsState.value.latitude
                val lon = rawGpsState.value.longitude
                if (lat != null && lon != null) {
                    Navigation.distanceMeters(lat, lon, wp.latitude, wp.longitude)
                } else {
                    Double.MAX_VALUE
                }
            }
            setActiveWaypoint(next?.id)
        }
    }

    fun importWaypoints(uri: Uri): ImportResult {
        val bytes = try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }

        if (bytes == null) {
            return ImportResult(emptyList(), emptyList(), emptyList(), listOf(SkipReason.FileUnreadable))
        }

        // Liest GPX/JSON direkt und entpackt ZIP-Archive (z. B. Suchergebnisse
        // von Opencaching), in denen mehrere Dateien stecken.
        val result = WaypointCodec.parseBytes(bytes, str(R.string.waypoint_default_name))

        if (result.waypoints.isNotEmpty()) {
            waypoints.value = waypoints.value + result.waypoints
            persistWaypoints()
        }
        if (result.tracks.isNotEmpty()) {
            // add() vergibt bei doppelten Kennungen eine neue, damit keine
            // bestehende Track-Datei überschrieben wird.
            tracks.value = tracks.value + result.tracks.map { trackStore.add(it) }
        }
        return result
    }

    /**
     * Exportiert pro Objekt eine eigene GPX-Datei in den gewählten Ordner
     * (Cache, Route bzw. freie Position). Gibt die Anzahl geschriebener Dateien zurück.
     */
    fun exportGpx(treeUri: Uri): Int {
        return try {
            val files = WaypointCodec.toGpxFiles(waypoints.value, tracks.value)
            if (files.isEmpty()) return 0
            val app = getApplication<Application>()
            val root = DocumentFile.fromTreeUri(app, treeUri) ?: return 0
            val resolver = app.contentResolver
            var written = 0
            files.forEach { f ->
                val file = root.createFile("application/gpx+xml", f.fileName) ?: return@forEach
                resolver.openOutputStream(file.uri)?.use {
                    it.write(f.content.toByteArray(Charsets.UTF_8))
                }
                written++
            }
            written
        } catch (e: Exception) {
            0
        }
    }

    fun exportJson(uri: Uri): Boolean {
        return try {
            val content = WaypointCodec.toJson(waypoints.value)
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Exportiert den Pufferinhalt als CSV: Kommentarkopf, dann eine Zeile pro
     * Satellit × Band × Epoche. `profile` koppelt Trennzeichen, Dezimalzeichen
     * und BOM.
     */
    /**
     * Schreibt den Pufferinhalt als CSV in den gewählten Ordner.
     *
     * Ordner statt Einzeldatei, damit daneben auch die Navigationsdatei aus
     * demselben Zeitfenster landet (siehe [exportNavRinex]) – Bahndaten gelten
     * nur für die Satelliten, die in ihnen stehen.
     */
    fun exportRawCsv(ordner: Uri, dateiName: String, profile: CsvProfile): Boolean {
        val app = getApplication<Application>()
        val meta = CsvMeta(
            device = "${Build.MANUFACTURER} ${Build.MODEL} · hardware ${Build.HARDWARE} · board ${Build.BOARD}",
            android = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
            app = appVersionName(),
            exportedAtUtc = utcNow(),
            capabilities = rawGnssManager.capabilities.value
        )
        val content = MeasurementCsv.toCsv(rawGnssManager.epochSnapshot(), meta, profile)
        return schreibeInOrdner(ordner, ExportFolder.MESSUNGEN, dateiName, "text/csv", content)
    }

    /**
     * Schreibt die gesammelten GPS- und GLONASS-Ephemeriden als
     * RINEX-Navigationsdatei in denselben Ordner. Liefert false, wenn noch kein
     * vollständiger Satz vorliegt oder das Schreiben scheitert — dann bleibt es
     * bei der CSV.
     *
     * Liegt zusätzlich ein Almanach vor, entsteht daneben `almanach_….txt`:
     * RINEX kennt keinen Almanach, und die Sätze sind für die Auswertung nicht
     * nötig — sie belegen aber, was der Empfänger geliefert hat.
     */
    fun exportNavRinex(ordner: Uri, dateiName: String): Boolean {
        val nav = gpsDataManager.navEphemerides.value
        val brauchbar = nav.gps.any { it.hasConsistentIode } ||
            nav.glo.any { it.hasPlausibleOrbit } ||
            nav.bds.any { it.hasConsistentToc } ||
            nav.gal.any { it.hasPlausibleOrbit }
        if (!brauchbar) return false
        // Eigener MIME-Typ statt "text/plain": Android haengt sonst die Endung
        // des MIME-Typs an (.txt) und aus navigation_….rnx wuerde .rnx.txt.
        val geschrieben = schreibeInOrdner(
            ordner, ExportFolder.NAVIGATION, dateiName, "application/rnx",
            RinexNav.write(nav.gps, nav.glo, nav.bds, nav.gal, nav.corrections)
        )
        if (nav.almanac.isNotEmpty()) {
            val almanachName = dateiName
                .substringAfter("navigation_", dateiName)
                .substringBeforeLast(".")
            schreibeInOrdner(
                ordner, ExportFolder.NAVIGATION, "almanach_$almanachName.txt",
                "text/plain",
                AlmanacFile.write(
                    nav.almanac,
                    listOf(
                        "geraet = ${Build.MANUFACTURER} ${Build.MODEL}",
                        "android = ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                        "exportiert = ${utcNow()}"
                    )
                )
            )
        }
        return geschrieben
    }

    /**
     * Eine Datei in einem vom Nutzer freigegebenen Ordner anlegen und füllen.
     *
     * [unterordner] wird im Ziel angelegt, falls er fehlt — so liegen Messungen
     * und Navigationsdateien getrennt, ohne dass der Nutzer selbst sortieren
     * muss. Ist `unterordner` null, wird direkt ins Ziel geschrieben.
     */
    private fun schreibeInOrdner(
        ordner: Uri,
        unterordner: String?,
        dateiName: String,
        mimeType: String,
        inhalt: String
    ): Boolean {
        return try {
            val app = getApplication<Application>()
            val ziel = DocumentFile.fromTreeUri(app, ordner) ?: return false
            val ablage = if (unterordner == null) {
                ziel
            } else {
                ziel.findFile(unterordner) ?: ziel.createDirectory(unterordner)
            } ?: return false
            val datei = ablage.findFile(dateiName) ?: ablage.createFile(mimeType, dateiName)
            if (datei == null) return false
            app.contentResolver.openOutputStream(datei.uri)?.use {
                it.write(inhalt.toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: Exception) {
            Log.w(TAG_RAW, "Schreiben von $dateiName fehlgeschlagen", e)
            false
        }
    }

    /** Startet eine zeitlich begrenzte Aufzeichnung; sie endet selbst. */
    fun startRawCapture() {
        rawGnssManager.startCapture()
        rawCaptureEndsAtMillis.value = System.currentTimeMillis() + RAW_CAPTURE_DURATION_MS
        rawCaptureJob?.cancel()
        rawCaptureJob = viewModelScope.launch {
            delay(RAW_CAPTURE_DURATION_MS)
            rawGnssManager.finishCapture()
            rawCaptureEndsAtMillis.value = 0L
        }
    }

    /** Laufende Aufzeichnung abbrechen. */
    fun cancelRawCapture() {
        rawCaptureJob?.cancel()
        rawCaptureEndsAtMillis.value = 0L
        rawGnssManager.cancelCapture()
    }

    /** Fertige Aufzeichnung verwerfen (zurück zum Ringpuffer). */
    fun discardRawCapture() {
        rawCaptureJob?.cancel()
        rawCaptureEndsAtMillis.value = 0L
        rawGnssManager.discardCapture()
    }

    private fun appVersionName(): String {
        return try {
            readAppVersion(getApplication()).name
        } catch (e: Exception) {
            "?"
        }
    }

    private fun utcNow(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        fmt.timeZone = TimeZone.getTimeZone("UTC")
        return fmt.format(Date())
    }

    private fun persistWaypoints() {
        val arr = JSONArray()
        waypoints.value.forEach { wp ->
            arr.put(
                JSONObject().apply {
                    put("id", wp.id)
                    put("name", wp.name)
                    put("lat", wp.latitude)
                    put("lon", wp.longitude)
                    wp.code?.let { put("code", it) }
                    wp.description?.let { put("description", it) }
                    wp.hint?.let { put("hint", it) }
                    wp.difficulty?.let { put("difficulty", it) }
                    wp.terrain?.let { put("terrain", it) }
                    wp.cacheType?.let { put("cacheType", it) }
                    wp.groupCacheId?.let { put("groupCacheId", it) }
                    put("archived", wp.archived)
                    put("found", wp.found)
                }
            )
        }
        prefs.edit().putString("waypoints", arr.toString()).apply()
    }

    private fun loadWaypoints(): List<Waypoint> {
        val raw = prefs.getString("waypoints", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Waypoint(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    latitude = o.getDouble("lat"),
                    longitude = o.getDouble("lon"),
                    code = if (o.has("code")) o.getString("code") else null,
                    description = if (o.has("description")) o.getString("description") else null,
                    hint = if (o.has("hint")) o.getString("hint") else null,
                    difficulty = if (o.has("difficulty")) o.optDouble("difficulty") else null,
                    terrain = if (o.has("terrain")) o.optDouble("terrain") else null,
                    cacheType = if (o.has("cacheType")) o.getString("cacheType") else null,
                    groupCacheId = if (o.has("groupCacheId")) o.getString("groupCacheId") else null,
                    archived = o.optBoolean("archived", false),
                    found = o.optBoolean("found", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Lädt die Tracks beim Start und übernimmt dabei einmalig die alte
     * Sammel-Ablage (alle Tracks als ein JSON-String unter `tracks`) in
     * Einzeldateien. Die alte Ablage wird erst gelöscht, nachdem alle Dateien
     * geschrieben sind – schlägt das fehl, bleibt sie als Sicherung liegen.
     */
    private fun loadInitialTracks(): List<Track> {
        trackStore.migrateLegacy(prefs.getString("tracks", null)) {
            prefs.edit().remove("tracks").apply()
        }
        return trackStore.loadAll()
    }

    override fun onCleared() {
        super.onCleared()
        gpsDataManager.stopTracking()
    }
}

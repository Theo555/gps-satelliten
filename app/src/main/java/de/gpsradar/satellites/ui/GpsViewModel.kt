package de.gpsradar.satellites.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.annotation.StringRes
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import de.gpsradar.satellites.R
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import de.gpsradar.satellites.gps.GpsDataManager
import de.gpsradar.satellites.gps.RawGnssManager
import de.gpsradar.satellites.model.Cache
import de.gpsradar.satellites.model.BufferStats
import de.gpsradar.satellites.model.CapturePhase
import de.gpsradar.satellites.model.CsvMeta
import de.gpsradar.satellites.model.CsvProfile
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.GnssNavMessage
import de.gpsradar.satellites.model.ImportResult
import de.gpsradar.satellites.model.MeasurementCsv
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.RawClockSnapshot
import de.gpsradar.satellites.model.RawMeasurement
import de.gpsradar.satellites.model.SatelliteInfo
import de.gpsradar.satellites.model.SkipReason
import de.gpsradar.satellites.model.Track
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
private const val ARRIVAL_THRESHOLD_M = 5.0

/** Dauer einer Rohdaten-Aufzeichnung; danach endet sie selbst und bietet den Export an. */
private const val RAW_CAPTURE_DURATION_MS = 5 * 60 * 1000L

class GpsViewModel(application: Application) : AndroidViewModel(application) {

    private val gpsDataManager = GpsDataManager(application.applicationContext)
    private val rawGnssManager = RawGnssManager(application.applicationContext)

    /** Kurzform für übersetzte Meldungen. */
    private fun str(@StringRes id: Int, vararg args: Any): String =
        getApplication<Application>().getString(id, *args)

    val rawGpsState: StateFlow<GpsLocationState> = gpsDataManager.gpsState
    val supportsNavigationMessages: StateFlow<Boolean> = gpsDataManager.supportsNavigationMessages
    val supportsRawMeasurements: StateFlow<Boolean> = rawGnssManager.supportsRawMeasurements
    val rawMeasurements: StateFlow<List<RawMeasurement>> = rawGnssManager.measurements
    val rawClock: StateFlow<RawClockSnapshot?> = rawGnssManager.clock
    val rawCapturePhase: StateFlow<CapturePhase> = rawGnssManager.capturePhase
    val rawBufferStats: StateFlow<BufferStats> = rawGnssManager.bufferStats
    val rawStreamStalled: StateFlow<Boolean> = rawGnssManager.streamStalled

    /** Ende der laufenden Aufzeichnung (Gerätezeit); 0 = keine Aufzeichnung. */
    val rawCaptureEndsAtMillis = MutableStateFlow(0L)
    private var rawCaptureJob: Job? = null

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
    }

    fun stopTracking() {
        gpsDataManager.stopTracking()
        rawGnssManager.stop()
    }

    fun onPermissionResult(granted: Boolean) {
        gpsDataManager.startTracking(granted)
        rawGnssManager.start(granted)
    }

    fun toggleOrientationMode() {
        gpsDataManager.toggleOrientationMode()
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
        trackFollowJob = viewModelScope.launch {
            rawGpsState.collect { st ->
                if (activeTrackId.value != trackId) return@collect
                val track = tracks.value.find { it.id == trackId } ?: return@collect
                val lat = st.latitude ?: return@collect
                val lon = st.longitude ?: return@collect
                val idx = activeTrackProgress.value
                val pts = track.orderedPoints
                if (idx >= pts.lastIndex) return@collect
                val target = pts[idx]
                val d = Navigation.distanceMeters(lat, lon, target.latitude, target.longitude)
                if (d < TRACK_ADVANCE_THRESHOLD_M) {
                    activeTrackProgress.value = idx + 1
                }
            }
        }
    }

    private fun cancelTrackFollowing() {
        trackFollowJob?.cancel()
        trackFollowJob = null
    }

    private fun startArrivalMonitoring(waypointId: Long) {
        cancelArrivalMonitoring()
        arrivalAlerted = false
        arrivalJob = viewModelScope.launch {
            rawGpsState.collect { st ->
                if (activeWaypointId.value != waypointId) return@collect
                val wp = waypoints.value.find { it.id == waypointId } ?: return@collect
                val lat = st.latitude ?: return@collect
                val lon = st.longitude ?: return@collect
                val d = Navigation.distanceMeters(lat, lon, wp.latitude, wp.longitude)
                if (d < ARRIVAL_THRESHOLD_M && !arrivalAlerted) {
                    arrivalAlerted = true
                    vibrateArrival()
                } else if (d > ARRIVAL_THRESHOLD_M * 2) {
                    arrivalAlerted = false
                }
            }
        }
    }

    private fun cancelArrivalMonitoring() {
        arrivalJob?.cancel()
        arrivalJob = null
    }

    private fun vibrateArrival() {
        val vibrator = getApplication<Application>().getSystemService(Vibrator::class.java) ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(500, VibrationEffect.DEFAULT_AMPLITUDE))
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
        val text = try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        }

        if (text == null) {
            return ImportResult(emptyList(), emptyList(), emptyList(), listOf(SkipReason.FileUnreadable))
        }

        val result = WaypointCodec.parse(text, str(R.string.waypoint_default_name))

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
    fun exportRawCsv(uri: Uri, profile: CsvProfile): Boolean {
        return try {
            val app = getApplication<Application>()
            val meta = CsvMeta(
                device = "${Build.MANUFACTURER} ${Build.MODEL} · hardware ${Build.HARDWARE} · board ${Build.BOARD}",
                android = "${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})",
                app = appVersionName(),
                exportedAtUtc = utcNow()
            )
            val content = MeasurementCsv.toCsv(rawGnssManager.epochSnapshot(), meta, profile)
            app.contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: Exception) {
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

    @Suppress("DEPRECATION")
    private fun appVersionName(): String {
        return try {
            val app = getApplication<Application>()
            app.packageManager.getPackageInfo(app.packageName, 0).versionName ?: "?"
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

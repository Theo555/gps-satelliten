package de.gpsradar.satellites.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import de.gpsradar.satellites.gps.GpsDataManager
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.SatelliteInfo
import de.gpsradar.satellites.model.Track
import de.gpsradar.satellites.model.TrackPoint
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.model.WaypointCodec
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class SatelliteSort(val label: String) {
    SIGNAL_DESC("Signalstärke (C/N₀)"),
    IN_FIX_FIRST("Im Fix zuerst"),
    ELEVATION_DESC("Höhenwinkel"),
    SVID_ASC("Satelliten-ID")
}

private const val MIN_RECORD_DISTANCE_M = 5.0
private const val TRACK_ADVANCE_THRESHOLD_M = 25.0
private const val TRACK_SNAP_THRESHOLD_M = 100.0

class GpsViewModel(application: Application) : AndroidViewModel(application) {

    private val gpsDataManager = GpsDataManager(application.applicationContext)

    val rawGpsState: StateFlow<GpsLocationState> = gpsDataManager.gpsState

    val selectedFilter = MutableStateFlow<Constellation?>(null)
    val selectedSort = MutableStateFlow(SatelliteSort.SIGNAL_DESC)
    val selectedSatellite = MutableStateFlow<SatelliteInfo?>(null)

    private val prefs = application.getSharedPreferences("gps_waypoints", Context.MODE_PRIVATE)

    val waypoints = MutableStateFlow<List<Waypoint>>(loadWaypoints())
    val activeWaypointId = MutableStateFlow<Long?>(
        prefs.getLong("active_waypoint_id", -1L).takeIf { it >= 0L }
    )

    val tracks = MutableStateFlow<List<Track>>(loadTracks())
    val activeTrackId = MutableStateFlow<Long?>(null)
    val activeTrackProgress = MutableStateFlow<Int>(0)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    private val _recordingPoints = MutableStateFlow<List<TrackPoint>>(emptyList())
    val recordingPoints: StateFlow<List<TrackPoint>> = _recordingPoints
    private var recordingJob: Job? = null
    private var trackFollowJob: Job? = null

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
    }

    fun stopTracking() {
        gpsDataManager.stopTracking()
    }

    fun onPermissionResult(granted: Boolean) {
        gpsDataManager.startTracking(granted)
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
            name = name.ifBlank { "Ziel" },
            latitude = latitude,
            longitude = longitude,
            code = code?.trim()?.ifBlank { null }
        )
        waypoints.value = waypoints.value + wp
        activeWaypointId.value = wp.id
        persistWaypoints()
    }

    fun deleteWaypoint(id: Long) {
        waypoints.value = waypoints.value.filterNot { it.id == id }
        if (activeWaypointId.value == id) {
            activeWaypointId.value = null
            prefs.edit().remove("active_waypoint_id").apply()
        }
        persistWaypoints()
    }

    fun setActiveWaypoint(id: Long?) {
        activeWaypointId.value = id
        if (id != null) {
            activeTrackId.value = null
            cancelTrackFollowing()
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
            prefs.edit().remove("active_waypoint_id").apply()

            // Startpunkt festlegen: nah am Track -> nächstgelegener Punkt, sonst Anfang.
            val track = tracks.value.find { it.id == id }
            var start = 0
            if (track != null && track.points.isNotEmpty()) {
                val st = rawGpsState.value
                val lat = st.latitude
                val lon = st.longitude
                if (lat != null && lon != null) {
                    val nearest = track.nearestIndex(lat, lon)
                    val d = Navigation.distanceMeters(
                        lat, lon,
                        track.points[nearest].latitude, track.points[nearest].longitude
                    )
                    if (d < TRACK_SNAP_THRESHOLD_M) start = nearest
                }
            }
            activeTrackProgress.value = start
            startTrackFollowing(id)
        } else {
            activeTrackProgress.value = 0
        }
    }

    private fun startTrackFollowing(trackId: Long) {
        trackFollowJob = viewModelScope.launch {
            rawGpsState.collect { st ->
                if (activeTrackId.value != trackId) return@collect
                val track = tracks.value.find { it.id == trackId } ?: return@collect
                val lat = st.latitude ?: return@collect
                val lon = st.longitude ?: return@collect
                val idx = activeTrackProgress.value
                if (idx >= track.points.lastIndex) return@collect
                val target = track.points[idx]
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

    fun startRecording() {
        if (_isRecording.value) return
        _recordingPoints.value = emptyList()
        _isRecording.value = true
        recordingJob = viewModelScope.launch {
            rawGpsState.collect { state ->
                val lat = state.latitude ?: return@collect
                val lon = state.longitude ?: return@collect
                val last = _recordingPoints.value.lastOrNull()
                val farEnough = last == null ||
                    Navigation.distanceMeters(last.latitude, last.longitude, lat, lon) >= MIN_RECORD_DISTANCE_M
                if (farEnough) {
                    _recordingPoints.value = _recordingPoints.value + TrackPoint(lat, lon)
                }
            }
        }
    }

    fun stopRecording(): String {
        recordingJob?.cancel()
        recordingJob = null
        _isRecording.value = false
        val points = _recordingPoints.value
        _recordingPoints.value = emptyList()
        if (points.size < 2) {
            return "Zu wenige Punkte für einen Track"
        }
        val name = "Aufzeichnung ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())}"
        val track = Track(
            id = System.currentTimeMillis(),
            name = name,
            points = points
        )
        tracks.value = tracks.value + track
        persistTracks()
        return "Track mit ${points.size} Punkten gespeichert"
    }

    fun deleteTrack(id: Long) {
        tracks.value = tracks.value.filterNot { it.id == id }
        if (activeTrackId.value == id) {
            activeTrackId.value = null
            cancelTrackFollowing()
            activeTrackProgress.value = 0
        }
        persistTracks()
    }

    fun updateWaypoint(updated: Waypoint) {
        waypoints.value = waypoints.value.map { wp ->
            if (wp.id == updated.id) updated else wp
        }
        persistWaypoints()
    }

    fun projectWaypoint(name: String, bearingDegrees: Double, distanceMeters: Double): String {
        val base = waypoints.value.find { it.id == activeWaypointId.value }
            ?: return "Kein aktives Ziel ausgewählt"
        val (lat, lon) = Navigation.project(base.latitude, base.longitude, bearingDegrees, distanceMeters)
        val wp = Waypoint(
            id = System.currentTimeMillis(),
            name = name.ifBlank { "${base.name} + ${distanceMeters.toInt()} m" },
            latitude = lat,
            longitude = lon
        )
        waypoints.value = waypoints.value + wp
        activeWaypointId.value = wp.id
        persistWaypoints()
        return "Projizierter Zielpunkt angelegt"
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

    fun importWaypoints(uri: Uri): String {
        val text = try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        } ?: return "Datei konnte nicht gelesen werden"

        val result = WaypointCodec.parse(text)
        if (result.waypoints.isEmpty() && result.tracks.isEmpty()) {
            return if (result.skipped > 0) {
                "Keine gültigen Zielpunkte oder Tracks gefunden"
            } else {
                "Keine Zielpunkte oder Tracks in der Datei gefunden"
            }
        }

        if (result.waypoints.isNotEmpty()) {
            waypoints.value = waypoints.value + result.waypoints
            persistWaypoints()
        }
        if (result.tracks.isNotEmpty()) {
            tracks.value = tracks.value + result.tracks
            persistTracks()
        }

        val parts = mutableListOf<String>()
        if (result.waypoints.isNotEmpty()) parts.add("${result.waypoints.size} Zielpunkt(e)")
        if (result.tracks.isNotEmpty()) parts.add("${result.tracks.size} Track(s)")
        val suffix = if (result.skipped > 0) ", ${result.skipped} übersprungen" else ""
        return parts.joinToString(" und ") + " importiert" + suffix
    }

    fun exportGpx(uri: Uri): Boolean {
        return try {
            val content = WaypointCodec.toGpx(waypoints.value, tracks.value)
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use {
                it.write(content.toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: Exception) {
            false
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
                    found = o.optBoolean("found", false)
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun persistTracks() {
        val arr = JSONArray()
        tracks.value.forEach { trk ->
            arr.put(
                JSONObject().apply {
                    put("id", trk.id)
                    put("name", trk.name)
                    val pts = JSONArray()
                    trk.points.forEach { p ->
                        pts.put(
                            JSONObject().apply {
                                put("lat", p.latitude)
                                put("lon", p.longitude)
                            }
                        )
                    }
                    put("points", pts)
                }
            )
        }
        prefs.edit().putString("tracks", arr.toString()).apply()
    }

    private fun loadTracks(): List<Track> {
        val raw = prefs.getString("tracks", null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                val ptsArr = o.getJSONArray("points")
                val points = (0 until ptsArr.length()).map { j ->
                    val p = ptsArr.getJSONObject(j)
                    TrackPoint(p.getDouble("lat"), p.getDouble("lon"))
                }
                Track(
                    id = o.getLong("id"),
                    name = o.getString("name"),
                    points = points
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    override fun onCleared() {
        super.onCleared()
        gpsDataManager.stopTracking()
    }
}

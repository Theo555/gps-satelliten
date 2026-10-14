package de.gpsradar.satellites.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import org.json.JSONArray
import org.json.JSONObject
import de.gpsradar.satellites.gps.GpsDataManager
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.Navigation
import de.gpsradar.satellites.model.SatelliteInfo
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.model.WaypointCodec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class SatelliteSort(val label: String) {
    SIGNAL_DESC("Signalstärke (C/N₀)"),
    IN_FIX_FIRST("Im Fix zuerst"),
    ELEVATION_DESC("Höhenwinkel"),
    SVID_ASC("Satelliten-ID")
}

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
        if (id == null) {
            prefs.edit().remove("active_waypoint_id").apply()
        } else {
            prefs.edit().putLong("active_waypoint_id", id).apply()
        }
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
        waypoints.value = waypoints.value.map { wp ->
            if (wp.id == id) wp.copy(found = !wp.found) else wp
        }
        persistWaypoints()
    }

    fun importWaypoints(uri: Uri): String {
        val text = try {
            getApplication<Application>().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            null
        } ?: return "Datei konnte nicht gelesen werden"

        val result = WaypointCodec.parse(text)
        if (result.waypoints.isEmpty()) {
            return if (result.skipped > 0) {
                "Keine gültigen Zielpunkte gefunden"
            } else {
                "Keine Zielpunkte in der Datei gefunden"
            }
        }

        waypoints.value = waypoints.value + result.waypoints
        persistWaypoints()

        return if (result.skipped > 0) {
            "${result.waypoints.size} Zielpunkte importiert, ${result.skipped} übersprungen"
        } else {
            "${result.waypoints.size} Zielpunkte importiert"
        }
    }

    fun exportGpx(uri: Uri): Boolean {
        return try {
            val content = WaypointCodec.toGpx(waypoints.value)
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

    override fun onCleared() {
        super.onCleared()
        gpsDataManager.stopTracking()
    }
}

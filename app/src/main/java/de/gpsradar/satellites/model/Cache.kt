package de.gpsradar.satellites.model

/**
 * Gruppierte Sicht auf einen Geocache: ein Cache mit seinen zugehörigen
 * Wegpunkten (Stage(s), Final, ggf. zugeordneter Parkplatz).
 *
 * Gruppiert wird über die `groundspeak:cache id` beim GPX-Import.
 * Wegpunkte ohne Cache-ID bleiben freie Wegpunkte (z. B. Parkplatz).
 */
data class Cache(
    val id: String,
    val name: String,
    val code: String?,
    val waypoints: List<Waypoint>,
    val difficulty: Double?,
    val terrain: Double?,
    val cacheType: String?,
    val hint: String?,
    val archived: Boolean = false
) {
    /** Primärer Wegpunkt = der Cache selbst (Träger der Cache-Metadaten). */
    val primary: Waypoint?
        get() = waypoints.firstOrNull { isCacheType(it.cacheType) } ?: waypoints.firstOrNull()

    /** Anzahl der zugehörigen Wegpunkte (Stages, Final usw.). */
    val childCount: Int get() = waypoints.size
}

data class CacheGrouping(
    val caches: List<Cache>,
    val freeWaypoints: List<Waypoint>
)

/**
 * Gruppiert Wegpunkte anhand der `groundspeak:cache id` zu Cache-Objekten.
 * Die Reihenfolge der Eingabeliste bleibt dabei erhalten.
 */
fun groupWaypoints(waypoints: List<Waypoint>): CacheGrouping {
    val free = mutableListOf<Waypoint>()
    val byId = LinkedHashMap<String, MutableList<Waypoint>>()

    for (wp in waypoints) {
        val gid = wp.groupCacheId
        if (gid.isNullOrBlank()) {
            free.add(wp)
        } else {
            byId.getOrPut(gid) { mutableListOf() }.add(wp)
        }
    }

    val caches = byId.map { (gid, wps) ->
        val primary = wps.firstOrNull { isCacheType(it.cacheType) } ?: wps.first()
        val ordered = listOf(primary) + wps.filter { it !== primary }
        Cache(
            id = gid,
            name = primary.name,
            code = primary.code,
            waypoints = ordered,
            difficulty = primary.difficulty,
            terrain = primary.terrain,
            cacheType = primary.cacheType,
            hint = primary.hint,
            archived = wps.any { it.archived }
        )
    }

    return CacheGrouping(caches, free)
}

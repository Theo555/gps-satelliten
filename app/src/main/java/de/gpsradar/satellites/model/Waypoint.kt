package de.gpsradar.satellites.model

import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class Waypoint(
    val id: Long,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val code: String? = null,
    val description: String? = null,
    val hint: String? = null,
    val difficulty: Double? = null,
    val terrain: Double? = null,
    val cacheType: String? = null,
    val groupCacheId: String? = null,
    val archived: Boolean = false,
    val found: Boolean = false
) {
    val formatted: String
        get() = Coordinates.formatDecimal(latitude, longitude)

    val formattedDms: String
        get() = Coordinates.formatDms(latitude, longitude)

    val formattedDdm: String
        get() = Coordinates.formatDdm(latitude, longitude)
}

object Navigation {

    /** Ab dieser Geschwindigkeit (m/s) übernimmt der GPS-Kurs die Pfeilreferenz. */
    const val COURSE_SPEED_ENTER_MS = 0.9f

    /** Darunter fällt die Pfeilreferenz auf den Kompass zurück (Hysterese gegen Flackern). */
    const val COURSE_SPEED_EXIT_MS = 0.5f

    /** Großkreis-Entfernung in Metern (Haversine-Formel). */
    fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earthRadius = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
            sin(dLon / 2) * sin(dLon / 2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return earthRadius * c
    }

    /** Anfangspeilung in Grad (0..360), von Punkt 1 Richtung Punkt 2. */
    fun initialBearingDegrees(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val dLambda = Math.toRadians(lon2 - lon1)
        val y = sin(dLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(dLambda)
        return (Math.toDegrees(atan2(y, x)) + 360.0) % 360.0
    }

    /**
     * Referenzrichtung für Richtungspfeile: der GPS-Kurs bei ausreichender
     * Bewegung, sonst der Kompasskurs. [wasUsingGpsCourse] liefert die
     * Hysterese: zurück auf den Kompass geht es erst unter
     * [COURSE_SPEED_EXIT_MS], übernommen wird der GPS-Kurs ab
     * [COURSE_SPEED_ENTER_MS]. Ohne gültigen GPS-Kurs bleibt der Kompass.
     */
    fun arrowReferenceDegrees(
        speedMps: Float?,
        bearingDegrees: Float?,
        compassHeading: Float,
        wasUsingGpsCourse: Boolean
    ): Float {
        val speed = speedMps ?: 0f
        val moving = if (wasUsingGpsCourse) {
            speed >= COURSE_SPEED_EXIT_MS
        } else {
            speed >= COURSE_SPEED_ENTER_MS
        }
        return if (moving && bearingDegrees != null) bearingDegrees else compassHeading
    }

    /** Projiziert einen Punkt aus Peilung + Entfernung. Liefert (Breite, Länge). */
    fun project(lat: Double, lon: Double, bearingDegrees: Double, distanceMeters: Double): Pair<Double, Double> {
        val earthRadius = 6371000.0
        val brng = Math.toRadians(bearingDegrees)
        val lat1 = Math.toRadians(lat)
        val lon1 = Math.toRadians(lon)
        val dR = distanceMeters / earthRadius
        val lat2 = asin(sin(lat1) * cos(dR) + cos(lat1) * sin(dR) * cos(brng))
        val lon2 = lon1 + atan2(sin(brng) * sin(dR) * cos(lat1), cos(dR) - sin(lat1) * sin(lat2))
        return Math.toDegrees(lat2) to Math.toDegrees(lon2)
    }
}

val CACHE_TYPES = listOf(
    "Traditional Cache",
    "Multi-cache",
    "Unknown Cache",
    "Letterbox Hybrid",
    "Earthcache",
    "Wherigo Cache",
    "Virtual Cache",
    "Event Cache"
)

fun cacheTypeEmoji(type: String?): String = when (type) {
    "Traditional Cache" -> "🟢"
    "Multi-cache" -> "🔵"
    "Unknown Cache" -> "❓"
    "Letterbox Hybrid" -> "📮"
    "Earthcache" -> "🌍"
    "Wherigo Cache" -> "🎮"
    "Virtual Cache" -> "👻"
    "Event Cache" -> "📅"
    else -> "📍"
}

/**
 * Liefert true, wenn [type] ein echter Cache-Typ ist
 * (im Gegensatz zu Wegpunkt-Typen wie "Final Location", "Parking Area" usw.).
 */
fun isCacheType(type: String?): Boolean {
    if (type.isNullOrBlank()) return false
    return CACHE_TYPES.any { it.equals(type.trim(), ignoreCase = true) }
}

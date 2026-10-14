package de.gpsradar.satellites.model

import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject

/**
 * Import/Export von Zielpunkten als GPX (Geocaching-Standard) und JSON (eigenes Format).
 */
object WaypointCodec {

    data class ParseResult(val waypoints: List<Waypoint>, val skipped: Int)

    fun parse(text: String): ParseResult {
        val t = text.trim()
        return when {
            t.startsWith("<") -> parseGpx(t)
            t.startsWith("[") || t.startsWith("{") -> parseJson(t)
            else -> ParseResult(emptyList(), 0)
        }
    }

    /** ROT13-Verschlüsselung/-Entschlüsselung (selbst-invers). */
    fun rot13(s: String): String = buildString {
        for (c in s) {
            append(
                when {
                    c in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
                    c in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
                    else -> c
                }
            )
        }
    }

    // ---------- GPX ----------

    fun toGpx(waypoints: List<Waypoint>): String {
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"GPS Satelliten\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
        waypoints.forEach { wp ->
            sb.append("  <wpt lat=\"")
                .append(String.format(Locale.US, "%.6f", wp.latitude))
                .append("\" lon=\"")
                .append(String.format(Locale.US, "%.6f", wp.longitude))
                .append("\">\n")
            sb.append("    <name>").append(escapeXml(wp.name)).append("</name>\n")
            wp.code?.let { sb.append("    <desc>").append(escapeXml(it)).append("</desc>\n") }
            wp.cacheType?.let { sb.append("    <type>").append(escapeXml(it)).append("</type>\n") }
            sb.append("  </wpt>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun parseGpx(text: String): ParseResult {
        val waypoints = mutableListOf<Waypoint>()
        var skipped = 0

        val wptRegex = Regex("<wpt\\b([^>]*)>(.*?)</wpt>", RegexOption.DOT_MATCHES_ALL)
        for (m in wptRegex.findAll(text)) {
            val attrs = m.groupValues[1]
            val body = m.groupValues[2]

            val lat = Regex("lat=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)?.toDoubleOrNull()
            val lon = Regex("lon=\"([^\"]+)\"").find(attrs)?.groupValues?.get(1)?.toDoubleOrNull()
            if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                skipped++
                continue
            }

            val rawName = extractTag(body, "name") ?: "Ziel"
            val desc = extractTag(body, "desc")

            // Geocaching-Konvention: <name> = GC-Code, <desc> = Titel.
            val isGc = Regex("^GC[0-9A-Z]{2,}$", RegexOption.IGNORE_CASE).matches(rawName.trim())
            val name = if (isGc) {
                desc?.substringBefore(",")?.trim()?.substringBefore(" by ")?.trim()?.ifBlank { rawName } ?: rawName
            } else {
                rawName
            }
            val code = if (isGc) rawName.trim() else null
            val hint = extractTag(body, "groundspeak:encoded_hints")
            val cacheType = extractTag(body, "type")?.substringAfter("|")?.trim()?.ifBlank { null }
            val cacheAttrs = Regex("<groundspeak:cache\\b([^>]*)>").find(body)?.groupValues?.get(1)
            val difficulty = cacheAttrs?.let { Regex("difficulty=\"([^\"]+)\"").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }
            val terrain = cacheAttrs?.let { Regex("terrain=\"([^\"]+)\"").find(it)?.groupValues?.get(1)?.toDoubleOrNull() }

            waypoints.add(
                Waypoint(
                    id = System.currentTimeMillis() + waypoints.size,
                    name = name,
                    latitude = lat,
                    longitude = lon,
                    code = code,
                    hint = hint,
                    difficulty = difficulty,
                    terrain = terrain,
                    cacheType = cacheType
                )
            )
        }
        return ParseResult(waypoints, skipped)
    }

    private fun extractTag(xml: String, tag: String): String? {
        val m = Regex("<$tag\\b[^>]*>(.*?)</$tag>", RegexOption.DOT_MATCHES_ALL).find(xml)
        return m?.groupValues?.get(1)?.trim()?.ifBlank { null }?.let { unescapeXml(it) }
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    private fun unescapeXml(s: String): String =
        s.replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")

    // ---------- JSON ----------

    fun toJson(waypoints: List<Waypoint>): String {
        val arr = JSONArray()
        waypoints.forEach { wp ->
            arr.put(
                JSONObject().apply {
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
        return arr.toString(2)
    }

    private fun parseJson(text: String): ParseResult {
        return try {
            jsonArrayToWaypoints(JSONArray(text))
        } catch (e: Exception) {
            try {
                val obj = JSONObject(text)
                val arr = obj.optJSONArray("waypoints")
                if (arr != null) jsonArrayToWaypoints(arr) else ParseResult(emptyList(), 0)
            } catch (e2: Exception) {
                ParseResult(emptyList(), 0)
            }
        }
    }

    private fun jsonArrayToWaypoints(arr: JSONArray): ParseResult {
        val waypoints = mutableListOf<Waypoint>()
        var skipped = 0
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                skipped++
                continue
            }
            val lat = o.optDouble("lat", Double.NaN)
            val lon = o.optDouble("lon", Double.NaN)
            val name = o.optString("name", "").ifBlank { "Ziel" }
            if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                skipped++
                continue
            }
            waypoints.add(
                Waypoint(
                    id = System.currentTimeMillis() + waypoints.size,
                    name = name,
                    latitude = lat,
                    longitude = lon,
                    code = o.optString("code", "").ifBlank { null },
                    description = o.optString("description", "").ifBlank { null },
                    hint = o.optString("hint", "").ifBlank { null },
                    difficulty = if (o.has("difficulty")) o.optDouble("difficulty") else null,
                    terrain = if (o.has("terrain")) o.optDouble("terrain") else null,
                    cacheType = o.optString("cacheType", "").ifBlank { null },
                    found = o.optBoolean("found", false)
                )
            )
        }
        return ParseResult(waypoints, skipped)
    }
}

package de.gpsradar.satellites.model

import android.util.Xml
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import org.xmlpull.v1.XmlPullParser

/** Grund, warum ein Eintrag beim Import übersprungen wurde.
 *
 * Bewusst ohne fertigen Text: Die Übersetzung passiert in der UI-Schicht
 * (`de.gpsradar.satellites.ui.SkipReason.describe`), damit das Modell frei von
 * Android-Abhängigkeiten und sprachunabhängig bleibt.
 */
sealed interface SkipReason {
    /** Die Datei ließ sich nicht als GPX öffnen. */
    data object GpxUnreadable : SkipReason

    /** Strukturfehler mitten im GPX-Dokument (bereits Gelesenes bleibt erhalten). */
    data class InvalidXml(val detail: String?) : SkipReason

    data object WaypointWithoutCoordinates : SkipReason

    data object TrackPointWithoutCoordinates : SkipReason

    data class TrackTooShort(val name: String) : SkipReason

    data object InvalidJson : SkipReason

    data class JsonEntryNotObject(val index: Int) : SkipReason

    data class JsonWaypointWithoutCoordinates(val name: String) : SkipReason

    /** Die Datei konnte gar nicht erst gelesen werden. */
    data object FileUnreadable : SkipReason

    /** ZIP-Archiv erkannt, ließ sich aber nicht entpacken. */
    data object ArchiveUnreadable : SkipReason
}

data class ImportResult(
    val caches: List<Cache>,
    val freeWaypoints: List<Waypoint>,
    val tracks: List<Track>,
    val skipped: List<SkipReason>
) {
    /** Alle Wegpunkte flach (Caches aufgeklappt + freie Wegpunkte). */
    val waypoints: List<Waypoint>
        get() = caches.flatMap { it.waypoints } + freeWaypoints
}

/**
 * Import/Export von Zielpunkten als GPX (Geocaching-Standard) und JSON (eigenes Format).
 */
object WaypointCodec {

    /** Eine einzelne zu exportierende Datei (pro Cache / Route / Position). */
    data class GpxExportFile(
        val fileName: String,
        val content: String
    )

    fun parse(text: String, fallbackWaypointName: String = DEFAULT_WAYPOINT_NAME): ImportResult {
        // Windows-Editoren (z. B. Notepad) schreiben vor dem ersten Zeichen ein
        // UTF-8-BOM. trim() entfernt es nicht (U+FEFF ist kein Leerzeichen),
        // ohne dieses Entfernen würde die Datei als "kein GPX/JSON" gelten.
        val t = text.trim().removePrefix("\uFEFF").trim()
        return when {
            t.startsWith("<") -> parseGpx(t, fallbackWaypointName)
            t.startsWith("[") || t.startsWith("{") -> parseJson(t, fallbackWaypointName)
            else -> ImportResult(emptyList(), emptyList(), emptyList(), emptyList())
        }
    }

    /**
     * Liest eine Datei, egal ob GPX/JSON oder ein ZIP-Archiv mit mehreren
     * solchen Dateien (so liefert Opencaching Suchergebnisse aus).
     */
    fun parseBytes(bytes: ByteArray, fallbackWaypointName: String = DEFAULT_WAYPOINT_NAME): ImportResult =
        if (isZip(bytes)) parseZip(bytes, fallbackWaypointName)
        else parse(bytes.toString(Charsets.UTF_8), fallbackWaypointName)

    /** ZIP-Archive beginnen mit "PK\u0003\u0004". */
    fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 &&
            bytes[0] == 0x50.toByte() && bytes[1] == 0x4B.toByte() &&
            bytes[2] == 0x03.toByte() && bytes[3] == 0x04.toByte()

    private fun parseZip(bytes: ByteArray, fallbackWaypointName: String): ImportResult {
        val results = mutableListOf<ImportResult>()
        try {
            ByteArrayInputStream(bytes).use { input ->
                ZipInputStream(input).use { zip ->
                    while (true) {
                        val entry = zip.nextEntry ?: break
                        val name = entry.name.lowercase()
                        if (entry.isDirectory) continue
                        // Nur echte Inhalte lesen: .gpx/.json, keine Bilder,
                        // keine __MACOSX-Reste, keine Ordner.
                        if (!name.endsWith(".gpx") && !name.endsWith(".json")) continue
                        // readBytes() liest bis zum Ende des Eintrags; danach
                        // steht der Strom auf dem nächsten Eintrag.
                        val text = zip.readBytes().toString(Charsets.UTF_8)
                        results += parse(text, fallbackWaypointName)
                    }
                }
            }
        } catch (e: Exception) {
            return ImportResult(emptyList(), emptyList(), emptyList(), listOf(SkipReason.ArchiveUnreadable))
        }
        return merge(results)
    }

    /**
     * Führt die Ergebnisse mehrerer Dateien zusammen.
     *
     * Kennungen entstehen in [parse] aus `System.currentTimeMillis() + Zähler`
     * und können sich zwischen zwei Dateien überschneiden – deshalb werden sie
     * hier neu durchnummeriert, sonst teilen sich zwei Zielpunkte eine ID.
     */
    private fun merge(results: List<ImportResult>): ImportResult {
        val base = System.currentTimeMillis()
        val waypoints = results.flatMap { it.waypoints }
            .mapIndexed { i, wp -> wp.copy(id = base + i) }
        val tracks = results.flatMap { it.tracks }
            .mapIndexed { i, trk -> trk.copy(id = base + 1_000_000L + i) }
        val grouping = groupWaypoints(waypoints)
        return ImportResult(
            grouping.caches,
            grouping.freeWaypoints,
            tracks,
            results.flatMap { it.skipped }
        )
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

    fun toGpx(waypoints: List<Waypoint>): String = toGpx(waypoints, emptyList())

    fun toGpx(waypoints: List<Waypoint>, tracks: List<Track>): String {
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
        tracks.forEach { trk ->
            sb.append("  <trk>\n")
            sb.append("    <name>").append(escapeXml(trk.name)).append("</name>\n")
            sb.append("    <trkseg>\n")
            trk.points.forEach { p ->
                sb.append("      <trkpt lat=\"")
                    .append(String.format(Locale.US, "%.6f", p.latitude))
                    .append("\" lon=\"")
                    .append(String.format(Locale.US, "%.6f", p.longitude))
                    .append("\">")
                if (p.elevationMeters != null || p.timeMillis != null) {
                    sb.append("\n")
                    p.elevationMeters?.let {
                        sb.append("        <ele>").append(String.format(Locale.US, "%.1f", it)).append("</ele>\n")
                    }
                    p.timeMillis?.let {
                        sb.append("        <time>").append(formatGpxTime(it)).append("</time>\n")
                    }
                    sb.append("      </trkpt>\n")
                } else {
                    sb.append("</trkpt>\n")
                }
            }
            sb.append("    </trkseg>\n")
            sb.append("  </trk>\n")
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private const val GPX_NS_1_0 = "http://www.topografix.com/GPX/1/0"
    private const val GPX_NS_1_1 = "http://www.topografix.com/GPX/1/1"
    private const val GROUNDSPEAK_NS_PREFIX = "http://www.groundspeak.com/cache/"

    /** Ersatzname für Zielpunkte ohne eigenen Namen, wenn der Aufrufer keinen übergibt. */
    const val DEFAULT_WAYPOINT_NAME = "Waypoint"

    private val GC_CODE_PREFIX = Regex("^(GC[0-9A-Z_]+)", RegexOption.IGNORE_CASE)
    private val GC_CODE_FULL = Regex("^GC[0-9A-Z]{2,}$", RegexOption.IGNORE_CASE)

    private fun parseGpx(text: String, fallbackWaypointName: String): ImportResult {
        val waypoints = mutableListOf<Waypoint>()
        val tracks = mutableListOf<Track>()
        val skipped = mutableListOf<SkipReason>()

        val parser = try {
            Xml.newPullParser().apply {
                setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
                setInput(StringReader(text))
            }
        } catch (e: Exception) {
            return ImportResult(emptyList(), emptyList(), emptyList(), listOf(SkipReason.GpxUnreadable))
        }

        try {
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG) {
                    when {
                        parser.isGpxElement("wpt") -> parseWaypoint(parser, waypoints, skipped, fallbackWaypointName)
                        parser.isGpxElement("trk") -> parseTrackLike(parser, "trk", "trkpt", tracks, skipped)
                        parser.isGpxElement("rte") -> parseTrackLike(parser, "rte", "rtept", tracks, skipped)
                        parser.isGpxElement("gpx") -> { /* Wurzelelement – absteigen, nicht überspringen */ }
                        else -> skipElement(parser)
                    }
                }
                event = parser.next()
            }
        } catch (e: Exception) {
            // Strukturfehler: bereits Gelesenes behalten, Rest als übersprungen melden.
            skipped.add(SkipReason.InvalidXml(e.message))
        }

        val grouping = groupWaypoints(waypoints)
        return ImportResult(grouping.caches, grouping.freeWaypoints, tracks, skipped)
    }

    private fun parseWaypoint(
        parser: XmlPullParser,
        out: MutableList<Waypoint>,
        skipped: MutableList<SkipReason>,
        fallbackWaypointName: String
    ) {
        val lat = parser.attr("lat")?.toDoubleOrNull()
        val lon = parser.attr("lon")?.toDoubleOrNull()

        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            skipped.add(SkipReason.WaypointWithoutCoordinates)
            skipElement(parser)
            return
        }

        var rawName: String? = null
        var desc: String? = null
        var type: String? = null
        var hint: String? = null
        var gsName: String? = null
        var groupCacheId: String? = null
        var archived = false
        var difficulty: Double? = null
        var terrain: Double? = null

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isGpxElement("name") -> rawName = readElementText(parser)
                    parser.isGpxElement("desc") -> desc = readElementText(parser)
                    parser.isGpxElement("type") -> type = readElementText(parser)
                    parser.isGroundspeakElement("cache") -> {
                        val c = parseGroundspeakCache(parser)
                        if (groupCacheId == null) groupCacheId = c.id
                        if (c.archived) archived = true
                        if (gsName == null) gsName = c.name
                        if (difficulty == null) difficulty = c.difficulty
                        if (terrain == null) terrain = c.terrain
                        if (hint == null) hint = c.hint
                    }
                    parser.isGroundspeakElement("name") -> if (gsName == null) gsName = readElementText(parser)
                    parser.isGroundspeakElement("encoded_hints") -> if (hint == null) hint = readElementText(parser)
                    else -> skipElement(parser)
                }
                XmlPullParser.END_TAG -> depth--
                // Abgeschnittene Datei: ohne diesen Zweig würde die Schleife
                // endlos weiterlaufen (next() liefert END_DOCUMENT beliebig oft)
                // und der Import haengen. Element verwerfen, Fehler melden.
                XmlPullParser.END_DOCUMENT -> throw TruncatedDocument()
            }
        }

        val fallbackName = rawName ?: fallbackWaypointName
        val codeFromName = GC_CODE_PREFIX.find(fallbackName.trim())?.groupValues?.get(1)
        val codeFromDesc = desc?.let { GC_CODE_PREFIX.find(it.trim())?.groupValues?.get(1) }
        val isGc = GC_CODE_FULL.matches(fallbackName.trim())

        val name = when {
            gsName != null -> gsName
            codeFromName != null -> fallbackName.substringAfter(" - ", fallbackName).trim().ifBlank { fallbackName }
            isGc -> desc?.substringBefore(",")?.trim()?.substringBefore(" by ")?.trim()?.ifBlank { fallbackName } ?: fallbackName
            else -> fallbackName
        }
        val code = codeFromName ?: if (isGc) fallbackName.trim() else null
        val cacheType = type?.substringAfter("|")?.trim()?.let { t ->
            CACHE_TYPES.firstOrNull { it.equals(t, ignoreCase = true) } ?: t
        }?.ifBlank { null }
        // Gruppenschlüssel: Der GC-Code verbindet Kopf und Zusatzpunkte – der
        // Kopf trägt ihn im Namen, die Zusatzpunkte in <desc>. Die groundspeak-id
        // ist nur Ersatz, weil sie in Zusatzpunkten fehlt; würde sie gewinnen,
        // zerfiele jede echte Pocket Query in Kopf und Zusatzpunkte.
        val finalGroupId = code ?: codeFromDesc ?: groupCacheId

        // <desc> traegt bei Zusatzpunkten einer Pocket Query den Hinweis des
        // Owners ("Parkplatz bei N51 ..."). Steht dort nur der GC-Code, ist
        // das keine Beschreibung, sondern die Gruppenzuordnung.
        val descriptionText = desc?.trim()?.takeIf { it.isNotEmpty() && !GC_CODE_FULL.matches(it) }

        out.add(
            Waypoint(
                id = System.currentTimeMillis() + out.size,
                name = name,
                latitude = lat,
                longitude = lon,
                code = code,
                description = descriptionText,
                hint = normalizeHint(hint),
                difficulty = difficulty,
                terrain = terrain,
                cacheType = cacheType,
                groupCacheId = finalGroupId,
                archived = archived
            )
        )
    }

    private data class GroundspeakCache(
        val id: String?,
        val name: String?,
        val difficulty: Double?,
        val terrain: Double?,
        val hint: String?,
        val archived: Boolean
    )

    private fun parseGroundspeakCache(parser: XmlPullParser): GroundspeakCache {
        var id: String? = null
        var archived = false

        // Attribute in beliebiger Reihenfolge.
        for (i in 0 until parser.attributeCount) {
            when (parser.getAttributeName(i)) {
                "id" -> id = parser.getAttributeValue(i)?.trim()?.ifBlank { null }
                "archived" -> archived = parser.getAttributeValue(i).asGpxBoolean()
            }
        }

        var name: String? = null
        var difficulty: Double? = null
        var terrain: Double? = null
        var hint: String? = null

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isGroundspeakElement("name") -> name = readElementText(parser)
                    parser.isGroundspeakElement("difficulty") -> difficulty = readElementText(parser)?.toDoubleOrNull()
                    parser.isGroundspeakElement("terrain") -> terrain = readElementText(parser)?.toDoubleOrNull()
                    parser.isGroundspeakElement("encoded_hints") -> hint = readElementText(parser)
                    else -> skipElement(parser)
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> throw TruncatedDocument()
            }
        }

        return GroundspeakCache(id, name, difficulty, terrain, hint, archived)
    }

    private fun parseTrackLike(
        parser: XmlPullParser,
        kind: String,
        pointTag: String,
        out: MutableList<Track>,
        skipped: MutableList<SkipReason>
    ) {
        var name: String? = null
        val points = mutableListOf<TrackPoint>()

        var depth = 1
        var truncated = false
        try {
            while (depth > 0) {
                when (parser.next()) {
                    XmlPullParser.START_TAG -> when {
                        parser.isGpxElement("name") -> name = readElementText(parser)
                        parser.isGpxElement(pointTag) -> {
                            val pt = parseTrackPoint(parser)
                            if (pt != null) points.add(pt) else skipped.add(SkipReason.TrackPointWithoutCoordinates)
                        }
                        parser.isGpxElement("trkseg") -> { /* Segment absteigen, enthält die <trkpt> */ }
                        else -> skipElement(parser)
                    }
                    XmlPullParser.END_TAG -> depth--
                    // Abgeschnittene Datei: die vollständigen Punkte behalten,
                    // den Fehler aber melden (kein Endloslauf).
                    XmlPullParser.END_DOCUMENT -> throw TruncatedDocument()
                }
            }
        } catch (e: TruncatedDocument) {
            truncated = true
        }

        val trackName = name?.ifBlank { null } ?: if (kind == "rte") "Route" else "Track"
        if (points.size >= 2) {
            out.add(Track(id = System.currentTimeMillis() + out.size + 1_000_000L, name = trackName, points = points))
        } else {
            skipped.add(SkipReason.TrackTooShort(trackName))
        }
        if (truncated) throw TruncatedDocument()
    }

    private fun parseTrackPoint(parser: XmlPullParser): TrackPoint? {
        val lat = parser.attr("lat")?.toDoubleOrNull()
        val lon = parser.attr("lon")?.toDoubleOrNull()

        if (lat == null || lon == null || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
            skipElement(parser)
            return null
        }

        var ele: Double? = null
        var time: Long? = null

        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> when {
                    parser.isGpxElement("ele") -> ele = readElementText(parser)?.toDoubleOrNull()
                    parser.isGpxElement("time") -> time = readElementText(parser)?.let { parseGpxTime(it) }
                    else -> skipElement(parser)
                }
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> throw TruncatedDocument()
            }
        }

        return TrackPoint(lat, lon, ele, time)
    }

    private fun XmlPullParser.attr(name: String): String? {
        for (i in 0 until attributeCount) {
            if (getAttributeName(i) == name) return getAttributeValue(i)
        }
        return null
    }

    private fun XmlPullParser.isGpxElement(local: String): Boolean {
        val ns = namespace
        return when {
            // Manche Werkzeuge schreiben GPX ohne xmlns; der Parser meldet dann
            // einen leeren (nicht null) Namensraum. Solche Dateien trotzdem lesen.
            ns.isNullOrEmpty() -> name == local || name.endsWith(":$local")
            ns == GPX_NS_1_0 || ns == GPX_NS_1_1 -> name == local
            else -> false
        }
    }

    private fun XmlPullParser.isGroundspeakElement(local: String): Boolean {
        val ns = namespace
        return when {
            ns != null && ns.startsWith(GROUNDSPEAK_NS_PREFIX) -> name == local
            ns == null -> name == local || name.endsWith(":$local")
            else -> false
        }
    }

    private fun String?.asGpxBoolean(): Boolean = this?.equals("true", ignoreCase = true) == true

    /**
     * Bereinigt einen Hint. Geocaching.com schreibt bei fehlendem Hint wörtlich
     * "none" in `groundspeak:encoded_hints`; leere Platzhalter dürfen nicht als
     * Hint angezeigt werden.
     */
    private fun normalizeHint(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        return when {
            trimmed.isEmpty() -> null
            trimmed.equals("none", ignoreCase = true) -> null
            else -> trimmed
        }
    }

    private fun readElementText(parser: XmlPullParser): String? {
        val sb = StringBuilder()
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.TEXT, XmlPullParser.CDSECT, XmlPullParser.ENTITY_REF -> sb.append(parser.text)
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                // Abgeschnittene Datei: Text zurückgeben, der Aufrufer bricht ab.
                XmlPullParser.END_DOCUMENT -> throw TruncatedDocument()
            }
        }
        return sb.toString().trim().ifBlank { null }
    }

    private fun skipElement(parser: XmlPullParser) {
        var depth = 1
        while (depth > 0) {
            when (parser.next()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> throw TruncatedDocument()
            }
        }
    }

    /** Signalisiert eine Datei, die mitten in einem Element endet. */
    private class TruncatedDocument : Exception("Datei endet vorzeitig")

    private fun formatGpxTime(millis: Long): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("UTC")
        return sdf.format(Date(millis))
    }

    private fun parseGpxTime(value: String): Long? {
        val cleaned = value.trim().substringBefore(".")
        val formats = listOf("yyyy-MM-dd'T'HH:mm:ss'Z'", "yyyy-MM-dd'T'HH:mm:ss")
        for (f in formats) {
            try {
                val sdf = SimpleDateFormat(f, Locale.US)
                sdf.timeZone = TimeZone.getTimeZone("UTC")
                return sdf.parse(cleaned)?.time
            } catch (e: Exception) {
                // nächstes Format versuchen
            }
        }
        return null
    }

    private fun escapeXml(s: String): String =
        s.replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")

    // ---------- Mehrfach-Export (pro Objekt eine Datei) ----------

    /**
     * Erzeugt pro Objekt eine eigene GPX-Datei:
     *  - jeder Cache (Kopf + Zusatz-Wegpunkte) → `cache_<GC-Code>.gpx`
     *  - jede freie Position → `position_<Name>.gpx`
     *  - jede Route → `track_<Name>.gpx`
     *
     * Die Gruppierung wird so geschrieben, dass Import → Export → Import → Export
     * strukturell identisch bleibt: Der Kopf trägt den GC-Code im `<name>`, die
     * Zusatz-Wegpunkte den GC-Code im `<desc>`.
     */
    fun toGpxFiles(waypoints: List<Waypoint>, tracks: List<Track>): List<GpxExportFile> {
        val files = mutableListOf<GpxExportFile>()
        val grouping = groupWaypoints(waypoints)

        grouping.caches.forEach { cache ->
            val code = cache.code?.takeIf { it.isNotBlank() }
            val baseName = code ?: cache.name
            files.add(GpxExportFile("cache_${sanitizeFileName(baseName)}.gpx", toCacheGpx(cache)))
        }

        grouping.freeWaypoints.forEach { wp ->
            files.add(GpxExportFile("position_${sanitizeFileName(wp.name)}.gpx", toFreeWaypointGpx(wp)))
        }

        tracks.forEach { trk ->
            files.add(GpxExportFile("track_${sanitizeFileName(trk.name)}.gpx", toGpx(emptyList(), listOf(trk))))
        }

        return files
    }

    private fun toCacheGpx(cache: Cache): String {
        val sb = StringBuilder()
        appendGpxHeader(sb)
        val primary = cache.primary
        if (primary != null) {
            val code = cache.code?.takeIf { it.isNotBlank() }
            val headName = if (code != null) "$code - ${primary.name}" else primary.name
            appendCacheHead(sb, primary, headName)
        }
        cache.waypoints.filter { it !== primary }.forEach { child ->
            appendWpt(sb, child.latitude, child.longitude, child.name, cache.code?.takeIf { it.isNotBlank() }, child.cacheType)
        }
        sb.append("</gpx>\n")
        return sb.toString()
    }

    /**
     * Kopf eines Caches samt `groundspeak:cache`.
     *
     * Ohne diesen Block gingen beim Export → Import die D/T-Wertung, der Hint,
     * die Archiv-Markierung und der Konstellations-Typ verloren – der Import
     * liest sie genau hier (siehe `parseGroundspeakCache`).
     */
    private fun appendCacheHead(sb: StringBuilder, head: Waypoint, headName: String) {
        sb.append("  <wpt lat=\"")
            .append(String.format(Locale.US, "%.6f", head.latitude))
            .append("\" lon=\"")
            .append(String.format(Locale.US, "%.6f", head.longitude))
            .append("\">\n")
        sb.append("    <name>").append(escapeXml(headName)).append("</name>\n")
        sb.append("    <desc>").append(escapeXml(head.name)).append("</desc>\n")
        val type = head.cacheType?.takeIf { it.isNotBlank() }
        if (type != null) {
            sb.append("    <type>Geocache|").append(escapeXml(type)).append("</type>\n")
        }

        val hint = head.hint?.takeIf { it.isNotBlank() }
        if (type != null || hint != null || head.difficulty != null ||
            head.terrain != null || head.archived
        ) {
            sb.append("    <groundspeak:cache archived=\"")
                .append(if (head.archived) "true" else "false")
                .append("\" xmlns:groundspeak=\"http://www.groundspeak.com/cache/1/0/1\">\n")
            sb.append("      <groundspeak:name>")
                .append(escapeXml(head.name))
                .append("</groundspeak:name>\n")
            if (type != null) {
                sb.append("      <groundspeak:type>")
                    .append(escapeXml(type))
                    .append("</groundspeak:type>\n")
            }
            head.difficulty?.let {
                sb.append("      <groundspeak:difficulty>")
                    .append(String.format(Locale.US, "%.1f", it))
                    .append("</groundspeak:difficulty>\n")
            }
            head.terrain?.let {
                sb.append("      <groundspeak:terrain>")
                    .append(String.format(Locale.US, "%.1f", it))
                    .append("</groundspeak:terrain>\n")
            }
            if (hint != null) {
                sb.append("      <groundspeak:encoded_hints>")
                    .append(escapeXml(hint))
                    .append("</groundspeak:encoded_hints>\n")
            }
            sb.append("    </groundspeak:cache>\n")
        }
        sb.append("  </wpt>\n")
    }

    private fun toFreeWaypointGpx(wp: Waypoint): String {
        val sb = StringBuilder()
        appendGpxHeader(sb)
        appendWpt(sb, wp.latitude, wp.longitude, wp.name, null, null)
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun appendGpxHeader(sb: StringBuilder) {
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"GPS Satelliten\" xmlns=\"http://www.topografix.com/GPX/1/1\">\n")
    }

    private fun appendWpt(sb: StringBuilder, lat: Double, lon: Double, name: String, desc: String?, type: String?) {
        sb.append("  <wpt lat=\"")
            .append(String.format(Locale.US, "%.6f", lat))
            .append("\" lon=\"")
            .append(String.format(Locale.US, "%.6f", lon))
            .append("\">\n")
        sb.append("    <name>").append(escapeXml(name)).append("</name>\n")
        if (!desc.isNullOrBlank()) sb.append("    <desc>").append(escapeXml(desc)).append("</desc>\n")
        if (!type.isNullOrBlank()) sb.append("    <type>").append(escapeXml(type)).append("</type>\n")
        sb.append("  </wpt>\n")
    }

    private fun sanitizeFileName(name: String): String =
        name.replace(Regex("[^A-Za-z0-9._-]"), "_").trim('_').ifBlank { "export" }

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

    private fun parseJson(text: String, fallbackWaypointName: String): ImportResult {
        return try {
            jsonArrayToWaypoints(JSONArray(text), fallbackWaypointName)
        } catch (e: Exception) {
            try {
                val obj = JSONObject(text)
                val arr = obj.optJSONArray("waypoints")
                if (arr != null) jsonArrayToWaypoints(arr, fallbackWaypointName)
                else ImportResult(emptyList(), emptyList(), emptyList(), emptyList())
            } catch (e2: Exception) {
                ImportResult(emptyList(), emptyList(), emptyList(), listOf(SkipReason.InvalidJson))
            }
        }
    }

    private fun jsonArrayToWaypoints(arr: JSONArray, fallbackWaypointName: String): ImportResult {
        val waypoints = mutableListOf<Waypoint>()
        val skipped = mutableListOf<SkipReason>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i)
            if (o == null) {
                skipped.add(SkipReason.JsonEntryNotObject(i + 1))
                continue
            }
            val lat = o.optDouble("lat", Double.NaN)
            val lon = o.optDouble("lon", Double.NaN)
            val name = o.optString("name", "").ifBlank { fallbackWaypointName }
            if (lat.isNaN() || lon.isNaN() || lat !in -90.0..90.0 || lon !in -180.0..180.0) {
                skipped.add(SkipReason.JsonWaypointWithoutCoordinates(name))
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
                    hint = normalizeHint(o.optString("hint", "")),
                    difficulty = if (o.has("difficulty")) o.optDouble("difficulty") else null,
                    terrain = if (o.has("terrain")) o.optDouble("terrain") else null,
                    cacheType = o.optString("cacheType", "").ifBlank { null },
                    found = o.optBoolean("found", false)
                )
            )
        }
        val grouping = groupWaypoints(waypoints)
        return ImportResult(grouping.caches, grouping.freeWaypoints, emptyList(), skipped)
    }
}

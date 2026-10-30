package de.gpsradar.satellites.model

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Speichert Tracks als **eine Datei pro Track** in [dir].
 *
 * Früher lag alles in einem einzigen JSON-String in den Einstellungen: jeder
 * neue Punkt eines Tracks schrieb alle Tracks neu. Hier wird beim Ändern nur
 * die betroffene Datei geschrieben. Die Reihenfolge der Tracks (und damit die
 * Anzeige-Reihenfolge) steht als kleiner Index über [readIndex]/[writeIndex] –
 * im Betrieb sind das zwei Zugriffe auf die Einstellungen.
 *
 * Dateiname: `track_<id>.json`, Inhalt ein einzelnes Track-Objekt. Geschrieben
 * wird über eine Zwischendatei, damit ein Abbruch (Akku leer, Prozess beendet)
 * keine halbe Datei hinterlässt.
 *
 * Bewusst schlicht gehalten (keine Coroutinen, kein Caching): die Dateien sind
 * klein, die Aufrufe sind selten, und ein klarer Ablauf ist hier mehr wert als
 * Ersparnis.
 */
class TrackStore(
    private val dir: File,
    private val readIndex: () -> String?,
    private val writeIndex: (String) -> Unit,
    private val nowMillis: () -> Long = System::currentTimeMillis
) {

    init {
        dir.mkdirs()
    }

    /** Alle Tracks in Index-Reihenfolge. Unlesbare Dateien werden übersprungen. */
    fun loadAll(): List<Track> {
        return readIds().mapNotNull { id -> readTrack(id) }
    }

    /**
     * Legt [track] neu an und gibt ihn mit der endgültigen Kennung zurück.
     * Ist die Kennung schon vergeben, wird eine freie gewählt – sonst würde
     * die neue Datei einen bestehenden Track überschreiben.
     */
    fun add(track: Track): Track {
        val ids = readIds().toMutableList()
        val stored = if (track.id in ids) track.copy(id = nextFreeId(ids)) else track
        writeTrack(stored)
        ids.add(stored.id)
        writeIds(ids)
        return stored
    }

    /** Schreibt einen bestehenden Track neu (z. B. Richtung umgedreht). */
    fun update(track: Track) {
        // Die Datei enthält die Kennung, der Dateiname ebenso: ein Track mit
        // neuer Kennung wäre ein anderer Track, also über [add] anlegen.
        if (track.id !in readIds()) {
            add(track)
            return
        }
        writeTrack(track)
    }

    fun delete(id: Long) {
        fileFor(id).delete()
        writeIds(readIds().filterNot { it == id })
    }

    /**
     * Übernimmt die alte Sammel-Ablage (`tracks` als ein JSON-String) in
     * Einzeldateien. Läuft nur, wenn noch kein Index existiert; [onDone] wird
     * erst nach vollständigem Erfolg aufgerufen und darf dann die alte Ablage
     * löschen. Bei unlesbarem Inhalt bleibt die alte Ablage unangetastet.
     */
    fun migrateLegacy(legacyJson: String?, onDone: () -> Unit) {
        if (legacyJson == null) return
        if (readIndex() != null) {
            // Schon migriert – alte Ablage nur noch aufräumen.
            onDone()
            return
        }
        val legacyTracks = parseLegacy(legacyJson) ?: return
        val ids = mutableListOf<Long>()
        for (legacy in legacyTracks) {
            val stored = if (legacy.id in ids) legacy.copy(id = nextFreeId(ids)) else legacy
            if (!writeTrack(stored)) return
            ids.add(stored.id)
        }
        writeIds(ids)
        onDone()
    }

    // ---- Dateien ----

    private fun fileFor(id: Long) = File(dir, "track_$id.json")

    private fun readTrack(id: Long): Track? {
        val file = fileFor(id)
        if (!file.isFile) return null
        return try {
            decodeTrack(JSONObject(file.readText()))
        } catch (e: Exception) {
            null // defekte Datei überspringen, die übrigen bleiben lesbar
        }
    }

    private fun writeTrack(track: Track): Boolean {
        val target = fileFor(track.id)
        val temp = File(dir, "${target.name}.tmp")
        return try {
            temp.writeText(encodeTrack(track))
            temp.renameTo(target)
        } catch (e: Exception) {
            temp.delete()
            false
        }
    }

    // ---- Index ----

    private fun readIds(): List<Long> {
        val raw = readIndex() ?: return emptyList()
        return try {
            val arr = JSONObject(raw).getJSONArray("ids")
            (0 until arr.length()).map { arr.getLong(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun writeIds(ids: List<Long>) {
        val arr = JSONArray()
        ids.forEach { arr.put(it) }
        writeIndex(JSONObject().put("ids", arr).toString())
    }

    private fun nextFreeId(taken: List<Long>): Long {
        var candidate = nowMillis()
        while (candidate in taken || fileFor(candidate).exists()) candidate++
        return candidate
    }

    // ---- JSON ----

    private fun encodeTrack(track: Track): String {
        val pts = JSONArray()
        track.points.forEach { p ->
            pts.put(
                JSONObject().apply {
                    put("lat", p.latitude)
                    put("lon", p.longitude)
                    p.elevationMeters?.let { put("ele", it) }
                    p.timeMillis?.let { put("time", it) }
                }
            )
        }
        return JSONObject().apply {
            put("id", track.id)
            put("name", track.name)
            put("points", pts)
            put("reversed", track.reversed)
        }.toString()
    }

    private fun decodeTrack(o: JSONObject): Track {
        val ptsArr = o.getJSONArray("points")
        val points = (0 until ptsArr.length()).map { i ->
            val p = ptsArr.getJSONObject(i)
            TrackPoint(
                latitude = p.getDouble("lat"),
                longitude = p.getDouble("lon"),
                elevationMeters = if (p.has("ele")) p.getDouble("ele") else null,
                timeMillis = if (p.has("time")) p.getLong("time") else null
            )
        }
        return Track(
            id = o.getLong("id"),
            name = o.getString("name"),
            points = points,
            reversed = o.optBoolean("reversed", false)
        )
    }

    private fun parseLegacy(raw: String): List<Track>? {
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map { decodeTrack(arr.getJSONObject(it)) }
        } catch (e: Exception) {
            null
        }
    }
}

package de.gpsradar.satellites.ui

import android.content.Context
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.ImportResult
import de.gpsradar.satellites.model.SkipReason

/**
 * Übersetzung der Import-Meldungen.
 *
 * Die Texte liegen bewusst hier und nicht im Modell: `WaypointCodec` bleibt
 * damit frei von Android-Abhängigkeiten und sprachunabhängig testbar.
 */

/** Übersetzt einen Grund, warum ein Eintrag übersprungen wurde. */
fun SkipReason.describe(context: Context): String = when (this) {
    SkipReason.GpxUnreadable -> context.getString(R.string.import_err_gpx_unreadable)

    is SkipReason.InvalidXml -> context.getString(
        R.string.import_err_invalid_xml,
        detail ?: context.getString(R.string.import_err_unknown)
    )

    SkipReason.WaypointWithoutCoordinates -> context.getString(R.string.import_skip_waypoint_no_coords)

    SkipReason.TrackPointWithoutCoordinates -> context.getString(R.string.import_skip_trackpoint_no_coords)

    is SkipReason.TrackTooShort -> context.getString(R.string.import_skip_track_too_short, name)

    SkipReason.InvalidJson -> context.getString(R.string.import_err_invalid_json)

    is SkipReason.JsonEntryNotObject -> context.getString(R.string.import_skip_json_entry_not_object, index)

    is SkipReason.JsonWaypointWithoutCoordinates ->
        context.getString(R.string.import_skip_json_waypoint_no_coords, name)

    SkipReason.FileUnreadable -> context.getString(R.string.import_err_file_unreadable)

    SkipReason.ArchiveUnreadable -> context.getString(R.string.import_err_archive_unreadable)
}

/** Fasst die übernommenen Einträge zusammen, z. B. „1 Cache und 7 Zielpunkte". */
private fun ImportResult.importedParts(context: Context): String {
    val res = context.resources
    return buildList {
        if (caches.isNotEmpty()) {
            add(res.getQuantityString(R.plurals.import_part_caches, caches.size, caches.size))
        }
        if (freeWaypoints.isNotEmpty()) {
            add(res.getQuantityString(R.plurals.import_part_waypoints, freeWaypoints.size, freeWaypoints.size))
        }
        if (tracks.isNotEmpty()) {
            add(res.getQuantityString(R.plurals.import_part_tracks, tracks.size, tracks.size))
        }
    }.joinToString(context.getString(R.string.import_join_and))
}

/**
 * Nutzerlesbare Zusammenfassung eines Imports (Toast).
 *
 * Kurz halten: ein Toast zeigt nur zwei Zeilen. Die Begründung für
 * übersprungene Einträge steht deshalb in [reportLines] und wird im Dialog
 * gezeigt.
 */
fun ImportResult.toMessage(context: Context): String {
    if (caches.isEmpty() && freeWaypoints.isEmpty() && tracks.isEmpty()) {
        return when {
            skipped.isEmpty() -> context.getString(R.string.import_summary_empty)
            skipped.size == 1 -> skipped[0].describe(context)
            else -> context.getString(R.string.import_summary_none_valid, skipped.size)
        }
    }

    val base = context.getString(
        R.string.import_summary_imported,
        importedParts(context)
    )
    return if (skipped.isEmpty()) {
        base
    } else {
        context.getString(R.string.import_summary_with_skipped, base, skipped.size)
    }
}

/**
 * Zeilen für den Import-Dialog: zuerst die Zusammenfassung, dann je
 * übersprungenem Grund eine Zeile mit Anzahl. Mehrere gleiche Gründe werden
 * gebündelt, damit die Liste auch bei großen Dateien kurz bleibt.
 *
 * Nur sinnvoll, wenn [ImportResult.skipped] nicht leer ist.
 */
fun ImportResult.reportLines(context: Context): List<String> {
    val summary = if (caches.isEmpty() && freeWaypoints.isEmpty() && tracks.isEmpty()) {
        context.getString(R.string.import_summary_empty)
    } else {
        context.getString(R.string.import_summary_imported, importedParts(context))
    }

    val lines = mutableListOf(summary)
    skipped.map { it.describe(context) }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .forEach { (reason, count) ->
            lines += if (count == 1) {
                reason
            } else {
                context.getString(R.string.import_reason_repeat, reason, count)
            }
        }
    return lines
}

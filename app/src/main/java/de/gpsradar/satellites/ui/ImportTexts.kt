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
}

/** Nutzerlesbare Zusammenfassung eines Imports (Toast). */
fun ImportResult.toMessage(context: Context): String {
    val res = context.resources

    if (caches.isEmpty() && freeWaypoints.isEmpty() && tracks.isEmpty()) {
        return when {
            skipped.isEmpty() -> context.getString(R.string.import_summary_empty)
            skipped.size == 1 -> skipped[0].describe(context)
            else -> context.getString(R.string.import_summary_none_valid, skipped.size)
        }
    }

    val parts = buildList {
        if (caches.isNotEmpty()) {
            add(res.getQuantityString(R.plurals.import_part_caches, caches.size, caches.size))
        }
        if (freeWaypoints.isNotEmpty()) {
            add(res.getQuantityString(R.plurals.import_part_waypoints, freeWaypoints.size, freeWaypoints.size))
        }
        if (tracks.isNotEmpty()) {
            add(res.getQuantityString(R.plurals.import_part_tracks, tracks.size, tracks.size))
        }
    }

    val base = context.getString(
        R.string.import_summary_imported,
        parts.joinToString(context.getString(R.string.import_join_and))
    )
    return if (skipped.isEmpty()) {
        base
    } else {
        context.getString(R.string.import_summary_with_skipped, base, skipped.size)
    }
}

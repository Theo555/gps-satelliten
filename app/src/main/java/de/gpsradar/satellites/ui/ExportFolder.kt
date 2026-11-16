package de.gpsradar.satellites.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.documentfile.provider.DocumentFile

/**
 * Merkt sich den Ordner, in den exportiert wird.
 *
 * Ohne das fragt Android bei **jedem** Export erneut „Zugriff auf Ordner
 * erlauben?": Die Freigabe aus der Ordnerauswahl gilt nur vorübergehend, und
 * ohne gemerkten Ordner muss die App jedes Mal neu auswählen lassen.
 *
 * Mit [merken] wird die Freigabe dauerhaft übernommen
 * (`takePersistableUriPermission`) und der Ordner abgelegt. Danach schreibt die
 * App direkt hinein. Wird der Ordner gelöscht oder die Freigabe entzogen,
 * scheitert das Schreiben – dann wird vergessen und neu gefragt.
 */
object ExportFolder {

    /**
     * Unterordner im gewählten Exportziel.
     *
     * Bewusst Unterordner und **kein** fest verdrahteter Pfad wie
     * `Download/gps-satelliten`: Ein fester Pfad ginge nur über MediaStore
     * (ab Android 10) oder über die Speicherberechtigung (bis Android 9) — also
     * neue Rechte und zwei Codezweige. Ein Unterordner im selbst gewählten
     * Ziel kann jeder Anbieter ab Android 7, ganz ohne Berechtigung.
     *
     * Der Nutzer wählt weiterhin den Ort (Download, SD-Karte, eigener Ordner);
     * die App sortiert darin nur.
     */
    const val MESSUNGEN = "messungen"

    /** Navigationsdateien (RINEX) — liegen neben, nicht zwischen den Messungen. */
    const val NAVIGATION = "navigation"

    /** Später: aufgezeichnete Tracks, sobald der Export dorthin wandert. */
    const val TRACKS = "tracks"

    private const val PREFS = "export"
    private const val KEY_URI = "ordner_uri"
    private const val KEY_NAME = "ordner_name"

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Gemerkter Ordner, null wenn noch keiner gewählt wurde. */
    fun gespeichert(context: Context): Uri? =
        prefs(context).getString(KEY_URI, null)?.let(Uri::parse)

    /** Anzeigename des gemerkten Ordners (z. B. „GPS"), sonst null. */
    fun name(context: Context): String? = prefs(context).getString(KEY_NAME, null)

    /**
     * Ordner samt Anzeigename ablegen und die Freigabe dauerhaft übernehmen.
     *
     * @return true, wenn die Freigabe übernommen werden konnte
     */
    fun merken(context: Context, uri: Uri): Boolean {
        val resolver = context.applicationContext.contentResolver
        val dauerhaft = try {
            resolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            true
        } catch (e: SecurityException) {
            // Manche Anbieter geben keine dauerhafte Freigabe – dann bleibt es
            // beim Fragen, das Schreiben funktioniert aber trotzdem.
            false
        }
        prefs(context).edit()
            .putString(KEY_URI, uri.toString())
            .putString(
                KEY_NAME,
                DocumentFile.fromTreeUri(context.applicationContext, uri)?.name
            )
            .apply()
        return dauerhaft
    }

    /** Ordner vergessen (Freigabe aufgeben). */
    fun vergessen(context: Context) {
        val uri = gespeichert(context) ?: return
        try {
            context.applicationContext.contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            // War nie dauerhaft freigegeben – nichts zu tun.
        }
        prefs(context).edit().remove(KEY_URI).remove(KEY_NAME).apply()
    }

    /** Ist der Ordner noch da und beschreibbar? */
    fun schreibbar(context: Context, uri: Uri): Boolean =
        try {
            DocumentFile.fromTreeUri(context.applicationContext, uri)?.canWrite() == true
        } catch (e: Exception) {
            false
        }
}

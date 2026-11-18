package de.gpsradar.satellites.io

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import de.gpsradar.satellites.model.WaypointCodec.GpxExportFile

/**
 * Der einzige Ort, der den Systemdialog für Dateien kennt (Storage Access
 * Framework: `DocumentFile`, `ContentResolver`, `Uri`).
 *
 * Bis zum 18.11.2026 lagen diese Aufrufe im `GpsViewModel`. Dort waren sie am
 * falschen Platz: Als einziges Stück der App brauchten sie einen
 * Android-Kontext und ließen sich deshalb nur mit Robolectric prüfen, während
 * der Rest des ViewModels Zustandsverwaltung ist. Inhalt und Ablage sind jetzt
 * getrennt — den Inhalt bauen die Codierer im `model`-Paket, hier wird er nur
 * noch geschrieben.
 *
 * Alle Funktionen schlucken Ausnahmen und melden den Erfolg als Rückgabewert:
 * Ein fehlgeschlagener Export darf die App nicht beenden, und der Aufrufer
 * zeigt dem Nutzer eine Meldung.
 */
object ExportFiles {

    private const val TAG = "ExportFiles"

    /** Datei vollständig lesen; `null`, wenn sie sich nicht öffnen lässt. */
    fun readBytes(context: Context, uri: Uri): ByteArray? = try {
        context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
    } catch (e: Exception) {
        Log.w(TAG, "Lesen fehlgeschlagen", e)
        null
    }

    /** Text in eine bereits gewählte Datei schreiben (Einzeldatei-Export). */
    fun writeText(context: Context, uri: Uri, text: String): Boolean = try {
        context.contentResolver.openOutputStream(uri)?.use {
            it.write(text.toByteArray(Charsets.UTF_8))
        } != null
    } catch (e: Exception) {
        Log.w(TAG, "Schreiben fehlgeschlagen", e)
        false
    }

    /**
     * Eine Datei in einem vom Nutzer freigegebenen Ordner anlegen und füllen.
     *
     * [subfolder] wird im Ziel angelegt, falls er fehlt — so liegen Messungen
     * und Navigationsdateien getrennt, ohne dass der Nutzer selbst sortieren
     * muss. Ist `subfolder` null, wird direkt ins Ziel geschrieben.
     */
    fun writeInFolder(
        context: Context,
        treeUri: Uri,
        subfolder: String?,
        fileName: String,
        mimeType: String,
        text: String
    ): Boolean {
        return try {
            val ziel = DocumentFile.fromTreeUri(context, treeUri) ?: return false
            val ablage = if (subfolder == null) {
                ziel
            } else {
                ziel.findFile(subfolder) ?: ziel.createDirectory(subfolder)
            } ?: return false
            val datei = ablage.findFile(fileName) ?: ablage.createFile(mimeType, fileName)
            if (datei == null) return false
            context.contentResolver.openOutputStream(datei.uri)?.use {
                it.write(text.toByteArray(Charsets.UTF_8))
            } != null
        } catch (e: Exception) {
            Log.w(TAG, "Schreiben von $fileName fehlgeschlagen", e)
            false
        }
    }

    /**
     * Mehrere GPX-Dateien in einen Ordner legen (je Zielpunkt eine).
     *
     * Gibt die Anzahl der geschriebenen Dateien zurück; einzelne Fehlschläge
     * brechen den Durchlauf nicht ab.
     */
    fun writeGpxFiles(
        context: Context,
        treeUri: Uri,
        files: List<GpxExportFile>
    ): Int = try {
        val root = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        var written = 0
        files.forEach { f ->
            val datei = root.createFile("application/gpx+xml", f.fileName) ?: return@forEach
            context.contentResolver.openOutputStream(datei.uri)?.use {
                it.write(f.content.toByteArray(Charsets.UTF_8))
            }
            written++
        }
        written
    } catch (e: Exception) {
        Log.w(TAG, "GPX-Export fehlgeschlagen", e)
        0
    }
}

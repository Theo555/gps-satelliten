package de.gpsradar.satellites

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** Name und Versionsnummer der laufenden App. */
data class AppVersion(val name: String, val code: Long)

/**
 * Liest die Versionsangaben — die Abfrage hat sich mit API 33 geändert.
 *
 * Ab Android 13 verlangt [PackageManager.getPackageInfo] die Flags als
 * [PackageManager.PackageInfoFlags]; die alte Variante mit einer Ganzzahl ist
 * abgekündigt. Weil die App ab Android 7 läuft, bleibt der alte Aufruf als
 * Zweig für ältere Geräte stehen — er ist dort der einzige Weg.
 */
@Suppress("DEPRECATION")
fun readAppVersion(context: Context): AppVersion = try {
    val pm = context.packageManager
    val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        pm.getPackageInfo(context.packageName, 0)
    }
    AppVersion(
        name = info.versionName ?: "?",
        code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode
        else info.versionCode.toLong()
    )
} catch (e: PackageManager.NameNotFoundException) {
    AppVersion("?", 0L)
}

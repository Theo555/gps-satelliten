package de.gpsradar.satellites.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Zeitstempel für Dateinamen beim Export.
 *
 * Anlass (12.11.2026, Rückmeldung aus dem Feldtest): Jeder Export hieß gleich
 * (`rohdaten_universal.csv`), der Dateidialog hängte deshalb ein „(1)", „(2)" an.
 * Aus dem Namen war danach nicht mehr abzulesen, wann die Aufnahme entstanden
 * ist – dabei steht genau das in der Datei selbst, nur eben erst nach dem
 * Öffnen.
 *
 * Sekunden statt Minuten: Zwei Exporte in derselben Minute sind keine Seltenheit
 * (erst universell, dann Excel), und beide sollen unterscheidbar sein.
 *
 * `Locale.US` ist Absicht – in einem Dateinamen haben lokalisierte Ziffern
 * nichts verloren, und die Sortierung soll überall gleich aussehen.
 */
object ExportNames {

    private const val PATTERN = "yyyyMMdd_HHmmss"

    /** Gerätezeit als `20261112_105712`. */
    fun timestamp(at: Date = Date()): String =
        SimpleDateFormat(PATTERN, Locale.US).format(at)
}

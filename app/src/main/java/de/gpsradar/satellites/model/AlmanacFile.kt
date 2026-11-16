package de.gpsradar.satellites.model

import java.util.Locale

/**
 * Schreibt die Almanach-Sätze als Textdatei.
 *
 * ## Warum kein RINEX
 *
 * Die RINEX-Navigationsdatei enthält ausschließlich Ephemeriden; einen
 * Almanach-Satz kennt das Format nicht (auch RINEX 3.04 nicht). Die Sätze
 * bekommen deshalb eine eigene Datei daneben — dieselbe Wahl wie beim CSV, das
 * ebenfalls neben der Navigationsdatei liegt.
 *
 * ## Format
 *
 * Kommentarzeilen mit `#`, danach eine Zeile je Satz mit fester Spaltenfolge.
 * Einheiten wie in [Almanac] beschrieben; Winkel in Grad, weil sie so lesbar
 * sind, `sqrtA` in √m und `af0`/`af1` in s bzw. s/s wie im Signal.
 */
object AlmanacFile {

    /** Spaltenüberschrift der Wertzeilen. */
    private const val SPALTEN = "system svid woche toa_s sqrtA_m e i0_deg " +
        "Omega0_deg omega_deg M0_deg Omega_dot_deg_s af0_s af1_s_ss health"

    /**
     * Dateiinhalt erzeugen.
     *
     * [kopf] sind zusätzliche Kommentarzeilen ohne `#` — der Aufrufer setzt dort
     * Gerät, Zeitpunkt und Herkunft hinein, so wie es die CSV auch macht.
     */
    fun write(almanac: List<Almanac>, kopf: List<String> = emptyList()): String {
        val sb = StringBuilder()
        sb.append("# Almanach aus den Navigationsnachrichten\n")
        sb.append("# Wozu: grobe Bahn und Uhr eines Satelliten ueber Wochen.\n")
        sb.append("# Die Nachverarbeitung (RINEX/RTKLIB) rechnet damit NICHT -\n")
        sb.append("# sie braucht Ephemeriden, und RINEX kennt keinen Almanach.\n")
        for (zeile in kopf) sb.append("# ").append(zeile).append('\n')
        sb.append("# spalten: ").append(SPALTEN).append('\n')
        for (a in almanac) {
            sb.append(String.format(Locale.US, "%-3s %2d %4d %6d %10.4f %.9f ",
                a.system, a.svid, a.week, a.referenceSeconds, a.sqrtA, a.eccentricity))
            sb.append(String.format(Locale.US, "%9.4f %10.4f %9.4f %9.4f %+.6e ",
                grad(a.inclination), grad(a.ascendingNode), grad(a.argumentOfPerigee),
                grad(a.meanAnomaly), grad(a.nodeRate)))
            sb.append(String.format(Locale.US, "%+.4e %+.4e %d\n",
                a.clockBias, a.clockRate, a.health))
        }
        return sb.toString()
    }

    private fun grad(rad: Double): Double = rad * 180.0 / Math.PI
}

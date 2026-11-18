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
     * GLONASS hat eine eigene Spaltenfolge: keine Halbachse, kein M0, dafür
     * Knotenzeit und Draconische Periode. Die Felder sind die der ICD, nur in
     * Grad statt Halbkreisen — so sind sie ohne Umrechnung lesbar.
     */
    private const val SPALTEN_GLO = "system svid t_lambda_s lambda_deg di_deg " +
        "T_s T_dot_s tau_n_s Hn Mn health"

    /**
     * Dateiinhalt erzeugen.
     *
     * [kopf] sind zusätzliche Kommentarzeilen ohne `#` — der Aufrufer setzt dort
     * Gerät, Zeitpunkt und Herkunft hinein, so wie es die CSV auch macht.
     */
    fun write(
        almanac: List<Almanac>,
        kopf: List<String> = emptyList(),
        gloAlmanac: List<GloAlmanac> = emptyList()
    ): String {
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
        if (gloAlmanac.isNotEmpty()) {
            sb.append("#\n")
            sb.append("# GLONASS: eigener Satzaufbau (Knotenzeit und Draconische\n")
            sb.append("# Periode statt Halbachse und M0), deshalb eigene Spalten.\n")
            sb.append("# Der Bezugstag NA steht fuer alle Saetze in String 5.\n")
            sb.append("# spalten: ").append(SPALTEN_GLO).append('\n')
            for (a in gloAlmanac) {
                sb.append(
                    String.format(
                        Locale.US, "%-3s %2d %5s %9s %8s %8s %10s %12s %3s %2s %s\n",
                        "GLO",
                        a.svid ?: 0,
                        a.nodeSeconds?.toString() ?: "-",
                        a.longitude?.let { zahl(grad(it), "%.4f") } ?: "-",
                        a.inclinationCorrection?.let { zahl(grad(it), "%.4f") } ?: "-",
                        a.draconianPeriodSeconds?.let { zahl(it, "%.0f") } ?: "-",
                        a.draconianPeriodRate?.let { zahl(it, "%+.3e") } ?: "-",
                        a.clockCorrection?.let { zahl(it, "%+.4e") } ?: "-",
                        a.frequencyChannel?.toString() ?: "-",
                        a.satelliteType?.toString() ?: "-",
                        a.health?.toString() ?: "-"
                    )
                )
            }
        }
        return sb.toString()
    }

    private fun zahl(wert: Double, muster: String): String =
        String.format(Locale.US, muster, wert)

    private fun grad(rad: Double): Double = rad * 180.0 / Math.PI
}

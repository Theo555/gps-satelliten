package de.gpsradar.satellites.model

/**
 * Systemweite Korrekturgrößen aus den Navigationsnachrichten.
 *
 * Anders als die Ephemeriden gelten diese Werte **nicht je Satellit**, sondern
 * für das ganze System: Alle Satelliten eines Systems senden dieselben
 * Koeffizienten. Sie gehören deshalb nicht in die Satzliste, sondern in den
 * Kopf der RINEX-Navigationsdatei.
 *
 * ## Wozu
 *
 * Ohne Ionosphärenmodell bleibt in der Einzelpunktlösung ein Fehler von 5 bis
 * 15 m (2026 nahe dem Sonnenmaximum). RTKLIB rechnet das Klobuchar-Modell nur,
 * wenn die Koeffizienten in der Datei stehen; beim BRDC sind sie im Kopf
 * enthalten, bei einer Datei aus eigenen Rohdaten bisher nicht.
 *
 * Der UTC-Bezug liefert die geltende Schaltsekunde. Ohne ihn müsste die App auf
 * den Gerätewert zurückfallen — und der ist auf manchen Geräten unbrauchbar
 * (das S20 FE meldet `Int.MIN_VALUE`).
 */

/**
 * Klobuchar-Koeffizienten von GPS und BeiDou.
 *
 * `alpha` in Sekunden, `beta` in Sekunden (Basiseinheiten der jeweiligen
 * Skalierung). Die Reihenfolge ist immer α₀…α₃, β₀…β₃.
 */
data class KlobucharIono(
    val alpha: List<Double>,
    val beta: List<Double>
) {
    /** Brauchbar, wenn alle acht Werte vorliegen und nicht alles null ist. */
    val isUsable: Boolean
        get() = alpha.size == 4 && beta.size == 4 &&
            (alpha + beta).any { it != 0.0 }
}

/**
 * Zeitbezug eines Systems zu UTC.
 *
 * Die RINEX-Kopfzeile `TIME SYSTEM CORR` erwartet A₀, A₁, die Bezugszeit und
 * die Bezugswoche. Welche Zeitbasis die Bezugszeit hat, ist je System
 * verschieden: GPS und Galileo zählen Stunden der Woche, BeiDou meldet keine
 * eigene Bezugszeit.
 */
data class UtcCorrection(
    /** Koeffizient in Sekunden. */
    val a0: Double,
    /** Koeffizient in Sekunden je Sekunde. */
    val a1: Double,
    /** Geltende Schaltsekunde in Sekunden (derzeit 18). */
    val deltaLs: Int,
    /** Bezugszeit in Sekunden der Systemwoche; 0, wenn das System keine meldet. */
    val referenceSeconds: Int,
    /** Bezugswoche der UTC-Parameter. */
    val weekNumber: Int,
    /** Künftige Schaltsekunde. */
    val deltaLsf: Int,
    /** Woche, in der `deltaLsf` gilt. */
    val weekNumberLsf: Int,
    /** Tag der Woche (1 = Sonntag/Montag je System), an dem `deltaLsf` gilt. */
    val dayNumber: Int
) {
    /** Brauchbar, wenn ein von null verschiedener Bezug vorliegt. */
    val isUsable: Boolean
        get() = a0 != 0.0 || a1 != 0.0 || deltaLs != 0
}

/** Was der Empfänger an systemweiten Korrekturen gehört hat. */
data class NavCorrections(
    val gpsIono: KlobucharIono? = null,
    val gpsUtc: UtcCorrection? = null,
    val galIono: GalIonosphere? = null,
    val galUtc: UtcCorrection? = null,
    val bdsIono: KlobucharIono? = null,
    val bdsUtc: UtcCorrection? = null,
    /**
     * GLONASS zu UTC(SU): A₀ ist die Korrektur τc.
     *
     * Die GLONASS-Nachricht liefert dazu nur den Tag `NA`, auf den sich der
     * Wert bezieht, und mit τGPS auch den Abstand zu GPS — beides hat in der
     * RINEX-Zeile `GLUT` keinen Platz (dort stehen A₀, A₁, T, W wie bei den
     * anderen Systemen). T und W bleiben deshalb 0; wer τc zeitlich einordnen
     * muss, braucht `NA` aus derselben Meldung.
     */
    val gloUtc: UtcCorrection? = null
)

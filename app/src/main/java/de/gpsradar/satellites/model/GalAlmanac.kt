package de.gpsradar.satellites.model

import kotlin.math.PI
import kotlin.math.sqrt

/**
 * Ein Galileo-Almanach in Teilen.
 *
 * ## Warum in Teilen
 *
 * Anders als bei GPS und BeiDou passt ein Almanach nicht in ein Wort: Die
 * Bahnwerte eines Satelliten stehen in einem Wort, seine Uhr im nächsten.
 * I/NAV-Worttyp 7 liefert den vollständigen Bahnsatz für SVID1 (ohne Uhr),
 * Worttyp 8 die Uhr dazu und den Bahnsatz für SVID2. Deshalb sind die Felder
 * hier alle optional und werden mit [merge] zusammengeführt.
 *
 * ## Bezugswerte
 *
 * Zwei Felder sind **Differenzen**: Δ(√A) gegen √A_nominal und δi gegen
 * i_nominal (Galileo-ICD Tabelle 1: A = 29 600 000 m, i = 56°, e = 0).
 * Erst [inclination] und [sqrtA] sind absolute Werte.
 *
 * ## Herkunft
 *
 * Galileo OS SIS ICD v2.1, Tabellen 46–49 (Worttypen 7–10) und Tabelle 84
 * (Skalen). Nicht aus RTKLIB.
 */
data class GalAlmanac(
    /** Satellit, dessen Almanach dieser Teil gehört. */
    val svid: Int,
    val iodA: Int,
    /** Die zwei niederwertigsten Bit der GST-Woche (Feld WN_a). */
    val weekBits: Int? = null,
    /** Bezugszeit t_oa in Sekunden der Galileo-Woche (Feld zählt in 600-s-Schritten). */
    val referenceSeconds: Int? = null,
    /** Wurzel der großen Halbachse (√m), bereits absolut. */
    val sqrtA: Double? = null,
    val eccentricity: Double? = null,
    /** Bahnneigung absolut (Radiant) — δi plus Nominalwert. */
    val inclination: Double? = null,
    val ascendingNode: Double? = null,
    val argumentOfPerigee: Double? = null,
    val meanAnomaly: Double? = null,
    val nodeRate: Double? = null,
    val clockBias: Double? = null,
    val clockRate: Double? = null,
    /** Signalgesundheit für E5b/E1-B, je 2 Bit; -1 = in diesem Wort nicht enthalten. */
    val health: Int? = null
) {

    /** Fehlt noch etwas für einen vollständigen Satz? */
    val isComplete: Boolean
        get() = svid > 0 && referenceSeconds != null && sqrtA != null &&
            eccentricity != null && inclination != null && ascendingNode != null &&
            argumentOfPerigee != null && meanAnomaly != null && nodeRate != null &&
            clockBias != null && clockRate != null

    /**
     * Werte des anderen Teils ergänzen, eigene behalten.
     *
     * Der eigene Satz ist der ältere Stand; fehlende Felder kommen hinzu.
     */
    fun merge(other: GalAlmanac): GalAlmanac = copy(
        referenceSeconds = referenceSeconds ?: other.referenceSeconds,
        weekBits = weekBits ?: other.weekBits,
        sqrtA = sqrtA ?: other.sqrtA,
        eccentricity = eccentricity ?: other.eccentricity,
        inclination = inclination ?: other.inclination,
        ascendingNode = ascendingNode ?: other.ascendingNode,
        argumentOfPerigee = argumentOfPerigee ?: other.argumentOfPerigee,
        meanAnomaly = meanAnomaly ?: other.meanAnomaly,
        nodeRate = nodeRate ?: other.nodeRate,
        clockBias = clockBias ?: other.clockBias,
        clockRate = clockRate ?: other.clockRate,
        health = health ?: other.health
    )

    /**
     * In den gemeinsamen Almanach-Typ überführen — nur wenn vollständig.
     *
     * [week] muss der Aufrufer aus der Empfangszeit ableiten; im Signal stehen
     * davon nur die zwei niederwertigsten Bit ([weekBits]).
     */
    fun toAlmanac(week: Int): Almanac? {
        if (!isComplete) return null
        return Almanac(
            system = "GAL",
            svid = svid,
            week = week,
            referenceSeconds = referenceSeconds ?: return null,
            sqrtA = sqrtA ?: return null,
            eccentricity = eccentricity ?: return null,
            inclination = inclination ?: return null,
            ascendingNode = ascendingNode ?: return null,
            argumentOfPerigee = argumentOfPerigee ?: return null,
            meanAnomaly = meanAnomaly ?: return null,
            nodeRate = nodeRate ?: return null,
            clockBias = clockBias ?: return null,
            clockRate = clockRate ?: return null,
            health = health ?: HEALTH_UNKNOWN
        )
    }

    companion object {
        /** A = 29 600 000 m (ICD Tabelle 1). */
        val SQRT_A_NOMINAL: Double = sqrt(29_600_000.0)

        /** i = 56° (ICD Tabelle 1). */
        val INCLINATION_NOMINAL: Double = 56.0 * PI / 180.0

        /** Worttyp 7/8/9/10 tragen keine nutzbare Gesundheitsangabe für uns. */
        const val HEALTH_UNKNOWN = -1
    }
}

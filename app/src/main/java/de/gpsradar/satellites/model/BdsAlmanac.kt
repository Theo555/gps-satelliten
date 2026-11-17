package de.gpsradar.satellites.model

import kotlin.math.PI

/**
 * Ein BeiDou-Almanach-Satz aus Unterrahmen 4 oder 5.
 *
 * ## Herkunft der Bitlagen
 *
 * Aus der Spezifikation (BDS-SIS-ICD-B1I 3.0, §5.2.4.13–15 und die Abbildung
 * „Bit allocation for subframe 4、5 (300 bits)"), nicht aus RTKLIB. Die
 * Abbildung ist die einzige verlässliche Quelle: `pdftotext` zerlegt ihr
 * Bitlineal in unbrauchbare Spalten, erst als Bild gerendert ist sie lesbar.
 *
 * **Der Knoten:** Wort 1 trägt 26 Datenbits und 4 Paritätsbits, die Wörter 2–10
 * dagegen nur 22 Datenbits und **8** Paritätsbits. Wer gleichmäßig 26+4
 * annimmt, verschiebt alles ab Bit 53 — daher rührte der frühere „3-Bit-Versatz".
 *
 * ## Bezugsiniklination
 *
 * Der Satz trägt nur δi, die Abweichung von einem Bezugswert. Laut ICD §5.2.4.15
 * gilt i₀ = 0,30 Halbkreise für MEO/IGSO und i₀ = 0 für GEO. Die absoluten
 * Bahnneigungen der beiden Gruppen liegen damit nahe 54° bzw. 0° —
 * [toAlmanac] rechnet um.
 */
data class BdsAlmanac(
    /** Satellit, dessen Almanach die Seite trägt (= Zielsatellit, nicht der Sender). */
    val svid: Int,
    /** Unterrahmen 4 oder 5 — die Seite kommt aus beiden. */
    val subframe: Int,
    /** Seitenkennung Pnum (Bit 44–50). */
    val page: Int,
    /** Wurzel der großen Halbachse (√m). */
    val sqrtA: Double,
    val eccentricity: Double,
    /** δi — Abweichung der Bahnneigung vom Bezugswert (Radiant). */
    val deltaInclination: Double,
    val ascendingNode: Double,
    val argumentOfPerigee: Double,
    val meanAnomaly: Double,
    /** Änderungsrate des aufsteigenden Knotens (rad/s). */
    val nodeRate: Double,
    /** Bezugszeit t_oa in Sekunden seit Beginn der BeiDou-Woche. */
    val referenceSeconds: Int,
    val clockBias: Double,
    val clockRate: Double
) {

    /**
     * Prüfungen, die ohne jede Referenzdatei auskommen.
     *
     * Die BeiDou-Konstellation hat zwei Bahnradien: GEO mit a ≈ 42 164 km
     * (√A ≈ 6493) und IGSO/MEO mit a ≈ 27 906 km (√A ≈ 5283). Eisenbahnen
     * dazwischen gibt es nicht — ein falscher Bitversatz trifft dieses Fenster
     * praktisch nie.
     */
    val isPlausible: Boolean
        get() = sqrtA in 5_000.0..7_000.0 &&
            eccentricity in 0.0..0.07 &&
            referenceSeconds in 0..604_800 &&
            referenceSeconds % 4096 == 0 &&
            kotlin.math.abs(deltaInclination) <= 0.2 &&
            kotlin.math.abs(ascendingNode) <= PI &&
            kotlin.math.abs(argumentOfPerigee) <= PI &&
            kotlin.math.abs(meanAnomaly) <= PI

    /** Geostationär? Nur dort gilt der Bezugswert i₀ = 0. */
    val isGeostationary: Boolean get() = svid in 1..5 || svid in 59..63

    /** Bahnneigung absolut (Radiant) — δi plus Bezugswert. */
    val inclination: Double
        get() = deltaInclination + if (isGeostationary) 0.0 else REFERENCE_INCLINATION

    /**
     * In den gemeinsamen Almanach-Typ überführen.
     *
     * [week] muss der Aufrufer setzen: Die Woche steht nicht auf der
     * Almanach-Seite, sondern nur auf der Gesundheitsseite des Rahmens.
     */
    fun toAlmanac(week: Int): Almanac = Almanac(
        system = "BDS",
        svid = svid,
        week = week,
        referenceSeconds = referenceSeconds,
        sqrtA = sqrtA,
        eccentricity = eccentricity,
        inclination = inclination,
        ascendingNode = ascendingNode,
        argumentOfPerigee = argumentOfPerigee,
        meanAnomaly = meanAnomaly,
        nodeRate = nodeRate,
        clockBias = clockBias,
        clockRate = clockRate,
        health = HEALTH_UNKNOWN
    )

    companion object {
        /** i₀ für MEO/IGSO: 0,30 Halbkreise (ICD §5.2.4.15). */
        const val REFERENCE_INCLINATION = 0.30 * PI

        /** Die Almanach-Seite trägt keine Gesundheitsangabe; sie steht getrennt. */
        const val HEALTH_UNKNOWN = -1
    }
}

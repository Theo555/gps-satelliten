package de.gpsradar.satellites.model

import kotlin.math.PI

/**
 * Ein GLONASS-Almanach-Satz aus den Strings 6–15.
 *
 * ## Woher die Bitlagen kommen
 *
 * Aus der Spezifikation (GLONASS-ICD 5.1, Tabelle 4.9 für Breiten und Skalen,
 * Tabelle 4.11 für die Lage im String), nicht aus RTKLIB — das dekodiert für
 * GLONASS **gar keinen** Almanach. Als zweite Umsetzung lag gnss-sdr daneben
 * (`GLONASS_L1_L2_CA.h`); alle zwölf Feldpositionen stimmen auf das Bit überein.
 *
 * **Bitnummern:** Die ICD zählt `b`-Nummern des Signals (b85 zuerst). Unsere
 * Helfer zählen 1-basiert ab dem Stringanfang, es gilt `Position = 85 − b_ende + 1`.
 * Belegt ist das am eigenen Mitschnitt (String 5: NA bei 6, τc bei 17, N4 bei
 * 50 — alle drei am Gerät bestätigt).
 *
 * ## Aufbau: ein Satz steckt in zwei Strings
 *
 * Nach den Strings 1–4 (Ephemeride des *sendenden* Satelliten) trägt jeder
 * Rahmen fünf Almanachsätze für **fremde** Satelliten, je zwei Strings:
 * der gerade String die Kennung ([svid] bis [health]), der ungerade die Bahn
 * ([nodeSeconds] bis [argumentOfPerigee]). Rahmen 1–4 decken die Plätze 1–20
 * ab, Rahmen 5 die Plätze 21–24. Die Zuordnung Rahmen/String → Platznummer
 * rechnet [GloLnav.almanacSlot] aus; der Satz nennt seine Platznummer zusätzlich
 * selbst, und der Sammler vergleicht beides.
 *
 * ## Warum kein gemeinsamer [Almanac]
 *
 * Der gemeinsame Typ ist an GPS ausgerichtet: `sqrtA`, `M0`, `Omega_dot` und
 * eine Bezugswoche. Nichts davon hat der GLONASS-Almanach. Er beschreibt die
 * Bahn über **Durchgangszeit des aufsteigenden Knotens** ([nodeSeconds]) und
 * **Draconische Periode** ([draconianPeriodCorrection]) — und die Woche ersetzt
 * er durch einen Tag im Vierjahreszyklus (NA/N4 in String 5). Eine Umrechnung
 * müsste vier Felder erfinden; deshalb steht der Satz hier für sich.
 *
 * Auch die große Halbachse wird **nicht** ausgerechnet: Aus der Draconischen
 * Periode (43 200 s) folgt mit μ allein a ≈ 26 600 km, tatsächlich liegt
 * GLONASS bei 25 510 km — der Unterschied ist die J2-Verkürzung des
 * Knotendurchgangs. Wer hier umrechnet, ohne J2 zu berücksichtigen, bekommt
 * eine Zahl, die plausibel aussieht und falsch ist.
 *
 * ## Kodierung
 *
 * Vorzeichen **und** Betrag (ICD, Anmerkung 2 zu Tabelle 4.9) — dieselbe
 * Eigenheit wie bei allen GLONASS-Werten, siehe [GloLnav].
 */
data class GloAlmanac(
    /**
     * Platznummer `nA` im System (1–24). **null**, solange nur die ungerade
     * Hälfte vorliegt: Sie steht ausschließlich im geraden String.
     */
    val svid: Int? = null,
    /**
     * Frequenzkanal `Hn`. Negativ möglich: die ICD überträgt −1 bis −7 als
     * 31 bis 25 (Tabelle 4.10); [GloLnav] rechnet das um.
     */
    val frequencyChannel: Int? = null,
    /**
     * Durchgangszeit `tλ` des aufsteigenden Knotens am Tag NA, in Sekunden.
     *
     * Auflösung 1/32 s (Skalenfaktor `2⁻⁵`, Tabelle 4.9) — deshalb keine ganze
     * Zahl.
     */
    val nodeSeconds: Double? = null,
    /** Länge `λ` des aufsteigenden Knotens um 0h des Tages NA (Radiant). */
    val longitude: Double? = null,
    /** Abweichung `Δi` von der mittleren Bahnneigung (Radiant). */
    val inclinationCorrection: Double? = null,
    /** Abweichung `ΔT` von der mittleren Draconischen Periode, in Sekunden. */
    val draconianPeriodCorrection: Double? = null,
    /** Änderung `ΔṪ` der Draconischen Periode, in Sekunden je Periode². */
    val draconianPeriodRate: Double? = null,
    val eccentricity: Double? = null,
    /** Argument des Perigäums `ω` (Radiant). */
    val argumentOfPerigee: Double? = null,
    /** Uhrkorrektur `τn` in Sekunden. */
    val clockCorrection: Double? = null,
    /** Satellitentyp `Mn`: 0 = GLONASS, 1 = GLONASS-M (2 Bit, heute stets 1). */
    val satelliteType: Int? = null,
    /** Gesundheitskennung `Cn`, 0 = gesund. */
    val health: Int? = null
) {

    /** Bahnneigung absolut (Radiant): 63° plus Abweichung. */
    val inclination: Double?
        get() = inclinationCorrection?.let { MEAN_INCLINATION + it }

    /** Draconische Periode absolut in Sekunden. */
    val draconianPeriodSeconds: Double?
        get() = draconianPeriodCorrection?.let { MEAN_DRACONIAN_PERIOD_SECONDS + it }

    /**
     * Vollständig genug für eine Bahn?
     *
     * Uhrkorrektur, Typ und Gesundheit fehlen dürfen: Sie stehen im zweiten
     * String und sind für die Bahn nicht nötig.
     */
    val isComplete: Boolean
        get() = nodeSeconds != null && longitude != null &&
            inclinationCorrection != null && draconianPeriodCorrection != null &&
            eccentricity != null && argumentOfPerigee != null

    /** Zwei Hälften zu einem Satz zusammensetzen; eigene Werte gewinnen. */
    fun merge(other: GloAlmanac): GloAlmanac = GloAlmanac(
        svid = svid ?: other.svid,
        frequencyChannel = frequencyChannel ?: other.frequencyChannel,
        nodeSeconds = nodeSeconds ?: other.nodeSeconds,
        longitude = longitude ?: other.longitude,
        inclinationCorrection = inclinationCorrection ?: other.inclinationCorrection,
        draconianPeriodCorrection = draconianPeriodCorrection ?: other.draconianPeriodCorrection,
        draconianPeriodRate = draconianPeriodRate ?: other.draconianPeriodRate,
        eccentricity = eccentricity ?: other.eccentricity,
        argumentOfPerigee = argumentOfPerigee ?: other.argumentOfPerigee,
        clockCorrection = clockCorrection ?: other.clockCorrection,
        satelliteType = satelliteType ?: other.satelliteType,
        health = health ?: other.health
    )

    /**
     * Prüfungen ohne jede Referenzdatei.
     *
     * Die Fenster sind aus den effektiven Bereichen der Tabelle 4.9 abgeleitet:
     * Die Draconische Periode weicht höchstens ±3,6·10³ s vom Mittel ab, die
     * Knotenzeit liegt im Tag, die Neigung ±0,067 Halbkreise um 63°.
     */
    val isPlausible: Boolean
        get() {
            val platz = svid ?: return false
            if (platz !in 1..24) return false
            val t = draconianPeriodSeconds ?: return false
            val e = eccentricity ?: return false
            val node = nodeSeconds ?: return false
            val lon = longitude ?: return false
            val inc = inclinationCorrection ?: return false
            val omg = argumentOfPerigee ?: return false
            return t in 39_600.0..46_800.0 &&
                e in 0.0..0.03 &&
                node in 0.0..44_100.0 &&
                kotlin.math.abs(lon) <= PI &&
                kotlin.math.abs(inc) <= 0.067 * PI &&
                kotlin.math.abs(omg) <= PI &&
                (frequencyChannel == null || frequencyChannel in -7..13)
        }

    companion object {
        /** Mittlere Bahnneigung nach ICD §4.5: 63°. */
        val MEAN_INCLINATION = 63.0 * PI / 180.0

        /** Mittlere Draconische Periode nach ICD §4.5: 43 200 s. */
        const val MEAN_DRACONIAN_PERIOD_SECONDS = 43_200.0
    }
}

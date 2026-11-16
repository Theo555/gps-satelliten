package de.gpsradar.satellites.model

/**
 * Dekodiert GLONASS-L1-Navigationsstrings (GNAV) aus den Rohdaten, die Android
 * als [android.location.GnssNavigationMessage] mit `TYPE_GLO_L1CA` liefert.
 *
 * ## Aufbau der Meldung (am Gerät nachgemessen, 14.11.2026)
 *
 * Ein String hat 85 Bit. Android legt sie MSB-first in **11 Byte** ab und lässt
 * die letzten drei Bit (B86–B88) leer — anders als bei GPS, wo 40 Byte zehn
 * Wörter à 24 Datenbits tragen. Jede Meldung ist genau ein String:
 * `submessageId` ist die Stringnummer 1–15, `messageId` die Rahmennummer 1–5.
 *
 * Die Ephemeride steht in den Strings 1–4, die jeder Satellit alle 30 Sekunden
 * wiederholt; Strings 5–15 tragen UTC-Parameter und den Almanach.
 *
 * Bitpositionen werden hier **1-basiert ab dem Stringanfang** gezählt, so wie in
 * der ICD. Aus GNSS-SDR (`src/core/system_parameters/GLONASS_L1_L2_CA.h`), am
 * eigenen Mitschnitt bestätigt: Bahnradius 25 508–25 538 km für fünf Satelliten,
 * Geschwindigkeit 3,14–3,48 km/s, τn auf 5·10⁻⁹ s gleich der BRDC, γn auf die
 * letzte Stufe (2⁻⁴⁰).
 *
 * ## Die Falle, die Zeit gekostet hat: Vorzeichen **und** Betrag
 *
 * GLONASS überträgt seine Zahlen nicht im Zweierkomplement, sondern als
 * Vorzeichenbit mit Betrag in den restlichen Bits — so liest es auch GNSS-SDR
 * (`read_navigation_signed`: `sign * value`). Mit Zweierkomplement ergaben vier
 * von fünf Satelliten einen Orbitradius von 33 000–42 000 km; der Unterschied
 * ist genau 2¹⁵ km, also das halbe Feld. Wer hier „aufräumt" und auf
 * Zweierkomplement umstellt, zerstört die Bahn.
 *
 * ## Was hier bewusst fehlt
 *
 * Keine Paritätsprüfung: Das Gerät prüft die Hammingbits und meldet das Ergebnis
 * in `GnssNavigationMessage.getStatus`; ein Nachbau wäre eine zweite
 * Fehlerquelle ohne Gewinn.
 *
 * Die Frequenznummer wird nicht ausgewertet — sie steht bei GLONASS auch gar
 * nicht in den unmittelbaren Daten, sondern im **Almanach** (§4.5), den diese
 * App nicht liest. In die RINEX-Datei geht deshalb 0: derselbe Wert, den RTKLIB
 * setzt, wenn es Ephemeriden aus Rohdaten baut (`rcvraw.c`: „geph->frq is set
 * to 0"). Für die Bahnrechnung wird sie nicht gebraucht, nur für RTCM 3.
 *
 * Das Wort **FT** im vierten String ist ebenfalls nicht die Frequenznummer,
 * sondern die vorhergesagte Bahngenauigkeit zum Zeitpunkt `tb` (Tabelle 4.4 der
 * ICD, Abstufungen von 1 m bis 7 m). Es wird nicht ausgewertet.
 */
object GloLnav {

    /** Länge einer Meldung in Byte. */
    const val PAYLOAD_BYTES = 11

    /** Länge des Strings in Bit (B86–B88 sind Füllbits). */
    const val DATA_BITS = 85

    /**
     * GLONASS zählt seine Zeit in Moskauer Zeit: UTC + 3 h, **ohne**
     * Schaltsekunden. `tb` und die Rahmenzeit aus String 1 sind darin angegeben.
     */
    const val MOSCOW_OFFSET_SECONDS = 10_800

    const val SECONDS_PER_DAY = 86_400

    /** Schrittweite des `tb`-Index in String 2: 15 Minuten. */
    const val TB_STEP_SECONDS = 900

    private const val P2_11 = 1.0 / 2048.0
    private const val P2_20 = 1.0 / 1_048_576.0
    private const val P2_30 = 1.0 / 1_073_741_824.0
    private const val P2_40 = 1.0 / 1_099_511_627_776.0

    /**
     * Rohdaten einer Meldung in einen Bitstrom aus 85 Zeichen umwandeln.
     * null, wenn die Länge nicht passt.
     */
    fun dataBits(data: ByteArray): String? {
        if (data.size != PAYLOAD_BYTES) return null
        val sb = StringBuilder(DATA_BITS)
        for (i in 0 until DATA_BITS) {
            val byte = data[i / 8].toInt() and 0xFF
            sb.append(if ((byte shr (7 - i % 8)) and 1 == 1) '1' else '0')
        }
        return sb.toString()
    }

    /** Stringnummer (1..15) aus dem String selbst; 0, wenn unbrauchbar. */
    fun stringId(bits: String): Int = unsigned(bits, 2, 4).toInt()

    /**
     * Eine der drei Bahnachsen. Strings 1, 2 und 3 haben denselben Aufbau und
     * liefern jeweils Ort, Geschwindigkeit und Beschleunigung einer Achse:
     * X in String 1, Y in String 2, Z in String 3 (PZ-90, erdfest, zum
     * Zeitpunkt `tb`).
     */
    fun axis(bits: String): GloAxis = GloAxis(
        positionKm = signMagnitude(bits, 51, 27) * P2_11,
        velocityKmPerSecond = signMagnitude(bits, 22, 24) * P2_20,
        accelerationKmPerSecond2 = signMagnitude(bits, 46, 5) * P2_30
    )

    /** Rahmenzeit aus String 1, in Sekunden seit Mitternacht **Moskauer Zeit**. */
    fun frameSeconds(bits: String): Int =
        unsigned(bits, 10, 5).toInt() * 3600 +
            unsigned(bits, 15, 6).toInt() * 60 +
            unsigned(bits, 21, 1).toInt() * 30

    /**
     * Gesundheitszustand `Bn` aus String 2 — **nur das höchstwertige Bit zählt**.
     *
     * `Bn` ist drei Bit breit, aber die ICD (§4.4) sagt ausdrücklich: „The user
     * navigation equipment analyzes the only one MSB of this word … does not
     * consider both second and third bits.“ Würde man alle drei Bit nehmen,
     * meldete ein Satellit mit gesetztem Reservebit fälschlich „krank“ — und in
     * der Navigationsdatei stünde eine Störung, die es nicht gibt.
     */
    fun health(bits: String): Int = if (bits[6] == '1') 1 else 0

    /**
     * `tb`-Index aus String 2: Nummer des 15-Minuten-Intervalls, in dem der
     * Ephemeridensatz gilt — **1-basiert** in Moskauer Zeit (am Gerät geprüft:
     * 77 heißt 19:00 Moskau = 16:00 UTC).
     */
    fun tbIndex(bits: String): Int = unsigned(bits, 10, 7).toInt()

    /** Relative Frequenzabweichung γn aus String 3. */
    fun gamma(bits: String): Double = signMagnitude(bits, 7, 11) * P2_40

    /** Uhr- und Kennungsdaten aus String 4. */
    fun clock(bits: String): GloClock = GloClock(
        tauSeconds = signMagnitude(bits, 6, 22) * P2_30,
        deltaTauSeconds = signMagnitude(bits, 28, 5) * P2_30,
        ageOfDataDays = unsigned(bits, 33, 5).toInt(),
        satelliteSlot = unsigned(bits, 71, 5).toInt(),
        dayNumber = unsigned(bits, 60, 11).toInt(),
        satelliteType = unsigned(bits, 76, 2).toInt()
    )

    /**
     * Gültigkeitsbeginn eines Ephemeridensatzes als Sekunden des Tages in UTC.
     *
     * Der `tb`-Index zählt 15-Minuten-Schritte ab Mitternacht Moskauer Zeit und
     * beginnt bei 1; deshalb erst eins abziehen, dann die drei Stunden
     * Zeitversatz herausrechnen. Probe am Mitschnitt vom 14.11.2026: Index 77
     * ergibt 57 600 s = 16:00 UTC — und genau dort trifft die aus der BRDC
     * integrierte Bahn die dekodierten Werte auf unter 1 km.
     */
    fun tbSecondsUtc(tbIndex: Int): Int =
        Math.floorMod((tbIndex - 1) * TB_STEP_SECONDS - MOSCOW_OFFSET_SECONDS, SECONDS_PER_DAY)

    /**
     * Zeitpunkt (UTC-Millisekunden) zum `tb`-Zeitpunkt des Tages.
     *
     * `tb` ist nur eine Tageszeit; das Datum kommt aus dem Empfangszeitpunkt.
     * Liegt der errechnete Zeitpunkt mehr als eine Stunde in der Zukunft, gehört
     * er zum Vortag (kurz nach Mitternacht UTC ist der laufende Satz noch der
     * von gestern).
     */
    fun epochMillis(tbSecondsUtc: Int, receivedAtMillis: Long): Long {
        val dayStartMillis = receivedAtMillis - Math.floorMod(receivedAtMillis, 86_400_000L)
        val millis = dayStartMillis + tbSecondsUtc * 1000L
        return if (millis > receivedAtMillis + 3_600_000L) millis - 86_400_000L else millis
    }

    private fun unsigned(bits: String, pos: Int, length: Int): Long =
        java.lang.Long.parseLong(bits.substring(pos - 1, pos - 1 + length), 2)

    /**
     * Vorzeichenbit plus Betrag — die Zahlenkodierung von GLONASS (siehe oben).
     * Das erste Bit des Feldes ist das Vorzeichen, die restlichen Bits sind der
     * Betrag.
     */
    private fun signMagnitude(bits: String, pos: Int, length: Int): Long {
        val magnitude = java.lang.Long.parseLong(bits.substring(pos, pos - 1 + length), 2)
        return if (bits[pos - 1] == '1') -magnitude else magnitude
    }
}

/** Ort, Geschwindigkeit und Beschleunigung einer Achse in PZ-90. */
data class GloAxis(
    val positionKm: Double,
    val velocityKmPerSecond: Double,
    val accelerationKmPerSecond2: Double
)

/** Uhr und Kennung aus String 4. */
data class GloClock(
    val tauSeconds: Double,
    val deltaTauSeconds: Double,
    val ageOfDataDays: Int,
    val satelliteSlot: Int,
    val dayNumber: Int,
    val satelliteType: Int
)

/**
 * Ein vollständiger GLONASS-Ephemeridensatz, aus den Strings 1–4 zusammengesetzt.
 *
 * [tbMillis] ist der Gültigkeitsbeginn des Satzes in UTC (RINEX: Epoche und
 * „Message frame time"), [frameSeconds] die Rahmenzeit aus String 1 in Moskauer
 * Zeit — sie wandert mit jedem Rahmen weiter, während die Bahnwerte für 30
 * Minuten unverändert bleiben, und dient nur der Plausibilitätsprüfung.
 *
 * Die Bahnwerte gelten zum Zeitpunkt [tbMillis], nicht zur Rahmenzeit: am
 * 14.11.2026 lag die Rahmenzeit bei 16:05:30 UTC, der Satz galt aber ab 16:00
 * UTC — dort traf die aus der BRDC integrierte Bahn die dekodierten Werte auf
 * unter 1 km.
 *
 * [frequencyChannel] bleibt null, solange das Gerät die Frequenznummer nicht
 * liefert (siehe Kopfkommentar von [GloLnav]).
 */
data class GloEphemeris(
    val satelliteSlot: Int,
    val tbMillis: Long,
    val frameSeconds: Int,
    val tauSeconds: Double,
    val gamma: Double,
    val health: Int,
    val ageOfDataDays: Int,
    val frequencyChannel: Int?,
    val x: GloAxis,
    val y: GloAxis,
    val z: GloAxis
) {
    /** Ein Satz ist nur brauchbar, wenn sich aus den Achsen eine GLONASS-Bahn ergibt. */
    val hasPlausibleOrbit: Boolean
        get() {
            val radius = Math.sqrt(x.positionKm * x.positionKm +
                y.positionKm * y.positionKm + z.positionKm * z.positionKm)
            val speed = Math.sqrt(x.velocityKmPerSecond * x.velocityKmPerSecond +
                y.velocityKmPerSecond * y.velocityKmPerSecond +
                z.velocityKmPerSecond * z.velocityKmPerSecond)
            return radius in ORBIT_RADIUS_MIN_KM..ORBIT_RADIUS_MAX_KM &&
                speed in ORBIT_SPEED_MIN_KM_S..ORBIT_SPEED_MAX_KM_S
        }

    companion object {
        /**
         * GLONASS kreist in 19 100 km Höhe, also 25 510 km vom Erdmittelpunkt.
         * Die Grenzen sind bewusst weit: sie sollen grobe Dekodierfehler fangen
         * (ein falsches Vorzeichen verschiebt um 32 768 km), nicht die Bahn
         * nachrechnen.
         */
        const val ORBIT_RADIUS_MIN_KM = 25_300.0
        const val ORBIT_RADIUS_MAX_KM = 25_750.0

        /** Bahngeschwindigkeit im erdfesten System, gemessen 3,14–3,48 km/s. */
        const val ORBIT_SPEED_MIN_KM_S = 2.0
        const val ORBIT_SPEED_MAX_KM_S = 5.8
    }
}

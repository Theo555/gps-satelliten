package de.gpsradar.satellites.model

import kotlin.math.PI

/**
 * Dekodiert GPS-L1-C/A-Unterrahmen aus den Rohdaten, die Android als
 * [android.location.GnssNavigationMessage] liefert.
 *
 * ## Aufbau der 40 Byte (am Gerät nachgemessen, 14.11.2026)
 *
 * Zehn Gruppen zu 32 Bit; jede Gruppe enthält zwei Füllbits und danach das
 * 30-Bit-Wort des Signals (24 Datenbits + 6 Paritätsbits). Macht 240 Datenbits
 * je Unterrahmen. Bit 0 ist die Präambel `10001011`, Bit 24 die Zeit (HOW),
 * Bit 48 beginnt der Nutzteil.
 *
 * ## Woher die Feldpositionen kommen
 *
 * Aus RTKLIB (`src/rcvraw.c`, `decode_frame_eph`), nicht aus dem Gedächtnis.
 * Zwei eigene Anläufe aus dem Kopf scheiterten an Unterrahmen 3: zwischen Ω0,
 * i0 und ω stehen die 16-Bit-Felder Cic und Cis, Ω̇ ist 24 Bit breit, und IODE
 * kommt erst nach Ω̇.
 *
 * ## Was hier bewusst fehlt
 *
 * Keine Paritätsprüfung. Das Gerät prüft sie selbst und meldet das Ergebnis in
 * [android.location.GnssNavigationMessage.getStatus]; ein Nachbau wäre eine
 * zweite Fehlerquelle ohne Gewinn.
 */
object GpsLnav {

    /** Länge einer Meldung in Byte. */
    const val PAYLOAD_BYTES = 40

    /** Länge des Datenstroms in Bit (zehn Wörter à 24 Datenbits). */
    const val DATA_BITS = 240

    private const val PREAMBLE = "10001011"
    private const val P2_5 = 1.0 / 32.0
    private const val P2_19 = 1.0 / 524288.0
    private const val P2_29 = 1.0 / 536870912.0
    private const val P2_30 = 1.0 / 1073741824.0
    private const val P2_31 = 1.0 / 2147483648.0
    private const val P2_33 = 1.0 / 8589934592.0
    private const val P2_43 = 1.0 / 8796093022208.0
    private const val P2_50 = 1.0 / 1125899906842624.0
    private const val P2_55 = 1.0 / 36028797018963968.0

    /**
     * Rohdaten einer Meldung in einen Bitstrom aus 240 Zeichen umwandeln.
     * Liefert null, wenn die Länge nicht passt oder die Präambel fehlt.
     */
    fun dataBits(data: ByteArray): String? {
        if (data.size != PAYLOAD_BYTES) return null
        val sb = StringBuilder(DATA_BITS)
        // Je 32-Bit-Gruppe: 2 Füllbits überspringen, dann 24 Datenbits nehmen.
        for (word in 0 until 10) {
            val groupStart = word * 4
            for (bit in 0 until 24) {
                val pos = 2 + bit
                val byte = data[groupStart + pos / 8].toInt() and 0xFF
                sb.append(if ((byte shr (7 - pos % 8)) and 1 == 1) '1' else '0')
            }
        }
        val bits = sb.toString()
        return if (bits.startsWith(PREAMBLE)) bits else null
    }

    /** Unterrahmen-Nummer (1..5) aus dem Bitstrom, 0 wenn unbrauchbar. */
    fun subframeNumber(bits: String): Int = unsigned(bits, 43, 3).toInt()

    /**
     * Seitenziffer aus den Unterrahmen 4 und 5 (SV ID, 6 Bit).
     *
     * Die ersten zwei Bit von Wort 3 sind die Data-ID (1 = LNAV), danach folgt
     * die Seitenziffer. Jeder Seite ist ein eigener Inhalt zugeordnet —
     * Ionosphäre und UTC stehen auf Seite 18.
     */
    fun pageNumber(bits: String): Int = unsigned(bits, 50, 6).toInt()

    /** Data-ID; nur der Wert 1 gehört zu LNAV. */
    fun dataId(bits: String): Int = unsigned(bits, 48, 2).toInt()

    /**
     * Klobuchar-Ionosphärenkoeffizienten von **Unterrahmen 4, Seite 18**.
     *
     * Reihenfolge und Skalen aus IS-GPS-200, §20.3.3.5.1.7 und Tabelle 20-X:
     * α₀…α₃ mit 2⁻³⁰ s, β₀…β₃ mit 2¹¹ s. Acht Felder zu acht Bit passen genau in
     * die Wörter 3 bis 5. Die Werte gelten für das ganze System — alle
     * Satelliten senden dieselben.
     */
    fun ionosphere(bits: String): KlobucharIono? {
        if (!isIonoUtcPage(bits)) return null
        return KlobucharIono(
            alpha = listOf(
                signed(bits, 56, 8) * P2_30,
                signed(bits, 64, 8) * P2_30,
                signed(bits, 72, 8) * P2_30,
                signed(bits, 80, 8) * P2_30
            ),
            beta = listOf(
                signed(bits, 88, 8).toDouble() * 2048.0,
                signed(bits, 96, 8).toDouble() * 2048.0,
                signed(bits, 104, 8).toDouble() * 2048.0,
                signed(bits, 112, 8).toDouble() * 2048.0
            )
        )
    }

    /**
     * Zeitbezug GPS zu UTC — steht auf **derselben Seite 18** wie die Ionosphäre.
     *
     * A₀ (32 Bit) und A₁ (24 Bit) sind über Wortgrenzen verteilt: A₀ als 24 + 8,
     * A₁ als 16 + 8 Bit. Danach folgen die Bezugszeit `tot`, die Bezugswoche
     * `WNt`, die Schaltsekunde, die Woche der künftigen Schaltsekunde, deren Tag
     * und die künftige Schaltsekunde (Tabelle 20-IX der ICD).
     *
     * **Reihenfolge am Signal statt in der Tabelle:** Tabelle 20-IX nennt
     * Δt_LS **vor** `tot`. Am Mitschnitt vom 16.11.2026 liegt `tot` aber davor —
     * nur so sind alle Gültigkeitsgrenzen der Tabelle gleichzeitig erfüllt:
     * `tot` = 78 · 4096 s = 319 488 s (Grenze 0…602 112 ✓), `WNt` = 141
     * (= Woche 2445 mod 256 ✓), Δt_LS = 18 (die geltende Schaltsekunde ✓),
     * DN = 7 (Grenze 1…7 ✓). Mit der Tabellenreihenfolge käme Δt_LS = 78
     * heraus — ein Wert, den es nicht gibt.
     */
    fun utc(bits: String): UtcCorrection? {
        if (!isIonoUtcPage(bits)) return null
        return UtcCorrection(
            a0 = signed2(bits, 120, 24, 144, 8) * P2_30,
            a1 = signed2(bits, 152, 16, 168, 8) * P2_50,
            referenceSeconds = unsigned(bits, 176, 8).toInt() * 4096,
            weekNumber = unsigned(bits, 184, 8).toInt(),
            deltaLs = signed(bits, 192, 8).toInt(),
            weekNumberLsf = unsigned(bits, 200, 8).toInt(),
            dayNumber = unsigned(bits, 208, 8).toInt(),
            deltaLsf = signed(bits, 216, 8).toInt()
        )
    }

    /** Steckt in diesem Bitstrom Unterrahmen 4, Seite 18 (Ionosphäre und UTC)? */
    fun isIonoUtcPage(bits: String): Boolean =
        bits.length >= 216 && subframeNumber(bits) == 4 &&
            dataId(bits) == 1 && pageNumber(bits) == 18

    /** Zeit im Unterrahmen: Sekunden seit Wochenbeginn (GPS-Zeit, 6-s-Schritte). */
    fun secondsOfWeek(bits: String): Long = unsigned(bits, 24, 17) * 6L

    /** Uhr und Gesundheit aus Unterrahmen 1. */
    fun clock(bits: String): GpsClock? {
        if (subframeNumber(bits) != 1) return null
        val iodcHigh = unsigned(bits, 70, 2).toInt()
        val iodcLow = unsigned(bits, 168, 8).toInt()
        return GpsClock(
            week = unsigned(bits, 48, 10).toInt(),
            l2Codes = unsigned(bits, 58, 2).toInt(),
            ura = unsigned(bits, 60, 4).toInt(),
            health = unsigned(bits, 64, 6).toInt(),
            l2pFlag = unsigned(bits, 72, 1) == 1L,
            tgd = signed(bits, 160, 8) * P2_31,
            iodc = (iodcHigh shl 8) + iodcLow,
            tocSeconds = (unsigned(bits, 176, 16) * 16).toInt(),
            af2 = signed(bits, 192, 8) * P2_55,
            af1 = signed(bits, 200, 16) * P2_43,
            af0 = signed(bits, 216, 22) * P2_31
        )
    }

    /** Bahnteil A aus Unterrahmen 2 (IODE, Fehlerkorrekturen, mittlere Anomalie, Exzentrizität, große Halbachse). */
    fun orbitA(bits: String): GpsOrbitA? {
        if (subframeNumber(bits) != 2) return null
        return GpsOrbitA(
            iode = unsigned(bits, 48, 8).toInt(),
            crs = signed(bits, 56, 16) * P2_5,
            deltaN = signed(bits, 72, 16) * P2_43 * PI,
            m0 = signed(bits, 88, 32) * P2_31 * PI,
            cuc = signed(bits, 120, 16) * P2_29,
            eccentricity = unsigned(bits, 136, 32) * P2_33,
            cus = signed(bits, 168, 16) * P2_29,
            sqrtA = unsigned(bits, 184, 32) * P2_19,
            toeSeconds = (unsigned(bits, 216, 16) * 16).toInt(),
            // Fit-Intervall-Kennzeichen (IS-GPS-200, §20.3.3.4.3.1): 0 heißt
            // Normalbetrieb mit 4-Stunden-Passung, 1 heißt erweiterter Betrieb
            // („länger als 4 Stunden“, §6.2.3). In die RINEX-Zeile gehört eine
            // Stundenzahl; geschrieben wird wie in RTKLIB 4,0 bzw. 0,0. Der Wert
            // geht in keine Positionsrechnung ein.
            fitHours = if (unsigned(bits, 232, 1) == 0L) 4.0 else 0.0
        )
    }

    /** Bahnteil B aus Unterrahmen 3 (Lage im Raum, Neigung, Knoten, Änderungsraten). */
    fun orbitB(bits: String): GpsOrbitB? {
        if (subframeNumber(bits) != 3) return null
        return GpsOrbitB(
            cic = signed(bits, 48, 16) * P2_29,
            omega0 = signed(bits, 64, 32) * P2_31 * PI,
            cis = signed(bits, 96, 16) * P2_29,
            i0 = signed(bits, 112, 32) * P2_31 * PI,
            crc = signed(bits, 144, 16) * P2_5,
            omega = signed(bits, 160, 32) * P2_31 * PI,
            omegaDot = signed(bits, 192, 24) * P2_43 * PI,
            iode = unsigned(bits, 216, 8).toInt(),
            idot = signed(bits, 224, 14) * P2_43 * PI
        )
    }

    /**
     * Wochenzahl auf den vollen Wert bringen.
     *
     * Im Signal stehen nur 10 Bit (0..1023); in einer RINEX-Datei wird die volle
     * Woche erwartet. RTKLIB macht in `adjgpsweek()` dasselbe Aufaddieren, nimmt
     * dafür aber die aktuelle Uhrzeit als Anhaltspunkt. Hier steht eine feste
     * Grenze: alles unter 2048 gilt als „vor der zweiten Wiederholung" und
     * bekommt 1024 dazu. Das trägt von 2019 bis 2039 (Woche 2048–3071); danach
     * müsste die Grenze mitwandern.
     */
    fun expandWeek(week: Int): Int {
        var w = week
        while (w < 2048) w += 1024
        return w
    }

    private fun unsigned(bits: String, pos: Int, length: Int): Long =
        java.lang.Long.parseLong(bits.substring(pos, pos + length), 2)

    private fun signed(bits: String, pos: Int, length: Int): Long {
        val raw = unsigned(bits, pos, length)
        return if (bits[pos] == '1') raw - (1L shl length) else raw
    }

    /** Feld über eine Wortgrenze hinweg, mit Vorzeichen über die Gesamtbreite. */
    private fun signed2(bits: String, pos1: Int, len1: Int, pos2: Int, len2: Int): Long {
        val total = len1 + len2
        val raw = (unsigned(bits, pos1, len1) shl len2) or unsigned(bits, pos2, len2)
        return if (raw and (1L shl (total - 1)) != 0L) raw - (1L shl total) else raw
    }
}

/** Uhr- und Gesundheitsdaten aus Unterrahmen 1. */
data class GpsClock(
    val week: Int,
    val l2Codes: Int,
    val ura: Int,
    val health: Int,
    val l2pFlag: Boolean,
    val tgd: Double,
    val iodc: Int,
    val tocSeconds: Int,
    val af2: Double,
    val af1: Double,
    val af0: Double
)

/** Bahndaten aus Unterrahmen 2. */
data class GpsOrbitA(
    val iode: Int,
    val crs: Double,
    val deltaN: Double,
    val m0: Double,
    val cuc: Double,
    val eccentricity: Double,
    val cus: Double,
    val sqrtA: Double,
    val toeSeconds: Int,
    val fitHours: Double
)

/** Bahndaten aus Unterrahmen 3. */
data class GpsOrbitB(
    val cic: Double,
    val omega0: Double,
    val cis: Double,
    val i0: Double,
    val crc: Double,
    val omega: Double,
    val omegaDot: Double,
    val iode: Int,
    val idot: Double
)

/**
 * Ein vollständiger Datensatz zu einem Satelliten, aus Unterrahmen 1–3.
 *
 * [week] ist bereits die volle Wochenzahl (siehe [GpsLnav.expandWeek]).
 * [transmissionSeconds] ist die Sendezeit des Rahmens (TOW des Unterrahmens 1).
 * Sie steht in RINEX in der letzten Zeile und darf nicht mit [GpsOrbitA.toeSeconds]
 * verwechselt werden — das ist der Bezugszeitpunkt der Bahn, nicht die Sendezeit.
 */
data class GpsEphemeris(
    val prn: Int,
    val week: Int,
    val clock: GpsClock,
    val orbitA: GpsOrbitA,
    val orbitB: GpsOrbitB,
    val transmissionSeconds: Int
) {
    /** Ein Satz ist nur brauchbar, wenn beide Bahnteile denselben IODE tragen. */
    val hasConsistentIode: Boolean get() = orbitA.iode == orbitB.iode

    companion object {
        /** Unterrahmen 1–3 zu einem Datensatz verbinden, null bei Widerspruch. */
        fun of(
            prn: Int,
            clock: GpsClock,
            orbitA: GpsOrbitA,
            orbitB: GpsOrbitB,
            transmissionSeconds: Int
        ): GpsEphemeris? {
            if (orbitA.iode != orbitB.iode) return null
            return GpsEphemeris(
                prn, GpsLnav.expandWeek(clock.week), clock, orbitA, orbitB, transmissionSeconds
            )
        }
    }
}

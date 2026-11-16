package de.gpsradar.satellites.model

import kotlin.math.PI

/**
 * Dekodiert BeiDou-D1-Navigationsdaten aus den Rohdaten, die Android als
 * [android.location.GnssNavigationMessage] liefert.
 *
 * ## Aufbau der 40 Byte (am Gerät gemessen, Sportplatz 16.11.2026)
 *
 * Dasselbe Raster wie GPS L1 C/A: zehn Gruppen zu 32 Bit, jede mit zwei
 * Füllbits und danach einem 30-Bit-Wort. Anders als bei GPS werden hier **alle
 * 30 Bit** je Wort gebraucht — die Felder laufen über die Wortgrenzen hinweg,
 * und RTKLIB überspringt in seinem 300-Bit-Strom genau die Bitpositionen 26–29
 * jedes Wortes (die Paritätsbits). Wer nur die 24 Datenbits nimmt, bekommt eine
 * Bitfolge, in der keine Feldposition mehr stimmt.
 *
 * Die Präambel ist elf Bit lang (`11100010010`) — erster Prüfpunkt.
 *
 * ## Woher die Feldpositionen kommen
 *
 * Aus RTKLIB (`src/rcvraw.c`, `decode_bds_d1_eph`), Zeile für Zeile
 * übernommen — nicht aus dem Gedächtnis. Gegenprobe am 16.11.2026: alle 16
 * Bahn- und Uhrwerte von fünf Satelliten stimmen **bis zur letzten Stelle** mit
 * der BRDC desselben Tages überein (siehe `BdsLnavTest`).
 *
 * ## Falle: halb gefüllte Unterrahmen
 *
 * Der Empfänger liefert regelmäßig „Subframe 1"-Meldungen, deren Nutzteil nur
 * bis etwa Bit 90 echt ist; danach folgt ein wiederholendes Füllmuster. Die
 * Felder für Uhr und Gesundheit stehen aber erst ab Bit 98 — man würde also
 * ein Füllmuster als Uhrversatz lesen, und weil das Muster bei allen Satelliten
 * gleich ist, bekämen alle dieselben Werte. Das fällt nur durch die Prüfung auf,
 * die RTKLIB ebenfalls macht: **`toc` muss gleich `toe` sein** ([hasConsistentToc]).
 * Sie braucht keine externe Referenz und sortiert die halben Sätze sicher aus.
 *
 * ## Was hier bewusst fehlt
 *
 * Keine Paritätsprüfung — das Gerät prüft sie und meldet das Ergebnis in
 * [android.location.GnssNavigationMessage.getStatus]. Unterrahmen 4 und 5
 * (Almanach, Ionosphäre, UTC) werden nicht ausgewertet; für eine
 * Navigationsdatei mit Bahn und Uhr sind sie nicht nötig.
 */
object BdsLnav {

    /** Länge einer Meldung in Byte. */
    const val PAYLOAD_BYTES = 40

    /** Länge des Datenstroms in Bit (zehn Wörter à 30 Bit). */
    const val DATA_BITS = 300

    private const val PREAMBLE = "11100010010"

    private const val P2_6 = 1.0 / 64.0
    private const val P2_19 = 1.0 / 524288.0
    private const val P2_31 = 1.0 / 2147483648.0
    private const val P2_33 = 1.0 / 8589934592.0
    private const val P2_43 = 1.0 / 8796093022208.0
    private const val P2_50 = 1.0 / 1125899906842624.0
    private const val P2_66 = 1.0 / 7.378697629483821e19

    /**
     * Rohdaten einer Meldung in einen Bitstrom aus 300 Zeichen umwandeln.
     * Liefert null, wenn die Länge nicht passt oder die Präambel fehlt.
     */
    fun dataBits(data: ByteArray): String? {
        if (data.size != PAYLOAD_BYTES) return null
        val sb = StringBuilder(DATA_BITS)
        // Je 32-Bit-Gruppe: zwei Füllbits überspringen, dann 30 Bit übernehmen.
        for (word in 0 until 10) {
            val groupStart = word * 4
            for (bit in 0 until 30) {
                val pos = 2 + bit
                val byte = data[groupStart + pos / 8].toInt() and 0xFF
                sb.append(if ((byte shr (7 - pos % 8)) and 1 == 1) '1' else '0')
            }
        }
        val bits = sb.toString()
        return if (bits.startsWith(PREAMBLE)) bits else null
    }

    /** Unterrahmen-Nummer (1..5) aus dem Bitstrom, 0 wenn unbrauchbar. */
    fun frameId(bits: String): Int = unsigned(bits, 15, 3).toInt()

    /**
     * Sendezeit des Unterrahmens in Sekunden seit Beginn der BeiDou-Woche.
     *
     * Das Feld ist 20 Bit breit und liegt auf zwei Wörtern (8 Bit ab 18, 12 Bit
     * ab 30); die Bits 26–29 dazwischen sind Parität. Der Wert steht **bereits
     * in Sekunden** — anders als bei GPS, wo die 17-Bit-Zeit in 6-Sekunden-
     * Schritten gezählt wird und deshalb mit 6 zu multiplizieren ist.
     */
    fun secondsOfWeek(bits: String): Long = unsigned2(bits, 18, 8, 30, 12)

    /** Uhr, Gesundheit und Zeitbezug aus Unterrahmen 1. */
    fun clock(bits: String): BdsClock? {
        if (frameId(bits) != 1) return null
        return BdsClock(
            week = unsigned(bits, 60, 13).toInt(),
            health = unsigned(bits, 42, 1).toInt(),
            aodc = unsigned(bits, 43, 5).toInt(),
            ura = unsigned(bits, 48, 4).toInt(),
            tocSeconds = (unsigned2(bits, 73, 9, 90, 8) * 8L).toInt(),
            tgd1 = signed(bits, 98, 10) * 0.1e-9,
            tgd2 = signed2(bits, 108, 4, 120, 6) * 0.1e-9,
            a2 = signed(bits, 214, 11) * P2_66,
            a0 = signed2(bits, 225, 7, 240, 17) * P2_33,
            a1 = signed2(bits, 257, 5, 270, 17) * P2_50,
            aode = unsigned(bits, 287, 5).toInt()
        )
    }

    /** Bahnteil A aus Unterrahmen 2. */
    fun orbitA(bits: String): BdsOrbitA? {
        if (frameId(bits) != 2) return null
        return BdsOrbitA(
            deltaN = signed2(bits, 42, 10, 60, 6) * P2_43 * PI,
            cuc = signed2(bits, 66, 16, 90, 2) * P2_31,
            m0 = signed2(bits, 92, 20, 120, 12) * P2_31 * PI,
            eccentricity = unsigned2(bits, 132, 10, 150, 22) * P2_33,
            cus = signed(bits, 180, 18) * P2_31,
            crc = signed2(bits, 198, 4, 210, 14) * P2_6,
            crs = signed2(bits, 224, 8, 240, 10) * P2_6,
            sqrtA = unsigned2(bits, 250, 12, 270, 20) * P2_19,
            toeHighBits = unsigned(bits, 290, 2).toInt()
        )
    }

    /** Bahnteil B aus Unterrahmen 3. */
    fun orbitB(bits: String): BdsOrbitB? {
        if (frameId(bits) != 3) return null
        return BdsOrbitB(
            toeLowBits = unsigned2(bits, 42, 10, 60, 5).toInt(),
            i0 = signed2(bits, 65, 17, 90, 15) * P2_31 * PI,
            cic = signed2(bits, 105, 7, 120, 11) * P2_31,
            omegaDot = signed2(bits, 131, 11, 150, 13) * P2_43 * PI,
            cis = signed2(bits, 163, 9, 180, 9) * P2_31,
            idot = signed2(bits, 189, 13, 210, 1) * P2_43 * PI,
            omega0 = signed2(bits, 211, 21, 240, 11) * P2_31 * PI,
            omega = signed2(bits, 251, 11, 270, 21) * P2_31 * PI
        )
    }

    /** Bezugszeit der Bahn aus den beiden Teilen: Sekunden der BeiDou-Woche. */
    fun toeSeconds(orbitA: BdsOrbitA, orbitB: BdsOrbitB): Int =
        ((orbitA.toeHighBits shl 15) or orbitB.toeLowBits) * 8

    private fun unsigned(bits: String, pos: Int, length: Int): Long =
        java.lang.Long.parseLong(bits.substring(pos, pos + length), 2)

    /** Feld über zwei Wortgrenzen hinweg, vorzeichenlos. */
    private fun unsigned2(bits: String, pos1: Int, len1: Int, pos2: Int, len2: Int): Long =
        (unsigned(bits, pos1, len1) shl len2) or unsigned(bits, pos2, len2)

    private fun signed(bits: String, pos: Int, length: Int): Long {
        val raw = unsigned(bits, pos, length)
        return if (bits[pos] == '1') raw - (1L shl length) else raw
    }

    private fun signed2(bits: String, pos1: Int, len1: Int, pos2: Int, len2: Int): Long {
        val total = len1 + len2
        val raw = unsigned2(bits, pos1, len1, pos2, len2)
        return if (raw and (1L shl (total - 1)) != 0L) raw - (1L shl total) else raw
    }
}

/** Uhr, Gesundheit und Zeitbezug aus Unterrahmen 1. */
data class BdsClock(
    /** BeiDou-Wochenzahl (13 Bit, reicht bis 2137). */
    val week: Int,
    /** SatH1: 0 heißt gesund. */
    val health: Int,
    /** Alter der Daten (AODC). */
    val aodc: Int,
    /** Genauigkeit (URA-Index). */
    val ura: Int,
    /** Bezugszeit der Uhr in Sekunden der BeiDou-Woche. */
    val tocSeconds: Int,
    /** Gruppenlaufzeit B1I in Sekunden. */
    val tgd1: Double,
    /** Gruppenlaufzeit B3I in Sekunden. */
    val tgd2: Double,
    val a2: Double,
    val a0: Double,
    val a1: Double,
    /** Alter der Ephemeride (AODE). */
    val aode: Int
)

/** Bahndaten aus Unterrahmen 2. */
data class BdsOrbitA(
    val deltaN: Double,
    val cuc: Double,
    val m0: Double,
    val eccentricity: Double,
    val cus: Double,
    val crc: Double,
    val crs: Double,
    val sqrtA: Double,
    /** Die zwei höchsten Bit der Bezugszeit; die unteren 15 stehen in Unterrahmen 3. */
    val toeHighBits: Int
)

/** Bahndaten aus Unterrahmen 3. */
data class BdsOrbitB(
    val toeLowBits: Int,
    val i0: Double,
    val cic: Double,
    val omegaDot: Double,
    val cis: Double,
    val idot: Double,
    val omega0: Double,
    val omega: Double
)

/**
 * Ein vollständiger BeiDou-Datensatz aus den Unterrahmen 1–3.
 *
 * Alle Zeiten sind BeiDou-Zeit (BDT). Sie liegt dauerhaft etwa 14 Sekunden vor
 * der GPS-Zeit, weil BDT bei der Einführung 2006 ohne Schaltsekundenübertrag
 * begann. In der RINEX-Datei stehen BeiDou-Sätze ebenfalls in BDT — es ist
 * deshalb richtig, die Werte unverändert zu schreiben, und falsch, sie
 * umzurechnen.
 */
data class BdsEphemeris(
    val prn: Int,
    val clock: BdsClock,
    val orbitA: BdsOrbitA,
    val orbitB: BdsOrbitB,
    /** Sendezeit des Unterrahmens 1 in Sekunden der BeiDou-Woche. */
    val transmissionSeconds: Int,
    /** Bezugszeit der Bahn in Sekunden der BeiDou-Woche. */
    val toeSeconds: Int
) {
    /**
     * Uhr und Bahn beziehen sich auf denselben Zeitpunkt.
     *
     * Diese Prüfung ist der Filter gegen halb gefüllte Unterrahmen (siehe
     * Klassenbeschreibung von [BdsLnav]) und stammt aus RTKLIB, das an derselben
     * Stelle abbricht.
     */
    val hasConsistentToc: Boolean get() = clock.tocSeconds == toeSeconds

    /** Bahnradius in Kilometern — grobe Plausibilität für BeiDou (rund 27 900 km MEO). */
    val orbitRadiusKm: Double get() = orbitA.sqrtA * orbitA.sqrtA / 1000.0

    companion object {
        /** Unterrahmen 1–3 verbinden; null, wenn Uhr und Bahn nicht zusammenpassen. */
        fun of(
            prn: Int,
            clock: BdsClock,
            orbitA: BdsOrbitA,
            orbitB: BdsOrbitB,
            transmissionSeconds: Int
        ): BdsEphemeris? {
            val toe = BdsLnav.toeSeconds(orbitA, orbitB)
            if (clock.tocSeconds != toe) return null
            return BdsEphemeris(prn, clock, orbitA, orbitB, transmissionSeconds, toe)
        }
    }
}

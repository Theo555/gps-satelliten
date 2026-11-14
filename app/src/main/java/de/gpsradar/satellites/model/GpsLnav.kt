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
    private const val P2_31 = 1.0 / 2147483648.0
    private const val P2_33 = 1.0 / 8589934592.0
    private const val P2_43 = 1.0 / 8796093022208.0
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
     * Woche erwartet. RTKLIB macht in `adjgpsweek()` dasselbe Aufaddieren.
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

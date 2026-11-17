package de.gpsradar.satellites.model

import kotlin.math.PI

/**
 * Dekodiert Galileo-I/NAV-Navigationsdaten aus den Rohdaten, die Android als
 * [android.location.GnssNavigationMessage] liefert.
 *
 * ## Aufbau einer Meldung (am Gerät gemessen, Sportplatz 16.11.2026)
 *
 * Das Gerät liefert 29 Byte (Note 9 mit Android 10: 30 Byte). Byte 0 ist der
 * **Worttyp** (1–5 für die Ephemeride, 6–10 für Ionosphäre, Zeit und Almanach);
 * Android gibt ihn zusätzlich als `submessageId` heraus. Danach folgen die
 * Nutzbits **ohne** Worttyp.
 *
 * ## Warum zwei Bits mitten im Inhalt fehlen
 *
 * Ein I/NAV-Seitenteil trägt laut Spezifikation 114 Bit: **1 Bit Even/Odd,
 * 1 Bit Page Type und 112 Bit Wortdaten**. Ein nominales Wort hat aber 128 Bit
 * und wird **auf zwei Seitenteile verteilt: 112 Bit (1/2) und 16 Bit (2/2)**.
 *
 * Daraus folgt die Teilnaht: 112 Bit Seitenteil minus 6 Bit Worttyp (die das
 * Gerät getrennt liefert) = **106 Bit**, danach stehen die Even/Odd- und
 * Page-Type-Bits des **nächsten** Seitenteils im Feld — die gehören nicht zum
 * Wort und werden in [contentBits] entfernt. Übrig bleiben
 * $106 + 2 + 14 = 122$ Bit, mit dem Worttyp also 128 Bit.
 *
 * Ohne diese Korrektur sind alle Felder ab Bit 106 falsch (√A, Crs, i̇, a₁).
 * Der Fehler ist am 16.11.2026 aufgefallen, weil fünf Satelliten zwar IOD,
 * Uhrzeit, M₀ und e exakt trafen, der Rest aber nicht — der Inhalt war also
 * nicht verschoben, sondern an einer Stelle unterbrochen.
 *
 * ## Woher die Feldpositionen kommen
 *
 * Aus RTKLIB (`src/rcvraw.c`, `decode_gal_inav_eph`), Zeile für Zeile
 * übernommen, und gegen die Spezifikationsstruktur geprüft. Gegenprobe am
 * 16.11.2026: 19 Bahn-, Uhr- und Gruppenlaufzeitwerte von fünf Satelliten
 * stimmen **bis zur letzten Stelle** mit der BRDC desselben Tages überein, dazu
 * SISA (Index 107 → 3,12 m) und die Wochenzahl (1421 + 1024 = 2445). Siehe
 * `GalLnavTest`.
 *
 * ## Was die Prüfungen abfangen
 *
 * Der HAL anderer Geräte packt Galileo anders (der Note 9 mit Android 10
 * liefert bei gleicher Worttyp-Angabe einen Inhalt, der zu keiner der oben
 * genannten Stellen passt). Damit daraus keine falsche Bahn in der
 * Navigationsdatei landet, verlangt [GalEphemeris.of] drei Dinge:
 *
 * - **IODnav gleich** in den Worttypen 1–4 (so wie RTKLIB es prüft),
 * - **PRN im Worttyp 4** gleich der Satellitenkennung der Meldung,
 * - **`toc` und `toe`** bezeichnen denselben Zeitpunkt (bei Galileo immer der
 *   Fall, nur über den Tageswechsel hinweg um 86 400 s verschoben),
 * - **plausible Bahn** ([GalEphemeris.hasPlausibleOrbit], rund 29 600 km).
 *
 * ## Was hier bewusst fehlt
 *
 * Keine Prüfsumme — das Gerät prüft sie und meldet das Ergebnis in
 * [android.location.GnssNavigationMessage.getStatus]. Worttypen 6–10
 * (Ionosphäre, GST→UTC, Almanach) werden nicht ausgewertet; für eine
 * Navigationsdatei mit Bahn und Uhr sind sie nicht nötig.
 */
object GalLnav {

    /** Mindestlänge einer Meldung in Byte: der Worttyp 3 reicht bis Bit 132. */
    const val MIN_PAYLOAD_BYTES = 17

    /** Die ersten acht Bit sind der Worttyp und stehen nicht im Nutzinhalt. */
    const val TYPE_BITS = 8

    /** Teilnaht: 112 Bit Seitenteil minus 6 Bit Worttyp. */
    const val SPLICE_POSITION = 106

    private const val P2_5 = 1.0 / 32.0
    private const val P2_8 = 1.0 / 256.0
    private const val P2_9 = 1.0 / 512.0
    private const val P2_14 = 1.0 / 16384.0
    private const val P2_15 = 1.0 / 32768.0
    private const val P2_16 = 1.0 / 65536.0
    private const val P2_19 = 1.0 / 524288.0
    private const val P2_29 = 1.0 / 536870912.0
    private const val P2_30 = 1.0 / 1073741824.0
    private const val P2_31 = 1.0 / 2147483648.0
    private const val P2_32 = 1.0 / 4294967296.0
    private const val P2_33 = 1.0 / 8589934592.0
    private const val P2_34 = 1.0 / 17179869184.0
    private const val P2_43 = 1.0 / 8796093022208.0
    private const val P2_38 = 1.0 / 274877906944.0
    private const val P2_46 = 1.0 / 70368744177664.0
    private const val P2_50 = 1.0 / 1125899906842624.0
    private const val P2_59 = 1.0 / 576460752303423488.0

    /** Worttyp aus Byte 0 der Meldung. */
    fun wordType(data: ByteArray): Int =
        if (data.isEmpty()) -1 else data[0].toInt() and 0xFF

    /**
     * Nutzbits als Zeichenkette: Worttyp abgeschnitten, Teilnaht entfernt.
     * Liefert null, wenn die Meldung zu kurz ist.
     */
    fun contentBits(data: ByteArray): String? {
        if (data.size < MIN_PAYLOAD_BYTES) return null
        val sb = StringBuilder(data.size * 8)
        for (b in data) {
            val v = b.toInt() and 0xFF
            for (i in 7 downTo 0) sb.append(if ((v shr i) and 1 == 1) '1' else '0')
        }
        val alle = sb.toString()
        val naht = TYPE_BITS + SPLICE_POSITION
        return alle.substring(TYPE_BITS, naht) + alle.substring(naht + 2)
    }

    /**
     * Worttyp 1: IODnav, Bezugszeit der Bahn und die ersten drei Bahnwerte.
     *
     * `toc` und `toe` werden hier in Sekunden der Galileo-Woche geliefert; das
     * Feld zählt 14 Bit in Minuten.
     */
    fun orbitA(bits: String): GalOrbitA? {
        if (bits.length < 120) return null
        return GalOrbitA(
            iodNav = unsigned(bits, 0, 10).toInt(),
            toeSeconds = (unsigned(bits, 10, 14) * 60L).toInt(),
            m0 = signed(bits, 24, 32) * P2_31 * PI,
            eccentricity = unsigned(bits, 56, 32) * P2_33,
            sqrtA = unsigned(bits, 88, 32) * P2_19
        )
    }

    /** Worttyp 2: Knotenlage, Bahnneigung, Perigäumswinkel und deren Raten. */
    fun orbitB(bits: String): GalOrbitB? {
        if (bits.length < 120) return null
        return GalOrbitB(
            iodNav = unsigned(bits, 0, 10).toInt(),
            omega0 = signed(bits, 10, 32) * P2_31 * PI,
            i0 = signed(bits, 42, 32) * P2_31 * PI,
            omega = signed(bits, 74, 32) * P2_31 * PI,
            idot = signed(bits, 106, 14) * P2_43 * PI
        )
    }

    /**
     * Worttyp 3: Störglieder der Bahn und SISA.
     *
     * SISA steht im Signal als **Index** 0–255; in die Datei gehört der
     * Nennwert in Metern ([GalEphemeris.sisaMetres]).
     */
    fun orbitC(bits: String): GalOrbitC? {
        if (bits.length < 122) return null
        return GalOrbitC(
            iodNav = unsigned(bits, 0, 10).toInt(),
            omegaDot = signed(bits, 10, 24) * P2_43 * PI,
            deltaN = signed(bits, 34, 16) * P2_43 * PI,
            cuc = signed(bits, 50, 16) * P2_29,
            cus = signed(bits, 66, 16) * P2_29,
            crc = signed(bits, 82, 16) * P2_5,
            crs = signed(bits, 98, 16) * P2_5,
            sisaIndex = unsigned(bits, 114, 8).toInt()
        )
    }

    /** Worttyp 4: Uhr, Bezugszeit der Uhr und die Neigungsstörglieder. */
    fun clock(bits: String): GalClock? {
        if (bits.length < 120) return null
        return GalClock(
            iodNav = unsigned(bits, 0, 10).toInt(),
            svid = unsigned(bits, 10, 6).toInt(),
            cic = signed(bits, 16, 16) * P2_29,
            cis = signed(bits, 32, 16) * P2_29,
            tocSeconds = (unsigned(bits, 48, 14) * 60L).toInt(),
            af0 = signed(bits, 62, 31) * P2_34,
            af1 = signed(bits, 93, 21) * P2_46,
            af2 = signed(bits, 114, 6) * P2_59
        )
    }

    /**
     * Worttyp 5: Gesundheit, Gruppenlaufzeiten, Woche und Sendezeit.
     *
     * Die ersten 41 Bit gehören zu einer anderen Signalzusammensetzung (ai0–ai2)
     * und werden übersprungen. Das Feld `week` ist die 12-Bit-Wochenzahl der
     * Galileo-Zeit; sie zählt gegenüber der GPS-Woche um **1024** versetzt
     * (RTKLIB: `week = wn + 1024`).
     */
    fun health(bits: String): GalHealth? {
        if (bits.length < 99) return null
        return GalHealth(
            bgdE5aE1 = signed(bits, 41, 10) * P2_32,
            bgdE5bE1 = signed(bits, 51, 10) * P2_32,
            health = healthBits(bits),
            week = unsigned(bits, 67, 12).toInt() + GAL_WEEK_OFFSET,
            transmissionSeconds = unsigned(bits, 79, 20).toInt()
        )
    }

    /**
     * Worttyp 5, erste 41 Bit: die Koeffizienten des NeQuick-G-Ionosphären­modells.
     *
     * Reihenfolge und Skalen aus RTKLIB (`decode_gal_inav_ion`), die Feldnamen
     * aus Tabelle 44 der ICD („Ionospheric correction“). Die fünf Bit nach ai2
     * sind die Störungsmeldungen des Modells (Az); sie werden nur durchgereicht,
     * nicht ausgewertet.
     */
    fun ionosphere(bits: String): GalIonosphere? {
        if (bits.length < 41) return null
        return GalIonosphere(
            ai0 = unsigned(bits, 0, 11) * 0.25,
            ai1 = signed(bits, 11, 11) * P2_8,
            ai2 = signed(bits, 22, 14) * P2_15,
            disturbanceFlags = unsigned(bits, 36, 5).toInt()
        )
    }

    /**
     * Worttyp 6: Zeitbezug Galileo-Zeit zu UTC.
     *
     * Raster aus RTKLIB (`decode_gal_inav_utc`), Feldnamen aus Tabelle 45 der
     * ICD. `deltaLs` ist die geltende Schaltsekunde — derselbe Wert, der in der
     * Navigationsdatei als LEAP SECONDS steht (derzeit 18).
     */
    fun utc(bits: String): UtcCorrection? {
        if (bits.length < 99) return null
        return UtcCorrection(
            a0 = signed(bits, 0, 32) * P2_30,
            a1 = signed(bits, 32, 24) * P2_50,
            deltaLs = signed(bits, 56, 8).toInt(),
            referenceSeconds = unsigned(bits, 64, 8).toInt() * 3600,
            weekNumber = unsigned(bits, 72, 8).toInt(),
            weekNumberLsf = unsigned(bits, 80, 8).toInt(),
            dayNumber = unsigned(bits, 88, 3).toInt(),
            deltaLsf = signed(bits, 91, 8).toInt()
        )
    }

    /**
     * Gesundheitsbits so zusammenschieben, wie RINEX sie erwartet: Bit 0 E1B DVS,
     * Bit 1–2 E1B HS, Bit 6 E5b DVS, Bit 7–8 E5b HS (E5a führt I/NAV nicht).
     */
    private fun healthBits(bits: String): Int {
        val e5bHs = unsigned(bits, 61, 2).toInt()
        val e1bHs = unsigned(bits, 63, 2).toInt()
        val e5bDvs = unsigned(bits, 65, 1).toInt()
        val e1bDvs = unsigned(bits, 66, 1).toInt()
        return (e5bHs shl 7) or (e5bDvs shl 6) or (e1bHs shl 1) or e1bDvs
    }

    /** SISA-Index auf seinen Nennwert in Metern — dieselbe Treppe wie RTKLIB. */
    fun sisaMetres(index: Int): Double = when {
        index < 0 -> -1.0
        index <= 49 -> index * 0.01
        index <= 74 -> 0.5 + (index - 50) * 0.02
        index <= 99 -> 1.0 + (index - 75) * 0.04
        index <= 125 -> 2.0 + (index - 100) * 0.16
        else -> -1.0
    }

    /** Versatz der Galileo-Wochenzählung gegenüber der GPS-Woche. */
    const val GAL_WEEK_OFFSET = 1024

    /**
     * Worttyp 7: Almanach-Bahnsatz für SVID1 — ohne Uhr.
     *
     * Die Uhr folgt im nächsten Wort (Typ 8), M₀ ist hier aber schon dabei.
     * Positionen aus ICD-Tabelle 46, 1-basiert, hier um sieben verringert
     * (sechs Bit Worttyp plus die Zählung ab eins).
     *
     * Gegenprobe am Mitschnitt vom 17.11.2026 (C05 sendet den Almanach von
     * C13): √A = 5440,6116 — Galileo liegt bei 5440,6; Bahnneigung 57,09°,
     * also 56° Nominalwert plus 1,09° Abweichung; t_oa = 209 400 s.
     */
    fun almanacReference(bits: String): GalAlmanac? {
        if (bits.length < 121) return null
        val svid = unsigned(bits, 16, 6).toInt()
        if (svid !in 1..36) return null
        return GalAlmanac(
            svid = svid,
            iodA = unsigned(bits, 0, 4).toInt(),
            weekBits = unsigned(bits, 4, 2).toInt(),
            referenceSeconds = (unsigned(bits, 6, 10) * 600L).toInt(),
            sqrtA = GalAlmanac.SQRT_A_NOMINAL + signed(bits, 22, 13) * P2_9,
            eccentricity = unsigned(bits, 35, 11) * P2_16,
            argumentOfPerigee = signed(bits, 46, 16) * P2_15 * PI,
            inclination = GalAlmanac.INCLINATION_NOMINAL + signed(bits, 62, 11) * P2_14 * PI,
            ascendingNode = signed(bits, 73, 16) * P2_15 * PI,
            nodeRate = signed(bits, 89, 11) * P2_33 * PI,
            meanAnomaly = signed(bits, 100, 16) * P2_15 * PI
        )
    }

    /**
     * Worttyp 8, erster Teil: Uhr für den Satelliten aus dem vorangehenden
     * Worttyp 7. Der Satellit steht dort, nicht hier — deshalb [svid].
     */
    fun almanacClock(bits: String, svid: Int): GalAlmanac? {
        if (bits.length < 39 || svid <= 0) return null
        return GalAlmanac(
            svid = svid,
            iodA = unsigned(bits, 0, 4).toInt(),
            clockBias = signed(bits, 4, 16) * P2_19,
            clockRate = signed(bits, 20, 13) * P2_38
        )
    }

    /**
     * Worttyp 8, zweiter Teil: Bahnsatz für SVID2 — ohne M₀ und ohne
     * Bezugszeit (die kommen in Worttyp 9).
     */
    fun almanacSecond(bits: String): GalAlmanac? {
        if (bits.length < 121) return null
        val svid = unsigned(bits, 37, 6).toInt()
        if (svid !in 1..36) return null
        return GalAlmanac(
            svid = svid,
            iodA = unsigned(bits, 0, 4).toInt(),
            sqrtA = GalAlmanac.SQRT_A_NOMINAL + signed(bits, 43, 13) * P2_9,
            eccentricity = unsigned(bits, 56, 11) * P2_16,
            argumentOfPerigee = signed(bits, 67, 16) * P2_15 * PI,
            inclination = GalAlmanac.INCLINATION_NOMINAL + signed(bits, 83, 11) * P2_14 * PI,
            ascendingNode = signed(bits, 94, 16) * P2_15 * PI,
            nodeRate = signed(bits, 110, 11) * P2_33 * PI
        )
    }

    private fun unsigned(bits: String, pos: Int, length: Int): Long =
        java.lang.Long.parseLong(bits.substring(pos, pos + length), 2)

    private fun signed(bits: String, pos: Int, length: Int): Long {
        val raw = unsigned(bits, pos, length)
        return if (bits[pos] == '1') raw - (1L shl length) else raw
    }
}

/** Bahnwerte aus Worttyp 1. */
data class GalOrbitA(
    val iodNav: Int,
    /** Bezugszeit der Bahn in Sekunden der Galileo-Woche. */
    val toeSeconds: Int,
    val m0: Double,
    val eccentricity: Double,
    val sqrtA: Double
)

/** Bahnwerte aus Worttyp 2. */
data class GalOrbitB(
    val iodNav: Int,
    val omega0: Double,
    val i0: Double,
    val omega: Double,
    val idot: Double
)

/** Bahnwerte und SISA aus Worttyp 3. */
data class GalOrbitC(
    val iodNav: Int,
    val omegaDot: Double,
    val deltaN: Double,
    val cuc: Double,
    val cus: Double,
    val crc: Double,
    val crs: Double,
    /** SISA als Rohindex; der Nennwert steht in [GalEphemeris.sisaMetres]. */
    val sisaIndex: Int
)

/** Uhr und Neigungsstörglieder aus Worttyp 4. */
data class GalClock(
    val iodNav: Int,
    /** Die Satellitenkennung, die der Satz selbst nennt. */
    val svid: Int,
    val cic: Double,
    val cis: Double,
    /** Bezugszeit der Uhr in Sekunden der Galileo-Woche. */
    val tocSeconds: Int,
    val af0: Double,
    val af1: Double,
    val af2: Double
)

/** Gesundheit, Gruppenlaufzeiten und Zeitbezug aus Worttyp 5. */
data class GalHealth(
    /** Gruppenlaufzeit E5a/E1 in Sekunden. */
    val bgdE5aE1: Double,
    /** Gruppenlaufzeit E5b/E1 in Sekunden. */
    val bgdE5bE1: Double,
    val health: Int,
    /** Galileo-Woche (GPS-Wochenzählung). */
    val week: Int,
    /** Sendezeit des Seitenteils in Sekunden der Galileo-Woche. */
    val transmissionSeconds: Int
)

/** Koeffizienten des NeQuick-G-Ionosphärenmodells aus Worttyp 5. */
data class GalIonosphere(
    /** Effektive Ionisation erster Ordnung in SFU. */
    val ai0: Double,
    /** Erster Ordnungskoeffizient der Sonnenaktivität. */
    val ai1: Double,
    /** Zweiter Ordnungskoeffizient der Sonnenaktivität. */
    val ai2: Double,
    /** Störungsmeldungen des Modells (Az); nur durchgereicht, nicht ausgewertet. */
    val disturbanceFlags: Int
)

/**
 * Ein vollständiger Galileo-Datensatz aus den Worttypen 1–5.
 *
 * Alle Zeiten sind Galileo-Zeit (GST). Sie läuft mit der GPS-Zeit gleich, hat
 * aber einen eigenen Nullpunkt (1999-08-22); Wochenzahl und Sekunden stimmen
 * deshalb mit der GPS-Zählung überein, und die RINEX-Datei bekommt sie
 * unverändert.
 */
data class GalEphemeris(
    val prn: Int,
    val orbitA: GalOrbitA,
    val orbitB: GalOrbitB,
    val orbitC: GalOrbitC,
    val clock: GalClock,
    val health: GalHealth
) {
    /** Kennung der Datengeneration, in allen vier Bahnwörtern dieselbe. */
    val iodNav: Int get() = orbitA.iodNav

    /** Bezugszeit der Bahn in Sekunden der Galileo-Woche. */
    val toeSeconds: Int get() = orbitA.toeSeconds

    /** Galileo-Woche (gleiche Zählung wie die GPS-Woche). */
    val week: Int get() = health.week

    /** SISA als Nennwert in Metern (Index 107 heißt 3,12 m). */
    val sisaMetres: Double get() = GalLnav.sisaMetres(orbitC.sisaIndex)

    /** Bahnradius in Kilometern — Galileo fliegt bei rund 29 600 km. */
    val orbitRadiusKm: Double get() = orbitA.sqrtA * orbitA.sqrtA / 1000.0

    /**
     * Uhr und Bahn beziehen sich auf denselben Zeitpunkt.
     *
     * Bei Galileo gilt `toc == toe`; nur über den Tageswechsel hinweg steht
     * einmal 0 und einmal 86 400. Verglichen wird deshalb modulo eines Tages.
     */
    val hasConsistentClockReference: Boolean
        get() = Math.floorMod(clock.tocSeconds - toeSeconds, SECONDS_PER_DAY) == 0

    /**
     * Die Bahn ist plausibel: mittlere große Halbachse im Galileo-Bereich und
     * eine Exzentrizität, wie sie für MEO-Satelliten üblich ist.
     *
     * Diese Prüfung ist der Fangnetz für falsch gepackte Rohdaten — lieber kein
     * Satz als eine Bahn, die in der Datei steht und nicht stimmt.
     */
    val hasPlausibleOrbit: Boolean
        get() = orbitRadiusKm in MIN_RADIUS_KM..MAX_RADIUS_KM &&
            orbitA.eccentricity in 0.0..MAX_ECCENTRICITY

    companion object {
        private const val SECONDS_PER_DAY = 86_400

        /** Galileo-MEO liegt bei 29 600 km; die Grenzen sind großzügig. */
        private const val MIN_RADIUS_KM = 25_000.0
        private const val MAX_RADIUS_KM = 32_000.0
        private const val MAX_ECCENTRICITY = 0.02

        /**
         * Worttypen 1–5 verbinden; null, wenn sie nicht zusammenpassen.
         *
         * Die Prüfungen stehen in der Klassenbeschreibung von [GalLnav].
         */
        fun of(
            prn: Int,
            orbitA: GalOrbitA,
            orbitB: GalOrbitB,
            orbitC: GalOrbitC,
            clock: GalClock,
            health: GalHealth
        ): GalEphemeris? {
            if (orbitA.iodNav != orbitB.iodNav ||
                orbitA.iodNav != orbitC.iodNav ||
                orbitA.iodNav != clock.iodNav
            ) {
                return null
            }
            // Der Satz nennt seine eigene Kennung. Passt sie nicht zur Meldung,
            // ist die Zuordnung des Geräts falsch — dann lieber verwerfen.
            if (clock.svid != prn) return null
            val eph = GalEphemeris(prn, orbitA, orbitB, orbitC, clock, health)
            return if (eph.hasConsistentClockReference && eph.hasPlausibleOrbit) eph else null
        }
    }
}

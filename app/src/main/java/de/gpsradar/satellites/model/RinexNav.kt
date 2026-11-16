package de.gpsradar.satellites.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Schreibt dekodierte GPS- und GLONASS-Ephemeriden als RINEX-3-Navigationsdatei.
 *
 * Spaltenbreiten und Feldreihenfolge sind aus RTKLIB abgeleitet
 * (`src/rinex.c`: `readrnxnavb` liest je Wert 19 Zeichen, `decode_eph` und
 * `decode_geph` legen die Reihenfolge fest). Wer hier aus dem Gedächtnis
 * formatiert, bekommt eine Datei, die aussieht wie RINEX und von strengen
 * Lesern verworfen wird.
 *
 * Zwei Fallen, die beim ersten Versuch zugeschlagen haben:
 *
 * - Die Wochenzahl steht im Signal nur 10 Bit breit. In die Datei gehört die
 *   volle Woche, sonst landet der Satz Jahrzehnte vor der Zeit.
 * - Die Kennung jeder Kopfzeile muss rechtsbündig in Spalte 60–80 stehen.
 */
object RinexNav {

    /** 1980-01-06 00:00 UTC, Beginn der GPS-Zeitzaehlung. */
    private const val GPS_EPOCH_MILLIS = 315_964_800_000L

    /** 2006-01-01 00:00, Beginn der BeiDou-Zeitzaehlung (BDT). */
    private const val BDT_EPOCH_MILLIS = 1_136_073_600_000L

    private const val SECONDS_PER_WEEK = 604_800L

    /**
     * Dateiinhalt erzeugen. GPS-Sätze mit unvollständigem oder widersprüchlichem
     * IODE und GLONASS-Sätze ohne plausible Bahn werden übersprungen — lieber
     * eine kürzere richtige Datei als eine lange mit einem falschen Satz.
     */
    fun write(
        gps: List<GpsEphemeris>,
        glo: List<GloEphemeris> = emptyList(),
        bds: List<BdsEphemeris> = emptyList(),
        gal: List<GalEphemeris> = emptyList(),
        corrections: NavCorrections = NavCorrections()
    ): String {
        val gpsSaetze = gps.filter { it.hasConsistentIode }.sortedBy { it.prn }
        val gloSaetze = glo.filter { it.hasPlausibleOrbit }.sortedBy { it.satelliteSlot }
        val bdsSaetze = bds.filter { it.hasConsistentToc }.sortedBy { it.prn }
        val galSaetze = gal.filter { it.hasPlausibleOrbit }.sortedBy { it.prn }
        val sb = StringBuilder()
        sb.append(
            header(
                gpsSaetze.size + gloSaetze.size + bdsSaetze.size + galSaetze.size,
                gloSaetze.isNotEmpty(),
                bdsSaetze.isNotEmpty(),
                galSaetze.isNotEmpty(),
                corrections
            )
        )
        for (e in gpsSaetze) sb.append(record(e))
        for (e in gloSaetze) sb.append(gloRecord(e))
        for (e in bdsSaetze) sb.append(bdsRecord(e))
        for (e in galSaetze) sb.append(galRecord(e))
        return sb.toString()
    }

    /** Ein Satz zu acht Zeilen, wie RINEX 3 es verlangt. */
    fun record(e: GpsEphemeris): String {
        val a = e.orbitA
        val b = e.orbitB
        val c = e.clock
        val parts = calendar(e.week, c.tocSeconds)
        val sb = StringBuilder()
        sb.append(
            String.format(
                Locale.US,
                "%s %4d %02d %02d %02d %02d %02d",
                satelliteId(e.prn), parts[0], parts[1], parts[2],
                parts[3], parts[4], parts[5]
            )
        )
        sb.append(plainValues(c.af0, c.af1, c.af2)).append('\n')
        sb.append(values(a.iode.toDouble(), a.crs, a.deltaN, a.m0)).append('\n')
        sb.append(values(a.cuc, a.eccentricity, a.cus, a.sqrtA)).append('\n')
        sb.append(values(a.toeSeconds.toDouble(), b.cic, b.omega0, b.cis)).append('\n')
        sb.append(values(b.i0, b.crc, b.omega, b.omegaDot)).append('\n')
        sb.append(
            values(b.idot, c.l2Codes.toDouble(), e.week.toDouble(), if (c.l2pFlag) 1.0 else 0.0)
        ).append('\n')
        sb.append(values(c.ura.toDouble(), c.health.toDouble(), c.tgd, c.iodc.toDouble()))
            .append('\n')
        // Sendezeit (Sekunden der Woche) und Fit-Intervall; der Rest bleibt 0.
        sb.append(values(e.transmissionSeconds.toDouble(), a.fitHours, 0.0, 0.0)).append('\n')
        return sb.toString()
    }

    private fun satelliteId(prn: Int): String = String.format(Locale.US, "G%02d", prn)

    /**
     * Ein GLONASS-Satz zu vier Zeilen.
     *
     * Reihenfolge und Einheiten stammen aus RTKLIB (`outrnxgnavb`): Uhrversatz,
     * relative Frequenzabweichung, Rahmenzeit — dann je Achse Ort (km),
     * Geschwindigkeit (km/s), Beschleunigung (km/s²) und ein Zusatzwert
     * (Gesundheit, Frequenznummer, Alter der Daten).
     *
     * Drei Feinheiten:
     *
     * - In Feld 1 steht **−τn**: RTKLIB liest `geph->taun = -data[0]` und
     *   rechnet die Uhrkorrektur als `taun + gamn·t`. Am Mitschnitt geprüft:
     *   τn = −7,4498·10⁻⁵ s entspricht der BRDC-Zeile +7,4496·10⁻⁵.
     * - Die Rahmenzeit ist die des Gültigkeitsbeginns `tb` (nicht die laufende
     *   Rahmenzeit aus String 1) und steht als Sekunden der GPS-Woche in UTC,
     *   so wie in den BRDC-Dateien. RTKLIB nimmt davon `fmod(…, 86400)` und
     *   setzt das Datum aus der Epoche.
     * - Die Frequenznummer ist 0, solange das Gerät sie nicht liefert (siehe
     *   [GloLnav]); RTKLIB setzt sie bei eigenen Rohdaten-Ephemeriden genauso.
     */
    fun gloRecord(e: GloEphemeris): String {
        val parts = calendarFromMillis(e.tbMillis)
        val sb = StringBuilder()
        sb.append(
            String.format(
                Locale.US,
                "R%02d %4d %02d %02d %02d %02d %02d",
                e.satelliteSlot, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]
            )
        )
        sb.append(
            plainValues(-e.tauSeconds, e.gamma, weekSecondsUtc(e.tbMillis))
        ).append('\n')
        sb.append(
            values(
                e.x.positionKm, e.x.velocityKmPerSecond,
                e.x.accelerationKmPerSecond2, e.health.toDouble()
            )
        ).append('\n')
        sb.append(
            values(
                e.y.positionKm, e.y.velocityKmPerSecond,
                e.y.accelerationKmPerSecond2, (e.frequencyChannel ?: 0).toDouble()
            )
        ).append('\n')
        sb.append(
            values(
                e.z.positionKm, e.z.velocityKmPerSecond,
                e.z.accelerationKmPerSecond2, e.ageOfDataDays.toDouble()
            )
        ).append('\n')
        return sb.toString()
    }

    /**
     * Ein BeiDou-Satz zu acht Zeilen.
     *
     * Reihenfolge aus RTKLIB (`outrnxnavb`), auf der Leseseite gegengelesen
     * (`decode_...`, BDS-Zweig in `readrnxnavb`): AODE/Crs/Δn/M0,
     * Cuc/e/Cus/√A, Toe/Cic/Ω0/Cis, i0/Crc/ω/Ω̇, dann i̇, Kennung, Woche,
     * Reserve — und in der siebten Zeile **URA in Metern, nicht als Index**:
     * RTKLIB wendet beim Lesen `uraindex()` auf den Zahlenwert an.
     *
     * Die Epoche steht in BeiDou-Zeit (BDT), wie es RINEX für C-Sätze verlangt.
     * BDT beginnt am 2006-01-01 ohne Schaltsekundenübertrag und liegt deshalb
     * heute 14 Sekunden vor GPS-Zeit — umgerechnet wird hier nichts, weil die
     * Datei genau diese Zeitbasis erwartet.
     */
    fun bdsRecord(e: BdsEphemeris): String {
        val parts = calendarFromMillis(
            BDT_EPOCH_MILLIS +
                (e.clock.week * SECONDS_PER_WEEK + e.clock.tocSeconds) * 1000L
        )
        val sb = StringBuilder()
        sb.append(
            String.format(
                Locale.US,
                "C%02d %4d %02d %02d %02d %02d %02d",
                e.prn, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]
            )
        )
        sb.append(plainValues(e.clock.a0, e.clock.a1, e.clock.a2)).append('\n')
        sb.append(
            values(e.clock.aode.toDouble(), e.orbitA.crs, e.orbitA.deltaN, e.orbitA.m0)
        ).append('\n')
        sb.append(
            values(
                e.orbitA.cuc, e.orbitA.eccentricity, e.orbitA.cus, e.orbitA.sqrtA
            )
        ).append('\n')
        sb.append(
            values(e.toeSeconds.toDouble(), e.orbitB.cic, e.orbitB.omega0, e.orbitB.cis)
        ).append('\n')
        sb.append(
            values(e.orbitB.i0, e.orbitA.crc, e.orbitB.omega, e.orbitB.omegaDot)
        ).append('\n')
        sb.append(values(e.orbitB.idot, 0.0, e.clock.week.toDouble(), 0.0)).append('\n')
        sb.append(
            values(
                uraNominal(e.clock.ura), e.clock.health.toDouble(),
                e.clock.tgd1, e.clock.tgd2
            )
        ).append('\n')
        sb.append(
            values(e.transmissionSeconds.toDouble(), e.clock.aodc.toDouble(), 0.0, 0.0)
        ).append('\n')
        return sb.toString()
    }

    /**
     * Ein Galileo-Satz zu acht Zeilen.
     *
     * Reihenfolge aus RTKLIB (`outrnxnavb`): af0–af2, dann IODnav/Crs/Δn/M0,
     * Cuc/e/Cus/√A, Toe/Cic/Ω0/Cis, i0/Crc/ω/Ω̇, i̇/Datenquelle/Woche/Reserve,
     * **SISA in Metern** mit Gesundheit und beiden Gruppenlaufzeiten, zuletzt die
     * Sendezeit.
     *
     * Drei Feinheiten:
     *
     * - **SISA** steht im Signal als Index. In der Datei erwarten die Leser den
     *   Nennwert in Metern und rechnen ihn beim Einlesen wieder auf den Index
     *   zurück (`sisa_index` in RTKLIB) — Index 107 heißt 3,12 m.
     * - **Datenquelle 512** (Bit 9) sagt: af0–af2 und toc gelten für E5b–E1.
     *   RTKLIB setzt beim Dekodieren von I/NAV genau diesen Wert. Die
     *   BRDC-Dateien desselben Tages führen 517, also zusätzlich Bit 0 („Quelle
     *   ist I/NAV E1-B") und Bit 2 („F/NAV E5b-I") — für eine Datei, die nur aus
     *   I/NAV entsteht, wäre Bit 2 falsch.
     * - Die Epoche steht in Galileo-Zeit, die mit der GPS-Zeit gleichläuft und
     *   deshalb mit derselben Rechnung wie bei GPS gebildet wird.
     */
    fun galRecord(e: GalEphemeris): String {
        val parts = calendar(e.week, e.clock.tocSeconds)
        val sb = StringBuilder()
        sb.append(
            String.format(
                Locale.US,
                "E%02d %4d %02d %02d %02d %02d %02d",
                e.prn, parts[0], parts[1], parts[2], parts[3], parts[4], parts[5]
            )
        )
        sb.append(plainValues(e.clock.af0, e.clock.af1, e.clock.af2)).append('\n')
        sb.append(
            values(e.iodNav.toDouble(), e.orbitC.crs, e.orbitC.deltaN, e.orbitA.m0)
        ).append('\n')
        sb.append(
            values(e.orbitC.cuc, e.orbitA.eccentricity, e.orbitC.cus, e.orbitA.sqrtA)
        ).append('\n')
        sb.append(
            values(e.toeSeconds.toDouble(), e.clock.cic, e.orbitB.omega0, e.clock.cis)
        ).append('\n')
        sb.append(
            values(e.orbitB.i0, e.orbitC.crc, e.orbitB.omega, e.orbitC.omegaDot)
        ).append('\n')
        sb.append(
            values(
                e.orbitB.idot, GAL_DATA_SOURCES_I_NAV.toDouble(), e.week.toDouble(), 0.0
            )
        ).append('\n')
        sb.append(
            values(
                e.sisaMetres, e.health.health.toDouble(),
                e.health.bgdE5aE1, e.health.bgdE5bE1
            )
        ).append('\n')
        sb.append(
            values(transmissionSeconds(e).toDouble(), 0.0, 0.0, 0.0)
        ).append('\n')
        return sb.toString()
    }

    /**
     * Sendezeit des Seitenteils in Sekunden der Galileo-Woche.
     *
     * Sie kommt aus Worttyp 5 und kann über den Wochenwechsel hinweg auf der
     * anderen Seite von `toe` liegen; dann wird sie um eine Woche verschoben,
     * damit sie im selben Bezug wie der Satz steht.
     */
    private fun transmissionSeconds(e: GalEphemeris): Int {
        var ttr = e.health.transmissionSeconds
        while (ttr - e.toeSeconds > SECONDS_PER_WEEK / 2) ttr -= SECONDS_PER_WEEK.toInt()
        while (ttr - e.toeSeconds < -SECONDS_PER_WEEK / 2) ttr += SECONDS_PER_WEEK.toInt()
        return ttr
    }

    /**
     * Datenquelle im Galileo-Satz: Bit 9 — Uhr und Bezugszeit gelten für
     * E5b–E1. RTKLIB setzt beim Dekodieren von I/NAV denselben Wert.
     */
    private const val GAL_DATA_SOURCES_I_NAV = 512

    /**
     * URA-Index auf seinen Nennwert in Metern — dieselbe Tabelle wie in RTKLIB
     * (`ura_nominal` in `rinex.c`). Index 0 heißt 2,0 m; alles ab 15 ist
     * „unbekannt" und wird als 8192 m geschrieben.
     *
     * **Bewusst auch für BeiDou:** Die B1I-ICD (Tabelle 5-4) nennt für ihren
     * URAI andere Grenzen (2,40 / 3,40 / 4,85 / 6,85 … m). Geschrieben wird
     * trotzdem die GPS-Treppe, weil RTKLIB beim Einlesen mit `uraindex()` genau
     * diese Tabelle verwendet: 2,8 m ergibt dort wieder Index 1, ein Wert von
     * 3,40 m dagegen Index 2. Mit der BeiDou-Tabelle wäre die Datei also nicht
     * mehr verlustfrei lesbar — der Zahlenwert sähe genauer aus und wäre falsch.
     */
    private fun uraNominal(index: Int): Double =
        if (index in URA_NOMINAL.indices) URA_NOMINAL[index] else 8192.0

    private val URA_NOMINAL = doubleArrayOf(
        2.0, 2.8, 4.0, 5.7, 8.0, 11.3, 16.0, 32.0,
        64.0, 128.0, 256.0, 512.0, 1024.0, 2048.0, 4096.0, 8192.0
    )

    /**
     * Zeitpunkt in Sekunden der GPS-Woche, aber auf der UTC-Zeitskala.
     *
     * RTKLIB schreibt GLONASS-Zeiten so (`time2gpst(gpst2utc(tof))`); die
     * BRDC-Dateien enthalten dieselbe Zählung (Beispiel: 574 200 heißt Samstag
     * 15:30:00 UTC). Unix-Millisekunden zählen ohne Schaltsekunden, deshalb ist
     * die Differenz zur GPS-Epoche genau diese Kalenderzählung.
     */
    private fun weekSecondsUtc(millis: Long): Double =
        ((millis - GPS_EPOCH_MILLIS) / 1000L).toDouble() % SECONDS_PER_WEEK

    /** Vier Werte zu je 19 Zeichen, mit den vier Füllzeichen am Zeilenanfang. */
    private fun values(vararg v: Double): String = "    " + plainValues(*v)

    /** Vier Werte ohne Einzug – so beginnt die erste Zeile eines Satzes (nach der Epoche). */
    private fun plainValues(vararg v: Double): String {
        val sb = StringBuilder()
        for (x in v) sb.append(String.format(Locale.US, "%19.12E", x))
        return sb.toString()
    }

    /** Kopfzeilen; die Kennung rechtsbündig in Spalte 60–80. */
    private fun header(
        count: Int,
        mitGlonass: Boolean,
        mitBeidou: Boolean = false,
        mitGalileo: Boolean = false,
        corrections: NavCorrections = NavCorrections()
    ): String = buildString {
        // RINEX 3 verlangt in Zeile 1 nicht nur den Dateityp, sondern in
        // Spalte 41 auch das System. Bis 16.11. stand hier der RINEX-2-Text
        // („NAVIGATION DATA"), womit die Datei formal keinem RINEX-3-Nav-File
        // entsprach — RTKLIB las sie trotzdem, andere Werkzeuge vielleicht nicht.
        val system = if (mitGlonass || mitBeidou || mitGalileo) "M: Mixed" else "G: GPS"
        val kopf = String.format(
            Locale.US, "%9.2f           %-20s%-20s", 3.04, "N: GNSS NAV DATA", system
        )
        append(headerLine(kopf, "RINEX VERSION / TYPE"))
        val systeme = buildString {
            append("       GPS")
            if (mitGlonass) append(" GLO")
            if (mitBeidou) append(" BDS")
            if (mitGalileo) append(" GAL")
        }
        append(headerLine("GPS-Satelliten$systeme", "PGM / RUN BY / DATE"))
        append(headerLine("Aus Navigationsnachrichten dekodiert", "COMMENT"))
        append(headerLine(String.format(Locale.US, "%6d", count), "# OF SATELLITES"))
        // Ionosphäre und UTC-Bezug: RTKLIB rechnet das Klobuchar-Modell nur,
        // wenn diese Zeilen im Kopf stehen. Ohne sie bleibt der ionosphärische
        // Fehler (5 bis 15 m) in der Einzelpunktlösung steckend.
        corrections.gpsIono?.takeIf { it.isUsable }?.let {
            append(ionoLine("GPSA", it.alpha))
            append(ionoLine("GPSB", it.beta))
        }
        corrections.galIono?.let {
            append(ionoLine("GAL", listOf(it.ai0, it.ai1, it.ai2)))
        }
        corrections.bdsIono?.takeIf { it.isUsable }?.let {
            append(ionoLine("BDSA", it.alpha))
            append(ionoLine("BDSB", it.beta))
        }
        corrections.gpsUtc?.takeIf { it.isUsable }?.let { append(utcLine("GPUT", it)) }
        corrections.galUtc?.takeIf { it.isUsable }?.let { append(utcLine("GAUT", it)) }
        corrections.bdsUtc?.takeIf { it.isUsable }?.let { append(utcLine("BDUT", it)) }
        append(headerLine("", "END OF HEADER"))
    }

    /**
     * Eine Ionozeile: vierstelliges Kürzel, dann Werte zu je zwölf Zeichen.
     *
     * RTKLIB liest die Werte aus festen Spalten (5, 17, 29, 41 — je zwölf
     * Zeichen), deshalb `%12.4E` statt einer eigenen Mantissenform. Galileo hat
     * nur drei Koeffizienten und benutzt das Kürzel „GAL“ ohne Buchstaben.
     */
    private fun ionoLine(label: String, values: List<Double>): String {
        val sb = StringBuilder(String.format(Locale.US, "%-4s ", label))
        for (v in values) sb.append(String.format(Locale.US, "%12.4E", v))
        return headerLine(sb.toString(), "IONOSPHERIC CORR")
    }

    /**
     * Eine UTC-Zeile: Kürzel, A₀ (17 Zeichen), A₁ (16), Bezugszeit (7), Woche (5).
     *
     * Die Spaltenbreiten sind die von RTKLIB (`out_time_sys`), damit dessen
     * spaltenweiser Leser die Werte wiederfindet.
     */
    private fun utcLine(label: String, u: UtcCorrection): String {
        val sb = StringBuilder(String.format(Locale.US, "%-4s ", label))
        sb.append(String.format(Locale.US, "%17.10E", u.a0))
        sb.append(String.format(Locale.US, "%16.9E", u.a1))
        sb.append(String.format(Locale.US, "%7d%5d", u.referenceSeconds, u.weekNumber))
        return headerLine(sb.toString(), "TIME SYSTEM CORR")
    }

    private fun headerLine(text: String, label: String): String =
        text.padEnd(60) + label.padStart(20) + "\n"

    /**
     * GPS-Woche und Sekunden in Jahr, Monat, Tag, Stunde, Minute, Sekunde.
     *
     * Bewusst ohne Schaltsekunden: RINEX erwartet bei GPS-Sätzen die GPS-Zeit.
     */
    private fun calendar(week: Int, secondsOfWeek: Int): IntArray =
        calendarFromMillis(GPS_EPOCH_MILLIS + (week * SECONDS_PER_WEEK + secondsOfWeek) * 1000L)

    /** Derselbe Aufbau für einen fertigen Zeitpunkt (UTC-Millisekunden). */
    private fun calendarFromMillis(millis: Long): IntArray {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        cal.timeInMillis = millis
        return intArrayOf(
            cal.get(Calendar.YEAR),
            cal.get(Calendar.MONTH) + 1,
            cal.get(Calendar.DAY_OF_MONTH),
            cal.get(Calendar.HOUR_OF_DAY),
            cal.get(Calendar.MINUTE),
            cal.get(Calendar.SECOND)
        )
    }
}

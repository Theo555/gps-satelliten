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
        bds: List<BdsEphemeris> = emptyList()
    ): String {
        val gpsSaetze = gps.filter { it.hasConsistentIode }.sortedBy { it.prn }
        val gloSaetze = glo.filter { it.hasPlausibleOrbit }.sortedBy { it.satelliteSlot }
        val bdsSaetze = bds.filter { it.hasConsistentToc }.sortedBy { it.prn }
        val sb = StringBuilder()
        sb.append(
            header(
                gpsSaetze.size + gloSaetze.size + bdsSaetze.size,
                gloSaetze.isNotEmpty(),
                bdsSaetze.isNotEmpty()
            )
        )
        for (e in gpsSaetze) sb.append(record(e))
        for (e in gloSaetze) sb.append(gloRecord(e))
        for (e in bdsSaetze) sb.append(bdsRecord(e))
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
     * URA-Index auf seinen Nennwert in Metern — dieselbe Tabelle wie in RTKLIB
     * (`ura_nominal` in `rinex.c`). Index 0 heißt 2,0 m; alles ab 15 ist
     * „unbekannt" und wird als 8192 m geschrieben.
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
        mitBeidou: Boolean = false
    ): String = buildString {
        // RINEX 3 verlangt in Zeile 1 nicht nur den Dateityp, sondern in
        // Spalte 41 auch das System. Bis 16.11. stand hier der RINEX-2-Text
        // („NAVIGATION DATA"), womit die Datei formal keinem RINEX-3-Nav-File
        // entsprach — RTKLIB las sie trotzdem, andere Werkzeuge vielleicht nicht.
        val system = if (mitGlonass || mitBeidou) "M: Mixed" else "G: GPS"
        val kopf = String.format(
            Locale.US, "%9.2f           %-20s%-20s", 3.04, "N: GNSS NAV DATA", system
        )
        append(headerLine(kopf, "RINEX VERSION / TYPE"))
        val systeme = buildString {
            append("       GPS")
            if (mitGlonass) append(" GLO")
            if (mitBeidou) append(" BDS")
        }
        append(headerLine("GPS-Satelliten$systeme", "PGM / RUN BY / DATE"))
        append(headerLine("Aus Navigationsnachrichten dekodiert", "COMMENT"))
        append(headerLine(String.format(Locale.US, "%6d", count), "# OF SATELLITES"))
        append(headerLine("", "END OF HEADER"))
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

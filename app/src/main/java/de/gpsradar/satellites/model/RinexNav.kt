package de.gpsradar.satellites.model

import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/**
 * Schreibt dekodierte GPS-Ephemeriden als RINEX-3-Navigationsdatei.
 *
 * Spaltenbreiten und Feldreihenfolge sind aus RTKLIB abgeleitet
 * (`src/rinex.c`: `readrnxnavb` liest je Wert 19 Zeichen, `decode_eph` legt die
 * Reihenfolge fest). Wer hier aus dem Gedächtnis formatiert, bekommt eine Datei,
 * die aussieht wie RINEX und von strengen Lesern verworfen wird.
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

    private const val SECONDS_PER_WEEK = 604_800L

    /**
     * Dateiinhalt erzeugen. Sätze mit unvollständigem oder widersprüchlichem
     * IODE werden übersprungen — lieber eine kürzere richtige Datei als eine
     * lange mit einem falschen Satz.
     */
    fun write(ephemerides: List<GpsEphemeris>): String {
        val brauchbar = ephemerides.filter { it.hasConsistentIode }.sortedBy { it.prn }
        val sb = StringBuilder()
        sb.append(header(brauchbar.size))
        for (e in brauchbar) sb.append(record(e))
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

    /** Vier Werte zu je 19 Zeichen, mit den vier Füllzeichen am Zeilenanfang. */
    private fun values(vararg v: Double): String = "    " + plainValues(*v)

    /** Vier Werte ohne Einzug – so beginnt die erste Zeile eines Satzes (nach der Epoche). */
    private fun plainValues(vararg v: Double): String {
        val sb = StringBuilder()
        for (x in v) sb.append(String.format(Locale.US, "%19.12E", x))
        return sb.toString()
    }

    /** Kopfzeilen; die Kennung steht rechtsbündig in Spalte 60–80. */
    private fun header(count: Int): String = buildString {
        append(headerLine("     3.04           NAVIGATION DATA", "RINEX VERSION / TYPE"))
        append(headerLine("GPS-Satelliten     GPS", "PGM / RUN BY / DATE"))
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
    private fun calendar(week: Int, secondsOfWeek: Int): IntArray {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.US)
        cal.timeInMillis = GPS_EPOCH_MILLIS + (week * SECONDS_PER_WEEK + secondsOfWeek) * 1000L
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

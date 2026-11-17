package de.gpsradar.satellites.model

import java.util.Locale

/**
 * Zielprofil für den Rohdaten-Export. Trennzeichen, Dezimalzeichen und BOM
 * sind bewusst gekoppelt: nur die Kombination `;` + `,` öffnet in deutschem
 * Excel direkt, nur `,` + `.` ist RFC 4180.
 */
enum class CsvProfile(val delimiter: Char, val decimalPoint: Char, val includeBom: Boolean) {
    UNIVERSAL(',', '.', false),
    EXCEL_DE(';', ',', true)
}

/** Metadaten für den Kommentarkopf – vom Aufrufer befüllt, damit der Writer rein bleibt. */
data class CsvMeta(
    val device: String,
    val android: String,
    val app: String,
    val exportedAtUtc: String,
    /**
     * Was das Gerät kann – trennt „kann nicht“ von „kam gerade nicht“.
     * Null lässt die Zeilen weg (ältere Aufrufer, Tests).
     */
    val capabilities: DeviceCapabilities? = null
)

/**
 * Schreibt die GNSS-Roh-Messungen als CSV (eine Zeile pro Satellit × Band).
 * Reine Kotlin-Funktion ohne Android-Abhängigkeit – dadurch unit-testbar.
 */
object MeasurementCsv {

    private val HEADER = listOf(
        "epoch", "svid", "constellation", "band", "freq_hz", "cn0_dbhz",
        "time_ns", "full_bias_ns", "bias_ns", "drift_nsps", "leap_s",
        "week_offset_ns", "t_tx_raw_ns", "t_tx_ns", "sv_time_unc_ns",
        "pseudorange_m", "doppler_mps", "adr_m", "adr_state", "phase_unc_m",
        "state_flags", "code_lock", "tow_known", "msec_ambiguous", "multipath",
        // Neue Spalten am Ende, damit Leser nach Spaltennamen weiter funktionieren.
        "pr_status",
        // Signalqualität und Uhrgüte – leer, wenn das Gerät sie nicht liefert.
        "agc_db", "snr_in_db", "baseband_cn0_dbhz", "code_type",
        "bias_unc_ns", "drift_unc_nsps", "elapsed_realtime_ns", "hw_clock_disc",
        // Lage am Himmel – aus dem Satellitenstatus, nicht aus der Messung.
        "elev_deg", "azim_deg"
    )

    fun toCsv(
        epochs: List<RawEpoch>,
        meta: CsvMeta,
        profile: CsvProfile
    ): String {
        val lines = mutableListOf<String>()
        val totalRows = epochs.sumOf { it.rows.size }

        // Kommentarkopf (#-Zeilen, vor der Kopfzeile).
        val delimiterName = if (profile.delimiter == ',') "Komma" else "Semikolon"
        val decimalName = if (profile.decimalPoint == ',') "Komma" else "Punkt"
        lines += comment("format", "RFC 4180 · UTF-8 · CRLF · Trennzeichen $delimiterName · Dezimalzeichen $decimalName", profile)
        lines += comment("export_utc", meta.exportedAtUtc, profile)
        lines += comment("geraet", meta.device, profile)
        lines += comment("android", meta.android, profile)
        lines += comment("app", meta.app, profile)
        lines += comment("aufzeichnung", "${epochs.size} Epochen · $totalRows Zeilen · 1 Epoche = ein GnssMeasurementsEvent", profile)
        lines += comment("zeitbasis", "export_utc ist die Geräteuhr · time_ns ist GNSS-Zeit · alle *_ns sind Nanosekunden · Epochen können Lücken haben · t_tx_ns ist die Sendezeit in GPS-Zeitbasis (Zeitbasis je Konstellation, GLONASS ab Systemtag) und week_offset_ns der dafür angewandte Versatz · fehlt leap_s, meldet das Gerät keine Schaltsekunde und es gilt der Rückfallwert 18 s", profile)
        lines += comment("hinweis", "adr_m ist die aufsummierte Trägerphase in Metern seit dem letzten Lock-Verlust (kumulativ, jeder Satellit ab eigenem Nullpunkt, keine absolute Phase) · vier Nachkommastellen = 0,1 mm · adr_state Bit 1 gesetzt bedeutet gültig", profile)
        lines += comment("oszillator", "adr_m und doppler_mps werden gegen den Empfängeroszillator gemessen und tragen dessen Frequenzversatz · beim S20 FE rund 500 ns/s (0,5 ppm, das sind etwa 150 m/s) · Korrektur: Phase minus c · Summe(drift_nsps · 1e-9 · Epochenabstand), Doppler minus c · drift_nsps · 1e-9 · pseudorange_m ist nicht betroffen, sie kommt aus full_bias_ns · ohne diese Korrektur läuft die Phase schneller als der Code und ist für Auswertungen nicht brauchbar", profile)
        lines += comment("flags", "state_flags: GnssMeasurement.getState() · adr_state: accumulatedDeltaRangeState · code_lock/tow_known/msec_ambiguous: 1 ja, 0 nein · pr_status: ok, no_clock, no_time oder implausible", profile)
        // Was das Gerät grundsätzlich kann. Erst zusammen mit "geliefert" lässt
        // sich eine leere Zelle deuten: kann es nicht, oder kam es nicht?
        meta.capabilities?.let { caps ->
            lines += comment("faehigkeiten", caps.csvSummary(), profile)
            lines += comment("signale", caps.csvSignals(), profile)
            lines += comment("antenne", caps.csvAntenna(), profile)
        }
        lines += comment("geliefert", deliverySummary(epochs), profile)

        lines += HEADER.joinToString(profile.delimiter.toString())

        var epochIndex = 0
        epochs.forEach { epoch ->
            epochIndex++
            val clock = epoch.clock
            val fullBiasNanos = clock?.fullBiasNanos?.takeIf { it != Long.MIN_VALUE }
            val biasNanos = clock?.biasNanos?.takeIf { !it.isNaN() }
            val driftNsPerSec = clock?.driftNsPerSec?.takeIf { !it.isNaN() }
            val leapSecond = clock?.leapSecond?.takeIf { it != Int.MIN_VALUE }
            // Empfangszeit in GNSS-Zeit; Grundlage der Sendezeit-Umrechnung.
            val tRxNanos: Long? = fullBiasNanos?.let { bias -> clock?.timeNanos?.minus(bias) }
            val leapSecondForTimeBase = clock?.leapSecond ?: 0

            epoch.rows.forEach { m ->
                val tTxNanos: Long? = m.tTxRawNanos?.let { raw ->
                    tRxNanos?.let { tRx ->
                        Pseudorange.absoluteTransmitNanos(m.constellation, raw, tRx, leapSecondForTimeBase)
                    }
                }
                val rawTxNanos = m.tTxRawNanos
                // Versatz, der tatsächlich angewandt wurde (volle Epoche + Zeitbasis).
                val appliedOffsetNanos: Long? =
                    if (rawTxNanos != null && tTxNanos != null) tTxNanos - rawTxNanos else null

                val cells = listOf(
                    epochIndex.toString(),
                    m.svid.toString(),
                    m.constellation.shortName,
                    m.bandShort,
                    fmtFloat(m.carrierFrequencyHz, "%.0f", profile),
                    fmtFloat(m.cn0DbHz, "%.1f", profile),
                    fmtLong(clock?.timeNanos),
                    fmtLong(fullBiasNanos),
                    fmtDouble(biasNanos, "%.6f", profile),
                    fmtDouble(driftNsPerSec, "%.6f", profile),
                    leapSecond?.toString() ?: "",
                    fmtLong(appliedOffsetNanos),
                    fmtLong(m.tTxRawNanos),
                    fmtLong(tTxNanos),
                    fmtLong(m.svTimeUncertaintyNanos),
                    fmtDouble(m.pseudorangeMeters, "%.3f", profile),
                    fmtDouble(m.dopplerMetersPerSec, "%.1f", profile),
                    // Trägerphase mit 0,1 mm. Mit einer Nachkommastelle wären es 0,1 m,
                    // bei L1 also gut eine halbe Wellenlänge -- damit ließe sich keine
                    // Phase auswerten. Der Sensor liefert den Wert vollauflösend.
                    fmtDouble(m.carrierPhaseMeters, "%.4f", profile),
                    m.adrState.toString(),
                    fmtDouble(m.carrierPhaseUncertaintyMeters, "%.3f", profile),
                    m.stateFlags.toString(),
                    bool01(m.codeLock),
                    bool01(m.towKnown),
                    bool01(m.msecAmbiguous),
                    multipathToken(m.multipath),
                    m.pseudorangeStatus.token,
                    // Signalqualität und Uhrgüte. Leer heisst: dieses Gerät liefert
                    // das Feld nicht – die Kommentarzeile "geliefert" sagt, welche.
                    fmtDouble(m.agcDb, "%.2f", profile),
                    fmtDouble(m.snrInDb, "%.2f", profile),
                    fmtDouble(m.basebandCn0DbHz, "%.2f", profile),
                    m.codeType ?: "",
                    fmtDouble(clock?.biasUncertaintyNanos, "%.3f", profile),
                    fmtDouble(clock?.driftUncertaintyNsPerSec, "%.6f", profile),
                    fmtLong(clock?.elapsedRealtimeNanos),
                    fmtLong(clock?.hardwareClockDiscontinuityCount?.toLong()),
                    fmtDouble(m.elevationDegrees, "%.1f", profile),
                    fmtDouble(m.azimuthDegrees, "%.1f", profile)
                )
                lines += cells.joinToString(profile.delimiter.toString())
            }
        }

        val body = lines.joinToString("\r\n") + "\r\n"
        return if (profile.includeBom) "\uFEFF$body" else body
    }

    private fun comment(key: String, value: String, profile: CsvProfile): String =
        "# $key = " + csvField(value, profile)

    /**
     * Sammelt, welche optionalen Felder vorkommen – über mehrere Epochen hinweg.
     *
     * Ein einzelner Messsatz kann ein Feld vermissen lassen, das das Gerät sehr
     * wohl kann: Die Satellitenlage etwa steht nur im Satellitenstatus, und der
     * kann beim allerersten Messsatz noch fehlen. Erst die Summe über eine
     * Aufzeichnung sagt, was das Gerät wirklich liefert.
     */
    internal class DeliveryTracker {
        private var agc = false
        private var snrIn = false
        private var basebandCn0 = false
        private var codeType = false
        private var clockUncertainty = false
        private var clockResets = false
        private var geometry = false

        fun add(rows: List<RawMeasurement>, clock: RawClockSnapshot?) {
            if (rows.any { it.agcDb != null }) agc = true
            if (rows.any { it.snrInDb != null }) snrIn = true
            if (rows.any { it.basebandCn0DbHz != null }) basebandCn0 = true
            if (rows.any { it.codeType != null }) codeType = true
            if (clock?.biasUncertaintyNanos != null) clockUncertainty = true
            if (clock?.hardwareClockDiscontinuityCount != null) clockResets = true
            if (rows.any { it.elevationDegrees != null && it.azimuthDegrees != null }) geometry = true
        }

        fun summary(): String {
            fun yesNo(present: Boolean) = if (present) "ja" else "nein"
            return listOf(
                "agc=" + yesNo(agc),
                "snr_in=" + yesNo(snrIn),
                "basisband_cn0=" + yesNo(basebandCn0),
                "code_type=" + yesNo(codeType),
                "uhr_unsicherheit=" + yesNo(clockUncertainty),
                "uhrspruenge=" + yesNo(clockResets),
                "elev_azim=" + yesNo(geometry)
            ).joinToString(" · ")
        }
    }

    /** Was dieses Gerät in dieser Aufzeichnung tatsächlich liefert – für die Kopfzeilen. */
    internal fun deliverySummary(epochs: List<RawEpoch>): String {
        val tracker = DeliveryTracker()
        epochs.forEach { tracker.add(it.rows, it.clock) }
        return tracker.summary()
    }

    private fun csvField(value: String, profile: CsvProfile): String {
        val needsQuote = value.any { it == profile.delimiter || it == '"' || it == '\n' || it == '\r' }
        return if (needsQuote) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun fmtLong(value: Long?): String = value?.toString() ?: ""

    private fun fmtDouble(value: Double?, pattern: String, profile: CsvProfile): String {
        if (value == null || value.isNaN() || value.isInfinite()) return ""
        return String.format(Locale.US, pattern, value).replace('.', profile.decimalPoint)
    }

    private fun fmtFloat(value: Float, pattern: String, profile: CsvProfile): String {
        if (value.isNaN() || value.isInfinite()) return ""
        return String.format(Locale.US, pattern, value).replace('.', profile.decimalPoint)
    }

    private fun bool01(value: Boolean): String = if (value) "1" else "0"

    private fun multipathToken(m: MultipathState): String = when (m) {
        MultipathState.UNKNOWN -> "UNKNOWN"
        MultipathState.DETECTED -> "DETECTED"
        MultipathState.NOT_DETECTED -> "NOT_DETECTED"
    }
}

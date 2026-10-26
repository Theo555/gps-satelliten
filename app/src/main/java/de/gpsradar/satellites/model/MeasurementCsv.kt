package de.gpsradar.satellites.model

import java.util.Locale
import kotlin.math.floor

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
    val exportedAtUtc: String
)

/**
 * Schreibt die GNSS-Roh-Messungen als CSV (eine Zeile pro Satellit × Band).
 * Reine Kotlin-Funktion ohne Android-Abhängigkeit – dadurch unit-testbar.
 */
object MeasurementCsv {

    private const val WEEK_NANOS = 604_800_000_000_000.0

    private val HEADER = listOf(
        "epoch", "svid", "constellation", "band", "freq_hz", "cn0_dbhz",
        "time_ns", "full_bias_ns", "bias_ns", "drift_nsps", "leap_s",
        "week_offset_ns", "t_tx_raw_ns", "t_tx_ns", "sv_time_unc_ns",
        "pseudorange_m", "doppler_mps", "adr_m", "adr_state", "phase_unc_m",
        "state_flags", "code_lock", "tow_known", "msec_ambiguous", "multipath"
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
        lines += comment("zeitbasis", "export_utc ist die Geräteuhr · time_ns ist GNSS-Zeit · alle *_ns sind Nanosekunden · Epochen können Lücken haben", profile)
        lines += comment("hinweis", "adr_m ist accumulated delta range (relativ zur Epoche und keine absolute Trägerphase) · adr_state Bit 1 gesetzt bedeutet gültig", profile)
        lines += comment("flags", "state_flags: GnssMeasurement.getState() · adr_state: accumulatedDeltaRangeState · code_lock/tow_known/msec_ambiguous: 1 ja, 0 nein", profile)

        lines += HEADER.joinToString(profile.delimiter.toString())

        var epochIndex = 0
        epochs.forEach { epoch ->
            epochIndex++
            val clock = epoch.clock
            val fullBiasNanos = clock?.fullBiasNanos?.takeIf { it != Long.MIN_VALUE }
            val biasNanos = clock?.biasNanos?.takeIf { !it.isNaN() }
            val driftNsPerSec = clock?.driftNsPerSec?.takeIf { !it.isNaN() }
            val leapSecond = clock?.leapSecond?.takeIf { it != Int.MIN_VALUE }
            val weekOffsetNanos: Long? = fullBiasNanos
                ?.let { floor(-it.toDouble() / WEEK_NANOS) * WEEK_NANOS }
                ?.toLong()

            epoch.rows.forEach { m ->
                val tTxNanos = m.tTxRawNanos?.let { raw -> weekOffsetNanos?.let { raw + it } }

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
                    fmtLong(weekOffsetNanos),
                    fmtLong(m.tTxRawNanos),
                    fmtLong(tTxNanos),
                    fmtLong(m.svTimeUncertaintyNanos),
                    fmtDouble(m.pseudorangeMeters, "%.3f", profile),
                    fmtDouble(m.dopplerMetersPerSec, "%.1f", profile),
                    fmtDouble(m.carrierPhaseMeters, "%.1f", profile),
                    m.adrState.toString(),
                    fmtDouble(m.carrierPhaseUncertaintyMeters, "%.3f", profile),
                    m.stateFlags.toString(),
                    bool01(m.codeLock),
                    bool01(m.towKnown),
                    bool01(m.msecAmbiguous),
                    multipathToken(m.multipath)
                )
                lines += cells.joinToString(profile.delimiter.toString())
            }
        }

        val body = lines.joinToString("\r\n") + "\r\n"
        return if (profile.includeBom) "\uFEFF$body" else body
    }

    private fun comment(key: String, value: String, profile: CsvProfile): String =
        "# $key = " + csvField(value, profile)

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

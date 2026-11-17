package de.gpsradar.satellites.model

/**
 * Was ein Gerät kann – und wie sicher wir das wissen.
 *
 * Drei Quellen, in dieser Reihenfolge:
 * 1. **Plattform** – manche Felder kennt Android erst ab einer API-Stufe
 *    (AGC ab 29, `getSnrInDb` ab 30, `GnssCapabilities` ab 31). Das ist
 *    verlässlich: unterhalb der Stufe gibt es das Feld nicht.
 * 2. **Gerät** – `GnssCapabilities` (ab API 31) sagt, was dieser Chip kann.
 * 3. **Messung** – erst die Aufzeichnung zeigt, was tatsächlich ankommt
 *    (siehe `# geliefert` im CSV-Kopf).
 *
 * Die Regel für die Oberfläche: **nur nachweislich Fehlendes wird weggelassen.**
 * `UNKNOWN` heißt „könnte sein“ und wird angezeigt – sonst würden wir Hardware
 * beschneiden, die wir nicht kennen.
 */
enum class CapabilityState {
    SUPPORTED,
    UNKNOWN,
    UNSUPPORTED;

    /** Darf angeboten werden? Alles außer nachweislich nicht vorhanden. */
    val offerable: Boolean get() = this != UNSUPPORTED

    /** Kurzform für den CSV-Kopf und die Anzeige. */
    val token: String
        get() = when (this) {
            SUPPORTED -> "ja"
            UNSUPPORTED -> "nein"
            UNKNOWN -> "unklar"
        }
}

/** Ein Signalweg, den der Empfänger laut `GnssCapabilities` verarbeitet. */
data class SupportedSignal(
    val constellation: Constellation,
    val carrierFrequencyHz: Long,
    val codeType: String?
) {
    /** Bandkürzel wie in der Messliste (L1, L5, E1, E5a, B1 …). */
    val band: String get() = gnssBandShort(constellation, carrierFrequencyHz.toFloat())

    /** z. B. „GPS L1 C/A“ oder „GPS L5“. */
    fun describe(): String {
        val code = codeType?.trim().orEmpty()
        return if (code.isEmpty()) {
            "${constellation.shortName} $band"
        } else {
            "${constellation.shortName} $band $code"
        }
    }
}

/** Antennen-Phasenmittelpunkt, soweit das Gerät ihn meldet. */
data class AntennaSpec(
    val carrierFrequencyHz: Long,
    val offsetXMm: Double,
    val offsetYMm: Double,
    val offsetZMm: Double,
    val hasVariationCorrections: Boolean
) {
    fun describe(): String {
        val mhz = carrierFrequencyHz / 1_000_000.0
        val variation = if (hasVariationCorrections) " · Phasenmittenvariation" else ""
        return String.format(
            java.util.Locale.US,
            "%.2f MHz · Versatz %.1f/%.1f/%.1f mm%s",
            mhz, offsetXMm, offsetYMm, offsetZMm, variation
        )
    }
}

/**
 * Momentaufnahme der Gerätefähigkeiten. Unveränderlich und ohne Android-Typen,
 * damit sie sich ohne Gerät prüfen lässt.
 */
data class DeviceCapabilities(
    val sdkInt: Int,
    val rawMeasurements: CapabilityState = CapabilityState.UNKNOWN,
    val accumulatedDeltaRange: CapabilityState = CapabilityState.UNKNOWN,
    val navigationMessages: CapabilityState = CapabilityState.UNKNOWN,
    val antennaInfo: CapabilityState = CapabilityState.UNKNOWN,
    val corrections: CapabilityState = CapabilityState.UNKNOWN,
    val signals: List<SupportedSignal> = emptyList(),
    val antennas: List<AntennaSpec> = emptyList()
) {

    /**
     * Feld, das die Plattform erst ab [minSdk] kennt.
     *
     * Das ist eine **belegbare** Auskunft: unterhalb der Stufe gibt es das Feld
     * nicht (`UNSUPPORTED`), ab der Stufe gibt es es (`SUPPORTED`). Ob dieser
     * Chip es dann auch füllt, ist eine andere Frage – die beantwortet die
     * Messung (`# geliefert`). `UNKNOWN` bleibt deshalb den Fällen vorbehalten,
     * in denen die Geräte-API keine Antwort gab.
     */
    fun platformField(minSdk: Int): CapabilityState =
        if (sdkInt >= minSdk) CapabilityState.SUPPORTED else CapabilityState.UNSUPPORTED

    /** `getAutomaticGainControlLevelDb()` – ab API 29. */
    val automaticGainControl: CapabilityState get() = platformField(29)

    /** Uhrunsicherheit und Resets im `GnssClock` – ab API 29. */
    val clockUncertainty: CapabilityState get() = platformField(29)

    /** `getSnrInDb()` – ab API 30. */
    val snrInDb: CapabilityState get() = platformField(30)

    /** `getBasebandCn0DbHz()` – ab API 30. */
    val basebandCn0: CapabilityState get() = platformField(30)

    /** `LocationManager.getGnssAntennaInfos()` – ab API 31 (nicht 30). */
    val antennaApi: CapabilityState get() = platformField(31)

    /**
     * Brauchbare Fähigkeitsabfragen – ab API 31.
     *
     * Den Getter `getGnssCapabilities()` gibt es zwar schon ab 30, dort aber
     * nur mit `hasAntennaInfo()`; `hasMeasurements`, `hasNavigationMessages`
     * und die Abfragen zu Trägerphase und Signalwegen folgen später.
     */
    val capabilityApi: CapabilityState get() = platformField(31)

    /** `getCodeType()` gibt es seit API 24 – also immer. */
    val codeType: CapabilityState get() = CapabilityState.SUPPORTED

    /**
     * Ist Trägerphase grundsätzlich zu erwarten?
     *
     * `ADR_STATE_VALID` liefert nur, wer die Phase wirklich verfolgt. Auf
     * Android 14+ sagt `hasAccumulatedDeltaRange()` das schon vorher; auf
     * älteren Geräten bleibt es offen und wird angezeigt (siehe `# geliefert`).
     */
    val phaseUsable: Boolean get() = accumulatedDeltaRange.offerable

    /** Konstellations-/Bandkombinationen, die dieser Chip verarbeitet. */
    fun signalLabels(): List<String> =
        signals.map { it.describe() }.distinct().sorted()

    /** Kurzfassung für eine Zeile im CSV-Kopf. */
    fun csvSummary(): String = buildString {
        append("Android SDK ").append(sdkInt)
        append(" · Rohmessungen ").append(rawMeasurements.token)
        append(" · Trägerphase ").append(accumulatedDeltaRange.token)
        append(" · Navigationsnachrichten ").append(navigationMessages.token)
        append(" · Antenneninfo ").append(antennaInfo.token)
        append(" · Korrekturdienste ").append(corrections.token)
        append(" · Fähigkeits-API ").append(capabilityApi.token)
        append(" · AGC ").append(automaticGainControl.token)
        append(" · SNR in dB ").append(snrInDb.token)
        append(" · Basisband-C/N0 ").append(basebandCn0.token)
        append(" · Uhrunsicherheit ").append(clockUncertainty.token)
    }

    /** Ein Satz zu den Signalwegen – leer, wenn die API sie nicht kennt. */
    fun csvSignals(): String =
        if (signals.isEmpty()) {
            "vom Gerät nicht gemeldet (Fähigkeits-API ab Android 12)"
        } else {
            signalLabels().joinToString(" · ")
        }

    /** Ein Satz zur Antenne – leer, wenn nicht gemeldet. */
    fun csvAntenna(): String =
        if (antennas.isEmpty()) {
            "vom Gerät nicht gemeldet"
        } else {
            antennas.joinToString(" · ") { it.describe() }
        }
}

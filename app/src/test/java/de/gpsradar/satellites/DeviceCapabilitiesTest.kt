package de.gpsradar.satellites

import de.gpsradar.satellites.model.AntennaSpec
import de.gpsradar.satellites.model.CapabilityState
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.DeviceCapabilities
import de.gpsradar.satellites.model.SupportedSignal
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceCapabilitiesTest {

    /**
     * Die wichtigste Regel des ganzen Modells: nur nachweislich Fehlendes wird
     * weggelassen. „Unbekannt“ muss angeboten werden, sonst beschneiden wir
     * Hardware, die wir nicht kennen.
     */
    @Test
    fun unknownIsOfferableUnsupportedIsNot() {
        assertTrue(CapabilityState.UNKNOWN.offerable)
        assertTrue(CapabilityState.SUPPORTED.offerable)
        assertFalse(CapabilityState.UNSUPPORTED.offerable)
    }

    @Test
    fun tokensAreGermanShortForms() {
        assertEquals("ja", CapabilityState.SUPPORTED.token)
        assertEquals("nein", CapabilityState.UNSUPPORTED.token)
        assertEquals("unklar", CapabilityState.UNKNOWN.token)
    }

    /**
     * Android 7 (minSdk 24): AGC, Uhrunsicherheit, SNR, Basisband-C/N0 und die
     * Fähigkeits-API gibt es dort nicht. Das ist die Untergrenze des Projekts.
     */
    @Test
    fun oldPlatformProvesFieldsUnsupported() {
        val caps = DeviceCapabilities(sdkInt = 24)

        assertEquals(CapabilityState.UNSUPPORTED, caps.automaticGainControl)
        assertEquals(CapabilityState.UNSUPPORTED, caps.clockUncertainty)
        assertEquals(CapabilityState.UNSUPPORTED, caps.snrInDb)
        assertEquals(CapabilityState.UNSUPPORTED, caps.basebandCn0)
        assertEquals(CapabilityState.UNSUPPORTED, caps.antennaApi)
        assertEquals(CapabilityState.UNSUPPORTED, caps.capabilityApi)
        // getCodeType() gibt es seit API 24 – also immer.
        assertEquals(CapabilityState.SUPPORTED, caps.codeType)

        assertFalse(caps.automaticGainControl.offerable)
        assertTrue(caps.codeType.offerable)
    }

    /** Grenzstufen: die Stufe selbst kennt das Feld schon. */
    @Test
    fun platformThresholdsAreInclusive() {
        assertEquals(CapabilityState.SUPPORTED, DeviceCapabilities(sdkInt = 29).automaticGainControl)
        assertEquals(CapabilityState.UNSUPPORTED, DeviceCapabilities(sdkInt = 28).automaticGainControl)
        assertEquals(CapabilityState.SUPPORTED, DeviceCapabilities(sdkInt = 30).snrInDb)
        assertEquals(CapabilityState.UNSUPPORTED, DeviceCapabilities(sdkInt = 29).snrInDb)
        assertEquals(CapabilityState.SUPPORTED, DeviceCapabilities(sdkInt = 31).capabilityApi)
        assertEquals(CapabilityState.UNSUPPORTED, DeviceCapabilities(sdkInt = 30).capabilityApi)
        assertEquals(CapabilityState.UNSUPPORTED, DeviceCapabilities(sdkInt = 30).antennaApi)
        assertEquals(CapabilityState.SUPPORTED, DeviceCapabilities(sdkInt = 31).antennaApi)
    }

    /**
     * Note-9-Fall: der Chip meldet „Trägerphase nicht unterstützt“. Dann darf
     * die Oberfläche keine Phasenauswertung anbieten – vor der ersten Messung.
     */
    @Test
    fun unsupportedCarrierPhaseIsReportedBeforeMeasuring() {
        val caps = DeviceCapabilities(
            sdkInt = 33,
            accumulatedDeltaRange = CapabilityState.UNSUPPORTED
        )
        assertFalse(caps.phaseUsable)
    }

    @Test
    fun unknownCarrierPhaseStaysOptimistic() {
        val caps = DeviceCapabilities(
            sdkInt = 33,
            accumulatedDeltaRange = CapabilityState.UNKNOWN
        )
        assertTrue(caps.phaseUsable)
    }

    /** Signalwege: gleiche Einträge zusammenfassen, Rest sortiert ausgeben. */
    @Test
    fun signalLabelsAreDistinctAndSorted() {
        val caps = DeviceCapabilities(
            sdkInt = 33,
            signals = listOf(
                SupportedSignal(Constellation.GPS, 1_575_420_032L, "C"),
                SupportedSignal(Constellation.GPS, 1_575_420_032L, "C"),
                SupportedSignal(Constellation.GALILEO, 1_575_420_000L, "C"),
                SupportedSignal(Constellation.GPS, 1_176_450_000L, null)
            )
        )

        assertEquals(
            listOf("GAL E1 C", "GPS L1 C", "GPS L5"),
            caps.signalLabels()
        )
    }

    @Test
    fun signalWithoutCodeTypeHasNoTrailingSpace() {
        val signal = SupportedSignal(Constellation.BEIDOU, 1_561_098_000L, "  ")
        assertEquals("BDS B1", signal.describe())
    }

    /** Ohne Fähigkeits-API (Android 7–11) bleibt nur der Hinweis, nicht eine leere Zeile. */
    @Test
    fun missingSignalsSayWhy() {
        val caps = DeviceCapabilities(sdkInt = 28)
        assertTrue(caps.csvSignals().contains("nicht gemeldet"))
        assertTrue(caps.csvSignals().contains("Android 12"))
        assertTrue(caps.csvAntenna().contains("nicht gemeldet"))
    }

    @Test
    fun csvSummaryNamesEveryCapability() {
        val caps = DeviceCapabilities(
            sdkInt = 33,
            rawMeasurements = CapabilityState.SUPPORTED,
            accumulatedDeltaRange = CapabilityState.UNSUPPORTED,
            navigationMessages = CapabilityState.SUPPORTED,
            antennaInfo = CapabilityState.SUPPORTED,
            corrections = CapabilityState.UNSUPPORTED
        )
        val line = caps.csvSummary()

        assertTrue(line.contains("SDK 33"))
        assertTrue(line.contains("Rohmessungen ja"))
        assertTrue(line.contains("Trägerphase nein"))
        assertTrue(line.contains("Navigationsnachrichten ja"))
        assertTrue(line.contains("Antenneninfo ja"))
        assertTrue(line.contains("Korrekturdienste nein"))
    }

    @Test
    fun antennaDescriptionUsesMillimetres() {
        val antenna = AntennaSpec(
            carrierFrequencyHz = 1_575_420_000L,
            offsetXMm = 1.5,
            offsetYMm = -2.25,
            offsetZMm = 0.0,
            hasVariationCorrections = true
        )
        assertEquals(
            "1575.42 MHz · Versatz 1.5/-2.3/0.0 mm · Phasenmittenvariation",
            antenna.describe()
        )
    }
}

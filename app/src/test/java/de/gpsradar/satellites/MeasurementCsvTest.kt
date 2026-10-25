package de.gpsradar.satellites

import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.CsvMeta
import de.gpsradar.satellites.model.CsvProfile
import de.gpsradar.satellites.model.MeasurementCsv
import de.gpsradar.satellites.model.MultipathState
import de.gpsradar.satellites.model.RawClockSnapshot
import de.gpsradar.satellites.model.RawMeasurement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MeasurementCsvTest {

    private val clock = RawClockSnapshot(
        timeNanos = 538241234567890L,
        fullBiasNanos = -1365123456L,
        biasNanos = 0.00012,
        driftNsPerSec = 0.41,
        leapSecond = 18,
        receivedAtMillis = 1L
    )

    private val meta = CsvMeta(
        device = "samsung SM-N960F · hardware qcom · board msm8998",
        android = "9 (SDK 28)",
        app = "1.3.4 (9)",
        exportedAtUtc = "2026-10-25T12:33:07Z"
    )

    private fun measurement(
        svid: Int = 7,
        constellation: Constellation = Constellation.GPS,
        pseudorangeMeters: Double? = 23145678.912,
        carrierPhaseMeters: Double? = -88213456.4,
        carrierPhaseUncertaintyMeters: Double? = 0.021,
        codeLock: Boolean = true,
        towKnown: Boolean = true,
        msecAmbiguous: Boolean = false,
        tTxRawNanos: Long? = 123456789L,
        svTimeUncertaintyNanos: Long? = 12L
    ) = RawMeasurement(
        svid = svid,
        constellation = constellation,
        carrierFrequencyHz = 1575420000f,
        bandLabel = "L1 (1575.42 MHz)",
        bandShort = "L1",
        cn0DbHz = 38.2f,
        pseudorangeMeters = pseudorangeMeters,
        dopplerMetersPerSec = 412.7,
        carrierPhaseMeters = carrierPhaseMeters,
        phaseValid = true,
        multipath = MultipathState.NOT_DETECTED,
        receivedAtMillis = 1L,
        stateFlags = 33,
        towKnown = towKnown,
        msecAmbiguous = msecAmbiguous,
        codeLock = codeLock,
        carrierPhaseUncertaintyMeters = carrierPhaseUncertaintyMeters,
        tTxRawNanos = tTxRawNanos,
        svTimeUncertaintyNanos = svTimeUncertaintyNanos,
        adrState = 5
    )

    @Test
    fun universalProfileUsesCommaAndDotAndNoBom() {
        val csv = MeasurementCsv.toCsv(listOf(measurement()), clock, meta, CsvProfile.UNIVERSAL)

        assertFalse(csv.startsWith("\uFEFF"))
        val row = csv.lines().first { it.startsWith("7,") }
        assertTrue(row.startsWith("7,GPS,L1,"))
        assertTrue(row.contains(",38.2,"))
        assertTrue(row.contains("23145678.912"))
    }

    @Test
    fun excelProfileUsesSemicolonCommaAndBom() {
        val csv = MeasurementCsv.toCsv(listOf(measurement()), clock, meta, CsvProfile.EXCEL_DE)

        assertTrue(csv.startsWith("\uFEFF"))
        val row = csv.lines().first { it.startsWith("7;") }
        assertTrue(row.startsWith("7;GPS;L1;"))
        assertTrue(row.contains(";38,2;"))
        assertTrue(row.contains("23145678,912"))
    }

    @Test
    fun allDataRowsHaveSameFieldCountAsHeader() {
        val measurements = listOf(
            measurement(),
            measurement(svid = 8, constellation = Constellation.GALILEO)
        )
        for (profile in CsvProfile.entries) {
            val csv = MeasurementCsv.toCsv(measurements, clock, meta, profile)
            val delim = profile.delimiter.toString()
            val lines = csv.lines().filter { it.isNotBlank() }
            val header = lines.first { it.startsWith("svid") }
            val fieldCount = header.split(delim).size
            val dataRows = lines.filter { it.startsWith("7$delim") || it.startsWith("8$delim") }

            assertEquals(24, fieldCount)
            dataRows.forEach { assertEquals(fieldCount, it.split(delim).size) }
        }
    }

    @Test
    fun missingValuesBecomeEmptyCellsNotNull() {
        val csv = MeasurementCsv.toCsv(
            listOf(
                measurement(
                    pseudorangeMeters = null,
                    carrierPhaseMeters = null,
                    carrierPhaseUncertaintyMeters = null,
                    tTxRawNanos = null,
                    svTimeUncertaintyNanos = null
                )
            ),
            clock, meta, CsvProfile.UNIVERSAL
        )

        val row = csv.lines().first { it.startsWith("7,") }
        assertFalse(row.contains("null"))
    }

    @Test
    fun booleansAsZeroOne() {
        val csv = MeasurementCsv.toCsv(
            listOf(measurement(codeLock = false, towKnown = false, msecAmbiguous = true)),
            clock, meta, CsvProfile.UNIVERSAL
        )

        val cells = csv.lines().first { it.startsWith("7,") }.split(',')
        assertEquals("0", cells[20]) // code_lock
        assertEquals("0", cells[21]) // tow_known
        assertEquals("1", cells[22]) // msec_ambiguous
    }

    @Test
    fun tTxNanosIsRawPlusWeekOffset() {
        val csv = MeasurementCsv.toCsv(
            listOf(measurement(tTxRawNanos = 123456789L)),
            clock, meta, CsvProfile.UNIVERSAL
        )

        val cells = csv.lines().first { it.startsWith("7,") }.split(',')
        val weekOffset = cells[10].toLong()
        val raw = cells[11].toLong()
        val adjusted = cells[12].toLong()

        assertEquals(raw + weekOffset, adjusted)
    }

    @Test
    fun commentValueWithCommaIsQuoted() {
        val csv = MeasurementCsv.toCsv(
            listOf(measurement()),
            clock, meta.copy(device = "vendor, model"), CsvProfile.UNIVERSAL
        )

        assertTrue(csv.lines().any { it.startsWith("# geraet = \"vendor, model\"") })
    }
}

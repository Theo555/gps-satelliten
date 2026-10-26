package de.gpsradar.satellites

import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.CsvMeta
import de.gpsradar.satellites.model.CsvProfile
import de.gpsradar.satellites.model.MeasurementCsv
import de.gpsradar.satellites.model.MultipathState
import de.gpsradar.satellites.model.RawClockSnapshot
import de.gpsradar.satellites.model.RawEpoch
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

    private fun epoch(vararg rows: RawMeasurement, atMillis: Long = 1L) =
        RawEpoch(receivedAtMillis = atMillis, clock = clock, rows = rows.toList())

    @Test
    fun universalProfileUsesCommaAndDotAndNoBom() {
        val csv = MeasurementCsv.toCsv(listOf(epoch(measurement())), meta, CsvProfile.UNIVERSAL)

        assertFalse(csv.startsWith("\uFEFF"))
        val row = csv.lines().first { it.startsWith("1,7,") }
        assertTrue(row.startsWith("1,7,GPS,L1,"))
        assertTrue(row.contains(",38.2,"))
        assertTrue(row.contains("23145678.912"))
    }

    @Test
    fun excelProfileUsesSemicolonCommaAndBom() {
        val csv = MeasurementCsv.toCsv(listOf(epoch(measurement())), meta, CsvProfile.EXCEL_DE)

        assertTrue(csv.startsWith("\uFEFF"))
        val row = csv.lines().first { it.startsWith("1;7;") }
        assertTrue(row.startsWith("1;7;GPS;L1;"))
        assertTrue(row.contains(";38,2;"))
        assertTrue(row.contains("23145678,912"))
    }

    @Test
    fun epochsAreNumberedAndAllRowsIncluded() {
        val csv = MeasurementCsv.toCsv(
            listOf(
                epoch(measurement(svid = 7), measurement(svid = 8), atMillis = 1L),
                epoch(measurement(svid = 7), atMillis = 2L)
            ),
            meta, CsvProfile.UNIVERSAL
        )

        val dataRows = csv.lines().filter { it.isNotBlank() && !it.startsWith("#") && !it.startsWith("epoch") }
        assertEquals(3, dataRows.size)
        assertEquals(2, dataRows.count { it.startsWith("1,") })
        assertEquals(1, dataRows.count { it.startsWith("2,") })
        assertTrue(csv.contains("# aufzeichnung = 2 Epochen · 3 Zeilen"))
    }

    @Test
    fun allDataRowsHaveSameFieldCountAsHeader() {
        val epochs = listOf(
            epoch(measurement(), measurement(svid = 8, constellation = Constellation.GALILEO), atMillis = 1L),
            epoch(measurement(svid = 9), atMillis = 2L)
        )
        for (profile in CsvProfile.entries) {
            val csv = MeasurementCsv.toCsv(epochs, meta, profile)
            val delim = profile.delimiter.toString()
            val lines = csv.lines().filter { it.isNotBlank() }
            val header = lines.first { it.startsWith("epoch") }
            val fieldCount = header.split(delim).size
            val dataRows = lines.filter { it.firstOrNull()?.isDigit() == true }

            assertEquals(25, fieldCount)
            assertEquals(3, dataRows.size)
            dataRows.forEach { assertEquals(fieldCount, it.split(delim).size) }
        }
    }

    @Test
    fun missingValuesBecomeEmptyCellsNotNull() {
        val csv = MeasurementCsv.toCsv(
            listOf(
                epoch(
                    measurement(
                        pseudorangeMeters = null,
                        carrierPhaseMeters = null,
                        carrierPhaseUncertaintyMeters = null,
                        tTxRawNanos = null,
                        svTimeUncertaintyNanos = null
                    )
                )
            ),
            meta, CsvProfile.UNIVERSAL
        )

        val row = csv.lines().first { it.startsWith("1,7,") }
        assertFalse(row.contains("null"))
    }

    @Test
    fun booleansAsZeroOne() {
        val csv = MeasurementCsv.toCsv(
            listOf(epoch(measurement(codeLock = false, towKnown = false, msecAmbiguous = true))),
            meta, CsvProfile.UNIVERSAL
        )

        val cells = csv.lines().first { it.startsWith("1,7,") }.split(',')
        assertEquals("0", cells[21]) // code_lock
        assertEquals("0", cells[22]) // tow_known
        assertEquals("1", cells[23]) // msec_ambiguous
    }

    @Test
    fun tTxNanosIsRawPlusWeekOffset() {
        val csv = MeasurementCsv.toCsv(
            listOf(epoch(measurement(tTxRawNanos = 123456789L))),
            meta, CsvProfile.UNIVERSAL
        )

        val cells = csv.lines().first { it.startsWith("1,7,") }.split(',')
        val weekOffset = cells[11].toLong()
        val raw = cells[12].toLong()
        val adjusted = cells[13].toLong()

        assertEquals(raw + weekOffset, adjusted)
    }

    @Test
    fun commentValueWithCommaIsQuoted() {
        val csv = MeasurementCsv.toCsv(
            listOf(epoch(measurement())),
            meta.copy(device = "vendor, model"), CsvProfile.UNIVERSAL
        )

        assertTrue(csv.lines().any { it.startsWith("# geraet = \"vendor, model\"") })
    }
}

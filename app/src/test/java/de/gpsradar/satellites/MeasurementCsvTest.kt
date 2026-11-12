package de.gpsradar.satellites

import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.CsvMeta
import de.gpsradar.satellites.model.CsvProfile
import de.gpsradar.satellites.model.MeasurementCsv
import de.gpsradar.satellites.model.MultipathState
import de.gpsradar.satellites.model.Pseudorange
import de.gpsradar.satellites.model.PseudorangeStatus
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
        svTimeUncertaintyNanos: Long? = 12L,
        pseudorangeStatus: PseudorangeStatus = PseudorangeStatus.OK,
        agcDb: Double? = null,
        snrInDb: Double? = null,
        basebandCn0DbHz: Double? = null,
        codeType: String? = null
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
        adrState = 5,
        pseudorangeStatus = pseudorangeStatus,
        agcDb = agcDb,
        snrInDb = snrInDb,
        basebandCn0DbHz = basebandCn0DbHz,
        codeType = codeType
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

            assertEquals(34, fieldCount)
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
                        svTimeUncertaintyNanos = null,
                        pseudorangeStatus = PseudorangeStatus.NO_TIME
                    )
                )
            ),
            meta, CsvProfile.UNIVERSAL
        )

        val row = csv.lines().first { it.startsWith("1,7,") }
        assertFalse(row.contains("null"))
        // Der Grund fuer die fehlende Pseudorange steht in der Spalte pr_status.
        assertTrue(row.split(',')[25] == "no_time")
    }

    /**
     * Ein Geraet, das AGC, SNR und Uhrgüte nicht liefert, darf keine Nullen und
     * keine NaN schreiben -- leere Zellen, und die Kopfzeile sagt warum.
     */
    @Test
    fun fehlendeSignalUndUhrgueteBleibenLeer() {
        val csv = MeasurementCsv.toCsv(
            listOf(epoch(measurement())),
            meta, CsvProfile.UNIVERSAL
        )

        val cells = csv.lines().first { it.startsWith("1,7,") }.split(',')
        assertEquals(34, cells.size)
        for (i in 26..33) {
            assertEquals("Spalte $i muss leer sein", "", cells[i])
        }
        assertFalse(csv.contains("NaN"))
        assertTrue(csv.contains("# geliefert = agc=nein · snr_in=nein · basisband_cn0=nein"))
    }

    /**
     * Gegenprobe: Liefert das Geraet die Felder, stehen sie an der erwarteten
     * Stelle -- und die Kopfzeile meldet sie als geliefert.
     */
    @Test
    fun gelieferteSignalUndUhrgueteStehenInDenSpalten() {
        val mitUhrguete = clock.copy(
            biasUncertaintyNanos = 4.5,
            driftUncertaintyNsPerSec = 0.00025,
            elapsedRealtimeNanos = 987654321L,
            hardwareClockDiscontinuityCount = 2
        )
        val csv = MeasurementCsv.toCsv(
            listOf(
                RawEpoch(
                    receivedAtMillis = 1L,
                    clock = mitUhrguete,
                    rows = listOf(
                        measurement(
                            agcDb = -12.34,
                            snrInDb = 21.5,
                            basebandCn0DbHz = 36.75,
                            codeType = "C"
                        )
                    )
                )
            ),
            meta, CsvProfile.UNIVERSAL
        )

        val cells = csv.lines().first { it.startsWith("1,7,") }.split(',')
        assertEquals("-12.34", cells[26]) // agc_db
        assertEquals("21.50", cells[27]) // snr_in_db
        assertEquals("36.75", cells[28]) // baseband_cn0_dbhz
        assertEquals("C", cells[29]) // code_type
        assertEquals("4.500", cells[30]) // bias_unc_ns
        assertEquals("0.000250", cells[31]) // drift_unc_nsps
        assertEquals("987654321", cells[32]) // elapsed_realtime_ns
        assertEquals("2", cells[33]) // hw_clock_disc
        assertTrue(
            csv.contains(
                "# geliefert = agc=ja · snr_in=ja · basisband_cn0=ja · code_type=ja" +
                    " · uhr_unsicherheit=ja · uhrspruenge=ja"
            )
        )
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
    fun tTxNanosIstRohwertPlusAngewandtemVersatz() {
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
    fun prStatusWirdGeschrieben() {
        val csv = MeasurementCsv.toCsv(
            listOf(
                epoch(
                    measurement(svid = 7),
                    measurement(
                        svid = 8,
                        constellation = Constellation.BEIDOU,
                        pseudorangeMeters = null,
                        pseudorangeStatus = PseudorangeStatus.IMPLAUSIBLE
                    )
                )
            ),
            meta, CsvProfile.UNIVERSAL
        )

        val rows = csv.lines().filter { it.firstOrNull()?.isDigit() == true }
        assertEquals("ok", rows[0].split(',')[25])
        assertEquals("implausible", rows[1].split(',')[25])
    }

    @Test
    fun beidouSendezeitWirdMitZeitbasisUmgerechnet() {
        // Rohwert 21 s vor der Empfangszeit -> nach 14 s Zeitbasis bleiben 7 s,
        // also unplausibel; ohne die Umrechnung waere es eine Woche zu weit weg.
        val csv = MeasurementCsv.toCsv(
            listOf(
                epoch(
                    measurement(
                        constellation = Constellation.BEIDOU,
                        tTxRawNanos = clock.timeNanos - clock.fullBiasNanos - 21_000_000_000L
                    )
                )
            ),
            meta, CsvProfile.UNIVERSAL
        )

        val cells = csv.lines().first { it.startsWith("1,7,") }.split(',')
        val raw = cells[12].toLong()
        val adjusted = cells[13].toLong()
        // Angewandter Versatz = volle Epoche (hier eine Woche) + 14 s Zeitbasisversatz.
        assertEquals(Pseudorange.BEIDOU_OFFSET_NANOS, (adjusted - raw) % Pseudorange.WEEK_NANOS)
    }

    /**
     * Die Trägerphase muss feiner als eine Wellenlänge (L1: 19,03 cm) im CSV
     * stehen. Mit nur einer Nachkommastelle wären es 0,1 m, also gut eine
     * halbe Wellenlänge -- eine Phasenauswertung wäre damit unmöglich.
     */
    @Test
    fun carrierPhaseKeepsSubMillimetreResolution() {
        val phase = -88213456.4321

        val universal = MeasurementCsv.toCsv(
            listOf(epoch(measurement(carrierPhaseMeters = phase))),
            meta, CsvProfile.UNIVERSAL
        )
        val universalRow = universal.lines().first { it.startsWith("1,7,") }.split(',')
        assertEquals("-88213456.4321", universalRow[17])

        val excel = MeasurementCsv.toCsv(
            listOf(epoch(measurement(carrierPhaseMeters = phase))),
            meta, CsvProfile.EXCEL_DE
        )
        val excelRow = excel.lines().first { it.startsWith("1;7;") }.split(';')
        assertEquals("-88213456,4321", excelRow[17])
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

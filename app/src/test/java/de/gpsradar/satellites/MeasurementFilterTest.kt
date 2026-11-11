package de.gpsradar.satellites

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import de.gpsradar.satellites.model.BufferStats
import de.gpsradar.satellites.model.CapturePhase
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.CsvProfile
import de.gpsradar.satellites.model.MultipathState
import de.gpsradar.satellites.model.RawMeasurement
import de.gpsradar.satellites.ui.components.MeasurementScreen
import de.gpsradar.satellites.ui.theme.GpsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prüfnetz für die Filterleiste der Messwert-Ansicht – läuft auf der JVM.
 *
 * Anlass: Die Leiste hatte drei feste Knöpfe „Alle / L1 / L5“, gefiltert wurde
 * über `gnssBandShort()`. Diese Funktion liefert je Konstellation einen eigenen
 * Namen (L1, E1, B1, G1), ein Knopf „L1“ traf deshalb nur GPS und „L5“ so gut
 * wie nie etwas – ein Filter, der aussieht, als könnte man wählen. Jetzt
 * entstehen die Knöpfe aus den tatsächlich empfangenen Systemen.
 *
 * Geprüft wird genau das: die Knöpfe kommen aus den Daten, ein Klick filtert,
 * und die Bandnamen tauchen nicht mehr als Filter auf.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de")
class MeasurementFilterTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun messung(
        svid: Int,
        constellation: Constellation,
        bandShort: String,
        pseudorangeMeters: Double
    ) = RawMeasurement(
        svid = svid,
        constellation = constellation,
        carrierFrequencyHz = 1575.42e6f,
        bandLabel = bandShort,
        bandShort = bandShort,
        cn0DbHz = 30f,
        pseudorangeMeters = pseudorangeMeters,
        dopplerMetersPerSec = 0.0,
        carrierPhaseMeters = null,
        phaseValid = false,
        multipath = MultipathState.UNKNOWN,
        receivedAtMillis = 0L
    )

    /** Zwei Systeme, damit sich „Alle“ und ein einzelner Knopf unterscheiden. */
    private val gps = messung(7, Constellation.GPS, "L1", 20_000.0)
    private val gal = messung(11, Constellation.GALILEO, "E1", 30_000.0)

    private fun render(measurements: List<RawMeasurement>) {
        compose.setContent {
            GpsRadarTheme {
                MeasurementScreen(
                    measurements = measurements,
                    clock = null,
                    capturePhase = CapturePhase.IDLE,
                    bufferStats = BufferStats(epochs = 1, rows = measurements.size),
                    captureEndsAtMillis = 0L,
                    rawStreamStalled = false,
                    onExportCsv = { _: Uri, _: CsvProfile -> true },
                    onStartCapture = {},
                    onCancelCapture = {},
                    onDiscardCapture = {}
                )
            }
        }
    }

    @Test
    fun knoepfeEntstehenAusDenEmpfangenenSystemen() {
        render(listOf(gps, gal))

        compose.onNodeWithText(context.getString(R.string.filter_all_short)).assertIsDisplayed()
        compose.onNodeWithText(Constellation.GPS.shortName).assertIsDisplayed()
        compose.onNodeWithText(Constellation.GALILEO.shortName).assertIsDisplayed()
    }

    @Test
    fun bandnamenSindKeineFilterMehr() {
        render(listOf(gps, gal))

        // „L1“ steht nur noch als Kennzeichen in der Zeile, nicht als Knopf.
        // Wäre es ein Knopf, gäbe es zwei Knoten mit diesem Text.
        compose.onNodeWithText("L1").assertExists()
        compose.onNodeWithText("L5").assertDoesNotExist()
    }

    @Test
    fun klickAufSystemFiltertDieListe() {
        render(listOf(gps, gal))

        compose.onNodeWithText(Constellation.GALILEO.shortName).performClick()

        // 30 km gehört zu Galileo, 20 km zu GPS.
        compose.onNodeWithText("30.000 km").assertIsDisplayed()
        compose.onNodeWithText("20.000 km").assertDoesNotExist()
    }

    @Test
    fun alleZeigtBeideSysteme() {
        render(listOf(gps, gal))

        compose.onNodeWithText(Constellation.GALILEO.shortName).performClick()
        compose.onNodeWithText(context.getString(R.string.filter_all_short)).performClick()

        compose.onNodeWithText("30.000 km").assertIsDisplayed()
        compose.onNodeWithText("20.000 km").assertIsDisplayed()
    }

    @Test
    fun ohneMessungenGibtEsNurAlle() {
        render(emptyList())

        compose.onNodeWithText(context.getString(R.string.filter_all_short)).assertIsDisplayed()
        compose.onNodeWithText(Constellation.GPS.shortName).assertDoesNotExist()
    }
}

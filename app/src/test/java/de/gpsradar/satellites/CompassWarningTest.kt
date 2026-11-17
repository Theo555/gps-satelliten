package de.gpsradar.satellites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.gpsradar.satellites.model.CompassIssue
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.ui.components.CompassWarningLine
import de.gpsradar.satellites.ui.theme.GpsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prüft die beiden Fassungen der Kompass-Warnung.
 *
 * Anlass (17.11.2026): Der Hinweis stand in einer eigenen Zeile über dem Radar,
 * während „Kompass prüfen" unten mittig saß — zwei Zeilen für dasselbe Thema.
 * Seit dem Umbau steht der Knopf links und der Hinweis direkt daneben. Dort ist
 * für „Kompass unsicher · Feld 45 µT (zu stark)" kein Platz, deshalb die
 * Kurzfassung; auf der Nav-Seite bleibt der volle Satz, weil dort kein Knopf
 * daneben steht, der den Bezug herstellt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de")
class CompassWarningTest {

    @get:Rule
    val compose = createComposeRule()

    private fun zeige(state: GpsLocationState, compact: Boolean) {
        compose.setContent {
            GpsRadarTheme {
                CompassWarningLine(state = state, compact = compact)
            }
        }
    }

    private fun starkesFeld() = GpsLocationState(
        compassIssue = CompassIssue.FIELD_TOO_STRONG,
        compassFieldMicroTesla = 45f
    )

    /** In der Knopfreihe des Radars: kurze Fassung, das Thema steht im Knopf. */
    @Test
    fun kompaktKuerztAufDenBefund() {
        zeige(starkesFeld(), compact = true)

        compose.onNodeWithText("Feld 45 µT · zu stark").assertIsDisplayed()
        compose.onNodeWithText("Kompass unsicher · Feld 45 µT (zu stark)").assertDoesNotExist()
    }

    /** Auf der Nav-Seite bleibt der volle Satz — dort fehlt der Bezug. */
    @Test
    fun vollNenntDenZusammenhang() {
        zeige(starkesFeld(), compact = false)

        compose.onNodeWithText("Kompass unsicher · Feld 45 µT (zu stark)").assertIsDisplayed()
        compose.onNodeWithText("Feld 45 µT · zu stark").assertDoesNotExist()
    }

    /** Ohne Befund bleibt die Zeile leer — es steht nie ein Platzhalter da. */
    @Test
    fun ohneBefundStehtNichtsDa() {
        zeige(GpsLocationState(compassIssue = CompassIssue.NONE), compact = true)

        compose.onNodeWithText("Kompass unsicher", substring = true).assertDoesNotExist()
    }
}

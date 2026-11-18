package de.gpsradar.satellites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import de.gpsradar.satellites.model.ScreenshotPosition
import de.gpsradar.satellites.ui.components.AboutScreen
import de.gpsradar.satellites.ui.theme.GpsRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prüft, dass der Schalter für die Ersatz-Koordinate **nur im Debug-Build**
 * existiert.
 *
 * Der Aufrufer (`MainActivity`) gibt den Rückruf nur weiter, wenn das Paket
 * `FLAG_DEBUGGABLE` trägt. Diese Prüfung ist die Absicherung dafür: Ohne
 * Rückruf darf die Karte samt Knopf gar nicht erst im Baum stehen — sonst könnte
 * ein Release-Build sie über einen anderen Weg zeigen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de")
class AboutScreenshotTest {

    @get:Rule
    val compose = createComposeRule()

    private fun zeigeInfo(
        place: ScreenshotPosition? = null,
        onCycle: (() -> Unit)? = null
    ) {
        compose.setContent {
            GpsRadarTheme {
                AboutScreen(
                    versionName = "1.3.14",
                    versionCode = 19,
                    onOpenRepository = {},
                    onOpenReadme = {},
                    screenshotPlace = place,
                    onCycleScreenshotPlace = onCycle
                )
            }
        }
    }

    /** Release-Fall: kein Rückruf, also keine Karte und kein Knopf. */
    @Test
    fun ohneRueckrufKeinSchalter() {
        zeigeInfo()

        compose.onNodeWithTag("about_screen").assertIsDisplayed()
        compose.onNodeWithTag("about_screenshot_btn").assertDoesNotExist()
    }

    /** Debug-Fall: Karte da, Beschriftung nennt den Zustand. */
    @Test
    fun mitRueckrufZeigtDenZustand() {
        zeigeInfo(place = null, onCycle = {})

        // Die Info-Seite ist lang und scrollbar; geprüft wird der Baum, nicht
        // der sichtbare Ausschnitt.
        compose.onNodeWithTag("about_screenshot_btn").assertExists()
        compose.onNodeWithText("aus").assertExists()
    }

    @Test
    fun beschriftungNenntDenGewaehltenOrt() {
        zeigeInfo(place = ScreenshotPosition.ATOMIUM, onCycle = {})

        compose.onNodeWithText(ScreenshotPosition.ATOMIUM.placeName).assertExists()
    }

    @Test
    fun klickLoestDenRueckrufAus() {
        var klicks = 0
        zeigeInfo(place = null, onCycle = { klicks++ })

        compose.onNodeWithTag("about_screenshot_btn").performScrollTo().performClick()

        assertEquals(1, klicks)
    }
}

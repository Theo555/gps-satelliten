package de.gpsradar.satellites

import android.content.Context
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.ui.components.PermissionAndStatusBanner
import de.gpsradar.satellites.ui.theme.GpsRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Prüft den Hinweis „Empfang hängt" im Banner — der erscheint in **allen**
 * Reitern, nicht nur im Mess-Tab.
 *
 * Anlass: Am 14.11.2026 blieb in der App die Position aus, während Roh-Messungen
 * und Navigationsmeldungen weiterliefen (S20 FE). Bis dahin gab es dafür nur im
 * Mess-Tab eine Anzeige; im Nav-Tab sah man lediglich „Kein Fix". Der Banner
 * erklärt jetzt die Lage und bietet den Neustart an — und zwar so, dass der
 * Nutzer ihn beim Navigieren selbst auslöst (eine Minute ohne Position).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de")
class ReceiverStalledBannerTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    private fun zeigeBanner(
        fixStalled: Boolean,
        restartCountdown: Int = 0,
        onRestart: () -> Unit = {}
    ) {
        compose.setContent {
            GpsRadarTheme {
                PermissionAndStatusBanner(
                    state = GpsLocationState(hasPermission = true, isGpsEnabled = true),
                    onRequestPermission = {},
                    onToggleDemoMode = {},
                    fixStalled = fixStalled,
                    restartCountdown = restartCountdown,
                    onRestartReceiver = onRestart
                )
            }
        }
    }

    @Test
    fun ohneHaengerKeinHinweis() {
        zeigeBanner(fixStalled = false)
        compose.onNodeWithTag("receiver_stalled_card").assertDoesNotExist()
    }

    @Test
    fun haengenderEmpfaengerWirdGemeldet() {
        zeigeBanner(fixStalled = true)
        compose.onNodeWithTag("receiver_stalled_card").assertIsDisplayed()
        compose.onNodeWithText(context.getString(R.string.receiver_stalled_title))
            .assertIsDisplayed()
        compose.onNodeWithTag("restart_receiver_btn").assertIsDisplayed()
    }

    @Test
    fun knopfLoestDenNeustartAus() {
        var gestartet = 0
        zeigeBanner(fixStalled = true, onRestart = { gestartet++ })
        compose.onNodeWithTag("restart_receiver_btn").performClick()
        assertEquals(1, gestartet)
    }

    @Test
    fun waehrendDesNeustartsStehtDerCountdownStattDesKnopfes() {
        zeigeBanner(fixStalled = true, restartCountdown = 42)
        compose.onNodeWithText(
            context.getString(R.string.receiver_stalled_restarting, 42)
        ).assertIsDisplayed()
        compose.onNodeWithTag("restart_receiver_btn").assertDoesNotExist()
    }
}

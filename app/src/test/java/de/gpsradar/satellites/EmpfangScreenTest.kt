package de.gpsradar.satellites

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.ui.EmpfangSummary
import de.gpsradar.satellites.ui.EmpfangSystemCounts
import de.gpsradar.satellites.ui.components.EmpfangScreen
import de.gpsradar.satellites.ui.theme.GpsRadarTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.RobolectricTestRunner

/**
 * Prüft die Beugung in der Zeile „Bahndaten".
 *
 * Anlass (17.11.2026): Auf dem Note 9 stand **„1 Ephemeriden"** — die Zeile war
 * eine feste Zeichenkette mit eingebautem Plural. Jetzt entscheiden zwei
 * plurals-Ressourcen, damit bei Eins „1 Ephemeride" steht.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de")
class EmpfangScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun zeige(ephemeriden: Int, almanach: Int) {
        compose.setContent {
            GpsRadarTheme {
                EmpfangScreen(
                    EmpfangSummary(
                        abilities = emptyList(),
                        systems = listOf(
                            EmpfangSystemCounts(
                                constellation = Constellation.GPS,
                                ephemerides = ephemeriden,
                                almanac = almanach
                            )
                        ),
                        ionosphereSystems = emptyList(),
                        utcSystems = emptyList()
                    )
                )
            }
        }
    }

    @Test
    fun einsWirdRichtigGebeugt() {
        zeige(ephemeriden = 1, almanach = 1)

        compose.onNodeWithText("1 Ephemeride · 1 Almanach").assertIsDisplayed()
        compose.onNodeWithText("1 Ephemeriden", substring = true).assertDoesNotExist()
    }

    @Test
    fun mehrereBleibenImPlural() {
        zeige(ephemeriden = 5, almanach = 27)

        compose.onNodeWithText("5 Ephemeriden · 27 Almanach").assertIsDisplayed()
    }
}

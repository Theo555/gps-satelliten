package de.gpsradar.satellites

import android.content.Context
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.ImportResult
import de.gpsradar.satellites.model.Waypoint
import de.gpsradar.satellites.ui.components.NavigationScreen
import de.gpsradar.satellites.ui.components.SECTION_TRACKS
import de.gpsradar.satellites.ui.components.SECTION_WAYPOINTS
import de.gpsradar.satellites.ui.theme.GpsRadarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Rauchtest für den Nav-Tab – läuft auf der JVM, ohne Gerät.
 *
 * Anlass: `NavigationScreen.kt` wurde in fünf Dateien aufgeteilt (Bildschirm,
 * aktives Ziel, Abteilungen, Zeilen, Dialoge) und war danach nur durch
 * Antippen von Hand prüfbar. Dieses Netz hält die Grundstruktur fest, die dabei
 * leicht kaputtgehen kann: aktive Karte, drei Werkzeuge, vier Abteilungen mit
 * Zähler, Aufklappen, Dialog.
 *
 * Bewusst keine Pixelprüfung und keine Logik – dafür gibt es die 210 Unit-Tests.
 * Geprüft wird, dass der Bildschirm überhaupt aufbaut und seine Teile zeigt.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "de")
class NavScreenSmokeTest {

    @get:Rule
    val compose = createComposeRule()

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    /** Ein freier Zielpunkt, wie ihn der Nutzer anlegt. */
    private val ziel = Waypoint(
        id = 1L,
        name = "Testziel",
        latitude = 51.1,
        longitude = 7.1
    )

    private fun text(id: Int): String = context.getString(id)

    private fun text(id: Int, vararg args: Any): String = context.getString(id, *args)

    private fun render(
        waypoints: List<Waypoint> = emptyList(),
        expandedSection: String? = null,
        onExpandedSectionChange: (String?) -> Unit = {}
    ) {
        compose.setContent {
            GpsRadarTheme {
                NavigationScreen(
                    state = GpsLocationState(),
                    waypoints = waypoints,
                    caches = emptyList(),
                    freeWaypoints = waypoints,
                    activeWaypointId = null,
                    tracks = emptyList(),
                    activeTrackId = null,
                    activeTrackProgress = 0,
                    arrivalNotice = null,
                    onAddCurrentPosition = { _, _ -> },
                    onAddManual = { _, _, _, _ -> },
                    onUpdateWaypoint = {},
                    onProjectWaypoint = { _, _, _ -> "" },
                    onToggleFound = {},
                    onDeleteWaypoint = {},
                    onSetActiveWaypoint = {},
                    onDeleteTrack = {},
                    onSetActiveTrack = {},
                    onImportWaypoints = { _: Uri -> ImportResult(emptyList(), emptyList(), emptyList(), emptyList()) },
                    onExportGpx = { 0 },
                    onExportJson = { false },
                    hintR13Map = emptyMap(),
                    onToggleHintFormat = {},
                    isRecording = false,
                    isRecordingPaused = false,
                    recordingPointCount = 0,
                    onStartRecording = {},
                    onStopRecording = { "" },
                    onDefaultRecordingName = { "" },
                    onPauseRecording = {},
                    onResumeRecording = {},
                    onDiscardRecording = {},
                    onReverseTrack = {},
                    expandedSection = expandedSection,
                    onExpandedSectionChange = onExpandedSectionChange
                )
            }
        }
    }

    @Test
    fun leererZustand_zeigtAktiveKarteWerkzeugeUndVierAbteilungen() {
        render()

        compose.onNodeWithText(text(R.string.nav_active_target_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.nav_no_active_target)).assertIsDisplayed()

        compose.onNodeWithText(text(R.string.btn_save_position)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.btn_enter_coordinates)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.nav_tool_projection)).assertIsDisplayed()

        compose.onNodeWithText(text(R.string.nav_section_caches, 0)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.nav_section_other, 0)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.nav_tracks_title, 0)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.nav_section_files)).assertIsDisplayed()
    }

    @Test
    fun zielpunkt_zaehltInSeinerAbteilung() {
        render(waypoints = listOf(ziel))

        compose.onNodeWithText(text(R.string.nav_section_other, 1)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.nav_section_caches, 0)).assertIsDisplayed()
    }

    @Test
    fun aufgeklappteAbteilung_zeigtDenZielpunkt() {
        render(waypoints = listOf(ziel), expandedSection = SECTION_WAYPOINTS)

        compose.onNodeWithText("Testziel").assertIsDisplayed()
    }

    @Test
    fun klickAufAbteilung_meldetDerenId() {
        var gemeldet: String? = null
        render(onExpandedSectionChange = { gemeldet = it })

        compose.onNodeWithText(text(R.string.nav_tracks_title, 0)).performClick()

        assertEquals(SECTION_TRACKS, gemeldet)
    }

    @Test
    fun koordinatenKnopf_oeffnetDenDialog() {
        render()

        compose.onNodeWithText(text(R.string.btn_enter_coordinates)).performClick()

        compose.onNodeWithText(text(R.string.dlg_manual_title)).assertIsDisplayed()
    }
}

package de.gpsradar.satellites

import android.content.Context
import android.content.res.Configuration
import androidx.test.core.app.ApplicationProvider
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wacht über die Ressourcen, die einen Platzhalter enthalten.
 *
 * Ein falscher Platzhalter fällt weder beim Bauen noch beim Übersetzen auf —
 * er wirft erst zur Laufzeit (IllegalFormatException) und dann genau in dem
 * Moment, in dem der Bildschirm geöffnet wird. Deshalb wird hier jede
 * Format-Zeichenkette in beiden Sprachen einmal wirklich aufgelöst.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StringFormatTest {

  private val context: Context = ApplicationProvider.getApplicationContext()

  private fun germanContext(): Context {
    val config = Configuration(context.resources.configuration).apply {
      setLocale(Locale.GERMANY)
    }
    return context.createConfigurationContext(config)
  }

  /** Jeder Eintrag: Ressource und Beispiel-Argumente, wie sie die App einsetzt. */
  private val formatCases: List<Pair<Int, Array<Any>>> = listOf(
    R.string.about_version to arrayOf<Any>("1.3.6"),
    R.string.about_version_code to arrayOf<Any>("1.3.6", 11L),
    R.string.orientation_heading_up to arrayOf<Any>(270),
    R.string.filter_all to arrayOf<Any>(12),
    R.string.detail_id_fmt to arrayOf<Any>(7),
    R.string.detail_show_raw_n to arrayOf<Any>(3),
    R.string.raw_subtitle_fmt to arrayOf<Any>("GPS-7"),
    R.string.pos_used_sats to arrayOf<Any>(8, 12),
    R.string.pos_fix_view to arrayOf<Any>(8, 12),
    R.string.label_ttff to arrayOf<Any>(5),
    R.string.share_position_text to arrayOf<Any>("52.520008° N, 13.404954° E", "52.520008", "13.404954"),
    R.string.meas_state_bits to arrayOf<Any>(20, 30),
    R.string.meas_phase_unc to arrayOf<Any>("0.42"),
    R.string.meas_recording to arrayOf<Any>("4:12"),
    R.string.meas_stats to arrayOf<Any>(3, 120),
    R.string.nav_waypoints_title to arrayOf<Any>(4),
    R.string.nav_section_caches to arrayOf<Any>(9),
    R.string.nav_section_other to arrayOf<Any>(2),
    R.string.nav_tracks_title to arrayOf<Any>(3),
    R.string.nav_active_target to arrayOf<Any>("Parkplatz"),
    R.string.nav_direction_fmt to arrayOf<Any>("245°", "SW (Südwest)"),
    R.string.nav_track_progress to arrayOf<Any>(3, 42, "Anfang", "1,2 km"),
    R.string.hint_prefix_fmt to arrayOf<Any>("Unter der Bank"),
    R.string.proj_base to arrayOf<Any>("Parkplatz")
  )

  @Test
  fun `format strings resolve in default language`() {
    for ((id, args) in formatCases) {
      val name = context.resources.getResourceEntryName(id)
      val text = context.getString(id, *args)
      assertTrue("leerer Text bei $name", text.isNotBlank())
      assertTrue("Platzhalter nicht ersetzt bei $name: $text", !text.contains("%"))
    }
  }

  @Test
  fun `format strings resolve in german`() {
    val de = germanContext()
    for ((id, args) in formatCases) {
      val name = de.resources.getResourceEntryName(id)
      val text = de.getString(id, *args)
      assertTrue("leerer deutscher Text bei $name", text.isNotBlank())
      assertTrue("Platzhalter nicht ersetzt bei $name: $text", !text.contains("%"))
    }
  }

  @Test
  fun `plural picks correct form in both languages`() {
    assertEquals(
      "1 satellite (0 in fix)",
      context.resources.getQuantityString(R.plurals.list_count, 1, 1, 0)
    )
    assertEquals(
      "5 satellites (2 in fix)",
      context.resources.getQuantityString(R.plurals.list_count, 5, 5, 2)
    )
    val de = germanContext().resources
    assertEquals("1 Satellit (0 im Fix)", de.getQuantityString(R.plurals.list_count, 1, 1, 0))
    assertEquals("5 Satelliten (2 im Fix)", de.getQuantityString(R.plurals.list_count, 5, 5, 2))
    assertEquals("1 Signalpfad", de.getQuantityString(R.plurals.meas_paths, 1, 1))
    assertEquals("7 Signalpfade", de.getQuantityString(R.plurals.meas_paths, 7, 7))
    assertEquals("1 signal path", context.resources.getQuantityString(R.plurals.meas_paths, 1, 1))

    // Navigation: Plural für Dateien und Track-Metadaten
    assertEquals("1 file exported", context.resources.getQuantityString(R.plurals.export_files, 1, 1))
    assertEquals("3 Dateien exportiert", de.getQuantityString(R.plurals.export_files, 3, 3))
    assertEquals("1 Punkt · 0,5 km · ▶ Ende", de.getQuantityString(R.plurals.track_meta, 1, 1, "0,5 km", "▶ Ende"))
    assertEquals(
      "„Tour“ wird mit 1 Punkt gelöscht. Das lässt sich nicht rückgängig machen.",
      de.getQuantityString(R.plurals.dlg_delete_track_text, 1, "Tour", 1)
    )
  }

  @Test
  fun `german locale delivers german text`() {
    val de = germanContext()
    assertEquals("GPS Satelliten", de.getString(R.string.app_name))
    assertEquals("Über diese App", de.getString(R.string.about_title))
    assertEquals("Lizenz", de.getString(R.string.about_license_title))
    assertEquals("Technik", de.getString(R.string.about_tech_title))
  }

  @Test
  fun `default locale is english`() {
    assertEquals("About this app", context.getString(R.string.about_title))
    assertEquals("License", context.getString(R.string.about_license_title))
    assertNotEquals(
      context.getString(R.string.about_intro),
      germanContext().getString(R.string.about_intro)
    )
  }

  @Test
  fun `percent sign stays literal`() {
    // "100 % Datenschutz:" ist kein Format-String — das Prozentzeichen muss stehen bleiben.
    assertTrue(context.getString(R.string.about_privacy_label).contains("100 %"))
    assertTrue(germanContext().getString(R.string.about_privacy_label).contains("100 %"))
  }

  @Test
  fun `all used strings are filled in both languages`() {
    val de = germanContext().resources

    // Stichprobe über alle in der App verwendeten Ressourcen: keine darf leer sein.
    val ids = intArrayOf(
      R.string.permission_title, R.string.permission_rationale, R.string.btn_grant_permission,
      R.string.gps_disabled_title, R.string.gps_disabled_desc, R.string.btn_open_settings,
      R.string.demo_banner_title, R.string.demo_banner_text, R.string.switch_to_real,
      R.string.label_sats, R.string.label_avg_snr, R.string.label_accuracy_short,
      R.string.label_zenith, R.string.orientation_north_up,
      R.string.about_title, R.string.about_intro, R.string.about_privacy_label,
      R.string.about_privacy_text, R.string.about_honest_label, R.string.about_honest_text,
      R.string.about_free_label, R.string.about_free_text, R.string.about_raw_title,
      R.string.about_raw_para, R.string.about_raw_more_label, R.string.about_raw_more_text,
      R.string.about_pseudorange_label, R.string.about_pseudorange_text,
      R.string.about_docs_title, R.string.about_docs_para, R.string.about_docs_btn,
      R.string.about_tech_title, R.string.about_tech_system_text,
      R.string.about_tech_sensors_text, R.string.about_tech_map_text,
      R.string.about_license_line, R.string.about_license_text, R.string.about_license_btn
    )
    for (id in ids) {
      val name = de.getResourceEntryName(id)
      assertTrue("deutsch leer: $name", de.getString(id).isNotBlank())
      assertTrue("englisch leer: $name", context.getString(id).isNotBlank())
    }
  }
}

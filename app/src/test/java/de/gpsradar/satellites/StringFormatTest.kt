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
    R.string.filter_all to arrayOf<Any>(12)
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

package de.gpsradar.satellites

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.FixType
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.SatelliteInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

  @Test
  fun `read string from context`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val appName = context.getString(R.string.app_name)
    assertEquals("GPS Satelliten", appName)
  }

  @Test
  fun `test satellite info and constellation mapping`() {
    val sat = SatelliteInfo(
      svid = 4,
      constellation = Constellation.GPS,
      cn0DbHz = 42.5f,
      elevationDegrees = 65f,
      azimuthDegrees = 120f,
      usedInFix = true
    )

    assertEquals("GPS-4", sat.identifier)
    assertTrue(sat.usedInFix)
    assertEquals(Constellation.GPS, sat.constellation)
  }

  @Test
  fun `test gps location state calculations`() {
    val satellites = listOf(
      SatelliteInfo(svid = 1, constellation = Constellation.GPS, cn0DbHz = 40f, elevationDegrees = 50f, azimuthDegrees = 100f, usedInFix = true),
      SatelliteInfo(svid = 2, constellation = Constellation.GALILEO, cn0DbHz = 35f, elevationDegrees = 40f, azimuthDegrees = 200f, usedInFix = true),
      SatelliteInfo(svid = 3, constellation = Constellation.GLONASS, cn0DbHz = 20f, elevationDegrees = 15f, azimuthDegrees = 300f, usedInFix = false)
    )

    val state = GpsLocationState(
      hasPermission = true,
      fixType = FixType.FIX_3D,
      latitude = 52.520008,
      longitude = 13.404954,
      satellites = satellites
    )

    assertEquals(3, state.satellitesInView)
    assertEquals(2, state.satellitesInFix)
    assertTrue(state.avgCn0 > 30f)
    assertTrue(state.formattedCoordinatesDecimal.contains("52.520008"))
  }
}

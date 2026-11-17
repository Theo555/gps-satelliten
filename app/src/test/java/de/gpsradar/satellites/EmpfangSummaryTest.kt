package de.gpsradar.satellites

import de.gpsradar.satellites.gps.NavEphemerides
import de.gpsradar.satellites.model.Almanac
import de.gpsradar.satellites.model.CapabilityState
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.DeviceCapabilities
import de.gpsradar.satellites.model.KlobucharIono
import de.gpsradar.satellites.model.NavCorrections
import de.gpsradar.satellites.ui.EmpfangAbility
import de.gpsradar.satellites.ui.EmpfangSummaryBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Zusammenfassung der Ansicht „Empfang".
 *
 * Sie ist sprachfrei und ohne Oberfläche prüfbar — deshalb steht hier die
 * ganze Logik: welche Fähigkeiten erscheinen, wie gezählt wird und wann die
 * Bahndaten-Gruppe etwas zu zeigen hat.
 */
class EmpfangSummaryTest {

    private fun almanac(system: String, svid: Int) = Almanac(
        system = system,
        svid = svid,
        week = 2445,
        referenceSeconds = 405_504,
        sqrtA = 5153.7,
        eccentricity = 0.006,
        inclination = 0.96,
        ascendingNode = -0.5,
        argumentOfPerigee = 1.3,
        meanAnomaly = 1.34,
        nodeRate = -4.3e-7,
        clockBias = 1.6e-4,
        clockRate = -1.5e-10,
        health = 0
    )

    private val leereNavigation = NavEphemerides()

    @Test
    fun ohneDatenBleibenAlleSystemeBeiNull() {
        val summary = EmpfangSummaryBuilder.of(DeviceCapabilities(sdkInt = 33), leereNavigation)

        assertEquals(4, summary.systems.size)
        assertTrue(summary.systems.all { it.ephemerides == 0 && it.almanac == 0 })
        assertFalse(summary.hasOrbitData)
    }

    /** Auch ein Almanach allein ist „Bahndaten" — Ephemeriden braucht es nicht. */
    @Test
    fun almanachZaehltAlsBahndaten() {
        val nav = NavEphemerides(
            almanac = listOf(
                almanac("GPS", 12),
                almanac("BDS", 3),
                almanac("BDS", 20)
            )
        )

        val summary = EmpfangSummaryBuilder.of(DeviceCapabilities(sdkInt = 33), nav)

        assertTrue(summary.hasOrbitData)
        assertEquals(1, summary.systems.single { it.constellation == Constellation.GPS }.almanac)
        assertEquals(2, summary.systems.single { it.constellation == Constellation.BEIDOU }.almanac)
        assertEquals(0, summary.systems.single { it.constellation == Constellation.GALILEO }.almanac)
    }

    /**
     * Nachweislich fehlende Fähigkeiten werden nicht angeboten — dieselbe Regel
     * wie im Messung-Tab. „Unklar" bleibt sichtbar.
     */
    @Test
    fun fehlendeFaehigkeitFehltAuchHier() {
        val altePlattform = DeviceCapabilities(
            sdkInt = 24,
            antennaInfo = CapabilityState.UNSUPPORTED
        )

        val summary = EmpfangSummaryBuilder.of(altePlattform, leereNavigation)

        assertTrue(summary.abilities.none { it.ability == EmpfangAbility.ANTENNA })
        // Die übrigen bleiben: unbekannt ist anbietbar.
        assertEquals(3, summary.abilities.size)
        assertTrue(summary.abilities.any { it.ability == EmpfangAbility.RAW_MEASUREMENTS })
    }

    @Test
    fun unklareFaehigkeitBleibtSichtbar() {
        val summary = EmpfangSummaryBuilder.of(DeviceCapabilities(sdkInt = 33), leereNavigation)

        assertEquals(4, summary.abilities.size)
        assertTrue(
            summary.abilities.all { it.state == CapabilityState.UNKNOWN }
        )
    }

    @Test
    fun ionosphaereWirdGenannt() {
        val nav = NavEphemerides(
            corrections = NavCorrections(
                gpsIono = KlobucharIono(
                    alpha = listOf(1.0, 2.0, 3.0, 4.0),
                    beta = listOf(5.0, 6.0, 7.0, 8.0)
                )
            )
        )

        val summary = EmpfangSummaryBuilder.of(DeviceCapabilities(sdkInt = 33), nav)

        assertEquals(listOf(Constellation.GPS), summary.ionosphereSystems)
        assertTrue(summary.utcSystems.isEmpty())
    }
}

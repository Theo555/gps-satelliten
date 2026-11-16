package de.gpsradar.satellites

import de.gpsradar.satellites.model.Almanac
import de.gpsradar.satellites.model.AlmanacFile
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Prüft die Almanach-Datei: Kopfzeilen, Spaltenfolge, leere Liste. */
class AlmanacFileTest {

    private fun satz() = Almanac(
        system = "GPS",
        svid = 17,
        week = 2445,
        referenceSeconds = 233_472,
        sqrtA = 5153.6890,
        eccentricity = 0.012607098,
        inclination = 0.956 * PI,
        ascendingNode = -2.567 * PI,
        argumentOfPerigee = -0.334 * PI,
        meanAnomaly = -0.518 * PI,
        nodeRate = -4.6e-10,
        clockBias = -2.1458e-04,
        clockRate = -2.1834e-11,
        health = 0
    )

    @Test
    fun kopfErklaertDasFormat() {
        val datei = AlmanacFile.write(listOf(satz()), listOf("geraet = Test"))
        val zeilen = datei.lines()
        assertTrue(zeilen.any { it.contains("geraet = Test") })
        assertTrue(zeilen.any { it.startsWith("# spalten: system svid woche") })
        // Der Hinweis, dass die Auswertung den Almanach nicht braucht, gehoert
        // in die Datei — sonst sucht spaeter jemand die Satze in der RINEX-Datei.
        assertTrue(zeilen.any { it.contains("RINEX kennt keinen Almanach") })
    }

    @Test
    fun wertzeileHatDieAngekuendigteSpaltenzahl() {
        val datei = AlmanacFile.write(listOf(satz()))
        val werte = datei.lines().first { !it.startsWith("#") }
        assertEquals(14, werte.trim().split(Regex("\\s+")).size)
        assertTrue(werte.startsWith("GPS 17 2445 233472 "))
        // Winkel in Grad, af0 in Sekunden.
        assertTrue(werte.contains("-2.1458e-04"))
        assertTrue(werte.contains(" 0"))
    }

    @Test
    fun leereListeErgibtNurKopf() {
        val datei = AlmanacFile.write(emptyList())
        assertTrue(datei.lines().all { it.startsWith("#") || it.isEmpty() })
    }
}

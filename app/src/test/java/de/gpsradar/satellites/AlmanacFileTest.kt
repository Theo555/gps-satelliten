package de.gpsradar.satellites

import de.gpsradar.satellites.model.Almanac
import de.gpsradar.satellites.model.AlmanacFile
import de.gpsradar.satellites.model.GloAlmanac
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

    /**
     * GLONASS bekommt einen eigenen Block: Der Satz hat keine Halbachse und
     * kein `M0`, dafür Knotenzeit und Draconische Periode. Die Knotenzeit hat
     * 1/32 s Auflösung — sie darf nicht auf ganze Sekunden gerundet werden.
     */
    @Test
    fun glonassBlockHatEigeneSpalten() {
        val glo = GloAlmanac(
            svid = 11,
            frequencyChannel = 0,
            nodeSeconds = 39_543.34375,
            longitude = 0.1778968,
            inclinationCorrection = 0.0279232,
            draconianPeriodCorrection = -2_655.5215,
            draconianPeriodRate = -0.001708984,
            eccentricity = 0.000469208,
            argumentOfPerigee = -2.0,
            clockCorrection = 0.0,
            satelliteType = 1,
            health = 1
        )

        val datei = AlmanacFile.write(listOf(satz()), gloAlmanac = listOf(glo))
        val zeilen = datei.lines()
        assertTrue(zeilen.any { it.startsWith("# spalten: system svid t_lambda_s") })
        val werte = zeilen.last { it.isNotBlank() && !it.startsWith("#") }
        val spalten = werte.trim().split(Regex("\\s+"))
        assertEquals(11, spalten.size)
        assertEquals("GLO", spalten[0])
        assertEquals("11", spalten[1])
        assertEquals("39543.34", spalten[2])
        assertEquals("10.1927", spalten[3])
        assertEquals("1.5999", spalten[4])
        // 43200 − 2655,5215
        assertEquals("40544", spalten[5])
        assertEquals("-1.709e-03", spalten[6])
        assertEquals("0", spalten[8])
        assertEquals("1", spalten[9])
        assertEquals("1", spalten[10])
    }
}

package de.gpsradar.satellites.gps

import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.FixType
import de.gpsradar.satellites.model.GpsLocationState
import de.gpsradar.satellites.model.SatelliteInfo
import java.util.Random
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Simulationsmodus: erfundene Satelliten und eine erfundene Position um
 * Berlin, damit die Oberfläche ohne Empfang etwas anzeigen kann.
 *
 * Der Modus schaltet sich nie selbst ein – erfundene Satelliten und eine
 * perfekte Position dürfen nie als echte Messwerte erscheinen (Lehre aus der
 * F-Droid-Rückmeldung). Nur der Schalter in der Oberfläche startet ihn.
 *
 * Eigene Datei, weil die Zahlenreihen hier viel Platz brauchen und mit
 * Satellitenempfang nichts zu tun haben.
 */
class DemoScenario(
    private val scope: CoroutineScope,
    private val state: MutableStateFlow<GpsLocationState>
) {

    private var job: Job? = null
    private val random = Random()
    private val satellites: List<SimulatedSatellite> = buildSatellites()

    /** true, solange die Schleife läuft. */
    val isRunning: Boolean get() = job?.isActive == true

    fun start() {
        job?.cancel()
        job = scope.launch {
            var simTimeSec = 0
            while (isActive) {
                simTimeSec++
                updateState(simTimeSec)
                delay(1000)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private fun updateState(elapsedSeconds: Int) {
        val updatedSatellites = satellites.map { sim ->
            // Subtle orbital motion: azimuth advances slowly, elevation oscillates gently
            val currentAzimuth = (sim.initialAzimuth + (elapsedSeconds * sim.azimuthDriftRate)) % 360f
            val elevationDelta = (sin((elapsedSeconds + sim.svid * 10) * 0.05) * 2.0).toFloat()
            val currentElevation = (sim.initialElevation + elevationDelta).coerceIn(5f, 88f)

            // Signal strength fluctuates slightly (+- 1.5 dB)
            val noise = (random.nextFloat() * 2.5f - 1.25f)
            val currentCn0 = (sim.baseCn0 + noise).coerceIn(12f, 48f)

            // High elevation and good SNR -> used in fix
            val inFix = currentElevation > 15f && currentCn0 >= 26f

            SatelliteInfo(
                svid = sim.svid,
                constellation = sim.constellation,
                cn0DbHz = currentCn0,
                elevationDegrees = currentElevation,
                azimuthDegrees = currentAzimuth,
                hasEphemeris = true,
                hasAlmanac = true,
                usedInFix = inFix,
                carrierFrequencyHz = sim.frequencyHz
            )
        }

        val inFixCount = updatedSatellites.count { it.usedInFix }
        val fixType = when {
            inFixCount >= 4 -> FixType.FIX_3D
            inFixCount >= 3 -> FixType.FIX_2D
            else -> FixType.NO_FIX
        }

        // Simulated location drift around a realistic city coordinate (Berlin center)
        val latOffset = sin(elapsedSeconds * 0.02) * 0.00018
        val lonOffset = cos(elapsedSeconds * 0.02) * 0.00025
        val simLat = 52.520008 + latOffset
        val simLon = 13.404954 + lonOffset
        val simAlt = 42.5 + sin(elapsedSeconds * 0.05) * 1.5
        val simSpeed = 3.6f + (sin(elapsedSeconds * 0.1).toFloat() * 1.2f).coerceAtLeast(0f)
        val simBearing = ((elapsedSeconds * 2.5f) % 360f)
        val simAccuracy = (2.2f + (random.nextFloat() * 1.4f)).coerceIn(1.8f, 5.0f)

        state.update { current ->
            current.copy(
                satellites = updatedSatellites,
                fixType = fixType,
                latitude = simLat,
                longitude = simLon,
                altitudeMeters = simAlt,
                speedKmh = simSpeed,
                bearingDegrees = simBearing,
                accuracyMeters = simAccuracy,
                timeMillis = System.currentTimeMillis(),
                ttffSeconds = current.ttffSeconds ?: 12,
                lastUpdateTime = System.currentTimeMillis()
            )
        }
    }

    private data class SimulatedSatellite(
        val svid: Int,
        val constellation: Constellation,
        val initialElevation: Float,
        val initialAzimuth: Float,
        val baseCn0: Float,
        val azimuthDriftRate: Float,
        val frequencyHz: Float
    )

    private fun buildSatellites(): List<SimulatedSatellite> = listOf(
        // GPS (USA)
        SimulatedSatellite(4, Constellation.GPS, 72f, 45f, 43.5f, 0.08f, 1575420000f),
        SimulatedSatellite(9, Constellation.GPS, 54f, 130f, 39.0f, -0.06f, 1575420000f),
        SimulatedSatellite(16, Constellation.GPS, 38f, 215f, 34.2f, 0.07f, 1176450000f),
        SimulatedSatellite(20, Constellation.GPS, 81f, 310f, 44.8f, -0.05f, 1575420000f),
        SimulatedSatellite(27, Constellation.GPS, 22f, 95f, 28.5f, 0.09f, 1575420000f),
        SimulatedSatellite(31, Constellation.GPS, 14f, 290f, 21.0f, -0.08f, 1575420000f),

        // Galileo (EU)
        SimulatedSatellite(3, Constellation.GALILEO, 68f, 170f, 42.0f, 0.05f, 1575420000f),
        SimulatedSatellite(11, Constellation.GALILEO, 49f, 260f, 37.8f, -0.06f, 1176450000f),
        SimulatedSatellite(19, Constellation.GALILEO, 78f, 20f, 45.2f, 0.04f, 1575420000f),
        SimulatedSatellite(25, Constellation.GALILEO, 31f, 140f, 31.4f, -0.07f, 1176450000f),
        SimulatedSatellite(36, Constellation.GALILEO, 18f, 340f, 24.6f, 0.08f, 1176450000f),

        // GLONASS (RU)
        SimulatedSatellite(2, Constellation.GLONASS, 61f, 80f, 38.6f, 0.07f, 1602000000f),
        SimulatedSatellite(8, Constellation.GLONASS, 44f, 190f, 35.1f, -0.06f, 1602000000f),
        SimulatedSatellite(15, Constellation.GLONASS, 26f, 280f, 27.4f, 0.08f, 1602000000f),
        SimulatedSatellite(22, Constellation.GLONASS, 12f, 25f, 19.8f, -0.05f, 1602000000f),

        // BeiDou (CN)
        SimulatedSatellite(6, Constellation.BEIDOU, 57f, 110f, 41.2f, 0.06f, 1561098000f),
        SimulatedSatellite(12, Constellation.BEIDOU, 70f, 240f, 43.0f, -0.05f, 1561098000f),
        SimulatedSatellite(18, Constellation.BEIDOU, 35f, 325f, 32.5f, 0.07f, 1561098000f),
        SimulatedSatellite(24, Constellation.BEIDOU, 20f, 60f, 26.0f, -0.08f, 1561098000f),

        // QZSS (JP) & SBAS (EGNOS)
        SimulatedSatellite(1, Constellation.QZSS, 42f, 155f, 33.0f, 0.03f, 1575420000f),
        SimulatedSatellite(123, Constellation.SBAS, 28f, 185f, 30.5f, 0.01f, 1575420000f)
    )
}

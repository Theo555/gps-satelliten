package de.gpsradar.satellites.model

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Sammelt die Punkte einer laufenden Track-Aufzeichnung.
 *
 * Der Zustand (läuft / pausiert / Punkte) lebt hier und nicht im `GpsViewModel`,
 * damit das ViewModel nur noch „Start/Pause/Weiter/Stopp/Verwerfen" durchreicht
 * und das Speichern übernimmt. Aufgezeichnet wird aus einem Positions-Fluss;
 * ein neuer Punkt entsteht erst, wenn der Abstand zum letzten mindestens
 * [minDistanceMeters] beträgt (GPS-Rauschen im Stand erzeugt sonst Punktwolken).
 */
class TrackRecorder(
    private val scope: CoroutineScope,
    private val minDistanceMeters: Double = DEFAULT_MIN_DISTANCE_M
) {

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isPaused = MutableStateFlow(false)
    val isPaused: StateFlow<Boolean> = _isPaused

    private val _points = MutableStateFlow<List<TrackPoint>>(emptyList())
    val points: StateFlow<List<TrackPoint>> = _points

    private var source: Flow<GpsLocationState>? = null
    private var job: Job? = null

    /**
     * Startet eine neue Aufzeichnung aus [positions]. Läuft bereits eine, bleibt
     * alles unverändert – ein doppelter Start darf gesammelte Punkte nicht
     * wegwerfen.
     */
    fun start(positions: Flow<GpsLocationState>) {
        if (_isRecording.value) return
        source = positions
        _points.value = emptyList()
        _isRecording.value = true
        _isPaused.value = false
        collect(positions)
    }

    fun pause() {
        if (!_isRecording.value || _isPaused.value) return
        job?.cancel()
        job = null
        _isPaused.value = true
    }

    fun resume() {
        val positions = source ?: return
        if (!_isRecording.value || !_isPaused.value) return
        _isPaused.value = false
        collect(positions)
    }

    /** Beendet die Aufzeichnung und gibt die gesammelten Punkte zurück. */
    fun stop(): List<TrackPoint> {
        val collected = _points.value
        end()
        return collected
    }

    /** Beendet die Aufzeichnung und wirft die Punkte weg. */
    fun discard() {
        end()
    }

    private fun end() {
        job?.cancel()
        job = null
        source = null
        _isRecording.value = false
        _isPaused.value = false
        _points.value = emptyList()
    }

    private fun collect(positions: Flow<GpsLocationState>) {
        job = scope.launch {
            positions.collect { state ->
                val lat = state.latitude ?: return@collect
                val lon = state.longitude ?: return@collect
                val last = _points.value.lastOrNull()
                val farEnough = last == null ||
                    Navigation.distanceMeters(last.latitude, last.longitude, lat, lon) >= minDistanceMeters
                if (farEnough) {
                    _points.value = _points.value + TrackPoint(
                        latitude = lat,
                        longitude = lon,
                        elevationMeters = state.altitudeMeters,
                        timeMillis = state.timeMillis
                    )
                }
            }
        }
    }

    companion object {
        /** Kleinster Abstand zwischen zwei aufgezeichneten Punkten. */
        const val DEFAULT_MIN_DISTANCE_M = 5.0
    }
}

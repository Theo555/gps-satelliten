package de.gpsradar.satellites.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssAntennaInfo
import android.location.GnssCapabilities
import android.location.GnssClock
import android.location.GnssMeasurement
import android.location.GnssMeasurementsEvent
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import android.util.Log
import de.gpsradar.satellites.model.AntennaSpec
import de.gpsradar.satellites.model.BufferStats
import de.gpsradar.satellites.model.CapabilityState
import de.gpsradar.satellites.model.CapturePhase
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.DeviceCapabilities
import de.gpsradar.satellites.model.EpochBuffer
import de.gpsradar.satellites.model.MultipathState
import de.gpsradar.satellites.model.MeasurementCsv
import de.gpsradar.satellites.model.Pseudorange
import de.gpsradar.satellites.model.PseudorangeResult
import de.gpsradar.satellites.model.PseudorangeStatus
import de.gpsradar.satellites.model.RawClockSnapshot
import de.gpsradar.satellites.model.RawEpoch
import de.gpsradar.satellites.model.RawMeasurement
import de.gpsradar.satellites.model.SatelliteInfo
import de.gpsradar.satellites.model.SupportedSignal
import de.gpsradar.satellites.model.gnssBandLabel
import de.gpsradar.satellites.model.gnssBandShort
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "RawGnssManager"
private const val MAX_REGISTER_ATTEMPTS = 5
private const val REGISTER_RETRY_MS = 2000L

// Wachhund: Der Empfänger kann registriert sein und trotzdem schweigen
// (z. B. nach einem Prozess-Neustart, während Standort/Status weiterlaufen).
// Dann erst neu registrieren, nach mehreren Fehlversuchen einen Hinweis
// für die Oberfläche setzen.
private const val STALL_CHECK_MS = 10_000L
private const val STALL_THRESHOLD_MS = 30_000L
private const val MAX_RECOVERY_ATTEMPTS = 4
private const val RECOVER_CHECK_MS = 15_000L

/**
 * Sammelt GNSS-Roh-Messungen (GnssMeasurements) — ein Eintrag pro Satellit × Frequenzband.
 *
 * @param satellites Satellitenlage (Höhenwinkel, Azimut) aus dem Satellitenstatus;
 *   die Messungen selbst enthalten sie nicht. Leere Liste = keine brauchbare
 *   Lage – dann bleiben die Spalten im Export leer.
 */
class RawGnssManager(
    context: Context,
    private val satellites: () -> List<SatelliteInfo>
) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val handler = Handler(Looper.getMainLooper())

    // Ab Android 11 verlangt die Registrierung einen Executor. Wir geben ihr den
    // Haupt-Thread, damit die Rueckrufe wie bisher dort ankommen.
    private val mainExecutor = Executor { handler.post(it) }

    /** true, solange ein Mess-Callback registriert ist. */
    private var registered = false
    /** Sammelt die gelieferten Rohfelder über die ganze Sitzung. */
    private var delivery = MeasurementCsv.DeliveryTracker()
    private var lastDeliverySummary: String? = null
    private var registerAttempts = 0

    /** Zeitpunkt des letzten Mess-Ereignisses (elapsedRealtime); 0 = noch keins. */
    private var lastEventAt = 0L

    /** Wann [start] zuletzt lief – Bezugspunkt für "noch nie etwas geliefert". */
    private var startedAt = 0L
    private var stallAttempts = 0
    private val stallWatchdog = Runnable { checkStall() }

    private val _streamStalled = MutableStateFlow(false)
    /** true, wenn registriert, aber seit >30 s keine Rohdaten ankommen. */
    val streamStalled: StateFlow<Boolean> = _streamStalled.asStateFlow()

    private val registerRetry = Runnable { registerIfPossible() }

    private val _supportsRawMeasurements = MutableStateFlow(false)
    val supportsRawMeasurements: StateFlow<Boolean> = _supportsRawMeasurements.asStateFlow()

    private val _capabilities = MutableStateFlow(DeviceCapabilities(sdkInt = Build.VERSION.SDK_INT))

    /**
     * Was dieses Gerät kann – einmal je [start] ermittelt.
     *
     * Damit lässt sich vor der ersten Messung sagen, ob etwa Trägerphase
     * überhaupt zu erwarten ist oder welche Signalwege der Chip kennt.
     * Wo die Plattform die Frage nicht beantwortet, bleibt der Zustand
     * `UNKNOWN` – Angebotenes wird dann nicht ausgeblendet.
     */
    val capabilities: StateFlow<DeviceCapabilities> = _capabilities.asStateFlow()

    private val _measurements = MutableStateFlow<List<RawMeasurement>>(emptyList())
    val measurements: StateFlow<List<RawMeasurement>> = _measurements.asStateFlow()

    private val _clock = MutableStateFlow<RawClockSnapshot?>(null)
    val clock: StateFlow<RawClockSnapshot?> = _clock.asStateFlow()

    /** Letzte 60 s im Normalbetrieb, alles während einer Aufzeichnung. */
    private val buffer = EpochBuffer()

    private val _capturePhase = MutableStateFlow(CapturePhase.IDLE)
    val capturePhase: StateFlow<CapturePhase> = _capturePhase.asStateFlow()

    private val _bufferStats = MutableStateFlow(BufferStats(0, 0))
    val bufferStats: StateFlow<BufferStats> = _bufferStats.asStateFlow()

    private val callback = object : GnssMeasurementsEvent.Callback() {
        override fun onGnssMeasurementsReceived(event: GnssMeasurementsEvent) {
            handleEvent(event)
        }

        override fun onStatusChanged(status: Int) {
            // Für die Anzeige nicht relevant.
        }
    }

    @SuppressLint("MissingPermission")
    fun start(hasPermission: Boolean) {
        if (!hasPermission) {
            stop()
            _supportsRawMeasurements.value = false
            return
        }
        val lm = locationManager
        if (lm == null) {
            _supportsRawMeasurements.value = false
            return
        }

        if (!registered) {
            registerAttempts = 0
            registerIfPossible()
        }
        startedAt = SystemClock.elapsedRealtime()

        // Fähigkeiten dieses Geräts einsammeln (Details: model/DeviceCapabilities).
        val caps = readCapabilities(lm)
        _capabilities.value = caps

        // Ab Android 12 sagt die Plattform die Fähigkeit direkt. Der Rückgabewert
        // der Registrierung ist unzuverlässig: kurz nach dem Prozessstart oder
        // wenn die GNSS-Engine gerade steht, liefert er false – das Gerät kann
        // Rohdaten trotzdem. Deshalb nur bei klarer Auskunft darauf hören.
        _supportsRawMeasurements.value = when (caps.rawMeasurements) {
            CapabilityState.SUPPORTED -> true
            CapabilityState.UNSUPPORTED -> false
            CapabilityState.UNKNOWN -> registered
        }

        // Wachhund: Registrierung klappt, aber der Empfänger kann trotzdem
        // schweigen. Erst neu registrieren, nach mehreren Fehlversuchen den
        // Hinweis in der Oberfläche setzen.
        lastEventAt = 0L
        stallAttempts = 0
        _streamStalled.value = false
        handler.removeCallbacks(stallWatchdog)
        handler.postDelayed(stallWatchdog, STALL_CHECK_MS)
    }

    /**
     * Liest, was die Plattform über dieses Gerät verrät.
     *
     * Die Wachen sind je Methodengruppe gesetzt, nicht pauschal: `GnssCapabilities`
     * gibt es zwar ab API 30, die brauchbaren Abfragen aber erst ab 31
     * (`hasMeasurements`, `hasNavigationMessages`, `hasAntennaInfo`) und
     * `hasAccumulatedDeltaRange`/`getGnssSignalTypes` erst ab 34. Ein zu früher
     * Aufruf wäre kein `Exception`, sondern ein `NoSuchMethodError` – deshalb
     * steht jeder Block einzeln und fängt `Throwable`: Hardware-Angaben dürfen
     * nie einen Absturz auslösen. Was nicht lesbar ist, bleibt `UNKNOWN` und
     * wird damit weiterhin angeboten.
     */
    @SuppressLint("MissingPermission")
    private fun readCapabilities(lm: LocationManager): DeviceCapabilities {
        var caps = DeviceCapabilities(sdkInt = Build.VERSION.SDK_INT)

        // Die Vergleiche stehen bewusst direkt auf Build.VERSION.SDK_INT: Lint
        // kann einer lokalen Kopie der Versionsnummer nicht folgen und meldet
        // dann jeden Aufruf darunter als Fehler (19 Fehlalarme am 18.11.2026).
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            caps = try {
                val platform = lm.gnssCapabilities
                caps.copy(
                    rawMeasurements = platform.hasMeasurements().toCapabilityState(),
                    navigationMessages = platform.hasNavigationMessages().toCapabilityState(),
                    antennaInfo = platform.hasAntennaInfo().toCapabilityState()
                )
            } catch (e: Throwable) {
                Log.d(TAG, "gnssCapabilities (API 31) nicht lesbar", e)
                caps
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            caps = try {
                val platform = lm.gnssCapabilities
                caps.copy(
                    accumulatedDeltaRange = platform.hasAccumulatedDeltaRange().toCapabilityState(),
                    corrections = platform.hasMeasurementCorrections().toCapabilityState(),
                    signals = platform.gnssSignalTypes.orEmpty().map {
                        SupportedSignal(
                            constellation = Constellation.fromTypeId(it.constellationType),
                            carrierFrequencyHz = it.carrierFrequencyHz.toLong(),
                            codeType = it.codeType?.takeIf { code -> code.isNotBlank() }
                        )
                    }
                )
            } catch (e: Throwable) {
                Log.d(TAG, "gnssCapabilities (API 34) nicht lesbar", e)
                caps
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            caps = try {
                caps.copy(antennas = lm.gnssAntennaInfos.orEmpty().map { it.toAntennaSpec() })
            } catch (e: Throwable) {
                Log.d(TAG, "gnssAntennaInfos nicht lesbar", e)
                caps
            }
        }

        Log.i(TAG, "Fähigkeiten: ${caps.csvSummary()}")
        return caps
    }

    /** `GnssCapabilities` meldet Ja/Nein/Unbekannt als Zahlen. */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun Int.toCapabilityState(): CapabilityState = when (this) {
        GnssCapabilities.CAPABILITY_SUPPORTED -> CapabilityState.SUPPORTED
        GnssCapabilities.CAPABILITY_UNSUPPORTED -> CapabilityState.UNSUPPORTED
        else -> CapabilityState.UNKNOWN
    }

    /** `hasX()` liefert nur wahr/falsch – „nein“ ist hier eine belastbare Auskunft. */
    private fun Boolean.toCapabilityState(): CapabilityState =
        if (this) CapabilityState.SUPPORTED else CapabilityState.UNSUPPORTED

    /**
     * Antennenmaße in eigene Felder überführen.
     *
     * Die Annotation hält den Vertrag fest: Der Aufrufer muss die
     * Versionswache stellen. Ohne sie meldet Lint hier jeden Zugriff als
     * Fehler, ohne dass man sieht, woher die Pflicht kommt.
     */
    @RequiresApi(Build.VERSION_CODES.R)
    private fun GnssAntennaInfo.toAntennaSpec(): AntennaSpec {
        val offset = phaseCenterOffset
        return AntennaSpec(
            carrierFrequencyHz = (carrierFrequencyMHz * 1_000_000.0).toLong(),
            offsetXMm = offset.xOffsetMm,
            offsetYMm = offset.yOffsetMm,
            offsetZMm = offset.zOffsetMm,
            hasVariationCorrections = phaseCenterVariationCorrections != null
        )
    }

    @SuppressLint("MissingPermission")
    private fun registerIfPossible() {
        if (registered) return
        val lm = locationManager ?: return
        val ok = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.registerGnssMeasurementsCallback(mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                lm.registerGnssMeasurementsCallback(callback)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Rohdaten-Callback abgelehnt", e)
            false
        }
        registered = ok
        if (ok) {
            registerAttempts = 0
            delivery = MeasurementCsv.DeliveryTracker()
            lastDeliverySummary = null
            Log.i(TAG, "Rohdaten-Callback registriert")
        } else if (registerAttempts < MAX_REGISTER_ATTEMPTS) {
            registerAttempts++
            Log.w(TAG, "Rohdaten-Callback noch nicht möglich (Versuch $registerAttempts)")
            handler.postDelayed(registerRetry, REGISTER_RETRY_MS)
        }
    }

    fun stop() {
        handler.removeCallbacks(registerRetry)
        handler.removeCallbacks(stallWatchdog)
        registerAttempts = 0
        lastEventAt = 0L
        stallAttempts = 0
        _streamStalled.value = false
        if (registered) {
            try {
                locationManager?.unregisterGnssMeasurementsCallback(callback)
            } catch (e: Exception) {
                // ignore
            }
            registered = false
        }
        _measurements.value = emptyList()
        _clock.value = null
        // Aufzeichnung nicht verwerfen: einfrieren, damit z. B. ein Drehen des
        // Geräts (Activity wird neu erstellt) die gesammelten Daten nicht löscht.
        if (_capturePhase.value == CapturePhase.CAPTURING) {
            finishCapture()
        }
    }

    /**
     * true, wenn seit dem letzten [start] mindestens ein Messsatz ankam.
     *
     * Nach dem Start dauert es je nach Empfänger 2–10 s bis zum ersten Satz;
     * bleibt es dabei, hat der Messkanal den Empfänger nicht erreicht.
     */
    fun hasDelivered(): Boolean = lastEventAt != 0L

    /**
     * Wie lange der Messkanal schon still ist – seit dem letzten Satz oder, wenn
     * noch keiner kam, seit dem Start.
     */
    fun silentMillis(): Long {
        val now = SystemClock.elapsedRealtime()
        val since = if (lastEventAt == 0L) startedAt else lastEventAt
        return if (since == 0L) 0L else now - since
    }

    /** Aufzeichnung starten: ab jetzt bleibt alles stehen, bis sie endet. */
    fun startCapture() {
        buffer.clear()
        buffer.keepAll = true
        _capturePhase.value = CapturePhase.CAPTURING
        publishStats()
    }

    /** Zeit abgelaufen (oder vom Nutzer beendet): Puffer einfrieren. */
    fun finishCapture() {
        if (_capturePhase.value != CapturePhase.CAPTURING) return
        buffer.freeze()
        _capturePhase.value = CapturePhase.FINISHED
        publishStats()
    }

    /** Laufende Aufzeichnung abbrechen. */
    fun cancelCapture() = resetCapture()

    /** Fertige Aufzeichnung verwerfen, zurück in den Ringpuffer-Betrieb. */
    fun discardCapture() = resetCapture()

    private fun resetCapture() {
        buffer.clear()
        _capturePhase.value = CapturePhase.IDLE
        publishStats()
    }

    /** Aktueller Pufferinhalt für den Export. */
    fun epochSnapshot(): List<RawEpoch> = buffer.snapshot()

    private fun publishStats() {
        _bufferStats.value = BufferStats(buffer.epochCount, buffer.rowCount)
    }

    /** Wachhund-Prüfung: registriert, aber keine Daten? Erst neu registrieren,
     *  nach mehreren Fehlversuchen den Hinweis-Zustand setzen. Sobald wieder
     *  Ereignisse ankommen, setzt handleEvent alles zurück. */
    private fun checkStall() {
        if (!registered) return
        val now = SystemClock.elapsedRealtime()
        val silent = lastEventAt == 0L || now - lastEventAt > STALL_THRESHOLD_MS
        if (!silent) {
            _streamStalled.value = false
            handler.postDelayed(stallWatchdog, STALL_CHECK_MS)
            return
        }
        if (stallAttempts < MAX_RECOVERY_ATTEMPTS) {
            stallAttempts++
            val since = if (lastEventAt == 0L) "Start" else "${(now - lastEventAt) / 1000}s"
            Log.w(TAG, "Keine Rohdaten seit $since – Recover-Versuch $stallAttempts (neu registrieren)")
            tryRecover()
            handler.postDelayed(stallWatchdog, RECOVER_CHECK_MS)
        } else {
            _streamStalled.value = true
            Log.w(TAG, "Rohdaten bleiben aus – Hinweis in der Oberfläche anzeigen")
            handler.postDelayed(stallWatchdog, STALL_CHECK_MS * 6)
        }
    }

    /** Abmelden und neu registrieren, ohne die Fähigkeit zurückzustufen. */
    private fun tryRecover() {
        val lm = locationManager ?: return
        if (registered) {
            try {
                lm.unregisterGnssMeasurementsCallback(callback)
            } catch (_: Exception) {
                // ignore
            }
            registered = false
        }
        registerAttempts = 0
        registerIfPossible()
    }

    private fun handleEvent(event: GnssMeasurementsEvent) {
        lastEventAt = SystemClock.elapsedRealtime()
        _streamStalled.value = false
        val clock = event.clock
        val snapshot = clock.toSnapshot()
        _clock.value = snapshot

        // GnssClock.HAS_NO_FULL_BIAS = Long.MIN_VALUE ("unbekannt"); HAS_NO_BIAS = NaN.
        val clockUsable = clock.fullBiasNanos != Long.MIN_VALUE && !clock.biasNanos.isNaN()
        // ACHTUNG Genauigkeit: timeNanos und fullBiasNanos liegen bei ~1,5e18 ns.
        // Ein Double loest dort nur 256 ns auf (= 77 m). Deshalb werden die grossen
        // Terme in Long verrechnet; nur die Differenz (~1e8 ns) geht nach Double.
        val tRxNanos = clock.timeNanos - clock.fullBiasNanos
        val biasNanos = clock.biasNanos
        // Schaltsekunden braucht nur GLONASS; das Gerät meldet sie nicht, siehe Pseudorange.
        val leapSecond = clock.leapSecond

        // Höhenwinkel und Azimut stehen nur im Satellitenstatus, nicht in der
        // Messung selbst. Einmal je Epoche zuordnen (SVID + System); fehlt ein
        // Satellit dort, bleiben die Spalten leer.
        val geometry = satellites().associateBy { it.svid to it.constellation }

        val list = event.measurements.mapNotNull { measurement ->
            measurement.toRawMeasurement(tRxNanos, biasNanos, leapSecond, clockUsable, geometry)
        }

        // Einmal je Registrierung protokollieren, welche optionalen Felder dieses
        // Gerät wirklich liefert – sonst sieht man im CSV nur leere Zellen und
        // weiss nicht, ob das Gerät sie nicht kann oder ob gerade kein Wert kam.
        // Gesammelt über alle Messsätze: der erste kann Felder vermissen, die
        // erst mit dem Satellitenstatus kommen. Gemeldet wird nur eine Änderung.
        delivery.add(list, snapshot)
        val summary = delivery.summary()
        if (summary != lastDeliverySummary) {
            lastDeliverySummary = summary
            Log.i(TAG, "Rohdatenfelder: $summary")
        }

        // Pro (SVID, Band) nur den letzten Wert behalten.
        val latest = list
            .groupBy { it.svid to it.carrierFrequencyHz }
            .values
            .map { it.last() }
            .sortedWith(compareByDescending<RawMeasurement> { it.cn0DbHz })

        _measurements.value = latest

        buffer.add(RawEpoch(System.currentTimeMillis(), snapshot, latest))
        publishStats()
    }

    private fun GnssMeasurement.toRawMeasurement(
        tRxNanos: Long,
        biasNanos: Double,
        leapSecond: Int,
        clockUsable: Boolean,
        geometry: Map<Pair<Int, Constellation>, SatelliteInfo>
    ): RawMeasurement? {
        val freq = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && hasCarrierFrequencyHz()) {
            carrierFrequencyHz
        } else {
            0f
        }

        // GnssMeasurement.getConstellationType() gibt es seit API 24 (= minSdk) und ist
        // eindeutig. Die Frequenz-Heuristik kann L1/E1/QZSS/SBAS (alle 1575,42 MHz)
        // nicht trennen und dient deshalb nur als Notnagel bei CONSTELLATION_UNKNOWN.
        val constellation = Constellation.fromTypeId(constellationType)
            .let { if (it == Constellation.UNKNOWN) constellationForFreq(freq) else it }

        // Zeitbasis je Konstellation (GLONASS zählt ab dem Systemtag, BeiDou liegt
        // 14 s hinter GPS) -- siehe Pseudorange. Ohne Uhr gibt es keine Pseudorange,
        // dann trägt der Status den Grund in den Export.
        val pseudorange = if (clockUsable) {
            Pseudorange.meters(constellation, tRxNanos, receivedSvTimeNanos, biasNanos, leapSecond)
        } else {
            PseudorangeResult(null, PseudorangeStatus.NO_CLOCK)
        }

        val adrState = accumulatedDeltaRangeState
        val phaseValid = (adrState and GnssMeasurement.ADR_STATE_VALID) != 0

        val multipath = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            when (multipathIndicator) {
                GnssMeasurement.MULTIPATH_INDICATOR_DETECTED -> MultipathState.DETECTED
                GnssMeasurement.MULTIPATH_INDICATOR_NOT_DETECTED -> MultipathState.NOT_DETECTED
                else -> MultipathState.UNKNOWN
            }
        } else {
            MultipathState.UNKNOWN
        }

        // Seit API 34 als veraltet markiert, liefert aber auf allen
        // unterstuetzten Versionen weiterhin Werte.
        @Suppress("DEPRECATION")
        val phaseUncertainty =
            if (carrierPhaseUncertainty.isNaN()) null else carrierPhaseUncertainty

        // TOW: STATE_TOW_DECODED gibt es seit API 24, STATE_TOW_KNOWN erst ab
        // API 29. Auf aelteren Geraeten (z. B. Android 9) ist nur das erste Bit
        // aussagekraeftig.
        // GLONASS nutzt diese Bits nicht, sondern GLO_TOD_DECODED/GLO_TOD_KNOWN.
        val towDecoded = (state and GnssMeasurement.STATE_TOW_DECODED) != 0
        val towKnownFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (state and GnssMeasurement.STATE_TOW_KNOWN) != 0
        } else {
            false
        }
        val gloTodDecoded = (state and GnssMeasurement.STATE_GLO_TOD_DECODED) != 0
        val gloTodKnown = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            (state and GnssMeasurement.STATE_GLO_TOD_KNOWN) != 0
        } else {
            false
        }

        // Code-Lock: Galileo meldet ihn ueber ein eigenes Bit, nicht ueber
        // STATE_CODE_LOCK.
        val codeLocked = (state and GnssMeasurement.STATE_CODE_LOCK) != 0 ||
            (state and GnssMeasurement.STATE_GAL_E1BC_CODE_LOCK) != 0

        // Rohfelder für den CSV-Export: Sendezeit und ihre Unsicherheit.
        // GnssMeasurement.HAS_NO_RECEIVED_SV_TIME_NANOS = Long.MIN_VALUE.
        val tTxRawNanos = if (receivedSvTimeNanos != Long.MIN_VALUE) receivedSvTimeNanos else null
        val svTimeUncertaintyNanos = if (receivedSvTimeUncertaintyNanos > 0) receivedSvTimeUncertaintyNanos else null

        // Signalqualitaet, die nur ein Teil der Geraete liefert. Alle drei
        // Kenngroessen melden NaN, wenn sie fehlen -- daraus wird null.
        @Suppress("DEPRECATION") // Nachfolger verlangt einen Signaltyp je Messung;
        // aus dem Band laesst der sich nicht sicher ableiten, und fuer die
        // Stoerungserkennung genuegt der Wert des Empfaengers.
        val agc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            automaticGainControlLevelDb.takeIf { !it.isNaN() }
        } else {
            null
        }
        val snrIn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            snrInDb.takeIf { !it.isNaN() }
        } else {
            null
        }
        val basebandCn0 = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            basebandCn0DbHz.takeIf { !it.isNaN() }
        } else {
            null
        }
        // Signalcode (C/A, L1C, L5 …) gibt es erst ab API 29. Ohne Wache warf
        // der Aufruf auf Android 7–9 einen NoSuchMethodError — und der ist ein
        // Error, kein Exception: Er hätte die ganze Messung mitgerissen.
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            codeType.takeIf { it.isNotEmpty() }
        } else {
            null
        }

        val satInfo = geometry[svid to constellation]

        return RawMeasurement(
            svid = svid,
            constellation = constellation,
            carrierFrequencyHz = freq,
            bandLabel = gnssBandLabel(constellation, if (freq > 0f) freq else null),
            bandShort = gnssBandShort(constellation, if (freq > 0f) freq else null),
            cn0DbHz = cn0DbHz.toFloat(),
            pseudorangeMeters = pseudorange.meters,
            dopplerMetersPerSec = pseudorangeRateMetersPerSecond,
            carrierPhaseMeters = accumulatedDeltaRangeMeters,
            phaseValid = phaseValid,
            multipath = multipath,
            receivedAtMillis = System.currentTimeMillis(),
            // Qualitätsmerkmale der Messung.
            stateFlags = state,
            towKnown = towDecoded || towKnownFlag || gloTodDecoded || gloTodKnown,
            msecAmbiguous = (state and GnssMeasurement.STATE_MSEC_AMBIGUOUS) != 0,
            codeLock = codeLocked,
            // HAS_NO_CARRIER_PHASE_UNCERTAINTY ist Double.NaN.
            carrierPhaseUncertaintyMeters = phaseUncertainty,
            tTxRawNanos = tTxRawNanos,
            svTimeUncertaintyNanos = svTimeUncertaintyNanos,
            adrState = adrState,
            pseudorangeStatus = pseudorange.status,
            // Signalqualitaet -- null, wenn das Geraet sie nicht liefert.
            agcDb = agc,
            snrInDb = snrIn,
            basebandCn0DbHz = basebandCn0,
            codeType = code,
            // Lage aus dem Satellitenstatus; null, wenn der Satellit dort fehlt
            // oder die Werte unbrauchbar sind.
            elevationDegrees = satInfo?.elevationDegrees?.toDouble()
                ?.takeIf { it in -90.0..90.0 },
            azimuthDegrees = satInfo?.azimuthDegrees?.toDouble()
                ?.takeIf { it in 0.0..360.0 }
        )
    }

    private fun GnssClock.toSnapshot(): RawClockSnapshot = RawClockSnapshot(
        timeNanos = timeNanos,
        fullBiasNanos = fullBiasNanos,
        biasNanos = biasNanos,
        driftNsPerSec = driftNanosPerSecond,
        leapSecond = leapSecond,
        receivedAtMillis = System.currentTimeMillis(),
        // Ab API 29. Nicht geliefert heisst hier NaN bzw. Long.MIN_VALUE --
        // daraus wird null, nie eine 0, sonst saehe eine Luecke wie ein Messwert aus.
        biasUncertaintyNanos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            biasUncertaintyNanos.takeIf { !it.isNaN() }
        } else {
            null
        },
        driftUncertaintyNsPerSec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            driftUncertaintyNanosPerSecond.takeIf { !it.isNaN() }
        } else {
            null
        },
        elapsedRealtimeNanos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            elapsedRealtimeNanos.takeIf { it != Long.MIN_VALUE }
        } else {
            null
        },
        hardwareClockDiscontinuityCount = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            hardwareClockDiscontinuityCount.takeIf { it >= 0 }
        } else {
            null
        }
    )

    /** Grobe Konstellations-Zuordnung über die Trägerfrequenz (nur für API < 34). */
    private fun constellationForFreq(freqHz: Float): Constellation = when {
        freqHz in 1.598e9f..1.606e9f -> Constellation.GLONASS   // G1 1602 MHz
        freqHz in 1.560e9f..1.562e9f -> Constellation.BEIDOU     // B1 1561.098 MHz
        freqHz in 1.226e9f..1.229e9f -> Constellation.GPS        // L2 1227.60 MHz
        freqHz in 1.206e9f..1.208e9f -> Constellation.GALILEO    // E5b 1207.14 MHz
        else -> Constellation.UNKNOWN
    }
}

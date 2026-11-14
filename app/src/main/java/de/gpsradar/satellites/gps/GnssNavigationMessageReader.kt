package de.gpsradar.satellites.gps

import android.annotation.SuppressLint
import android.content.Context
import android.location.GnssNavigationMessage
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.util.Log
import de.gpsradar.satellites.R
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.GnssNavMessage
import java.util.Locale
import java.util.concurrent.Executor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "GnssNavMessages"
private const val NAV_REGISTER_ATTEMPTS = 5
private const val NAV_REGISTER_RETRY_MS = 2000L
private const val MAX_STORED_MESSAGES = 20

/** Abstand der Übersichtszeile im Protokoll (siehe [GnssNavigationMessageReader.logSummary]). */
private const val SUMMARY_INTERVAL_MS = 60_000L

/**
 * Liest die Navigationsnachrichten (Ephemeriden-Bits) des Empfängers für die
 * Anzeige im Tab „Signale".
 *
 * Eigene Datei, weil die Registrierung ihre eigenen Fallstricke hat: Der
 * Aufruf kann kurz nach dem Start `false` liefern, obwohl das Gerät es kann –
 * dann wird mehrfach nachgesetzt statt endgültig aufzugeben.
 */
class GnssNavigationMessageReader(
    private val context: Context,
    private val locationManager: LocationManager?,
    private val mainHandler: Handler,
    private val isSimulated: () -> Boolean
) {

    private val _messages = MutableStateFlow<List<GnssNavMessage>>(emptyList())
    val messages: StateFlow<List<GnssNavMessage>> = _messages.asStateFlow()

    private val _supports = MutableStateFlow(false)
    val supports: StateFlow<Boolean> = _supports.asStateFlow()

    /** Sammelt vollständige Ephemeriden (GPS und GLONASS) für die Navigationsdatei. */
    private val ephemerisStore = NavEphemerisStore()
    private val _ephemerides = MutableStateFlow(NavEphemerides())
    val ephemerides: StateFlow<NavEphemerides> = _ephemerides.asStateFlow()

    private var registered = false
    private var attempts = 0
    private val retry = Runnable { registerIfPossible() }

    // Ab Android 11 verlangt die Registrierung einen Executor; wie bisher der
    // Haupt-Thread.
    private val mainExecutor = Executor { mainHandler.post(it) }

    private val callback = object : GnssNavigationMessage.Callback() {
        override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
            if (isSimulated()) return
            _messages.update { current ->
                (current + navMessageFrom(event)).takeLast(MAX_STORED_MESSAGES)
            }
            if (ephemerisStore.add(event)) {
                _ephemerides.value = ephemerisStore.snapshot()
            }
            countForSummary(event)
        }

        override fun onStatusChanged(status: Int) {
            // Für die Anzeige nicht relevant.
        }
    }

    @SuppressLint("MissingPermission")
    fun register() {
        val lm = locationManager
        if (lm == null) {
            _supports.value = false
            return
        }
        if (!registered) {
            attempts = 0
            registerIfPossible()
        }

        // Ab Android 12 sagt die Plattform die Fähigkeit direkt; der Rückgabewert
        // der Registrierung ist kurz nach dem Start unzuverlässig.
        _supports.value = capable(lm) ?: registered
    }

    fun unregister() {
        mainHandler.removeCallbacks(retry)
        mainHandler.removeCallbacks(summaryTick)
        summaryRunning = false
        attempts = 0
        if (registered) {
            try {
                locationManager?.unregisterGnssNavigationMessageCallback(callback)
            } catch (e: Exception) {
                // ignore
            }
            registered = false
        }
        _messages.value = emptyList()
    }

    private fun capable(lm: LocationManager): Boolean? =
        // Ab Android 12 für normale Apps abrufbar; davor verlangt der Aufruf
        // LOCATION_HARDWARE und wirft eine SecurityException.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                lm.gnssCapabilities.hasNavigationMessages()
            } catch (e: Exception) {
                Log.d(TAG, "gnssCapabilities nicht lesbar", e)
                null
            }
        } else {
            null
        }

    @SuppressLint("MissingPermission")
    private fun registerIfPossible() {
        if (registered) return
        val lm = locationManager ?: return
        val ok = try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                lm.registerGnssNavigationMessageCallback(mainExecutor, callback)
            } else {
                @Suppress("DEPRECATION")
                lm.registerGnssNavigationMessageCallback(callback)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Navigationsnachrichten nicht registriert", e)
            false
        }
        registered = ok
        if (ok) {
            attempts = 0
        } else if (attempts < NAV_REGISTER_ATTEMPTS) {
            attempts++
            Log.w(TAG, "Navigationsnachrichten noch nicht möglich (Versuch $attempts)")
            mainHandler.postDelayed(retry, NAV_REGISTER_RETRY_MS)
        }
    }

    // ---- Übersicht für Protokoll und Diagnose ----

    private val summarySvids = mutableMapOf<Int, MutableSet<Int>>()
    private val summarySub = mutableMapOf<Int, MutableSet<Int>>()
    private val summaryCount = mutableMapOf<Int, Int>()
    private var summaryRunning = false
    // Eigener Typ nötig: Der Runnable nimmt sich selbst wieder in den Handler.
    private val summaryTick: Runnable = Runnable {
        logSummary()
        if (summaryRunning) mainHandler.postDelayed(summaryTick, SUMMARY_INTERVAL_MS)
    }

    private fun countForSummary(event: GnssNavigationMessage) {
        summarySvids.getOrPut(event.type) { mutableSetOf() }.add(event.svid)
        summarySub.getOrPut(event.type) { mutableSetOf() }.add(event.submessageId)
        summaryCount[event.type] = (summaryCount[event.type] ?: 0) + 1
        if (!summaryRunning) {
            summaryRunning = true
            mainHandler.postDelayed(summaryTick, SUMMARY_INTERVAL_MS)
        }
    }

    /**
     * Was dieses Gerät an Navigationsnachrichten liefert – eine Zeile je Minute.
     *
     * Für die Anzeige genügt die Liste der letzten 20 Meldungen. Für die Frage
     * „kann man daraus eine vollständige Ephemeride bauen?“ nicht: Ein
     * GPS-Satellit braucht die Unterrahmen 1–5, Galileo die Wörter 1–10, GLONASS
     * die Strings 1–4. Diese Übersicht zählt deshalb mit, welche Unterrahmen je
     * System und Satellit tatsächlich ankommen.
     */
    private fun logSummary() {
        if (summaryCount.isEmpty()) {
            Log.i(TAG, "Navigationsnachrichten: keine empfangen")
            return
        }
        val parts = summaryCount.keys.sorted().map { type ->
            val svids = summarySvids[type].orEmpty().size
            val sub = summarySub[type].orEmpty().sorted().joinToString(",")
            "${navTypeLabel(type)}: $svids Sats, Unterrahmen [$sub], ${summaryCount[type]} Meldungen"
        }
        // Anzahl der fertig zusammengesetzten Ephemeriden: Meldungen allein sagen
        // nichts darüber, ob daraus eine brauchbare Navigationsdatei wird.
        val fertig = ephemerisStore.snapshot()
        Log.i(
            TAG,
            "Navigationsnachrichten: " + parts.joinToString(" · ") +
                " · Ephemeriden fertig: GPS ${fertig.gps.size}, GLONASS ${fertig.glo.size}"
        )
    }

    private fun navMessageFrom(event: GnssNavigationMessage): GnssNavMessage {
        val type = event.type
        val constellation = constellationForNavType(type)
        val raw = event.data ?: ByteArray(0)
        val hex = raw.joinToString(" ") { String.format(Locale.US, "%02X", it.toInt() and 0xFF) }
        return GnssNavMessage(
            svid = event.svid,
            constellation = constellation,
            typeLabel = navTypeLabel(type),
            statusLabel = navStatusLabel(event.status),
            messageId = event.messageId,
            submessageId = event.submessageId,
            dataHex = hex,
            receivedAtMillis = System.currentTimeMillis()
        )
    }

    private fun constellationForNavType(type: Int): Constellation = when (type) {
        GnssNavigationMessage.TYPE_GPS_L1CA,
        GnssNavigationMessage.TYPE_GPS_L2CNAV,
        GnssNavigationMessage.TYPE_GPS_L5CNAV,
        GnssNavigationMessage.TYPE_GPS_CNAV2 -> Constellation.GPS
        GnssNavigationMessage.TYPE_GLO_L1CA -> Constellation.GLONASS
        GnssNavigationMessage.TYPE_BDS_D1,
        GnssNavigationMessage.TYPE_BDS_D2,
        GnssNavigationMessage.TYPE_BDS_CNAV1,
        GnssNavigationMessage.TYPE_BDS_CNAV2 -> Constellation.BEIDOU
        GnssNavigationMessage.TYPE_GAL_I,
        GnssNavigationMessage.TYPE_GAL_F -> Constellation.GALILEO
        GnssNavigationMessage.TYPE_QZS_L1CA -> Constellation.QZSS
        else -> Constellation.UNKNOWN
    }

    private fun navTypeLabel(type: Int): String = when (type) {
        GnssNavigationMessage.TYPE_GPS_L1CA -> "GPS L1 C/A"
        GnssNavigationMessage.TYPE_GPS_L2CNAV -> "GPS L2C"
        GnssNavigationMessage.TYPE_GPS_L5CNAV -> "GPS L5"
        GnssNavigationMessage.TYPE_GPS_CNAV2 -> "GPS CNAV-2"
        GnssNavigationMessage.TYPE_GLO_L1CA -> "GLONASS L1 C/A"
        GnssNavigationMessage.TYPE_BDS_D1 -> "BeiDou D1"
        GnssNavigationMessage.TYPE_BDS_D2 -> "BeiDou D2"
        GnssNavigationMessage.TYPE_BDS_CNAV1 -> "BeiDou CNAV-1"
        GnssNavigationMessage.TYPE_BDS_CNAV2 -> "BeiDou CNAV-2"
        GnssNavigationMessage.TYPE_GAL_I -> "Galileo I/NAV"
        GnssNavigationMessage.TYPE_GAL_F -> "Galileo F/NAV"
        GnssNavigationMessage.TYPE_QZS_L1CA -> "QZSS L1 C/A"
        GnssNavigationMessage.TYPE_IRN_L1 -> "IRNSS L1"
        GnssNavigationMessage.TYPE_IRN_L5 -> "IRNSS L5"
        GnssNavigationMessage.TYPE_IRN_L5CA -> "IRNSS L5 C/A"
        else -> "Typ 0x${Integer.toHexString(type)}"
    }

    private fun navStatusLabel(status: Int): String = when (status) {
        GnssNavigationMessage.STATUS_PARITY_PASSED -> context.getString(R.string.nav_status_parity_ok)
        GnssNavigationMessage.STATUS_PARITY_REBUILT -> context.getString(R.string.nav_status_parity_rebuilt)
        else -> "Status 0x${Integer.toHexString(status)}"
    }
}

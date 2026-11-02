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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val TAG = "GnssNavMessages"
private const val NAV_REGISTER_ATTEMPTS = 5
private const val NAV_REGISTER_RETRY_MS = 2000L
private const val MAX_STORED_MESSAGES = 20

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

    private var registered = false
    private var attempts = 0
    private val retry = Runnable { registerIfPossible() }

    private val callback = object : GnssNavigationMessage.Callback() {
        override fun onGnssNavigationMessageReceived(event: GnssNavigationMessage) {
            if (isSimulated()) return
            _messages.update { current ->
                (current + navMessageFrom(event)).takeLast(MAX_STORED_MESSAGES)
            }
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
            lm.registerGnssNavigationMessageCallback(callback)
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

package de.gpsradar.satellites.gps

import android.location.GnssNavigationMessage
import de.gpsradar.satellites.model.GpsClock
import de.gpsradar.satellites.model.GpsEphemeris
import de.gpsradar.satellites.model.GpsLnav
import de.gpsradar.satellites.model.GpsOrbitA
import de.gpsradar.satellites.model.GpsOrbitB

/**
 * Sammelt die Unterrahmen je Satellit und gibt einen Datensatz erst frei, wenn
 * Uhr (1) und beide Bahnteile (2 und 3) vorliegen und zusammenpassen.
 *
 * Ein Satellit sendet die drei Unterrahmen innerhalb von 30 Sekunden, verteilt
 * über fünf Rahmen. Einzelne Meldungen gehen verloren, deshalb wird je
 * Unterrahmen immer die jüngste behalten statt alles zu verwerfen.
 *
 * Die Prüfung „IODE in Unterrahmen 2 und 3 gleich" stammt aus RTKLIB
 * (`decode_frame_eph`): Sie verhindert, dass zwei zeitlich auseinanderliegende
 * Datensätze zu einem falschen Satz zusammengesetzt werden.
 */
class NavEphemerisStore {

    private class Teile {
        var clock: GpsClock? = null
        var clockAtSeconds: Int = 0
        var orbitA: GpsOrbitA? = null
        var orbitB: GpsOrbitB? = null
    }

    private val teile = mutableMapOf<Int, Teile>()

    /**
     * Eine Meldung aufnehmen. Liefert den aktuellen, vollständigen Datensatz des
     * Satelliten — auch wenn er schon vorher vollständig war (der Aufrufer legt
     * ihn nach PRN ab). null, solange etwas fehlt oder die Daten unbrauchbar sind.
     */
    fun add(message: GnssNavigationMessage): GpsEphemeris? {
        if (message.type != GnssNavigationMessage.TYPE_GPS_L1CA) return null
        val data = message.data ?: return null
        val bits = GpsLnav.dataBits(data) ?: return null
        val seconds = GpsLnav.secondsOfWeek(bits).toInt()

        val t = teile.getOrPut(message.svid) { Teile() }
        when (GpsLnav.subframeNumber(bits)) {
            1 -> {
                t.clock = GpsLnav.clock(bits) ?: return null
                t.clockAtSeconds = seconds
            }
            2 -> t.orbitA = GpsLnav.orbitA(bits) ?: return null
            3 -> t.orbitB = GpsLnav.orbitB(bits) ?: return null
            else -> return null // Unterrahmen 4/5 tragen keine Ephemeriden
        }

        val clock = t.clock ?: return null
        val orbitA = t.orbitA ?: return null
        val orbitB = t.orbitB ?: return null
        return GpsEphemeris.of(message.svid, clock, orbitA, orbitB, t.clockAtSeconds)
    }

    fun clear() = teile.clear()
}

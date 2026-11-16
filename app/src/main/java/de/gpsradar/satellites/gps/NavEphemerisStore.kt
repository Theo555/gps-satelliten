package de.gpsradar.satellites.gps

import android.location.GnssNavigationMessage
import de.gpsradar.satellites.model.BdsClock
import de.gpsradar.satellites.model.BdsEphemeris
import de.gpsradar.satellites.model.BdsLnav
import de.gpsradar.satellites.model.BdsOrbitA
import de.gpsradar.satellites.model.BdsOrbitB
import de.gpsradar.satellites.model.GloAxis
import de.gpsradar.satellites.model.GloClock
import de.gpsradar.satellites.model.GloEphemeris
import de.gpsradar.satellites.model.GloLnav
import de.gpsradar.satellites.model.GpsClock
import de.gpsradar.satellites.model.GpsEphemeris
import de.gpsradar.satellites.model.GpsLnav
import de.gpsradar.satellites.model.GpsOrbitA
import de.gpsradar.satellites.model.GpsOrbitB

/** Was der Empfänger bisher an vollständigen Ephemeriden geliefert hat. */
data class NavEphemerides(
    val gps: List<GpsEphemeris> = emptyList(),
    val glo: List<GloEphemeris> = emptyList(),
    val bds: List<BdsEphemeris> = emptyList()
) {
    val total: Int get() = gps.size + glo.size + bds.size
}

/**
 * Sammelt die Navigationsmeldungen je Satellit und gibt einen Datensatz erst
 * frei, wenn er vollständig ist und die Teile zusammenpassen.
 *
 * **GPS:** Ein Satellit sendet die Unterrahmen 1–3 innerhalb von 30 Sekunden,
 * verteilt über fünf Rahmen. Einzelne Meldungen gehen verloren, deshalb wird je
 * Unterrahmen immer die jüngste behalten statt alles zu verwerfen. Die Prüfung
 * „IODE in Unterrahmen 2 und 3 gleich" stammt aus RTKLIB (`decode_frame_eph`):
 * Sie verhindert, dass zwei zeitlich auseinanderliegende Datensätze zu einem
 * falschen Satz zusammengesetzt werden.
 *
 * **GLONASS:** Die Ephemeride steckt in den Strings 1–4 (Ort, Geschwindigkeit
 * und Beschleunigung achsenweise, dazu Uhr und Kennung). Einen IODE wie bei GPS
 * gibt es nicht; zusammengehalten werden die Teile stattdessen durch zwei
 * Prüfungen:
 *
 * - `tb` aus String 2 muss zur Rahmenzeit aus String 1 passen. Die Rahmenzeit
 *   wandert mit jedem 30-Sekunden-Rahmen weiter, `tb` bleibt für einen ganzen
 *   Ephemeridensatz stehen (GLONASS lädt alle 30 Minuten hoch). Weichen beide
 *   um mehr als einen Satzabstand voneinander ab, gehören die Teile zu
 *   verschiedenen Generationen und werden verworfen.
 * - Alle vier Strings müssen innerhalb der letzten zwei Rahmen angekommen sein,
 *   sonst ist der Satz zu alt.
 *
 * Zusätzlich muss sich aus den Achsen eine GLONASS-Bahn ergeben
 * ([GloEphemeris.hasPlausibleOrbit]) — das fängt grobe Dekodierfehler ab.
 */
class NavEphemerisStore {

    private class GpsTeile {
        var clock: GpsClock? = null
        var clockAtSeconds: Int = 0
        var orbitA: GpsOrbitA? = null
        var orbitB: GpsOrbitB? = null
    }

    private class GloTeile {
        var frameSeconds: Int? = null
        var frameAtMillis: Long = 0L
        var health: Int? = null
        var tbIndex: Int? = null
        var tbAtMillis: Long = 0L
        var gamma: Double? = null
        var clock: GloClock? = null
        var clockAtMillis: Long = 0L

        /** Achsen 1 (X), 2 (Y), 3 (Z) mit ihrem Empfangszeitpunkt. */
        val axes = mutableMapOf<Int, GloAxis>()
        val axesAtMillis = mutableMapOf<Int, Long>()
    }

    private val gpsTeile = mutableMapOf<Int, GpsTeile>()
    private val gpsEphemerides = mutableMapOf<Int, GpsEphemeris>()

    private val gloTeile = mutableMapOf<Int, GloTeile>()
    private val gloEphemerides = mutableMapOf<Int, GloEphemeris>()

    private class BdsTeile {
        var clock: BdsClock? = null
        var clockAtMillis: Long = 0L
        var transmissionSeconds: Int = 0
        var orbitA: BdsOrbitA? = null
        var orbitAToMillis: Long = 0L
        var orbitB: BdsOrbitB? = null
        var orbitBAtMillis: Long = 0L
    }

    private val bdsTeile = mutableMapOf<Int, BdsTeile>()
    private val bdsEphemerides = mutableMapOf<Int, BdsEphemeris>()

    /**
     * Eine Meldung aufnehmen. Liefert true, wenn sich dadurch ein vollständiger
     * Datensatz geändert hat — der Aufrufer veröffentlicht dann den neuen Stand.
     */
    fun add(message: GnssNavigationMessage, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val data = message.data ?: return false
        return when (message.type) {
            GnssNavigationMessage.TYPE_GPS_L1CA -> addGps(message.svid, data)
            GnssNavigationMessage.TYPE_GLO_L1CA -> addGlo(message.svid, data, nowMillis)
            GnssNavigationMessage.TYPE_BDS_D1 -> addBds(message.svid, data, nowMillis)
            else -> false
        }
    }

    /** Derzeit vollständige Sätze, nach Satellitennummer sortiert. */
    fun snapshot(): NavEphemerides = NavEphemerides(
        gps = gpsEphemerides.values.sortedBy { it.prn },
        glo = gloEphemerides.values.sortedBy { it.satelliteSlot },
        bds = bdsEphemerides.values.sortedBy { it.prn }
    )

    fun clear() {
        gpsTeile.clear()
        gpsEphemerides.clear()
        gloTeile.clear()
        gloEphemerides.clear()
        bdsTeile.clear()
        bdsEphemerides.clear()
    }

    // ---- GPS ----

    private fun addGps(svid: Int, data: ByteArray): Boolean {
        val bits = GpsLnav.dataBits(data) ?: return false
        val seconds = GpsLnav.secondsOfWeek(bits).toInt()

        val t = gpsTeile.getOrPut(svid) { GpsTeile() }
        when (GpsLnav.subframeNumber(bits)) {
            1 -> {
                t.clock = GpsLnav.clock(bits) ?: return false
                t.clockAtSeconds = seconds
            }
            2 -> t.orbitA = GpsLnav.orbitA(bits) ?: return false
            3 -> t.orbitB = GpsLnav.orbitB(bits) ?: return false
            else -> return false // Unterrahmen 4/5 tragen keine Ephemeriden
        }

        val clock = t.clock ?: return false
        val orbitA = t.orbitA ?: return false
        val orbitB = t.orbitB ?: return false
        val eph = GpsEphemeris.of(svid, clock, orbitA, orbitB, t.clockAtSeconds) ?: return false
        val changed = gpsEphemerides[svid] != eph
        gpsEphemerides[svid] = eph
        return changed
    }

    // ---- GLONASS ----

    private fun addGlo(svid: Int, data: ByteArray, nowMillis: Long): Boolean {
        val bits = GloLnav.dataBits(data) ?: return false
        val t = gloTeile.getOrPut(svid) { GloTeile() }
        when (GloLnav.stringId(bits)) {
            1 -> {
                t.frameSeconds = GloLnav.frameSeconds(bits)
                t.frameAtMillis = nowMillis
                storeAxis(t, 1, bits, nowMillis)
            }
            2 -> {
                t.health = GloLnav.health(bits)
                t.tbIndex = GloLnav.tbIndex(bits)
                t.tbAtMillis = nowMillis
                storeAxis(t, 2, bits, nowMillis)
            }
            3 -> {
                t.gamma = GloLnav.gamma(bits)
                storeAxis(t, 3, bits, nowMillis)
            }
            4 -> {
                val clock = GloLnav.clock(bits)
                // Der Satz nennt seine eigene Slotnummer. Passt sie nicht zur
                // Meldung, ist die Zuordnung des Geräts falsch — dann lieber
                // alles wegwerfen, als eine fremde Bahn unter diesem Namen zu
                // führen.
                if (clock.satelliteSlot != svid) {
                    gloTeile.remove(svid)
                    gloEphemerides.remove(svid)
                    return false
                }
                t.clock = clock
                t.clockAtMillis = nowMillis
            }
            else -> return false // Strings 5–15: UTC-Parameter und Almanach
        }

        val eph = complete(svid, t, nowMillis) ?: return false
        val changed = gloEphemerides[svid] != eph
        gloEphemerides[svid] = eph
        return changed
    }

    private fun storeAxis(t: GloTeile, axis: Int, bits: String, nowMillis: Long) {
        t.axes[axis] = GloLnav.axis(bits)
        t.axesAtMillis[axis] = nowMillis
    }

    private fun complete(svid: Int, t: GloTeile, nowMillis: Long): GloEphemeris? {
        val frameSeconds = t.frameSeconds ?: return null
        val health = t.health ?: return null
        val tbIndex = t.tbIndex ?: return null
        val gamma = t.gamma ?: return null
        val clock = t.clock ?: return null
        val x = t.axes[1] ?: return null
        val y = t.axes[2] ?: return null
        val z = t.axes[3] ?: return null

        val frischAb = nowMillis - GLO_PART_MAX_AGE_MILLIS
        if (t.frameAtMillis < frischAb || t.tbAtMillis < frischAb ||
            t.clockAtMillis < frischAb ||
            (1..3).any { (t.axesAtMillis[it] ?: 0L) < frischAb }
        ) {
            return null
        }

        // Rahmenzeit und tb müssen zur selben Generation gehören: tb liegt vor
        // der Rahmenzeit, höchstens einen Satzabstand (plus Reserve) entfernt.
        val tbMoscowSeconds = (tbIndex - 1) * GloLnav.TB_STEP_SECONDS
        val abstand = Math.floorMod(frameSeconds - tbMoscowSeconds, GloLnav.SECONDS_PER_DAY)
        if (abstand > GLO_FRAME_AFTER_TB_SECONDS &&
            abstand < GloLnav.SECONDS_PER_DAY - GLO_FRAME_BEFORE_TB_SECONDS
        ) {
            return null
        }

        val eph = GloEphemeris(
            satelliteSlot = svid,
            tbMillis = GloLnav.epochMillis(GloLnav.tbSecondsUtc(tbIndex), nowMillis),
            frameSeconds = frameSeconds,
            tauSeconds = clock.tauSeconds,
            gamma = gamma,
            health = health,
            ageOfDataDays = clock.ageOfDataDays,
            frequencyChannel = null,
            x = x,
            y = y,
            z = z
        )
        return if (eph.hasPlausibleOrbit) eph else null
    }

    // ---- BeiDou ----

    /**
     * Die Unterrahmen 1–3 sammeln und zu einem Satz verbinden.
     *
     * Zwei Prüfungen halten fremde Teile draußen:
     *
     * - **Frische:** alle drei Teile innerhalb von [BDS_PART_MAX_AGE_MILLIS].
     * - **`toc == toe`** (in [BdsEphemeris.of]). Das ist die eigentliche
     *   Schutzregel: Der Empfänger liefert „Subframe 1“-Meldungen, deren
     *   Nutzteil ab etwa Bit 90 ein Füllmuster enthält. Ohne die Prüfung
     *   landete ein Füllmuster als Uhrversatz in der Datei — und zwar bei allen
     *   Satelliten derselbe Wert.
     */
    private fun addBds(svid: Int, data: ByteArray, nowMillis: Long): Boolean {
        val bits = BdsLnav.dataBits(data) ?: return false
        val t = bdsTeile.getOrPut(svid) { BdsTeile() }
        when (BdsLnav.frameId(bits)) {
            1 -> {
                t.clock = BdsLnav.clock(bits) ?: return false
                t.transmissionSeconds = BdsLnav.secondsOfWeek(bits).toInt()
                t.clockAtMillis = nowMillis
            }
            2 -> {
                t.orbitA = BdsLnav.orbitA(bits) ?: return false
                t.orbitAToMillis = nowMillis
            }
            3 -> {
                t.orbitB = BdsLnav.orbitB(bits) ?: return false
                t.orbitBAtMillis = nowMillis
            }
            else -> return false // Unterrahmen 4/5: Almanach, Ionosphäre, UTC
        }

        val clock = t.clock ?: return false
        val orbitA = t.orbitA ?: return false
        val orbitB = t.orbitB ?: return false
        val frischAb = nowMillis - BDS_PART_MAX_AGE_MILLIS
        if (t.clockAtMillis < frischAb || t.orbitAToMillis < frischAb ||
            t.orbitBAtMillis < frischAb
        ) {
            return false
        }

        // null heißt hier: Uhr und Bahn gehören nicht zusammen (Füllmuster oder
        // Generationswechsel). Der zuletzt gültige Satz bleibt dann stehen.
        val eph = BdsEphemeris.of(
            svid, clock, orbitA, orbitB, t.transmissionSeconds
        ) ?: return false
        val changed = bdsEphemerides[svid] != eph
        bdsEphemerides[svid] = eph
        return changed
    }

    private companion object {
        /**
         * Ein vollständiger Durchlauf über die Strings 1–4 dauert 8 Sekunden,
         * wiederholt wird er alle 30. Nach 70 Sekunden fehlt mindestens eine
         * Generation — dann lieber neu sammeln.
         */
        const val GLO_PART_MAX_AGE_MILLIS = 70_000L

        /**
         * BeiDou wiederholt einen Rahmen alle 30 Sekunden. Nach 60 Sekunden ist
         * jeder Teil schon zweimal dagewesen — was dann noch fehlt, kommt so
         * schnell nicht nach.
         */
        const val BDS_PART_MAX_AGE_MILLIS = 60_000L
        /** Rahmenzeit darf bis zu 35 Minuten nach tb liegen (Satzabstand 30 min). */
        const val GLO_FRAME_AFTER_TB_SECONDS = 2_100

        /** Und höchstens 5 Minuten davor, falls ein Satellit früher umschaltet. */
        const val GLO_FRAME_BEFORE_TB_SECONDS = 300
    }
}

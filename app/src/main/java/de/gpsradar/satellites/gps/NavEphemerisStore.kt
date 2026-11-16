package de.gpsradar.satellites.gps

import android.location.GnssNavigationMessage
import de.gpsradar.satellites.model.Almanac
import de.gpsradar.satellites.model.BdsClock
import de.gpsradar.satellites.model.BdsEphemeris
import de.gpsradar.satellites.model.BdsLnav
import de.gpsradar.satellites.model.BdsOrbitA
import de.gpsradar.satellites.model.BdsOrbitB
import de.gpsradar.satellites.model.GloAxis
import de.gpsradar.satellites.model.GloClock
import de.gpsradar.satellites.model.GloEphemeris
import de.gpsradar.satellites.model.GloLnav
import de.gpsradar.satellites.model.GalClock
import de.gpsradar.satellites.model.GalEphemeris
import de.gpsradar.satellites.model.GalHealth
import de.gpsradar.satellites.model.GalIonosphere
import de.gpsradar.satellites.model.GalLnav
import de.gpsradar.satellites.model.GalOrbitA
import de.gpsradar.satellites.model.GalOrbitB
import de.gpsradar.satellites.model.GalOrbitC
import de.gpsradar.satellites.model.GpsClock
import de.gpsradar.satellites.model.GpsEphemeris
import de.gpsradar.satellites.model.GpsLnav
import de.gpsradar.satellites.model.GpsOrbitA
import de.gpsradar.satellites.model.GpsOrbitB
import de.gpsradar.satellites.model.KlobucharIono
import de.gpsradar.satellites.model.NavCorrections
import de.gpsradar.satellites.model.UtcCorrection

/** Was der Empfänger bisher an vollständigen Ephemeriden geliefert hat. */
data class NavEphemerides(
    val gps: List<GpsEphemeris> = emptyList(),
    val glo: List<GloEphemeris> = emptyList(),
    val bds: List<BdsEphemeris> = emptyList(),
    val gal: List<GalEphemeris> = emptyList(),
    val corrections: NavCorrections = NavCorrections(),
    /**
     * Almanach-Sätze. Sie gehören nicht in die RINEX-Navigationsdatei — das
     * Format kennt keinen Almanach — und werden deshalb als eigene Datei
     * geschrieben.
     */
    val almanac: List<Almanac> = emptyList()
) {
    val total: Int get() = gps.size + glo.size + bds.size + gal.size
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

    /** Almanach je System und Satellit; der jüngste Satz gewinnt. */
    private val almanac = mutableMapOf<String, MutableMap<Int, Almanac>>()

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
        /**
         * Koeffizienten aus demselben Unterrahmen 1 — aber erst übernehmen,
         * wenn der Satz als Ephemeride taugt (siehe [addBds]).
         */
        var iono: KlobucharIono? = null
    }

    private val bdsTeile = mutableMapOf<Int, BdsTeile>()
    private val bdsEphemerides = mutableMapOf<Int, BdsEphemeris>()

    private class GalTeile {
        var orbitA: GalOrbitA? = null
        var orbitAAtMillis: Long = 0L
        var orbitB: GalOrbitB? = null
        var orbitBAtMillis: Long = 0L
        var orbitC: GalOrbitC? = null
        var orbitCAtMillis: Long = 0L
        var clock: GalClock? = null
        var clockAtMillis: Long = 0L
        var health: GalHealth? = null
        var healthAtMillis: Long = 0L
    }

    private val galTeile = mutableMapOf<Int, GalTeile>()
    private val galEphemerides = mutableMapOf<Int, GalEphemeris>()

    /**
     * Systemweite Korrekturen. Sie gelten nicht je Satellit, sondern für das
     * ganze System — deshalb ein einzelner Platz je Art und nicht eine Liste.
     */
    private var gpsIono: KlobucharIono? = null
    private var gpsUtc: UtcCorrection? = null
    private var gloUtc: UtcCorrection? = null
    private var bdsIono: KlobucharIono? = null
    private var bdsUtc: UtcCorrection? = null
    private var galIono: GalIonosphere? = null
    private var galUtc: UtcCorrection? = null

    /**
     * Eine Meldung aufnehmen. Liefert true, wenn sich dadurch ein vollständiger
     * Datensatz geändert hat — der Aufrufer veröffentlicht dann den neuen Stand.
     */
    fun add(message: GnssNavigationMessage, nowMillis: Long = System.currentTimeMillis()): Boolean {
        val data = message.data ?: return false
        return when (message.type) {
            GnssNavigationMessage.TYPE_GPS_L1CA -> addGps(message.svid, data, nowMillis)
            GnssNavigationMessage.TYPE_GLO_L1CA -> addGlo(message.svid, data, nowMillis)
            GnssNavigationMessage.TYPE_BDS_D1 -> addBds(message.svid, data, nowMillis)
            GnssNavigationMessage.TYPE_GAL_I ->
                addGal(message.svid, message.submessageId, data, nowMillis)
            else -> false
        }
    }

    /** Derzeit vollständige Sätze, nach Satellitennummer sortiert. */
    fun snapshot(): NavEphemerides = NavEphemerides(
        gps = gpsEphemerides.values.sortedBy { it.prn },
        glo = gloEphemerides.values.sortedBy { it.satelliteSlot },
        bds = bdsEphemerides.values.sortedBy { it.prn },
        gal = galEphemerides.values.sortedBy { it.prn },
        corrections = NavCorrections(
            gpsIono = gpsIono,
            gpsUtc = gpsUtc,
            gloUtc = gloUtc,
            galIono = galIono,
            galUtc = galUtc,
            bdsIono = bdsIono,
            bdsUtc = bdsUtc
        ),
        almanac = almanac.values
            .flatMap { it.values }
            .sortedWith(compareBy({ it.system }, { it.svid }))
    )

    fun clear() {
        gpsTeile.clear()
        gpsEphemerides.clear()
        gloTeile.clear()
        gloEphemerides.clear()
        bdsTeile.clear()
        bdsEphemerides.clear()
        galTeile.clear()
        galEphemerides.clear()
        almanac.clear()
        gpsIono = null
        gpsUtc = null
        gloUtc = null
        bdsIono = null
        bdsUtc = null
        galIono = null
        galUtc = null
    }

    // ---- GPS ----

    /**
     * Unterrahmen 4, Seite 18: Ionosphäre und UTC. Andere Seiten liefern hier
     * nichts — der Almanach wird in [gpsExtra] behandelt.
     */
    private fun gpsCorrections(bits: String): Boolean {
        val iono = GpsLnav.ionosphere(bits) ?: return false
        val utc = GpsLnav.utc(bits)
        val changed = iono != gpsIono || (utc != null && utc != gpsUtc)
        if (iono.isUsable) gpsIono = iono
        if (utc != null && utc.isUsable) gpsUtc = utc
        return changed
    }

    /**
     * Unterrahmen 4/5: entweder Ionosphäre und UTC (Seite 18) oder der Almanach
     * eines Satelliten (Unterrahmen 5 Seiten 1–24, Unterrahmen 4 Seiten 2–5 und
     * 7–10). Beides schließt sich aus — die Seitenprüfung entscheidet.
     */
    private fun gpsExtra(bits: String, nowMillis: Long): Boolean {
        if (gpsCorrections(bits)) return true
        val alm = GpsLnav.almanac(bits) ?: return false
        if (!alm.isPlausible) return false
        val week = gpsWeekOf(nowMillis, alm.referenceSeconds)
        val satz = alm.copy(week = week)
        val fach = almanac.getOrPut(satz.system) { mutableMapOf() }
        val changed = fach[satz.svid] != satz
        fach[satz.svid] = satz
        return changed
    }

    /**
     * GPS-Woche, in die ein Almanach-Bezug fällt.
     *
     * Der Almanach trägt `toa` nur als Sekunden seit Wochenbeginn; die Woche
     * steht auf einer anderen Seite. Deshalb aus der Empfangszeit ableiten: Ist
     * der Bezug mehr als eine halbe Woche „in der Zukunft“, gehört er zur
     * Vorwoche. Almanachsätze sind bis zu sechs Tage alt, deshalb genügt dieser
     * eine Schritt.
     */
    private fun gpsWeekOf(nowMillis: Long, referenceSeconds: Int): Int {
        val secondsSinceEpoch = (nowMillis - GPS_EPOCH_MILLIS) / 1000L
        val week = (secondsSinceEpoch / 604_800L).toInt()
        val sow = (secondsSinceEpoch % 604_800L).toInt()
        return if (referenceSeconds - sow > 302_400) week - 1 else week
    }

    private fun addGps(svid: Int, data: ByteArray, nowMillis: Long): Boolean {
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
            // Unterrahmen 4/5 tragen keine Ephemeride, aber auf Seite 18
            // Ionosphäre und UTC-Parameter und auf den übrigen Seiten den
            // Almanach.
            else -> return gpsExtra(bits, nowMillis)
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
            else -> {
                // Strings 5–15: String 5 trägt den Zeitbezug GLONASS→UTC(SU).
                // Der Almanach der übrigen Strings wird nicht ausgewertet.
                val utc = GloLnav.utc(bits) ?: return false
                if (!utc.isUsable || utc == gloUtc) return false
                gloUtc = utc
                return true
            }
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
        // Unterrahmen 5, Seite 10 traegt nur den Zeitbezug zu UTC.
        if (BdsLnav.frameId(bits) == 5) {
            val utc = BdsLnav.utc(bits) ?: return false
            if (!utc.isUsable || utc == bdsUtc) return false
            bdsUtc = utc
            return true
        }
        val t = bdsTeile.getOrPut(svid) { BdsTeile() }
        var korrekturGeaendert = false
        when (BdsLnav.frameId(bits)) {
            1 -> {
                t.clock = BdsLnav.clock(bits) ?: return false
                t.transmissionSeconds = BdsLnav.secondsOfWeek(bits).toInt()
                t.clockAtMillis = nowMillis
                // Derselbe Unterrahmen traegt die Klobuchar-Koeffizienten.
                // Sie werden nur gemerkt, nicht sofort veroeffentlicht.
                t.iono = BdsLnav.ionosphere(bits)
            }
            2 -> {
                t.orbitA = BdsLnav.orbitA(bits) ?: return false
                t.orbitAToMillis = nowMillis
            }
            3 -> {
                t.orbitB = BdsLnav.orbitB(bits) ?: return false
                t.orbitBAtMillis = nowMillis
            }
            else -> return false // Unterrahmen 4: Almanach
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
        // Erst jetzt die Iono-Koeffizienten uebernehmen: Der Empfaenger liefert
        // regelmaessig halb gefuellte Unterrahmen 1, in denen der hintere Teil
        // ein Fuellmuster ist ("toc" liegt dann ausserhalb der Woche). Nur ein
        // Satz, der als Ephemeride durchgeht, traegt auch brauchbare Koeffizienten.
        val iono = t.iono
        if (iono != null && iono.isUsable && iono != bdsIono) {
            bdsIono = iono
            korrekturGeaendert = true
        }
        val changed = bdsEphemerides[svid] != eph
        bdsEphemerides[svid] = eph
        return changed || korrekturGeaendert
    }

    // ---- Galileo ----

    /**
     * Die Worttypen 1–5 sammeln und zu einem Satz verbinden.
     *
     * Der Worttyp kommt aus `submessageId`; er muss mit Byte 0 der Rohdaten
     * übereinstimmen (das Gerät liefert ihn an beiden Stellen). Weicht beides ab,
     * ist die Meldung nicht zu trauen und wird verworfen.
     *
     * Die eigentlichen Prüfungen stecken in [GalEphemeris.of] — vor allem, dass
     * IODnav in allen vier Bahnwörtern gleich ist. Sie sind der Grund, warum
     * Geräte, deren HAL Galileo anders packt (Note 9 mit Android 10), hier
     * nichts Falsches einliefern, sondern gar nichts.
     */
    private fun addGal(svid: Int, wordType: Int, data: ByteArray, nowMillis: Long): Boolean {
        if (wordType != GalLnav.wordType(data)) return false
        val bits = GalLnav.contentBits(data) ?: return false
        // Worttyp 6 traegt nur den Zeitbezug zu UTC, keine Bahndaten.
        if (wordType == 6) {
            val utc = GalLnav.utc(bits) ?: return false
            if (!utc.isUsable || utc == galUtc) return false
            galUtc = utc
            return true
        }
        val t = galTeile.getOrPut(svid) { GalTeile() }
        var korrekturGeaendert = false
        when (wordType) {
            1 -> {
                t.orbitA = GalLnav.orbitA(bits) ?: return false
                t.orbitAAtMillis = nowMillis
            }
            2 -> {
                t.orbitB = GalLnav.orbitB(bits) ?: return false
                t.orbitBAtMillis = nowMillis
            }
            3 -> {
                t.orbitC = GalLnav.orbitC(bits) ?: return false
                t.orbitCAtMillis = nowMillis
            }
            4 -> {
                t.clock = GalLnav.clock(bits) ?: return false
                t.clockAtMillis = nowMillis
            }
            5 -> {
                t.health = GalLnav.health(bits) ?: return false
                t.healthAtMillis = nowMillis
                // Derselbe Worttyp traegt vorn die NeQuick-Koeffizienten.
                val iono = GalLnav.ionosphere(bits)
                if (iono != null && iono != galIono) {
                    galIono = iono
                    korrekturGeaendert = true
                }
            }
            else -> return false // Worttypen 6–10: Ionosphäre, Zeit, Almanach
        }

        val orbitA = t.orbitA ?: return false
        val orbitB = t.orbitB ?: return false
        val orbitC = t.orbitC ?: return false
        val clock = t.clock ?: return false
        val health = t.health ?: return false
        val frischAb = nowMillis - GAL_PART_MAX_AGE_MILLIS
        if (t.orbitAAtMillis < frischAb || t.orbitBAtMillis < frischAb ||
            t.orbitCAtMillis < frischAb || t.clockAtMillis < frischAb ||
            t.healthAtMillis < frischAb
        ) {
            return false
        }

        // null heißt hier: Worttypen passen nicht zusammen (anderer IODnav,
        // fremde Kennung, unplausible Bahn). Der letzte gültige Satz bleibt stehen.
        val eph = GalEphemeris.of(svid, orbitA, orbitB, orbitC, clock, health)
            ?: return false
        val changed = galEphemerides[svid] != eph
        galEphemerides[svid] = eph
        return changed || korrekturGeaendert
    }

    private companion object {
        /** Beginn der GPS-Zeit in Millisekunden seit 1970 (6. Januar 1980). */
        const val GPS_EPOCH_MILLIS = 315_964_800_000L

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

        /**
         * Galileo sendet je Seitenteil einen Worttyp; ein voller Durchlauf über
         * die Typen 1–5 ist in wenigen Sekunden durch. Danach darf nichts länger
         * als eine Minute auf seinen Gegenpart warten.
         */
        const val GAL_PART_MAX_AGE_MILLIS = 60_000L
        /** Rahmenzeit darf bis zu 35 Minuten nach tb liegen (Satzabstand 30 min). */
        const val GLO_FRAME_AFTER_TB_SECONDS = 2_100

        /** Und höchstens 5 Minuten davor, falls ein Satellit früher umschaltet. */
        const val GLO_FRAME_BEFORE_TB_SECONDS = 300
    }
}

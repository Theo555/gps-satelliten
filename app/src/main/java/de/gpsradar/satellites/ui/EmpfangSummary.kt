package de.gpsradar.satellites.ui

import de.gpsradar.satellites.gps.NavEphemerides
import de.gpsradar.satellites.model.CapabilityState
import de.gpsradar.satellites.model.Constellation
import de.gpsradar.satellites.model.DeviceCapabilities

/**
 * Was die Ansicht „Empfang" zeigt — bewusst flach.
 *
 * Die Zusammenfassung ist **sprachfrei**: sie nennt Arten und Zahlen, der
 * Bildschirm übersetzt sie in Text. Dadurch lässt sie sich ohne Oberfläche
 * prüfen, und ein späteres Vertiefen (mehr Zeilen, mehr Kennzahlen) braucht
 * keine Änderung an der Darstellung.
 *
 * Aufbau in drei Gruppen:
 * 1. **Gerät** — was der Empfänger laut Plattform kann
 * 2. **Bahndaten** — wie viele Ephemeriden und Almanach-Sätze vorliegen
 * 3. **Korrekturen** — ob Ionosphären- und UTC-Parameter empfangen wurden
 */
data class EmpfangSummary(
    /** Fähigkeiten mit ihrem Zustand; nachweislich fehlende fehlen ganz. */
    val abilities: List<EmpfangAbilityState>,
    val systems: List<EmpfangSystemCounts>,
    val ionosphereSystems: List<Constellation>,
    val utcSystems: List<Constellation>
) {
    /** Ohne Bahndaten hat die mittlere Gruppe nichts zu zeigen. */
    val hasOrbitData: Boolean get() = systems.any { it.ephemerides > 0 || it.almanac > 0 }

    /**
     * Steht mindestens eine Fähigkeit auf „unklar“?
     *
     * Dann braucht der Geräte-Kasten den erklärenden Satz — sonst deutet man
     * „unklar“ als „kann das Gerät nicht“. Auf Android 12 und 13 ist das der
     * Regelfall: die Trägerphase lässt sich dort erst ab Android 14 abfragen.
     */
    val hasUnclearAbilities: Boolean
        get() = abilities.any { it.state == CapabilityState.UNKNOWN }
}

/** Eine Fähigkeit, die in der Ansicht als Zeile erscheint. */
enum class EmpfangAbility {
    RAW_MEASUREMENTS,
    CARRIER_PHASE,
    NAVIGATION_MESSAGES,
    ANTENNA
}

/** Fähigkeit und ihr Zustand — das, was eine Zeile braucht. */
data class EmpfangAbilityState(
    val ability: EmpfangAbility,
    val state: CapabilityState
)

/** Kennzahlen eines Systems. */
data class EmpfangSystemCounts(
    val constellation: Constellation,
    val ephemerides: Int,
    val almanac: Int
)

/** Baut die Zusammenfassung aus Gerätefähigkeiten und Navigationsspeicher. */
object EmpfangSummaryBuilder {

    fun of(capabilities: DeviceCapabilities, nav: NavEphemerides): EmpfangSummary =
        EmpfangSummary(
            abilities = abilities(capabilities),
            systems = listOf(
                counts(Constellation.GPS, nav.gps.size, nav),
                counts(Constellation.GLONASS, nav.glo.size, nav),
                counts(Constellation.BEIDOU, nav.bds.size, nav),
                counts(Constellation.GALILEO, nav.gal.size, nav)
            ),
            ionosphereSystems = listOfNotNull(
                Constellation.GPS.takeIf { nav.corrections.gpsIono != null },
                Constellation.GALILEO.takeIf { nav.corrections.galIono != null },
                Constellation.BEIDOU.takeIf { nav.corrections.bdsIono != null }
            ),
            // GLONASS gehört hierher: es sendet zwar keine Ionosphären-
            // koeffizienten (deshalb fehlt es oben zu Recht), wohl aber den
            // Zeitbezug zu UTC(SU). Der Wert lag längst vor — er steht als
            // GLUT in der Navigationsdatei —, nur die Anzeige hat ihn
            // vergessen (gefunden am 17.11.2026 auf dem Note 9).
            utcSystems = listOfNotNull(
                Constellation.GPS.takeIf { nav.corrections.gpsUtc != null },
                Constellation.GLONASS.takeIf { nav.corrections.gloUtc != null },
                Constellation.BEIDOU.takeIf { nav.corrections.bdsUtc != null },
                Constellation.GALILEO.takeIf { nav.corrections.galUtc != null }
            )
        )

    /**
     * Nur Fähigkeiten, die nicht nachweislich fehlen.
     *
     * Dieselbe Regel wie im Messung-Tab: was die Plattform nicht kann, wird
     * nicht als Zeile angeboten; „unklar" bleibt sichtbar.
     */
    private fun abilities(capabilities: DeviceCapabilities): List<EmpfangAbilityState> =
        listOf(
            EmpfangAbility.RAW_MEASUREMENTS to capabilities.rawMeasurements,
            EmpfangAbility.CARRIER_PHASE to capabilities.accumulatedDeltaRange,
            EmpfangAbility.NAVIGATION_MESSAGES to capabilities.navigationMessages,
            EmpfangAbility.ANTENNA to capabilities.antennaInfo
        ).filter { (_, state) -> state.offerable }
            .map { (ability, state) -> EmpfangAbilityState(ability, state) }

    private fun counts(
        constellation: Constellation,
        ephemerides: Int,
        nav: NavEphemerides
    ): EmpfangSystemCounts = EmpfangSystemCounts(
        constellation = constellation,
        ephemerides = ephemerides,
        // GLONASS führt seinen Almanach in einer eigenen Liste: Der Satz hat
        // eine andere Form als der gemeinsame Typ (Knotenzeit und Draconische
        // Periode statt Halbachse und M0).
        almanac = if (constellation == Constellation.GLONASS) {
            nav.gloAlmanac.size
        } else {
            nav.almanac.count { it.system == constellation.shortName }
        }
    )
}

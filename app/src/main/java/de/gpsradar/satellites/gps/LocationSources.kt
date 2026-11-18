package de.gpsradar.satellites.gps

/**
 * Die zusätzlichen Ortsquellen **dieses** Builds.
 *
 * Hier ist die eine Stelle, an der sich der öffentliche Build und eine
 * persönliche Ausbaustufe unterscheiden. Der öffentliche Build bleibt rein
 * offline und liefert eine leere Liste: Standort kommt ausschließlich vom
 * GNSS-Empfänger, es wird nichts an fremde Dienste geschickt und
 * `NETWORK_PROVIDER` nicht angefasst.
 *
 * Eine Ausbaustufe mit Netzwerkortung liefert hier die Quellen des
 * `LocationManager` (Funkzelle, WLAN). Alles Weitere — Anmeldung, Vorrangregel,
 * Kennzeichnung in der Oberfläche — hängt dann an [LocationSource] und
 * [de.gpsradar.satellites.model.FixOrigin]; [GpsDataManager] bleibt unberührt.
 *
 * **Warum ein eigener Baustein statt eines Schalters:** Ein Schalter im Code
 * hätte in der öffentlichen Version die Netzwerk-Ortung mit ausgeliefert, nur
 * abgeschaltet. Hier fehlt sie ganz.
 */
object LocationSources {

    /** Zusätzliche Ortsquellen; im öffentlichen Build keine. */
    fun create(): List<LocationSource> = emptyList()
}

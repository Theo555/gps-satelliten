package de.gpsradar.satellites.gps

import de.gpsradar.satellites.model.FixOrigin

/**
 * Eine zusätzliche Ortsquelle neben dem GNSS-Empfänger.
 *
 * Der öffentliche Build hat keine: [LocationSources.create] liefert dort eine
 * leere Liste, es wird ausschließlich GNSS gefragt. Der Baustein existiert,
 * damit eine persönliche Ausbaustufe eine zweite Quelle an **einer** Stelle
 * einhängen kann, ohne [GpsDataManager] und die Oberfläche umzubauen.
 *
 * Die Quelle bringt ihre eigene Regel mit, wann sie schweigen soll
 * ([yieldsToGnss]): Eine ungenauere Ortsangabe darf eine laufende GNSS-Ortung
 * nicht überschreiben. Diese Regel steckt als Angabe in der Quelle und nicht als
 * Sonderfall im Code — sonst müsste die Verzweigung bei jeder neuen Quelle
 * wieder angefasst werden.
 *
 * Bewusst ohne Android-Typen in den Feldern (der Anbieter ist ein String): So
 * lässt sich die Entscheidung ohne Gerät prüfen, siehe [shouldDefer].
 */
data class LocationSource(
    /** Anbietername für den `LocationManager`, z. B. `LocationManager.GPS_PROVIDER`. */
    val provider: String,
    /** Kürzester Abstand zweier Meldungen in Millisekunden. */
    val minTimeMillis: Long,
    /** Kürzeste Strecke zweier Meldungen in Metern. */
    val minDistanceMeters: Float,
    /** true: Meldungen dieser Quelle schweigen, solange GNSS ohnehin ortet. */
    val yieldsToGnss: Boolean = true,
    /** Herkunft, die in [de.gpsradar.satellites.model.GpsLocationState.fixOrigin] landet. */
    val origin: FixOrigin = FixOrigin.ALTERNATIVE
)

/**
 * Soll diese Meldung verworfen werden, weil der Empfänger bereits ortet?
 *
 * [satellitesInFix] ist die Anzahl der Satelliten, die das Gerät gerade für die
 * Positionslösung benutzt. Sobald dort mindestens einer steht, liegt eine
 * GNSS-Ortung vor — eine zweite, gröbere Quelle würde sie nur verschlechtern.
 *
 * Die Schwelle ist bewusst `> 0` und nicht `>= 4`: Ein Fix mit drei Satelliten
 * ist zwar schwach, aber immer noch eine Satellitenmessung — und damit
 * hochwertiger als eine Zelle.
 */
fun LocationSource.shouldDefer(satellitesInFix: Int): Boolean =
    yieldsToGnss && satellitesInFix > 0

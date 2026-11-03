package de.gpsradar.satellites.model

/**
 * Kurzmeldung nach dem Ankunftsalarm.
 *
 * Sie nennt den Umkreis, innerhalb dessen die App „angekommen" als belegt
 * ansieht – dieselbe Zahl, mit der die Regel rechnet. Das ist die ehrliche
 * Aussage: näher kann das Gerät nicht bestätigen.
 */
data class ArrivalNotice(
    val radiusMeters: Double,
    val atMillis: Long
)

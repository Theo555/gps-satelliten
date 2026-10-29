package de.gpsradar.satellites.model

/** Ein einzelner Punkt eines Tracks oder einer Route. */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double,
    val elevationMeters: Double? = null,
    val timeMillis: Long? = null
)

/**
 * Ein zusammenhängender Track bzw. eine Route (Linie aus Punkten),
 * getrennt von einzelnen Wegpunkten gespeichert.
 */
data class Track(
    val id: Long,
    val name: String,
    val points: List<TrackPoint>,
    val reversed: Boolean = false
) {
    /**
     * Punkte in aktueller Verfolgungsrichtung. `points` bleibt immer in der
     * ursprünglich aufgezeichneten/importierten Reihenfolge; `reversed` kippt
     * lediglich die Verfolgungsrichtung (ohne die Daten zu verändern).
     */
    val orderedPoints: List<TrackPoint>
        get() = if (reversed) points.reversed() else points

    /** Gesamtlänge der Linie in Metern (richtungsunabhängig). */
    val lengthMeters: Double
        get() {
            var sum = 0.0
            for (i in 1 until points.size) {
                sum += Navigation.distanceMeters(
                    points[i - 1].latitude, points[i - 1].longitude,
                    points[i].latitude, points[i].longitude
                )
            }
            return sum
        }

    /** Index (in Verfolgungsrichtung) des Punktes, der der Position am nächsten liegt. */
    fun nearestIndex(lat: Double, lon: Double): Int {
        var bestIndex = 0
        var bestDistance = Double.MAX_VALUE
        orderedPoints.forEachIndexed { i, p ->
            val d = Navigation.distanceMeters(lat, lon, p.latitude, p.longitude)
            if (d < bestDistance) {
                bestDistance = d
                bestIndex = i
            }
        }
        return bestIndex
    }

    /** Reststrecke in Metern vom Punkt [fromIndex] bis zum Ende der Verfolgungsrichtung. */
    fun remainingMeters(fromIndex: Int): Double {
        var sum = 0.0
        for (i in fromIndex until orderedPoints.lastIndex) {
            sum += Navigation.distanceMeters(
                orderedPoints[i].latitude, orderedPoints[i].longitude,
                orderedPoints[i + 1].latitude, orderedPoints[i + 1].longitude
            )
        }
        return sum
    }

    /**
     * Kurs der Linie in Verfolgungsrichtung ab [fromIndex]: Peilung von diesem
     * Punkt zu dem Punkt, der mindestens [lookAheadMeters] weiter entlang der
     * Linie liegt. Der Anker liegt damit auf der Linie selbst (nicht an der
     * GPS-Position) und glättet das Punktrauschen der Aufzeichnung. Am Ende
     * fällt er auf die letzten beiden Punkte zurück.
     */
    fun courseDegrees(fromIndex: Int, lookAheadMeters: Double): Double? {
        val pts = orderedPoints
        if (pts.size < 2) return null
        val start = fromIndex.coerceIn(0, pts.lastIndex - 1)
        var acc = 0.0
        var i = start
        while (i < pts.lastIndex) {
            acc += Navigation.distanceMeters(
                pts[i].latitude, pts[i].longitude,
                pts[i + 1].latitude, pts[i + 1].longitude
            )
            i++
            if (acc >= lookAheadMeters) break
        }
        return Navigation.initialBearingDegrees(
            pts[start].latitude, pts[start].longitude,
            pts[i].latitude, pts[i].longitude
        )
    }
}

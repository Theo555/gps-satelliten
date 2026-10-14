package de.gpsradar.satellites.model

/** Ein einzelner Punkt eines Tracks oder einer Route. */
data class TrackPoint(
    val latitude: Double,
    val longitude: Double
)

/**
 * Ein zusammenhängender Track bzw. eine Route (Linie aus Punkten),
 * getrennt von einzelnen Wegpunkten gespeichert.
 */
data class Track(
    val id: Long,
    val name: String,
    val points: List<TrackPoint>
) {
    /** Gesamtlänge der Linie in Metern. */
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

    /** Index des Punktes, der der aktuellen Position am nächsten liegt. */
    fun nearestIndex(lat: Double, lon: Double): Int {
        var bestIndex = 0
        var bestDistance = Double.MAX_VALUE
        points.forEachIndexed { i, p ->
            val d = Navigation.distanceMeters(lat, lon, p.latitude, p.longitude)
            if (d < bestDistance) {
                bestDistance = d
                bestIndex = i
            }
        }
        return bestIndex
    }

    /** Reststrecke in Metern vom Punkt [fromIndex] bis zum Ende des Tracks. */
    fun remainingMeters(fromIndex: Int): Double {
        var sum = 0.0
        for (i in fromIndex until points.lastIndex) {
            sum += Navigation.distanceMeters(
                points[i].latitude, points[i].longitude,
                points[i + 1].latitude, points[i + 1].longitude
            )
        }
        return sum
    }
}

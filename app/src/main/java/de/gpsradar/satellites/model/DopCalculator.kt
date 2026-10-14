package de.gpsradar.satellites.model

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Berechnet die Dilution-of-Precision (DOP) Werte aus der Satellitengeometrie.
 *
 * Die Geometrie-Matrix wird aus Azimut und Elevation der im Fix verwendeten
 * Satelliten aufgebaut (ENU-Koordinaten + Empfänger-Uhrfehler).
 */
object DopCalculator {

    data class DopValues(
        val hdop: Double? = null,
        val vdop: Double? = null,
        val pdop: Double? = null,
        val tdop: Double? = null,
        val gdop: Double? = null
    )

    fun calculate(satellites: List<SatelliteInfo>): DopValues {
        val used = satellites.filter { it.usedInFix && it.cn0DbHz > 0f }
        return when {
            used.size >= 4 -> calculate3D(used)
            used.size == 3 -> calculate2D(used)
            else -> DopValues()
        }
    }

    private fun calculate3D(sats: List<SatelliteInfo>): DopValues {
        // Design-Matrix: [Ost, Nord, Hoch, 1] pro Satellit
        val rows = sats.map { sat ->
            val el = Math.toRadians(sat.elevationDegrees.toDouble())
            val az = Math.toRadians(sat.azimuthDegrees.toDouble())
            doubleArrayOf(
                sin(az) * cos(el), // Ost-Komponente
                cos(az) * cos(el), // Nord-Komponente
                sin(el),           // Hoch-Komponente
                1.0                // Empfänger-Uhrfehler
            )
        }
        val q = inverse(multiplyAtA(rows)) ?: return DopValues()
        return DopValues(
            hdop = sqrtSafe(q[0][0] + q[1][1]),
            vdop = sqrtSafe(q[2][2]),
            pdop = sqrtSafe(q[0][0] + q[1][1] + q[2][2]),
            tdop = sqrtSafe(q[3][3]),
            gdop = sqrtSafe(q[0][0] + q[1][1] + q[2][2] + q[3][3])
        )
    }

    private fun calculate2D(sats: List<SatelliteInfo>): DopValues {
        // 2D-Lösung: [Ost, Nord, 1] -> nur HDOP verfügbar
        val rows = sats.map { sat ->
            val el = Math.toRadians(sat.elevationDegrees.toDouble())
            val az = Math.toRadians(sat.azimuthDegrees.toDouble())
            doubleArrayOf(
                sin(az) * cos(el),
                cos(az) * cos(el),
                1.0
            )
        }
        val q = inverse(multiplyAtA(rows)) ?: return DopValues()
        return DopValues(hdop = sqrtSafe(q[0][0] + q[1][1]))
    }

    /** A^T * A für eine Liste von Zeilenvektoren. */
    private fun multiplyAtA(rows: List<DoubleArray>): Array<DoubleArray> {
        val n = rows[0].size
        val result = Array(n) { DoubleArray(n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                var sum = 0.0
                for (row in rows) {
                    sum += row[i] * row[j]
                }
                result[i][j] = sum
            }
        }
        return result
    }

    /** Matrix-Inversion über Gauß-Jordan; liefert null bei Singularität. */
    private fun inverse(matrix: Array<DoubleArray>): Array<DoubleArray>? {
        val n = matrix.size
        val aug = Array(n) { i -> DoubleArray(2 * n) }
        for (i in 0 until n) {
            for (j in 0 until n) {
                aug[i][j] = matrix[i][j]
            }
            aug[i][i + n] = 1.0
        }

        for (col in 0 until n) {
            var pivot = col
            for (row in col + 1 until n) {
                if (abs(aug[row][col]) > abs(aug[pivot][col])) pivot = row
            }
            if (abs(aug[pivot][col]) < 1e-12) return null
            val tmp = aug[col]
            aug[col] = aug[pivot]
            aug[pivot] = tmp

            val div = aug[col][col]
            for (j in 0 until 2 * n) aug[col][j] /= div

            for (row in 0 until n) {
                if (row != col) {
                    val factor = aug[row][col]
                    if (factor != 0.0) {
                        for (j in 0 until 2 * n) aug[row][j] -= factor * aug[col][j]
                    }
                }
            }
        }

        return Array(n) { i -> DoubleArray(n) { j -> aug[i][j + n] } }
    }

    private fun sqrtSafe(v: Double): Double? =
        if (v.isFinite() && v >= 0.0) sqrt(v) else null
}

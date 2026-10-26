package de.gpsradar.satellites.model

/** Ein Messsatz (eine Epoche) mit der Empfängeruhr, die dazu gehörte. */
data class RawEpoch(
    val receivedAtMillis: Long,
    val clock: RawClockSnapshot?,
    val rows: List<RawMeasurement>
)

/** Zustand der Rohdaten-Aufzeichnung. */
enum class CapturePhase {
    /** Normalbetrieb: nur die letzten Sekunden werden behalten. */
    IDLE,
    /** Aufzeichnung läuft: alles bleibt stehen. */
    CAPTURING,
    /** Aufzeichnung beendet: Puffer eingefroren, bereit zum Export. */
    FINISHED
}

/** Kurzinfo für die Anzeige. */
data class BufferStats(val epochs: Int, val rows: Int)

/**
 * Ringpuffer für Roh-Messsätze.
 *
 * Im Normalbetrieb werden nur die letzten [maxAgeMillis] behalten, damit der
 * Export immer eine kurze Zeitreihe enthält. Während einer Aufzeichnung
 * ([keepAll]) wird nicht nach Alter gekürzt. Danach kann der Puffer
 * eingefroren werden ([freeze]), damit der Export die Aufzeichnung vollständig
 * sieht, während parallel weiter gemessen wird.
 *
 * Reine Kotlin-Logik ohne Android-Abhängigkeit – dadurch unit-testbar.
 */
class EpochBuffer(
    private val maxAgeMillis: Long = DEFAULT_MAX_AGE_MILLIS,
    private val maxRows: Int = DEFAULT_MAX_ROWS
) {
    private val epochs = ArrayDeque<RawEpoch>()
    private var rowTotal = 0
    private var frozen = false

    /** Während einer Aufzeichnung nicht nach Alter kürzen. */
    var keepAll: Boolean = false

    fun add(epoch: RawEpoch) {
        if (frozen) return
        if (epoch.rows.isEmpty()) return
        epochs.addLast(epoch)
        rowTotal += epoch.rows.size
        trim()
    }

    /** Ab jetzt nichts mehr aufnehmen (Export-Zustand). */
    fun freeze() {
        frozen = true
    }

    /** Puffer leeren und wieder aufnehmen (Normalbetrieb). */
    fun clear() {
        epochs.clear()
        rowTotal = 0
        frozen = false
        keepAll = false
    }

    fun snapshot(): List<RawEpoch> = epochs.toList()

    val epochCount: Int get() = epochs.size
    val rowCount: Int get() = rowTotal
    val isFrozen: Boolean get() = frozen

    private fun trim() {
        if (!keepAll) {
            val newest = epochs.lastOrNull()?.receivedAtMillis ?: return
            while (epochs.isNotEmpty()) {
                val oldest = epochs.first().receivedAtMillis
                if (newest - oldest <= maxAgeMillis) break
                rowTotal -= epochs.removeFirst().rows.size
            }
        }
        // Harte Obergrenze, damit auf schwachen Geräten nichts unbegrenzt wächst.
        while (rowTotal > maxRows && epochs.size > 1) {
            rowTotal -= epochs.removeFirst().rows.size
        }
    }

    companion object {
        const val DEFAULT_MAX_AGE_MILLIS = 60_000L
        const val DEFAULT_MAX_ROWS = 20_000
    }
}

package de.gpsradar.satellites.model

/**
 * Entscheidet, ob die Ortung hängt, obwohl der Empfänger arbeitet.
 *
 * **Der beobachtete Gerätefehler** (S20 FE, 14.11.2026): Der Empfänger trackt
 * weiter — Roh-Messungen und Navigationsmeldungen kommen im Sekundentakt —,
 * liefert aber keine Navigationslösung mehr. In der Anzeige steht dann
 * „Kein Fix · Sats 0/0", obwohl im Hintergrund Daten fließen; im Systemprotokoll
 * verwirft der Standortdienst jede Zustellung (`dropped delivery - too close`).
 * Abhilfe ist immer dieselbe: die App meldet sich eine Minute lang ganz ab und
 * danach neu an — erst dann startet die Engine kalt.
 *
 * Die Regel ist absichtlich eng gefasst: Nur wenn **nachweislich Messungen
 * ankommen** und trotzdem keine Position entsteht, ist der Empfänger gemeint —
 * und nicht eine Abschattung, ein Keller oder ein noch nicht eingeschwungener
 * Kaltstart. Fehlt der Fix, weil wirklich kein Himmel da ist, kommen auch keine
 * Messungen, und es passiert nichts.
 */
object ReceiverHealth {

    /**
     * So lange ohne Fix, bevor ein hängender Empfänger angenommen wird. Bewusst
     * großzügig: Ein Kaltstart braucht am S20 FE bis zu 60 Sekunden, und ein
     * kurzer Aussetzer unter Bäumen soll keine Minute Ortung kosten.
     */
    const val FIX_STALL_LIMIT_MILLIS = 120_000L

    /** Messungen gelten als „fließen", wenn sie höchstens so alt sind. */
    const val MEASUREMENT_RECENT_MILLIS = 20_000L

    /**
     * Liegt ein hängender Empfänger vor?
     *
     * @param noFixForMillis wie lange schon kein Fix mehr vorliegt
     * @param measurementSilentMillis Alter der letzten Roh-Messung
     */
    fun fixStalled(noFixForMillis: Long, measurementSilentMillis: Long): Boolean =
        noFixForMillis >= FIX_STALL_LIMIT_MILLIS &&
            measurementSilentMillis <= MEASUREMENT_RECENT_MILLIS

    /**
     * Automatisch neu anfahren darf nur, wer **nicht navigiert**: Der Neustart
     * kostet eine Minute ohne Position. Wer einem Ziel oder Track folgt, bekommt
     * stattdessen nur den Hinweis und entscheidet selbst.
     *
     * @param navigating aktives Ziel oder Track laufen gerade
     * @param alreadyRestarted in diesem Ausfall wurde schon einmal neu angefahren
     */
    fun mayRestartAutomatically(navigating: Boolean, alreadyRestarted: Boolean): Boolean =
        !navigating && !alreadyRestarted
}

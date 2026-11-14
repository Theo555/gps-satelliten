package de.gpsradar.satellites

import de.gpsradar.satellites.model.ReceiverHealth
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regelt, wann ein hängender Empfänger angenommen wird — und wann nicht.
 *
 * Hintergrund (am S20 FE beobachtet, 14.11.2026): Der Chip trackt weiter
 * (Roh-Messungen und Navigationsmeldungen laufen), liefert aber keine
 * Navigationslösung. Erkennen lässt sich das nur an der Kombination „kein Fix"
 * **und** „Messungen kommen trotzdem" — fehlt beides, ist es eine Abschattung.
 */
class ReceiverHealthTest {

    @Test
    fun keinFixNachZweiMinutenMitLaufendenMessungenIstEinHaenger() {
        assertTrue(
            ReceiverHealth.fixStalled(
                noFixForMillis = ReceiverHealth.FIX_STALL_LIMIT_MILLIS,
                measurementSilentMillis = 1_000L
            )
        )
    }

    @Test
    fun kurzerAussetzerLoestNichtsAus() {
        assertFalse(
            ReceiverHealth.fixStalled(
                noFixForMillis = ReceiverHealth.FIX_STALL_LIMIT_MILLIS - 1,
                measurementSilentMillis = 1_000L
            )
        )
    }

    @Test
    fun schweigenderEmpfaengerIstKeinHaenger() {
        // Kein Fix und keine Messungen: Abschattung, Keller, Kaltstart – dort
        // hilft kein Neustart.
        assertFalse(
            ReceiverHealth.fixStalled(
                noFixForMillis = 600_000L,
                measurementSilentMillis = ReceiverHealth.MEASUREMENT_RECENT_MILLIS + 1
            )
        )
    }

    @Test
    fun automatischerNeustartNurOhneNavigation() {
        assertTrue(ReceiverHealth.mayRestartAutomatically(navigating = false, alreadyRestarted = false))
        // Beim Navigieren kostet der Neustart eine Minute Position – das
        // entscheidet der Nutzer selbst.
        assertFalse(ReceiverHealth.mayRestartAutomatically(navigating = true, alreadyRestarted = false))
        // Nur einmal je Ausfall, sonst entstünde eine Schleife.
        assertFalse(ReceiverHealth.mayRestartAutomatically(navigating = false, alreadyRestarted = true))
    }
}

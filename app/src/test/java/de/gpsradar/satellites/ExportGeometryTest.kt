package de.gpsradar.satellites

import de.gpsradar.satellites.model.ExportGeometry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Prüft die Regel, nach der die Satellitenlage in den Rohdatenexport geht.
 *
 * Hintergrund: Am 14.11.2026 auf dem Sportplatz meldete der Empfänger nach
 * 3:12 Minuten einmal „gestoppt", lief aber weiter (C/N₀ unverändert, keine
 * Lücke im Datenstrom). Danach kam kein neuer Satellitenstatus mehr, weil sich
 * die Liste im freien Feld nicht ändert – die Folge waren 109 von 300 Epochen
 * ohne Höhenwinkel. Die alte Regel hing an einem Kennzeichen, das ein solcher
 * Fehlalarm löschen konnte; jetzt entscheidet eine Frist.
 */
class ExportGeometryTest {

    private fun nutzbar(
        tracking: Boolean = true,
        simuliert: Boolean = false,
        vorhanden: Boolean = true,
        alter: Long = 1_000L,
        frist: Long = 30 * 60 * 1000L
    ) = ExportGeometry.usable(tracking, simuliert, vorhanden, alter, frist)

    @Test
    fun frischeLageBeimLaufendenBetriebIstBrauchbar() {
        assertTrue(nutzbar())
    }

    @Test
    fun nachEinemGemeldetenStoppBleibtDieLageBrauchbar() {
        // Der Kern des Fehlers vom 14.11.: Es gibt keinen Zustand „Stopp", der
        // die Lage entwertet – nur Alter, Simulation und fehlende Ortung zählen.
        assertTrue(nutzbar(alter = 5 * 60 * 1000L))
        assertTrue(nutzbar(alter = 30 * 60 * 1000L))
    }

    @Test
    fun ohneOrtungGibtEsKeineLage() {
        assertFalse(nutzbar(tracking = false))
    }

    @Test
    fun erfundeeneMesswerteBekommenKeineEchteLage() {
        assertFalse(nutzbar(simuliert = true))
    }

    @Test
    fun ohneGemeldeteLageGibtEsNichts() {
        assertFalse(nutzbar(vorhanden = false))
    }

    @Test
    fun dieFristIstEineNotbremse() {
        assertFalse(nutzbar(alter = 30 * 60 * 1000L + 1))
        assertFalse(nutzbar(alter = Long.MAX_VALUE))
        assertFalse("negatives Alter ist ein Rechenfehler", nutzbar(alter = -1L))
    }
}

package de.gpsradar.satellites.model

/**
 * Ersatz-Koordinaten für Bildschirmaufnahmen.
 *
 * ## Wozu
 *
 * Auf der Positionsseite steht die gemessene Koordinate. Für Aufnahmen, die
 * veröffentlicht werden, wird sie sonst nachträglich übermalt — in der Mitte
 * der Hauptkarte sieht das wie ein Zeichenfehler aus. Ein bekannter Ort ist in
 * einem Rutsch zu haben und sieht aus wie eine echte Aufnahme.
 *
 * ## Warum das nicht über einen System-Mock geht
 *
 * Der Weg über „Mock-Standort" bräuchte eine zusätzliche App und die
 * Entwickleroptionen, und er wirkt auf **alle** Apps und **alle** Builds — ein
 * vergessener Mock würde still jede spätere Messung verfälschen. Diese Ersetzung
 * dagegen hängt am Debug-Build (dieselbe `FLAG_DEBUGGABLE`-Prüfung wie beim
 * Rohbit-Mitschnitt und der Navigationsdatei), ein Release-Build kann sie gar
 * nicht anzeigen, und sie endet mit dem Prozess.
 *
 * ## Was echt bleibt
 *
 * Ersetzt werden **nur** Breite, Länge und Höhe. Genauigkeit, Tempo, Kurs,
 * Satelliten, C/N₀ und DOP bleiben gemessen — die Aufnahme zeigt also einen
 * echten Empfang an einem erfundenen Ort.
 *
 * **Achtung:** Wer während der Ersetzung einen Track aufzeichnet oder einen
 * Export mit Position erzeugt, schreibt die falschen Koordinaten hinein. Der
 * Hinweis auf der Info-Seite sagt das, und die Ersetzung ist an ihrer
 * Beschriftung ablesbar.
 */
enum class ScreenshotPosition(
    /** Ortsname — ein Eigenname, in beiden Sprachen gleich. */
    val placeName: String,
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double
) {
    BRANDENBURGER_TOR("Brandenburger Tor, Berlin", 52.516275, 13.377704, 40.0),
    DEUTSCHES_MUSEUM("Deutsches Museum, München", 48.129894, 11.583440, 520.0),
    ATOMIUM("Atomium, Brüssel", 50.894920, 4.341600, 100.0);

    companion object {
        /**
         * Nächster Eintrag im Ring: `null` → Brandenburger Tor → … → Atomium
         * → `null`.
         *
         * Ein einzelner Knopf statt dreier Schalter: Die Info-Seite ist kein
         * Einstellungsbildschirm, und der Zustand steht in seiner Beschriftung.
         */
        fun next(current: ScreenshotPosition?): ScreenshotPosition? {
            val werte = entries
            // `null` ist der Zustand „aus" und steht im Ring vor dem ersten Ort.
            val i = if (current == null) -1 else werte.indexOf(current)
            return when {
                i < 0 -> werte.first()
                i >= werte.lastIndex -> null
                else -> werte[i + 1]
            }
        }
    }
}

/**
 * Was am Ende angezeigt wird — gemessen oder ersetzt.
 *
 * [replaced] macht die Entscheidung nachvollziehbar, ohne dass der Aufrufer sie
 * noch einmal treffen muss.
 */
data class DisplayedPosition(
    val latitude: Double,
    val longitude: Double,
    val altitudeMeters: Double?,
    val replaced: Boolean
)

/**
 * Ersatzwerte über eine gemessene Position legen.
 *
 * [place] `null` heißt: nichts ersetzen (Normalfall, auch im Release-Build).
 */
fun displayedPosition(
    place: ScreenshotPosition?,
    latitude: Double,
    longitude: Double,
    altitudeMeters: Double?
): DisplayedPosition = if (place == null) {
    DisplayedPosition(latitude, longitude, altitudeMeters, replaced = false)
} else {
    DisplayedPosition(place.latitude, place.longitude, place.altitudeMeters, replaced = true)
}

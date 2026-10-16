# GPS Satelliten 📡

Native Android-App (Kotlin + Jetpack Compose) zur **Live-Anzeige von GNSS-Satelliten** – mit Himmels-Radar, Signalanalyse, Positionsanzeige und vollwertigen **Geocaching-Funktionen**. Komplett offline nutzbar, ohne Werbung und ohne Datenweitergabe.

## Download (fertige APK) ⬇️

Keine Lust, selbst zu kompilieren? Lade einfach die fertige APK herunter und installiere sie direkt:

- **[Neueste Version](https://github.com/Theo555/gps-satelliten/releases/latest)** – das aktuelle Release (dort APK herunterladen)
- **[Alle Releases](https://github.com/Theo555/gps-satelliten/releases)** – Übersicht aller Releases

> **Hinweis:** Die APK ist mit einem Test-Schlüssel signiert. Beim ersten Installieren kann eine **Play-Protect-Warnung** erscheinen – dort einfach **„Trotzdem installieren"** wählen. Voraussetzung: Android 7.0 (API 24) oder neuer.

## Funktionen

### 🛰️ GNSS-Live-Anzeige

- Echtzeit-Erfassung empfangener Satelliten (**GPS, GLONASS, Galileo, BeiDou, QZSS, SBAS**)
- **Himmels-Radar** – Polarplot mit Elevation/Azimut, „Nord oben" oder „Kompass oben", animierter Radar-Sweep
- **Signalstärke** – C/N₀-Balkendiagramm mit Qualitätsfarben und Zusammenfassung
- **Satellitenliste** – sortier-/filterbar, mit Ephemeriden-/Almanach-Status und Trägerfrequenz

### 📍 Positionsbestimmung

- Koordinaten in drei Formaten: **Dezimalgrad, DMS und DDM** (Geocaching-Standard)
- Genauigkeit, Höhe (MSL), Geschwindigkeit, Kurs/Peilung
- **DOP-Werte** (HDOP/VDOP/PDOP/GDOP) – direkt aus der Satellitengeometrie berechnet
- TTFF (Time To First Fix)

### 🧭 Geocaching

- **Zielpunkte** mit Name, GC-Code, Hint (ROT13), Schwierigkeit/Gelände (D/T) und Cache-Typ
- **Pfeil-Navigation** zum aktiven Ziel (Entfernung + Peilung, relativ zur Blickrichtung)
- **Projektion** – neuen Zielpunkt aus Peilung + Entfernung berechnen (für Multis/Mysteries)
- **Hint-Anzeige** mit ROT13-Entschlüsselung (Spoiler-Schutz)
- **Import/Export** – GPX (Geocaching-Standard) und JSON über den Android-Datei-Picker, inkl. Pocket-Query-Support (GC-Code, Typ, D/T, Hint)
- **„In Karte öffnen"** – Zielpunkt per `geo:`-Intent an eine externe Karten-App übergeben

### ⚙️ Sonstiges

- **Simulationsmodus** – realistisches Satelliten-Szenario, wenn kein Empfang verfügbar ist (z. B. in Gebäuden oder im Emulator)
- Dunkles Telemetrie-Design

## Screenshots

> _TODO: Screenshots der vier Tabs (Radar, Signale, Liste, Position) einfügen._

## Technik

| | |
|---|---|
| Sprache | Kotlin 2.2 |
| UI | Jetpack Compose (Material 3) |
| Architektur | MVVM (`ViewModel` + `StateFlow`) |
| GNSS | `GnssStatusCompat`, `LocationManager`, Kompass-Sensoren |
| minSdk / targetSdk | 24 (Android 7.0) / 36 |
| Build | AGP 9.x, Gradle 9.3 (Wrapper), R8 + Ressourcen-Shrinking |

## Voraussetzungen

- Android 7.0 (API 24) oder neuer
- Android Studio (empfohlen) **oder** Gradle 9.3 + JDK 17 + Android SDK 36

## Bauen

```bash
# SDK-Pfad hinterlegen (falls nicht automatisch erkannt)
echo "sdk.dir=/pfad/zu/android-sdk" > local.properties

# Debug-APK
./gradlew assembleDebug

# Release-APK (minifiziert, ~2 MB)
./gradlew assembleRelease
```

Die Release-APK liegt danach unter `app/build/outputs/apk/release/app-release.apk`.

> **Hinweis zum Signieren:** Für den Release-Build wird ein Signatur-Key benötigt (siehe `signingConfigs` in `app/build.gradle.kts`). Für eine Play-Store-Veröffentlichung einen **eigenen** Upload-Key erstellen und sicher aufbewahren – ein im Projekt liegender Key ist nur für lokale Tests gedacht.

## Nutzung als Geocaching-Begleiter

1. Pocket-Query von geocaching.com als **GPX** herunterladen.
2. In der App unter **Position → Datei-Übertragung → Importieren (GPX/JSON)** importieren.
3. Cache in der Liste antippen, um ihn zum **aktiven Ziel** zu machen.
4. Dem **Richtungspfeil** folgen (Entfernung + Peilung).
5. Hint über **„Hint anzeigen"** entschlüsseln, Cache als **„gefunden"** markieren.
6. Für Multis/Mysteries: **Projektion** nutzen oder neue Zielpunkte **manuell** anlegen (Koordinaten in Dezimal/DMS/DDM).

## Projektstruktur

```
app/src/main/java/de/gpsradar/satellites/
├── gps/               GpsDataManager (GNSS-Status + Kompass-Sensoren)
├── model/             Datenmodelle, DOP-Berechnung, Koordinaten-Parser, GPX/JSON-Codec
├── ui/                ViewModel + Compose-Screens und -Komponenten
└── MainActivity.kt    App-Einstieg mit Tab-Navigation
```

## Lizenz

[MIT](LICENSE) © 2026 GPS Satelliten contributors


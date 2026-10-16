# Projekt-Konventionen für KI-Agenten

## Projekt
GPS-Satelliten-App (Android, Kotlin + Jetpack Compose)

- Package: `de.gpsradar.satellites`
- minSdk 24, targetSdk 36
- Build-System: Gradle 9.3.1 (Wrapper), AGP 9.1.1, Kotlin 2.2.10

## Build

Lokaler Release-Build (Signing über Umgebungsvariablen):

```bash
STORE_PASSWORD=... KEY_PASSWORD=... ./gradlew assembleRelease
```

Keystore: `my-upload-key.jks` (Alias `upload`). Liegt **nicht** im Repo (gitignored).

## Release-Regeln (wichtig!)

1. **Nicht jede kleine Änderung als GitHub-Release veröffentlichen.**
   - Ein Release entsteht nur, wenn der Nutzer es ausdrücklich verlangt
     oder ein sinnvoller Meilenstein / Feature-Abschluss erreicht ist.
   - Kleine Änderungen und Fixes werden nur committet – kein Release, kein Tag,
     keine Versionserhöhung.
2. **Beim Veröffentlichen eines Releases wird die aktuelle APK automatisch
   gebaut und angehängt** – siehe `.github/workflows/release.yml`.
   - Der Workflow braucht die Secrets `KEYSTORE_BASE64`, `STORE_PASSWORD`
     und `KEY_PASSWORD` (siehe Workflow-Datei).
   - Keine Passwörter oder Keystores in Dateien dieses Repos ablegen.

## Wichtige Dateien

- `app/src/main/java/de/gpsradar/satellites/` – Quellcode
- `gradle/libs.versions.toml` – Versionskatalog
- `.github/workflows/release.yml` – Release-Automatisierung

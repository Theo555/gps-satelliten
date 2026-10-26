# F-Droid – Vorbereitung

Hier liegt **nur der Entwurf** der Metadaten. Es wurde nichts eingereicht.

## Was in diesem Repository liegt

| Pfad | Zweck |
|---|---|
| `fastlane/metadata/android/de-DE/` | deutscher F-Droid-/Store-Eintrag |
| `fastlane/metadata/android/en-US/` | englischer Eintrag |
| `f-droid/de.gpsradar.satellites.yml` | Entwurf für `fdroiddata/metadata/` |

`fastlane/` **muss** im Wurzelverzeichnis liegen, weil F-Droid dort sucht.
Der Ordner `f-droid/` ist frei gewählt und nur für uns.

## Bereits erfüllt

- MIT-Lizenz, `LICENSE` im Repo
- öffentliches Git-Repository mit Tags
- keine Werbung, keine Tracker, keine Nicht-freien Abhängigkeiten
- keine `INTERNET`-Berechtigung
- `assembleRelease` funktioniert **ohne Keystore** und erzeugt
  `app/build/outputs/apk/release/app-release-unsigned.apk`

## Vor dem Einreichen zu prüfen

1. **Kategorien**: `Navigation` und `Science & Education` müssen in
   `fdroiddata/config/categories.yml` existieren. Wenn nicht, dort die
   vorhandenen Namen nehmen.
2. **Spenden**: Es gibt bewusst **kein `Donate`-Feld** und keinen Spenden-Knopf
   in der App – damit entfällt die F-Droid-Nachweispflicht für Spendenlinks
   („donation links need verification“). Der Link steht ausschließlich in der
   README unter `## Unterstützung`, also an der Stelle, die die Policy als
   Nachweis akzeptiert.
3. **Screenshots**: nach
   `fastlane/metadata/android/<locale>/images/phoneScreenshots/` legen
   (JPG oder PNG, Hochformat). Ohne Screenshots ist der Eintrag karg.
4. **Changelog**: `changelogs/<versionCode>.txt`. Beim nächsten Release
   also `9.txt`.
5. **E-Mail-Adresse** `t.kichtan@gmail.com` wird öffentlich sichtbar.

## Was man wissen sollte

- F-Droid **signiert die APK mit eigenem Schlüssel**. Die F-Droid-Version
  und die GitHub-Version sind deshalb nicht gegeneinander updatebar;
  ein Wechsel erfordert Deinstallieren (Datenverlust).
- Der Build läuft auf F-Droids Servern aus dem Quelltext. Reproduzierbare
  Builds sind kein Muss, aber ein Plus.
- **Tags nur von `main` erstellen.** Ein Tag auf dem privaten Branch
  würde dessen Inhalt mitveröffentlichen.

## Wenn es losgehen soll

1. Fork von `https://gitlab.com/fdroid/fdroiddata`
2. `metadata/de.gpsradar.satellites.yml` aus diesem Ordner dorthin kopieren
3. Merge-Request stellen, Beschreibung kurz halten
4. Review abwarten – Rückfragen kommen meist zu Build-Details

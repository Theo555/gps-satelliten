# Werkzeuge

Kleine Helfer, die verhindern sollen, dass Inhalte veröffentlicht werden,
die nicht zur öffentlichen Historie gehören. Alle Skripte nennen bewusst
nur den Branch `main` — sie enthalten keine internen Bezeichnungen und
dürfen deshalb im öffentlichen Repository liegen.

| Datei | Zweck |
|---|---|
| `tag-release.sh` | legt einen Release-Tag **immer auf `main`** an und prüft ihn |
| `release-notes.sh` | baut die Release-Notizen aus beiden Changelogs (deutsch oben, englischer Teil ausklappbar) und auf Wunsch die vorausgefüllte Release-Adresse |
| `check-public-refs.sh` | prüft lokale Tags und die Refs auf `origin` gegen `main` |
| `git-hooks/pre-push` | blockiert Pushes von Refs, die nicht von `main` erreichbar sind |

## Einmalig einrichten

```bash
git config core.hooksPath tools/git-hooks
chmod +x tools/*.sh tools/git-hooks/pre-push
```

`core.hooksPath` gilt pro Klon und muss nach einem frischen `git clone`
erneut gesetzt werden.

## Ablauf für ein Release

```bash
tools/check-public-refs.sh          # Ausgangslage prüfen
tools/tag-release.sh v1.4.0         # Tag auf main anlegen (bricht bei unsauberem Baum ab)
git push origin main
git push origin v1.4.0              # der Hook prüft dabei mit
tools/check-public-refs.sh          # Ergebnis kontrollieren
```

**Release-Notizen:** `tools/release-notes.sh <versionCode>` gibt den Text aus,
`tools/release-notes.sh <versionCode> --url v1.4.0` die Adresse, die Tag, Titel
und Notizen schon vorausfüllt. Der Titel ist der Tag selbst (`v1.4.0`, nicht
`1.4.0`). Der Text enthält **beide Sprachen** — deutsch oben, darunter der
englische Teil in `<details><summary>English</summary>`, so wie bis 1.3.10.
Das Anlegen des Releases bleibt Handarbeit im Browser: Es gibt auf dem
Entwicklungsrechner keinen API-Token, und der Release-Workflow hängt die APK
nur an ein *veröffentlichtes* Release (ein Tag-Push allein startet ihn nicht).

## Grenzen

- `git push --no-verify` umgeht den Hook.
- Der Hook prüft nur, was gerade gepusht wird. Er kann nicht verhindern,
  dass jemand einen anderen Klon ohne Hook verwendet.
- `check-public-refs.sh` braucht Netzwerkzugriff für den Teil zu `origin`.

## Bildschirmaufnahmen

Die Aufnahmen in `screenshots/` zeigen einen echten Empfang, aber keinen echten
Ort: Die Positionsseite würde sonst verraten, wo das Gerät stand.

Dafür gibt es im **Debug-Build** einen Schalter auf der Info-Seite (Fragezeichen
in der Kopfzeile), Karte „Screenshot-Position": Er ersetzt die angezeigte
Koordinaten durch einen bekannten Ort (Brandenburger Tor, Deutsches Museum,
Atomium) und schaltet im Ring weiter, bis wieder „aus" steht. Ein Release-Build
zeigt die Karte nicht — dort gibt es den Rückruf nicht, den sie braucht.

Was dabei echt bleibt: Genauigkeit, Tempo, Kurs, Satelliten, C/N₀ und DOP. Die
Höhe stammt vom gewählten Ort. Ein **Track**, der währenddessen aufgezeichnet
wird, enthält die falschen Koordinaten — die Aufzeichnung also vorher beenden.
Die Ersetzung steht nur im Arbeitsspeicher und endet mit dem Neustart der App.

Vorher prüfen, nachher kontrollieren:

```bash
tools/check-screenshots.sh          # Ordner und READMEs passen zusammen
```


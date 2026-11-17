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

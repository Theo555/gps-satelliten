# Werkzeuge

Kleine Helfer, die verhindern sollen, dass Inhalte veröffentlicht werden,
die nicht zur öffentlichen Historie gehören. Alle Skripte nennen bewusst
nur den Branch `main` — sie enthalten keine internen Bezeichnungen und
dürfen deshalb im öffentlichen Repository liegen.

| Datei | Zweck |
|---|---|
| `tag-release.sh` | legt einen Release-Tag **immer auf `main`** an und prüft ihn |
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

## Grenzen

- `git push --no-verify` umgeht den Hook.
- Der Hook prüft nur, was gerade gepusht wird. Er kann nicht verhindern,
  dass jemand einen anderen Klon ohne Hook verwendet.
- `check-public-refs.sh` braucht Netzwerkzugriff für den Teil zu `origin`.

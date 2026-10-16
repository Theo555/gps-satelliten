#!/usr/bin/env bash
# Synchronisiert main -> netzwerk-fallback (privater Branch).
#
# Nutzung (vom Branch "main" aus):
#   ./sync-private.sh ["Commit-Message"]
#
# 1) Committet offene Änderungen auf "main".
# 2) Mergt "main" in den privaten Branch "netzwerk-fallback".
# 3) Löst den bekannten Konflikt in gps/GpsDataManager.kt automatisch
#    zugunsten des privaten Netzwerk-Fallbacks auf.
# 4) Wechselt am Ende zurück zu "main".
set -euo pipefail

cd "$(dirname "$0")"

PUBLIC="main"
PRIVATE="netzwerk-fallback"
FALLBACK_FILE="app/src/main/java/de/gpsradar/satellites/gps/GpsDataManager.kt"

current=$(git rev-parse --abbrev-ref HEAD)
if [ "$current" != "$PUBLIC" ]; then
  echo "Fehler: Skript muss auf '$PUBLIC' laufen (aktuell: '$current')." >&2
  exit 1
fi

# 1) Offene Änderungen (inkl. neuer Dateien) auf main committen
if [ -n "$(git status --porcelain)" ]; then
  msg="${1:-Update}"
  echo ">> Änderungen auf $PUBLIC werden committet: '$msg'"
  git add -A
  git commit -m "$msg"
else
  echo ">> $PUBLIC ist sauber, nichts zu committen."
fi

# 2) In den privaten Branch mergen
echo ">> Wechsel zu $PRIVATE und merge $PUBLIC ..."
git checkout "$PRIVATE"

if git merge "$PUBLIC"; then
  echo ">> Merge sauber."
else
  echo ">> Konflikt beim Merge. Privaten Fallback in $FALLBACK_FILE behalten."
  git checkout --ours -- "$FALLBACK_FILE" 2>/dev/null || true
  git add "$FALLBACK_FILE"
  remaining=$(git diff --name-only --diff-filter=U || true)
  if [ -n "$remaining" ]; then
    echo ">> Zusätzliche Konflikte — bitte manuell lösen:" >&2
    echo "$remaining" >&2
    exit 1
  fi
  git commit --no-edit
  echo ">> Konflikt gelöst, Merge abgeschlossen."
fi

# 3) Zurück zu main
if ! grep -q "NETWORK_PROVIDER" "$FALLBACK_FILE"; then
  echo ">> WARNUNG: NETWORK_PROVIDER fehlt in $FALLBACK_FILE — Fallback manuell prüfen!" >&2
fi
echo ">> Zurück zu $PUBLIC."
git checkout "$PUBLIC"
echo ">> Fertig: $PRIVATE ist auf dem Stand von $PUBLIC."

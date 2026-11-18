#!/usr/bin/env bash
#
# Prüft die Schichtgrenzen zwischen den drei Paketen.
#
# Regel: `model/` ist die unterste Schicht. Es beschreibt die Daten und die
# Rechnung, kennt aber weder Oberfläche noch Hardware noch Dateizugriff — nur so
# lässt es sich in gewöhnlichen Unit-Tests ohne Gerät prüfen. `gps/` spricht mit
# der Hardware und darf deshalb nicht auf `ui/` zugreifen.
#
#   Fehler (Exit 1): verbotener Import in model/ oder gps/
#   Ausgabe        : Datei, Zeile und der gefundene Import
#
# Der Anlass stand am 18.11.2026: `Constellation` trug eine Compose-Farbe
# (`badgeColor`), das Modell hing damit am UI-Framework. Die Farbe liegt jetzt
# in `ui/theme/ConstellationColor.kt`.
#
# Bewusst erlaubt und hier begründet:
#   androidx.annotation.StringRes        Ressourcen-Kennung, kein Android-Lauf
#   androidx.core.location.GnssStatus*   nur Typ-Kennungen des Systems
#   android.util.Xml                     Pull-Parser ohne Context (WaypointCodec);
#                                        dessen Tests laufen mit Robolectric
#
# Aktivieren:  git config core.hooksPath tools/git-hooks
# Umgehen:     git commit --no-verify   (nur wenn man weiß, was man tut)

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

MODELL="app/src/main/java/de/gpsradar/satellites/model"
HARDWARE="app/src/main/java/de/gpsradar/satellites/gps"

status=0

pruefe() {
  local ordner=$1 muster=$2 was=$3
  [ -d "${ordner}" ] || return 0
  local treffer
  treffer=$(grep -rnE "^import (${muster})" "${ordner}" --include=*.kt || true)
  if [ -n "${treffer}" ]; then
    echo "check-architecture: ${was}" >&2
    printf '%s\n' "${treffer}" | sed "s|${ordner}/||; s/^/  /" >&2
    status=1
  fi
}

# model/ darf nichts aus der Oberfläche, dem Dateizugriff oder der Hardware holen.
pruefe "${MODELL}" \
  'androidx\.compose\.|android\.content\.|android\.app\.|android\.location\.|android\.os\.|android\.graphics\.|android\.view\.|android\.widget\.' \
  "model/ greift auf Oberfläche, Dateizugriff oder Hardware zu — das Modell muss Android-frei bleiben."

# model/ ist die unterste Schicht und kennt die darüberliegenden nicht.
pruefe "${MODELL}" \
  'de\.gpsradar\.satellites\.(ui|gps)\.' \
  "model/ importiert aus ui/ oder gps/ — die Abhängigkeit muss andersherum laufen."

# gps/ spricht mit der Hardware und darf keine Darstellung kennen.
pruefe "${HARDWARE}" \
  'androidx\.compose\.|de\.gpsradar\.satellites\.ui\.' \
  "gps/ importiert aus ui/ oder aus Compose — Hardware und Darstellung bleiben getrennt."

if [ "${status}" -eq 0 ]; then
  echo "check-architecture: Schichtgrenzen in Ordnung."
fi

exit "${status}"

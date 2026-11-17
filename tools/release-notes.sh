#!/usr/bin/env bash
#
# Baut den Text für ein GitHub-Release aus den beiden Changelogs.
#
#   tools/release-notes.sh 19              Text auf die Ausgabe
#   tools/release-notes.sh 19 --url v1.3.14  vorausgefüllte Release-Adresse
#
# Warum es das gibt (17.11.2026): Die Notizen zu 1.3.14 waren nur deutsch,
# obwohl die README zweisprachig ist und die Changelogs für F-Droid längst
# in beiden Sprachen vorliegen. Es fehlte kein Inhalt, nur der Griff zum
# zweiten Ordner – genau die Sorte Schritt, die man vergisst.
#
# Die deutschen Notizen stehen vorn (Hauptpublikum), darunter der englische
# Teil unter "### English" – so wie bei den Releases bis 1.3.13.
set -euo pipefail

cd "$(dirname "$0")/.."

if [ $# -lt 1 ]; then
  echo "Aufruf: tools/release-notes.sh <versionCode> [--url <tag>]" >&2
  echo "Beispiel: tools/release-notes.sh 19 --url v1.3.14" >&2
  exit 2
fi

CODE="$1"
DE="fastlane/metadata/android/de-DE/changelogs/${CODE}.txt"
EN="fastlane/metadata/android/en-US/changelogs/${CODE}.txt"

for f in "$DE" "$EN"; do
  if [ ! -f "$f" ]; then
    echo "FEHLER: ${f} fehlt." >&2
    exit 2
  fi
done

# Erste Zeile ist die Versionsnummer – im Release-Text steht sie schon im Titel.
strip_version() {
  tail -n +2 "$1" | sed -e '/./,$!d'
}

BODY="$(strip_version "$DE")

### English

$(strip_version "$EN")"

if [ "${2:-}" = "--url" ]; then
  if [ $# -lt 3 ]; then
    echo "FEHLER: --url braucht einen Tag, z. B. --url v1.3.14" >&2
    exit 2
  fi
  TAG="$3"
  # Die Adresse füllt Tag, Titel und Notizen vor – es bleibt ein Klick auf
  # "Publish release". Ein Token für die API gibt es auf diesem Rechner nicht.
  REPO_URL="$(git remote get-url origin | sed -E 's#^.*github\.com[:/]##; s#\.git$##')"
  BODY="$BODY" python3 - "$TAG" "$REPO_URL" <<'PY'
import os
import sys
from urllib.parse import quote

tag, repo = sys.argv[1], sys.argv[2]
body = os.environ["BODY"].strip()
title = tag[1:] if tag.startswith("v") else tag
print(f"https://github.com/{repo}/releases/new"
      f"?tag={tag}&title={quote(title)}&body={quote(body)}")
PY
else
  printf '%s\n' "$BODY"
fi

#!/usr/bin/env bash
#
# Prüft, dass screenshots/ und README.md zusammenpassen.
#
# Regel: screenshots/ enthält ausschließlich Bilder, die in der README
# verwendet werden. Alles andere (Testaufnahmen, Austausch, Entwürfe)
# gehört nach _austausch/.
#
#   Fehler  (Exit 1): in der README verlinkt, aber Datei fehlt
#   Warnung (Exit 0): Datei liegt in screenshots/, wird aber nicht verlinkt
#
# Wird vom pre-commit-Hook aufgerufen.

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

README="README.md"
DIR="screenshots"
status=0

if [ ! -f "${README}" ] || [ ! -d "${DIR}" ]; then
  echo "check-screenshots: ${README} oder ${DIR} fehlt." >&2
  exit 1
fi

referenced=$(grep -o "screenshots/[A-Za-z0-9._-]*\.jpg" "${README}" \
  | sed 's|^screenshots/||' | grep -v '^$' | sort -u)
present=$(find "${DIR}" -maxdepth 1 -type f -name '*.jpg' -printf '%f\n' \
  | grep -v '^$' | sort -u)

missing=$(comm -23 <(printf '%s\n' "${referenced}") <(printf '%s\n' "${present}"))
extra=$(comm -13 <(printf '%s\n' "${referenced}") <(printf '%s\n' "${present}"))

if [ -n "${missing}" ]; then
  echo "check-screenshots: in der README verlinkt, aber nicht vorhanden:" >&2
  printf '%s\n' "${missing}" | sed 's/^/  /' >&2
  status=1
fi

if [ -n "${extra}" ]; then
  echo "check-screenshots: in ${DIR}/, aber nirgends in der README verlinkt" >&2
  echo "  (gehört das Bild wirklich dorthin? Sonst nach _austausch/ verschieben)" >&2
  printf '%s\n' "${extra}" | sed 's/^/  /' >&2
fi

exit "${status}"

#!/usr/bin/env bash
#
# Prüft, ob alle veröffentlichten Refs ausschließlich auf 'main' zurückgehen.
#
#   tools/check-public-refs.sh
#
# Rückgabewert 0 = alles in Ordnung, 1 = Problem gefunden, 2 = Voraussetzung fehlt.
# Nennt bewusst keine Branch-Namen außer 'main' und kann daher öffentlich sein.

set -uo pipefail

cd "$(dirname "$0")/.." || exit 2

BASE="main"
status=0

if ! git rev-parse --verify --quiet "${BASE}^{commit}" >/dev/null; then
  echo "FEHLER: Branch '${BASE}' nicht gefunden." >&2
  exit 2
fi

echo "== Lokale Tags =="
tags=$(git tag --list)
if [ -z "${tags}" ]; then
  echo "  (keine)"
else
  while read -r t; do
    [ -z "${t}" ] && continue
    c=$(git rev-parse "${t}^{commit}")
    if git merge-base --is-ancestor "${c}" "${BASE}"; then
      printf '  ok       %-12s %s\n' "${t}" "${c:0:8}"
    else
      printf '  FEHLER   %-12s %s  (nicht von %s erreichbar)\n' "${t}" "${c:0:8}" "${BASE}"
      status=1
    fi
  done <<< "${tags}"
fi

echo "== Entfernte Refs (origin) =="
if ! git remote get-url origin >/dev/null 2>&1; then
  echo "  (kein Remote 'origin' vorhanden — übersprungen)"
else
  remote=$(git ls-remote origin) || {
    echo "  FEHLER: 'git ls-remote origin' fehlgeschlagen (Netz?)" >&2
    status=1
    remote=""
  }
  if [ -z "${remote}" ]; then
    echo "  (keine Refs gelesen)"
  else
    while read -r sha ref; do
      [ -z "${ref}" ] && continue
      case "${ref}" in
        HEAD) continue ;;
        *"^{}") continue ;;
        "refs/heads/${BASE}")
          printf '  ok       %-34s %s\n' "${ref}" "${sha:0:8}"
          ;;
        refs/tags/*)
          peeled=$(git ls-remote origin "${ref}^{}" | cut -f1)
          [ -z "${peeled}" ] && peeled="${sha}"
          if ! git cat-file -e "${peeled}^{commit}" >/dev/null 2>&1; then
            printf '  WARNUNG  %-34s %s  (Objekt lokal nicht vorhanden, nicht prüfbar)\n' "${ref}" "${peeled:0:8}"
          elif git merge-base --is-ancestor "${peeled}" "${BASE}"; then
            printf '  ok       %-34s %s\n' "${ref}" "${peeled:0:8}"
          else
            printf '  FEHLER   %-34s %s  (nicht von %s erreichbar)\n' "${ref}" "${peeled:0:8}" "${BASE}"
            status=1
          fi
          ;;
        *)
          printf '  FEHLER   %-34s %s  (unerwarteter Ref)\n' "${ref}" "${sha:0:8}"
          status=1
          ;;
      esac
    done <<< "${remote}"
  fi
fi

echo
if [ "${status}" -eq 0 ]; then
  echo "Ergebnis: alles in Ordnung."
else
  echo "Ergebnis: PROBLEME gefunden — nicht veröffentlichen, bevor das geklärt ist."
fi

exit "${status}"

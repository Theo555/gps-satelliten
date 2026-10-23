#!/usr/bin/env bash
#
# Erstellt einen Release-Tag immer auf 'main' — nie auf dem gerade
# ausgecheckten Branch. Prüft den Tag anschließend.
#
#   tools/tag-release.sh v1.4.0
#
# Gepusht wird nicht automatisch; das bleibt eine bewusste Entscheidung.

set -euo pipefail

cd "$(dirname "$0")/.."

if [ $# -ne 1 ]; then
  echo "Aufruf: tools/tag-release.sh v1.4.0" >&2
  exit 2
fi

TAG="$1"

if ! git rev-parse --verify --quiet "main^{commit}" >/dev/null; then
  echo "FEHLER: Branch 'main' nicht gefunden." >&2
  exit 2
fi

if git rev-parse --verify --quiet "refs/tags/${TAG}" >/dev/null; then
  echo "FEHLER: Tag '${TAG}' existiert bereits." >&2
  exit 2
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "FEHLER: Arbeitsverzeichnis ist nicht sauber." >&2
  exit 2
fi

COMMIT=$(git rev-parse "main^{commit}")
NAME=$(git show "main:app/build.gradle.kts" | sed -n 's/.*versionName = "\(.*\)".*/\1/p' | head -1)

git tag -a "${TAG}" main -m "Version ${TAG#v}"

echo "Tag ${TAG} -> ${COMMIT:0:8} (main)"
echo "versionName im Tag: ${NAME:-unbekannt}"

if git merge-base --is-ancestor "${TAG}^{commit}" main; then
  echo "Prüfung: Tag liegt auf main."
else
  echo "FEHLER: Tag liegt NICHT auf main." >&2
  exit 1
fi

echo
echo "Nächster Schritt (bewusst manuell):  git push origin ${TAG}"

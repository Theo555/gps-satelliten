#!/usr/bin/env bash
#
# Prüft, dass die Screenshot-Ordner und die READMEs zusammenpassen.
#
# Regel: screenshots/ (deutsch) und screenshots/en/ (englisch) enthalten
# ausschließlich Bilder, die in der jeweiligen README verwendet werden.
# Alles andere (Testaufnahmen, Austausch, Entwürfe) gehört nach _austausch/.
#
#   Fehler  (Exit 1): in der README verlinkt, aber Datei fehlt; fastlane
#                     weicht vom englischen Satz ab
#   Warnung (Exit 0): Datei liegt im Ordner, wird aber nicht verlinkt
#
# Wird vom pre-commit-Hook aufgerufen.

set -uo pipefail
cd "$(dirname "$0")/.." || exit 1

status=0

check_readme() {
  local readme=$1 dir=$2
  if [ ! -f "${readme}" ] || [ ! -d "${dir}" ]; then
    echo "check-screenshots: ${readme} oder ${dir} fehlt." >&2
    status=1
    return
  fi

  local referenced present missing extra
  referenced=$(grep -o "${dir}/[A-Za-z0-9._-]*\.jpg" "${readme}" \
    | sed "s|^${dir}/||" | grep -v '^$' | sort -u)
  present=$(find "${dir}" -maxdepth 1 -type f -name '*.jpg' -printf '%f\n' \
    | grep -v '^$' | sort -u)

  missing=$(comm -23 <(printf '%s\n' "${referenced}") <(printf '%s\n' "${present}"))
  extra=$(comm -13 <(printf '%s\n' "${referenced}") <(printf '%s\n' "${present}"))

  if [ -n "${missing}" ]; then
    echo "check-screenshots: in ${readme} verlinkt, aber nicht vorhanden:" >&2
    printf '%s\n' "${missing}" | sed 's/^/  /' >&2
    status=1
  fi

  if [ -n "${extra}" ]; then
    echo "check-screenshots: in ${dir}/, aber nirgends in ${readme} verlinkt" >&2
    echo "  (gehört das Bild wirklich dorthin? Sonst nach _austausch/ verschieben)" >&2
    printf '%s\n' "${extra}" | sed 's/^/  /' >&2
  fi
}

# Deutsch: README.md nutzt screenshots/, englisch: README.en.md nutzt screenshots/en/
check_readme "README.md" "screenshots"
check_readme "README.en.md" "screenshots/en"

# F-Droid nutzt die englischen Bilder (fastlane). Abweichungen sollen auffallen,
# damit README und F-Droid nicht auseinanderlaufen.
EN_DIR="screenshots/en"
FASTLANE="fastlane/metadata/android/en-US/images/phoneScreenshots"
if [ -d "${FASTLANE}" ]; then
  while IFS= read -r name; do
    [ -n "${name}" ] || continue
    target=$(find "${FASTLANE}" -maxdepth 1 -type f -name "*-${name}" | head -1)
    if [ -z "${target}" ]; then
      echo "check-screenshots: ${name} fehlt in ${FASTLANE}" >&2
      status=1
    elif ! cmp -s "${EN_DIR}/${name}" "${target}"; then
      echo "check-screenshots: ${target} ist nicht identisch mit ${EN_DIR}/${name}" >&2
      status=1
    fi
  done <<< "$(find "${EN_DIR}" -maxdepth 1 -type f -name '*.jpg' -printf '%f\n' | sort)"

  while IFS= read -r path; do
    [ -n "${path}" ] || continue
    base=$(basename "${path}")
    name=${base#*-}
    if [ ! -f "${EN_DIR}/${name}" ]; then
      echo "check-screenshots: ${path} hat kein Gegenstück in ${EN_DIR}/" >&2
    fi
  done <<< "$(find "${FASTLANE}" -maxdepth 1 -type f -name '*.jpg')"
else
  echo "check-screenshots: ${FASTLANE} fehlt (F-Droid hätte keine Screenshots)" >&2
fi

exit "${status}"

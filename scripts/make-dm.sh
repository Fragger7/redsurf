#!/usr/bin/env bash
# Builds an ART dex-metadata (.dm) file from the baseline profile AGP packed into an APK.
# A .dm is a zip holding primary.prof (+ primary.profm); installing it next to the APK through
# PackageInstaller makes ART compile the profile at install time instead of waiting for idle
# dexopt (PERFORMANCE_MAX.md item 4).
#
# Usage: scripts/make-dm.sh <app.apk> <out.dm>
# Exits 1 (and writes nothing) if the APK carries no baseline profile.
set -euo pipefail

apk="${1:?usage: make-dm.sh <app.apk> <out.dm>}"
out="${2:?usage: make-dm.sh <app.apk> <out.dm>}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

unzip -q -o -j "$apk" assets/dexopt/baseline.prof -d "$work/p" \
  || { echo "make-dm: no assets/dexopt/baseline.prof in $apk" >&2; exit 1; }
mv "$work/p/baseline.prof" "$work/primary.prof"
if unzip -q -o -j "$apk" assets/dexopt/baseline.profm -d "$work/p" 2>/dev/null; then
  mv "$work/p/baseline.profm" "$work/primary.profm"
fi

rm -f "$out"
out_abs="$(cd "$(dirname "$out")" && pwd)/$(basename "$out")"
(cd "$work" && zip -q -X "$out_abs" primary.prof $( [ -f primary.profm ] && echo primary.profm ))
echo "make-dm: wrote $out"

#!/usr/bin/env bash
# Builds an ART dex-metadata (.dm) file from the baseline profile AGP packed into an APK.
# A .dm is a zip holding primary.prof; installing it next to the APK through PackageInstaller
# (as "base.apk" + "base.dm") makes ART compile the profile at install time instead of waiting
# for idle dexopt (PERFORMANCE_MAX.md item 4).
#
# The APK carries the profile in format 010 (+ .profm metadata); ART on Android 12+ only reads
# format 015 from a .dm and silently falls back to "verify" otherwise (seen on the TV
# 2026-10-29). ProfileToS.java runs profileinstaller's own transcoder to convert it, so this
# needs javac and the profileinstaller AAR in the Gradle cache (any build of :app fetches it).
#
# Usage: scripts/make-dm.sh <app.apk> <out.dm>
# Exits 1 (and writes nothing) if the APK carries no baseline profile or transcoding fails.
set -euo pipefail

apk="${1:?usage: make-dm.sh <app.apk> <out.dm>}"
out="${2:?usage: make-dm.sh <app.apk> <out.dm>}"
pi_version="1.4.1" # keep in step with tv-native/app/build.gradle.kts
here="$(cd "$(dirname "$0")" && pwd)"
bin="${JAVA_HOME:+$JAVA_HOME/bin/}"

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT

aar="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2" \
  -path "*androidx.profileinstaller/profileinstaller/$pi_version/*" \
  -name "profileinstaller-$pi_version.aar" 2>/dev/null | head -1)"
[ -n "$aar" ] || { echo "make-dm: profileinstaller-$pi_version.aar not in the Gradle cache" >&2; exit 1; }
unzip -q -o "$aar" classes.jar -d "$work"

unzip -q -o -j "$apk" assets/dexopt/baseline.prof assets/dexopt/baseline.profm -d "$work/p" \
  || { echo "make-dm: no baseline.prof/.profm in $apk" >&2; exit 1; }

mkdir -p "$work/cls"
"${bin}javac" -nowarn -d "$work/cls" -cp "$work/classes.jar" "$here/dm/ProfileToS.java" 2>/dev/null \
  || "${bin}javac" -d "$work/cls" -cp "$work/classes.jar" "$here/dm/ProfileToS.java"
"${bin}java" -cp "$work/cls:$work/classes.jar" androidx.profileinstaller.ProfileToS \
  "$work/p/baseline.prof" "$work/p/baseline.profm" "$work/primary.prof"
# "pro\0" + "015\0"
[ "$(od -An -tx1 -N8 "$work/primary.prof" | tr -d ' \n')" = "70726f0030313500" ] \
  || { echo "make-dm: transcoded profile is not format 015" >&2; exit 1; }

rm -f "$out"
out_abs="$(cd "$(dirname "$out")" && pwd)/$(basename "$out")"
(cd "$work" && zip -q -X "$out_abs" primary.prof)
echo "make-dm: wrote $out"

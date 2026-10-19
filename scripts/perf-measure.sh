#!/bin/bash
# Frame-timing measurement for the performance salvage (PERFORMANCE_SALVAGE.md step 2) and the
# TiviMate comparison (TIVIMATE_LEARNINGS.md S2). Same key sequence, same pacing, any package:
#
#   source scripts/perf-measure.sh
#   measure com.redsurf.tv "grid-down" 0.25 DPAD_DOWN x20
#   measure ar.tvplayer.tv "grid-down" 0.25 DPAD_DOWN x20
#
# Args: package, label, seconds between presses, then key tokens (KEY or KEY xN). Keys are sent
# from an on-device shell loop so adb round-trips don't skew the pacing. Prints one line from
# `dumpsys gfxinfo` (frames, janky %, p50/p90/p99 ms, slow-UI-thread count) and appends it to
# $PERF_LOG (default /tmp/redsurf-perf.tsv).

source "$(dirname "${BASH_SOURCE[0]}")/tv-test.sh"
PERF_LOG="${PERF_LOG:-/tmp/redsurf-perf.tsv}"

_expand_keys() {
  local out=() k n
  while [ $# -gt 0 ]; do
    k="$1"; shift
    n=1
    if [[ "${1:-}" =~ ^x([0-9]+)$ ]]; then n="${BASH_REMATCH[1]}"; shift; fi
    for ((i = 0; i < n; i++)); do out+=("KEYCODE_${k#KEYCODE_}"); done
  done
  echo "${out[@]}"
}

measure() {
  local pkg="$1" label="$2" gap="$3"; shift 3
  local keys; keys="$(_expand_keys "$@")"
  ADB shell dumpsys gfxinfo "$pkg" reset >/dev/null
  ADB shell "for k in $keys; do input keyevent \$k; sleep $gap; done"
  sleep 1.5
  ADB shell dumpsys gfxinfo "$pkg" | python3 -c '
import re, sys
pkg, label = sys.argv[1], sys.argv[2]
t = sys.stdin.read()
def g(p):
    m = re.search(p, t)
    return m.group(1) if m else "?"
row = [pkg, label, g(r"Total frames rendered: (\d+)"), g(r"Janky frames: \d+ \(([\d.]+)%\)"),
       g(r"50th percentile: (\d+)ms"), g(r"90th percentile: (\d+)ms"), g(r"99th percentile: (\d+)ms"),
       g(r"Number Slow UI thread: (\d+)")]
print("\t".join(row))
' "$pkg" "$label" | tee -a "$PERF_LOG"
}

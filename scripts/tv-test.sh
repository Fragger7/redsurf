#!/bin/bash
# RedSurf device test helper - source this, then call the functions (WORKFLOW.md "Sprint mode").
#
#   source scripts/tv-test.sh
#   nav_to_settings           # verify-then-act: reaches the Settings pill wherever focus starts
#   key KEYCODE_DPAD_DOWN     # one press, then a short settle (override: key KEYCODE_X 1.0)
#   focused_info              # which node has real focus, by bounds + nearest visible text
#   clearlogs; ...; logs      # app-tag logcat around an action
#   shot /tmp/x.png           # screenshot - diagnosis only, never routine (image cost)
#
# Promoted from the scratch helper Sonnet built mid-sprint-1 after batched raw `input keyevent`
# sequences with fixed sleeps kept dropping presses and assuming a starting pill that wasn't
# there. Every device interaction in a sprint goes through here now; don't hand-roll sequences.
#
# Focus detection: `uiautomator dump` marks the *container* node focused (a Compose Surface has
# no text of its own), so focused_info also reports the nearest visible text as a label. Treat
# that label as a hint - when the focused bounds are huge (a whole content area, e.g. an unbuilt
# PlaceholderScreen's Box) the "nearest text" is meaningless. Trust bounds + logcat over it.

export ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
export PATH="$ANDROID_HOME/platform-tools:$PATH"
D="${REDSURF_DEVICE:-192.172.7.160:35631}"
ADB() { adb -s "$D" "$@"; }
LOG_TAGS="${REDSURF_LOG_TAGS:-SettingsScreen|PlayerScreen|LiveTvScreen|MainViewModel}"

focused_info() {
  ADB shell uiautomator dump /sdcard/window_dump.xml >/dev/null 2>&1
  ADB pull /sdcard/window_dump.xml /tmp/wd_now.xml >/dev/null 2>&1
  python3 - <<'PY'
import re
data = open('/tmp/wd_now.xml').read()
fb = None
for m in re.finditer(r'<node ([^>]*)/?>', data):
    if 'focused="true"' in m.group(1):
        bm = re.search(r'bounds="([^"]*)"', m.group(1))
        fb = bm.group(1)
if not fb:
    print('no focused node found'); raise SystemExit
x1,y1,x2,y2 = [int(v) for v in re.findall(r'-?\d+', fb)]
best=None; bestd=1e9
for m in re.finditer(r'<node [^>]*text="([^"]+)"[^>]*bounds="([^"]*)"', data):
    text, b = m.groups()
    if not text.strip(): continue
    bx1,by1,bx2,by2 = [int(v) for v in re.findall(r'-?\d+', b)]
    d = (((bx1+bx2)/2-(x1+x2)/2)**2+((by1+by2)/2-(y1+y2)/2)**2)**0.5
    if d < bestd: bestd=d; best=text
huge = (x2-x1) > 1000 and (y2-y1) > 500
print('focused bounds=%s nearest_text=%r%s' % (fb, best, '  (huge bounds - label unreliable)' if huge else ''))
PY
}

# Press one key, then settle. Default 0.5s is what sprint 1 found reliable on this Chromecast;
# use 1.0+ after anything that recomposes a whole screen (entering a destination, confirm dialogs).
key() {
  ADB shell input keyevent "$1"
  sleep "${2:-0.5}"
}

# Walks the nav strip to a named pill and presses OK. Verify-then-act: reads where focus actually
# is before each step instead of assuming, so it works from any starting state. UP first because
# from any content area that's the one direction that reliably reaches the strip.
nav_to_pill() {
  local target="$1"
  key KEYCODE_DPAD_UP 1
  local i info
  for i in 1 2 3 4 5 6 7 8; do
    info=$(focused_info)
    [[ "$info" == *"'$target'"* ]] && break
    # Pills are left-to-right: Home, Live TV, Movies, Series, Guide, Search, Settings.
    key KEYCODE_DPAD_RIGHT 0.4
  done
  focused_info
  key KEYCODE_DPAD_CENTER 1
}
nav_to_settings() { nav_to_pill "Settings"; }
nav_to_livetv()   { nav_to_pill "Live TV"; }

logs()      { ADB logcat -d 2>&1 | grep -E "$LOG_TAGS"; }
clearlogs() { ADB logcat -c; }
shot()      { ADB exec-out screencap -p > "${1:-/tmp/shot.png}"; }

# Fresh start: force-stop + relaunch, wait for the first frame.
relaunch() {
  ADB shell am force-stop com.redsurf.tv
  ADB shell am start -n com.redsurf.tv/.MainActivity >/dev/null
  sleep "${1:-4}"
}

# Seed the test playlist through the on-device pairing server. Xtream API by default (seconds);
# pass "m3u" only when the M3U parser itself is what's under test (~3-4 min on this device).
seed_playlist() {
  local mode="${1:-xtream}" url
  url=$(cat ~/.redsurf/test-playlist.url)
  ADB forward tcp:8080 tcp:8080 >/dev/null
  if [[ "$mode" == "m3u" ]]; then
    curl -s -o /dev/null -w "seed (m3u): HTTP %{http_code}\n" \
      --data-urlencode "type=m3u" --data-urlencode "name=Sprint Test Playlist" \
      --data-urlencode "m3u=$url" --data-urlencode "contentType=live" \
      http://127.0.0.1:8080/submit
  else
    local server user pass
    server=$(echo "$url" | sed -E 's#^(https?://[^/]+).*#\1#')
    user=$(echo "$url"   | sed -E 's#.*[?&]username=([^&]+).*#\1#')
    pass=$(echo "$url"   | sed -E 's#.*[?&]password=([^&]+).*#\1#')
    curl -s -o /dev/null -w "seed (xtream): HTTP %{http_code}\n" \
      --data-urlencode "type=xtream" --data-urlencode "name=Sprint Test Playlist" \
      --data-urlencode "server=$server" --data-urlencode "user=$user" \
      --data-urlencode "pass=$pass" --data-urlencode "contentType=live" \
      http://127.0.0.1:8080/submit
  fi
}

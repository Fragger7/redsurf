#!/bin/bash
# agy-user-remove.sh - undo agy-user-setup.sh completely: the Gemini builder's macOS user (and its
# home: clone, toolchain copies, Gradle caches), the sudo rules, the TV wrapper, the screenshot
# folder, admin's git safe.directory entry, and the lock on admin's home.
#
#   sudo scripts/agy-user-remove.sh       # run by the user, in Terminal
#
# Safe to re-run: each step skips what is already gone. Written by the lead, 2026-10-29.
set -uo pipefail

[ "$(id -u)" = 0 ] || { echo "Run with sudo: sudo $0"; exit 1; }
ADMIN="${SUDO_USER:-}"
[ "$ADMIN" = admin ] || { echo "Run from the admin account with sudo (SUDO_USER=$ADMIN)"; exit 1; }
U=agy
step() { printf '\n== %s\n' "$*"; }
cd /

step "1/6 stop the TV remote-watch, if it's still running"
ADBBIN=/Users/$ADMIN/Library/Android/sdk/platform-tools/adb
D="$(sudo -u "$ADMIN" "$ADBBIN" devices 2>/dev/null | awk -F'\t' '($1 ~ /^adb-0C101HFDD16JPN.*_adb-tls-connect/ || $1=="192.172.7.160:35631") && $2=="device"{print $1; exit}')"
if [ -n "$D" ]; then
  sudo -u "$ADMIN" "$ADBBIN" -s "$D" shell "pkill -x getevent; rm -f /data/local/tmp/redsurf-human.log" >/dev/null 2>&1
  echo "ok"
else
  echo "TV not connected - skipping (nothing runs there after a reboot anyway)"
fi

step "2/6 sudo rules + the adb-server firewall rule"
rm -f /etc/sudoers.d/redsurf-agy
launchctl bootout system/com.redsurf.agy-pf 2>/dev/null
rm -f /Library/LaunchDaemons/com.redsurf.agy-pf.plist /etc/pf.anchors/redsurf-agy
pfctl -a com.apple/redsurf-agy -F all 2>/dev/null
[ -s /var/run/redsurf-agy-pf.token ] && pfctl -X "$(cat /var/run/redsurf-agy-pf.token)" 2>/dev/null
rm -f /var/run/redsurf-agy-pf.token
echo "ok"

step "3/6 TV wrapper + screenshot folder"
rm -f /usr/local/libexec/redsurf-tv
rmdir /usr/local/libexec 2>/dev/null || true    # only if empty - it didn't exist before setup
rm -rf /Users/Shared/redsurf-tv
echo "ok"

step "4/6 user '$U' and its home"
if id "$U" >/dev/null 2>&1; then
  pkill -u "$U" 2>/dev/null; sleep 1
  sysadminctl -deleteUser "$U" && echo "ok"
else
  echo "already gone"
fi
rm -rf "/Users/$U"                              # in case deleteUser kept the home

step "5/6 admin's git safe.directory entry"
sudo -u "$ADMIN" git config --global --unset-all safe.directory "^/Users/$U/RedSurf-agy\$" 2>/dev/null
echo "ok"

step "6/6 unlock admin's home (back to how it was: 755)"
chmod 755 "/Users/$ADMIN" && echo "ok"

echo
fail=0
id "$U" >/dev/null 2>&1 && { echo "FAIL: user $U still exists"; fail=1; }
[ -e "/Users/$U" ] && { echo "FAIL: /Users/$U still exists"; fail=1; }
[ -e /etc/sudoers.d/redsurf-agy ] && { echo "FAIL: sudo rule still there"; fail=1; }
[ -e /usr/local/libexec/redsurf-tv ] && { echo "FAIL: wrapper still there"; fail=1; }
[ -e /Library/LaunchDaemons/com.redsurf.agy-pf.plist ] && { echo "FAIL: firewall daemon still there"; fail=1; }
[ $fail = 0 ] && echo "ALL REMOVED" || { echo "SOMETHING WAS LEFT - tell Claude"; exit 1; }

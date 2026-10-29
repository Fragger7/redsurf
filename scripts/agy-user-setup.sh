#!/bin/bash
# agy-user-setup.sh - give the Gemini builder its own macOS user, so it can't read admin's keys,
# logins or files, can't push to GitHub, and reaches the TV only through redsurf-tv's allowlist.
#
#   sudo scripts/agy-user-setup.sh        # run by the user, in Terminal (asks for passwords)
#   sudo scripts/agy-user-remove.sh       # undoes all of it
#
# Safe to re-run: it skips what already exists, and re-installs redsurf-tv from the repo (that's
# how a wrapper change goes live). Written by the lead, 2026-10-29.
set -euo pipefail

[ "$(id -u)" = 0 ] || { echo "Run with sudo: sudo $0"; exit 1; }
ADMIN="${SUDO_USER:-}"
[ "$ADMIN" = admin ] || { echo "Run from the admin account with sudo (SUDO_USER=$ADMIN)"; exit 1; }
REPO="$(cd "$(dirname "$0")/.." && pwd)"
U=agy
H=/Users/$U
step() { printf '\n== %s\n' "$*"; }
cd /                                           # agy can't enter admin's folders once they're locked

step "1/8 user '$U'"
if id "$U" >/dev/null 2>&1; then
  echo "exists - skipping"
else
  echo "Choose a password for the new '$U' user (you'll rarely need it):"
  sysadminctl -addUser "$U" -fullName "Gemini builder" -shell /bin/zsh -home "$H" -password -
  createhomedir -c -u "$U" >/dev/null
  dscl . create "/Users/$U" IsHidden 1        # not on the login screen
fi
id "$U" | grep -q '(admin)' && { echo "STOP: $U is an admin - remove that first"; exit 1; }

step "2/8 lock admin's home (other users can't look in)"
chmod 700 "/Users/$ADMIN"
chmod 750 "$H"                                 # admin (group staff) can fetch agy's commits

step "3/8 toolchain copies for $U (no adb)"
sudo -u "$U" mkdir -p "$H/.local/opt" "$H/.local/bin" "$H/Library/Android"
[ -d "$H/.local/opt/jdk17" ] || ditto "/Users/$ADMIN/.local/opt/jdk17" "$H/.local/opt/jdk17"
rsync -a --delete --exclude platform-tools "/Users/$ADMIN/Library/Android/sdk/" "$H/Library/Android/sdk/"
install -m 755 "/Users/$ADMIN/.local/bin/agy" "$H/.local/bin/agy"
chown -R "$U:staff" "$H/.local" "$H/Library/Android"

step "4/8 $U's own clone (public HTTPS, no GitHub login - it cannot push)"
if [ ! -d "$H/RedSurf-agy/.git" ]; then
  sudo -u "$U" git clone -q https://github.com/Fragger7/redsurf.git "$H/RedSurf-agy"
fi
sudo -u "$U" touch "$H/RedSurf-agy/.builder-worktree"
sudo -u "$U" git -C "$H/RedSurf-agy" config core.hooksPath .githooks
sudo -u "$ADMIN" git config --global --get-all safe.directory | grep -qx "$H/RedSurf-agy" ||
  sudo -u "$ADMIN" git config --global --add safe.directory "$H/RedSurf-agy"

step "5/8 install the TV wrapper (root-owned; $U can't change it)"
mkdir -p /usr/local/libexec && chown root:wheel /usr/local/libexec && chmod 755 /usr/local/libexec
install -o root -g wheel -m 755 "$REPO/scripts/redsurf-tv" /usr/local/libexec/redsurf-tv
mkdir -p /Users/Shared/redsurf-tv
chown "$ADMIN:staff" /Users/Shared/redsurf-tv && chmod 755 /Users/Shared/redsurf-tv

step "6/8 sudo rules"
TMP=$(mktemp)
cat > "$TMP" <<EOF
# RedSurf: agy may run the TV wrapper as admin, nothing else. admin may act as agy (to launch it).
$U ALL=($ADMIN) NOPASSWD: /usr/local/libexec/redsurf-tv
$ADMIN ALL=($U) NOPASSWD: ALL
EOF
visudo -cf "$TMP" >/dev/null || { echo "STOP: sudoers rule failed validation"; rm -f "$TMP"; exit 1; }
install -o root -g wheel -m 440 "$TMP" /etc/sudoers.d/redsurf-agy
rm -f "$TMP"

step "7/8 $U's shell: PATH + the redsurf-tv shim"
sudo -u "$U" tee "$H/.local/bin/redsurf-tv" >/dev/null <<'EOF'
#!/bin/sh
exec sudo -n -u admin /usr/local/libexec/redsurf-tv "$@"
EOF
chmod 755 "$H/.local/bin/redsurf-tv"
grep -qs '.local/bin' "$H/.zshenv" || sudo -u "$U" sh -c "echo 'export PATH=\"\$HOME/.local/bin:\$PATH\"' >> '$H/.zshenv'"

step "8/8 checks"
fail=0
sudo -u "$U" test -r "/Users/$ADMIN/.redsurf" 2>/dev/null && { echo "FAIL: $U can read ~/.redsurf"; fail=1; }
sudo -u "$U" ls "/Users/$ADMIN" >/dev/null 2>&1 && { echo "FAIL: $U can list admin's home"; fail=1; }
sudo -u "$U" test -e "$H/Library/Android/sdk/platform-tools" && { echo "FAIL: $U has adb"; fail=1; }
sudo -u "$U" sudo -n -u "$ADMIN" /bin/ls >/dev/null 2>&1 && { echo "FAIL: $U can sudo beyond the wrapper"; fail=1; }
sudo -u "$U" sudo -n -u "$ADMIN" /usr/local/libexec/redsurf-tv version >/dev/null 2>&1 \
  && echo "ok: wrapper reachable (TV answered)" || echo "ok: wrapper runs (TV not connected right now - fine)"
[ $fail = 0 ] && echo "ALL CHECKS PASSED" || { echo "SOME CHECKS FAILED - tell Claude"; exit 1; }
echo
echo "Next: sign Gemini in as $U (Claude will tell you how)."

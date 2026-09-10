#!/bin/bash
# RedSurf signing setup — run this yourself; it never prints your passphrase.
#
#   1. Generates the permanent release keystore
#   2. Writes a local signing.properties for release builds (outside the repo)
#   3. Builds an AES-256 encrypted backup disk image containing the keystore + credentials
#   4. Uploads the keystore + passwords to GitHub Actions secrets via `gh`
#
# One passphrase protects both the keystore and the backup image — one thing to remember,
# one thing to lose. Put it in Keychain Access -> File -> New Secure Note the moment you pick it.

set -u
KEYDIR="$HOME/.redsurf/keys"
KEYSTORE="$KEYDIR/redsurf-release.jks"
ALIAS="redsurf"
BACKUP="$HOME/Desktop/RedSurf-Signing-Backup.dmg"
JAVA_HOME="${JAVA_HOME:-$HOME/.local/opt/jdk17/Contents/Home}"
KEYTOOL="$JAVA_HOME/bin/keytool"

command -v "$KEYTOOL" >/dev/null 2>&1 || { echo "ERROR: keytool not found at $KEYTOOL"; exit 1; }

if [ -f "$KEYSTORE" ]; then
  echo "A keystore already exists at $KEYSTORE"
  echo "Refusing to overwrite it — that would permanently break updates for any installed RedSurf."
  echo "Delete it deliberately first if you really mean to start over."
  exit 1
fi

echo "=============================================="
echo " RedSurf release keystore setup"
echo "=============================================="
echo
echo "Choose a passphrase you will REMEMBER. If it is lost, no future build can ever"
echo "update an installed RedSurf — every device must uninstall and reinstall."
echo "A memorable passphrase beats a strong one you lose."
echo
printf "Passphrase: "; read -rs PW1; echo
printf "Confirm:    "; read -rs PW2; echo
[ "$PW1" = "$PW2" ] || { echo "ERROR: passphrases do not match."; exit 1; }
[ ${#PW1} -ge 8 ] || { echo "ERROR: must be at least 8 characters (keystore minimum)."; exit 1; }
export RS_PW="$PW1"; unset PW1 PW2

mkdir -p "$KEYDIR"; chmod 700 "$HOME/.redsurf" "$KEYDIR"

echo
echo "--> Generating keystore (RSA 2048, valid 10000 days / ~27 years)..."
"$KEYTOOL" -genkeypair -v \
  -keystore "$KEYSTORE" \
  -storepass:env RS_PW -keypass:env RS_PW \
  -alias "$ALIAS" -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=RedSurf, OU=RedSurf, O=RedSurf, L=Unknown, ST=Unknown, C=US" >/dev/null 2>&1 \
  || { echo "ERROR: keytool failed."; exit 1; }
chmod 600 "$KEYSTORE"
echo "    created: $KEYSTORE"

FINGERPRINT=$("$KEYTOOL" -list -v -keystore "$KEYSTORE" -storepass:env RS_PW -alias "$ALIAS" 2>/dev/null \
  | grep "SHA256:" | head -1 | sed 's/.*SHA256: //')
echo "    SHA-256: $FINGERPRINT"

echo
echo "--> Writing local signing.properties (outside the repo, never committed)..."
cat > "$KEYDIR/signing.properties" <<PROPS
storeFile=$KEYSTORE
storePassword=$RS_PW
keyAlias=$ALIAS
keyPassword=$RS_PW
PROPS
chmod 600 "$KEYDIR/signing.properties"
echo "    created: $KEYDIR/signing.properties"

echo
echo "--> Building encrypted backup disk image..."
STAGE=$(mktemp -d)
cp "$KEYSTORE" "$STAGE/"
cat > "$STAGE/CREDENTIALS.txt" <<INFO
RedSurf release signing credentials
Created: $(date)

Keystore file : redsurf-release.jks
Key alias     : $ALIAS
Passphrase    : $RS_PW
  (same passphrase unlocks the keystore, the key, and this disk image)

SHA-256 fingerprint:
$FINGERPRINT

WHAT THIS IS
This keystore is RedSurf's permanent identity. Android will only install an update over an
existing RedSurf if the new APK is signed with THIS key. If this file or its passphrase is
lost, updates become impossible forever - every device must uninstall (losing playlists and
favourites) and reinstall.

KEEP TWO COPIES IN DIFFERENT PLACES.
e.g. one in iCloud/Google Drive, one on a USB stick. Never commit it to git - the repo is public.
INFO
rm -f "$BACKUP"
printf '%s' "$RS_PW" | hdiutil create -encryption AES-256 -stdinpass \
  -volname "RedSurf Signing" -srcfolder "$STAGE" -format UDZO "$BACKUP" >/dev/null 2>&1 \
  && echo "    created: $BACKUP" || echo "    WARNING: disk image creation failed - back the keystore up manually!"
rm -rf "$STAGE"

echo
echo "--> Uploading to GitHub Actions secrets..."
if command -v gh >/dev/null 2>&1; then
  base64 -i "$KEYSTORE" | gh secret set KEYSTORE_BASE64 --repo Fragger7/redsurf >/dev/null 2>&1 && echo "    set KEYSTORE_BASE64"
  printf '%s' "$RS_PW"  | gh secret set KEYSTORE_PASSWORD --repo Fragger7/redsurf >/dev/null 2>&1 && echo "    set KEYSTORE_PASSWORD"
  printf '%s' "$ALIAS"  | gh secret set KEY_ALIAS        --repo Fragger7/redsurf >/dev/null 2>&1 && echo "    set KEY_ALIAS"
  printf '%s' "$RS_PW"  | gh secret set KEY_PASSWORD     --repo Fragger7/redsurf >/dev/null 2>&1 && echo "    set KEY_PASSWORD"
else
  echo "    SKIPPED: gh not found."
fi

unset RS_PW
echo
echo "=============================================="
echo " Done. Your next three steps:"
echo "=============================================="
echo " 1. Put the passphrase in Keychain Access -> File -> New Secure Note, right now."
echo " 2. Copy $BACKUP to TWO places (cloud + USB stick)."
echo " 3. Tell Claude it's done - the fingerprint above is safe to share."
echo

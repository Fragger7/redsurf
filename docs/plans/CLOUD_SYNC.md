# Cloud Sync - brief (draft for the user's review)

**Written 2026-10-20 by Opus (AGENTS.md: "Brief is Opus work when its turn comes - security-
sensitive").** Nothing here is built. Every decision marked **D#** is yours; the rest is the
recommended shape, which you can also overrule. The performance salvage is at the stop point
(PERFORMANCE_SALVAGE.md, night 2), and the prerequisite - the web portal building locally - was
fixed the same day (`cp .env.example .env.local && npm run build`).

## 0. DECIDED - interview with the user, 2026-10-31 (supersedes the open questions in §5)
- **D1 end-to-end** encryption **+ admin escrow**: every user's account key is also sealed to the
  master admin's key, so the admin can read/add/fix/remove any user's playlists; Firebase/Google
  only ever hold ciphertext.
- **D2 live listener** (Firestore SDK; measure APK/PSS before committing, HARDWARE.md).
- **D3:** logging in never deletes local playlists; the TV offers to move them into the account.
- **D4:** keep the LAN phone-entry page for TVs not logged in; retire `pairingSessions`.
- **Accounts:** no public sign-up anywhere - only the admin creates accounts (portal admin panel);
  the app has "Log in" only. **v1 has one admin (the user)**; shape the data so a reseller tier
  (admin > resellers > their users) can come later.
- **Cap:** 3 cloud playlists per normal account; the admin account is unlimited. Enforce outside
  the client (rules + a per-user counter, or a server route) - not UI-only.
- **TV login: both** - code + QR approved on a logged-in phone (default), and email + password
  typed on the TV (fallback; the password-derived key unwraps the account key).
- **Also synced:** Manage Groups (per-playlist category order + hidden), favourites, app settings.
  **Not** recents (a write per zap is the one thing that would strain the free tier).
- **Hosting:** the portal is on Vercel.
- **Free-tier estimate (Spark: ~1 GiB stored, ~50K reads/day, ~20K writes/day):** a TV reads
  about ten small documents per launch (playlists, one groups doc + one favourites doc per
  playlist, one settings doc), then only what changes - ~100 reads/TV/day, so the read budget
  covers hundreds of TVs. Writes are human-paced (a favourite, a setting). Keep each playlist's
  groups in one document (well under the 1 MiB doc limit even for thousands of categories).
- **New TV-side need from the same day's feel-check:** a real Manage Groups screen
  (TESTING_OUTSTANDING.md section O) - build it local-first, then it rides the sync.

## 1. What it is (PRODUCT_VISION.md "credential locker", AGENTS.md roadmap)

An account tied to an email. The web portal adds, edits, enables and disables playlists; every
TV running RedSurf and linked to the account shows exactly that set. **Cloud is truth, the device
is a cache**: playlist *definitions* sync (name, type, server, username, password, content type,
user agent, EPG settings) - never channel or EPG data; each TV re-imports from the provider.

Out of scope for this pass (but the account document is shaped so they can ride the same rails
later): favorites, recents, settings, watch position.

**TiviMate check (standing rule):** TiviMate has no cloud account at all - playlists live on the
device, with backup/restore to a file. So there is no parity target here; this is RedSurf's
differentiator, and the bar is "never worse than TiviMate": linking a TV must take less effort than
typing a playlist on the remote, and a TV with no network must keep working from its cache.

## 2. What exists today (read 2026-10-20)

- **Web portal** (Next.js 15, Firebase Auth email/password, Firestore). `users/{uid}/playlists/{id}`
  holds `{name, server, username, password, type, contentType, isActive, addedAt}`; rules are
  owner-only with a strict schema (`firestore.rules`).
- **Passwords are stored in plain text** in those documents - protected by rules only. AGENTS.md's
  flag: "credentials encrypted per account, not just rules-protected." Not met today.
- `pairingSessions` - two web flows write to it; **nothing reads it** (the TV moved to local
  NanoHttpd pairing). Reads are denied. Decision on record (2026-09-10): revive, don't delete.
- The dashboard also queries a global `family_devices` collection; the rules' catch-all denies it,
  so that listener fails silently. To be replaced by per-user devices (below).
- **TV**: playlists in Room (`PlaylistEntity`: id, name, serverUrl, username, password, type,
  userAgent, epgOffsetHours, macAddress, contentType). Added via on-screen forms or the LAN pairing
  page. No cloud identity, no Firebase SDK.

## 3. Recommended architecture

### 3.1 Linking a TV - serverless device approval
1. The TV signs in to Firebase **anonymously** (its own uid, persisted on the device) and shows a
   short link code + QR (`redsurf.app/link?c=XXXXXX`).
2. The TV writes `linkRequests/{code}` = `{ tvUid, deviceName, createdAt }` (rules: create only by
   that anonymous uid, code format checked, expires after 10 minutes).
3. The user, signed in on the portal, opens the link (or types the code) and approves: the portal
   writes `users/{uid}/devices/{tvUid}` = `{ name, linkedAt }` and marks the request approved.
4. Rules let a uid listed in `users/{uid}/devices` read that user's playlists
   (`exists(/databases/$(db)/documents/users/$(userId)/devices/$(request.auth.uid))`).
5. **Revoke** = delete the device document; the TV loses access on its next read.

Why this shape: no server holding Admin SDK secrets, no custom tokens, every grant is a document
the owner can see and delete. The code is a rendezvous, not a credential - approval needs the
signed-in owner. (Alternative: a server endpoint minting custom tokens. More moving parts, more
secrets, nothing gained here.)

### 3.2 Credentials - **D1: how strongly to protect them**
- **D1-a, end-to-end (recommended).** A random 256-bit account key encrypts each playlist's
  secrets (AES-GCM). The web keeps it wrapped with a key derived from the login password
  (PBKDF2/Argon2), so Google/Firebase only ever hold ciphertext. A newly linked TV receives the
  account key *during approval*, sealed to a public key the TV generated (in the link request) -
  never in plain text in the cloud. Cost: a password reset makes stored credentials unreadable
  (the user re-enters playlists - the portal says so up front); a little more code on both sides.
- **D1-b, server-side encryption.** Ciphertext in Firestore, key in a KMS behind an API route that
  checks the caller's account/device. Protects against a leaked database export, not against the
  service itself; needs a hosted endpoint with secrets.
- **D1-c, rules only (today).** Simplest; does not meet the AGENTS.md requirement.

### 3.3 The TV's sync engine - **D2: Firestore SDK or REST**
- **D2-a, Firestore Android SDK (recommended)** - a real-time listener on the user's playlists
  ("live listener" in the AGENTS.md flags), an offline cache for free. Cost: ~2 MB of APK and some
  memory; measure PSS before/after against HARDWARE.md's ceiling before committing.
- **D2-b, REST on launch + every N minutes** - smallest footprint, changes land within N minutes.

Either way: **sync on launch, honour the last-known state offline, last write wins** (server
timestamps). Cloud changes map onto Room: new → import; edited credentials → re-import; disabled
→ hidden, data kept; deleted → removed with its channels/EPG (the existing delete path).

### 3.4 Data model
```
users/{uid}
  playlists/{playlistId}   name, type, server, username, secrets (ciphertext under D1-a/b),
                           contentType, userAgent?, epgOffsetHours?, isActive, updatedAt
  devices/{deviceUid}      name, linkedAt, lastSeenAt
  (later) settings/…, favorites/…
linkRequests/{code}        tvUid, deviceName, tvPublicKey (D1-a), createdAt, status, expiresAt
```
`playlistId` becomes the Room id too, so a playlist is the same object everywhere. Room gains
`cloudUpdatedAt` and an `origin` (cloud/local) column (+ a migration).

### 3.5 Existing playlists - **D3: what happens to playlists already on a TV when it links**
- **D3-a (recommended):** offer once, "Add these 3 playlists to your account?" - yes uploads them
  (encrypted), no keeps them local-only.
- **D3-b:** the account replaces local playlists.
- **D3-c:** local playlists always stay separate.

### 3.6 pairingSessions and the LAN pairing page - **D4**
The decision on record is "revive". With 3.1, the account *is* the way to send a playlist to a TV
(add it on the portal → every linked TV gets it). Recommendation: keep the LAN page for TVs that
aren't linked (no account needed), retire `pairingSessions` once linking ships. Your call.

## 4. Phases and acceptance criteria (each verified on the TV, per AGENTS.md)

| # | Scope | Done when |
|---|---|---|
| C0 | Firestore emulator + rules unit tests (`@firebase/rules-unit-testing`) for every rule below; fix `family_devices` | tests pass locally and in CI; a non-owner, an unlinked device and a revoked device are all denied |
| C1 | Linking (3.1): TV link screen (code + QR), portal approval page, device list with revoke | a fresh TV links in under a minute with the remote; revoking it cuts access on its next sync |
| C2 | Read-only sync (D2): portal playlists appear on the TV, disabled ones hide, edits re-import | add / edit / disable / delete on the portal each reach the TV; with Wi-Fi off the TV keeps working |
| C3 | Credentials per D1 | no plain-text secret in Firestore (checked by reading raw documents in the console/emulator); a linked TV still plays |
| C4 | Existing playlists per D3; TV-side edits write back (last write wins) | the 3 playlists on the test TV survive linking per the chosen option |
| C5 | Portal polish (TiviMate-calm, branding), Settings rows on the TV ("Account", linked email, sync now, unlink) | the user's feel-check |

Security review before C2 ships: rules tests, code expiry and rate limiting on `linkRequests`, no
secrets in logs, revoke path tested, threat notes (lost TV, leaked code, compromised email).

## 5. What I need from you
1. **D1** - end-to-end (my recommendation), server-side, or rules-only?
2. **D2** - Firestore SDK (recommended) or REST polling?
3. **D3** - what happens to playlists already on a TV when it links?
4. **D4** - keep the LAN pairing page for unlinked TVs and retire `pairingSessions`?
5. Where the portal is hosted today (Vercel? Firebase Hosting?) - the `/link` page lives there.

Once these are answered, C0-C1 are a good first sprint.

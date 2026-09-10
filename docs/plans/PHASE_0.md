# Phase 0 — Make the loop work

**Goal:** a working build-test loop. No new features. Nothing in this phase is user-visible
except that updates start installing.

**Why first:** three releases of RedSurf are signed with three different debug keys, so **no OTA
update can ever install** (`INSTALL_FAILED_UPDATE_INCOMPATIBLE`). Until that is fixed, every
subsequent phase is untestable on the TV. Everything else here is cheap and clears the runway.

**Definition of done:** an APK built by CI installs itself over a previous version on the
Chromecast, via the in-app updater, with no manual step.

---

## 0.1 — Local toolchain (minimal)

Install **only**:
- **Temurin JDK 17** — prebuilt tarball to `~/.local/opt`. **Do not `brew install openjdk@17`**:
  there is no bottle for macOS 13 / Intel and it triggers a multi-hour source compile.
- Android `cmdline-tools`, `platform-tools`, `platforms;android-34`, `build-tools;34.0.0` into
  `~/Library/Android/sdk`. Nothing else — no Studio, no emulator, no extra API levels.
- Write `tv-native/local.properties` with `sdk.dir` (already gitignored).

Versions come from the repo, not from guesswork: AGP 8.2.2, Kotlin 1.9.22, JDK 17, compileSdk 34,
Compose compiler 1.5.8.

**Add the missing Gradle wrapper** (the repo has `gradle-wrapper.properties` but no `gradlew`, so
CI invokes bare `gradle`). Pin to **8.7** to match CI, commit `gradlew` + `gradle-wrapper.jar`,
and switch CI to `./gradlew`. Local and CI then run identical Gradle.

**Acceptance:** `cd tv-native && ./gradlew :app:assembleDebug` succeeds from a clean checkout.

## 0.2 — Fix the generator-script damage

Cheap, mechanical, and it removes real bugs before anyone builds on top of them.

1. **17 broken string interpolations** across 11 files — literal `\$` from bash heredoc
   over-escaping. Mostly logs, but **`backup/BackupManager.kt:27-28` is a real bug**: it looks for
   a file literally named `$dbName-wal`, so backups silently omit the SQLite WAL.
   Find them with: `grep -rn '\\\$' --include="*.kt" tv-native`
2. **Delete one of the two `EpgSyncWorker` classes.** `worker/EpgSyncWorker.kt` is an admitted stub
   ("we simulate the success of the background job"); `sync/EpgSyncWorker.kt` is the real one.
   Delete the stub.
3. **Delete the generator scripts** — `tv_feature_burst*.sh`, `tv_night_shift*.sh`,
   `fix_compile.sh`, `init_tv_native.sh`, `tv_update_docs.sh`. 3,249 lines of bash that emitted
   the Kotlin. They are the cause of (1) and must never be run again.

**Acceptance:** zero matches for `grep -rn '\\\$' --include="*.kt" tv-native`; still builds.

## 0.3 — Release signing (THE critical fix)

1. Generate a keystore **once**, and back it up somewhere permanent:
   ```
   keytool -genkeypair -v -keystore redsurf-release.jks -keyalg RSA -keysize 2048 \
           -validity 10000 -alias redsurf
   ```
   **If this file is lost, no future build can ever update an installed RedSurf.** Keep it out of
   git (`.gitignore`) and store a copy off-machine.
2. Add GitHub repo secrets: `KEYSTORE_BASE64` (`base64 -i redsurf-release.jks`),
   `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
3. Add a `signingConfigs.release` block in `tv-native/app/build.gradle.kts` reading those from env
   vars, applied to `buildTypes.release`. Keep `minifyEnabled false` for now — turn on R8 later as
   its own change, so a shrinking bug is never confused with a feature bug.
4. In `.github/workflows/release.yml`: decode the keystore to a file, and change
   `gradle assembleDebug` → `./gradlew assembleRelease`. Update the APK path in the rename step
   (`app/build/outputs/apk/release/app-release.apk`).

**Note:** the first release-signed build cannot install over the existing debug-signed app. One
manual uninstall + sideload is required, once. After that, OTA works forever.

**Acceptance:** two consecutive CI releases produce APKs with **identical** signing certs. Verify:
```
unzip -p RedSurf-vX.apk META-INF/*.RSA | shasum -a 256
```
Run it on two releases; the hashes must match. (They currently differ on every build.)

## 0.4 — Firestore lockdown

In `firestore.rules`, `pairingSessions` is `allow read, write: if true` while
`app/pair/[code]/page.tsx` writes IPTV credentials into it as a plaintext
`get.php?username=…&password=…` URL. That is world-readable subscriber credentials.

Also: `isValidPlaylist` requires exactly `['url','addedAt']` (2 keys), but
`app/dashboard/page.tsx` writes 8 fields and no `url` — **so the cloud dashboard cannot save a
playlist at all**. And `family_devices` has no rule, so the global deny blocks it.

Fix all three: scope `pairingSessions` to short-lived, single-use, authenticated-writer documents;
update `isValidPlaylist` to the real schema (`name, server, username, password, type, contentType,
isActive, addedAt`); add a `family_devices` rule.

**Acceptance:** an unauthenticated read of `pairingSessions` is denied; signed-in "Add Playlist"
in the dashboard succeeds.

## 0.5 — Drop the dead Firebase project

`firebase-applet-config.json` points at `fourth-surge-1txfk`, a leftover AI Studio project. The
live project is `redsurf-fdd99`. Delete that file and any reference to it. Also drop the hardcoded
config fallbacks in `lib/firebase.ts` — env vars only.

(The orphaned Google Cloud project itself is a separate cleanup; removing repo references is what
protects RedSurf.)

## 0.6 — Connect to the TV

Follow `HARDWARE.md` → "ADB works over Ethernet". No need to unplug the adapter.

**Acceptance:** `adb connect <ip>:5555` succeeds, `adb install -r` puts a build on the TV, and
`adb logcat -s RedSurf` streams output to the laptop.

## 0.6b — OTA bugs found by actually running the app (2026-09-10)

Installing the first release-signed build on the Chromecast and watching it launch surfaced three
defects that reading the code did not:

1. **The updater auto-installs with no user consent.** `MainActivity.onCreate` calls
   `UpdateManager.downloadAndInstall(...)` immediately whenever an update is detected. It should
   prompt. TiViMate-class behaviour is "an update is available — install now?".
2. **Version comparison is string inequality, not version ordering.**
   `UpdateManager.checkForUpdates` tests `latestTag != currentVersion`. The locally-built APK
   reports `versionName=v1.0.0` while GitHub's latest is `v0.17.3`, so they differ and the app
   tries to "update" — actually a **downgrade** — on *every single launch*. Compare parsed
   semantic versions and only offer strictly-newer ones.
3. **`REQUEST_INSTALL_PACKAGES` is declared but never requested.** Android 8+ requires the user to
   additionally grant "Install unknown apps" for RedSurf specifically. Observed live: the install
   is blocked by *"For your security, your TV currently isn't allowed to install unknown apps from
   this source."* The app must detect `packageManager.canRequestPackageInstalls()` and send the
   user to `ACTION_MANAGE_UNKNOWN_APP_SOURCES` with an explanation, rather than silently failing.

All three must be fixed before 0.7 can pass.

**Verified working:** the polling, download and installer hand-off all function correctly. The
engine is real — it is the version logic, consent and permission gating that are missing.

**Baseline memory:** `TOTAL PSS 41 MB` (Java heap 3 MB) at the onboarding screen. Record this;
it is the number to compare against once real playlists are loaded.

## 0.7 — Prove OTA end to end

1. Sideload the first release-signed APK manually (one time).
2. Push a trivial change; let CI cut the next release.
3. On the TV, the in-app updater should detect, download, and install it **with no manual step**.

**Acceptance:** the version string on screen changes after an in-app update. Until observed on the
actual TV, this phase is not done — do not mark it complete from a green CI badge.

---

## Rules for whoever executes this

- **Never mark an item done without running it.** A compile is not a test; a green CI badge is
  not an install.
- If something cannot be verified (needs the TV, needs the user), say so explicitly and stop.
- Do not start Phase 1 work. No new screens, no design system, no features. This phase is
  plumbing.
- Read `../vision/README.md` first. Two binding constraints: **no Dagger Hilt** (it caused the KSP
  compiler loops that killed the predecessor project), and **borrow techniques, never transplant
  UI**.

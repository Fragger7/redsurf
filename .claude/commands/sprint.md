---
description: Run a module sprint end-to-end against the brief given as the argument (WORKFLOW.md "Sprint mode")
argument-hint: <path to the module brief, e.g. docs/plans/SETTINGS.md>
---

You are running a **module sprint** for RedSurf. The brief is `$ARGUMENTS`.

Read, in this order, before doing anything: `AGENTS.md`, `docs/plans/WORKFLOW.md` (the
"Sprint mode" section is the protocol you're executing), then the brief. The brief's acceptance
section has two lists - *machine-verifiable* and *feel/vision*. You own the first; the user owns
the second.

## Setup

1. Toolchain: `export JAVA_HOME="$HOME/.local/opt/jdk17/Contents/Home"` and
   `export ANDROID_HOME="$HOME/Library/Android/sdk"` in the **same** shell call as every
   `gradlew`/`adb`/`apksigner` invocation (each Bash call is a fresh process). `adb` is at
   `$ANDROID_HOME/platform-tools/adb`; the device is `192.172.7.160:35631` - always pass `-s`.
2. Device: `adb connect`, confirm it answers. If it doesn't within two attempts, **stop** and log
   the blocker (see "Blocked" below) - don't keep retrying.
3. Record the installed release version (`dumpsys package com.redsurf.tv | grep versionName`),
   then **uninstall it** and install a debug build (`./gradlew :app:assembleDebug`,
   `adb install -r -d app/build/outputs/apk/debug/app-debug.apk`). Debug and release can't
   coexist (different signing keys).
4. Seed the test playlist. First time: verify the `adb forward tcp:8080 tcp:8080` + `curl` path
   against the pairing server actually works and record the exact command in
   `docs/plans/SPRINT_LOG.md`; afterwards just use it.

## Build

5. Build the **whole module** the brief describes. No "coming soon" placeholders inside it. Small
   commits as you go (conventional prefixes: `feat:`/`fix:`), but **do not push until the sweep
   is green** - every push to `main` cuts a release.
6. Debug builds must log the state transitions the tests assert on (`overlay ->`, `tuned ->`,
   `focus ->`, `settings ->` etc. per the brief). That logging is part of the module, not extra.

## Sweep

7. Run the machine-verifiable list top to bottom via `adb shell input keyevent` + `adb logcat`.
   Log every failure with the key sequence and the log lines seen vs. expected. **Do not stop to
   fix the first one.** Screenshots only to diagnose a failure you can't explain from logs.
8. Fix everything that failed. One build. Sweep again from the top. Repeat until green.

## Hand-off

9. Uninstall the debug build. Push to `main`, wait for CI (`gh run watch`), confirm the release
   exists, then install **that** APK on the device (download it with `gh release download`) so
   OTA keeps working from here. Verify `versionName` on the device matches the new release.
10. Update the brief's status / "what actually happened" section and the PHASE status board.
    Append a dated entry to `docs/plans/SPRINT_LOG.md`: what was built, sweep results per pass,
    the release tag, and anything deferred (also add deferred items to `AGENTS.md`'s Backlog).
11. Give the user the **feel/vision list only** - short, numbered, one line each, tagged with
    what to look at. Not a bug report, not a summary of the sweep. If you can send a push
    notification or a file to the user from this session, do so with that list.

## Blocked

If anything blocks you for more than one attempt - device unreachable, a build that won't
compile after a real try, a permission you need, a brief decision that isn't written down - write
the blocker to `docs/plans/SPRINT_LOG.md` with what you tried, then **stop and tell the user**.
Do not burn the window retrying. A brief decision that isn't written down is an Opus question,
not something to guess at.

## The one rule

State plainly which of *builds clean* / *tests pass* / *verified on device* each claim is.
"Verified on device" means a logcat line or a screenshot from the Chromecast, nothing else.

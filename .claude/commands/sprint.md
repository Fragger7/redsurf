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
4. Seed the test playlist via `adb forward tcp:8080 tcp:8080` + `curl` POST to
   `http://127.0.0.1:8080/submit` (verified working, sprint 1). **Use the Xtream API path**
   (`type=xtream`, `server`/`user`/`pass` parsed from `~/.redsurf/test-playlist.url`) with
   `contentType=live` - never the M3U URL unless the test *is* the M3U parser. The M3U path
   streams the provider's entire file including VOD/series it then discards: ~3-4 minutes per
   seed on this device versus seconds via the JSON API. Do not add VOD until a test needs it.
5. Use `scripts/tv-test.sh` for every device interaction (focused-node lookup, verify-then-act
   navigation, logcat capture). Do not hand-roll raw `input keyevent` sequences with fixed sleeps -
   sprint 1 lost most of an hour to dropped presses and wrong starting-pill assumptions before
   building exactly this helper from scratch.

## Build

6. Build the **whole module** the brief describes. No "coming soon" placeholders inside it. Small
   commits as you go (conventional prefixes: `feat:`/`fix:`), but **do not push until the sweep
   is green** - every push to `main` cuts a release.
7. Debug builds must log the state transitions the tests assert on (`overlay ->`, `tuned ->`,
   `focus ->`, `settings ->` etc. per the brief). That logging is part of the module, not extra.

## Sweep

8. Run the machine-verifiable list top to bottom via `scripts/tv-test.sh` + `adb logcat`. Log
   every failure with the key sequence and the log lines seen vs. expected. **Do not stop to fix
   the first one.** Screenshots only to diagnose a failure logs can't explain - never for routine
   "where is focus now" checks (that's `uiautomator` via the helper).
9. **"Blocking" means one thing only:** later test cases *cannot be executed* without the fix.
   Wrong-focus-landing, cosmetic, and data failures are not blocking - log and carry them. When
   genuinely blocked, apply the *smallest* change that unblocks, rebuild once, and keep sweeping.
   Do not iterate that fix toward polish, and do not re-verify it in isolation - it gets verified
   by the next full pass like everything else.
10. Fix everything that failed. One build. Sweep again from the top. Repeat until green.
    **Hard cap: 3 debug builds per sweep pass.** If you're about to do a 4th, stop, write what
    you know to `docs/plans/SPRINT_LOG.md`, and tell the user - that's a re-plan, not a retry.
11. Any departure from the sweep order (a mid-sweep fix, an extra build) gets a one-line
    `deviated: <why>` in the sprint's `SPRINT_LOG.md` entry, at the time it happens. Sprint 1
    drifted into fix-as-you-go without noticing; this line is what makes it visible.

## Hand-off

12. Uninstall the debug build. Push to `main`, wait for CI (`gh run watch`), confirm the release
   exists, then install **that** APK on the device (download it with `gh release download`) so
   OTA keeps working from here. Verify `versionName` on the device matches the new release.
13. Update the brief's status / "what actually happened" section and the PHASE status board.
    Append a dated entry to `docs/plans/SPRINT_LOG.md`: what was built, sweep results per pass,
    how many debug builds it took and every `deviated:` line, the release tag, and anything
    deferred (also add deferred items to `AGENTS.md`'s Backlog). Note: the debug/release swap
    wipes the device's app data - say so in the hand-off so the user isn't surprised by the
    Onboarding screen.
14. Give the user the **feel/vision list only** - short, numbered, one line each, tagged with
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

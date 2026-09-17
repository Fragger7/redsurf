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
3. **Build locally with the real release signing config, then push - user directive, corrected
   2026-09-17 (a same-day reversal of that day's own earlier CI-round-trip rule, which traded real
   wall-clock time and token spend on `gh run watch`/`gh release download` polling for a benefit
   that didn't materialize).** The thing that was actually expensive, across every prior version of
   this rule, was never "local" per se - it was a *separately-versioned, debug-signed* artifact
   that has to be walked back (uninstalled, reseeded) before OTA works again. A **local release
   build**, signed with the same real keystore and given the *next real version number* (not a
   `-debug`/`v0.0.0-local` placeholder), doesn't have that problem: `git push` still cuts the
   authoritative CI release and history entry, but verification itself uses
   `./gradlew :app:assembleRelease -PversionName=vX.Y.Z -PversionCode=N` (match whatever semantic-
   release would assign next) and `adb install -r` that APK directly - no `gh run watch` wait, no
   download round-trip. Batch several logically-related fixes together before building. Compile-
   check-only (`compileDebugKotlin`, no install) is still right for anything that doesn't need a
   real device check at all.
   **The one thing to still avoid:** a debug-signed or arbitrarily-versioned build that would
   out-rank or conflict with the real release history - keep the local build's version number
   consistent with what actually gets pushed, so OTA continuity is never in question.
4. Seed the test playlist **only if the device doesn't already have one** (check via a screenshot
   or the app's own state, not automatically) via `adb forward tcp:8080 tcp:8080` + `curl` POST to
   `http://127.0.0.1:8080/submit` (verified working, sprint 1). **Use the Xtream API path**
   (`type=xtream`, `server`/`user`/`pass` parsed from `~/.redsurf/test-playlist.url`) with
   `contentType=live` - never the M3U URL unless the test *is* the M3U parser. The M3U path
   streams the provider's entire file including VOD/series it then discards: ~3-4 minutes per
   seed on this device versus seconds via the JSON API. Do not add VOD until a test needs it.
   **If a sprint does still need to wipe the device (a Reset test, a fresh-install acceptance
   criterion) re-seed immediately afterward, automatically** - never hand off or pause with the
   device sitting on Onboarding waiting for the user to re-add their playlist.
5. Use `scripts/tv-test.sh` for every device interaction (focused-node lookup, verify-then-act
   navigation, logcat capture). Do not hand-roll raw `input keyevent` sequences with fixed sleeps -
   sprint 1 lost most of an hour to dropped presses and wrong starting-pill assumptions before
   building exactly this helper from scratch.

## Build

6. Build the **whole module** the brief describes. No "coming soon" placeholders inside it. Small
   commits as you go (conventional prefixes: `feat:`/`fix:`). **Pushing during a sprint is normal
   now, not something to avoid** - per step 3 above, a push is how a real verification artifact
   gets made at all, since there's no local build standing in for it anymore. Standing permission
   to merge freely already covers this (`AGENTS.md` - no live users). Batch a logically-complete
   piece of the module into each push rather than one push per line changed, but don't withhold a
   push just to keep the release count down - that's the old, now-wrong instinct step 3 replaced.
7. Log the state transitions the tests assert on (`overlay ->`, `tuned ->`, `focus ->`,
   `settings ->` etc. per the brief) in the real code path - not gated to a debug-only build
   variant, since there mostly isn't one in this workflow anymore. That logging is part of the
   module, not extra.

## Sweep

8. Run the machine-verifiable list top to bottom via `scripts/tv-test.sh` + `adb logcat`, against
   the real release just pushed (step 3/6). Log every failure with the key sequence and the log
   lines seen vs. expected. **Do not stop to fix the first one.** Screenshots only to diagnose a
   failure logs can't explain - never for routine "where is focus now" checks (that's
   `uiautomator` via the helper).
9. **"Blocking" means one thing only:** later test cases *cannot be executed* without the fix.
   Wrong-focus-landing, cosmetic, and data failures are not blocking - log and carry them. When
   genuinely blocked, apply the *smallest* change that unblocks, push once, and keep sweeping.
   Do not iterate that fix toward polish, and do not re-verify it in isolation - it gets verified
   by the next full pass like everything else.
10. Fix everything that failed. One push, one real release. Sweep again from the top against that
    release. Repeat until green. **Hard cap: 3 release cuts per sweep pass.** If you're about to
    push a 4th, stop, write what you know to `docs/plans/SPRINT_LOG.md`, and tell the user - that's
    a re-plan, not a retry. (A tightly-scoped local debugging burst per step 3's exception doesn't
    count against this cap - it's not a sweep-pass release - but log it as its own `deviated:` line
    regardless.)
11. Any departure from the sweep order (a mid-sweep fix, an extra release, a local debugging
    burst) gets a one-line `deviated: <why>` in the sprint's `SPRINT_LOG.md` entry, at the time it
    happens. Sprint 1 drifted into fix-as-you-go without noticing; this line is what makes it
    visible.

## Hand-off

12. Nothing extra to do here now - the sweep already ran against a real release (step 8), so the
    device is already on the right version by the time the sweep is green. Just confirm
    `versionName` on the device matches the sweep's release and its signature
    (`apksigner verify --print-certs`) matches prior releases, and let the user know it's ready to
    test - via the app's own OTA update check if they'd rather not wait on you, or the build
    that's already installed.
13. Update the brief's status / "what actually happened" section and the PHASE status board.
    Append a dated entry to `docs/plans/SPRINT_LOG.md`: what was built, sweep results per pass,
    how many release cuts it took and every `deviated:` line, the release tag, and anything
    deferred (also add deferred items to `AGENTS.md`'s Backlog). Note whether the device's app
    data survived the whole sprint (expected now) or was wiped at some point and why.
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

# How this project is worked on

The user is on a Claude Pro plan with real usage limits. Model choice is a budget decision, so the
work is deliberately split into two lanes.

## The two lanes

**Sonnet — execution.** Work that is already specified: a task with a written acceptance criterion,
a known file to change, and an unambiguous definition of done. Mechanical fixes, wiring existing
components together, CI config, test writing, refactors that follow a stated pattern.

**Opus — judgment.** Work where the *right answer isn't written down yet*: architecture decisions,
anything involving visual taste, writing the next phase brief, resolving contradictions between
docs and code, and any security-sensitive design.

The rule of thumb: **if the brief tells you what to do, Sonnet does it. If someone has to decide
what the brief should say, that's Opus.**

## Escalate back to Opus when

- A task turns out to be under-specified, and progress requires a decision rather than execution.
- You catch yourself about to claim something works without build output, a test result, or a
  screenshot behind it. That is the exact failure mode this project was rebuilt to escape — stop
  and hand back rather than guess.
- A fix requires changing architecture rather than code.
- Anything touching signing or Firestore rules.
- ~~Push to `main`~~ — **the user has granted standing permission** (2026-09-11): no live users,
  merge freely to get builds onto the TV via OTA. Don't re-ask.
- Two documents disagree and you can't tell which is authoritative.

## Phase gates

A phase is done when its acceptance criteria are **observed**, not when the code exists. Do not
start the next phase early — the previous attempt at this project failed precisely by working
breadth-first and marking things complete that had never run.

### Gate: Phase 0 → Phase 1

**Satisfied as of 2026-09-10. Phase 1 may begin.** All seven were required; all seven are done and
verified against the actually running system, not just reviewed or built:

1. ✅ Zero `\$` interpolation bugs remain (`grep -rn '\\\$' --include="*.kt" tv-native` returns
   nothing).
2. ✅ The duplicate `worker/EpgSyncWorker.kt` stub is deleted.
3. ✅ CI runs `./gradlew assembleRelease` and publishes a release-signed APK; a debug-signed build
   now fails the pipeline outright rather than being allowed to publish.
4. ✅ Two consecutive CI releases carry the same signing certificate — verified across v0.17.4 and
   v0.17.5, both `1b13f1d9…d2510d8a`.

   Compare certificates with **apksigner**, never by hashing the signature block:
   ```bash
   apksigner verify --print-certs RedSurf-vX.apk | grep "SHA-256 digest"
   ```
   `unzip -p … META-INF/*.RSA | shasum` is **wrong** — that file is a PKCS#7 block containing the
   signature over *that specific APK*, so it differs on every build even when the certificate is
   identical. It will make correct releases look broken.
5. ✅ All three OTA defects from `PHASE_0.md` §0.6b are fixed and device-verified: real semantic
   version comparison, a consent dialog, and an install-permission check/explainer. Two more bugs
   were caught only by testing live (the dialog didn't trap D-pad focus; its default colors were
   unreadable) and are also fixed — see `PHASE_0.md` §0.7 for the full account.
6. ✅ `firestore.rules` no longer exposes `pairingSessions`; the dashboard can save a playlist.
   Verified with real HTTP requests against the live `redsurf-fdd99` project (a throwaway test
   account, exercised, then deleted) — not emulated, not just reviewed.
7. ✅ **An in-app OTA update was observed installing on the actual Chromecast**: consent dialog →
   "Install now" → permission check → Android's own confirmation → installed, versionCode 61→62.
   The permission-denial path was verified separately too.

Item 7 was the real gate; the rest were how you get there. A green CI badge alone would not have
satisfied it — every item above has a corresponding live observation on record in `PHASE_0.md`.

### Gate: Phase 1 → Phase 2

**Satisfied as of 2026-09-12. Phase 2 may begin.** `PHASE_1.md` 1.6: memory flat across playlist
size (large real list 112.8 MB vs small 145.7 MB PSS - paging works), all seven acceptance lines
observed, real end-to-end playback confirmed on the Chromecast by the user, six rounds of
device-found bugs fixed. Phase 2 brief: `PHASE_2.md`, written by Opus; execution Sonnet.

## Why Phase 1 is an Opus phase

Phase 1 is the design system — palette tokens, the two-state focus model, the top nav strip, the
three-column Live TV layout — built against `../vision/UI_SPEC.md` and the mockups. It is the part
of the product the user cares most about, and it is judgment work: "does this look right" is not
something an acceptance criterion can capture. Expect to iterate against screenshots from the
device.

Phase 0, by contrast, is entirely mechanical. It is a good Sonnet phase.

**The Phase 1 brief is written: `PHASE_1.md`** (2026-09-10). The pairing question that gated it
was resolved — revive — and is on record in `AGENTS.md`. The brief front-loads every
architectural decision precisely so that the *execution* is Sonnet-lane; Opus is needed only at
the two screenshot checkpoints (A after the layout, B after playback) and for anything the brief
didn't anticipate. That's the intended budget shape: one expensive planning pass, cheap execution,
two short reviews.

## Sprint mode — how work is paced from 2026-09-13 on (user decision, Opus session)

**What changed and why.** Phase 2 was built as thin slices (2.1a, 2.1b, 2.2 backend, …), each
handed to the user for device testing before the next began. It found real bugs, but the user
called out two costs: every stop is a build + a long written bug report from them, and the
stubs the slicing forces into existence (an Actions floor with placeholder text, a RIGHT key that
fakes a banner, recents held in memory) generate bugs of their own that a finished module can't
have. So: **the unit of user-facing testing is now the module, not the slice.** Commits stay
small (bisectable, reviewable); *stopping* doesn't happen until the module is done.

**A sprint is one of the user's 5-hour usage windows.** Shape:

1. Build the whole module end-to-end — no "coming soon" placeholders left inside it.
2. Sweep the module's **machine-verifiable** test list on the real device over ADB. Log every
   failure; do **not** fix-build-fix-build one at a time.
3. Fix everything that failed, one build, sweep again. Repeat until green.
4. Hand the user one build and the module's **feel/vision** test list - short, tagged, the things
   ADB genuinely can't judge. They work through it on their own clock while the next sprint runs.

**Two test lists per module, written before the sweep starts:**
- *Machine-verifiable*: key injection (`adb shell input keyevent`) + assertions on **logcat**, not
  screenshots. Debug builds log state transitions (`overlay -> …`, `tuned -> num/name`,
  `focus -> owner`); a test is a key sequence and the log lines it must produce. Screenshots only
  to diagnose a failure - each one is an image in context, and image cost is exactly why the old
  per-task screenshot loop was stopped.
- *Feel/vision*: anything needing eyes or taste - "does this match the TiviMate reference," did
  that feel dead, is the video actually clean between zaps. Note the last one: `screencap` on the
  Chromecast returns black for the video surface, so playback quality is *never* machine-verified
  here, only inferred from player state logs.

**Device protocol (supersedes two older rules - see below):**
- Sprints run on **debug builds**, `adb install -r -d` for any version, no semver games. As of
  2026-09-15 (user decision - no live users, single family device, the usual reason to keep debug
  and release signing apart doesn't apply here) **debug builds share the release signing key**
  (`app/build.gradle.kts`'s `debug` block, same `hasReleaseSigning` keystore the `release` block
  already used) - debug and release used to be unable to coexist on the device at all (same
  package, different signature), which is what forced a sprint to *uninstall the release build to
  start and uninstall the debug build to hand off*, wiping the device's app data (playlists,
  settings, everything) each time. **No longer needed**: `adb install -r -d` now moves between a
  release build and a debug one in either direction with the user's data intact throughout - a
  sprint installs a debug build directly over whatever's already there, and hands off the same
  way, straight to the new CI release, never touching an uninstall. Pass a `versionCode` higher
  than what's installed (`./gradlew :app:assembleDebug -PversionCode=999 -PversionName=vX.Y.Z-debug`)
  - Android's own downgrade-protection blocks `-r` otherwise, `-d` alone isn't enough on every
  device. **Always re-seed automatically after any wipe you do still cause** (a Reset, a fresh
  playlist test) - don't leave the device on Onboarding for the user to redo by hand.
- `run-as` for on-device DB inspection is **not reliably available on this Chromecast** - found
  2026-09-15, `run-as: /mnt has wrong owner: 0/1000, not 1000`, unrelated to the signing change
  above (a device/ROM quirk). No root either (`adb root` refused, no `su`). DB inspection needs a
  different path (e.g. reading through the app's own UI/logs, or asking the user to pull a backup)
  until/unless that's solved - don't assume `run-as` works without checking first.
- The Chromecast stays reachable over WiFi ADB while asleep - **verified 2026-09-13**: put to
  sleep via `KEYCODE_SLEEP`, `mWakefulness=Asleep` / screen `OFF`, still answering 30s later,
  woken with `KEYCODE_WAKEUP`. No "Stay awake" toggle needed. Screen off is not a blocker.
- Playlist seeding after a wipe: the on-device pairing server is plain HTTP on :8080, so
  `adb forward` + `curl` should re-add the test playlist with no remote in hand. **Verify this
  once before relying on it** - if it works, wipe/migration tests are fully automatable too.

**Superseded by this section:** `AGENTS.md`'s "stop doing per-task ADB screenshot round-trips"
(still true *as stated* - screenshots are the expensive part; logcat-driven sweeps are the
replacement, not a return to the old loop) and its "never install an ad-hoc-versioned local
build" rule (replaced by the uninstall-at-sprint-end protocol above). `PHASE_2.md`'s "Order of
work" still lists per-slice hand-offs; read it as module-level from here on - Checkpoint B is
the sprint-end acceptance, not a mid-module stop.

**Lanes are unchanged.** Opus writes the module brief and both test lists; Sonnet runs the sprint.
Opus runs it only when the debugging turns into design.

### Sprint 1 retrospective (Settings shell, 2026-09-13 - Opus, from the full transcript)

**Verdict from the user:** the approach works - far more got done than with the user in the loop
at every slice. Keep it. **What didn't go to protocol,** read directly from the transcript rather
than asked of Sonnet:

- **Fix-as-you-go crept back in.** The protocol says sweep everything, log every failure, fix all,
  *one* rebuild, sweep again. What happened: the LEFT-to-rail bug was hit at criterion #3 and
  Sonnet then did roughly six rebuild+reinstall+re-navigate cycles trying successive fixes
  (`onKeyEvent` → `onPreviewKeyEvent` → deferred `LaunchedEffect` → trap disabled → direction-aware
  `exit` → explicit guard) before continuing the sweep. The first deviation was defensible - that
  bug genuinely blocked traversing the remaining criteria, and Sonnet said so. The next two
  (confirm-dialog focus, null-context onboarding) were *not* blocking and were still each fixed
  and rebuilt immediately. So: one legitimate exception, then drift. The result was correct, but
  the window was spent on ~9 debug builds and far more screenshots than "only on failure."
- **Test-harness flakiness ate cycles.** Batched `adb shell input keyevent` sequences dropped
  presses and assumed a starting pill that wasn't there; Sonnet rebuilt a verify-then-act helper
  mid-sprint from scratch. That helper should exist in the repo *before* the next sprint, not be
  re-derived inside it.
- **Seeding choice mattered more than expected.** The playlist was seeded via the provider's M3U
  URL, which streams the *whole* file (VOD/series lines included, then discarded) - ~3-4 minutes
  per re-seed, three re-seeds. Same provider via the Xtream JSON API is ~28K objects, far faster,
  and is the path the app already prefers. (This is also the root of the user's "the screen sits
  forever after channels are done" report - see `AGENTS.md` backlog, progress feedback.)

**Protocol tightened accordingly** (`.claude/commands/sprint.md` updated to match):
1. "Blocking" is defined narrowly: a failure blocks only if later test cases *cannot be executed*
   without fixing it. Cosmetic, data, or focus-lands-wrong failures are logged and carried.
2. When genuinely blocked: apply the *smallest* fix that unblocks, rebuild once, and continue the
   sweep - don't iterate on that fix until it's polished, and don't verify it in isolation.
3. Hard cap: **3 debug builds per sweep pass.** Hitting it means stop, log, re-plan - not a 4th.
4. Every deviation from the sweep order gets a one-line "deviated: <why>" in `SPRINT_LOG.md`.
5. Screenshots are budgeted: none for routine state checks (use logcat/`uiautomator`), only to
   diagnose a failure that logs can't explain.
6. Seed via the Xtream API (server/user/pass), `contentType=live`, never the M3U URL, unless the
   test *is* the M3U path.
7. The verify-then-act ADB helper lives at `scripts/tv-test.sh` (to be committed from Sonnet's
   sprint-1 scratch version) and every sprint uses it instead of raw key sequences.

## Docs-only commits: skip the release

Every push to `main` triggers the release workflow and publishes a new version. For commits that
change nothing shippable — docs, comments, this file — put `[skip ci]` in the commit message and
GitHub will skip the run entirely.

Reserve real releases for changes that actually alter the APK.

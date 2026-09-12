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

## Docs-only commits: skip the release

Every push to `main` triggers the release workflow and publishes a new version. For commits that
change nothing shippable — docs, comments, this file — put `[skip ci]` in the commit message and
GitHub will skip the run entirely.

Reserve real releases for changes that actually alter the APK.

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
- Anything touching signing, Firestore rules, or a push to `main` (which cuts a public release).
- Two documents disagree and you can't tell which is authoritative.

## Phase gates

A phase is done when its acceptance criteria are **observed**, not when the code exists. Do not
start the next phase early — the previous attempt at this project failed precisely by working
breadth-first and marking things complete that had never run.

### Gate: Phase 0 → Phase 1

Phase 1 (design system + Live TV screen) may begin only once **all** of these are true:

1. `grep -rn '\\\$' --include="*.kt" tv-native` returns **zero** matches.
2. The duplicate `worker/EpgSyncWorker.kt` stub is deleted.
3. CI runs `./gradlew assembleRelease` and publishes a **release-signed** APK.
4. ✅ **Done.** Two consecutive CI releases carry the same signing certificate — verified across
   v0.17.4 and v0.17.5, both `1b13f1d9…d2510d8a`.

   Compare certificates with **apksigner**, never by hashing the signature block:
   ```bash
   apksigner verify --print-certs RedSurf-vX.apk | grep "SHA-256 digest"
   ```
   `unzip -p … META-INF/*.RSA | shasum` is **wrong** — that file is a PKCS#7 block containing the
   signature over *that specific APK*, so it differs on every build even when the certificate is
   identical. It will make correct releases look broken.
5. The three OTA defects in `PHASE_0.md` §0.6b are fixed: real semantic version comparison, a user
   consent prompt, and an "install unknown apps" permission request.
6. `firestore.rules` no longer exposes `pairingSessions`, and the dashboard can save a playlist.
7. **An in-app OTA update has been observed installing on the actual Chromecast** — the version
   string on screen changed, with no manual step.

Item 7 is the real gate. Items 1–6 are how you get there. A green CI badge does not satisfy it.

## Why Phase 1 is an Opus phase

Phase 1 is the design system — palette tokens, the two-state focus model, the top nav strip, the
three-column Live TV layout — built against `../vision/UI_SPEC.md` and the mockups. It is the part
of the product the user cares most about, and it is judgment work: "does this look right" is not
something an acceptance criterion can capture. Expect to iterate against screenshots from the
device.

Phase 0, by contrast, is entirely mechanical. It is a good Sonnet phase.

## Docs-only commits: skip the release

Every push to `main` triggers the release workflow and publishes a new version. For commits that
change nothing shippable — docs, comments, this file — put `[skip ci]` in the commit message and
GitHub will skip the run entirely.

Reserve real releases for changes that actually alter the APK.

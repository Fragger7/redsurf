# RedSurf's focus model — read this before writing any focus-handling code

This is the canonical rule set, extracted 2026-09-22 from the accumulated real incidents this
project has hit (`AGENTS.md`'s "State and focus discipline" section has the dated provenance for
each one, if you need the history — this file is the distilled *rules*, not the incident log).
Every rule below was learned from a real bug a real person hit on a real device, not derived in
the abstract. Read this first; check new focus-handling code against it before shipping, the same
way "builds clean" and "tests pass" get checked.

## The meta-rule

**Never assume focus/state continuity works because the code looks plausible.** This project's
own binding rule (`AGENTS.md`, "the one rule that matters") — "never claim something works
because you wrote plausible code for it" — applies to focus with extra force, because a focus bug
often produces *no crash, no error, no log line* — Compose just quietly picks something else. The
only way to know continuity actually holds is to watch it happen: leave the screen, come back,
confirm the exact row/item/state is what's focused, not just that nothing threw.

## The rules

### 1. Every focusable region claims focus explicitly on entry — never trust default spatial search to land somewhere reasonable

Compose's default directional search (`moveFocus`) and its "focus whatever's nearest" fallback are
not a substitute for an explicit `FocusRequester.requestFocus()` call. Left to its own devices it
will land on the NavStrip, or nothing, or whatever happens to be spatially closest in the current
layout — rarely what the user actually wants. Every screen, and every region within a screen that
can independently hold focus, needs its own explicit claim, wired to a real `FocusRequester`.

### 2. Leaving and returning restores the *exact* item — never "spatially nearest," never "row 0 of whatever's there now"

**Locked project-wide, 2026-09-13** (`AGENTS.md`) — not a per-case judgment call. Basis: real
TiviMate does this (drilling into a settings category and hitting Back refocuses that exact
category), it matches documented Android TV / console UX guidance, and it's the same
recognition-over-recall principle either way. A "coarse" landing (first row of a list, rather than
the exact item last focused) is a real, loggable scope trim if you have to ship it under time
pressure — but it's still a bug to close, not a permanent shrug. `ChannelsColumn.kt`'s
`returnFocusRequester` (channel-exact) is the reference implementation of doing this correctly;
`EpgGridColumn`'s shared `gridFocus` (row-0-only, not channel-aware) is the reference example of
the trim still open as of this writing — see Finding 6 below.

### 3. Any focus claim targeting content that might not be composed yet must retry in a bounded loop — never attempt once

A `FocusRequester` attached to a row inside a `TvLazyColumn`/Paging list/debounced-query result
often doesn't exist in the composition tree the instant your effect runs — the list hasn't
composed that far, the query hasn't resolved, the Paging source hasn't loaded that page.
`requestFocus()` on an unattached node **throws** (not a no-op), so a bare
`runCatching { requestFocus() }` with no retry silently fails and the claim is simply lost —
Compose then picks whatever it likes by default (rule 1's failure mode, again). Every focus claim
in this codebase that targets async/paged/debounced content uses a bounded retry loop
(`repeat(N) { delay(ms); if (runCatching{...}.isSuccess) return }`) for exactly this reason — see
`GroupsColumn.kt`'s `initialFocus`, `LiveTvScreen.kt`'s `fullscreenFocus`/`claimInitialFocusTrigger`
paths, `PlayerScreen.kt`'s tile-floor claims. **A single-shot claim with no retry is a real,
recurring source of bugs in this codebase specifically** — found and fixed once already
(`LiveTvScreen.kt`'s fullscreen-*entry* claim, 2026-09-18) and found open *again* this session in
the fullscreen-*exit* branch of the exact same effect (Finding 5 below) and in
`SettingsScreen.kt`'s rail re-entry claim (Finding 9 below) — check every `runCatching { ...
.requestFocus() }` in the codebase against whether its target can plausibly not exist yet; if so,
it needs the retry loop, full stop.

### 4. Don't remove a composable from the tree to hide it — keep it composed, toggle visibility/size instead

Conditional composition (`if (condition) { Screen() }`, or a `when` branch that only renders a
subtree some of the time) destroys **everything** underneath it when the condition goes false —
not just the specific piece of state you meant to preserve, but every derived query result, every
scroll position, every one-shot latch (`hadFocus`-style booleans), every subscription. This is
exactly the bug Sprint 1 found and fixed for `LiveTvScreen` (2026-09-22): it was being torn out of
composition on every destination switch, silently rebuilding the categories query, the grid query,
the scroll position, and the focus-claim latches from zero every single time. The fix: keep the
subtree **permanently composed** once it's genuinely needed, and give it a `visible: Boolean` param
that swaps real layout for `Modifier.size(0.dp)` (zero layout cost, can't receive focus/input)
instead of removing it. `SettingsScreen` still uses the older, riskier pattern as of this writing —
see Finding 9.

### 5. Key a focus-claim effect on the *event* that should trigger it — never on data that changes for unrelated reasons

`LaunchedEffect(someQueryResult) { requestFocus() }` fires every single time `someQueryResult`
changes — including every time it changes for a completely unrelated, legitimate reason (the user
just browsing, a background refresh, anything). If the *intent* was "claim focus when this screen
is freshly entered," keying on the data that happens to load when the screen is entered is the
wrong trigger — it will keep firing on every subsequent legitimate data change too, stealing focus
during ordinary use. Key on a real one-shot signal (a dedicated trigger boolean consumed back to
false, an actual state transition like `isFullscreen` flipping, a `hadFocus`-style
"did-focus-just-arrive-from-outside" latch) — not on a query/list result that has its own reasons
to change. This is exactly `LiveTvScreen.kt`'s `LaunchedEffect(gridChannels)` bug (Finding 4 in
`SEQUENCING.md`'s Sprint 2, found 2026-09-22): keyed on the grid's channel-list data, it re-fires
on every ordinary category browse, not just on genuine grid entry.

### 6. One shared `FocusRequester`, one effect responsible for claiming it

If multiple `LaunchedEffect`s independently call `.requestFocus()` on the *same* `FocusRequester`,
keyed on different triggers, with no coordination between them, the result is a race: whichever
effect's retry timing happens to land first wins, and which one that is can vary run to run. This
produces exactly the symptom this project has already been burned by once (Teleport Menu's
build-time sweep said "verified," real remote testing found most rows broken) — a claim that
*sometimes* works isn't a claim that works. If a `FocusRequester` legitimately needs to be claimed
from more than one place, route all of them through a single effect reacting to a unified signal,
rather than leaving several independent effects to compete. See Finding 7 below for a live example
of this pattern already existing in the codebase, undiagnosed until this pass.

### 7. Don't trust Compose's default directional search to stop at a scrollable container's real boundary

UP/DOWN escaping a list before genuinely reaching its top/bottom (especially a list with mixed
content types — header rows plus item rows — or one that's long enough that far rows aren't
composed yet) is a real, reproducible failure mode of relying on default `moveFocus` behavior alone
to naturally stop at a container's edge. Where this codebase has already hit this and needs a real
boundary, it uses **explicit `onPreviewKeyEvent` interception** at the point that needs to guard
the escape (`SettingsScreen.kt`'s rail/pane router, `PlayerScreen.kt`'s own key router,
`AppShell.kt`'s root-level long-press-Back interceptor) — never `focusProperties { exit = ... }`,
which this project found behaves **inconsistently by direction** in the pinned Compose/tv-
foundation versions (blocked an explicit `requestFocus()` even to a destination still inside the
trapped subtree, while not reliably blocking the default escape it was meant to catch — Settings
sprint, 2026-09-13). `GroupsColumn.kt` (Categories) used to have **no boundary guard of any kind**
on UP — fixed Sprint 2, 2026-09-22 (`SEQUENCING.md`).

**The rule cuts both ways — don't just guard the in-list case, guard the escape itself too.**
Sprint 2's own first attempt at `GroupsColumn.kt`'s UP guard handled the in-list "scroll and move"
case explicitly, but deliberately left the true-row-0 escape to fall through to default `moveFocus`
— on the reasoning that *this* was the case the rule already covered elsewhere. Live device testing
found that default escape does **not** reliably work either: three consecutive UP presses at a
genuine top row all stayed on the same row, confirmed via `uiautomator` focus bounds never
changing. The fix was an explicit callback (`onEscapeUp`) calling the same canonical
`navPillFocusRequesters[destination].requestFocus()` this codebase already uses successfully
elsewhere (the long-press-Back nav-jump), not another default-search attempt. Lesson: when adding a
boundary guard, the *escape* direction needs the same explicit-interception treatment as the
in-list direction — don't assume "default search will at least get you off the edge," since that's
the exact failure mode this rule exists to name.

### 8. Every overlay/modal a screen owns internally needs the same reclaim-on-close discipline as the screen level — not just once, at the door

It's not enough to get focus right when entering/leaving a whole screen. A screen with its own
internal overlay/modal states (a picker, a context menu, a Controls floor) needs the *same*
explicit "reclaim focus when this closes" treatment for transitions *within* itself. Skipping this
doesn't just misplace focus — it can silently break **all further key routing** on a screen whose
input handling depends on something staying focused (`PlayerScreen.kt`'s original incident, 2026-
09-12: "the whole button engine seems to crash" — Back kept working only because its dispatcher
doesn't need focus at all). `PlayerScreen.kt`'s current `LaunchedEffect(overlay)` reclaiming
`focusRequester` whenever `overlay` returns to `None`/`ZapBanner` is the reference fix for this
class — but note it's itself a **single-shot claim with no retry** (rule 3) on a target that should
always be attached (the persistently-composed player root, not async content), which is why it's
lower-risk than the other single-shot violations found this pass, not zero-risk.

### 9. State discipline is broader than focus — ask explicitly, for every new flow, whether leaving-and-returning loses something the user would expect kept

**Widened, 2026-09-15** (`AGENTS.md`) — apply this by default when building something new, don't
wait for a report. Concrete checklist for any new flow with meaningful state:
- Does leaving this screen for another destination and coming back lose a selection, a scroll
  position, an in-progress input, a search query/result set?
- Does a full relaunch (not just a session) lose something the user would reasonably expect to
  survive it (the model here: "resume last channel" persisting to disk, not just in-memory)?
- If a value is hoisted to survive navigation, does *everything derived from it* also survive, or
  does the derived state get silently recomputed from an empty starting point (rule 4's failure
  mode — hoisting the data is necessary but not sufficient if the composable that reads it still
  gets torn down and rebuilt)?

## Established good idioms in this codebase — use these, don't reinvent

- **The `hadFocus` re-entry pattern** (`GroupsColumn.kt`, `ChannelsColumn.kt`): a local
  `var hadFocus by remember { mutableStateOf(false) }`, updated in the container's own
  `onFocusChanged`, redirecting to the "correct" target only on the `false -> true` transition
  (focus arriving from *outside*) — never fires on ordinary in-container up/down movement, so it
  can't fight normal navigation. This is the standard way to make rule 2 hold for a column/region
  entered laterally (LEFT/RIGHT) or from a sibling.
- **The bounded-retry claim** (rule 3) — `repeat(N) { delay(ms); if (runCatching{
  requestFocus() }.isSuccess) return@LaunchedEffect }`. Used throughout; the retry count/interval
  should roughly match how long the target realistically takes to compose (a short in-memory list
  needs less margin than a fresh Paging load against a large real playlist — `GroupsColumn`'s
  category-row claim measured this directly: ~3.4s for a 123-channel group's *first* Paging load
  on real hardware, far longer than any UI-only claim needs). **A retry budget is a claim about a
  measured worst case, not a guessed round number — verify it against one.** Sprint 2, 2026-09-22:
  `LiveTvScreen.kt`'s `claimGridFocus()` shipped with a 20×150ms=3s budget that looked generous by
  eye, then failed on the very first genuinely-cold-cache device test — a real group query measured
  **5.23s**, and the claim silently exhausted its retries before the target ever composed, landing
  focus on Categories instead of the intended channel row. A second test with a warm cache (363ms)
  passed, which would have made the bug look fixed if that had been the only test run. Widened to
  20×300ms=6s to match `GroupsColumn`'s own already-proven ceiling for this exact class of
  cold-launch race. Test a retry budget against a genuinely cold cache (fresh install or
  `force-stop`, not a warm relaunch), not just the fast path.
- **Explicit `onPreviewKeyEvent` interception** (rule 7) over `focusProperties { exit = ... }` —
  the established router pattern, not a special case.
- **A `visible: Boolean` param instead of conditional composition** (rule 4) — the fix shape for
  any screen that both (a) needs to be hidden sometimes and (b) holds real derived/async state
  worth preserving.
- **One-shot trigger booleans, consumed back to `false`** (`liveTvClaimInitialFocusTrigger`,
  `liveTvAutoPlayTrigger`) — the standard shape for "claim focus/take this action exactly once, in
  response to a real event," satisfying rule 5.

## Open findings from the 2026-09-22 audit

**Sprint 2 closed the same day, real release v0.37.13** — all 9 items fixed and device-verified
(two of them, items 1/8 and 5/6, needed a real second fix after their first attempt failed live
verification; see the rule 3 and rule 7 notes above for what was learned). See
`docs/plans/SEQUENCING.md`'s Sprint 2 section for the full per-item account. One item (1/8's
true-boundary escape) is fixed in code but not re-confirmed at the actual list boundary — this
device's real category list turned out to run into the hundreds of rows, and brute-force climbing
back to the top a second time to re-check wasn't a good use of one session. Worth a quick targeted
check next time Categories is touched.

**New, found during Sprint 2 verification, not yet fixed — a real crash, not a focus bug.** A
genuine `FATAL EXCEPTION` (`IllegalStateException: Expected BringIntoViewRequester to not be used
before parents are placed`) reproduces live in `SettingsScreen`'s Playlists pane: cancel a
remove-playlist confirmation, then press UP a few times, and the app force-quits. Confirmed not
caused by Sprint 2's own changes (no scroll/`BringIntoView` call exists anywhere in
`SettingsScreen.kt`) — this looks like a Compose Foundation internal timing race, plausibly
triggered by the confirm panel's rows disappearing from the list right as a bring-into-view request
fires before the shrunk list finishes re-laying-out. Out of scope for a focus-model rule (it's not
a "focus landed in the wrong place" bug, it's a framework-level crash), logged here as a pointer —
full account in `docs/plans/TESTING_OUTSTANDING.md` section H and `SPRINT_LOG.md`'s 2026-09-22
entry.

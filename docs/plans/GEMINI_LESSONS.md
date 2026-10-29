# Gemini underused - lessons from the first joint run (2026-10-28/29)

## What happened (from GEMINI.md §9 and SPRINT_LOG 2026-10-29)

- Gemini got **8 tasks**. 001-004 were tiny (3-6 min each, all clean). 005-008 were TV checks:
  one clean, one correctly stopped (TV asleep), **three blocked by the lead's own allow-list**
  (`./gradlew`, `sleep`, a file read). Gemini did perhaps 30 minutes of real work all night.
- Everything big - Search UI, ESPN fetcher, Perf Max steps 5-7 - went to Sonnet/Opus, so the run
  spent Claude quota while Gemini's sat unused. A Sonnet agent hit its 5h limit mid-task.
- The 02:17 uninstall that wiped the family's playlists was a **Sonnet** agent running
  `:benchmark:assemble`, not Gemini. Guarded since (`-Predsurf.deviceRun=true`).

## Why

1. **Tasks too small for the overhead.** A precise task file + review cost the lead about as many
   tokens as doing a 5-minute change itself. Delegation only saves quota at feature size.
2. **Trust ladder too slow for a time-boxed run.** Level 1 -> 2 took most of a night; the real
   work was already handed to Sonnet by then.
3. **One task at a time, no queue.** Gemini waited for the lead between every task.
4. **Permissions not preflighted per task type.** Tightening agy's allow-list for TV tasks silently
   broke code tasks; found at 02:10 with the user asleep, so nothing could be fixed until morning.
5. **On a block, work moved to Claude** instead of the block being fixed and Gemini re-queued.

## Next time

1. **Gemini is the default builder.** Claude writes briefs, acceptance tests and reviews; it builds
   only security, architecture and focus/navigation-design work. Target: Gemini busy >= 70% of
   the run's wall clock.
2. **Feature-sized tasks with tests as the spec.** The lead writes the failing tests; Gemini makes
   them pass. Review = run the tests + read the diff stat; read in full only risky files.
3. **Queue 5-10 approved task files before the run;** Gemini takes the next one on finishing
   (needs a GEMINI.md change: "work the queue in order" instead of "never start the next").
4. **Preflight canary before the user goes to bed:** one throwaway agy run per task type (compile,
   write under `src/`, read a file, `redsurf-tv begin/end`). Anything denied -> one paste-able fix
   for the user, then. (See memory `preflight-agent-permissions`.)
5. **A block re-queues to Gemini after the fix** - Claude takes it only when Gemini has failed twice.
6. **Start the next run at level 2** (earned: 001-004 clean); level 3 TV tasks once the allow-list
   is widened and preflighted.
7. **Measure it.** Each run's SPRINT_LOG entry gets one line: Gemini task-minutes vs Claude agent
   runs, so underuse shows up the same night.
8. **No agent of any model runs Gradle tasks that can reach the TV** (`connected*`, `:benchmark`,
   baseline-profile generation) without `-Predsurf.deviceRun=true` and the user's OK.

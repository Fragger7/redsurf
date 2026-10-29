---
description: Start or stop an unattended RedSurf run - Mac awake, wake-ups per usage window, Chromecast test settings, and the full clean-up
argument-hint: start | stop
---

Unattended-run routine for RedSurf's own devices (the Mac and the family's Chromecast with
Google TV). Argument: `$ARGUMENTS` - run the matching section below, report each step's result in
one line. Background and history: `docs/plans/PERFORMANCE_SALVAGE.md` (unattended-run notes),
memories `unattended-mac-sleep`, `autonomous-wakeups-per-window`, `batch-build-then-test-sprint`.

Toolchain in every shell call: `export JAVA_HOME="$HOME/.local/opt/jdk17/Contents/Home"
ANDROID_HOME="$HOME/Library/Android/sdk"`; adb is `$ANDROID_HOME/platform-tools/adb`, always
`-s "<serial>"` (mDNS serial from `adb devices`, see `scripts/tv-test.sh`).

## start

1. **Mac awake:** `pmset -g batt` must say AC power (warn the user if not). Then
   `caffeinate -is -t 18900` in the background (5h15m - overlaps the next wake-up). The Mac
   idle-sleeps after 1 minute otherwise; that caused every earlier stall.
2. **Wake-ups:** ask the user for the usage-window reset times and the stop point if they aren't
   in this conversation. One one-shot `CronCreate` per window reset, at reset + 2 min - never
   hourly, never recurring (recurring jobs drift). Each wake-up's prompt: re-run
   `caffeinate -is -t 18900`, resume from AGENTS.md "RESUME HERE", append one short line to
   `docs/plans/SPRINT_LOG.md` even if nothing was done (so gaps are visible). The last wake-up
   runs `/redsurf-unattended stop`.
3. **Gemini builder (only if this run will give it tasks):** ask the user to check Antigravity's
   own screen and say roughly how much Gemini quota is left and when its window resets - Claude
   can't read Gemini's quota, only see a run fail when it's out. Check the `agy` user is signed in
   with a tiny no-op run; if it isn't, tell the user now (sign-in needs them), not in the morning.
   Gemini runs one task at a time via `scripts/agy-run.sh`; only one engineer drives the TV at once.
4. **TV:** `adb devices` - note whether the TV answers; don't retry more than twice. Record the
   current values of `settings get secure sleep_timeout`, `settings get secure
   screensaver_enabled`, `settings get global hdmi_control_enabled` in SPRINT_LOG before
   changing anything.
5. **Only when device testing actually starts** (not at start-up if the run is build-only) and
   only once the user has said the TV is free: `settings put global hdmi_control_enabled 0` (a
   wake can't switch the real TV on), `svc power stayon true`, `settings put secure
   sleep_timeout -1`, `settings put secure screensaver_enabled 0`. Tell the user CEC is off: the
   remote won't power the TV or change its volume until `stop`.
6. **Standing rules for the whole run:** never send keys while someone uses the TV
   (`scripts/tv-test.sh` `tv_in_use`); never remove a real playlist, run "Clear watch history" or
   the History picker's "Clear history", or save a real credential change; screenshot before any
   OK in Settings; mass-build first, device-test last (end of the final window).

## stop

1. **Chromecast restore** (if the TV answers): `settings delete global hdmi_control_enabled`
   (unset = CEC on) unless step 3 of `start` recorded a different value, `svc power stayon
   false`, `settings put secure sleep_timeout <recorded, normally 1200000>`, `settings put secure
   screensaver_enabled <recorded, normally 1>`.
2. **Test state left by the run:** revert it (e.g. the test parental PIN, hidden categories) via
   the app's own Settings - screenshot before each OK. List anything that couldn't be reverted.
3. **Wake-ups:** `CronList`, then `CronDelete` every remaining job this run created.
4. **Mac:** `pkill caffeinate`. If Gemini ran: make sure no `agy` run is still going
   (`pgrep -u agy`), and `redsurf-tv end` if it touched the TV.
5. **Records:** update AGENTS.md "RESUME HERE" and `docs/plans/SPRINT_LOG.md` (what was built,
   what is verified on device vs builds clean, what is left on the TV). Commit, then `git push`
   (cuts the CI release; versionCode = 2000 + run number).
6. Report to the user: restore results, what's left on the TV, the release version, and - if
   Gemini ran - its tasks done/stopped and whether it hit its quota (and when).

When the user says they're done with Gemini, run the clean-up in memory `gemini-setup-cleanup`
(`sudo scripts/agy-user-remove.sh` for the Mac side) - show the list first.

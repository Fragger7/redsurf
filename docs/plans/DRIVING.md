# Driving this project — the cheat sheet (2026-09-13)

How you, the user, operate Claude Code on RedSurf. Kept short on purpose; each item is one
thing to type. Claude: when you use any of these mechanisms in a session, **say so in one line
and show the user the command they'd type themselves** - the user asked to learn these in the
moment, not from a manual.

## Starting work

| You type | What happens |
|---|---|
| `/sprint docs/plans/SETTINGS.md` | Runs a whole module sprint per `WORKFLOW.md` Sprint mode: debug build on the TV, ADB sweep, fix-all-then-rebuild loop, CI release at the end, you get the feel/vision list. The prompt lives in `.claude/commands/sprint.md` - edit that file to change how sprints run. |
| `/model` | Switch Sonnet ↔ Opus. Sonnet runs sprints; Opus writes briefs and makes taste/architecture calls. |
| `! adb devices` | The `!` prefix runs a shell command *yourself*, right in the session, and its output lands in the conversation. Use it for anything interactive (logins) or when you want to see something without asking Claude to. |

## Staying out of the way

| You type | What happens |
|---|---|
| `/permissions` | See/edit what runs without asking. The project allowlist is `.claude/settings.json` (committed - gradle, adb, gh, git); machine-only overrides go in `.claude/settings.local.json` (git-ignored). An unattended sprint stalls at the first thing that isn't allowed. |
| `/fewer-permission-prompts` | Scans past sessions and proposes allowlist additions for commands that keep prompting. Run it after a sprint that asked too often. |
| `/hooks` | See the notifications wired up: a macOS banner + sound when Claude is waiting on you, a soft pop when a turn ends. Defined in `.claude/settings.local.json`. Delete the `Stop` entry if the pop gets annoying. |

## Getting told, not watching

- The sprint's last step sends you a **push notification** and the feel/vision list as a file,
  so the hand-off reaches your phone. Nothing to set up - it's part of the sprint prompt.
- You can follow any session from another device (claude.ai/code) - useful to glance at a sprint
  from the couch.

## Running a sprint while you're asleep

1. First, watch one `/sprint` go cleanly while you're present. Don't skip this.
2. Then: `scripts/sprint-headless.sh docs/plans/SETTINGS.md` runs the same thing with no
   terminal - try it once by hand.
3. Then schedule it: copy `scripts/com.redsurf.sprint.plist.example` to
   `~/Library/LaunchAgents/com.redsurf.sprint.plist`, set the hour to your window reset, and
   `launchctl load` it (instructions are in the file). Logs land in `~/.redsurf/sprint-logs/`.

Not `/schedule` - that runs in Anthropic's cloud and can't reach the Chromecast over your LAN.
`/loop 5m …` exists for polling something while a session is open; it's not a sprint starter.

## When it goes wrong

- Sprint went quiet: read the newest file in `~/.redsurf/sprint-logs/`, then
  `docs/plans/SPRINT_LOG.md` - the sprint writes its blocker there and stops on purpose rather
  than retrying all night.
- TV unreachable: `! adb connect 192.172.7.160:35631`. It stays reachable asleep (verified), but
  a router reboot changes the port - Developer options → Wireless debugging shows the new one.
- OTA stopped updating after a sprint: a debug build is probably still installed.
  `! adb uninstall com.redsurf.tv`, then install the latest release from GitHub.

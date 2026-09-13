#!/bin/bash
# Runs one RedSurf module sprint unattended (WORKFLOW.md "Sprint mode").
#
#   scripts/sprint-headless.sh docs/plans/SETTINGS.md
#
# Same thing as typing `/sprint docs/plans/SETTINGS.md` in an interactive session, but with no
# terminal attached - meant to be fired by launchd at your usage-window reset time (see the
# .plist.example next to this file). Everything the sprint does is governed by:
#   .claude/commands/sprint.md   - the sprint prompt itself
#   .claude/settings.json        - which tools run without asking (an unattended run can't answer
#                                  a permission prompt, so anything not allowed there = a stall)
#   docs/plans/SPRINT_LOG.md     - where the sprint writes its own record and any blocker
#
# Output lands in ~/.redsurf/sprint-logs/<timestamp>.log. Read that first if a sprint went quiet.

set -u
BRIEF="${1:?usage: sprint-headless.sh <path/to/brief.md>}"
REPO="$(cd "$(dirname "$0")/.." && pwd)"
LOGDIR="$HOME/.redsurf/sprint-logs"
mkdir -p "$LOGDIR"
LOG="$LOGDIR/$(date +%Y%m%d-%H%M%S)-$(basename "$BRIEF" .md).log"

# launchd jobs don't source ~/.zshrc - set the toolchain explicitly.
export JAVA_HOME="$HOME/.local/opt/jdk17/Contents/Home"
export ANDROID_HOME="$HOME/Library/Android/sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/platform-tools:/opt/homebrew/bin:/usr/local/bin:$PATH"

echo "=== sprint start $(date) brief=$BRIEF ===" | tee -a "$LOG"

# --permission-mode dontAsk + --permission-prompts none: anything .claude/settings.json doesn't
# allow is denied rather than waited on. That's deliberate - a stall is worse than a denial the
# sprint can log and stop on.
claude -p "/sprint $BRIEF" \
  --cd "$REPO" \
  --model sonnet \
  --permission-mode dontAsk \
  --permission-prompts none \
  --output-format text \
  >> "$LOG" 2>&1
STATUS=$?

echo "=== sprint end $(date) exit=$STATUS ===" | tee -a "$LOG"
osascript -e "display notification \"Sprint finished (exit $STATUS) - see $LOG\" with title \"RedSurf\" sound name \"Glass\"" 2>/dev/null || true
exit $STATUS

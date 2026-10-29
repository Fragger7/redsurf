#!/bin/bash
# Run one Gemini (Antigravity) builder task headless, as the separate macOS user "agy" in its own
# clone (/Users/agy/RedSurf-agy) - it can't read admin's files or push; the TV only via redsurf-tv.
#
#   scripts/agy-run.sh 005-search-data          # build-only task: agy's own --sandbox on
#   scripts/agy-run.sh --tv 006-search-check    # level-3 task: no --sandbox (it needs sudo for redsurf-tv)
#   scripts/agy-run.sh --check                  # signed in? a tiny no-op run
#
# Live log: /Users/Shared/agy-logs/<task>.log (readable by admin). Fetch its work from admin:
#   git fetch /Users/agy/RedSurf-agy agy/NNN
# Stop: sudo -u agy pkill -f "agy -p"; then the redsurf-tv end if it used the TV.
set -u
AGY=/Users/agy/.local/bin/agy
CLONE=/Users/agy/RedSurf-agy
LOGDIR=/Users/Shared/agy-logs
as_agy() { sudo -n -u agy -H "$@"; }

sandbox=--sandbox
case "${1:-}" in
  --check)
    cd / && as_agy "$AGY" -p "Reply with the single word OK. Do nothing else." \
      --model gemini-3.8-flash-high --sandbox --print-timeout 3m
    exit $? ;;
  --tv) sandbox=; shift ;;
esac
task="${1:?usage: agy-run.sh [--tv] <task> | --check}"
[[ "$task" =~ ^[0-9]{3}-[a-z0-9-]+$ ]] || { echo "bad task name: $task" >&2; exit 1; }

cd /
as_agy mkdir -p "$LOGDIR" && as_agy chmod 755 "$LOGDIR"
log="$LOGDIR/$task.log"
as_agy test -f "$CLONE/.builder-worktree" || { echo "no .builder-worktree marker in $CLONE" >&2; exit 1; }

as_agy sh -c "echo '=== $(date '+%F %T') start $task ${sandbox:-(tv)} ===' >> '$log'"
# caffeinate keeps the Mac awake only while this run lasts.
caffeinate -is sudo -n -u agy -H sh -c "cd '$CLONE' && exec '$AGY' -p 'You are the builder. Follow GEMINI.md. Do exactly the task in docs/agy/tasks/$task.md (git fetch origin first - the file is on origin/main). Do no other task.' --model gemini-3.8-flash-high $sandbox --print-timeout 25m >> '$log' 2>&1"
rc=$?
as_agy sh -c "echo '=== $(date '+%F %T') exit $rc $task ===' >> '$log'"

#!/bin/bash
# Run one Gemini (Antigravity) builder task headless in the RedSurf-agy worktree.
# Usage: scripts/agy-run.sh 002-quality-p-tokens
# Live log: ~/Development/Claude/agy-logs/<task>.log  (watch with: tail -f <that file>)
# Stop:     pkill -f "agy -p"; then ./gradlew --stop inside RedSurf-agy/tv-native
set -u
task="$1"
wt="$HOME/Development/Claude/RedSurf-agy"
logdir="$HOME/Development/Claude/agy-logs"
mkdir -p "$logdir"
log="$logdir/$task.log"

cd "$wt" || exit 1
[ -f .builder-worktree ] || { echo "no .builder-worktree marker in $wt" >&2; exit 1; }

echo "=== $(date '+%F %T') start $task ===" >> "$log"
# caffeinate keeps the Mac awake only while this run lasts.
caffeinate -is agy -p "You are the builder. Follow GEMINI.md. Do exactly the task in docs/agy/tasks/$task.md (fetch origin first - the file is on origin/main). Do no other task." \
  --model gemini-3.8-flash-high --sandbox --print-timeout 25m >> "$log" 2>&1
echo "=== $(date '+%F %T') exit $? $task ===" >> "$log"

#!/bin/bash
# Run several queued Gemini tasks one after another, one agy session per task (GEMINI.md §0).
#   scripts/agy-queue.sh 009-epg-team-match 010-event-name-parser ...
# A failed task doesn't stop the queue. Each task branches from origin/main on its own, so order
# doesn't matter. Per-task logs: /Users/Shared/agy-logs/<task>.log; the queue's timing summary
# (for measuring Gemini minutes): ~/Library/Logs/agy-queue-<date>.log.
# Stop everything: sudo -u agy pkill -f "agy -p"   (then Ctrl-C this script)
cd "$(dirname "$0")/.." || exit 1
[ $# -gt 0 ] || { echo "usage: agy-queue.sh <task> [<task>...]" >&2; exit 1; }
summary="$HOME/Library/Logs/agy-queue-$(date +%F).log"
fails=0
for task in "$@"; do
  start=$(date +%s)
  echo "$(date '+%F %T') start $task" | tee -a "$summary"
  scripts/agy-run.sh "$task"; rc=$?
  mins=$(( ($(date +%s) - start + 59) / 60 ))
  echo "$(date '+%F %T') end   $task exit=$rc minutes=$mins" | tee -a "$summary"
  [ $rc = 0 ] || fails=$((fails + 1))
done
echo "queue done: $# tasks, $fails failed - summary in $summary" | tee -a "$summary"

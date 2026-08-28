#!/bin/sh
# Finds a seed whose run reaches the flagship and lives a while doing it.
#
# The capture is deterministic, so a bad run is not tuned away -- it is re-rolled. This tries a
# handful of seeds at one level and prints what each did, so a human picks the take.
#
# usage: tools/press/sweep.sh <level> [ticks] [difficulty] [seeds...]
set -e
LEVEL=$1
TICKS=${2:-9000}
DIFF=${3:-EASY}
shift 3 2>/dev/null || shift $#
SEEDS=${*:-"1 2 3 4 5 6"}
CP="target/classes:$(cat /tmp/cp.txt)"
for S in $SEEDS; do
  ERR=$(mktemp)
  java -cp "/tmp/preview:$CP" com.hashimjacobs.spacecase.engine.CaptureHarness \
       "$LEVEL" "$TICKS" "$S" "$DIFF" 2>"$ERR" >/dev/null
  printf 'level %s seed %-3s %s\n' "$LEVEL" "$S" "$(grep 'frames,' "$ERR")"
  rm -f "$ERR"
done

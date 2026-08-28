#!/bin/sh
# One frame from a capture, at native 996x864.
#
# The frame index comes from looking at `contact.sh` output. Captures are deterministic, so the
# index that looked right there is the frame that lands here.
#
# usage: tools/press/still.sh <level> <seed> <difficulty> <boss|waves> <frame> <out.png>
set -e
LEVEL=$1; SEED=$2; DIFF=$3; MODE=$4; FRAME=$5; OUT=$6
CP="target/classes:$(cat /tmp/cp.txt)"
mkdir -p "$(dirname "$OUT")"
java -cp "/tmp/preview:$CP" com.hashimjacobs.spacecase.engine.CaptureHarness \
     "$LEVEL" "$((FRAME + 1))" "$SEED" "$DIFF" "$MODE" 2>/dev/null \
 | ffmpeg -v error -y -f rawvideo -pixel_format bgra -video_size 996x864 -i - \
     -vf "select=eq(n\,$FRAME)" -frames:v 1 "$OUT"
echo "$OUT"

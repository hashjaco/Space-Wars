#!/bin/sh
# A contact sheet of one capture, so a still can be chosen by looking rather than by guessing.
#
# Frames are deterministic, so the index picked here is the index `still.sh` will reproduce.
#
# usage: tools/press/contact.sh <level> <ticks> <seed> <difficulty> <boss|waves> <out.png> [every]
set -e
LEVEL=$1; TICKS=$2; SEED=$3; DIFF=$4; MODE=$5; OUT=$6; EVERY=${7:-60}
CP="target/classes:$(cat /tmp/cp.txt)"
[ "$MODE" = boss ] && BOSSARG=boss || BOSSARG=waves
java -cp "/tmp/preview:$CP" com.hashimjacobs.spacecase.engine.CaptureHarness \
     "$LEVEL" "$TICKS" "$SEED" "$DIFF" "$BOSSARG" 2>/dev/null \
 | ffmpeg -v error -y -f rawvideo -pixel_format bgra -video_size 996x864 -i - \
     -vf "select='not(mod(n,$EVERY))',scale=249:216,tile=5x4" \
     -frames:v 1 "$OUT"
echo "$OUT  5x4 grid, reading left-to-right; cell k is frame k*$EVERY"

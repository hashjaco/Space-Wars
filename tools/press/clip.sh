#!/bin/sh
# One framed 1920x1080 trailer clip, straight from the engine.
#
# The surround is a still plate drawn once by FrameShot; ffmpeg overlays the live arena onto it.
# Framing every frame through Java instead would be thousands of PNG round trips for a backdrop
# that never changes.
#
# usage: tools/press/clip.sh <level> <seed> <difficulty> <boss|waves> <skip> <frames> <out.mp4>
set -e
LEVEL=$1; SEED=$2; DIFF=$3; MODE=$4; SKIP=$5; FRAMES=$6; OUT=$7
CP="target/classes:$(cat /tmp/cp.txt)"
PLATE=$(mktemp -t plate).png
mkdir -p "$(dirname "$OUT")"
java -cp "target/classes:/tmp/press" FrameShot "$LEVEL" - "$PLATE" >/dev/null
java -cp "/tmp/preview:$CP" com.hashimjacobs.spacecase.engine.CaptureHarness \
     "$LEVEL" "$((SKIP + FRAMES))" "$SEED" "$DIFF" "$MODE" 2>/dev/null \
 | ffmpeg -v error -y -loop 1 -i "$PLATE" \
     -f rawvideo -pixel_format bgra -video_size 996x864 -framerate 60 -i - \
     -filter_complex "[1:v]trim=start_frame=$SKIP,setpts=PTS-STARTPTS,scale=1245:1080:flags=lanczos[game];[0:v][game]overlay=337:0:shortest=1[v]" \
     -map "[v]" -c:v libx264 -preset medium -crf 18 -pix_fmt yuv420p -r 60 "$OUT"
rm -f "$PLATE"
printf '  %-40s %s frames\n' "$OUT" "$FRAMES"

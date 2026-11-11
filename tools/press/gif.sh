#!/bin/sh
# A README gif, straight from the engine: the same seeded capture as clip.sh, at 12fps, 420px wide
# (W=360 for busy scenes) and 64 colors -- indistinguishable from 128 at this size, 25% smaller, and
# every byte of it stays in the history forever. Aim for under 3 MB.
#
# usage: tools/press/gif.sh <level> <seed> <difficulty> <boss|waves> <skip> <frames> <out.gif>
#        tools/press/gif.sh onfoot <mission> <seed> - <skip> <frames> <out.gif>
#        <raw 996x864 bgra frames> | tools/press/gif.sh - - - - 0 0 <out.gif>   (splice several captures)
set -e
LEVEL=$1; SEED=$2; DIFF=$3; MODE=$4; SKIP=$5; FRAMES=$6; OUT=$7
CP="/tmp/preview:target/classes:$(cat /tmp/cp.txt)"
mkdir -p "$(dirname "$OUT")"
if [ "$LEVEL" = - ]; then
  cat
elif [ "$LEVEL" = onfoot ]; then
  java -cp "$CP" com.hashimjacobs.spacecase.onfoot.OnFootCapture "$SEED" "$((SKIP + FRAMES))" "$DIFF"
else
  java -cp "$CP" com.hashimjacobs.spacecase.engine.CaptureHarness \
       "$LEVEL" "$((SKIP + FRAMES))" "$SEED" "$DIFF" "$MODE"
fi 2>/dev/null \
 | ffmpeg -v error -y -f rawvideo -pixel_format bgra -video_size 996x864 -framerate 60 -i - \
     -vf "trim=start_frame=$SKIP,setpts=PTS-STARTPTS,fps=12,scale=${W:-420}:-1:flags=lanczos,split[a][b];[a]palettegen=max_colors=64:stats_mode=diff[p];[b][p]paletteuse=dither=bayer:bayer_scale=5:diff_mode=rectangle" \
     "$OUT"
printf '  %-40s %s\n' "$OUT" "$(du -h "$OUT" | cut -f1)"

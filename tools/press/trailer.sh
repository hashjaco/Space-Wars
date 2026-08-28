#!/bin/sh
# The trailer, start to finish, from the engine.
#
# Eleven clips: six flying legs that walk the five galaxies in order, then five flagship fights
# ending on Aeon. The surround's accent changes galaxy to galaxy, so the frame escalates with the
# campaign. Every clip is a seeded capture -- re-running this produces the same film.
#
# Music is the game's own, composed by the author and MIT like the rest of the repo: the gameplay
# cue under the flying, the boss cue under the fights, crossfaded where the montage starts.
set -e
CD=docs/press/trailer
WORK=$CD/clips
mkdir -p "$WORK"
SND=src/main/resources/sounds

clip() { ./tools/press/clip.sh "$@"; }

echo "Flying legs"
clip  1 1 EASY waves  120 420 "$WORK/01.mp4"
clip  5 1 EASY waves  420 420 "$WORK/02.mp4"
clip 18 1 EASY waves  480 420 "$WORK/03.mp4"
clip 26 1 EASY waves  480 420 "$WORK/04.mp4"
clip 33 1 EASY waves  480 420 "$WORK/05.mp4"
clip 45 1 EASY waves  360 360 "$WORK/06.mp4"

echo "Flagships"
clip 10 1 EASY boss   660 420 "$WORK/07.mp4"
clip 20 1 EASY boss   300 360 "$WORK/08.mp4"
clip 30 1 EASY boss   420 360 "$WORK/09.mp4"
clip 40 1 EASY boss   360 360 "$WORK/10.mp4"
clip 50 1 EASY boss   360 600 "$WORK/11.mp4"

echo "End card"
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
"$CHROME" --headless --disable-gpu --hide-scrollbars --allow-file-access-from-files \
  --window-size=1920,1080 --screenshot="$WORK/endcard.png" \
  "file:///Users/hashim/Projects/Space-Wars/tools/press/capsule/endcard.html" >/dev/null 2>&1
ffmpeg -v error -y -loop 1 -i "$WORK/endcard.png" -t 7 -c:v libx264 -preset medium -crf 18 \
  -pix_fmt yuv420p -r 60 "$WORK/12.mp4"

echo "Joining"
: > "$WORK/list.txt"
for f in 01 02 03 04 05 06 07 08 09 10 11 12; do
  echo "file '$f.mp4'" >> "$WORK/list.txt"
done
ffmpeg -v error -y -f concat -safe 0 -i "$WORK/list.txt" -c copy "$CD/silent.mp4"

DUR=$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$CD/silent.mp4")
FADE=$(awk -v d="$DUR" 'BEGIN{printf "%.2f", d-4}')
echo "Scoring (${DUR}s)"
# The gameplay cue under the flying legs, the boss cue under the montage, crossfaded where the
# montage starts and faded out under the end card. Both tracks are the author's own.
ffmpeg -v error -y -i "$CD/silent.mp4" \
  -i "$SND/starlight-circuit.mp3" -i "$SND/grime-quest-remix.mp3" \
  -filter_complex "\
[1:a]atrim=0:49,asetpts=PTS-STARTPTS,afade=t=in:st=0:d=2[a1];\
[2:a]atrim=18,asetpts=PTS-STARTPTS[a2];\
[a1][a2]acrossfade=d=3:c1=tri:c2=tri[mix];\
[mix]atrim=0:${DUR},asetpts=PTS-STARTPTS,afade=t=out:st=${FADE}:d=4,volume=0.9[a]" \
  -map 0:v -map "[a]" -c:v copy -c:a aac -b:a 192k -shortest \
  "$CD/space-case-trailer.mp4"

rm -f "$CD/silent.mp4"
ffprobe -v error -show_entries format=duration:stream=width,height -of default=nw=1 "$CD/space-case-trailer.mp4"
echo "wrote $CD/space-case-trailer.mp4"

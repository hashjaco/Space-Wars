#!/bin/sh
# Regenerates every generated asset, then compresses the two music tracks.
#
# GenerateAssets.java writes plain WAV because it has no encoder; the music is converted to MP3
# afterwards to keep the packaged jar small. ffmpeg is needed for that step only.
#
# Usage:  tools/generate-assets.sh
set -eu

cd "$(dirname "$0")/.."

echo "generating sprites and audio..."
java tools/GenerateAssets.java

if ! command -v ffmpeg > /dev/null 2>&1; then
    echo "ffmpeg not found: leaving music as WAV." >&2
    echo "Install ffmpeg and re-run to produce the MP3s the game expects." >&2
    exit 1
fi

for track in main-theme battle-theme; do
    wav="src/main/resources/sounds/$track.wav"
    mp3="src/main/resources/sounds/$track.mp3"
    [ -f "$wav" ] || continue
    echo "encoding $track.mp3"
    ffmpeg -hide_banner -loglevel error -y -i "$wav" -codec:a libmp3lame -b:a 128k "$mp3"
    rm -f "$wav"
done

echo "done. See ASSETS.md for what is generated and what is hand-made."

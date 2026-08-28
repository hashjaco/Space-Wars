#!/bin/sh
# Renders the capsule template at every storefront size.
#
# Drawn at twice the target and downsampled, because Impact at 231x87 has no pixels to spare and
# the browser's own hinting at 1x is coarser than a clean 2x reduction.
#
# Storefronts reject an image that is one pixel off, so the size is asserted rather than trusted.
set -e
CHROME="/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"
PAGE="file:///Users/hashim/Projects/Space-Wars/tools/press/capsule/capsule.html"
TMP=$(mktemp -d)

shot() { # w h shape out
  W=$1; H=$2; SHAPE=$3; OUT=$4
  mkdir -p "$(dirname "$OUT")"
  # Chrome renders nothing in a window as small as the 231x87 capsule, so anything under 400px
  # wide is laid out at twice the size and reduced the rest of the way here. Every length in the
  # template is relative, so the doubled layout is the same layout.
  LW=$W; LH=$H
  if [ "$W" -lt 400 ]; then LW=$((W * 2)); LH=$((H * 2)); fi
  "$CHROME" --headless --disable-gpu --hide-scrollbars --allow-file-access-from-files \
    --force-device-scale-factor=2 --window-size="$LW,$LH" \
    --screenshot="$TMP/raw.png" "$PAGE?shape=$SHAPE" >/dev/null 2>&1
  ffmpeg -v error -y -i "$TMP/raw.png" -vf "scale=$W:$H:flags=lanczos" "$OUT"
  GOT=$(python3 -c "import struct,sys;d=open(sys.argv[1],'rb').read(33);w,h=struct.unpack('>II',d[16:24]);print(f'{w}x{h}')" "$OUT")
  [ "$GOT" = "${W}x${H}" ] || { echo "SIZE MISMATCH $OUT: wanted ${W}x${H} got $GOT" >&2; exit 1; }
  printf '  %-52s %s\n' "$OUT" "$GOT"
}

echo "Steam"
shot  616 353 wide     docs/press/capsules/steam/main-capsule-616x353.png
shot  460 215 wide     docs/press/capsules/steam/header-460x215.png
shot  231  87 tiny     docs/press/capsules/steam/small-231x87.png
shot  374 448 vertical docs/press/capsules/steam/vertical-374x448.png
shot  600 900 vertical docs/press/capsules/steam/library-600x900.png
shot 1920 620 wide     docs/press/capsules/steam/library-hero-1920x620.png
shot 1438 810 wide     docs/press/capsules/steam/page-background-1438x810.png

echo "itch.io"
shot  630 500 vertical docs/press/capsules/itch/cover-630x500.png
shot  960 400 wide     docs/press/capsules/itch/banner-960x400.png

echo "GitHub"
shot 1280 640 wide     docs/press/github/hero-1280x640.png

rm -rf "$TMP"

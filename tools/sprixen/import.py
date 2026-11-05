#!/usr/bin/env python3
"""Places downloaded Sprixen output into src/main/resources under names the generator never writes.

    import.py icons    # tools/sprixen/out/icons/garage-*.png -> sprites/garage/<name>.png (48 -> 24, nearest)
                       # tools/sprixen/out/icons/pickup-*.png -> sprites/pickup-sx-<name>.png (as is)

Only PIL. Every output path is new, so `java tools/GenerateAssets.java` never overwrites it and the
reproducibility check in CI stays clean (ASSETS.md lists them under "Sprixen").
"""
import glob, os, sys
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
OUT = os.path.join(ROOT, "tools", "sprixen", "out")
SPRITES = os.path.join(ROOT, "src", "main", "resources", "sprites")


def icons():
    os.makedirs(os.path.join(SPRITES, "garage"), exist_ok=True)
    for f in sorted(glob.glob(os.path.join(OUT, "icons", "garage-*.png"))):
        name = os.path.basename(f)[len("garage-"):]
        im = Image.open(f).convert("RGBA").resize((24, 24), Image.NEAREST)
        im.save(os.path.join(SPRITES, "garage", name))
        print("garage/" + name)
    for f in sorted(glob.glob(os.path.join(OUT, "icons", "pickup-*.png"))):
        name = os.path.basename(f)[len("pickup-"):]
        im = Image.open(f).convert("RGBA")
        if not im.getchannel("A").getbbox():
            print("skip empty", name); continue
        im.save(os.path.join(SPRITES, "pickup-sx-" + name))
        print("pickup-sx-" + name)


if __name__ == "__main__":
    {"icons": icons}[sys.argv[1]]()

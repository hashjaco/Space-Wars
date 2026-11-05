#!/usr/bin/env python3
"""Places downloaded Sprixen output into src/main/resources under names the generator never writes.

    import.py icons    # tools/sprixen/out/icons/garage-*.png -> sprites/garage/<name>.png (48 -> 24, nearest)
                       # tools/sprixen/out/icons/pickup-*.png -> sprites/pickup-sx-<name>.png (as is)
    import.py vfx <name> <px> [--drop-empty]
                       # tools/sprixen/out/vfx/<name>/ (an unzipped Sprixen VFX export, v1 or v2)
                       # -> sprites/fx-<name>/1..N.png, each frame scaled to <px> square

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


def vfx(name, px, drop_empty=False):
    """Slices a Sprixen VFX sheet into the numbered per-frame PNGs the runtime addresses."""
    import json
    src = os.path.join(OUT, "vfx", name)
    meta = json.load(open(os.path.join(src, "effect.json")))
    ss, count = meta["spriteSheet"], meta["animation"]["frames"]
    sheet = Image.open(os.path.join(src, ss["file"])).convert("RGBA")
    w, h = ss["frameWidth"], ss["frameHeight"]
    frames = [sheet.crop((c * w, r * h, (c + 1) * w, (r + 1) * h))
              for r in range(ss["rows"]) for c in range(ss["columns"])][:count]
    if drop_empty:
        frames = [f for f in frames if f.getchannel("A").getbbox()]
    out = os.path.join(SPRITES, "fx-" + name)
    os.makedirs(out, exist_ok=True)
    for i, frame in enumerate(frames, start=1):
        frame.resize((px, px), Image.LANCZOS).save(os.path.join(out, f"{i}.png"), optimize=True)
    print(f"fx-{name}: {len(frames)} frames at {px}px, {meta['animation']['fps']} fps")


if __name__ == "__main__":
    if sys.argv[1] == "icons":
        icons()
    elif sys.argv[1] == "vfx":
        vfx(sys.argv[2], int(sys.argv[3]), "--drop-empty" in sys.argv)

#!/usr/bin/env python3
"""Places downloaded Sprixen output into src/main/resources under names the generator never writes.

    import.py icons    # tools/sprixen/out/icons/garage-*.png -> sprites/garage/<name>.png (48 -> 24, nearest)
                       # tools/sprixen/out/icons/pickup-*.png -> sprites/pickup-sx-<name>.png (as is)
    import.py vfx <name> <px> [--drop-empty] [--frames 1,2,4] [--trim]
                       # --frames keeps those sheet frames, in that order; --trim crops every frame to
                       # one shared square around the art, so a small projectile fills its box
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


def vfx(name, px, drop_empty=False, pick=None, trim=False):
    """Slices a Sprixen VFX sheet into the numbered per-frame PNGs the runtime addresses."""
    import json
    src = os.path.join(OUT, "vfx", name)
    meta = json.load(open(os.path.join(src, "effect.json")))
    ss, count = meta["spriteSheet"], meta["animation"]["frames"]
    sheet = Image.open(os.path.join(src, ss["file"])).convert("RGBA")
    w, h = ss["frameWidth"], ss["frameHeight"]
    frames = [sheet.crop((c * w, r * h, (c + 1) * w, (r + 1) * h))
              for r in range(ss["rows"]) for c in range(ss["columns"])][:count]
    if pick:
        frames = [frames[i - 1] for i in pick]
    if drop_empty:
        frames = [f for f in frames if f.getchannel("A").getbbox()]
    if trim:
        boxes = [f.getchannel("A").point(lambda a: 255 if a > 24 else 0).getbbox() for f in frames]
        l, t = min(b[0] for b in boxes), min(b[1] for b in boxes)
        r, b = max(b[2] for b in boxes), max(b[3] for b in boxes)
        side, cx, cy = max(r - l, b - t) + 4, (l + r) // 2, (t + b) // 2
        frames = [f.crop((cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side)) for f in frames]
    out = os.path.join(SPRITES, "fx-" + name)
    os.makedirs(out, exist_ok=True)
    for i, frame in enumerate(frames, start=1):
        frame.resize((px, px), Image.LANCZOS).save(os.path.join(out, f"{i}.png"), optimize=True)
    print(f"fx-{name}: {len(frames)} frames at {px}px, {meta['animation']['fps']} fps")


if __name__ == "__main__":
    if sys.argv[1] == "icons":
        icons()
    elif sys.argv[1] == "vfx":
        pick = [int(n) for n in sys.argv[sys.argv.index("--frames") + 1].split(",")] if "--frames" in sys.argv else None
        vfx(sys.argv[2], int(sys.argv[3]), "--drop-empty" in sys.argv, pick, "--trim" in sys.argv)

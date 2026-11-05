#!/usr/bin/env python3
"""Phase 0 contact sheet: one row per test boss, one column per route, a frame from each loop that exists.
    python3 tools/sprixen/cg_sheet.py   -> tools/sprixen/out/cg-test/sheet.png
"""
import os
from PIL import Image, ImageDraw

OUT = "tools/sprixen/out/cg-test"
CELL, PAD = 360, 14
COLS = [
    ("A  Sprixen 2D still (2 cr)", lambda n: f"{OUT}/A-{n}-{1 if n == 'hydra' else 0}.png"),
    ("A  premium loop, frame 20 (8 cr)", lambda n: f"{OUT}/A-premium-{n.split('-')[0]}-frames/20.png"),
    ("A  premium loop, frame 60", lambda n: f"{OUT}/A-premium-{n.split('-')[0]}-frames/60.png"),
    ("B  3D model + Blender, idle (10 cr)", lambda n: f"{OUT}/B-{n}-frames/1.png"),
    ("B  Blender rage state (free)", lambda n: f"{OUT}/B-{n}-frames/rage/1.png"),
    ("C  Flux concept, keyed (no credits)", lambda n: f"{OUT}/C-{n}.png"),
]
ROWS = ["hydra", "cinder-warden"]
W = PAD + len(COLS) * (CELL + PAD); H = 40 + len(ROWS) * (CELL + PAD + 24)
sheet = Image.new("RGB", (W, H), (22, 26, 38)); d = ImageDraw.Draw(sheet)
for c, (label, _) in enumerate(COLS):
    d.text((PAD + c * (CELL + PAD), 12), label, fill=(200, 210, 230))
for r, name in enumerate(ROWS):
    y = 40 + r * (CELL + PAD + 24)
    d.text((PAD, y), name.upper(), fill=(255, 209, 102))
    for c, (_, path) in enumerate(COLS):
        x = PAD + c * (CELL + PAD); p = path(name)
        if not os.path.exists(p):
            d.rectangle((x, y + 20, x + CELL, y + 20 + CELL), outline=(70, 75, 90)); d.text((x + 10, y + 30), "not available", fill=(120, 125, 140)); continue
        im = Image.open(p).convert("RGBA"); im.thumbnail((CELL, CELL))
        cell = Image.new("RGBA", (CELL, CELL), (10, 14, 26, 255)); cell.alpha_composite(im, ((CELL - im.width) // 2, (CELL - im.height) // 2))
        sheet.paste(cell.convert("RGB"), (x, y + 20))
sheet.save(f"{OUT}/sheet.png"); print("wrote", f"{OUT}/sheet.png", sheet.size)

#!/usr/bin/env python3
"""Each on-foot mission's backdrop and floor, from Tripo text-to-image (5 cr an image).

    python3 tools/tripo/stages.py [folder...]   # -> src/main/resources/sprites/onfoot/<folder>/{backdrop,floor}.png

Tripo returns a square; the backdrop is its middle band at 2:1 and the floor a strip, both cut and sized
to the Undercity's own (1024x512, 1024x214). Raw images stay in the gitignored out/onfoot/stages/.
"""
import glob, os, shutil, subprocess, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(HERE, "out", "onfoot", "stages")
SPRITES = os.path.join(ROOT, "src/main/resources/sprites/onfoot")
ENV = dict(os.environ, TRIPO_CLIENT_APP="claude")

BACK = ("A wide side-view background painting for a 2D side-scrolling brawler, {}, painterly pre-rendered CGI, "
        "deep shadow and haze, no characters, no text, no frame, the bottom fifth an empty dark floor area.")
FLOOR = ("A seamless side-view strip of {}, seen exactly side-on, the top edge a flat walkway, filling the whole "
         "frame edge to edge, pre-rendered CGI, no characters, no text, no frame.")
STAGES = {
    "forgeworks": ("a vast alien forge foundry hall: rivers of molten metal in channels, giant tilted crucibles, "
                   "black iron gantries and chains, orange furnace glow through smoke",
                   "black iron grated foundry walkway with glowing orange cracks and slag stains"),
    "under-ice": ("a cavern beneath a frozen alien sea: translucent blue ice walls and pillars, hanging icicles, "
                  "pale cyan light filtering down through the ice, dark water glints, cold mist",
                  "a thick frozen ice floor slab with frost, cracks and embedded rocks, pale blue"),
    "mag-storm": ("magnetized storm caverns: iron-rich rock pillars with copper veins, floating rock shards, "
                  "blue-violet lightning arcs jumping between pillars, dark and stormy",
                  "dark magnetite rock floor with glowing copper veins and scattered metal filings"),
    "hulk-drift": ("the interior of a vast derelict alien starship: broken hull ribs, breaches open to black space "
                   "and stars, dim violet emergency light, floating debris and dead cables",
                   "derelict starship deck plating, scuffed dark metal panels with dim violet strip lights"),
}


def generate(prompt, dest):
    if os.path.exists(dest):
        return
    tmp = dest + ".tmp"; os.makedirs(tmp, exist_ok=True)
    p = subprocess.run(["tripo", "generate", "text-to-image", prompt, "-o", tmp, "--yes", "--json", "--no-open"],
                       capture_output=True, text=True, env=ENV)
    if p.returncode:
        raise SystemExit(p.stdout[-500:] + p.stderr[-500:])
    Image.open(glob.glob(os.path.join(tmp, "**", "generated_image.png"), recursive=True)[0]).convert("RGB").save(dest)
    shutil.rmtree(tmp)


def band(im, aspect, size):
    w, h = im.size
    bh = round(w / aspect)
    top = (h - bh) // 2
    out = im.crop((0, top, w, top + bh)).resize(size, Image.LANCZOS)
    return out.quantize(256, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)


for folder in sys.argv[1:] or STAGES:
    back, floor = STAGES[folder]
    os.makedirs(OUT, exist_ok=True)
    generate(BACK.format(back), os.path.join(OUT, folder + "-backdrop.png"))
    generate(FLOOR.format(floor), os.path.join(OUT, folder + "-floor.png"))
    d = os.path.join(SPRITES, folder); os.makedirs(d, exist_ok=True)
    band(Image.open(os.path.join(OUT, folder + "-backdrop.png")), 2, (1024, 512)).save(os.path.join(d, "backdrop.png"), optimize=True)
    band(Image.open(os.path.join(OUT, folder + "-floor.png")), 1024 / 214, (1024, 214)).save(os.path.join(d, "floor.png"), optimize=True)
    print(folder, "->", d, flush=True)

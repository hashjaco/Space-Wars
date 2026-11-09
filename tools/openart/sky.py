#!/usr/bin/env python3
"""CG galaxy skies: far/mid/near parallax layers per galaxy from OpenArt, for Level.SKIES.

    python3 tools/openart/sky.py gen [galaxy...]      # 2 variants per layer (nano-banana-pro 2K, ~40 cr each)
    python3 tools/openart/sky.py sheet                # out/sky.png: each variant tiled twice, to check the seam
    python3 tools/openart/sky.py pick <galaxy> <layer> <0|1>
    python3 tools/openart/sky.py import [galaxy...]   # -> sprites/sky-cg/<galaxy>/{far,mid,near}.png

    --side on gen/sheet/pick/import: the set for the side-on levels, sprites/sky-cg/<galaxy>-side/. Its
    far layer is the top-down pick re-seamed left-to-right (free); mid and near are generated afresh
    with their streaks running sideways, since the overlays carry a direction and the far does not.

far is opaque, referenced on the universe map's sky for that galaxy (map-cg/sky-<galaxy>.png).
mid and near are luminous overlays generated on black; alpha is the brightest channel, un-premultiplied.
Every layer is made seamless top-to-bottom, because Renderer scrolls two copies end to end.
"""
import glob, json, os, shutil, subprocess, sys
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(HERE, "out")
STATE = os.path.join(HERE, ".sky-state.json")
SPRITES = os.path.join(ROOT, "src/main/resources/sprites")
W, H, BAND = 996, 864, 200  # GameConfig arena; BAND is the cross-faded strip at each end

LAYERS = {
 "VERDANCE": ["a deep-space nebula in sickly green and black, faint spore clouds, sparse dim stars",
              "drifting glowing yellow-green spores of different sizes and a few faint translucent resin threads",
              "a handful of large soft-focus glowing yellow-green spore sacs drifting past the camera"],
 "ASHFALL":  ["a dark smoky red-black nebula with faint molten-orange veins, sparse dim stars",
              "drifting glowing orange embers and grey-lit ash flecks of different sizes",
              "a handful of large motion-blurred burning cinders and glowing slag fragments"],
 "CRYONIS":  ["a dark cold blue-black nebula with frozen pale-cyan gas veils, sparse dim stars",
              "drifting pale cyan ice motes and tiny frost crystals of different sizes",
              "a handful of large translucent ice shards catching cold cyan light, soft focus"],
 "TEMPEST":  ["a dark slate-blue storm nebula with the faint glow of distant lightning, sparse dim stars",
              "thin wisps of storm cloud lit from inside by faint blue-white lightning",
              "a few crackling branches of white-blue lightning and bright cloud wisps"],
 "NULL":     ["a near-black void with faint violet nebula filaments and sparse cold stars",
              "faint drifting violet light filaments and glowing motes of different sizes",
              "a handful of long glowing violet tendrils drifting past the camera, soft focus"],
}
FAR = (" Looking straight down into space: the scrolling background of a vertical shoot-'em-up. Dark and low "
       "contrast so bright sprites read on top of it, detail spread evenly over the whole frame with no single "
       "focal point, no planets, no ships, no text. Match the palette and mood of the reference image.")
OVERLAY = (" Isolated on a pure solid black background, full-bleed square image filling the frame edge to edge with no "
           "border, panel or frame. Sparse, with lots of empty black space, spread evenly over the whole frame, glowing "
           "so it reads against black. Any motion streaks run straight up and down, never diagonal. No text.")
NAMES = ["far", "mid", "near"]
SIDE = "--side" in sys.argv
if SIDE:
    sys.argv.remove("--side")
    OVERLAY = OVERLAY.replace("Any motion streaks run straight up and down, never diagonal.",
                              "Any motion streaks run straight left and right, never diagonal.")
AXIS = 1 if SIDE else 0

def key(g):
    return g.upper() + ("-SIDE" if SIDE else "")

def gen_dir(g, layer):
    # The side set shares the top-down far: only its overlays are generated.
    return os.path.join(OUT, g.lower() + ("-side" if SIDE and layer != "far" else ""), layer)

def load():
    return json.load(open(STATE)) if os.path.exists(STATE) else {}

def run(args):
    p = subprocess.run(args, capture_output=True, text=True)
    if p.returncode:
        raise SystemExit(f"{args[0]} exit {p.returncode}: {p.stdout[-400:]} {p.stderr[-400:]}")

def cmd_gen(galaxies):
    for g in [x.upper() for x in galaxies] or LAYERS:
        for li, layer in enumerate(NAMES):
            if SIDE and layer == "far":
                continue
            d = gen_dir(g, layer); os.makedirs(d, exist_ok=True)
            args = ["openart", "generate", "image", LAYERS[g][li] + (FAR if li == 0 else OVERLAY),
                    "--model", "nano-banana-pro", "--yes", "--no-input", "--quiet", "--timeout", "5m"]
            if li == 0:
                args += ["--image", os.path.join(SPRITES, f"map-cg/sky-{g.lower()}.png")]
            for v in range(2):
                dest = os.path.join(d, f"{v}.png")
                if os.path.exists(dest):
                    continue
                tmp = os.path.join(d, f"tmp{v}"); os.makedirs(tmp, exist_ok=True)
                run(args + ["-o", tmp + "/"])
                Image.open(sorted(glob.glob(tmp + "/*"))[0]).convert("RGB").save(dest); shutil.rmtree(tmp)
                print(g, layer, v, flush=True)

def fit(im):
    """Cover-crop to the arena's aspect, then resize to it."""
    w, h = im.size
    if w / h > W / H:
        nw = round(h * W / H); im = im.crop(((w - nw) // 2, 0, (w - nw) // 2 + nw, h))
    else:
        nh = round(w * H / W); im = im.crop((0, (h - nh) // 2, w, (h - nh) // 2 + nh))
    return im.resize((W, H), Image.LANCZOS)

def seamless(a):
    """Cross-fade each end into the image rolled by half: the rolled copy wraps continuously, so top meets bottom."""
    n = a.shape[AXIS]
    rolled = np.roll(a, n // 2, axis=AXIS)
    y = np.arange(n, dtype=np.float32)
    t = np.clip(np.minimum(y, n - 1 - y) / BAND, 0, 1)
    t = t * t * (3 - 2 * t)
    w = t[:, None, None] if AXIS == 0 else t[None, :, None]
    return a * w + rolled * (1 - w)

def layer_rgba(path, layer):
    rgb = np.asarray(fit(Image.open(path).convert("RGB")), dtype=np.float32) / 255
    if layer == "far":
        # ponytail: dimmed by half, measured in play: at full strength the hostiles and shots get lost in it
        rgba = np.dstack([rgb * 0.5, np.ones(rgb.shape[:2], np.float32)])
    else:  # black is clear: alpha is the brightest channel, colour divided back out of it
        # ponytail: fixed opacity per layer; near is the fastest and biggest, so it is held back from the bullets
        a = np.clip((rgb.max(axis=2) - 0.04) / 0.96, 0, 1) * {"mid": 0.5, "near": 0.35}[layer]
        rgba = np.dstack([rgb, a])
    premul = np.dstack([rgba[..., :3] * rgba[..., 3:], rgba[..., 3:]])
    premul = seamless(premul)
    a = premul[..., 3:]
    rgb = np.where(a > 1e-4, premul[..., :3] / np.maximum(a, 1e-4), 0)
    return Image.fromarray((np.dstack([np.clip(rgb, 0, 1), a]) * 255 + 0.5).astype(np.uint8), "RGBA")

def cmd_sheet(_):
    rows = []
    for g in LAYERS:
        for layer in NAMES:
            for v in range(2):
                p = os.path.join(gen_dir(g, layer), f"{v}.png")
                if SIDE and layer == "far":
                    p = os.path.join(gen_dir(g, layer), f"{load().get(g, {}).get('far', 0)}.png")
                if os.path.exists(p):
                    rows.append((g, layer, v, layer_rgba(p, layer)))
    cw, ch = W // 4, H // 4
    sheet = Image.new("RGBA", (cw * 6, ch * 2 * 5), (24, 20, 40, 255))
    for g, layer, v, im in rows:
        x = (NAMES.index(layer) * 2 + v) * cw; y = list(LAYERS).index(g) * ch * 2
        small = im.resize((cw, ch))
        if SIDE:  # tiled twice side by side, across the two variant columns' width
            half = small.resize((cw // 2, ch)); sheet.alpha_composite(half, (x, y)); sheet.alpha_composite(half, (x + cw // 2, y))
        else:
            sheet.alpha_composite(small, (x, y)); sheet.alpha_composite(small, (x, y + ch))  # tiled twice
    sheet.save(os.path.join(OUT, "sky-side.png" if SIDE else "sky.png")); print(len(rows), "variants")

def cmd_pick(args):
    s = load(); s.setdefault(key(args[0]), {})[args[1]] = int(args[2]); json.dump(s, open(STATE, "w"), indent=1)

def cmd_import(galaxies):
    s = load()
    for g in [x.upper() for x in galaxies] or [k for k in s if len(s[k]) == (2 if SIDE else 3) and k.endswith("-SIDE") == SIDE]:
        g = g.replace("-SIDE", "")
        dest = os.path.join(SPRITES, "sky-cg", g.lower() + ("-side" if SIDE else "")); os.makedirs(dest, exist_ok=True)
        for layer in NAMES:
            pick = s[g][layer] if SIDE and layer == "far" else s[key(g)][layer]
            im = layer_rgba(os.path.join(gen_dir(g, layer), f"{pick}.png"), layer)
            (im.convert("RGB") if layer == "far" else im).save(f"{dest}/{layer}.png", optimize=True)  # far is opaque
        print(g, "->", dest)

if __name__ == "__main__":
    cmd, args = (sys.argv[1] if len(sys.argv) > 1 else "sheet"), sys.argv[2:]
    {"gen": cmd_gen, "sheet": cmd_sheet, "pick": cmd_pick, "import": cmd_import}[cmd](args)

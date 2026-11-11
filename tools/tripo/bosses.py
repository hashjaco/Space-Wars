#!/usr/bin/env python3
"""Side-on flagships in true profile: a Tripo model from the boss's own plan view, Blender loops from the beam.

    python3 tools/tripo/bosses.py model [slug...]    # Tripo image-to-model on the current art's frame 1 (H3.1, 30 cr each)
    python3 tools/tripo/bosses.py check <slug> [tilt...]  # out/boss-check/<slug>.png: side stills, 4 yaws per tilt, to set yaw/tilt
    python3 tools/tripo/bosses.py preview [slug...]  # out/boss-check/preview.png: each boss as the manifest has it
    python3 tools/tripo/bosses.py render [slug...]   # idle (sway), rage (twitch) and death (sink), 24 frames each
    python3 tools/tripo/bosses.py import [slug...]   # -> sprites/boss-<slug>-side/{N,rage/N,death/N}.png, prints the box

A model made from a straight-down still comes out as a thick relief standing on its tail, so every
one needs a tilt as well as a yaw; `check` is how to find them. Models and renders stay in the
gitignored out/; the imported frames are what gets committed.
"""
import glob, json, os, shutil, subprocess, sys
from concurrent.futures import ThreadPoolExecutor
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(HERE, "out")
MAN = json.load(open(os.path.join(HERE, "manifest", "bosses-side.json")))["bosses"]
SPRITES = os.path.join(ROOT, "src/main/resources/sprites")
BLENDER = "/Applications/Blender.app/Contents/MacOS/Blender"
SCRIPT = os.path.join(HERE, "render_blender.py")
LOOPS = {"": "sway", "rage": "twitch", "death": "sink"}  # BossArt.State directory -> motion
FRAMES, PX, ZOOM = 24, 620, 1.25  # about twice the on-screen length, with room for the twitch

def run(args, env=None):
    p = subprocess.run(args, capture_output=True, text=True, env=env)
    if p.returncode:
        raise SystemExit(f"{args[0]} exit {p.returncode}: {p.stdout[-400:]} {p.stderr[-400:]}")
    return p.stdout

def glb(slug):
    found = sorted(glob.glob(os.path.join(OUT, "models", "boss-" + slug, "**", "*.glb"), recursive=True), key=os.path.getmtime)
    return found[-1] if found else None

def blender(slug, out, yaw, px, frames, motion, tilt, zoom=1.08):
    run([BLENDER, "-b", "--factory-startup", "-P", SCRIPT, "--", glb(slug), out, str(yaw), str(px), str(frames),
         "side", motion, tilt, str(zoom)])

def cmd_model(slugs):
    def one(slug):
        d = os.path.join(OUT, "models", "boss-" + slug); os.makedirs(d, exist_ok=True)
        im = Image.open(os.path.join(SPRITES, MAN[slug]["src"], "1.png")).convert("RGBA")
        im = im.crop(im.getbbox())
        side = int(max(im.size) * 1.25)
        bg = Image.new("RGB", (side, side), (205, 205, 205))
        bg.paste(im, ((side - im.width) // 2, (side - im.height) // 2), im)
        src = os.path.join(d, "source.png"); bg.resize((1024, 1024), Image.LANCZOS).save(src)
        res = json.loads(run(["tripo", "make", src, "--model", "H3.1", "--out", d, "--yes", "--no-open", "--json"],
                             dict(os.environ, TRIPO_CLIENT_APP="claude")).strip().splitlines()[-1])
        return slug, res.get("credits_consumed"), res.get("model_file")
    with ThreadPoolExecutor(4) as pool:
        for row in pool.map(one, slugs or [s for s in MAN if not glb(s)]):
            print(*row, flush=True)

def cmd_check(args):
    slug, tilts = args[0], args[1:] or ["0,0,0", "0,90,0", "0,-90,0", "90,0,0", "-90,0,0"]
    yaws = (0, 90, 180, -90)
    jobs = [(t, y) for t in tilts for y in yaws]
    d = os.path.join(OUT, "boss-check", slug); shutil.rmtree(d, ignore_errors=True)
    with ThreadPoolExecutor(5) as pool:
        list(pool.map(lambda j: blender(slug, os.path.join(d, f"{j[0]}_{j[1]}"), j[1], 256, 1, "none", j[0]), jobs))
    sheet = Image.new("RGB", (256 * len(yaws), 256 * len(tilts)), (40, 44, 60))
    for (t, y) in jobs:
        im = Image.open(os.path.join(d, f"{t}_{y}", "0001.png")).convert("RGBA")
        sheet.paste(im, (yaws.index(y) * 256, tilts.index(t) * 256), im)
    dest = os.path.join(OUT, "boss-check", slug + ".png"); sheet.save(dest)
    print(dest, "rows (tilt):", tilts, "columns (yaw):", yaws)

def cmd_preview(slugs):
    """One still of each boss as the manifest has it, nose left, in out/boss-check/preview.png."""
    slugs = slugs or list(MAN)
    d = os.path.join(OUT, "boss-check", "_preview"); shutil.rmtree(d, ignore_errors=True)
    with ThreadPoolExecutor(5) as pool:
        list(pool.map(lambda s: blender(s, os.path.join(d, s), MAN[s]["yaw"], 320, 1, "none", MAN[s]["tilt"], ZOOM), slugs))
    sheet = Image.new("RGB", (320 * 5, 320 * ((len(slugs) + 4) // 5)), (40, 44, 60))
    for i, s in enumerate(slugs):
        im = finished(s, Image.open(os.path.join(d, s, "0001.png")).convert("RGBA"))
        sheet.paste(im, ((i % 5) * 320 + (320 - im.width) // 2, (i // 5) * 320 + (320 - im.height) // 2), im)
    sheet.save(os.path.join(OUT, "boss-check", "preview.png")); print(slugs)

def finished(slug, im):
    """A rendered frame as it ships: nose left, and levelled."""
    if MAN[slug].get("mirror", True):
        im = im.transpose(Image.Transpose.FLIP_LEFT_RIGHT)
    return im.rotate(MAN[slug].get("level", 0), resample=Image.BICUBIC) if MAN[slug].get("level", 0) else im

def cmd_render(slugs):
    def one(slug):
        for state, motion in LOOPS.items():
            d = os.path.join(OUT, "renders-boss", slug, state or "idle"); shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
            blender(slug, d, MAN[slug].get("yaw", 0), PX, FRAMES, motion, MAN[slug].get("tilt", "0,0,0"), ZOOM)
        return slug
    with ThreadPoolExecutor(4) as pool:
        for slug in pool.map(one, slugs or [s for s in MAN if glb(s) and "yaw" in MAN[s]]):
            print(slug, "rendered", flush=True)

def cmd_import(slugs):
    for slug in slugs or sorted(os.listdir(os.path.join(OUT, "renders-boss"))):
        loops = {state: [finished(slug, Image.open(os.path.join(OUT, "renders-boss", slug, state or "idle", f"{i:04d}.png"))
                         .convert("RGBA")) for i in range(1, FRAMES + 1)]
                 for state in LOOPS}
        box = None  # the idle and rage loops' bounds; the death loop only shrinks inside them
        for f in loops[""] + loops["rage"]:
            b = f.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
            box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
        dest = os.path.join(SPRITES, f"boss-{slug}-side"); shutil.rmtree(dest, ignore_errors=True)
        for state, frames in loops.items():
            os.makedirs(os.path.join(dest, state))
            for i, f in enumerate(frames, start=1):
                f = f.crop(box).resize(((box[2] - box[0]) // 2, (box[3] - box[1]) // 2), Image.LANCZOS)
                # Snap alpha first: FASTOCTREE averages it, and a half-clear fringe reads as a halo.
                f.putalpha(f.getchannel("A").point(lambda a: 255 if a > 127 else 0))
                f.quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(
                    os.path.join(dest, state, f"{i}.png"), optimize=True)
        print(slug, "box", ((box[2] - box[0]) // 2, (box[3] - box[1]) // 2), flush=True)

if __name__ == "__main__":
    {"model": cmd_model, "check": cmd_check, "preview": cmd_preview, "render": cmd_render, "import": cmd_import}[sys.argv[1]](sys.argv[2:])

#!/usr/bin/env python3
"""CG hostiles: an OpenArt concept, a Tripo 3D model, a Blender loop, imported as EnemyArt frames.

    python3 tools/tripo/enemies.py concept [slug...]   # 2 OpenArt variants per design (nano-banana-pro, ~40 cr each)
    python3 tools/tripo/enemies.py sheet               # out/concepts.png contact sheet to pick from
    python3 tools/tripo/enemies.py pick <slug> <0|1>
    python3 tools/tripo/enemies.py model [slug...]     # Tripo image-to-model on the pick (H3.1, 30 cr, textured by default)
    python3 tools/tripo/enemies.py render [slug...]    # headless Blender, 24-frame loop, nose down
    python3 tools/tripo/enemies.py import [slug...]    # -> sprites/enemy-cg/<slug>{,-side}/1..24.png

A slug is <galaxy>-<kind>, e.g. verdance-scout. State lives in the gitignored .tripo-state.json; the
imported frames are what gets committed. With no slugs, a command runs every design that is ready for it.
"""
import glob, json, os, shutil, subprocess, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(HERE, "out")
STATE = os.path.join(HERE, ".tripo-state.json")
MAN = json.load(open(os.path.join(HERE, "manifest", "enemies.json")))
FLAVOUR = json.load(open(os.path.join(ROOT, "tools/sprixen/manifest/bosses.json")))["flavour"]
SPRITES = os.path.join(ROOT, "src/main/resources/sprites")
BLENDER = "/Applications/Blender.app/Contents/MacOS/Blender"
FRAMES = 24
# Rendered at about 2.25x the hitbox, the scale the generated level-N/enemy-*.png use (46x41 -> 104x92).
RENDER_PX = {"SCOUT": 112, "FIGHTER": 152, "CRUISER": 192}
SLUGS = [f"{g.lower()}-{k.lower()}" for g in MAN["refs"] for k in MAN["kinds"]]

def load():
    return json.load(open(STATE)) if os.path.exists(STATE) else {}

def save(s):
    json.dump(s, open(STATE, "w"), indent=1)

def parts(slug):
    g, k = slug.split("-")
    return g.upper(), k.upper()

def prompt(slug):
    g, k = parts(slug)
    return ("A single enemy spaceship for a sci-fi shoot-'em-up: " + MAN["kinds"][k] + ". Style: " + FLAVOUR[g] +
            ". Match the materials, palette and rendering of the reference image, but this is a different, smaller ship. "
            "Three-quarter view from above and slightly behind, the whole ship visible and centred with margin around it, "
            "plain flat light-grey studio background, soft even lighting, pre-rendered 3D CGI look, one object, no text, no base, no ground.")

def run(args, env=None):
    p = subprocess.run(args, capture_output=True, text=True, env=env)
    if p.returncode:
        raise SystemExit(f"{args[0]} exit {p.returncode}: {p.stdout[-400:]} {p.stderr[-400:]}")
    return p.stdout

def cmd_concept(slugs):
    for slug in slugs or SLUGS:
        d = os.path.join(OUT, "concepts", slug); os.makedirs(d, exist_ok=True)
        ref = os.path.join(SPRITES, MAN["refs"][parts(slug)[0]])
        for v in range(2):
            dest = os.path.join(d, f"{v}.png")
            if os.path.exists(dest):
                continue
            tmp = os.path.join(d, f"tmp{v}"); os.makedirs(tmp, exist_ok=True)
            run(["openart", "generate", "image", prompt(slug), "--model", "nano-banana-pro", "--image", ref,
                 "-o", tmp + "/", "--yes", "--no-input", "--quiet", "--timeout", "5m"])
            got = sorted(glob.glob(tmp + "/*"))
            Image.open(got[0]).convert("RGB").save(dest); shutil.rmtree(tmp)
            print(slug, v, "->", dest, flush=True)

def cmd_sheet(_):
    cell = 256
    rows = [s for s in SLUGS if os.path.exists(os.path.join(OUT, "concepts", s, "0.png"))]
    sheet = Image.new("RGB", (cell * 2, cell * len(rows)), "black")
    for r, s in enumerate(rows):
        for v in range(2):
            p = os.path.join(OUT, "concepts", s, f"{v}.png")
            if os.path.exists(p):
                sheet.paste(Image.open(p).convert("RGB").resize((cell, cell)), (v * cell, r * cell))
    sheet.save(os.path.join(OUT, "concepts.png")); print("rows:", rows)

def cmd_pick(args):
    s = load(); s.setdefault(args[0], {})["pick"] = int(args[1]); save(s)

def cmd_model(slugs):
    s = load()
    for slug in slugs or [k for k, v in s.items() if "pick" in v and not glb(k)]:
        src = os.path.join(OUT, "concepts", slug, f"{s[slug]['pick']}.png")
        d = os.path.join(OUT, "models", slug); os.makedirs(d, exist_ok=True)
        env = dict(os.environ, TRIPO_CLIENT_APP="claude")
        res = json.loads(run(["tripo", "make", src, "--model", "H3.1", "--out", d,
                              "--yes", "--no-open", "--json"], env).strip().splitlines()[-1])
        print(slug, "->", res.get("model_file"), res.get("task_id"), flush=True)  # no state write: runs in parallel

def glb(slug):
    found = sorted(glob.glob(os.path.join(OUT, "models", slug, "**", "*.glb"), recursive=True), key=os.path.getmtime)
    return found[-1] if found else None

def cmd_render(slugs):
    for slug in slugs or [k for k in SLUGS if glb(k)]:
        d = os.path.join(OUT, "renders", slug); shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
        yaw = MAN["yaw"].get(slug, 0)
        run([BLENDER, "-b", "--factory-startup", "-P", os.path.join(HERE, "render_blender.py"), "--",
             glb(slug), d, str(yaw), str(RENDER_PX[parts(slug)[1]]), str(FRAMES)])
        print(slug, "->", d, flush=True)

def turned(im):
    # Same quarter turn as tools/sprixen/cg.py: side-on levels fly right-to-left, so nose-down turns to point left.
    return im.rotate(-90, expand=True)

def cmd_import(slugs):
    for slug in slugs or sorted(os.listdir(os.path.join(OUT, "renders"))):
        frames = [Image.open(os.path.join(OUT, "renders", slug, f"{i:04d}.png")).convert("RGBA") for i in range(1, FRAMES + 1)]
        box = None  # union of alpha bounds, so the loop does not jitter inside its box
        for f in frames:
            b = f.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
            box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
        for name, xf in ((slug, lambda im: im), (slug + "-side", turned)):
            dest = os.path.join(SPRITES, "enemy-cg", name); shutil.rmtree(dest, ignore_errors=True); os.makedirs(dest)
            for i, f in enumerate(frames, start=1):
                xf(f.crop(box)).quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(
                    f"{dest}/{i}.png", optimize=True)
        print(slug, box, "->", "enemy-cg/" + slug, flush=True)

if __name__ == "__main__":
    cmd, args = (sys.argv[1] if len(sys.argv) > 1 else "sheet"), sys.argv[2:]
    {"concept": cmd_concept, "sheet": cmd_sheet, "pick": cmd_pick, "model": cmd_model,
     "render": cmd_render, "import": cmd_import}[cmd](args)

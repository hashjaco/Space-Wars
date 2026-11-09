#!/usr/bin/env python3
"""Props from a Tripo model: the hydra and the player hulls. Same route as enemies.py, more views.

    python3 tools/tripo/models.py concept [name...]   # OpenArt nano-banana-pro concept(s), ~40 cr each
    python3 tools/tripo/models.py sheet               # out/models.png contact sheet
    python3 tools/tripo/models.py pick <name> <n>
    python3 tools/tripo/models.py model [name...]     # Tripo H3.1 image-to-model on the pick, 30 cr
    python3 tools/tripo/models.py render [name...]    # headless Blender, every view/state the item lists
    python3 tools/tripo/models.py preview [name...]   # out/render-<name>.png, a strip of each render
    python3 tools/tripo/models.py import [name...]    # -> sprites/...

Items are models.json's plus the hulls, generated here: hull-<chassis> (from the Sprixen still the
loop was cut from) and hull-<chassis>-<kit> (an edit of that concept with the kit bolted on). A hull
renders three views: top (the flown loop), side (the profile the side-on levels fly) and turn (the
garage's turntable). Hull models carry no flame; the game draws the fx-engine-flame loop behind
them, anchored per chassis (ChassisArt.tailX/tailY).
"""
import glob, json, os, shutil, subprocess, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(HERE, "out")
STATE = os.path.join(HERE, ".models-state.json")
SPRITES = os.path.join(ROOT, "src/main/resources/sprites")
BLENDER = "/Applications/Blender.app/Contents/MacOS/Blender"
ITEMS = json.load(open(os.path.join(HERE, "models.json")))["items"]

CHASSIS = {"stock": 0, "interceptor": 1, "gunship": 0, "twin-boom": 0}  # the Sprixen still each loop was cut from
KITS = {"fins": "two large swept-back delta fins bolted to the wingtips and a tall dorsal fin",
        "armour": "thick bolted ablative armour plates over the nose, fuselage and wing roots, riveted and scuffed",
        "lance": "a long pointed spear-like nose lance boom extending far ahead of the nose, with a small sensor tip"}
HULL = ("Turn this top-down illustration into the same spaceship as a 3D model, seen in three-quarter view from above and "
        "in front, the nose pointing toward the viewer's bottom left. Same shape, same militia-green paint, same gold canopy, "
        "same panel lines and markings. The engine nozzles are dark and unlit, no flame, no exhaust, no glow. The whole ship "
        "visible and centred with margin, plain flat light-grey studio background, pre-rendered 3D CGI look, one object, no text, no ground.")
KIT = ("The exact same spaceship as the reference image, same paint, materials, canopy and camera angle, now fitted with {}. "
       "Engines dark and unlit, no flame. The whole ship visible and centred with margin, plain flat light-grey studio background, "
       "pre-rendered 3D CGI look, one object, no text, no ground.")
HULL_RENDERS = [["top", "top", "sway", 16, 384], ["side", "side", "sway", 16, 384], ["turn", "turn", "none", 36, 320]]
# How each Tripo hull came out, set from a first render: (yaw, tilt) to put the nose at -Y, dorsal up.
# A gunship modelled from a straight-down still stood on its tail, and the stock hull climbs
# 20 degrees nose-up, hence the tilts.
ORIENT = {"hull-stock": (90, "0,-20,0"), "hull-interceptor": (-90, "0,0,0"), "hull-gunship": (-90, "0,90,0"),
          "hull-twin-boom": (-90, "0,0,0"), "hull-stock-armour": (90, "0,-55,0")}
for c, pick in CHASSIS.items():
    ITEMS[f"hull-{c}"] = {"ref": f"tools/sprixen/out/hulls/{c}/A-{pick}.png", "variants": 1, "yaw": 0,
                          "prompt": HULL, "renders": HULL_RENDERS}
    for k, text in KITS.items():
        ITEMS[f"hull-{c}-{k}"] = {"ref": f"from:hull-{c}", "variants": 1, "yaw": 0,
                                  "prompt": KIT.format(text), "renders": HULL_RENDERS}

for name, item in ITEMS.items():  # a kit came out of Tripo the same way up as its bare hull
    base = name if name in ORIENT else next((b for b in ORIENT if name.startswith(b + "-")), None)
    if base:
        item["yaw"], item["tilt"] = ORIENT[base]

def load():
    return json.load(open(STATE)) if os.path.exists(STATE) else {}

def save(s):
    json.dump(s, open(STATE, "w"), indent=1)

def run(args, env=None):
    p = subprocess.run(args, capture_output=True, text=True, env=env)
    if p.returncode:
        raise SystemExit(f"{args[0]} exit {p.returncode}: {p.stdout[-400:]} {p.stderr[-400:]}")
    return p.stdout

def concept_path(name, v):
    return os.path.join(OUT, "concepts", name, f"{v}.png")

def ref_path(name):
    ref = ITEMS[name]["ref"]
    if ref.startswith("from:"):
        src = ref[5:]
        pick = load().get(src, {}).get("pick")
        if pick is None:
            raise SystemExit(f"{name} needs {src} picked first")
        return concept_path(src, pick)
    return os.path.join(SPRITES, ref) if not ref.startswith("tools/") else os.path.join(ROOT, ref)

def cmd_concept(names):
    for name in names or ITEMS:
        item = ITEMS[name]
        for v in range(item["variants"]):
            dest = concept_path(name, v)
            if os.path.exists(dest):
                continue
            tmp = os.path.join(OUT, "concepts", name, f"tmp{v}"); os.makedirs(tmp, exist_ok=True)
            run(["openart", "generate", "image", item["prompt"], "--model", "nano-banana-pro", "--image", ref_path(name),
                 "-o", tmp + "/", "--yes", "--no-input", "--quiet", "--timeout", "5m"])
            Image.open(sorted(glob.glob(tmp + "/*"))[0]).convert("RGB").save(dest); shutil.rmtree(tmp)
            print(name, v, "->", dest, flush=True)
        if item["variants"] == 1:
            s = load(); s.setdefault(name, {}).setdefault("pick", 0); save(s)

def cmd_sheet(names):
    cell = 256
    rows = [n for n in (names or ITEMS) if os.path.exists(concept_path(n, 0))]
    sheet = Image.new("RGB", (cell * 2, cell * len(rows)), "black")
    for r, n in enumerate(rows):
        for v in range(2):
            if os.path.exists(concept_path(n, v)):
                sheet.paste(Image.open(concept_path(n, v)).convert("RGB").resize((cell, cell)), (v * cell, r * cell))
    sheet.save(os.path.join(OUT, "models.png")); print("rows:", rows)

def cmd_pick(args):
    s = load(); s.setdefault(args[0], {})["pick"] = int(args[1]); save(s)

def glb(name):
    found = sorted(glob.glob(os.path.join(OUT, "models", name, "**", "*.glb"), recursive=True), key=os.path.getmtime)
    return found[-1] if found else None

def cmd_model(names):
    s = load()
    for name in names or [k for k in ITEMS if "pick" in s.get(k, {}) and not glb(k)]:
        d = os.path.join(OUT, "models", name); os.makedirs(d, exist_ok=True)
        env = dict(os.environ, TRIPO_CLIENT_APP="claude")
        res = json.loads(run(["tripo", "make", concept_path(name, s[name]["pick"]), "--model", "H3.1", "--out", d,
                              "--yes", "--no-open", "--json"], env).strip().splitlines()[-1])
        print(name, "->", res.get("model_file"), res.get("task_id"), flush=True)

def render_dir(name, state):
    return os.path.join(OUT, "renders", name, state)

def cmd_render(names):
    for name in names or [k for k in ITEMS if glb(k)]:
        item = ITEMS[name]
        for state, view, motion, frames, px in item["renders"]:
            d = render_dir(name, state); shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
            run([BLENDER, "-b", "--factory-startup", "-P", os.path.join(HERE, "render_blender.py"), "--",
                 glb(name), d, str(item["yaw"]), str(px), str(frames), view, motion, item.get("tilt", "0,0,0")])
            print(name, state, "->", d, flush=True)

def frames_of(name, state):
    return [Image.open(p).convert("RGBA") for p in sorted(glob.glob(os.path.join(render_dir(name, state), "*.png")))]

def cmd_preview(names):
    for name in names or [k for k in ITEMS if os.path.isdir(os.path.join(OUT, "renders", k))]:
        rows = [frames_of(name, st) for st, *_ in ITEMS[name]["renders"] if os.path.isdir(render_dir(name, st))]
        cell, cols = 128, max(len(r) for r in rows)
        sheet = Image.new("RGBA", (cell * min(cols, 12), cell * sum((len(r) + 11) // 12 for r in rows)), (50, 50, 60, 255))
        y = 0
        for r in rows:
            for i, f in enumerate(r):
                sheet.alpha_composite(f.resize((cell, cell)), ((i % 12) * cell, y + (i // 12) * cell))
            y += cell * ((len(r) + 11) // 12)
        sheet.save(os.path.join(OUT, f"render-{name}.png")); print(name, "->", f"out/render-{name}.png")

def union_box(frames, threshold=8):
    box = None
    for f in frames:
        b = f.getchannel("A").point(lambda a: 255 if a > threshold else 0).getbbox()
        if b:
            box = b if box is None else (min(box[0], b[0]), min(box[1], b[1]), max(box[2], b[2]), max(box[3], b[3]))
    return box

def fit(frame, box, canvas, symmetric_x=False):
    """Crop to box, scale to fit canvas (w, h) with a 3px margin, centre on a transparent canvas."""
    if symmetric_x:  # keep the spine on the centre line, so a bank squash happens about it
        cx = frame.width / 2; half = max(cx - box[0], box[2] - cx)
        box = (int(cx - half), box[1], int(cx + half), box[3])
    crop = frame.crop(box)
    s = min((canvas[0] - 6) / crop.width, (canvas[1] - 6) / crop.height)
    crop = crop.resize((max(1, round(crop.width * s)), max(1, round(crop.height * s))), Image.LANCZOS)
    out = Image.new("RGBA", canvas, (0, 0, 0, 0))
    out.paste(crop, ((canvas[0] - crop.width) // 2, (canvas[1] - crop.height) // 2), crop)
    return out

def write(frames, dest):
    shutil.rmtree(dest, ignore_errors=True); os.makedirs(dest)
    for i, f in enumerate(frames, start=1):
        f.quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(f"{dest}/{i}.png", optimize=True)

def import_hydra_body():
    states = {st: frames_of("hydra-body", st) for st in ("idle", "rage", "death")}
    box = union_box(states["idle"] + states["rage"])
    w, h = box[2] - box[0], box[3] - box[1]
    canvas = (472, round(472 * h / w)) if w >= h else (round(472 * w / h), 472)
    for st, frames in states.items():
        write([fit(f, box, canvas) for f in frames],
              os.path.join(SPRITES, "boss-hydra-cg", "" if st == "idle" else st))
    print("hydra-body", box, canvas, "aspect", round(w / h, 3), "-> boss-hydra-cg (set HYDRA_CG's box to this aspect)")

def import_hydra_head():
    idle, bite = frames_of("hydra-head", "idle"), frames_of("hydra-head-open", "bite")
    for frames, sub in ((idle, ""), (bite, "bite")):
        box = union_box(frames)
        # Keep the skull, not the neck behind it: the game draws the neck live, and at 64 px a
        # frame that is half neck leaves the jaws a smudge.
        box = (box[0], max(box[1], box[3] - round((box[2] - box[0]) * 1.05)), box[2], box[3])
        write([fit(f, box, (128, 128)) for f in frames], os.path.join(SPRITES, "boss-hydra-head-cg", sub))
    print("hydra-head -> boss-hydra-head-cg (+bite/)")

def militia(frame):
    """Shift, darken and saturate the green paint to the Sprixen hulls' militia green (H 0.34, S 0.33, V 0.37),
    which is what asset.HullPaint's hue swap was tuned on: Tripo's paler, yellower green (H 0.27,
    S 0.23, V 0.58) turned Corsair Red into pink."""
    import numpy as np
    hsv = np.asarray(frame.convert("RGB").convert("HSV")).astype(np.float32)
    h, sat, val = hsv[..., 0] / 255, hsv[..., 1] / 255, hsv[..., 2]
    paint = (sat > 0.15) & (np.abs(h - 0.33) < 0.11)
    hsv[..., 0] = np.where(paint, np.clip(h + 0.07, 0, 1) * 255, hsv[..., 0])  # 0.27 yellow-green -> 0.34
    hsv[..., 1] = np.where(paint, np.clip(sat * 1.45, 0, 1) * 255, hsv[..., 1])
    hsv[..., 2] = np.where(paint, val * 0.68, val)
    out = Image.fromarray(hsv.astype(np.uint8), "HSV").convert("RGB")
    out.putalpha(frame.getchannel("A"))
    return out

def import_hull(name):
    slug = name[len("hull-"):]
    for state, canvas, sym in (("top", (180, 192), True), ("side", (192, 180), False), ("turn", (256, 256), False)):
        frames = frames_of(name, state)
        if state == "top":  # rendered nose-down like the hostiles; the player flies nose-up
            frames = [f.rotate(180) for f in frames]
        box = union_box(frames)
        write([militia(fit(f, box, canvas, sym)) for f in frames], os.path.join(SPRITES, "hull-cg", slug, state))
    print(name, "-> hull-cg/" + slug)

def cmd_import(names):
    for name in names or [k for k in ITEMS if os.path.isdir(os.path.join(OUT, "renders", k))]:
        if name == "hydra-body":
            import_hydra_body()
        elif name in ("hydra-head", "hydra-head-open"):
            import_hydra_head()
        elif name.startswith("hull-"):
            import_hull(name)

if __name__ == "__main__":
    cmd, args = (sys.argv[1] if len(sys.argv) > 1 else "sheet"), sys.argv[2:]
    {"concept": cmd_concept, "sheet": cmd_sheet, "pick": cmd_pick, "model": cmd_model, "render": cmd_render,
     "preview": cmd_preview, "import": cmd_import}[cmd](args)

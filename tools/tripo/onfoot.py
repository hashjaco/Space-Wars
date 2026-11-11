#!/usr/bin/env python3
"""The on-foot cast as CG: an OpenArt concept, a Tripo model, rig and preset animations, a Blender side view.

    python3 tools/tripo/onfoot.py concept [slug...]   # 2 OpenArt variants each (nano-banana-pro, ~40 cr each)
    python3 tools/tripo/onfoot.py tconcept [slug...]  # 1 Tripo text-to-image each (5 cr), picked as 0; no reference image
    python3 tools/tripo/onfoot.py sheet                # out/onfoot-concepts.png to pick from
    python3 tools/tripo/onfoot.py pick <slug> <0|1>
    python3 tools/tripo/onfoot.py model [slug...]      # Tripo image-to-model on the pick (H3.1, 30 cr)
    python3 tools/tripo/onfoot.py rig [slug...]        # Tripo biped rig, then the manifest's preset animations
    python3 tools/tripo/onfoot.py render <slug> [move...]  # Blender side view, one fixed camera per character
    python3 tools/tripo/onfoot.py import <slug>        # -> sprites/onfoot/<slug>/<move>/1..N.png, prints the art factor

Characters and prompts live in manifest/onfoot.json. State is in the gitignored .tripo-state.json under
"onfoot/<slug>"; the imported frames are what gets committed.
"""
import glob, json, os, shutil, subprocess, sys
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(HERE, "out", "onfoot")
STATE = os.path.join(HERE, ".tripo-state.json")
MAN = json.load(open(os.path.join(HERE, "manifest", "onfoot.json")))
CHARS = {c["slug"]: c for c in MAN["characters"]}
SPRITES = os.path.join(ROOT, "src/main/resources/sprites")
ENV = dict(os.environ, TRIPO_CLIENT_APP="claude")


def load():
    return json.load(open(STATE)) if os.path.exists(STATE) else {}


def save(s):
    json.dump(s, open(STATE, "w"), indent=1)


def key(slug):
    return "onfoot/" + slug


def run(args, env=None):
    p = subprocess.run(args, capture_output=True, text=True, env=env)
    if p.returncode:
        raise SystemExit(f"{args[0]} exit {p.returncode}: {p.stdout[-600:]} {p.stderr[-600:]}")
    return p.stdout


def last_json(text):
    return json.loads(text.strip().splitlines()[-1])


def cmd_concept(slugs):
    for slug in slugs or CHARS:
        c = CHARS[slug]
        d = os.path.join(OUT, "concepts", slug); os.makedirs(d, exist_ok=True)
        for v in range(2):
            dest = os.path.join(d, f"{v}.png")
            if os.path.exists(dest):
                continue
            tmp = os.path.join(d, f"tmp{v}"); os.makedirs(tmp, exist_ok=True)
            run(["openart", "generate", "image", c["prompt"] + " " + MAN["concept_style"], "--model", "nano-banana-pro",
                 "--image", os.path.join(SPRITES, c["ref"]), "-o", tmp + "/", "--yes", "--no-input", "--quiet",
                 "--timeout", "5m"])
            got = sorted(glob.glob(tmp + "/*"))
            Image.open(got[0]).convert("RGB").save(dest); shutil.rmtree(tmp)
            print(slug, v, "->", dest, flush=True)


def cmd_tconcept(slugs):
    """Tripo text-to-image: far cheaper than OpenArt, but it cannot match a reference image."""
    for slug in slugs:
        c = CHARS[slug]
        d = os.path.join(OUT, "concepts", slug); os.makedirs(d, exist_ok=True)
        dest = os.path.join(d, "0.png")
        if not os.path.exists(dest):
            tmp = os.path.join(d, "tmp"); os.makedirs(tmp, exist_ok=True)
            run(["tripo", "generate", "text-to-image", c["prompt"] + " " + MAN["concept_style"].split(" Match the")[0],
                 "-o", tmp, "--yes", "--json", "--no-open"], ENV)
            Image.open(newest(os.path.join(tmp, "**", "generated_image.png"))).convert("RGB").save(dest)
            shutil.rmtree(tmp)
        s = load(); s.setdefault(key(slug), {})["pick"] = 0; save(s)
        print(slug, "->", dest, flush=True)


def cmd_sheet(_):
    cell = 320
    rows = [s for s in CHARS if os.path.exists(os.path.join(OUT, "concepts", s, "0.png"))]
    sheet = Image.new("RGB", (cell * 2, cell * len(rows)), "black")
    for r, s in enumerate(rows):
        for v in range(2):
            p = os.path.join(OUT, "concepts", s, f"{v}.png")
            if os.path.exists(p):
                sheet.paste(Image.open(p).convert("RGB").resize((cell, cell)), (v * cell, r * cell))
    path = os.path.join(HERE, "out", "onfoot-concepts.png"); sheet.save(path); print(path, "rows:", rows)


def cmd_pick(args):
    s = load(); s.setdefault(key(args[0]), {})["pick"] = int(args[1]); save(s)


def cmd_model(slugs):
    s = load()
    for slug in slugs or [c for c in CHARS if "pick" in s.get(key(c), {})]:
        src = os.path.join(OUT, "concepts", slug, f"{s[key(slug)]['pick']}.png")
        d = os.path.join(OUT, "models", slug); os.makedirs(d, exist_ok=True)
        res = last_json(run(["tripo", "make", src, "--model", "H3.1", "--out", d, "--yes", "--no-open", "--json"], ENV))
        s = load(); s[key(slug)]["model_task"] = res.get("task_id"); save(s)
        print(slug, "->", res.get("model_file"), res.get("task_id"), flush=True)


def cmd_rig(slugs):
    s = load()
    for slug in slugs or [c for c in CHARS if "model_task" in s.get(key(c), {})]:
        st = s[key(slug)]
        d = os.path.join(OUT, "rigs", slug); os.makedirs(d, exist_ok=True)
        if "rig_task" not in st:
            res = last_json(run(["tripo", "anim", "rig", st["model_task"], "--rig-type", "biped", "--spec", "mixamo",
                                 "--out", d, "--yes", "--no-open", "--json"], ENV))
            st["rig_task"] = res.get("task_id")
            s = load(); s[key(slug)] = st; save(s)  # reloaded: other characters may be rigging in parallel
            print(slug, "rigged", res, flush=True)
        presets = sorted({v.split("@")[0] for v in CHARS[slug]["anims"].values()})
        for i in range(0, len(presets), 5):  # five animations a call at most
            batch = presets[i:i + 5]
            ad = os.path.join(OUT, "anims", slug, "-".join(batch)); os.makedirs(ad, exist_ok=True)
            res = last_json(run(["tripo", "anim", "retarget", st["rig_task"], "--animate-in-place", "--out-format", "glb",
                                 "--animation", *[f"preset:{p}" for p in batch], "--out", ad,
                                 "--yes", "--no-open", "--json"], ENV))
            print(slug, batch, res, flush=True)


BLENDER = "/Applications/Blender.app/Contents/MacOS/Blender"
RENDER_PX = 512
YAW = -90            # Tripo stands a character facing -X; this turns it to face screen-right
FRAMES = {"idle": 16, "combat_idle": 16, "walk": 16, "run": 12, "jump": 10, "hurt": 6, "death": 12, "special": 12}


def newest(pattern):
    found = sorted(glob.glob(pattern, recursive=True), key=os.path.getmtime)
    return found[-1] if found else None


def glb_with(slug, preset):
    """The retargeted GLB that carries this preset (each retarget call is its own file)."""
    for d in sorted(glob.glob(os.path.join(OUT, "anims", slug, "*"))):
        if preset in os.path.basename(d).split("-"):
            return newest(os.path.join(d, "**", "*.glb"))
    raise SystemExit(f"{slug}: no retarget carries {preset}")


def cmd_render(args):
    slug, only = args[0], set(args[1:])
    c = CHARS[slug]
    rig = newest(os.path.join(OUT, "rigs", slug, "**", "*.glb"))
    jobs = [(move, "action", spec, glb_with(slug, spec.split("@")[0])) for move, spec in c["anims"].items()]
    stance = c["poses"]["stance"]
    for move, m in c["poses"]["moves"].items():
        keys = [[t, {"mixamorig:" + b: r for b, r in {**stance, **k}.items()}] for t, k in m["keys"]]
        path = os.path.join(OUT, "poses", slug, move + ".json"); os.makedirs(os.path.dirname(path), exist_ok=True)
        json.dump({"keys": keys}, open(path, "w"))
        jobs.append((move, "poses", path, rig))
    for move, mode, arg, glb in jobs:
        if only and move not in only:
            continue
        d = os.path.join(OUT, "renders", slug, move); shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
        frames = c["poses"]["moves"].get(move, {}).get("frames") or FRAMES.get(move, 12)
        run([BLENDER, "-b", "--factory-startup", "-P", os.path.join(HERE, "render_action.py"), "--",
             glb, d, str(RENDER_PX), str(frames), str(YAW), mode, arg])
        print(slug, move, frames, "->", d, flush=True)


FLOOR_ROW = round(RENDER_PX * (1 - 0.12))   # where render_action.py puts z = 0


def cmd_import(args):
    """Every move of a character was shot from one camera, so they share a scale and a floor already: one
    canvas around all of them, feet on its bottom edge, body on its centre line, as the renderer anchors them.
    The jump is the exception: the physics does the rising, so each of its frames stands on its own feet."""
    slug = args[0]
    root = os.path.join(OUT, "renders", slug)
    moves = {m: [Image.open(f).convert("RGBA") for f in sorted(glob.glob(os.path.join(root, m, "*.png")))]
             for m in sorted(os.listdir(root))}
    alpha = lambda f: f.getchannel("A").point(lambda a: 255 if a > 16 else 0).getbbox()
    for m, frames in moves.items():
        if m == "jump":
            moves[m] = [f.transform(f.size, Image.AFFINE, (1, 0, 0, 0, 1, alpha(f)[3] - FLOOR_ROW)) for f in frames]
    # Tripo's "in place" run still drifts ahead of the root: put each locomotion clip's average body centre
    # back on the standing pose's, or the art runs half a body ahead of the hitbox.
    standing = next(iter(moves.get("idle") or moves.get("combat_idle")))
    home = sum(alpha(standing)[0:3:2]) / 2
    for m in ("walk", "run", "jump"):
        if m in moves:
            centre = sum(sum(alpha(f)[0:3:2]) / 2 for f in moves[m]) / len(moves[m])
            dx = round(centre - home)
            moves[m] = [f.transform(f.size, Image.AFFINE, (1, 0, dx, 0, 1, 0)) for f in moves[m]]
    boxes = [alpha(f) for frames in moves.values() for f in frames]
    cx = home
    half = max(max(cx - b[0], b[2] - cx) for b in boxes) + 2
    top = min(b[1] for b in boxes) - 2
    body = FLOOR_ROW - alpha(standing)[1]
    k = CHARS[slug]["size"] / body
    cw, ch = round(2 * half * k), round((FLOOR_ROW - top) * k)
    summary = {}
    for m, frames in moves.items():
        d = os.path.join(SPRITES, "onfoot", slug, m); shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
        for i, f in enumerate(frames, start=1):
            g = f.crop((round(cx - half), round(top), round(cx + half), FLOOR_ROW)).resize((cw, ch), Image.LANCZOS)
            # Alpha snapped to all-or-nothing before the palette: FASTOCTREE averages alpha within a bucket, so
            # a quantized body came out 80-99% opaque everywhere and the stage showed through it.
            solid = g.getchannel("A").point(lambda a: 255 if a >= 128 else 0)
            g = Image.composite(g, Image.new("RGBA", g.size), solid); g.putalpha(solid)
            g.quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(f"{d}/{i}.png", optimize=True)
        summary[m] = len(frames)
    print(slug, f"canvas {cw}x{ch}  art factor {ch / CHARS[slug]['size']:.3f}", json.dumps(summary))


if __name__ == "__main__":
    cmd, args = (sys.argv[1] if len(sys.argv) > 1 else "sheet"), sys.argv[2:]
    {"concept": cmd_concept, "tconcept": cmd_tconcept, "sheet": cmd_sheet, "pick": cmd_pick, "model": cmd_model, "rig": cmd_rig,
     "render": cmd_render, "import": cmd_import}[cmd](args)

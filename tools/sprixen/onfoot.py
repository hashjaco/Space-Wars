#!/usr/bin/env python3
"""The on-foot level's art: Sprixen side-view stills, then one character moveset each.

    python3 tools/sprixen/onfoot.py still <slug>...         2 variants each (4 credits); props too
    python3 tools/sprixen/onfoot.py sheet <slug>...         download variants -> out/onfoot/<slug>/stills.png
    python3 tools/sprixen/onfoot.py pick <slug> <0|1>
    python3 tools/sprixen/onfoot.py moveset <slug> [--go]   dry run by default; --go spends
    python3 tools/sprixen/onfoot.py import <slug>           moveset ZIP -> sprites/onfoot/<slug>/<move>/1..N.png
    python3 tools/sprixen/onfoot.py prop <slug> <px>        picked still -> sprites/onfoot/<slug>.png
    python3 tools/sprixen/onfoot.py status

Same project (and so the same style lock) as the CG flagships. State in .onfoot-state.json, gitignored.
"""
import io, json, os, sys, zipfile
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cg  # rest(), get(), the project id

HERE = cg.HERE
STATE = os.path.join(HERE, ".onfoot-state.json")
OUT = os.path.join(HERE, "out", "onfoot")
SPRITES = os.path.join(cg.ROOT, "src/main/resources/sprites/onfoot")
MAN = json.load(open(os.path.join(HERE, "manifest", "onfoot.json")))
ROWS = {r["slug"]: r for r in MAN["characters"] + MAN["props"]}
CHAR = {r["slug"] for r in MAN["characters"]}
# What each character gets. The keys are Sprixen's; the descriptions in the manifest say what they really are
# (dodge is the pilot's sweep, flourish its roundhouse, victory its guard). Kept standard where premium buys nothing.
MOVES = {
    "pilot": ["idle", "walk", "run", "combo_1", "combo_2", "combo_3", "hurt", "death", "jump", "dodge", "flourish",
              "special", "victory"],
    "thrall": ["combat_idle", "walk", "combo_1", "combo_2", "hurt", "death"],
    "foreman": ["combat_idle", "walk", "combo_1", "combo_2", "combo_3", "hurt", "death", "special"],
}
STANDARD = ["dodge", "flourish", "victory"]
# The Foreman went in on what was left of the month's credits: 8 frames and a standard beam (32 vs 46).
FRAMES = {"foreman": 8}
STANDARD_FOR = {"foreman": ["special"]}
SIZE = {"pilot": 256, "thrall": 256, "foreman": 384}  # largest edge after import

def load():
    return json.load(open(STATE)) if os.path.exists(STATE) else {}

def save(s):
    json.dump(s, open(STATE, "w"), indent=1)

def prompt(row):
    return row["prompt"] + " " + MAN["style"]

def still(slugs):
    s = load()
    for slug in slugs:
        row = ROWS[slug]
        body = {"prompt": prompt(row) if not row.get("scene") else row["prompt"], "projectId": cg.PID,
                "resolution": row.get("resolution", "512x512"), "pixelPerfect": "off",
                "variantCount": 2, "type": "sprite", "noBase": slug in CHAR}
        if row.get("scene"):
            body["skipBgRemoval"] = True
        r = cg.rest("POST", "/v1/generations", body)
        s.setdefault(slug, {})["still"] = r["id"]; save(s); print(slug, "still", r["id"], r.get("status"))

def sheet(slugs):
    s = load()
    for slug in slugs:
        gen = cg.rest("GET", f"/v1/generations/{s[slug]['still']}")
        if gen.get("status") != "completed":
            print(slug, gen.get("status"), gen.get("error")); continue
        d = os.path.join(OUT, slug); os.makedirs(d, exist_ok=True)
        ims = []
        for i, sp in enumerate(gen["sprites"]):
            im = Image.open(io.BytesIO(cg.get(sp["imageUrl"]))).convert("RGBA"); im.save(f"{d}/A-{i}.png"); ims.append(im)
        s[slug]["sprites"] = [sp["id"] for sp in gen["sprites"]]
        w, h = ims[0].size
        out = Image.new("RGB", (w * len(ims), h), (20, 24, 40))
        for i, im in enumerate(ims):
            out.paste(im, (w * i, 0), im)
        out.save(f"{d}/stills.png"); print(slug, "->", f"{d}/stills.png")
    save(s)

def moveset(slug, go):
    s = load(); row = ROWS[slug]
    sid = s[slug]["sprites"][s[slug].get("pick", 0)]
    args = {"spriteId": sid, "moves": MOVES[slug], "archetype": "humanoid_biped", "direction": "right",
            "frames": FRAMES.get(slug, 16), "descriptions": {k: v for k, v in row.get("descriptions", {}).items() if k in MOVES[slug]},
            "standard": [m for m in STANDARD + STANDARD_FOR.get(slug, []) if m in MOVES[slug]], "maxCredits": 36, "dryRun": not go}
    if "idle" in MOVES[slug]:
        args["poses"] = ["pose_relaxed"]
    r = call("create_character_moveset", args)
    if go:
        s[slug]["moveset"] = r.get("movesetId") or r.get("id"); save(s)
    print(json.dumps(r, indent=1)[:3000])

def call(tool, args):
    import subprocess
    out = subprocess.run([sys.executable, os.path.join(HERE, "sprixen.py"), "call", tool, json.dumps(args)],
                         capture_output=True, text=True)
    if out.returncode:
        raise SystemExit(out.stdout[:500] + out.stderr[:500])
    r = json.loads(out.stdout)
    return r.get("data", r)

def moveset_info(slug):
    """By REST: the get_character_moveset tool 500s while a set is building, the sprite's list does not."""
    s = load()
    sid = s[slug]["sprites"][s[slug].get("pick", 0)]
    return next(m for m in cg.rest("GET", f"/v1/sprites/{sid}/movesets") if m["id"] == s[slug]["moveset"])

MAX_FRAMES = 24  # premium clips arrive at 95; the game indexes by progress, so a quarter is plenty

def import_(slug):
    """Unpacks the moveset ZIP's sheets into numbered frames on one canvas per character, feet on its
    bottom edge and the body on its centre line, which is how the renderer anchors them.

    Sprixen's sheets don't agree with each other: each move has its own floor line, body position and
    even scale (the Foreman's idle is a fifth smaller than its punches). Every move starts in a standing
    pose, so its first frame sets that move's scale and centre, and its lowest pixel is its floor.
    The jump carries its own rise and fall, which the physics already does: only its airborne frames
    are kept, each standing on its own lowest pixel.
    """
    info = moveset_info(slug)
    data = zipfile.ZipFile(io.BytesIO(cg.get(info["downloadPath"])))
    meta = json.loads(data.read("moveset.json"))
    moves = {}
    for m in meta["moves"]:
        im = Image.open(io.BytesIO(data.read(m["file"]))).convert("RGBA")
        fw, fh = m["frameWidth"], m["frameHeight"]
        frames = [im.crop((i * fw, 0, (i + 1) * fw, fh)) for i in range(m["frameCount"])]
        frames = [f for f in frames if f.getchannel("A").getbbox()]
        moves[m["key"]] = frames
    box = lambda f: f.getchannel("A").getbbox()
    ref = box(next(iter(moves.values()))[0])
    ref_h = ref[3] - ref[1]
    placed = {}  # key -> [(frame, scale, x of body centre, y of floor)]
    for key, frames in moves.items():
        first = box(frames[0])
        k = ref_h / (first[3] - first[1])
        cx = (first[0] + first[2]) / 2
        floor = max(box(f)[3] for f in frames)
        if key == "jump":
            frames = [f for f in frames if box(f)[3] < floor - 12] or frames
        if len(frames) > MAX_FRAMES:
            frames = [frames[round(i * (len(frames) - 1) / (MAX_FRAMES - 1))] for i in range(MAX_FRAMES)]
        placed[key] = [(f, k, cx, box(f)[3] if key == "jump" else floor) for f in frames]
    # One canvas around every frame, in units of the reference scale, origin at the feet.
    left = right = top = 0
    for rows in placed.values():
        for f, k, cx, floor in rows:
            b = box(f)
            left, right = min(left, (b[0] - cx) * k), max(right, (b[2] - cx) * k)
            top = min(top, (b[1] - floor) * k)
    half = max(-left, right)
    out = SIZE[slug] / ref_h  # the standing body is SIZE pixels tall
    cw, ch = round(2 * half * out), round(-top * out)
    summary = {}
    for key, rows in placed.items():
        d = os.path.join(SPRITES, slug, key); os.makedirs(d, exist_ok=True)
        for old in os.listdir(d):
            os.remove(os.path.join(d, old))
        for i, (f, k, cx, floor) in enumerate(rows, start=1):
            g = f.resize((round(f.width * k * out), round(f.height * k * out)), Image.LANCZOS)
            canvas = Image.new("RGBA", (cw, ch))
            canvas.paste(g, (round(cw / 2 - cx * k * out), round(ch - floor * k * out)), g)  # clips what hangs below the floor
            canvas.quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(f"{d}/{i}.png", optimize=True)
        summary[key] = len(rows)
    # The renderer draws the canvas at <art> x the fighter's height: canvas / standing body.
    print(slug, f"canvas {cw}x{ch}  art factor {ch / SIZE[slug]:.3f}", json.dumps(summary))

def prop(slug, px):
    s = load()
    im = Image.open(os.path.join(OUT, slug, f"A-{s[slug].get('pick', 0)}.png")).convert("RGBA")
    b = im.getchannel("A").getbbox()
    im = im.crop(b) if b else im
    im.thumbnail((int(px), int(px)), Image.LANCZOS)
    os.makedirs(SPRITES, exist_ok=True)
    im.quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(os.path.join(SPRITES, f"{slug}.png"), optimize=True)
    print(slug, im.size)

def status():
    s = load()
    for slug, v in s.items():
        line = [slug, "still:" + ("downloaded" if "sprites" in v else "queued"), f"pick:{v.get('pick', 0)}"]
        if "moveset" in v:
            info = moveset_info(slug)
            line.append(f"moveset:{info['status']} " + " ".join(f"{m['key']}={m['status']}" for m in info.get("items", [])))
        print("  ".join(line))

if __name__ == "__main__":
    cmd, a = sys.argv[1], sys.argv[2:]
    {"still": lambda: still(a), "sheet": lambda: sheet(a), "pick": lambda: (lambda s: (s[a[0]].__setitem__("pick", int(a[1])), save(s)))(load()),
     "moveset": lambda: moveset(a[0], "--go" in a), "import": lambda: import_(a[0]), "prop": lambda: prop(a[0], a[1]),
     "status": status}[cmd]()

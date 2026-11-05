#!/usr/bin/env python3
"""The CG flagship pipeline: Sprixen 2D still (route A) + premium idle loop, imported as game frames.

    python3 tools/sprixen/cg.py still <slug>...        2 variants each, 4 credits a boss
    python3 tools/sprixen/cg.py stills-sheet <slug>... download the variants, write out/cg/<slug>/stills.png
    python3 tools/sprixen/cg.py pick <slug> <0|1>      choose the variant to animate
    python3 tools/sprixen/cg.py loop <slug>...         premium custom idle loop on the pick, 8 credits
    python3 tools/sprixen/cg.py import <slug>...       loop -> sprites/boss-<slug>-cg/1..24.png (320 px, 256 colours)
    python3 tools/sprixen/cg.py status

Slugs come from tools/sprixen/manifest/bosses.json (bosses and parts alike). Ids live in
tools/sprixen/.cg-state.json, which is gitignored; the frames it imports are what gets committed.
The standard animation path garbles painterly sprites in this project, so loops are always premium.
"""
import io, json, os, subprocess, sys, urllib.request
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
STATE = os.path.join(HERE, ".cg-state.json")
OUT = os.path.join(HERE, "out", "cg")
PID = open(os.path.join(HERE, ".cg-project-id")).read().strip()
MAN = json.load(open(os.path.join(HERE, "manifest", "bosses.json")))
ROWS = {r["slug"]: r for r in MAN["bosses"] + MAN["parts"]}
# 24 frames spread over the ~9.5 s premium loop, played back at source speed with a cross-fade
# between neighbours (Renderer), at 320 px (the largest flagship box) with a 256-colour palette.
FRAMES, SIZE = 24, 320

SUFFIX = (" Viewed from directly above for a vertical shoot-'em-up, nose pointing straight down toward the player, "
          "bilaterally symmetrical, filling the frame, pre-rendered 3D CGI look with smooth shading, specular highlights "
          "and deep shadow, transparent background, no text, no base, no ground.")
LOOP = ("Seamless idle loop of this thing holding station, seen from above: its glowing parts pulse brighter and dimmer, "
        "its mouth, mandibles or eyes slowly move, loose flesh and feelers sway, its engines flare. "
        "The body does not move, turn or change size; the last frame flows back into the first.")

def key():
    return json.load(open(os.path.expanduser("~/.claude.json")))["projects"]["/Users/hashim/Projects"]["mcpServers"]["sprixen"]["headers"]["Authorization"]

def rest(method, path, body=None):
    import time
    args = [sys.executable, os.path.join(HERE, "sprixen.py"), "rest", method, path] + ([json.dumps(body)] if body else [])
    for attempt in range(6):
        out = subprocess.run(args, capture_output=True, text=True)
        if out.returncode == 0:
            return json.loads(out.stdout)
        if "429" in out.stdout:  # 60 requests a minute across the key: wait it out
            time.sleep(10 * (attempt + 1)); continue
        break
    raise SystemExit(f"{method} {path}: {out.stdout[:300]} {out.stderr[:200]}")

def get(url):
    req = urllib.request.Request(url if url.startswith("http") else "https://api.sprixen.com" + url, headers={"Authorization": key()})
    return urllib.request.urlopen(req, timeout=600).read()

def load():
    return json.load(open(STATE)) if os.path.exists(STATE) else {}

def save(state):
    json.dump(state, open(STATE, "w"), indent=1)

ART_FOR = {"VAUNT": "FORGE_RIG", "VAUNT_IN_THE_STORM_RIG": "STORM_RIG"}

def box(row):
    """The generated BossArt's on-screen box, which is the hitbox the new art must fill."""
    import re
    src = open(os.path.join(ROOT, "src/main/java/com/hashimjacobs/spacecase/asset/BossArt.java")).read()
    m = re.search(r"^\s+" + ART_FOR.get(row["boss"], row["boss"]) + r'\("[^"]+", \d+, \d+, ([\d.]+), ([\d.]+)\)', src, re.M)
    w, h = float(m.group(1)), float(m.group(2))
    return (h, w) if row.get("side") else (w, h)  # side-on art is drawn nose-down, then turned

def prompt_for(row):
    w, h = box(row)
    r = w / h
    shape = ("much wider than tall" if r > 1.4 else "wider than tall" if r > 1.12 else
             "much taller than wide" if r < 0.72 else "taller than wide" if r < 0.9 else "about as wide as tall")
    aspect = f" Overall silhouette {shape}, about {w:.0f} by {h:.0f}, so it fills a {w:.0f}x{h:.0f} box edge to edge."
    return row["prompt"] + ". Style: " + MAN["flavour"][row["galaxy"]] + "." + aspect + SUFFIX

def cmd_still(slugs):
    state = load()
    for slug in slugs:
        row = ROWS[slug]
        r = rest("POST", "/v1/generations", {"prompt": prompt_for(row), "projectId": PID, "resolution": "512x512",
                                              "pixelPerfect": "off", "variantCount": 2, "noBase": True, "type": "sprite"})
        state.setdefault(slug, {})["still"] = r["id"]
        save(state)  # per job: Sprixen caps active generations, and a refusal must not lose the rest
        print(slug, "still", r["id"], r.get("status"))

def cmd_stills_sheet(slugs):
    state = load()
    for slug in slugs:
        gen = rest("GET", f"/v1/generations/{state[slug]['still']}")
        if gen.get("status") != "completed":
            print(slug, gen.get("status"), gen.get("error")); continue
        d = os.path.join(OUT, slug); os.makedirs(d, exist_ok=True)
        ids, ims = [], []
        for i, s in enumerate(gen["sprites"]):
            im = Image.open(io.BytesIO(get(s["imageUrl"]))).convert("RGBA"); im.save(f"{d}/A-{i}.png"); ids.append(s["id"]); ims.append(im)
        state[slug]["sprites"] = ids
        sheet = Image.new("RGB", (512 * len(ims), 512), (20, 24, 40))
        for i, im in enumerate(ims):
            sheet.paste(im, (512 * i, 0), im)
        sheet.save(f"{d}/stills.png"); print(slug, len(ims), "variants ->", f"{d}/stills.png")
    save(state)

def cmd_pick(slug, idx):
    state = load(); state[slug]["pick"] = int(idx); save(state); print(slug, "pick", idx)

def cmd_loop(slugs):
    state = load()
    for slug in slugs:
        sid = state[slug]["sprites"][state[slug].get("pick", 0)]
        r = rest("POST", f"/v1/sprites/{sid}/animations", {"type": "custom", "frameCount": 12, "fps": 10, "quality": "premium",
                                                           "resolution": "512x512", "customDescription": LOOP})
        state[slug]["loop"] = r["id"]; save(state); print(slug, "loop", r["id"], r.get("status"), r.get("error"))

def turned(im):
    # The generator's quarterTurnLeft: the side-on levels fly right-to-left, so a nose-down hull turns to point left.
    return im.rotate(-90, expand=True)

def cmd_import(slugs):
    state = load()
    for slug in slugs:
        row = ROWS[slug]
        anim = rest("GET", f"/v1/animations/{state[slug]['loop']}")
        if anim.get("status") != "completed":
            print(slug, anim.get("status"), anim.get("error")); continue
        frames = sorted(anim["framesJson"], key=lambda f: f["index"])
        n = len(frames); picks = [frames[round(i * n / FRAMES)] for i in range(FRAMES)]
        dest = os.path.join(ROOT, "src/main/resources/sprites", f"boss-{slug}-cg"); os.makedirs(dest, exist_ok=True)
        for i, f in enumerate(picks, start=1):
            im = Image.open(io.BytesIO(get(f["url"]))).convert("RGBA")
            if row.get("side"):
                im = turned(im)
            im.thumbnail((SIZE, SIZE), Image.LANCZOS)
            im.quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(f"{dest}/{i}.png", optimize=True)
        size = sum(os.path.getsize(f"{dest}/{i}.png") for i in range(1, FRAMES + 1)) // 1024
        state[slug]["imported"] = True; print(slug, "->", dest, f"{size} KB")
    save(state)

def cmd_status():
    """One request for the queue; a still counts as done once its variants are downloaded."""
    state = load()
    jobs = {j["id"]: j for j in rest("GET", "/v1/jobs").get("data", [])}
    for slug, s in state.items():
        line = [slug]
        if "still" in s:
            j = jobs.get(s["still"])
            line.append("still:" + ("downloaded" if "sprites" in s else j["status"] if j else "completed"))
        if "pick" in s:
            line.append(f"pick:{s['pick']}")
        if "loop" in s:
            j = jobs.get(s["loop"])
            line.append("loop:" + ("imported" if s.get("imported") else
                                   f"{j['status']} {j.get('phase') or ''} {j.get('progressCompleted') or 0}/{j.get('progressTotal') or '?'}" if j else "done?"))
        print("  ".join(line))

if __name__ == "__main__":
    cmd, args = (sys.argv[1] if len(sys.argv) > 1 else "status"), sys.argv[2:]
    {"still": lambda: cmd_still(args), "stills-sheet": lambda: cmd_stills_sheet(args), "pick": lambda: cmd_pick(*args),
     "loop": lambda: cmd_loop(args), "import": lambda: cmd_import(args), "status": cmd_status}[cmd]()

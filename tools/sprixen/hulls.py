#!/usr/bin/env python3
"""The CG player hulls: one Sprixen still and one premium idle loop per chassis, in militia green.

    python3 tools/sprixen/hulls.py still [chassis...]     2 variants each (4 credits)
    python3 tools/sprixen/hulls.py sheet [chassis...]     variants -> out/hulls/<chassis>/stills.png
    python3 tools/sprixen/hulls.py pick <chassis> <0|1>
    python3 tools/sprixen/hulls.py loop [chassis...]      premium idle loop on the pick (8 credits)
    python3 tools/sprixen/hulls.py import [chassis...]    loop -> sprites/player-cg/<chassis>/1..N.png
    python3 tools/sprixen/hulls.py status

Only the straight, green hull is on disk. Paint, bank, the hit flash and the side-on turn are derived
when the game decodes it (asset.HullPaint), which is 16 frames a chassis instead of 960.
Same project, and so the same style lock, as the CG flagships. State in .hulls-state.json, gitignored.
"""
import io, json, os, sys
from PIL import Image, ImageChops, ImageStat

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cg

STATE = os.path.join(cg.HERE, ".hulls-state.json")
OUT = os.path.join(cg.HERE, "out", "hulls")
SPRITES = os.path.join(cg.ROOT, "src/main/resources/sprites/player-cg")
W, H = 180, 192  # the stock hull's canvas: Sprite.P1_STRAIGHT is drawn 60x64 from it
FRAMES = 16

PAINT = ("hull painted glossy militia green with dark gunmetal panel lines, a gold-tinted bubble canopy, "
         "engine nozzles burning bright blue")
HULLS = {
    "stock": "the Militia fighter, the player's starting ship: a compact starfighter with a pointed nose, the canopy "
             "set mid-fuselage, swept main wings with a squared armour pod on each wing root, two big engine nozzles at the tail",
    "interceptor": "the Interceptor: a slim needle-nosed dart, a long narrow fuselage, the canopy far forward, thin "
                   "swept-back delta wings, two small engine nozzles at the tail",
    "gunship": "the Gunship: a wide heavily armoured fighter with a blunt broad nose, a pair of forward gun barrels on "
               "each side, thick straight wings carrying weapon hardpoints, four engine nozzles across the tail",
    "twin-boom": "the Twin-Boom: a narrow central cockpit pod and two long parallel tail booms either side of it, the booms "
                 "joined at the rear by a straight crossbar, one engine nozzle at the back of each boom",
}
SUFFIX = (" A clean human-built military starfighter, machined metal, nothing organic. Viewed from directly above for a "
          "vertical shoot-'em-up, nose pointing straight up toward the top of the frame, bilaterally symmetrical, "
          "slightly taller than wide, filling the frame, pre-rendered 3D CGI look with smooth shading, specular "
          "highlights and deep shadow, transparent background, no text, no base, no ground.")
LOOP = ("Seamless idle loop of this starfighter holding station, seen from above: its engine flames flicker and flare, "
        "small navigation lights on the wingtips blink, light glints across the canopy. The ship does not move, turn "
        "or change size; the last frame flows back into the first.")

def load():
    return json.load(open(STATE)) if os.path.exists(STATE) else {}

def save(s):
    json.dump(s, open(STATE, "w"), indent=1)

def still(names):
    s = load()
    for n in names:
        r = cg.rest("POST", "/v1/generations", {"prompt": HULLS[n] + ", " + PAINT + "." + SUFFIX, "projectId": cg.PID,
                                                "resolution": "512x512", "pixelPerfect": "off", "variantCount": 2,
                                                "noBase": True, "type": "sprite"})
        s.setdefault(n, {})["still"] = r["id"]; save(s); print(n, "still", r["id"], r.get("status"))

def sheet(names):
    s = load()
    for n in names:
        gen = cg.rest("GET", f"/v1/generations/{s[n]['still']}")
        if gen.get("status") != "completed":
            print(n, gen.get("status"), gen.get("error")); continue
        d = os.path.join(OUT, n); os.makedirs(d, exist_ok=True)
        ims = [Image.open(io.BytesIO(cg.get(sp["imageUrl"]))).convert("RGBA") for sp in gen["sprites"]]
        s[n]["sprites"] = [sp["id"] for sp in gen["sprites"]]
        out = Image.new("RGB", (512 * len(ims), 512), (20, 24, 40))
        for i, im in enumerate(ims):
            im.save(f"{d}/A-{i}.png"); out.paste(im, (512 * i, 0), im)
        out.save(f"{d}/stills.png"); print(n, "->", f"{d}/stills.png")
    save(s)

def loop(names):
    s = load()
    for n in names:
        sid = s[n]["sprites"][s[n].get("pick", 0)]
        r = cg.rest("POST", f"/v1/sprites/{sid}/animations", {"type": "custom", "frameCount": 12, "fps": 10,
                                                              "quality": "premium", "resolution": "512x512",
                                                              "customDescription": LOOP})
        s[n]["loop"] = r["id"]; save(s); print(n, "loop", r["id"], r.get("status"), r.get("error"))

def opaque(im):
    return im.getchannel("A").point(lambda a: 255 if a > 24 else 0).getbbox()

def frames_of(anim):
    """Every frame of a completed loop, in order, as RGBA."""
    return [Image.open(io.BytesIO(cg.get(f["url"]))).convert("RGBA")
            for f in sorted(anim["framesJson"], key=lambda f: f["index"])]

def steady(every):
    """The frames of a premium loop that still look like its still.

    Premium loops drift: the camera pushes in for a stretch and a flare blooms, so the subject grows by
    up to half. The first frame is the still itself; keep the frames as wide as it is (the flames
    change the height, not the width), then drop flares and blooms. Shared with map.py."""
    width = lambda im: (lambda b: b[2] - b[0])(opaque(im))
    area = lambda im: sum(im.getchannel("A").histogram()[25:])
    kept = [im for im in every if abs(width(im) - width(every[0])) <= 0.03 * width(every[0])]
    # A glint or a streak flaring off the hull grows its area without its width.
    kept = [im for im in kept if area(im) <= 1.04 * sorted(map(area, kept))[len(kept) // 2]]
    # A bloom or a streak over the hull keeps both: it is the frames that look least like the still.
    change = lambda im: sum(ImageStat.Stat(ImageChops.difference(im, every[0]).convert("RGB")).mean)
    typical = sorted(map(change, kept))[len(kept) // 2]
    return [im for im in kept if change(im) <= 1.5 * typical + 1]

def import_(names):
    """FRAMES evenly spaced frames of the loop, all cropped to one box (so the hull never swims) and
    centred on the stock hull's canvas at the size the stock hull fills it."""
    s = load()
    for n in names:
        anim = cg.rest("GET", f"/v1/animations/{s[n]['loop']}")
        if anim.get("status") != "completed":
            print(n, anim.get("status"), anim.get("error")); continue
        every = frames_of(anim)
        steady_ = steady(every)
        ims = [steady_[round(i * (len(steady_) - 1) / (FRAMES - 1))] for i in range(FRAMES)]
        boxes = [opaque(im) for im in ims]
        box = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
        cx = 256  # symmetric hull: centre on the frame's middle, not the box, so the bank squash is about the spine
        half = max(cx - box[0], box[2] - cx)
        box = (cx - half, box[1], cx + half, box[3])
        k = min((W - 6) / (box[2] - box[0]), (H - 6) / (box[3] - box[1]))
        d = os.path.join(SPRITES, n); os.makedirs(d, exist_ok=True)
        for old in os.listdir(d):
            os.remove(os.path.join(d, old))
        for i, im in enumerate(ims, start=1):
            g = im.crop(box); g = g.resize((round(g.width * k), round(g.height * k)), Image.LANCZOS)
            canvas = Image.new("RGBA", (W, H)); canvas.paste(g, ((W - g.width) // 2, (H - g.height) // 2), g)
            canvas.quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(f"{d}/{i}.png", optimize=True)
        print(n, f"{len(steady_)}/{len(every)} steady ->", d, sum(os.path.getsize(os.path.join(d, f)) for f in os.listdir(d)) // 1024, "KB")
    save(s)

def status():
    for n, v in load().items():
        line = [n, "pick:" + str(v.get("pick", 0))]
        if "loop" in v:
            a = cg.rest("GET", f"/v1/animations/{v['loop']}"); line.append("loop:" + str(a.get("status")))
        elif "still" in v:
            g = cg.rest("GET", f"/v1/generations/{v['still']}"); line.append("still:" + str(g.get("status")))
        print("  ".join(line))

if __name__ == "__main__":
    cmd, a = sys.argv[1], sys.argv[2:]
    names = [x for x in a if x in HULLS] or list(HULLS)
    {"still": lambda: still(names), "sheet": lambda: sheet(names), "loop": lambda: loop(names),
     "import": lambda: import_(names), "status": status,
     "pick": lambda: (lambda s: (s[a[0]].__setitem__("pick", int(a[1])), save(s), print(a[0], "pick", a[1])))(load())}[cmd]()

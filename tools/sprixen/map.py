#!/usr/bin/env python3
"""The universe map's art: an animated planet and a wide backdrop per galaxy.

    python3 tools/sprixen/map.py still [galaxy...]      planet + backdrop stills, 2 variants each (8 credits a galaxy)
    python3 tools/sprixen/map.py sheet [galaxy...]      variants -> out/map/<galaxy>/{planet,sky}.png
    python3 tools/sprixen/map.py pick <galaxy> <planet|sky> <0|1>
    python3 tools/sprixen/map.py loop [galaxy...]       premium turning loop on the picked planet (8 credits)
    python3 tools/sprixen/map.py import [galaxy...]     -> sprites/map-cg/planet-<galaxy>/1..24.png, sky-<galaxy>.png
    python3 tools/sprixen/map.py status

Same project, and so the same style lock, as the CG flagships and hulls. The galaxy palettes are
the flagships' flavour lines in manifest/bosses.json, so a galaxy's world looks like what lives
there. State in .map-state.json (gitignored, like the others).
"""
import json, os, sys
from PIL import Image

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import cg, hulls

STATE = os.path.join(cg.HERE, ".map-state.json")
OUT = os.path.join(cg.HERE, "out", "map")
SPRITES = os.path.join(cg.ROOT, "src/main/resources/sprites/map-cg")
PLANET, FRAMES = 256, 24
# Loops that are not loops. Null's camera pushes in after frame 11 and the planet turns its eye out
# of view, so the last frame is a different picture from the first and the steady filter kept 13 of
# 95. Its first 12 frames hold still and shimmer: play them forward then back, which is 24 and
# meets itself at both ends. A galaxy here skips the steady filter.
PING_PONG = {"NULL": 12}
# SystemMapView's canvas is 996x620; the sky is kept wider than that so it can drift.
SKY_W, SKY_H = 1240, 620

WORLDS = {
    "VERDANCE": "Verdance, a jungle world overrun by an alien hive: green continents crusted with chitin and resin, "
                "sick yellow hive-glands glowing across the night side",
    "ASHFALL": "Ashfall, a volcanic forge world: a black crust split by rivers of lava, drifting ash clouds, "
               "ember-lit cracks and vents",
    "CRYONIS": "Cryonis, a frozen world: blue-white ice sheets, frost-cracked crust, a pale cold glow leaking "
               "from deep fissures",
    "TEMPEST": "Tempest, a storm giant: slate-blue cloud bands, one great storm eye, white lightning crawling "
               "across the clouds, a thin ring",
    "NULL": "Null, a void world at the edge of everything: a void-black surface, violet light leaking from "
            "fissures in geometry that looks wrong, one vast crater like a lidless eye",
}
PLANET_SUFFIX = (" A single whole planet seen from space, perfectly round, centred, filling the frame edge to edge, "
                 "lit from the upper left with a dark terminator on the right, a thin glowing atmosphere rim, "
                 "pre-rendered 3D CGI look with smooth shading, transparent background, no stars, no moons, no text.")
SKY = ("A wide deep-space panorama for a star map, the sky of the {name} galaxy: soft dark nebula clouds in its "
       "palette ({palette}), scattered faint stars, low contrast and dark overall so bright map markers read "
       "on top of it, no planets, no ships, no text, no frame.")
LOOP = ("Seamless loop of this planet turning slowly on its axis: its surface and cloud bands drift steadily from "
        "left to right, its glowing parts pulse softly, the atmosphere rim shimmers. The planet stays exactly the "
        "same size and position and the camera does not move or zoom; the last frame flows back into the first.")

def load():
    return json.load(open(STATE)) if os.path.exists(STATE) else {}

def save(s):
    json.dump(s, open(STATE, "w"), indent=1)

def palette(g):
    return cg.MAN["flavour"][g]

def still(names):
    s = load()
    for g in names:
        planet = {"prompt": WORLDS[g] + ". Palette and mood: " + palette(g) + "." + PLANET_SUFFIX,
                  "projectId": cg.PID, "resolution": "512x512", "pixelPerfect": "off", "variantCount": 2,
                  "noBase": True, "type": "sprite"}
        sky = {"prompt": SKY.format(name=g.title(), palette=palette(g)), "projectId": cg.PID,
               "resolution": "1024x512", "pixelPerfect": "off", "variantCount": 2, "type": "sprite",
               "noBase": False, "skipBgRemoval": True}
        for part, body in (("planet", planet), ("sky", sky)):
            if s.get(g, {}).get(part, {}).get("still"):
                print(g, part, "already has a still; skipping (no credits spent)"); continue
            r = cg.rest("POST", "/v1/generations", body)
            s.setdefault(g, {}).setdefault(part, {})["still"] = r["id"]; save(s)
            print(g, part, "still", r["id"], r.get("status"))

def sheet(names):
    import io
    s = load()
    for g in names:
        for part in ("planet", "sky"):
            gen = cg.rest("GET", f"/v1/generations/{s[g][part]['still']}")
            if gen.get("status") != "completed":
                print(g, part, gen.get("status"), gen.get("error")); continue
            d = os.path.join(OUT, g); os.makedirs(d, exist_ok=True)
            ims = [Image.open(io.BytesIO(cg.get(sp["imageUrl"]))).convert("RGBA") for sp in gen["sprites"]]
            s[g][part]["sprites"] = [sp["id"] for sp in gen["sprites"]]
            w, h = ims[0].size
            out = Image.new("RGB", (w * len(ims), h), (20, 24, 40))
            for i, im in enumerate(ims):
                im.save(f"{d}/{part}-{i}.png"); out.paste(im, (w * i, 0), im)
            out.save(f"{d}/{part}.png"); print(g, part, "->", f"{d}/{part}.png")
    save(s)

def loop(names):
    s = load()
    for g in names:
        p = s[g]["planet"]
        if p.get("loop"):
            print(g, "already has a loop; skipping (no credits spent)"); continue
        sid = p["sprites"][p.get("pick", 0)]
        r = cg.rest("POST", f"/v1/sprites/{sid}/animations", {"type": "custom", "frameCount": 12, "fps": 10,
                                                              "quality": "premium", "resolution": "512x512",
                                                              "customDescription": LOOP})
        p["loop"] = r["id"]; save(s); print(g, "loop", r["id"], r.get("status"), r.get("error"))

def import_(names):
    s = load()
    for g in names:
        slug = g.lower()
        os.makedirs(SPRITES, exist_ok=True)
        # The sky: cover the canvas height, keep the width for drift.
        sky = Image.open(os.path.join(OUT, g, f"sky-{s[g]['sky'].get('pick', 0)}.png")).convert("RGB")
        k = max(SKY_W / sky.width, SKY_H / sky.height)
        sky = sky.resize((round(sky.width * k), round(sky.height * k)), Image.LANCZOS)
        left, top = (sky.width - SKY_W) // 2, (sky.height - SKY_H) // 2
        sky.crop((left, top, left + SKY_W, top + SKY_H)).save(os.path.join(SPRITES, f"sky-{slug}.png"), optimize=True)
        # The planet: FRAMES steady frames, one shared square crop so it never swims.
        anim = cg.rest("GET", f"/v1/animations/{s[g]['planet']['loop']}")
        if anim.get("status") != "completed":
            print(g, "planet loop", anim.get("status"), anim.get("error")); continue
        every = hulls.frames_of(anim)
        if g in PING_PONG:
            kept = every[:PING_PONG[g]]
            ims = kept + kept[::-1]
        else:
            kept = hulls.steady(every)
            ims = [kept[round(i * (len(kept) - 1) / (FRAMES - 1))] for i in range(FRAMES)]
        boxes = [hulls.opaque(im) for im in ims]
        box = (min(b[0] for b in boxes), min(b[1] for b in boxes), max(b[2] for b in boxes), max(b[3] for b in boxes))
        side = max(box[2] - box[0], box[3] - box[1])
        cx, cy = (box[0] + box[2]) // 2, (box[1] + box[3]) // 2
        box = (cx - side // 2, cy - side // 2, cx - side // 2 + side, cy - side // 2 + side)
        d = os.path.join(SPRITES, f"planet-{slug}"); os.makedirs(d, exist_ok=True)
        for old in os.listdir(d):
            os.remove(os.path.join(d, old))
        for i, im in enumerate(ims, start=1):
            g_ = im.crop(box).resize((PLANET - 4, PLANET - 4), Image.LANCZOS)
            canvas = Image.new("RGBA", (PLANET, PLANET)); canvas.paste(g_, (2, 2), g_)
            canvas.quantize(256, method=Image.Quantize.FASTOCTREE, dither=Image.Dither.NONE).save(f"{d}/{i}.png", optimize=True)
        print(g, f"{len(kept)}/{len(every)} steady ->", d)
    save(s)

def status():
    for g, v in load().items():
        line = [g]
        for part in ("planet", "sky"):
            p = v.get(part, {})
            if "loop" in p:
                line.append(part + " loop:" + str(cg.rest("GET", f"/v1/animations/{p['loop']}").get("status")))
            elif "still" in p:
                line.append(part + " still:" + str(cg.rest("GET", f"/v1/generations/{p['still']}").get("status"))
                            + " pick:" + str(p.get("pick", 0)))
        print("  ".join(line))

if __name__ == "__main__":
    cmd, a = sys.argv[1], sys.argv[2:]
    names = [x.upper() for x in a if x.upper() in WORLDS] or list(WORLDS)
    def pick():
        s = load(); s[a[0].upper()][a[1]]["pick"] = int(a[2]); save(s); print(a[0].upper(), a[1], "pick", a[2])
    {"still": lambda: still(names), "sheet": lambda: sheet(names), "loop": lambda: loop(names),
     "import": lambda: import_(names), "status": status, "pick": pick}[cmd]()

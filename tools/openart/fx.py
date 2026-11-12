#!/usr/bin/env python3
"""2D stills from OpenArt, animated here rather than by the model: projectiles, splashes, the flame, the hangar.

    python3 tools/openart/fx.py gen [name...]      # 2 variants each (nano-banana-pro, ~40 cr each; header-* on local FLUX.2)
    python3 tools/openart/fx.py sheet              # out/fx.png, every variant side by side
    python3 tools/openart/fx.py pick <name> <0|1>
    python3 tools/openart/fx.py import [name...]   # -> sprites/<dir>/1..N.png (or one png)

An image model cannot hold a subject steady across frames, so each effect is one still and the motion
is made on import: a wobble for the acid, a burst for a splash, a flicker for the flame. Bones are one
frame each; the game spins them in flight.
"""
import glob, json, math, os, shutil, subprocess, sys
import numpy as np
from PIL import Image, ImageFilter

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
OUT = os.path.join(HERE, "out", "fx")
STATE = os.path.join(HERE, ".fx-state.json")
SPRITES = os.path.join(ROOT, "src/main/resources/sprites")

KEY = " Isolated on a perfectly flat pure magenta (#FF00FF) background, nothing else in the frame, centred with margin, no text, no shadow on the background."
GLOW = " Isolated on a pure solid black background, centred with margin, glowing so it reads against black, no text, no border."
STYLE = " Pre-rendered 3D CGI game sprite, crisp, high detail, dark horror sci-fi."

# name: (prompt, key mode, import recipe, output, size)
ITEMS = {
 "acid-vomit": ("A single glob of thick glowing acid-green bile vomit in flight, a wet bulbous head with a short "
                "stringy dripping tail streaming upward, bubbles and flecks of half-dissolved bone in it, seen from above." + STYLE + KEY,
                "key", "wobble", "fx-acid-vomit", 96),
 "acid-splash": ("A splash of glowing acid-green bile hitting a surface seen from directly above: a radial burst of "
                 "droplets, smoking sizzling puddle in the middle, thin hissing vapour." + STYLE + KEY,
                 "key", "burst", "fx-acid-splash", 128),
 "bone-skull": ("A single human skull, bloody and slimed, wet red gore and torn sinew clinging to it, cracked bone, "
                "seen from above." + STYLE + KEY, "key", "still", "fx-bone-skull", 64),
 "bone-rib": ("A single bloody human ribcage fragment, a few curved ribs still joined to a piece of spine, wet red gore "
              "and torn sinew, seen from above." + STYLE + KEY, "key", "still", "fx-bone-rib", 64),
 "bone-femur": ("A single bloody human thigh bone, femur, cracked at one end, wet red gore and torn sinew on it, lying "
                "diagonally, seen from above." + STYLE + KEY, "key", "still", "fx-bone-femur", 64),
 "blood-splat": ("A spray of dark red blood and gore flying outward from an impact, seen from directly above: a ragged "
                 "radial splatter with droplets and a few chunks of flesh." + STYLE + KEY,
                 "key", "burst", "fx-blood-splat", 128),
 "engine-flame": ("A single jet-engine exhaust flame pointing straight down: a hot white-blue core at the top fading "
                  "into a long bright blue plume and soft violet tip, sci-fi spaceship thruster." + GLOW,
                  "glow", "flicker", "fx-engine-flame", 96),
 "hangar": ("A dark industrial starship hangar bay seen straight on from the front at eye level: a steel deck with two "
            "empty circular lift pads with glowing amber light rings set apart left and right, heavy overhead gantry "
            "crane and lamps casting pools of light, tool racks, hoses and cable runs along the walls, scorch marks, "
            "low contrast and dark so menu text reads on it, no ships, no people, no text, no logos, cinematic.",
            "opaque", "backdrop", "garage/bay.png", 0),
 "title-backdrop": ("Cinematic key art for a space shooter title screen: deep space seen from high orbit, the curved "
                    "night-side horizon of a dark green-black alien planet sweeping across the lower right with a thin "
                    "glowing militia-green atmosphere rim, a sickly green and violet nebula and sparse stars filling the "
                    "upper right, a distant pale moon. One continuous seamless image with no panels, bars or borders: the "
                    "composition is weighted to the right and the left side fades naturally into dark, nearly empty "
                    "space with sparse dim stars. Low contrast overall, moody, no ships, no people, no text, no logos.",
                    "opaque", "backdrop", "title/backdrop.png", 0),
 "title-logo": ("A video game logo wordmark reading exactly \"SPACE CASE\" in two words on one line, heavy condensed "
                "futuristic capital letters, brushed chrome bevelled faces with a militia-green (#0ec417) glowing inner "
                "edge, slightly italic for speed, crisp and legible, every letter spelled correctly. The logo only, no "
                "tagline, no ships, no other text." + KEY,
                "key", "logo", "title/logo.png", 600),
}
FRAMES = {"wobble": 8, "burst": 12, "flicker": 8}

# Painted menu headings, in the title logo's chrome-and-green, and one wordmark per galaxy in that
# galaxy's material. Generated on FLUX.2 dev through the local Workers AI worker (tools/flux, `npx
# wrangler dev --port 8791`) rather than OpenArt: wide frames, and it spells short titles reliably.
# Read every variant on the sheet before picking -- a misspelt one is simply not imported, and
# MenuTitle falls back to set type for any title without a wordmark.
WORDMARK = ("A video game menu heading wordmark reading exactly \"{text}\" on one line, heavy condensed "
            "futuristic capital letters slanted forward for speed, {look}, crisp and legible, every letter "
            "spelled correctly. The wordmark only, no other text, no icons, no ornaments. Isolated on a "
            "perfectly flat pure magenta (#FF00FF) background, centred with margin, no shadow on the background.")
MENU_LOOK = ("brushed chrome bevelled metal faces with a thin dark outline and a glowing militia-green "
             "(#0ec417) inner edge")
MENU_HEADERS = ["PAUSED", "SETTINGS", "SAVE GAME", "EXTRAS", "STORY", "SOUNDTRACK", "SOUND EFFECTS",
                "UNIVERSE", "RESET PROGRESS", "LOAD GAME", "PILOTS", "CLOUD SAVE", "REPLACE PROGRESS",
                "MULTIPLAYER", "HOST", "JOIN", "KEYBOARD", "ABOUT", "HELP", "GAME OVER", "GALAXY CLEARED",
                "SINGLE PLAYER BOARD", "CO-OP BOARD", "BATTLE BOARD"]
GALAXY_LOOKS = {
    "VERDANCE": "slimy organic green alien flesh letters (#0ec417) with dripping ooze and thin creeping vines, "
                "a dark outline",
    "ASHFALL": "scorched riveted black iron letters cracked open over glowing orange (#e8641c) molten embers, "
               "a dark outline",
    "CRYONIS": "frosted translucent ice crystal letters with frost rime and an icy cyan (#4fd0e8) glow, "
               "a dark outline",
    "TEMPEST": "storm-grey steel letters crackling with pale blue (#7ea8ff) lightning arcs, a dark outline",
    "NULL": "void-black obsidian letters dripping, rimmed with a violet (#9a6bff) glow, a dark outline",
}
HEADERS = {**{t: MENU_LOOK for t in MENU_HEADERS}, **GALAXY_LOOKS}

def header_slug(text):
    return text.lower().replace(" ", "-")

for _text, _look in HEADERS.items():
    ITEMS["header-" + header_slug(_text)] = (WORDMARK.format(text=_text, look=_look), "sampled", "header",
                                             f"header/{header_slug(_text)}.png", 128)

FLUX = "http://localhost:8791/gen"

def load():
    return json.load(open(STATE)) if os.path.exists(STATE) else {}

def run(args):
    p = subprocess.run(args, capture_output=True, text=True)
    if p.returncode:
        raise SystemExit(f"{args[0]} exit {p.returncode}: {p.stdout[-400:]} {p.stderr[-400:]}")

def cmd_gen(names):
    for name in names or ITEMS:
        d = os.path.join(OUT, name); os.makedirs(d, exist_ok=True)
        for v in range(2):
            dest = os.path.join(d, f"{v}.png")
            if os.path.exists(dest):
                continue
            if ITEMS[name][2] == "header":
                import io, urllib.parse, urllib.request
                q = urllib.parse.urlencode({"model": "dev", "seed": v + 1, "w": 1024, "h": 384, "prompt": ITEMS[name][0]})
                for attempt in range(3):  # Workers AI throws the odd transient 500
                    try:
                        with urllib.request.urlopen(f"{FLUX}?{q}", timeout=300) as r:
                            Image.open(io.BytesIO(r.read())).convert("RGB").save(dest)
                        break
                    except urllib.error.HTTPError:
                        if attempt == 2:
                            raise
                print(name, v, "->", dest, flush=True); continue
            tmp = os.path.join(d, f"tmp{v}"); os.makedirs(tmp, exist_ok=True)
            run(["openart", "generate", "image", ITEMS[name][0], "--model", "nano-banana-pro",
                 "-o", tmp + "/", "--yes", "--no-input", "--quiet", "--timeout", "5m"])
            Image.open(sorted(glob.glob(tmp + "/*"))[0]).convert("RGB").save(dest); shutil.rmtree(tmp)
            print(name, v, "->", dest, flush=True)

def cmd_sheet(_):
    cell = 256
    rows = [n for n in ITEMS if os.path.exists(os.path.join(OUT, n, "0.png"))]
    sheet = Image.new("RGB", (cell * 2, cell * len(rows)), (40, 40, 40))
    for r, n in enumerate(rows):
        for v in range(2):
            p = os.path.join(OUT, n, f"{v}.png")
            if os.path.exists(p):
                sheet.paste(Image.open(p).convert("RGB").resize((cell, cell)), (v * cell, r * cell))
    sheet.save(os.path.join(HERE, "out", "fx.png")); print("rows:", rows)

def cmd_pick(args):
    s = load(); s[args[0]] = int(args[1]); json.dump(s, open(STATE, "w"), indent=1)

def cutout(im, mode):
    """RGBA with the background removed: magenta keyed out, or alpha from brightness for glows."""
    a = np.asarray(im.convert("RGB")).astype(np.float32)
    r, g, b = a[..., 0], a[..., 1], a[..., 2]
    if mode == "sampled":
        # FLUX drifts from magenta toward hot pink, image to image, so key on the colour the image
        # actually has: the median of its border. Alpha ramps with distance from it, and the
        # fringe is un-mixed (rgb = (pixel - (1 - alpha) * bg) / alpha) rather than despilled.
        border = np.concatenate([a[:4].reshape(-1, 3), a[-4:].reshape(-1, 3), a[:, :4].reshape(-1, 3), a[:, -4:].reshape(-1, 3)])
        bg = np.median(border, axis=0)
        alpha = np.clip((np.linalg.norm(a - bg, axis=2) - 50) / 90, 0, 1)
        rgb = (a - (1 - alpha[..., None]) * bg) / np.maximum(alpha[..., None], 1e-3)
        # The model also paints drop shadows onto the pink, which un-mixing cannot touch: they are
        # darker than the key, not a blend with it. Anything within ~25 degrees of the key's hue is
        # pulled to its low channel -- a neutral dark shadow. Null's violet sits ~60 degrees away.
        def hue(c):
            r_, g_, b_ = c[..., 0], c[..., 1], c[..., 2]
            return np.degrees(np.arctan2(np.sqrt(3) * (g_ - b_), 2 * r_ - g_ - b_)) % 360
        dh = np.abs((hue(np.clip(rgb, 0, 255)) - hue(bg[None, None, :])[0, 0] + 180) % 360 - 180)
        chroma = np.clip(rgb, 0, 255).max(axis=2) - np.clip(rgb, 0, 255).min(axis=2)
        w = (np.clip(1 - dh / 25, 0, 1) * np.clip(chroma / 40, 0, 1))[..., None]
        rgb = rgb * (1 - w) + np.clip(rgb, 0, 255).min(axis=2, keepdims=True) * w
        out = np.dstack([np.clip(rgb, 0, 255), alpha * 255]).astype(np.uint8)
        img = Image.fromarray(out, "RGBA")
        return img.crop(img.getchannel("A").point(lambda v: 255 if v > 16 else 0).getbbox())
    if mode == "glow":
        alpha = np.clip(a.max(axis=2) * 1.4, 0, 255)
        rgb = np.where(alpha[..., None] > 0, a * 255 / np.maximum(alpha[..., None], 1), 0)
    else:
        # Distance from magenta: green low, red and blue high and equal.
        mag = np.clip((np.minimum(r, b) - g) / 255 * 1.0, 0, 1) * np.clip(1 - np.abs(r - b) / 120, 0, 1)
        alpha = np.clip((1 - mag) * 255 * 1.6 - 60, 0, 255)
        rgb = a.copy()
        # Despill: pull lingering magenta out of the fringe.
        spill = np.clip(np.minimum(r, b) - g, 0, None) * (1 - alpha / 255)[...]
        rgb[..., 0] -= spill; rgb[..., 2] -= spill
    out = np.dstack([np.clip(rgb, 0, 255), alpha]).astype(np.uint8)
    img = Image.fromarray(out, "RGBA")
    box = img.getchannel("A").point(lambda v: 255 if v > 16 else 0).getbbox()
    return img.crop(box)

def square(img, size, fill=0.9):
    """Fit inside a size x size canvas, centred, so a rotated draw does not shift it."""
    s = size * fill / max(img.size)
    img = img.resize((max(1, round(img.width * s)), max(1, round(img.height * s))), Image.LANCZOS)
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    canvas.paste(img, ((size - img.width) // 2, (size - img.height) // 2), img)
    return canvas

def wobble(img, size, n):
    frames = []
    for i in range(n):
        t = 2 * math.pi * i / n
        sx, sy = 1 + 0.08 * math.sin(t), 1 - 0.08 * math.sin(t)  # squash and stretch, volume kept
        w = img.resize((max(1, round(img.width * sx)), max(1, round(img.height * sy))), Image.LANCZOS)
        w = w.rotate(6 * math.sin(t + 1), resample=Image.BICUBIC, expand=True)
        frames.append(square(w, size, 0.82))
    return frames

def burst(img, size, n):
    frames = []
    for i in range(n):
        p = (i + 1) / n
        scale = 0.35 + 0.65 * (1 - (1 - p) ** 3)  # fast out, settles
        f = square(img, size, 0.95 * scale)
        if p > 0.5:
            fade = 1 - (p - 0.5) / 0.5
            f.putalpha(f.getchannel("A").point(lambda v: int(v * fade)))
        frames.append(f)
    return frames

def flicker(img, size, n):
    frames = []
    for i in range(n):
        t = 2 * math.pi * i / n
        stretch = 1 + 0.12 * math.sin(t) + 0.05 * math.sin(3 * t + 0.7)
        w = img.resize((max(1, round(img.width * (1 - 0.04 * math.sin(2 * t)))), max(1, round(img.height * stretch))), Image.LANCZOS)
        canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        s = size * 0.95 / max(img.size)
        w = w.resize((max(1, round(w.width * s)), max(1, round(w.height * s))), Image.LANCZOS)
        canvas.paste(w, ((size - w.width) // 2, 0), w)  # anchored at the top: the nozzle end
        frames.append(canvas)
    return frames

def cmd_import(names):
    s = load()
    for name in names or [n for n in ITEMS if n in s]:
        prompt, mode, recipe, dest, size = ITEMS[name]
        src = Image.open(os.path.join(OUT, name, f"{s[name]}.png"))
        if recipe == "backdrop":
            from PIL import ImageOps
            img = ImageOps.fit(src.convert("RGB"), (996, 864), Image.LANCZOS)
            img = Image.eval(img, lambda v: int(v * 0.7))
            os.makedirs(os.path.dirname(os.path.join(SPRITES, dest)), exist_ok=True)
            img.save(os.path.join(SPRITES, dest), optimize=True)
            print(name, "->", dest); continue
        cut = cutout(src, mode)
        if recipe in ("logo", "header"):  # one png, size px wide (logo) or tall (header)
            # Faint keyed-out speckle far from the letters would otherwise set the crop box.
            cut.putalpha(cut.getchannel("A").point(lambda v: 0 if v < 48 else v))
            cut = cut.crop(cut.getchannel("A").getbbox())
            cut = (cut.resize((size, round(cut.height * size / cut.width)), Image.LANCZOS) if recipe == "logo"
                   else cut.resize((round(cut.width * size / cut.height), size), Image.LANCZOS))
            os.makedirs(os.path.dirname(os.path.join(SPRITES, dest)), exist_ok=True)
            cut.save(os.path.join(SPRITES, dest), optimize=True)
            print(name, cut.size, "->", dest); continue
        frames = ([square(cut, size)] if recipe == "still" else
                  {"wobble": wobble, "burst": burst, "flicker": flicker}[recipe](cut, size, FRAMES[recipe]))
        d = os.path.join(SPRITES, dest); shutil.rmtree(d, ignore_errors=True); os.makedirs(d)
        for i, f in enumerate(frames, start=1):
            f.save(f"{d}/{i}.png", optimize=True)
        print(name, len(frames), "frames ->", dest, flush=True)

if __name__ == "__main__":
    cmd, args = (sys.argv[1] if len(sys.argv) > 1 else "sheet"), sys.argv[2:]
    {"gen": cmd_gen, "sheet": cmd_sheet, "pick": cmd_pick, "import": cmd_import}[cmd](args)

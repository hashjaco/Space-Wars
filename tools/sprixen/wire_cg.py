#!/usr/bin/env python3
"""Points imported CG flagships at the game: appends BossArt *_CG rows and re-points Boss rows.

    python3 tools/sprixen/wire_cg.py <slug>...

The box of each new row is copied from the generated constant it replaces (the box is the hitbox),
the frame count is read off disk, and ticks per frame is chosen so the loop plays at the speed it
was rendered. Parts (heads, eyes, cockpits, arms) re-point the Boss methods that hand them out.
Idempotent: a slug already wired is skipped.
"""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(os.path.dirname(HERE))
JAVA = os.path.join(ROOT, "src/main/java/com/hashimjacobs/spacecase")
ART = os.path.join(JAVA, "asset/BossArt.java")
BOSS = os.path.join(JAVA, "entity/Boss.java")
MAN = json.load(open(os.path.join(HERE, "manifest", "bosses.json")))
ROWS = {r["slug"]: r for r in MAN["bosses"] + MAN["parts"]}
# A Boss constant whose art is named for its rig rather than itself.
ART_FOR = {"VAUNT": "FORGE_RIG", "VAUNT_IN_THE_STORM_RIG": "STORM_RIG"}
TICKS_PER_FRAME = 24  # ~95 source frames at 10 fps sampled to 24: one sampled frame per 0.4 s

def wire(slug):
    row = ROWS[slug]
    base = ART_FOR.get(row["boss"], row["boss"])
    cg = base + "_CG"
    art = open(ART).read()
    if re.search(r"^\s+" + cg + r"\(", art, re.M):
        print(slug, "already wired"); return
    m = re.search(r"^\s+" + base + r'\("[^"]+", \d+, \d+, ([\d.]+), ([\d.]+)\)', art, re.M)
    if not m:
        raise SystemExit(f"no generated BossArt row {base}")
    w, h = m.group(1), m.group(2)
    d = os.path.join(ROOT, "src/main/resources/sprites", f"boss-{slug}-cg")
    frames = len([f for f in os.listdir(d) if re.fullmatch(r"\d+\.png", f)])
    line = f'    {cg}("boss-{slug}-cg", {frames}, {TICKS_PER_FRAME}, {w}, {h})'
    # Append after the last constant: the enum's constant list ends at the first ';' line.
    end = re.search(r"\);\n\n", art)
    art = art[:end.start()] + "),\n" + line + art[end.start() + 1:]
    open(ART, "w").write(art)
    boss = open(BOSS).read()
    n = 0
    for pattern in (r"BossArt\." + base + r"(_SX)?\b",):
        boss, n = re.subn(pattern, "BossArt." + cg, boss)
    open(BOSS, "w").write(boss)
    print(slug, "->", cg, f"{frames} frames, box {w}x{h}, {n} Boss reference(s) re-pointed")

if __name__ == "__main__":
    for s in sys.argv[1:]:
        wire(s)

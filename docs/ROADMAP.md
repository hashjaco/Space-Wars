# Universe roadmap

Where the campaign is, and how to build the rest of it.

The plan is five galaxies of ten levels. Two are done. This file is what the remaining three need,
the rules that will bite while building them, and the things already learned the hard way.

## Where it stands

| | |
|---|---|
| Levels | 20 of 50 |
| Galaxies | `VERDANCE` (1–10), `ASHFALL` (11–20) |
| Tests | 420, all headless |
| `src/main/resources` | 53 MB |
| Per galaxy, measured | ~156 PNGs, ~6 MB, ~4 s of generator time |

Three galaxies left, so budget roughly **470 PNGs and 18 MB**, finishing near 71 MB. Generator
runtime is not a concern: it was 3.1 s at ten levels and 4 s at twenty, so fifty lands well inside
ten seconds. Ignore any advice about parallelising it.

The framework is finished. Everything below is data plus a small, named amount of new code.

---

## The rules that will bite

These are not style preferences. Each one is a build failure, or worse, a silent one.

**1. Generated art must be byte-reproducible.** CI runs `java tools/GenerateAssets.java` and fails if
`git status --porcelain src/main/resources/sprites` is dirty. Trig goes through `StrictMath`, never
`Math` — `Math.sin` is allowed a ulp of error and may differ between JVMs. Never
`Graphics2D.drawString`: font availability differs per machine. One `Random` per image.

**2. Never edit an existing draw method.** Add a new one. Changing `hydraTorsoFrame`, `bossFrame` or
`ground` regenerates committed art and breaks rule 1 for every level that already used it. When a
new boss wanted a parameterised organic frame, `creatureFrame` was written *beside* the hydra's
rather than by generalising it — a little duplication is the cheaper side of that trade. Where a
shared method genuinely must change, add an optional field with a default equal to the current
behaviour, as `BossProfile.plate` does.

**3. A galaxy's `Level` constants, its `Boss` constants and its `Galaxy` constant land in one
commit.** `GalaxyTest` asserts `Galaxy.values().length * 10 == Level.values().length` and `LevelTest`
asserts the boss↔level bijection in both directions. A half-landed galaxy does not compile-and-pass;
it just fails.

**4. Append, never insert or reorder.** Saved progress is a bitmask indexed by `Level.ordinal()`, and
`SaveSlot` keys the level by name. Inserting a level hands every player somebody else's progress.

**5. Boss health *and* score rise strictly with enum order.** `BossTest` holds it. The ladder
continues across galaxies rather than restarting — `ASHFALL` ends at Vaunt on 2800 health / 2550
score, so `CRYONIS` starts above that. The per-level difficulty ramp in `prefs.Difficulty` restarts
each galaxy, so the two do not compound.

**6. Tests must never start the JavaFX toolkit.** `KeyCode` and `Color.web` are fine; `Font`, `Scene`
and `Canvas` are not. Keep cursor arithmetic and rules in plain classes — the split between
`SystemMapModel` and `SystemMapView` is the pattern.

**7. Every asset directory must be named in `ASSETS.md`.** The provenance test's needle for a nested
path is the *directory* name, so a glob like `level-{21..30}` does **not** contain `level-27` and the
test fails. Spell the ten out.

**8. Do not add a `random.next*()` call to an existing spawn path.**
`SpawnDirectorTest.theSameSeedProducesTheSameRun` pins the draw order. New constraints clamp the
result after the draw, as the terrain lane check does.

**9. A side-on level is four things that must agree.** The sky tiles horizontally
(`Theme(..., sideways = true)`), the hulls are cut pointing left (`Faction(..., sideways = true)`),
the `Sprite` constants swap width and height, and the flagship's frames are turned once by the
generator (`BossProfile(..., sideways = true)`) rather than rotated at render time.
`LevelTest.sideOnLevelsDeclareTransposedEnemySizes` guards the third; nothing guards the others.
Getting it wrong draws a ship with its hitbox at right angles and reads as a collision bug.

---

## Carried forward from Ashfall

**Three hull families per galaxy, not one.** Ashfall was built on the theory that six flagships could
share one hull and be told apart by turret and engine counts. Drawn side by side, five of the six
were the same ship: at two hundred pixels the eye reads silhouette and colour long before it counts
anything. Ashfall now uses three hulls and three wings paired up, with a plate colour per class.
Budget the same. Use `tools/preview/BossSheet` and look before committing four hundred frames.

**Render it and look at it.** The 420 tests cannot see a picture. Everything in
`tools/preview/README.md` earned its place by catching something the suite was happy with.

**Backdrop house style is what it is.** `SURFACE` levels are grey-and-coloured mounds, `CAVERN`
levels are near-black with light strips, `STARFIELD` levels are sparse. That is true of levels 3, 4,
5, 9 and 10 as much as of anything new. Matching it is correct; changing it means regenerating all
committed art and altering the whole game's look, which is a separate decision and a large one.

**`WorldTemplate` rows go in as the level that needs them is tuned.** An untuned row is worse than no
row. There are two constants today, `OPEN_FIELD` and `CAVE`; canyon, ice tunnel, lava fissure and
event horizon are each one row when somebody is actually playing the level.

---

## Phase 2 — Cryonis (21–30)

Ice and water. Seeds **4400–4490**. Galaxy accent: `#4fd0e8`.

**Structural identity: pacing.** Waves alternate short and long — `3,5,3,5,3,5,4,6,4,6` — so the
galaxy feels different on the clock rather than only in the palette. It also has **two** side-on legs,
the only galaxy that does.

| # | Level | `Backdrop` | Notes |
|---|---|---|---|
| 21 | Frost Ring | `BELT` | **side-on** |
| 22 | Rime Sky | `ATMOSPHERE` | |
| 23 | Glacier Shelf | `SURFACE` | |
| 24 | Under-Ice | `ATMOSPHERE` | water: cold blue tints, light shafts |
| 25 | Crevasse | `CAVERN` | `CAVE` template |
| 26 | Black Trench | `CAVERN` | `CAVE` template |
| 27 | Geyser Flats | `SURFACE` | |
| 28 | Hailwall | `ATMOSPHERE` | |
| 29 | Shatter Drift | `BELT` | **side-on** |
| 30 | The Frozen Heart | `CAVERN` | `CAVE` template, finale |

`BELT` and `EVENT_HORIZON` are safe side-on because they are axis-agnostic. `ATMOSPHERE`, `SURFACE`
and `CAVERN` all have a built-in up and must stay top-down.

**Bosses.** Ladder: x1–x9 health 2860 → 3340 in steps of 60, score 2600 → 3000 in steps of 50;
finale **3700 / 3200**. Six warships from three new hull families, three creatures via
`CreatureProfile`, and one set piece:

**The Frozen Empress — `heads = 4`, reusing `BossHead` entirely.** No new class. `BossHead` was
generalised in Phase 0 to spread any number of heads across its arc, and `BossTest` already covers
one to six. One thing to get right: the generator's hydra torso draws **three** neck sockets from a
hardcoded array, and `BossHead.socket()` now computes `0.5 + SOCKET_SPAN * (spread - 0.5)`. A
four-headed torso needs four sockets drawn at those positions or the necks grow out of blank hide.
Draw the torso with a new method — see rule 2.

## Phase 3 — Tempest (31–40)

Storm and gas giant. Seeds **4500–4590**. Accent `#7ea8ff`.

**Structural identity: no floor.** Six of ten are `ATMOSPHERE`, one is ground, and the finale arrives
out of a cloud deck rather than flying in.

Cloudwall · Thunderhead · The Eye · Ring Debris (`BELT`) · Static Canyon (`SURFACE`) · Mag-Storm
Caverns (`CAVERN`, `CAVE`) · Deep Descent · Upper Deck · Lightning Reach (`STARFIELD`, **side-on**) ·
**Storm Crown** (finale).

Ladder: x1–x9 3760 → 4240 health, 3250 → 3650 score; finale **4700 / 3850**.

**Two set pieces, both nearly free:**

- **Vaunt returns**, in a bigger rig. A data row on the existing `PilotedMech` — new `Boss` constant,
  new `BossArt`, larger numbers. This is the payoff for building him in Ashfall.
- **The Storm Serpent** reuses `BurrowingWorm` top-down, striking down out of the cloud deck instead
  of sideways out of a wall. `BurrowingWorm.strikeDepth()` was made orientation-derived in Phase 0
  precisely for this, so it should need only a `Boss` row, art, and one entry in the
  `SpawnDirector.maybeSpawnBoss` switch.

## Phase 4 — Null (41–50)

Void, gravity, the black hole. Seeds **4600–4690**. Accent `#9a6bff`.

**Structural identity: no sky and no ground.** Not one `ATMOSPHERE` or `SURFACE` level in the galaxy.
Nothing to fly over and nothing overhead; the only enclosures are dead structures.

Dead Belt (`BELT`) · Hulk Drift · Shroud · Lens Corridor (`EVENT_HORIZON`) · Tidal Shear
(**side-on**) · The Shell (`CAVERN`, `CAVE`) · Ergosphere · Photon Ring · The Throat (`CAVERN`) ·
**Event Horizon** (finale).

Ladder: x1–x9 4780 → 5420 health, 3900 → 4300 score; finale **6000 / 4600**. Sanity check: 6000
health at a maxed ship's ~200 damage a second is about thirty seconds of perfect fire — a final boss,
not a sponge.

**This is the phase with real new code in it.** Three pieces:

1. **`Backdrop.EVENT_HORIZON`** — a black disc smaller than the canvas (per the wrap constraint
   `planet()` documents), an accretion ring as two `RadialGradientPaint` annuli, and lensing streaks,
   all inside `wrapped()`. The one image in the game worth extra time.
2. **`BossPhase.VORTEX`** — a full-arena ring whose centre rotates and whose spread tightens. One
   enum constant plus one branch in `EnemyWeapons.centreAngleFor`, beside the existing `SPIRAL` and
   `SPAWNER` cases. Used by the final boss and nothing else.
3. **`VoidEntity` + orbiting eyes** — Aeon, the Hollow Star. Orbits the arena centre as a pure
   function of age, the discipline `BurrowingWorm` and `PilotedMech` both keep. Four eyes shield it
   through the existing parts path, so `heads = 4` pays for the `BossHead` work a second time.
   **Watch the hitbox:** `BossArt`'s declared size *is* the collision box, and a 300×300 entity is a
   300×300 box. Tune it against the orbit radius; there is no separate hitbox to fall back on.

A galaxy with `engines = 0` on its flagships is free characterisation — a navy with no thrusters, in
the galaxy about gravity. Verified safe: the engine loop simply does not run.

## Phase 5 — Polish

- Per-upgrade garage icons: 12 shapes at 24×24 under `sprites/garage/`, one `Sprite` constant each,
  one `ASSETS.md` row (the directory name covers all twelve). **Never `drawString`** — see rule 1.
  `insignia()` shows how to draw a symbol without a font.
- `Difficulty.enemyScale(galaxy)`, health only — but only if galaxy 5 actually plays flat.
  `SpawnDirector.escalated` already raises spawn pressure per galaxy, so this may not be needed.
  Never scale score: it feeds the bonus-to-credits divisor and would double the economy.
- HUD and debrief galaxy labels; `README.md` still describes a ten-level game.
- The health bar's green → gold → red ramp is the primary health signal and a deutan collision. It
  deserves its own ticket rather than being folded into a content phase.

---

## Per-phase checklist

Working order, one galaxy at a time:

1. **Generator data.** Ten `Theme` rows (seeds ten apart), ten `Faction` rows, warship
   `BossProfile` rows across three hull families, `CreatureProfile` rows, any new draw methods.
   Run `java tools/GenerateAssets.java`. **Confirm `git status src/main/resources/sprites` shows no
   modified files** — only new ones. A modified file means rule 2 was broken.
2. **Look at it.** `tools/preview/Sheet 21 30` for the backdrops, `tools/preview/BossSheet` for the
   flagships next to an existing galaxy's. Iterate here, not after four hundred frames exist.
3. **Java constants**, in one commit: 60 `Sprite`, 10 `BossArt` (+ parts), 10 `Boss`, 10 `Level`, the
   `Galaxy` constant.
4. **Set-piece code**, if the phase has any. Give each new rule a test — the mech's guard rule is the
   model: it is the one thing about that boss that is logic rather than numbers.
5. **`ASSETS.md`** section, with all ten directory names spelled out.
6. **`mvn -B verify`**, then `java tools/GenerateAssets.java && git status --porcelain
   src/main/resources/sprites` one more time.
7. **Commit code and data first, regenerated PNGs second**, so `git log -p` on the first commit is
   readable without a hundred binary blobs.

## The one long-term cost

PNGs do not delta-compress. Any future change to a shared draw method rewrites every frame that used
it — several megabytes of new history per commit. The working rule is the same as rule 2 for a
different reason: once art has landed, add per-row parameters, never edit the method.

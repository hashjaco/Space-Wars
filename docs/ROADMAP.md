# Universe roadmap

Where the campaign is, and how to build the rest of it.

The plan was five galaxies of ten levels. **All five are done.** This file is the rules that bit
while building them and the things learned the hard way, kept because Phase 5 and anything after it
still has to live with them.

## Where it stands

| | |
|---|---|
| Levels | **50 of 50** |
| Galaxies | `VERDANCE` (1–10), `ASHFALL` (11–20), `CRYONIS` (21–30), `TEMPEST` (31–40), `NULL` (41–50) |
| Tests | 453 executed, all headless |
| `src/main/resources` | 71 MB |
| Per galaxy, measured | ~150 PNGs, ~6 MB, ~5.9 s of generator time |

The campaign is complete. Null came in at 148 PNGs and 6 MB against a budget of 155 and 7, and the
generator finished in 5.9 s at fifty levels against the 3.1 s it took at ten — so the projection
held and the advice to ignore parallelising it still stands.

**The test figure counts executed cases, not `@Test` annotations.** The two disagree, because some
cases are parameterised: at forty levels this table said 434, which was the annotation count at that
commit and never the number of tests that ran. Quote one or the other and say which.

Only Phase 5 is left, and none of it is content.

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

**Render it and look at it.** The tests cannot see a picture. Everything in
`tools/preview/README.md` earned its place by catching something the suite was happy with.

**Backdrop house style is what it is.** `SURFACE` levels are grey-and-coloured mounds, `CAVERN`
levels are near-black with light strips, `STARFIELD` levels are sparse. That is true of levels 3, 4,
5, 9 and 10 as much as of anything new. Matching it is correct; changing it means regenerating all
committed art and altering the whole game's look, which is a separate decision and a large one.

**`WorldTemplate` rows go in as the level that needs them is tuned.** An untuned row is worse than no
row. There are two constants today, `OPEN_FIELD` and `CAVE`; canyon, ice tunnel, lava fissure and
event horizon are each one row when somebody is actually playing the level.

## Carried forward from Cryonis

**The galaxy accent does not go in a `Theme` row.** `tintB` means *a lit surface* to `rocks()` and
`ground()` — a rock's sunward face, a mound's top. Cryonis's first pass used `#4fd0e8` there, being
the galaxy colour, and every ice shard rendered as a glowing ball and every hill as a bubble.
Ashfall runs `0x9a4a1e` and `0x7a3014` in those slots: mid-dark and desaturated. Match that
*weight*, in the new galaxy's hue. The accent is worn by the ships, which is where it reads. The
same applies to `glow` on a `BossProfile` — a near-white glow turns the core into a flare that
swallows the hull, which is why Ashfall's two palest flagships are its least readable.

**Classes separate by aspect, not by plate colour.** Rendered side by side, Ashfall's six plates are
all one brown and its six ships are still instantly distinguishable, because their aspect ratios
span 0.95 to 1.96. Cryonis's first pass had good silhouettes clustered in a narrow aspect band and
read worse. Give every galaxy one tall-narrow outlier, the `slag-baron` slot.

**A creature cannot guard a side-on level.** `CreatureProfile` has no `sideways` field, where
`Theme`, `Faction` and `BossProfile` all do. Side-on legs field warships or a set piece. Cryonis's
21 and 29 are warships for that reason, not by preference.

**Adding a part to a flagship must not thicken its barrage.** Both the bullet cooldown and the acid
cooldown were flat constants tuned when every multi-part boss fielded exactly three parts, so the
part count was invisible in the arithmetic. The Frozen Empress's fourth head would have added a
third more fire and a fifth ball of acid with nobody having chosen either. Both now derive from
`EnemyShip.siblingParts()`. **Derive from the part count, never from `Boss.heads()`** — the two
disagree, because the rig declares two heads and fields three targets.

---

## Phase 2 — Cryonis (21–30) — **done**

Ice and water. Seeds **4400–4490**. Galaxy accent: `#4fd0e8`. Built as described below, with two
corrections found while building; both are folded into *Carried forward* above.

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

## Phase 3 — Tempest (31–40) — **done**

Storm and gas giant. Seeds **4500–4590**. Accent `#7ea8ff`. Built as described below. Both set pieces
landed, and both cost more than this entry said they would; the difference is folded into *Carried
forward* above and spelled out here.

**Structural identity: no floor.** Six of ten are `ATMOSPHERE`, one is ground, and the finale arrives
out of a cloud deck rather than flying in. Waves run `4,4,5,5,5,5,6,6,6,6` — a plain climb, because
this galaxy's shape is its sky rather than its clock.

| # | Level | `Backdrop` | Notes |
|---|---|---|---|
| 31 | Cloudwall | `ATMOSPHERE` | |
| 32 | Thunderhead | `ATMOSPHERE` | |
| 33 | The Eye | `ATMOSPHERE` | |
| 34 | Ring Debris | `BELT` | |
| 35 | Static Canyon | `SURFACE` | the galaxy's only ground |
| 36 | Mag-Storm Caverns | `CAVERN` | `CAVE` template |
| 37 | Deep Descent | `ATMOSPHERE` | |
| 38 | Upper Deck | `ATMOSPHERE` | Vaunt returns |
| 39 | Lightning Reach | `STARFIELD` | **side-on** |
| 40 | Storm Crown | `ATMOSPHERE` | finale |

Ladder: x1–x9 3760 → 4240 health, 3250 → 3650 score; finale **4700 / 3850**. Five warships from three
hulls and three vanes, three creatures, and the two set pieces.

**The Storm Serpent was as cheap as promised, in the engine.** `BurrowingWorm.strikeDepth()` really is
orientation-derived, the class holds no axis of its own, and a top-down level turns the strike with no
changes to it. The `SpawnDirector` entry really was one line.

It was **not** free in the art, in three ways this entry did not name. `wormMawFrame` draws the maw
opening left, so top-down needs a new one. `Renderer.drawWormBody` hardcoded `Sprite.WORM_SEGMENT`,
and `wormSegment()` is not rotationally symmetric — it carries bristles down one side, right for an
animal crossing the screen and wrong for one striking down it. And `STRIKE_REACH` was a single
constant shared with the Leviathan: what decides whether a strike is fair is the reach *plus* the
art's extent along the strike axis, and both differ between a 996-deep side-on arena and an 864-deep
top-down one, so the shared 0.62 put the serpent's jaws past the line the player spawns on. It
carries its own 0.53 now, and level 9 is untouched.

**Vaunt's return was not a data row.** Three things:

- `PilotedMech` hardcoded `BossArt arm = BossArt.FORGE_RIG_ARM`, so a bigger rig wore Ashfall's
  78-pixel pods. Now `Boss.armArt()`, a method rather than a tenth constructor argument, as
  `Boss.music()` is.
- The generator has no rig profile: `mechFrame`, `armFrame` and `cockpitFrame` took only a frame
  index. They now take canvas and plate colours too, defaults equal to what Ashfall always passed —
  rule 2's escape hatch rather than a hundred lines of second walking machine.
- **A `PilotedMech` row must declare `heads >= 1`** even though the class discards the parts they
  imply. `EnemyShip.bodyShare` hands a flagship all of its authored health at zero and `EnemyWeapons`
  arms it with rocket salvos, so a rig written with `heads = 0` would carry 145% of its stated health
  and fire something no rig has ever fired.

## Carried forward from Tempest

**Six levels on one recipe need to be separated on the axis that recipe actually reads.** Tempest
runs `sky()` six times. The first pass spread those rows on `blobs` — 10, 4, 3, 6, 7, 9 — and it
changed nothing whatever, because `sky()` never reads `blobs`. It reads `density` for the deck count
and paints every deck `brighten(tintB, 78)` over a `tintA`/`tintB` gradient. Six pale `tintB` values
are six white decks on six white bands, and on the sheet they were one sky. `blobs` does real work
only in `stars()`, `ground()` and `tunnel()`. **Check which fields the recipe you picked consumes
before tuning them.**

**A hull runs nose-down: `y = 0.99` is the nose, `y = 0.02` is the tail.** That is where `bossFrame`
puts the prow blade and the engine bank. Tempest's first three hulls were written widest at the nose
and pointed at the tail, and all three rendered as the same wide dome no matter how their vertices
differed. `CRYO_PROW` is the reference — a lens, pointed at both ends, widest below the middle.

**Aspect separates classes, and three of five is already too many in one band.** Cryonis said this
about plate colour; Tempest found the floor. Three of its five warships sat between 1.3 and 1.9 on
two hulls and read as one ship on `BossSheet`. They now span 0.75 to 2.06, and no two of the three
broad classes share a hull. The two that repeat one are the tall Mast and the turned Delta, which
cannot be confused with anything.

**Rule 8 also covers the *number* of draws, not only new ones.** `maybeSpawnEnemy` checks the
population against the difficulty cap *before* it rolls, so that early return decides whether
`nextInt(1000)` is consumed this tick. Anything that changes how many enemies are on the field
therefore shifts every subsequent draw in the stream, asteroids included — no new `random.next*()`
call is added, but the stream moves just the same. Authored waves did exactly that, deliberately and
with nowhere better to go: rolling before the cap check *adds* a draw, and exempting wave ships from
the count makes the cap not a cap. Every seeded assertion in `SpawnDirectorTest` is a property
rather than a golden number, which is why they survived it; if you add one, keep it that way.

**`SpawnDirectorTest.theSameSeedProducesTheSameRun` does not enforce rule 8.** It counts asteroids
over two runs in the same JVM at the same code version, so it proves determinism and nothing else. An
added `random.next*()` call in a spawn path would fail no test at all. Rule 8 is a convention held by
reading, so read it.

**`PilotedMech` has a width ceiling of 398 and nothing guarded it.** The stride walks the lane centre
to 0.8 of the arena breadth and subtracts half the body's width, so a wider rig overhangs the edge.
`PilotedMechTest` asserted `x() <= 996`, which is vacuously true of anything on screen — it now checks
the trailing edge, for every rig rather than the two that exist.

## Phase 4 — Null (41–50) — **done**

Void, gravity, the black hole. Seeds **4600–4690**. Accent `#9a6bff`. Built as described below. All
three code pieces landed roughly as budgeted, which is the first phase that can be said of; what it
did not budget was four defects in the art and the arithmetic, all four found by looking at the
thing rather than by running the suite. They are in *Carried forward* below.

The level list under-specified itself: it named backdrops for five of the ten and banned
`ATMOSPHERE` and `SURFACE`, which left five to choose. The choice made was to spread the galaxy
across **five recipes**, the widest any galaxy has used, and to give the four `EVENT_HORIZON` levels
an authored disc progression — 0.10, 0.20, 0.26, 0.32 — so they read as one fall toward the hole
rather than as four skies with the same object in them. Waves run `6,6,6,6,5,5,5,4,4,4`, the only
galaxy that shortens as it goes.

| # | Level | `Backdrop` | Waves | Notes |
|---|---|---|---|---|
| 41 | Dead Belt | `BELT` | 6 | |
| 42 | Hulk Drift | `STARFIELD` | 6 | emptiest sky in the game |
| 43 | Shroud | `PLANET_RISE` | 6 | a world that went out |
| 44 | Lens Corridor | `EVENT_HORIZON` | 6 | `disc` 0.10, first sighting |
| 45 | Tidal Shear | `BELT` | 5 | **side-on** |
| 46 | The Shell | `CAVERN` | 5 | `CAVE` template |
| 47 | Ergosphere | `EVENT_HORIZON` | 5 | `disc` 0.20 |
| 48 | Photon Ring | `EVENT_HORIZON` | 4 | `disc` 0.26, brightest backdrop in the game |
| 49 | The Throat | `CAVERN` | 4 | `CAVE` template |
| 50 | Event Horizon | `EVENT_HORIZON` | 4 | `disc` 0.32, finale |

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
   enum constant, plus **two** touches rather than one. The rotating centre is a branch in
   `EnemyWeapons.centreAngleFor` beside `SPIRAL`. The tightening spread cannot go there — that
   method returns only the centre angle, and `phase.spreadRadians()` is read as a constant twice in
   `firePattern`, once for `firstOffset` and once per shot. A spread that varies needs `firePattern`
   to ask for it per tick. Budget both. Used by the final boss and nothing else.
3. **`VoidEntity` + orbiting eyes** — Aeon, the Hollow Star. Orbits the arena centre as a pure
   function of age, the discipline `BurrowingWorm` and `PilotedMech` both keep. Four eyes shield it
   through the existing parts path, so `heads = 4` pays for the `BossHead` work a second time.
   **Watch the hitbox:** `BossArt`'s declared size *is* the collision box, and a 300×300 entity is a
   300×300 box. Tune it against the orbit radius; there is no separate hitbox to fall back on.

A galaxy with `engines = 0` on its flagships is free characterisation — a navy with no thrusters, in
the galaxy about gravity. Verified safe: the engine loop simply does not run.

## Carried forward from Null

**A backdrop that occludes something must own every layer it appears on.** The renderer stacks far,
mid and near with ordinary alpha, so anything opaque on a nearer layer lands on top of a subject
painted on the far one. The black hole was drawn on the far layer with `stars()` still running on all
three, and the finale shipped its first pass as a circle with stars inside it — a translucent hole,
which is the one thing a hole cannot be. `eventHorizon` now draws its own stars and clips the horizon
out of the two nearer layers. Nothing in the suite can see this; it took looking at the sheet.

**A per-layer `Random` cannot place a feature that more than one layer has to agree about.**
`backgrounds()` seeds one `Random` per image as `seed + layer` — which is right, and is what keeps a
change to one layer from shifting another — but `stars()` draws from it a different number of times
on each layer. So two draws taken afterwards for a position put that position somewhere different on
all three. The lensing streaks were struck around a centre the disc was nowhere near, and the comment
above them claimed they shared one. **Anything two layers must agree on has to come from the seed by
arithmetic, not from the draw.** The false comment is the part worth remembering: it was written from
the intent rather than from the code, and it hid the defect for as long as it stood.

**For a multi-part boss that moves, the parts set the bounds, not the body.** A `BossArt`'s declared
size is the collision box, so it is tempting to size an orbit against it — and it is wrong by a wide
margin. `BossHead` spreads its sectors symmetrically but lengthens every neck by `NECK_LENGTH_STEP`
per index, so the outermost head on the `+across` side is also the one on the longest neck: measured,
Aeon's eyes reach 298 one way and 221 the other. The envelope is wider than the body and *not centred
on it*. The first pass sized the orbit from an estimate of that reach, was 40 pixels out, and swung an
eye through the side wall. `AeonTest` steps the parts rather than only the body for exactly that
reason, and it caught it.

**A subclass that wants parts must call the flagship constructor, not the protected one.**
`EnemyShip(Boss, x, y, scale)` sizes the box from the art, applies `bodyShare` and builds a
`BossHead` per declared head; the protected eight-argument constructor `BurrowingWorm` uses does none
of the three, because a worm is deliberately one hitbox at full health. `VoidEntity` reached for the
worm's out of habit and got a boss with no eyes and 145% of its authored health — the same trap the
Tempest notes record for `PilotedMech`, arrived at from the other direction. One line either way, and
nothing warns you.

**The aspect lesson applies to creatures too.** Ashfall recorded it for hulls, Cryonis recorded it
again, Tempest found the floor, and Null found that `CreatureProfile` has the same failure mode: its
three creatures went in at 1.09, 1.28 and 1.59 and read as one dark rounded mass with pale ribs on
it. Leg counts of 4, 0 and 8 did not save them — at two hundred pixels a leg is a stub and the eye
has the outline first. They are 0.97, 1.94 and 1.25 now. **Whatever the record, separate its rows on
the outline before separating them on anything else.**

**`engines = 0` is free and it reads.** The thrusterless navy the Phase 4 entry proposed as
characterisation cost exactly nothing — the engine loop in `bossFrame` does not run at zero — and on
the boss sheet a fleet with no thrusters in the galaxy about gravity is legible as an idea, not just
as an absence. Three galaxies of stepping the count down (five and six, three and four, two and
three) were worth it for the arrival.

## Phase 5 — Polish

- Per-upgrade garage icons: 12 shapes at 24×24 under `sprites/garage/`, one `Sprite` constant each,
  one `ASSETS.md` row (the directory name covers all twelve). **Never `drawString`** — see rule 1.
  `insignia()` shows how to draw a symbol without a font.
- `Difficulty.enemyScale(galaxy)`, health only — but only if galaxy 5 actually plays flat.
  Never scale score: it feeds the bonus-to-credits divisor and would double the economy.

  This entry used to say `SpawnDirector.escalated` already raised spawn pressure per galaxy, so
  the work might not be needed. **That was wrong, and the correction matters more than the entry
  did.** `escalated` adds `loopsCompleted * LOOP_SPAWN_BONUS`, and `loopsCompleted` only rises in
  `advanceLevel()` when the level wraps to `Level.values()[0]` — that is once per pass through the
  *entire fifty-level campaign*, not once per galaxy. Nothing raises spawn pressure between galaxy
  1 and galaxy 5 on a first play. So the flat feel this entry hedged against is real rather than
  already handled, and the only per-galaxy escalation in the game is the bosses' authored health.
- HUD and debrief galaxy labels; `README.md` still describes a much smaller game.

  This entry used to say the README described a ten-level game. It does not, and it is worse than
  that: `README.md` lists **eight** places by name, says "the ten places" a few dozen lines later,
  and still claims the run loops back after the eighth. Three mutually inconsistent descriptions of
  what is now a fifty-level campaign. `tools/preview/README.md` is stale the same way — it still
  says "the 420 passing tests".
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

# Space Case — press kit

Everything here is generated from the game itself. The screenshots are the real engine at a real
tick; the capsules and plates are composited from the shipped sprites; the trailer is a seeded
capture, not a recording of someone playing. Nothing was drawn by hand for marketing, and nothing
here shows a build that does not exist.

Regenerating any of it is a command, listed under each section. All of them run from the repo root
and need `mvn -q compile` plus the preview classpath first:

```sh
mvn -q compile
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
CP="target/classes:$(cat /tmp/cp.txt)"
javac -cp "$CP" -d /tmp/preview \
  tools/preview/com/hashimjacobs/spacecase/prefs/ScratchSettings.java \
  tools/preview/com/hashimjacobs/spacecase/engine/TrailerPilot.java \
  tools/preview/com/hashimjacobs/spacecase/engine/CaptureHarness.java
javac -cp target/classes -d /tmp/press tools/press/*.java
```

Nothing here is bundled into the game. `src/main/resources` is guarded by `AssetProvenanceTest`
and by the CI check that re-runs the asset generator; `docs/press` is outside both, deliberately.

## The facts, as the code has them

Quote these rather than rounding them.

| | |
|---|---|
| Title | **Space Case** |
| Levels | 50, in five galaxies of ten |
| Flagships | 50, one closing each level |
| Galaxies | Verdance `#0ec417` · Ashfall `#e8641c` · Cryonis `#4fd0e8` · Tempest `#7ea8ff` · Null `#9a6bff` |
| Modes | Single Player · Co-op · Battle · Endless Run |
| Players | Two, sharing one keyboard, or two controllers |
| Difficulties | Easy · Normal · Hard · I Want To Suffer · I Want To Die |
| Soundtrack | 10 original instrumental tracks by the author |
| Platforms | macOS · Windows · Linux |
| Licence | MIT, code and assets alike |
| Built with | Java 26, JavaFX 26 |

Brand type is **Impact** — the first entry in `Assets.DISPLAY_FONTS` and what the game's own title
screen uses. Body type is Verdana. No fonts are bundled with the game, by design.

## wordmark/

`SPACE CASE` on transparency, at 2× for print.

- `wordmark-horizontal.png` — one line, white→green gradient. The default.
- `wordmark-stacked.png` — two lines, for square and vertical placements.
- `wordmark-mono-light.png` — flat white, for dark grounds where the gradient cannot be trusted.
- `wordmark-mono-dark.png` — flat `#05070c`, for light grounds and print.

```sh
tools/press/capsule/wordmark.html          # rendered by the mark() helper in this README's history
```

## screenshots/

Twelve stills spanning all five galaxies, six flagships, both orientations, cave levels and the
brightest and darkest skies in the game.

- `native/` — 996×864, the arena's true size. Use these on itch and GitHub.
- `framed/` — 1920×1080. The arena at 1.25× centred, with the 337px each side carrying the level's
  own sky, its galaxy and its flagship. Use these on Steam, which wants 16:9.

```sh
tools/press/contact.sh 50 1200 1 EASY boss /tmp/sheet.png 60   # pick a frame by looking
tools/press/still.sh   50 1 EASY boss 720 docs/press/screenshots/native/50-aeon.png
java -cp "target/classes:/tmp/press" FrameShot 50 \
     docs/press/screenshots/native/50-aeon.png docs/press/screenshots/framed/50-aeon.png
```

## capsules/

Composited from the real sprites — Aeon the Hollow Star, the Event Horizon sky, the player hull.
Sizes are asserted at render time, because storefronts reject an image that is one pixel out.

**Steam** — main 616×353 · header 460×215 · small 231×87 · vertical 374×448 · library 600×900 ·
library hero 1920×620 · page background 1438×810.

**itch.io** — cover 630×500 · banner 960×400.

The 231×87 capsule carries the wordmark and nothing else; at that size nothing else survives.

```sh
tools/press/capsules.sh
```

## github/

`hero-1280x640.png` — sized for the README header and for GitHub's social preview card
(Settings → General → Social preview).

## posters/

Print-capable plates, 3600px wide. These are the pieces worth showing on their own.

- `flagships.png` — all 50 flagships at **true relative scale**, ordered by level, banded by galaxy,
  each labelled with its name and health. Health runs 900 → 9,000 across the campaign and the plate
  shows that as physical growth. Note this is *not* what `tools/preview/BossSheet` draws: that one
  scales each ship to fill its cell, which is right for telling ships apart and wrong for comparing
  them.
- `skies.png` — all 50 backdrops, each composited far+mid+near as the game stacks them, marked for
  the levels flown side-on and the levels where rock closes in from both sides.

```sh
java -cp "target/classes:/tmp/press" FlagshipPlate docs/press/posters/flagships.png
java -cp "target/classes:/tmp/press" SkyPlate      docs/press/posters/skies.png
```

## trailer/

**The video is not in this repository.** It ships as a [Releases](https://github.com/hashjaco/Space-Wars/releases)
asset, and `docs/press/trailer/*.mp4` is gitignored so it cannot be swept in by a stray `git add`.

| File | | |
|---|---|---|
| `space-case-trailer.mp4` | 1920×1080, 60fps | 83s · 21 MB |
| `space-case-trailer-30s.mp4` | 1920×1080, 60fps | 30s · 9.7 MB |
| `space-case-trailer-square.mp4` | 1080×1080 | 83s · 18 MB |

Why out of tree: video delta-compresses no better than the PNGs that `docs/ROADMAP.md` warns about
under *"The one long-term cost"*, and unlike the sprites it is not a build input, cannot be verified
by CI, and gets re-cut whenever the marketing changes. Each re-cut would append another full copy to
a history that is already 280 MB against an 83 MB tree. The stills stay committed because they are
small, seeded and stable, and because the README wants URLs that do not move.

The cut: six flying legs walking the five galaxies in order, then five flagship fights ending on
Aeon, then the end card. The surround's accent changes with the galaxy, so the frame escalates with
the campaign.

Music is the game's own: `starlight-circuit` (the gameplay cue) under the legs, crossfading to
`grime-quest-remix` (the boss cue) for the montage. Both are composed by the author and MIT like the
rest of the repo, so the trailer needs no separate clearance.

```sh
tools/press/trailer.sh                       # rebuild all three cuts into docs/press/trailer/
gh release upload <tag> docs/press/trailer/*.mp4 --clobber
```

`clips/` holds the per-clip intermediates and is gitignored too — gigabytes, and the script rebuilds
them.

## How the gameplay was captured

There is no screen recording here and nobody played for the camera. `tools/preview/.../CaptureHarness`
stands up the real `World`, `SpawnDirector`, `CollisionSystem` and `Renderer`, flies the ship with a
scripted `IntentSource`, and writes one frame per simulation tick as raw BGRA on stdout for ffmpeg
to encode. It steps by tick rather than by wall clock, so capture speed does not affect what is
recorded, and it runs at about 300 fps — five times faster than the game plays.

The consequence worth knowing: **a capture is reproducible from its seed**. The same seed gives the
same fight, byte for byte. So a run that looks wrong is not tuned away, it is re-rolled —
`tools/press/sweep.sh` tries a handful of seeds at one level and reports which reached the flagship
and how long the pilot lived. Every still and clip in this kit names the seed and frame that
produced it, and re-running that command reproduces it exactly.

The harness changes nothing under `src/main/java`. It reaches the engine through seams that were
already public for the netcode (`setIntentSource`, `setStepGate`) and for level-select replays.

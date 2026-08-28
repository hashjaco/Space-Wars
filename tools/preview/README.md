# Preview harnesses

Throwaway programs that draw something and write a PNG, so it can be looked at instead of reasoned
about. None of them is a test, none runs in CI, and none writes into `src/`.

They exist because the test suite cannot see a picture. Between them these found, in work the 420
passing tests were entirely happy with:

- a cave whose lane was 72% open with no protrusions at all
- a wall face that moved sideways faster than the ship could fly, from a cusp in `|x|^(1/3)`
- a garage readout drawn straight through the next row's meter
- a control hint hidden behind the panels after they moved up
- `LAUNCH` scrolled off the bottom of the garage, so there was no visible way out
- a browsed paint job showing a price but not its name
- five of six new flagships being visibly the same ship

If a change alters what something looks like, draw it and look at it. That is the whole point of
this directory.

## Running them

The plain ones need only the compiled classes:

```sh
mvn -q compile
javac -cp target/classes -d /tmp/preview tools/preview/TerrainPreview.java
java  -cp target/classes:/tmp/preview TerrainPreview /tmp/cave.png
```

| Program | Draws | Arguments |
|---|---|---|
| `Sheet` | A galaxy's backdrops, far+mid+near composited as the game stacks them | `<fromLevel> <toLevel> <out.png>` |
| `BossSheet` | Frame 1 of each named boss, side by side at on-screen size | `<comma,separated,dirs> <out.png>` |
| `TerrainPreview` | A top-down cave at three moments, with a ship for scale | `<out.png>` |
| `SideCave` | The same terrain side-on, so ceiling and floor | `<out.png>` |
| `Codex` | Four concept plates for a sixth galaxy: hulls, places, fauna, one flagship | `<outDir>` |

`BossSheet` is the one to reach for when adding flagships: put the new ones next to an existing
galaxy's and see whether they read as different ships.

`Codex` is the odd one out: it draws nothing that exists yet. It is art direction for the three
garage hulls and the sixth galaxy, in the manner set out in `docs/COLD-TAXONOMY.md`, and it needs no
classpath at all -- `java tools/preview/Codex.java /tmp/plates`. It earned its keep the same way
everything else here did: the first pass built its twelve creatures as one ellipse with twelve
parameter sets and they were, unmistakably on the sheet, one creature. They are six construction
families now. Nothing but looking would have said so.

## The five that need JavaFX

`MapSmoke`, `GarageSmoke`, `MenuSmoke`, `BoardSmoke` and `VignetteSmoke` start the toolkit and
snapshot a real screen, so they need the JavaFX jars on the classpath and they live in the package of the thing they
draw (most of what they reach for is package-private).

```sh
mvn -q compile
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
CP="target/classes:$(cat /tmp/cp.txt)"
javac -cp "$CP" -d /tmp/preview tools/preview/com/hashimjacobs/spacecase/scene/MapSmoke.java
java  -cp "/tmp/preview:$CP" com.hashimjacobs.spacecase.scene.MapSmoke /tmp/map.png 1 5
```

`MapSmoke` takes `<out.png> [galaxyIndex] [levelsCleared]` and writes into a throwaway Preferences
node, so it never touches real progress. `GarageSmoke` takes `<out.png>`.

`MenuSmoke` takes `<out.png>` and draws the start screen -- backdrop, title, buttons. Written for
the pass that moved the menu off `L1_MID`, the level-one layer with nothing in it, onto `L1_FAR`,
which has the planet: the menus had been reading as a plain black field for want of one constant.

`BoardSmoke` takes `<outDir>` and writes four: the score board full and empty, cloud save, and the
confirmation that stands in front of a download. The board is the longest panel in the game -- ten
entries plus two rows is exactly `MenuPanel`'s twelve-row window -- so it is drawn at its worst
case, ten-character names against eight-digit scores, which is the pair that decides whether a row
can fit its own text. It builds a throwaway `Preferences` node for the account: `Account.load()`
would mint and keep this machine's real sync code, and that is the one value in the game that must
never end up in a PNG.

`VignetteSmoke` takes `<out.png> [levelNumber] [0|1]` and draws a level's sky with and without the
corner falloff, reading the real paint off `Renderer` so the preview cannot drift from the game.
Run it on a bright level -- 2 shows the effect where 7 is too dark to judge it.

They copy the snapshot out pixel by pixel rather than using `SwingFXUtils`, because `javafx-swing`
is not a dependency and adding one to save a screenshot would be the worse trade.

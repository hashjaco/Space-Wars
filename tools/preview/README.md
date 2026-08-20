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

`BossSheet` is the one to reach for when adding flagships: put the new ones next to an existing
galaxy's and see whether they read as different ships.

## The two that need JavaFX

`MapSmoke` and `GarageSmoke` start the toolkit and snapshot a real screen, so they need the JavaFX
jars on the classpath and they live in the package of the thing they draw (both of those classes are
package-private).

```sh
mvn -q compile
mvn -q dependency:build-classpath -Dmdep.outputFile=/tmp/cp.txt
CP="target/classes:$(cat /tmp/cp.txt)"
javac -cp "$CP" -d /tmp/preview tools/preview/com/hashimjacobs/spacecase/scene/MapSmoke.java
java  -cp "/tmp/preview:$CP" com.hashimjacobs.spacecase.scene.MapSmoke /tmp/map.png 1 5
```

`MapSmoke` takes `<out.png> [galaxyIndex] [levelsCleared]` and writes into a throwaway Preferences
node, so it never touches real progress. `GarageSmoke` takes `<out.png>`.

They copy the snapshot out pixel by pixel rather than using `SwingFXUtils`, because `javafx-swing`
is not a dependency and adding one to save a screenshot would be the worse trade.

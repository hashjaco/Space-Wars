# Contributing

Thanks for looking. Bug reports, fixes and small features are all welcome.

## Getting it running

    brew install maven      # macOS; any JDK 26 + Maven works
    mvn javafx:run

    mvn test                # the suite needs no display and no audio device
    mvn package             # target/space-case-2.0.jar

JavaFX comes from Maven Central, including the platform natives, so there is nothing to install by
hand. `javafx.version` in `pom.xml` tracks the JDK major version — JavaFX 26 needs a JDK 26 runtime.

## Two invariants worth knowing before you change the engine

Both of these were bugs once, and both are easy to reintroduce.

**1. `World` is the only place entities are added or removed.** Collision handling never removes
anything; it calls `kill()`, and `World.sweep()` clears the dead once per frame after all handling
has finished. Removing an entity while iterating threw `ConcurrentModificationException` on the
JavaFX thread every frame. `WorldSweepTest` covers it.

**2. Sprites and sounds are enums, never string keys.** A `HashMap<String, Image>` returned null for
keys nobody had registered, and the null reached `drawImage` and took down the render thread. If you
add art, add an enum constant — a missing asset should be a compile error.

Two more, less obvious:

- **Simulation speed must not depend on the display.** The loop steps at a fixed 1/60 s via
  `FixedTimestep`; do not move anything by a per-frame constant. The game used to run at double
  speed on a 120 Hz monitor.
- **Assets must be reachable from the classpath, not the working directory.** `new File("sprites/…")`
  breaks the packaged jar. Use `getResourceAsStream`.

## Assets

Every bundled file is listed in [ASSETS.md](ASSETS.md) with its origin, and `AssetProvenanceTest`
fails the build if that drifts. If you add art or audio:

- Either add it to `tools/GenerateAssets.java` so it is generated and reproducible, or contribute
  work you made yourself and are happy to license under MIT.
- Record it in `ASSETS.md`.
- Please do not add anything downloaded from an asset site unless you have checked the specific
  licence and can name it. "Found on the internet" is how the project ended up shipping commercial
  music it had no right to.

Fonts are referenced, not bundled — shipping a font file is redistribution.

## Style

Match what is there. A few things the codebase is consistent about:

- Catch specific exception types, never bare `Exception`.
- Imports at the top of the file.
- Name a constructed value before returning it, so it is inspectable at a breakpoint.
- Comments explain *why*, or document a constraint that the code cannot state itself. They do not
  narrate what the next line does, and they do not describe what changed in a pull request — that
  belongs in the commit message.

## Pull requests

- One concern per pull request.
- `mvn test` green.
- If you fixed a bug, add the test that would have caught it.
- Say what you did and why. Screenshots help for anything visual, since the tests cannot judge how
  the game looks.

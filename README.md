# Space Case

[![CI](https://github.com/hashjaco/Space-Wars/actions/workflows/ci.yml/badge.svg)](https://github.com/hashjaco/Space-Wars/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-0ec417.svg)](LICENSE)
[![Java 26](https://img.shields.io/badge/Java-26-blue.svg)](https://adoptium.net)
[![JavaFX 26](https://img.shields.io/badge/JavaFX-26-orange.svg)](https://openjfx.io)

A 2D space shooter: fly, dodge asteroids, shoot up enemy ships, fight bosses — or turn on each other
in Battle mode. Two players share one keyboard.

Originally written as *Space Wars* to get comfortable before building a game engine from scratch.

<p align="center">
  <img src="docs/screenshots/start-menu.png" alt="Start menu" width="760">
</p>

<p align="center">
  <img src="docs/screenshots/single-player.png" alt="Single player" width="380">
  <img src="docs/screenshots/battle.png" alt="Battle mode" width="380">
</p>

## Running it

    brew install maven      # macOS; any JDK 26 + Maven works
    mvn javafx:run

JavaFX stopped shipping inside the JDK in Java 11, so `pom.xml` pulls it from Maven Central
(including the platform natives) and puts it on the module path. `javafx.version` tracks the JDK major
version — JavaFX 26 needs a JDK 26 runtime.

Prefer a download? Grab a jar for your platform from
[Releases](https://github.com/hashjaco/Space-Wars/releases) and run `java -jar space-case-<os>.jar`.

Other targets:

    mvn test        # unit tests; no display or audio device needed
    mvn package     # target/space-case-2.0.jar

## Modes

| Mode | Players | Enemies | Friendly fire | Ends when |
|---|---|---|---|---|
| Single Player | 1 | yes | no | you run out of lives |
| Co-op | 2 | yes | no | both players are out |
| Battle | 2 | none | **yes** | one player is left |

In Battle, player two starts at the top of the arena facing down, both players' shots hurt each
other, and asteroids and power-ups keep arriving as shared hazards and prizes.

## Controls

| | Move | Fire |
|---|---|---|
| Player 1 | `W` `A` `S` `D` | `SHIFT` |
| Player 2 | arrow keys | `,` |

Single player accepts either WASD or the arrow keys, and fires with `SHIFT` or `SPACE`.
`ESCAPE` pauses, `F11` toggles fullscreen. Menus take the arrows or `W`/`S`, `Enter` to choose.

## Power-ups

Dropped by defeated enemies: tri-shot, mega laser, shield, health refill, speed boost, extra life.
The timed ones show a countdown in the HUD.

## Enemies

Scouts, fighters and cruisers arrive in waves that get tougher. Every fourth wave brings a boss that
changes attack pattern as you wear it down — a wide spread, then a fan that sweeps across the arena,
then fast bursts aimed straight at you.

<p align="center">
  <img src="docs/screenshots/boss.png" alt="Boss fight, with every power-up" width="620">
</p>

## Layout

```
src/main/java/com/hashimjacobs/spacecase/
  Main.java  Launcher.java  GameConfig.java
  scene/   SceneRouter and every screen (start, multiplayer, settings, help,
           pause overlay, game over) plus the menu widgets and navigator
  mode/    GameMode + ModeRules — what differs between the three modes, as data
  engine/  GameLoop, FixedTimestep, World, CollisionSystem, Renderer, Hud,
           InputState, QuadTree, SpawnDirector, ShipController, EnemyWeapons
  entity/  Entity, PlayerShip, EnemyShip, Asteroid, Bullet, PowerUp,
           Facing, BossPhase
  asset/   Assets + the Sprite / SoundFx / MusicTrack / Explosion enums, SoundBank
  prefs/   Settings and HighScores, persisted via java.util.prefs
tools/     GenerateAssets.java — regenerates the art and audio
src/test/java/...   JUnit 5
```

Four invariants worth knowing before changing the engine, each of which was a bug once. They are
spelled out in [CONTRIBUTING.md](CONTRIBUTING.md); the short version:

- **`World` is the only place entities are added or removed** — collisions only mark, `sweep()`
  removes.
- **Sprites and sounds are enums, not string keys** — a missing asset is a compile error.
- **Simulation speed must not depend on the display** — the loop steps at a fixed 1/60 s.
- **Assets load from the classpath, not the working directory** — so the jar runs from anywhere.

## Soundtrack

Six original instrumental tracks. Which one plays is chosen by *cue* rather than fixed per screen —
gameplay and battle each rotate between two tracks, so replaying a mode does not always sound the
same, and a boss arriving swaps the music until it dies.

| Cue | When |
|---|---|
| `MENU` | Start screen, submenus, game over |
| `GAMEPLAY` | Single player and co-op |
| `BATTLE` | Battle mode |
| `BOSS` | While a boss is on screen |

Re-pointing a cue at different tracks is a one-line edit in `asset/MusicCue.java`.

## Assets

Every bundled file's origin is recorded in [ASSETS.md](ASSETS.md), and a test fails the build if that
drifts. The music is composed by the author; the art is either hand-drawn by the author or generated
by `tools/GenerateAssets.java`, which is committed and deterministic — `java tools/GenerateAssets.java`
reproduces it byte for byte, and CI checks that it still does.

No fonts are bundled; menu headings use whichever display face the host already has.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md). Bug reports and small fixes are welcome.

## Licence

[MIT](LICENSE) — code and assets alike.

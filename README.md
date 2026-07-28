# Space Case

A 2D space shooter: fly, dodge asteroids, shoot up enemy ships, fight bosses — or turn on each
other in Battle mode. Originally written as *Space Wars* to get comfortable before building a game
engine from scratch.

## Running it

    brew install maven      # macOS; any JDK 26 + Maven works
    mvn javafx:run

JavaFX stopped shipping inside the JDK in Java 11, so `pom.xml` pulls it from Maven Central
(including the platform-specific natives) and puts it on the module path. `javafx.version` tracks
the JDK major version — JavaFX 26 needs a JDK 26 runtime.

Other useful targets:

    mvn test        # unit tests (no display or audio device needed)
    mvn package     # builds target/space-case-2.0.jar

All assets load through the classloader, so the game runs from any working directory.

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
`ESCAPE` opens the pause menu.

## Power-ups

Tri-shot, mega laser, shield, health refill, speed boost, and an extra life. The timed ones show a
countdown in the HUD.

## Layout

```
src/main/java/com/hashimjacobs/spacecase/
  Main.java  Launcher.java  GameConfig.java
  scene/   SceneRouter and every screen (start, multiplayer, settings, help,
           pause overlay, game over) plus the menu widgets
  mode/    GameMode + ModeRules — what differs between the three modes, as data
  engine/  GameLoop, World, CollisionSystem, Renderer, Hud, InputState,
           QuadTree, SpawnDirector, ShipController
  entity/  Entity, PlayerShip, EnemyShip, Asteroid, Bullet, PowerUp, Facing
  asset/   Assets + the Sprite / SoundFx / MusicTrack / Explosion enums, SoundBank
  prefs/   Settings and HighScores, persisted via java.util.prefs
src/main/resources/   sprites/  sounds/  fonts/
src/test/java/...     JUnit 5
```

Two invariants worth knowing before changing the engine:

- **`World` is the only place entities are added or removed.** Collision handling never removes
  anything; it calls `kill()`, and `World.sweep()` clears the dead once per frame after all handling
  has finished. Removing an entity mid-iteration is what used to throw
  `ConcurrentModificationException` every frame.
- **Sprites and sounds are enums, not string keys.** A missing asset is a compile error rather than
  a null that reaches `drawImage` and takes down the render thread.

Settings and high scores live in the platform preference store, so they survive a reinstall of the
game but are per-user.

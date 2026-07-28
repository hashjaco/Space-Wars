# Asset provenance

Every file under `src/main/resources` is listed here with where it came from. The MIT licence in
`LICENSE` covers all of it, which is only honest if nothing third-party is mixed in — hence this
table.

Two origins appear below:

- **Generated** — produced by [`tools/GenerateAssets.java`](tools/GenerateAssets.java). Re-run
  `java tools/GenerateAssets.java` to reproduce any of them byte for byte; every random draw uses a
  fixed seed. Because the generator is in the repository, these assets are original work with a
  reproducible derivation rather than files of uncertain origin.
- **Original** — hand-made by Hashim Jacobs for this game.

## Sprites

| File | Origin | Notes |
|---|---|---|
| `daShootaStraight.png`, `daShootaStraight2.png` | Original | Player 1 and player 2 ships |
| `shootaLeft1.png`, `shootaLeft2.png` | Original | Banking left |
| `daShootasRight.png`, `daShootasRight2.png` | Original | Banking right |
| `daShootaStraightDamage.png`, `shootaLeftDamage.png`, `daShootasRightDamage.png` | Original | Scorched frames shown briefly after a hit |
| `PlayProjectile.png`, `EnemyProjectile1.png`, `MegaLaser.png` | Original | Projectiles |
| `triBulletL.png`, `triBulletU.png`, `triBulletR.png` | Original | Tri-shot projectiles |
| `explosion-small/1..25.png` | Original | 25-frame explosion, used for asteroids |
| `explosion-large/1..49.png` | Original | 49-frame explosion, used for ships |
| `enemy-scout.png`, `enemy-fighter.png`, `enemy-cruiser.png` | Generated | Three enemy archetypes |
| `boss.png` | Generated | Capital ship with turret pods |
| `asteroid-small.png`, `asteroid-big.png`, `asteroid-huge.png` | Generated | Procedural cratered rock |
| `background-far.png`, `background-mid.png`, `background-near.png` | Generated | Parallax starfield; the far layer carries the nebula |
| `pickup-speed.png`, `pickup-health.png`, `pickup-shield.png` | Generated | Pickup icons |
| `pickup-tri-shot.png`, `pickup-mega-laser.png`, `pickup-extra-life.png` | Generated | Pickup icons |

## Music

Composed by Hashim Jacobs using [Suno](https://suno.com), and tagged as such in each file's
metadata. All instrumental.

Which track plays when is decided by `asset/MusicCue`, not by the file names — a cue holds several
suitable tracks and picks one per round so replaying a mode does not always sound the same.

All six are encoded at 128 kbps to keep the download reasonable; the soundtrack would otherwise be
most of it. The higher-bitrate exports are in git history.

| File | Origin | Cue |
|---|---|---|
| `arcade-womps.mp3` | Composed by Hashim Jacobs (Suno) | `MENU` |
| `pixel-womp-run.mp3` | Composed by Hashim Jacobs (Suno) | `GAMEPLAY` |
| `bassline-riot-remastered.mp3` | Composed by Hashim Jacobs (Suno) | `GAMEPLAY` |
| `grime-quest.mp3` | Composed by Hashim Jacobs (Suno) | `BATTLE` |
| `bassline-riot-remastered-variant.mp3` | Composed by Hashim Jacobs (Suno) | `BATTLE` |
| `grime-quest-remix.mp3` | Composed by Hashim Jacobs (Suno) | `BOSS` |

> **Before publishing, confirm the Suno plan these were made under grants the right to license them
> onward.** Suno's paid tiers generally assign ownership of generations to the creator while the free
> tier is non-commercial only; MIT is a commercial-use grant, so the distinction matters. This is the
> one asset row that rests on an account term rather than on something checkable from the file.

## Sound effects

| File | Origin | Notes |
|---|---|---|
| `laser.wav` | Generated | Downward pitch sweep |
| `explosion.wav` | Generated | Filtered noise burst with a low rumble |
| `collision.wav` | Generated | Short thud |
| `game-over.wav` | Generated | Descending four-note arpeggio |

## Fonts

None are bundled. Menu headings ask for the first available of `Impact`, `Haettenschweiler`,
`Arial Black`, `Franklin Gothic Heavy` or `DejaVu Sans Condensed`, falling back to a generic serif —
see `Assets.loadDisplayFont`. Referencing a font the host already has is not redistribution, which
bundling a font file would be.

## What was removed, and why

The project previously carried third-party art and audio. None of it could be licensed onward, so it
was replaced with the generated equivalents above and deleted. Recorded here so the change is not
mistaken for vandalism, and so nobody restores these files from history:

| Removed | Reason |
|---|---|
| `Katdrop-Call-The-Cops.wav` | Commercial music recording |
| `ZHU-Nero-Dreams(Tank Trim).wav` | Commercial music recording |
| `main-theme.mp3`, `battle-theme.mp3` | Placeholder chiptunes, superseded by the composed soundtrack above |
| `bassline-riot.mp3`, `bassline-riot-remix.mp3` | Earlier mixes, superseded by the two remastered takes |
| `TITANIC-FLUTE-FAIL-…​.wav` | Ripped from a YouTube sound-effects compilation. Also 172 seconds long, where a game-over sting was wanted |
| `redbull.png` | Depicts a Red Bull can — active trademark |
| `invader-animated-red.gif` | Space Invaders sprite — Taito intellectual property |
| `asteroid.png` | Carried a visible "OpenGameArt.Org" watermark, i.e. a preview download rather than a licensed asset |
| `aNuttaAsteroid.png` | Same source as above |
| `healthPU.png`, `shield.png`, `extraLife.gif` | Unattributed stock icons |
| `enemyShip2.png`, `enemyShip3.png`, `bossShip1.jpg` | Found 3D renders of unknown origin |
| `Lugosi.ttf` | Font licence could not be established; most freely downloadable faces are not licensed for redistribution |
| `spaceBackground.gif` | Superseded by the parallax layers |
| `explosionSheet.png`, `explosion17.png`, `daShootasShips.png`, `daShootasSlight*.png`, `shootaLeft.png` | Unreferenced leftovers |

If you hold a licence for any of the above and would rather use it, drop the file back into
`src/main/resources/sprites` (or `sounds`) and point the matching constant in
`asset/Sprite.java`, `asset/SoundFx.java` or `asset/MusicTrack.java` at it, then record it here.

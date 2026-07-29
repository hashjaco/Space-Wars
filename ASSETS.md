# Asset provenance

Every file under `src/main/resources` is listed here with where it came from. The MIT licence in
`LICENSE` covers all of it, which is only honest if nothing third-party is mixed in — hence this
table.

Two origins appear below:

- **Generated** — produced by [`tools/GenerateAssets.java`](tools/GenerateAssets.java). Re-run
  `java tools/GenerateAssets.java` to reproduce any of them byte for byte; every random draw uses a
  fixed seed. Because the generator is in the repository, these assets are original work with a
  reproducible derivation rather than files of uncertain origin.
- **Original** — hand-made by Hashim Jacobs for this game. The player ships arrive as one
  spritesheet in `tools/art/spritesheet.png`, which the generator cuts into individual frames; the
  sheet itself is original work and is a generator input rather than a bundled asset.

## Sprites

| File | Origin | Notes |
|---|---|---|
| `player/p1-{bank-left,left,straight,right,bank-right}.png` | Original | Player 1's five bank poses, cut from `tools/art/spritesheet.png` |
| `player/p2-{bank-left,left,straight,right,bank-right}.png` | Original | Player 2's five bank poses, from the same sheet |
| `player/p{1,2}-*-hit.png` | Original | Damage frames: each pose blended toward the hostile glow by the generator |
| `PlayProjectile.png`, `EnemyProjectile1.png`, `MegaLaser.png` | Original | Projectiles |
| `triBulletL.png`, `triBulletU.png`, `triBulletR.png` | Original | Tri-shot projectiles |
| `explosion-small/1..25.png` | Original | 25-frame explosion, used for asteroids |
| `explosion-large/1..49.png` | Original | 49-frame explosion, used for ships |
| `level-{1..8}/enemy-{scout,fighter,cruiser}.png` | Generated | Three archetypes per level, one faction each; levels 2 and 3 are the organic builds |
| `boss-sentinel/1..8.png` | Generated | Level 1 boss; 8-frame idle animation |
| `boss-hive-matriarch/1..8.png` | Generated | Level 2 boss; wide and low, membranous wings |
| `boss-bloom-colossus/1..8.png` | Generated | Level 3 boss; tall, wings opening like petals |
| `boss-scrap-hive/1..8.png` | Generated | Level 4 boss; carrier that vents escorts |
| `boss-foundry-warden/1..8.png` | Generated | Level 5 boss; slab-sided, widest engine bank |
| `boss-void-weaver/1..8.png` | Generated | Level 6 boss; long-limbed and narrow |
| `boss-core-tyrant/1..8.png` | Generated | Level 7 boss; heaviest hull, most turrets |
| `boss-exodus-dreadnought/1..8.png` | Generated | Level 8 boss; longest hull, most pods |
| `asteroid-small.png`, `asteroid-big.png`, `asteroid-huge.png` | Generated | Procedural cratered rock |
| `level-1/{far,mid,near}.png` | Generated | Orbital Approach; starfield with the target world hanging in it |
| `level-2/{far,mid,near}.png` | Generated | Verdant Airspace; banded sky with cloud decks |
| `level-3/{far,mid,near}.png` | Generated | Canopy Descent; jungle floor and treetops |
| `level-4/{far,mid,near}.png` | Generated | Rust Canyon; oxide rock, barer and bigger-boned |
| `level-5/{far,mid,near}.png` | Generated | Undercity; tunnel walls, light strips and dust |
| `level-6/{far,mid,near}.png` | Generated | Void Rift; deep violet and sparse |
| `level-7/{far,mid,near}.png` | Generated | Star Core; red and orange embers |
| `level-8/{far,mid,near}.png` | Generated | Escape Vector; the world falling away astern |
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
| `level-clear.wav` | Generated | Ascending arpeggio over a held top note |

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

Two generated files were also retired, for reasons of scope rather than licence: `boss.png`, replaced
by the animated per-level bosses, and `background-far.png` / `background-mid.png` /
`background-near.png`, replaced by the per-level backdrops.

The nine loose `daShoota*` / `shoota*` player frames were replaced by the twenty in `player/`, cut
from `tools/art/spritesheet.png`; the three shared `enemy-*.png` were replaced by the twenty-four
per-level ones; and `boss-glacier-warden/` became `boss-hive-matriarch/` when level 2 stopped being an
ice field. Same authorship in every case — only the level lineup changed.

## Unresolved provenance

These arrived in the working tree without a recorded source and **nothing in the code references
them**, so they ship as dead weight in the jar. They are listed here so the manifest is complete and
the question is not lost, not because their origin is established. Resolve before publishing: either
record where each came from and confirm its licence permits MIT redistribution, or delete them.

| File | Note |
|---|---|
| `sounds/lordsonny-plasma-gun-fire-162136.mp3` | Filename pattern suggests a sound-library download |
| `sounds/dragon-studio-massive-explosion-2-397983.mp3` | Same |
| `sounds/machine-gun-burst.mp3`, `sounds/machine-gun-single-burst.mov` | Origin unrecorded; `.mov` is also not a format the game loads |
| `sounds/cyber-laser.mov` | Origin unrecorded; not a format the game loads |
| `sounds/electricity.mp3`, `sounds/firing-pulse.mp3` | Origin unrecorded |
| `sprites/gorkhs-vessel.png` | Origin unrecorded; 2 MB and unreferenced |

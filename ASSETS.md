# Asset provenance

Every file under `src/main/resources` is listed here with where it came from. The MIT licence in
`LICENSE` covers the art and audio, which is only honest if nothing third-party is mixed in — hence
this table. One third-party file is bundled, and only one: the controller mapping database, which is
permissively licensed and credited under "Controller mappings" below.

Three origins appear below:

- **Generated** — produced by [`tools/GenerateAssets.java`](tools/GenerateAssets.java). Re-run
  `java tools/GenerateAssets.java` to reproduce any of them byte for byte; every random draw uses a
  fixed seed. Because the generator is in the repository, these assets are original work with a
  reproducible derivation rather than files of uncertain origin.
- **Original** — hand-made by Hashim Jacobs for this game. The player ships arrive as one
  spritesheet in `tools/art/spritesheet.png`, which the generator cuts into individual frames; the
  sheet itself is original work and is a generator input rather than a bundled asset.
- **Third party** — not ours, redistributed under its own licence, which is reproduced in the file
  itself. Exactly one file, listed under "Controller mappings".

## Sprites

| File | Origin | Notes |
|---|---|---|
| `player/p1-{bank-left,left,straight,right,bank-right}.png` | Original | Player 1's five bank poses, cut from `tools/art/spritesheet.png` |
| `player/p2-{bank-left,left,straight,right,bank-right}.png` | Original | Player 2's five bank poses, from the same sheet |
| `player/p{1,2}-*-hit.png` | Original | Damage frames: each pose blended toward the hostile glow by the generator |
| `player/{azure,amber,violet,chrome}-*.png` | Original | Garage paint jobs: player one's cut frames, hue-rotated by the generator. Outlines are unsaturated, so they survive the rotation and the shading is preserved |
| `player/{azure,amber,violet,chrome}-*-hit.png` | Original | Damage frames for the paint jobs, same blend as the stock hulls |
| `player/kit-{fins,armour,lance}-*.png` | Original | Garage body kits: transparent decals drawn over any hull, placed from each pose's alpha bounding box so they track the bank |
| `player/*-side.png`, `player/*-hit-side.png` | Original | Every hull, paint job and kit decal above, cut again for the one level flown side-on. A quarter turn of the frame beside it, applied by the generator rather than at draw time, so the art and the collision box are the same shape — the same bargain `level-9/enemy-*.png` makes. Their `Sprite` dimensions are transposed to match |
| `insignia/{chevrons,rods,bars,stars}-{1..4}.png` | Generated | Rank badges for the debrief. Four tiers by four mark counts, which is the grid `prefs.Rank` folds all twenty-six ranks onto |
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
| `pickup-rocket.png` | Generated | Pickup icon |
| `level-9/{far,mid,near}.png` | Generated | Dust Reach. The one level flown side-on, so its sky tiles horizontally rather than vertically — do not reuse these on a top-down level or the wrap shows a seam |
| `level-9/enemy-{scout,fighter,cruiser}.png` | Generated | Dust Reach's hostiles, cut pointing left because the level runs that way. Their `Sprite` dimensions are transposed to match |
| `level-10/{far,mid,near}.png` | Generated | Hollow Womb; a cavern in shades of meat |
| `level-10/enemy-{scout,fighter,cruiser}.png` | Generated | Hollow Womb's hostiles |
| `boss-hydra/1..8.png` | Generated | Level 10's torso. Its three necks and heads are drawn live from their positions, not baked into these frames — only the gut-sac breath is animated here |
| `boss-hydra-head/1..8.png` | Generated | One hydra head, jaw working on the breath. Three are on screen at once, each a separate target |
| `boss-dune-leviathan/1..8.png` | Generated | Level 9's boss: a burrowing maw seen side-on, mandibles flowering open |
| `worm-segment.png` | Generated | One armoured ring of the Leviathan's body; the renderer trails six behind the maw |
| `acid-ball.png` | Generated | A hydra head's acid. Its own art rather than the reused orange projectile, because the renderer tints halos per sprite and green-on-orange reads as a fault |

## Music

Composed by Hashim Jacobs using [Suno](https://suno.com), and tagged as such in each file's
metadata. All instrumental.

Which track plays when is decided by `asset/MusicCue`, not by the file names — a cue holds several
suitable tracks and picks one per round so replaying a mode does not always sound the same.

All ten are encoded at 128 kbps to keep the download reasonable; the soundtrack would otherwise be
most of it. The higher-bitrate exports are in git history.

| File | Origin | Cue |
|---|---|---|
| `arcade-womps.mp3` | Hashim Jacobs (Suno) | `MENU` |
| `pixel-womp-run.mp3` | Hashim Jacobs (Suno) | `GAMEPLAY` |
| `bassline-riot-remastered.mp3` | Hashim Jacobs (Suno) | `GAMEPLAY` |
| `starlight-circuit.mp3` | Hashim Jacobs (Suno) | `GAMEPLAY` |
| `grime-quest.mp3` | Hashim Jacobs (Suno) | `BATTLE` |
| `bassline-riot-remastered-variant.mp3` | Hashim Jacobs (Suno) | `BATTLE` |
| `grime-quest-remix.mp3` | Hashim Jacobs (Suno) | `BOSS` |
| `garage-music.mp3` | Hashim Jacobs (Suno) | `GARAGE` |
| `death-metal.mp3` | Hashim Jacobs | `HYDRA_BOSS`. Transcoded at 128 kbps from a 166 s stereo wav |
| `death-punk.mp3` | Hashim Jacobs | `LEVIATHAN_BOSS`. Transcoded at 128 kbps from an 81 s stereo wav |

## Sound effects

| File | Origin | Notes |
|---|---|---|
| `punchy-laser.wav` | Hashim Jacobs | `LASER` — the player's default weapon, cut to 0.5 s. The authored file ran two seconds but everything after 0.45 s was digital silence, and a voice holds a native media player open through silence exactly as long as through sound. An earlier trim to 0.35 s was reverted as inaudible: that cut landed on the sample's loudest point, since it swells rather than striking. The audible part is untouched — same peak, same mean. Full-length original in git history |
| `explosion.wav` | Generated | Filtered noise burst with a low rumble. `EXPLOSION`, kept for asteroids |
| `spaceship-explosion.wav` | Hashim Jacobs | `SHIP_EXPLOSION`, ship and flagship kills only. Three seconds, still long enough that asteroids keep the short generated burst above. The authored file ran nine, of which the last six were a tail under −20 dB that cost six seconds of open native player per kill; it now fades out from 2.6 s. Body of the sound is unchanged. Full-length original in git history |
| `machine-gun-burst.wav` | Hashim Jacobs | `BOSS_GUN`, the flagship's phase pattern. Decoded to 48 kHz mono PCM from the authored MP3, which is in git history: sound effects play through `javax.sound.sampled`, which is in the JDK and cannot read MP3. Same 1.632 s, same audio |
| `mega-boss-cannon.wav` | Hashim Jacobs | `BOSS_ROCKET`, the flagship's rocket salvo |
| `collision.wav` | Generated | Short thud |
| `low-health.wav` | Generated | Two-note square warble, retriggered while a player is nearly dead |
| `game-over.wav` | Generated | Descending four-note arpeggio |
| `level-clear.wav` | Generated | Ascending arpeggio over a held top note |
| `laser.wav` | Generated | Downward pitch sweep. The default weapon before `punchy-laser.wav`; still produced by the generator and kept as the licence-clean fallback |

## Controller mappings

| File | Origin | Notes |
|---|---|---|
| `gamecontrollerdb.txt` | Third party | [SDL_GameControllerDB](https://github.com/mdqinc/SDL_GameControllerDB), pinned at commit `42f28e22d20761e7004e8db91c4ad86402fdf600` (2026-08-12). zlib licence, the same terms as SDL itself: redistribution is permitted and the notice is retained in the file's own header comments, so do not strip them when updating |

Bundled because it is load-bearing, not cosmetic. Jamepad loads this file from the classpath as
`/gamecontrollerdb.txt`; without it SDL falls back to the 156 mappings compiled into it, and any pad
outside that set is never opened — it reports as disconnected and the game silently ignores it. That
is how an 8BitDo Ultimate 2C came to look like broken controller support. `GamepadDatabaseTest`
fails the build if the file goes missing again.

Refresh it by re-downloading from upstream and updating the commit above. A player whose pad is
newer than the pinned copy can point `SDL_GAMECONTROLLERCONFIG_FILE` at their own file instead of
waiting for a release.

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

Removed for size rather than licensing, and recoverable from git history if any of them is wanted
back — 93 MB between them, none referenced by any code:

| Removed | Reason |
|---|---|
| `garage-music.wav`, `death-metal.wav`, `death-punk.wav` | The uncompressed sources for the three MP3s the game loads. 89 MB between them, and the MP3s are what ship |
| `gorkhs-vessel.png` | Unreferenced, 2 MB |
| `default-round.wav` | Superseded by `punchy-laser.wav` as the default weapon |
| `mega-boss-cannon-2.wav` | An alternate take of `mega-boss-cannon.wav` |
| `lordsonny-plasma-gun-fire-162136.mp3`, `electricity.mp3`, `firing-pulse.mp3`, `explosion.mp3` | Unreferenced |
| `machine-gun-single-burst.mov`, `cyber-laser.mov` | Unreferenced, and `.mov` is not a format the game can load |

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

## Audio ownership

Every audio file in `src/main/resources/sounds` belongs to Hashim Jacobs, who holds the rights to
use and license all of it. That covers the generated stings, the composed soundtrack, and the
sourced effects alike. There is no outstanding licence question on any sound in this repository.


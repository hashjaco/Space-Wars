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
| `player/{interceptor,gunship,twin-boom}-{militia,corsair,azure,amber,violet,chrome}-*.png` | Generated | Garage chassis: player one's cut frames rescaled into three silhouettes by the generator, then hue-rotated into all six paints. Generated rather than Original because the outline is the generator's arithmetic and not the sheet's — the colour is all the sheet still contributes |
| `player/kit-{fins,armour,lance,canards,scoop,mast,rack}-*.png` | Original | Garage body kits: transparent decals drawn over any hull, placed from each pose's alpha bounding box so they track the bank |
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
| `pickup-scythe.png`, `pickup-flak.png`, `pickup-nova.png` | Generated | Pickup icons for the three weapons added with the chassis |
| `boss-ember.png`, `boss-shard.png`, `boss-bolt.png`, `boss-void.png` | Generated | What the flagships fire, one round per galaxy from Ashfall onward. Verdance keeps the hand-drawn `EnemyProjectile1.png`, so the first fight anybody meets is the one it always was |
| `scythe-blade.png`, `flak-pellet.png`, `nova-shell.png` | Generated | The rounds those three fire. The only generated projectiles; the rest are the author's own |
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

Nine of the ten are encoded at about 128 kbps to keep the download reasonable; the soundtrack would
otherwise be most of it. `starlight-circuit.mp3` is the exception at 200 kbps -- it never went
through the same encode pass, which is also why it is the loudest of the ten and half again the
size of its neighbours.

Only three have a higher-bitrate export in git history: `death-metal.wav`, `death-punk.wav` and
`garage-music.wav`. The other seven were committed as the shipped MP3 and nothing else, so for
those the 128 kbps file is the only copy that exists and a re-encode would be generation loss
rather than a remaster.

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

### Galaxy 2 — Ashfall (levels 11–20)

Fire, ash and industry. One palette across the galaxy, worn by its grunts and its flagships alike.

| File | Origin | Notes |
|---|---|---|
| Level directories `level-11`, `level-12`, `level-13`, `level-14`, `level-15`, `level-16`, `level-17`, `level-18`, `level-19`, `level-20` — each holding `{far,mid,near}.png` and `enemy-{scout,fighter,cruiser}.png` | Generated | Seeds 4300–4390, ten apart. `level-11` is a new `BELT` backdrop: a starfield with lit rock tumbling through it, because a tinted starfield never reads as a belt. `level-18` reuses the planet disc lit hot, so the star is actually in frame. `level-17` is flown side-on, so its sky tiles horizontally and its hulls are cut pointing left |
| Boss frames `1..8.png` in `boss-cinder-warden`, `boss-slag-baron`, `boss-forge-overseer`, `boss-pyre-sovereign`, `boss-sunward-lance`, `boss-corona-herald` | Generated | Six warships from three hulls and three wings, paired up. One hull for all six was tried first and five of them came out the same ship — at two hundred pixels the eye reads silhouette and colour, not turret counts. `boss-sunward-lance` is turned once here rather than rotated each frame, so its box matches its picture |
| Boss frames `1..8.png` in `boss-ash-revenant`, `boss-vent-crawler`, `boss-ember-titan` | Generated | Three creatures from one parameterised method. Written beside the hydra's rather than by generalising it: reworking that one risked shifting its committed frames, and the byte-identical check in CI is what keeps this art from drifting |
| Boss frames `1..8.png` in `boss-forge-rig`, `boss-forge-rig-arm`, `boss-forge-rig-cockpit` | Generated | Vaunt's rig. Three separate sets because they are three separate targets: the arms have to stop being drawn once they are shot off, which they could not if they lived in the body's frames |

### Galaxy 3 — Cryonis (levels 21–30)

Ice and water. Cold hulls and a pale cyan accent, and the only galaxy flown side-on twice.

| File | Origin | Notes |
|---|---|---|
| Level directories `level-21`, `level-22`, `level-23`, `level-24`, `level-25`, `level-26`, `level-27`, `level-28`, `level-29`, `level-30` — each holding `{far,mid,near}.png` and `enemy-{scout,fighter,cruiser}.png` | Generated | Seeds 4400–4490, ten apart. `level-21` and `level-29` are both flown side-on, so their skies tile horizontally and their hulls are cut pointing left — the first galaxy with two such legs. Both use `BELT`, which is safe sideways because it draws through `wrapped()` and that shifts by the canvas width instead of its height. `level-24` is the water level: the same `ATMOSPHERE` recipe as a sky, tinted cold and dark so its cloud decks read as light shafts through the shelf |
| Boss frames `1..8.png` in `boss-shard-cutter`, `boss-frost-harrier`, `boss-glacier-breaker`, `boss-cryo-marshal`, `boss-hail-bastion`, `boss-shatter-prow` | Generated | Six warships from three hulls and three fins, paired up, on Ashfall's 1.5× canvases. Narrow and edged where Ashfall's are wide and blunt: those were built to work, these to cut through something. Three and four engines against Ashfall's five and six. `boss-shard-cutter` and `boss-shatter-prow` guard the two side-on legs and are turned once here rather than rotated each frame, so their boxes match their pictures |
| Boss frames `1..8.png` in `boss-ice-wraith`, `boss-trench-horror`, `boss-geyser-maw` | Generated | Three creatures from the same parameterised method Ashfall's use. All three sit on top-down levels because `CreatureProfile` has no sideways flag — levels 21 and 29 field warships for that reason rather than by preference |
| Boss frames `1..8.png` in `boss-frozen-empress`, `boss-frozen-empress-head` | Generated | The Frozen Empress, four-headed. Two new methods written beside the hydra's rather than by generalising it, for the reason the creatures' note gives. Her socket positions are computed from the same span the engine spreads necks across, so a torso drawn for four heads cannot disagree with the arc four necks are flown on. Not a hydra recolour: no legs, shards where the hydra has ribs, and a faceted skull, so the two multi-headed bosses read as different things |

### Galaxy 4 — Tempest (levels 31–40)

Storm and gas giant, and the galaxy with no floor: six of its ten levels are inside cloud, exactly one has ground under it, and the finale comes down out of the deck.

| File | Origin | Notes |
|---|---|---|
| Level directories `level-31`, `level-32`, `level-33`, `level-34`, `level-35`, `level-36`, `level-37`, `level-38`, `level-39`, `level-40` — each holding `{far,mid,near}.png` and `enemy-{scout,fighter,cruiser}.png` | Generated | Seeds 4500–4590, ten apart. Six of the ten run one recipe, so these rows are separated on the only axis that recipe reads — `tintB`, from saturated mid blue at 31 through near-white at 33 and near-black at 37 to indigo at 40, with the deck count from 0.45 to 1.8 under it. The first pass spread them on `blobs` instead, which `sky()` never reads, and the six rendered as one sky. `level-39` is the side-on leg, so its sky tiles horizontally and its hulls are cut pointing left; it uses `STARFIELD`, which is safe sideways for the reason levels 9 and 17 are — stars look the same lying on their side |
| Boss frames `1..8.png` in `boss-squall-warden`, `boss-eyewall-lance`, `boss-ring-reaver`, `boss-downdraft-prow`, `boss-arc-lance` | Generated | Five warships from three hulls and three vanes, on the same 1.5× canvases. Two and three engines against Cryonis's three and four, continuing toward the thrusterless navy the last galaxy wants. Aspects run 0.75 to 2.06, wider than Ashfall's band and deliberately so: the first pass put three of the five between 1.3 and 1.9 on two hulls and they read as one wide ship, which is the mistake Ashfall recorded about its own flagships. `boss-arc-lance` guards the side-on leg and is turned once here rather than rotated each frame, so its box matches its picture |
| Boss frames `1..8.png` in `boss-thunder-brood`, `boss-static-crawler`, `boss-magnetar-maw` | Generated | Three creatures from the same parameterised method Ashfall's and Cryonis's use. All three sit on top-down levels, because `CreatureProfile` still has no sideways flag |
| Boss frames `1..8.png` in `boss-storm-rig`, `boss-storm-rig-cockpit`, `boss-storm-rig-arm` | Generated | Vaunt again, in a bigger one, and the payoff for having built him in Ashfall. Not new drawings: the three methods that draw the Forge-Rig now take their canvas and their plate colours as arguments, and at the literals Ashfall always passed they produce the frames already committed — so this rig exists without a pixel of that galaxy's art moving. Deliberately the same machine, same silhouette and the same man behind the same glass, with the furnace and cockpit ring left Ashfall orange; only the armour is repainted. Three directories because they are three separate targets, as the Forge-Rig's are |
| Boss frames `1..8.png` in `boss-storm-serpent` | Generated | The finale, striking down out of the cloud deck. A new method beside the Dune Leviathan's rather than that one rotated and recoloured — the Leviathan's frames open leftward, and a galaxy-four finale that is visibly galaxy one's boss turned sideways reads as reused. Same species, not the same animal: three long petals against four short ones, a broad skull the Leviathan has no equivalent of, arcs jumping the gaps between its plates, and a throat lit from inside instead of flat black |
| `storm-segment.png` | Generated | One ring of the Storm Serpent's body. Its own file rather than `worm-segment.png` because that one is not rotationally symmetric — it carries its bristles down one side, which is right for an animal crossing the screen and visibly wrong for one striking down it. This ring has no up |

### Galaxy 5 — Null (levels 41–50)

Void, gravity and the thing at the bottom of it. The galaxy with no sky and no ground: not one `ATMOSPHERE` or `SURFACE` backdrop, nothing to fly over and nothing overhead, and the only two enclosures are dead structures.

| File | Origin | Notes |
|---|---|---|
| Level directories `level-41`, `level-42`, `level-43`, `level-44`, `level-45`, `level-46`, `level-47`, `level-48`, `level-49`, `level-50` — each holding `{far,mid,near}.png` and `enemy-{scout,fighter,cruiser}.png` | Generated | Seeds 4600–4690, ten apart. Five recipes over ten levels, the widest spread any galaxy has used, and the direct answer to Tempest putting six levels on one. Four of the ten run the new `EVENT_HORIZON` recipe, and they are not four skies with the same object in them: the disc grows 0.10 → 0.20 → 0.26 → 0.32 of the canvas across levels 44, 47, 48 and 50, so the galaxy reads as one continuous fall toward the hole. 0.32 is near the ceiling rather than a round number — the accretion ring reaches a further 35%, so the whole feature is about 746 across an 864 canvas, and anything taller than the canvas cannot tile without overlapping itself. `level-45` is the side-on leg, so its sky tiles horizontally and its hulls are cut pointing left; it uses `BELT`, safe sideways for the reason levels 21 and 29 are |
| Boss frames `1..8.png` in `boss-bonepicker`, `boss-lensbreaker`, `boss-tidewrack`, `boss-frame-drag`, `boss-photon-halo`, `boss-gullet` | Generated | Six warships from three hulls and three vanes, paired six ways with no pair repeated, on the same 1.5× canvases. **Zero engines on all six** — Ashfall ran five and six, Cryonis three and four, Tempest two and three, and each of those blocks called the next a step toward the thrusterless navy this galaxy wanted: a fleet with nothing to push against, in the galaxy about gravity. Aspects run 0.82 to 2.10. The first pass had both Waist-hulled classes at 1.17 and 1.51 and they read as one ship on the boss sheet, which is the mistake Ashfall recorded, Cryonis recorded again and Tempest recorded a third time; they are now 0.95 and 1.87. `boss-tidewrack` guards the side-on leg and is turned once here rather than rotated each frame, so its box matches its picture |
| Boss frames `1..8.png` in `boss-hulk-choir`, `boss-shroudmaw`, `boss-shellborn` | Generated | Three creatures from the same parameterised method the last three galaxies use. All three sit on top-down levels, because `CreatureProfile` still has no sideways flag. `boss-shroudmaw` is the first creature in the game with no legs at all, which that record already supported without a branch — `legs` at zero simply draws none. Aspects 0.97, 1.94 and 1.25: the first pass had them at 1.09, 1.28 and 1.59 and all three read as the same dark rounded mass, since at two hundred pixels a leg is a stub and the outline is what the eye gets first |
| Boss frames `1..8.png` in `boss-aeon`, `boss-aeon-eye` | Generated | Aeon, the Hollow Star: the last fight in the campaign, four-eyed. A third pair of methods written beside the hydra's and the Empress's rather than by generalising either, for the reason their own rows give. The construction is the inverse of every other boss in the game — all of those are a lit body with a core burning inside it, and this one is a hole with its light entirely outside it, which is why it could not be a recolour of anything. Its four socket positions are computed from the same span the engine spreads necks across, so a body drawn for four eyes cannot disagree with the arc four necks are flown on |

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


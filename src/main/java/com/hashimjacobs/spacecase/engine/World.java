package com.hashimjacobs.spacecase.engine;

import java.util.ArrayList;
import java.util.List;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Explosion;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.Bullet;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.Entity;
import com.hashimjacobs.spacecase.entity.Facing;
import com.hashimjacobs.spacecase.entity.Orientation;
import com.hashimjacobs.spacecase.entity.PlayerShip;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.garage.Loadout;
import com.hashimjacobs.spacecase.garage.Upgrade;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.mode.ModeRules;

/**
 * Everything currently in the arena, and the only place entities are added or removed.
 *
 * Removal happens exclusively in {@link #sweep()}, once per frame, after all collision handling has
 * finished. Collision code only calls {@code kill()}. That ordering is what fixes the
 * ConcurrentModificationException the old engine threw: it removed from the same bullet lists it was
 * iterating, from inside the iteration.
 */
public final class World {

    private final GameMode mode;
    private final List<PlayerShip> players = new ArrayList<>();
    private final List<EnemyShip> enemies = new ArrayList<>();
    private final List<Asteroid> asteroids = new ArrayList<>();
    private final List<Bullet> bullets = new ArrayList<>();
    private final List<PowerUp> powerUps = new ArrayList<>();
    private final List<ActiveExplosion> explosions = new ArrayList<>();

    /**
     * Ships brought in so far, counting from zero, purely to spread their first shots apart.
     *
     * Never reset and never read for anything else, so it does not matter that it keeps climbing
     * across a level; only the gaps between consecutive values do any work.
     */
    private int arrivals;

    /** Which way the current level runs. Re-stamped on every level change; see setOrientation. */
    private Orientation orientation = Orientation.TOP_DOWN;

    /** What shape the current level is. Open space until a level says otherwise. */
    private Terrain terrain = Terrain.NONE;

    private int tick;

    /**
     * How hard the camera is being thrown, and when the throw started.
     *
     * Amplitude decays linearly over {@link #SHAKE_TICKS} rather than being stepped down each
     * update, so the kick is a pure function of the tick -- the same rule the parallax and the
     * garage turntable follow, and for the same reason: draws are refresh-bound and steps are not.
     *
     * Started a full window in the past so a fresh world is already settled without a null case.
     */
    private static final int SHAKE_TICKS = 18;
    private static final double SHAKE_MAX = 14;
    private int shakeStartTick = -SHAKE_TICKS;
    private double shakeAmplitude;

    /**
     * Names shown under the ships when nobody has been to the pilots screen.
     *
     * Four, because an online room seats four. Only as many are read as there are ships, so a solo
     * or two-player game is unchanged by the extra rows.
     */
    private static final List<String> UNNAMED_PILOTS =
            List.of("PILOT 1", "PILOT 2", "PILOT 3", "PILOT 4");

    public World(GameMode mode) {
        this(mode, UNNAMED_PILOTS.subList(0, mode.rules().playerCount()));
    }

    /** @param pilotNames one per player slot, player one first */
    public World(GameMode mode, List<String> pilotNames) {
        this.mode = mode;
        spawnPlayers(pilotNames);
    }

    /**
     * One ship per name, rather than the count on {@link ModeRules}.
     *
     * The names were always the better source and are already a constructor parameter: an online
     * room seats two to four and its size is not known until everyone has joined, where
     * {@code playerCount} is a constant baked into the mode. So the list decides, and the local
     * constructor trims {@link #UNNAMED_PILOTS} to the mode's count -- which leaves solo and couch
     * play exactly as they were, and lets a networked co-op field four without lying about the mode.
     */
    private void spawnPlayers(List<String> pilotNames) {
        for (int number = 1; number <= pilotNames.size(); number++) {
            players.add(new PlayerShip(number, pilotNames.get(number - 1), Facing.UP, 0, 0));
        }
        placeSpawns();
    }

    /**
     * Sets which way this level runs and re-lays the player spawns to match.
     *
     * Called between levels, never during one.
     */
    /**
     * Takes on a level's shape: which way it runs, and what rock is in it.
     *
     * The one call the loop makes at a level boundary. {@link #setOrientation} stays public and
     * separate because several tests set only the axis, and because a level's rock is seeded from
     * its position in the campaign -- which a test that only cares about direction should not have
     * to know about.
     */
    public void enterLevel(Level level) {
        setOrientation(level.orientation());
        terrain = new Terrain(level.template(), level.orientation(), level.ordinal());
    }

    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
        placeSpawns();
    }

    /** The rock in this level, or {@link Terrain#NONE} in the open ones. Never null. */
    public Terrain terrain() {
        return terrain;
    }

    public Orientation orientation() {
        return orientation;
    }

    /**
     * Where each pilot starts, in arena terms rather than screen terms.
     *
     * A hundred and thirty back from the far edge, spread across the lane. Written this way the
     * co-op layout needs no special case: "side by side at a third and two thirds of the breadth"
     * is shoulder to shoulder in a top-down level and stacked one above the other in a side view,
     * which is the correct arrangement in both.
     */
    private void placeSpawns() {
        ModeRules rules = mode.rules();
        boolean headToHead = rules.lastPlayerStanding();
        Facing near = orientation.playerFacing();

        for (PlayerShip player : players) {
            int number = player.playerNumber();
            // Head-to-head alternates ends: odd numbers hold the near line, even numbers the far
            // one, which keeps two-player battle exactly where it has always been and gives four
            // players two a side rather than a queue at one end.
            boolean opposed = headToHead && number % 2 == 0;
            double w = player.width();
            double h = player.height();

            // Evenly spaced across the arena, one lane per ship. n/(count+1) reproduces the old
            // numbers exactly -- one player at a half, two at a third and two thirds -- and keeps
            // going for three and four rather than needing a case each.
            double lane = players.size() == 1 || headToHead
                    ? battleLane(number, players.size())
                    : orientation.arenaBreadth() * number / (players.size() + 1.0);
            double across = lane - orientation.acrossExtent(w, h) / 2;
            // Battle's second seat starts at the far end facing back down the arena.
            double depth = opposed ? 70 : orientation.arenaDepth() - 130;

            player.setSpawn(orientation.atX(depth, across, w, h),
                    orientation.atY(depth, across, w, h),
                    opposed ? near.opposite() : near);
            player.returnToSpawn();
        }
    }

    /**
     * Where a ship lines up when the mode is head to head, or when it is flying alone.
     *
     * Both cases used to be "the middle", and for one ship or two facing off that is still exactly
     * right. Four in a battle need spreading, so each end's ships are spaced across their own half
     * of the breadth -- which leaves a lone ship, and each of a facing pair, on the centre line as
     * before.
     */
    private double battleLane(int number, int count) {
        int perEnd = (count + 1) / 2;
        if (perEnd <= 1) {
            return orientation.arenaBreadth() / 2;
        }
        // Position within this end: 1, 2, 3... among the ships that share it.
        int placeAtEnd = (number + 1) / 2;
        return orientation.arenaBreadth() * placeAtEnd / (perEnd + 1.0);
    }

    /** Moves everything and expires anything that has left the arena. Does not remove. */
    public void update() {
        tickScenery();

        for (PlayerShip player : players) {
            player.tickTimers();
            player.update();
            applyPull(player);
            clampToArena(player);
        }
        for (EnemyShip enemy : enemies) {
            // Beside update() rather than inside it, exactly as the players above are ticked: the
            // set-piece flagships all override update() without calling super, so a timer in there
            // would never run for any of them. See EnemyShip.tickTimers.
            enemy.tickTimers();
            enemy.update();
            // Kept out of the rock, but never hurt by it: an enemy grinding along a wall is scenery,
            // an enemy killing itself on one is the level playing itself. Bosses are exempt because
            // the chamber is opening for them anyway.
            if (!enemy.isBoss()) {
                terrain.pushInside(enemy);
            }
        }
        for (Asteroid asteroid : asteroids) {
            asteroid.update();
        }
        for (Bullet bullet : bullets) {
            bullet.update();
        }
        for (PowerUp powerUp : powerUps) {
            powerUp.update();
            reelIn(powerUp);
        }
        killWhatLeftTheArena();
    }

    /**
     * Advances the clock and the burning wreckage, and nothing else.
     *
     * The between-levels victory lap runs this instead of {@link #update()}: the backdrop keeps
     * scrolling and the explosions keep burning, while nothing is moved or -- crucially --
     * expired, since {@link #killWhatLeftTheArena()} would delete the players as they fly off the top.
     */
    public void tickScenery() {
        tick++;
        // Scrolls the rock and opens the boss chamber. Here rather than in update() so it keeps
        // running through the victory lap, which calls this and nothing else -- the chamber has to
        // stay open while the ships fly out through it.
        terrain.tick(bossPresent());
        for (ActiveExplosion explosion : explosions) {
            explosion.tick();
        }
    }

    /**
     * Ends the fight: destroys everything hostile still on the field and clears every shot in the air,
     * so the victory lap flies through empty sky.
     */
    public void clearBattlefield() {
        for (EnemyShip enemy : enemies) {
            if (!enemy.isAlive()) {
                continue;
            }
            addExplosion(enemy, Explosion.LARGE);
            enemy.kill();
        }
        for (Bullet bullet : bullets) {
            bullet.kill();
        }
        for (Asteroid asteroid : asteroids) {
            asteroid.kill();
        }
        sweep();
    }

    /**
     * Co-op only: a partner who finished the level alone buys back everyone who ran out of lives.
     *
     * Not battle mode, where outlasting the other player is the win condition and reviving them
     * would undo it. Not solo either, and that exclusion is load-bearing rather than tidy: the game
     * loop tests for a cleared level before it tests for a finished round, so a lone player killed
     * on the very tick the flagship dies reaches the victory lap while out of lives. Without the
     * player-count guard that player is handed three free lives instead of a game over.
     */
    public void reviveFallenAllies() {
        ModeRules rules = rules();
        // players.size(), not rules.playerCount(): an online room's size is decided when everyone
        // has joined, and the mode's constant no longer knows it.
        if (rules.lastPlayerStanding() || players.size() < 2) {
            return;
        }
        for (PlayerShip player : players) {
            if (player.isOut()) {
                player.revive();
            }
        }
    }

    private void killWhatLeftTheArena() {
        double margin = 140;
        for (Bullet bullet : bullets) {
            // Both axes, not just the vertical one. Tri-shot spread, every boss fan and curtain
            // pattern, and every rocket already carry sideways velocity, so a y-only test leaks
            // anything that drifts off the left or right edge instead of the bottom.
            boolean gone = bullet.y() + bullet.height() < 0 || bullet.y() > GameConfig.HEIGHT
                    || bullet.x() + bullet.width() < 0 || bullet.x() > GameConfig.WIDTH;
            if (gone) {
                bullet.kill();
            }
        }
        // Hazards and pickups are culled directionally, not on all four sides like bullets: they
        // spawn off-screen on the entry side and have to survive the trip in. A boss arrives at a
        // depth of about -190, which any symmetric margin would kill on the spot.
        double arenaDepth = orientation.arenaDepth();
        for (Asteroid asteroid : asteroids) {
            double across = orientation.across(asteroid.x(), asteroid.y());
            double acrossExtent = orientation.acrossExtent(asteroid.width(), asteroid.height());
            boolean gone = orientation.depth(asteroid.x(), asteroid.y(),
                            asteroid.width(), asteroid.height()) > arenaDepth
                    || across + acrossExtent < -margin
                    || across > orientation.arenaBreadth() + margin
                    // Buried in the tunnel wall. Not pushed aside like an enemy -- a clamped
                    // asteroid stops reading as something falling freely -- and killed without an
                    // explosion, because addExplosion kicks the camera and a stream of rocks
                    // grinding into the wall would shake the screen without stopping.
                    || terrain.solidAt(asteroid.centerX(), asteroid.centerY());
            if (gone) {
                asteroid.kill();
            }
        }
        for (EnemyShip enemy : enemies) {
            boolean gone = orientation.depth(enemy.x(), enemy.y(),
                    enemy.width(), enemy.height()) > arenaDepth;
            if (gone) {
                enemy.kill();
            }
        }
        for (PowerUp powerUp : powerUps) {
            boolean gone = orientation.depth(powerUp.x(), powerUp.y(),
                    powerUp.width(), powerUp.height()) > arenaDepth;
            if (gone) {
                powerUp.kill();
            }
        }
    }

    /**
     * The collector: nudges a pickup toward whichever pilot has the longest reach on it.
     *
     * Nudges rather than teleports, and well below {@code PLAYER_SPEED}, so this saves a pilot the
     * detour for something nearly in reach instead of fetching the arena to them. In co-op it goes
     * to the nearer claim, which is the same rule the battle-mode pickup lane already follows: the
     * pickup does not decide who deserves it.
     */
    private void reelIn(PowerUp powerUp) {
        PlayerShip best = null;
        double bestDistance = Double.MAX_VALUE;
        for (PlayerShip player : players) {
            if (player.isOut()) {
                continue;
            }
            int level = player.loadout().level(Upgrade.COLLECTOR);
            if (level <= 0) {
                continue;
            }
            double reach = level * GameConfig.UPGRADE_COLLECTOR_RANGE;
            double dx = player.centerX() - powerUp.centerX();
            double dy = player.centerY() - powerUp.centerY();
            double distance = Math.hypot(dx, dy);
            if (distance <= reach && distance < bestDistance) {
                best = player;
                bestDistance = distance;
            }
        }
        if (best == null || bestDistance < 1) {
            return;
        }
        double dx = (best.centerX() - powerUp.centerX()) / bestDistance;
        double dy = (best.centerY() - powerUp.centerY()) / bestDistance;
        powerUp.setPosition(powerUp.x() + dx * GameConfig.UPGRADE_COLLECTOR_PULL,
                powerUp.y() + dy * GameConfig.UPGRADE_COLLECTOR_PULL);
    }

    /**
     * Drags a ship down-arena: an undertow, a gravity well, a solar wind.
     *
     * Applied after the ship has moved, so it reads as being pulled rather than as sluggish
     * controls, and left well below PLAYER_SPEED so the pull can always be flown against.
     */
    private void applyPull(PlayerShip player) {
        double pull = terrain.template().pull();
        if (pull == 0) {
            return;
        }
        player.setPosition(player.x() + orientation.vx(pull, 0),
                player.y() + orientation.vy(pull, 0));
    }

    private void clampToArena(PlayerShip player) {
        double maxX = GameConfig.WIDTH - player.width();
        double maxY = GameConfig.HEIGHT - player.height();
        double clampedX = Math.max(0, Math.min(maxX, player.x()));
        double clampedY = Math.max(0, Math.min(maxY, player.y()));
        player.setPosition(clampedX, clampedY);
    }

    /**
     * Removes every entity marked dead. The single removal point in the engine; safe because no
     * iteration over these lists is in progress when it runs.
     */
    public void sweep() {
        enemies.removeIf(enemy -> !enemy.isAlive());
        asteroids.removeIf(asteroid -> !asteroid.isAlive());
        bullets.removeIf(bullet -> !bullet.isAlive());
        powerUps.removeIf(powerUp -> !powerUp.isAlive());
        explosions.removeIf(ActiveExplosion::isFinished);
    }

    /**
     * Fits every ship the loadout its pilot is flying, by seat.
     *
     * The garage is local and what it sells is not: an upgrade changes a ship's damage, hull and
     * speed, so a peer that did not hear about one simulates a different ship. Positional by player
     * number, which is why the list that reaches here is built in seat order rather than in the
     * order peers happened to speak.
     *
     * A short list leaves the later seats as they are, which is what a peer that has said nothing
     * yet should mean.
     */
    public void fitLoadouts(List<String> codes) {
        for (PlayerShip player : players) {
            int seat = player.playerNumber() - 1;
            if (seat >= 0 && seat < codes.size()) {
                player.applyLoadout(Loadout.decode(codes.get(seat), player.playerNumber()));
            }
        }
    }

    /**
     * A cheap fingerprint of everything the simulation decides, for comparing two runs tick by tick.
     *
     * Positions, the counts, and each pilot's health, lives and score. Anything that diverges
     * without a position following it within a tick or two does not exist in this game: a shot
     * leaving at a different angle has moved somewhere else by the next update, and a hit that
     * lands on one machine and not the other changes a count immediately.
     *
     * Deliberately excludes the explosions and the screen shake. Both are read only by the
     * renderer, and both are functions of the tick a draw happens on rather than of the
     * simulation -- folding them in would make two machines drawing at different rates look like
     * a divergence.
     *
     * Raw bits rather than the value, so -0.0 and a NaN are compared as they are stored. Two
     * machines that disagree about a sign of zero have already diverged; the point here is to say
     * so rather than to be forgiving about it.
     *
     * ponytail: positions, counts and pilot state. Widen it if a divergence ever hides behind it.
     */
    public long checksum() {
        long hash = tick;
        hash = fold(hash, players);
        hash = fold(hash, enemies);
        hash = fold(hash, asteroids);
        hash = fold(hash, bullets);
        hash = fold(hash, powerUps);
        for (PlayerShip player : players) {
            hash = hash * 31 + player.health();
            hash = hash * 31 + player.lives();
            hash = hash * 31 + player.score();
        }
        return hash;
    }

    private static long fold(long hash, List<? extends Entity> entities) {
        hash = hash * 31 + entities.size();
        for (Entity entity : entities) {
            hash = hash * 31 + Double.doubleToRawLongBits(entity.x());
            hash = hash * 31 + Double.doubleToRawLongBits(entity.y());
        }
        return hash;
    }

    public void addBullet(Bullet bullet) {
        bullets.add(bullet);
    }

    /**
     * Brings a ship into the arena, pointed down the lane and off its neighbours' firing beat.
     *
     * Every enemy arrives through here -- authored waves, the difficulty trickle, and the escorts
     * a spawner flagship calls in -- which is what makes it the one place that can hand out a
     * spread. See {@link EnemyShip#enter(Orientation, int)} for why the counter is not a draw from
     * the spawn generator.
     */
    public void addEnemy(EnemyShip enemy) {
        enemy.enter(orientation, arrivals++);
        enemies.add(enemy);
    }

    public void addAsteroid(Asteroid asteroid) {
        asteroids.add(asteroid);
    }

    /**
     * Sets the pickup drifting down this level's lane.
     *
     * Stamped here rather than in the PowerUp constructor, which hard-codes a downward drift: both
     * the ambient drops and the ones enemies leave behind come through this one method, so it is
     * the only place that has to know which way is down-arena.
     */
    public void addPowerUp(PowerUp powerUp) {
        powerUp.setVelocity(orientation.vx(GameConfig.POWERUP_DRIFT_SPEED, 0),
                orientation.vy(GameConfig.POWERUP_DRIFT_SPEED, 0));
        powerUps.add(powerUp);
    }

    /**
     * A detonation at a point rather than on a thing.
     *
     * The {@code Entity} form sizes itself off what blew up, which a blast has nothing to ask. One
     * call per shell and never one per victim: this kicks the camera, and a nova into a group would
     * otherwise shake it once for every ship in the radius.
     */
    public void addBlast(double centerX, double centerY, double radius) {
        explosions.add(new ActiveExplosion(Explosion.LARGE, centerX, centerY, radius * 2));
        shake(Math.min(SHAKE_MAX, radius * 0.09));
    }

    public void addExplosion(Entity source, Explosion size) {
        double extent = Math.max(source.width(), source.height()) * 1.6;
        ActiveExplosion explosion = new ActiveExplosion(size, source.centerX(), source.centerY(), extent);
        explosions.add(explosion);
        // Scaled off the thing that blew up rather than special-cased per caller: a flagship is an
        // order of magnitude bigger than a scout, so "how big was it" already separates a boss
        // going up from an asteroid popping, and every explosion in the game routes through here.
        double kick = extent * (size == Explosion.LARGE ? 0.05 : 0.025);
        shake(Math.min(SHAKE_MAX, kick));
    }

    /** Throws the camera. The hardest kick still in flight wins; a weaker one does not cut it short. */
    public void shake(double amplitude) {
        if (amplitude <= shakeRemaining()) {
            return;
        }
        shakeAmplitude = amplitude;
        shakeStartTick = tick;
    }

    /** How far the camera should still be thrown, in pixels. Zero once the kick has settled. */
    public double shakeRemaining() {
        int elapsed = tick - shakeStartTick;
        if (elapsed >= SHAKE_TICKS) {
            return 0;
        }
        double remaining = shakeAmplitude * (1 - elapsed / (double) SHAKE_TICKS);
        return remaining;
    }

    /** The living player closest to the given enemy, or null when everyone is out. */
    public PlayerShip nearestPlayer(Entity to) {
        PlayerShip nearest = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        for (PlayerShip player : players) {
            if (player.isOut()) {
                continue;
            }
            double dx = player.centerX() - to.centerX();
            double dy = player.centerY() - to.centerY();
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                nearest = player;
            }
        }
        return nearest;
    }

    /**
     * The living enemy closest to the given entity, or null when the lane is clear.
     *
     * Mirrors {@link #nearestPlayer(Entity)} the other way round, for a player's rockets. Null is a
     * normal answer, not an error: {@link com.hashimjacobs.spacecase.entity.Rocket} flies straight
     * when it has nothing to chase, so a rocket launched into an empty sky simply leaves it.
     */
    public EnemyShip nearestEnemy(Entity to) {
        EnemyShip nearest = null;
        double bestDistanceSquared = Double.MAX_VALUE;
        for (EnemyShip enemy : enemies) {
            if (!enemy.isAlive()) {
                continue;
            }
            double dx = enemy.centerX() - to.centerX();
            double dy = enemy.centerY() - to.centerY();
            double distanceSquared = dx * dx + dy * dy;
            if (distanceSquared < bestDistanceSquared) {
                bestDistanceSquared = distanceSquared;
                nearest = enemy;
            }
        }
        return nearest;
    }

    /**
     * Whether the level still has a flagship to kill.
     *
     * Counts parts as well as bodies, deliberately. A hydra's heads always die before its torso --
     * the torso is untouchable until they have -- so this is already correct, and counting them
     * makes it the safety net if that invariant is ever broken: a level cannot clear while
     * anything belonging to the flagship is still alive and shooting.
     */
    public boolean bossPresent() {
        for (EnemyShip enemy : enemies) {
            if (enemy.isBoss()) {
                return true;
            }
        }
        return false;
    }

    /**
     * The flagship itself, for the HUD bar and the boss music.
     *
     * Prefers the torso rather than trusting insertion order: a hydra's heads are flagships by
     * every other test, and the health bar has to read the torso whichever one the list happens to
     * reach first.
     *
     * Falls back to a surviving part instead of returning null, which is what pairs this with
     * {@link #bossPresent}. A head only notices its torso has died on the next {@code update()},
     * so there is one step -- between the sweep that removes the torso and that update -- where
     * {@code bossPresent()} is true and there is no torso to find. Returning null there made the
     * boss music read {@code world.boss().boss().music()} on nothing, and blanked the HUD bar for a
     * frame while the heads were still on screen. A part carries its parent's {@code Boss}, so
     * answering with one is right rather than merely non-null.
     */
    public EnemyShip boss() {
        EnemyShip part = null;
        for (EnemyShip enemy : enemies) {
            if (!enemy.isBoss()) {
                continue;
            }
            if (!enemy.isBossPart()) {
                return enemy;
            }
            if (part == null) {
                part = enemy;
            }
        }
        return part;
    }

    public GameMode mode() {
        return mode;
    }

    public ModeRules rules() {
        ModeRules rules = mode.rules();
        return rules;
    }

    public int tick() {
        return tick;
    }

    /**
     * Puts the clock at an agreed number, for a networked level start and nothing else.
     *
     * The tick is simulation input, not a counter: {@code EnemyWeapons} derives its firing patterns
     * from it, the terrain scrolls by it, and the parallax reads it. Two machines that disagree
     * about the tick are two machines playing different games from the first frame.
     *
     * They will disagree by the time a level ends, and legitimately so. Every phase between two
     * fights -- the victory lap, the debrief, the garage, the warp -- runs {@link #tickScenery()},
     * and three of the four end when a *local* thing happens: a pilot pressing a key, a pilot
     * finishing their shopping, this machine's disk finishing a decode. That is the right design
     * for those phases and it is why they are not lockstepped. The cost is this method: the host
     * names the tick the next fight begins on, and everyone starts it there.
     */
    public void resumeAt(int tick) {
        this.tick = tick;
    }

    public List<PlayerShip> players() {
        return players;
    }

    public List<EnemyShip> enemies() {
        return enemies;
    }

    public List<Asteroid> asteroids() {
        return asteroids;
    }

    public List<Bullet> bullets() {
        return bullets;
    }

    public List<PowerUp> powerUps() {
        return powerUps;
    }

    public List<ActiveExplosion> explosions() {
        return explosions;
    }
}

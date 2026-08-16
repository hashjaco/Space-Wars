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
import com.hashimjacobs.spacecase.mode.GameMode;
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

    /** Which way the current level runs. Re-stamped on every level change; see setOrientation. */
    private Orientation orientation = Orientation.TOP_DOWN;

    private int tick;

    /** Names shown under the ships when nobody has been to the pilots screen. */
    private static final List<String> UNNAMED_PILOTS = List.of("PILOT 1", "PILOT 2");

    public World(GameMode mode) {
        this(mode, UNNAMED_PILOTS);
    }

    /** @param pilotNames one per player slot, player one first */
    public World(GameMode mode, List<String> pilotNames) {
        this.mode = mode;
        spawnPlayers(pilotNames);
    }

    private void spawnPlayers(List<String> pilotNames) {
        players.add(new PlayerShip(1, pilotNames.get(0), Facing.UP, 0, 0));
        if (mode.rules().playerCount() > 1) {
            players.add(new PlayerShip(2, pilotNames.get(1), Facing.UP, 0, 0));
        }
        placeSpawns();
    }

    /**
     * Sets which way this level runs and re-lays the player spawns to match.
     *
     * Called between levels, never during one.
     */
    public void setOrientation(Orientation orientation) {
        this.orientation = orientation;
        placeSpawns();
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
            boolean second = player.playerNumber() == 2;
            boolean opposed = headToHead && second;
            double w = player.width();
            double h = player.height();

            double lane = rules.playerCount() == 1 || headToHead
                    ? orientation.arenaBreadth() / 2
                    : orientation.arenaBreadth() * (second ? 2 : 1) / 3;
            double across = lane - orientation.acrossExtent(w, h) / 2;
            // Battle's second seat starts at the far end facing back down the arena.
            double depth = opposed ? 70 : orientation.arenaDepth() - 130;

            player.setSpawn(orientation.atX(depth, across, w, h),
                    orientation.atY(depth, across, w, h),
                    opposed ? near.opposite() : near);
            player.returnToSpawn();
        }
    }

    /** Moves everything and expires anything that has left the arena. Does not remove. */
    public void update() {
        tickScenery();

        for (PlayerShip player : players) {
            player.tickTimers();
            player.update();
            clampToArena(player);
        }
        for (EnemyShip enemy : enemies) {
            enemy.update();
        }
        for (Asteroid asteroid : asteroids) {
            asteroid.update();
        }
        for (Bullet bullet : bullets) {
            bullet.update();
        }
        for (PowerUp powerUp : powerUps) {
            powerUp.update();
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
        if (rules.lastPlayerStanding() || rules.playerCount() < 2) {
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
                    || across > orientation.arenaBreadth() + margin;
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

    public void addBullet(Bullet bullet) {
        bullets.add(bullet);
    }

    /** Points the arrival down this level's lane before it joins the fight. */
    public void addEnemy(EnemyShip enemy) {
        enemy.enter(orientation);
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

    public void addExplosion(Entity source, Explosion size) {
        double extent = Math.max(source.width(), source.height()) * 1.6;
        ActiveExplosion explosion = new ActiveExplosion(size, source.centerX(), source.centerY(), extent);
        explosions.add(explosion);
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

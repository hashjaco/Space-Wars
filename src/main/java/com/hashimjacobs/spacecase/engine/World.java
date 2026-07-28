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

    private int tick;

    public World(GameMode mode) {
        this.mode = mode;
        spawnPlayers();
    }

    private void spawnPlayers() {
        ModeRules rules = mode.rules();
        boolean headToHead = rules.lastPlayerStanding();

        if (rules.playerCount() == 1) {
            PlayerShip solo = new PlayerShip(1, Facing.UP,
                    GameConfig.WIDTH / 2 - 28, GameConfig.HEIGHT - 130);
            players.add(solo);
            return;
        }

        if (headToHead) {
            // Battle: facing each other down the long axis of the arena.
            PlayerShip bottom = new PlayerShip(1, Facing.UP,
                    GameConfig.WIDTH / 2 - 28, GameConfig.HEIGHT - 130);
            PlayerShip top = new PlayerShip(2, Facing.DOWN,
                    GameConfig.WIDTH / 2 - 28, 70);
            players.add(bottom);
            players.add(top);
            return;
        }

        // Co-op: side by side, both pushing up the arena.
        PlayerShip left = new PlayerShip(1, Facing.UP,
                GameConfig.WIDTH / 3 - 28, GameConfig.HEIGHT - 130);
        PlayerShip right = new PlayerShip(2, Facing.UP,
                2 * GameConfig.WIDTH / 3 - 28, GameConfig.HEIGHT - 130);
        players.add(left);
        right.setLean(PlayerShip.Lean.NONE);
        players.add(right);
    }

    /** Moves everything and expires anything that has left the arena. Does not remove. */
    public void update() {
        tick++;

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
        for (ActiveExplosion explosion : explosions) {
            explosion.tick();
        }

        killWhatLeftTheArena();
    }

    private void killWhatLeftTheArena() {
        double margin = 140;
        for (Bullet bullet : bullets) {
            boolean gone = bullet.y() + bullet.height() < 0 || bullet.y() > GameConfig.HEIGHT;
            if (gone) {
                bullet.kill();
            }
        }
        for (Asteroid asteroid : asteroids) {
            boolean gone = asteroid.y() > GameConfig.HEIGHT
                    || asteroid.x() + asteroid.width() < -margin
                    || asteroid.x() > GameConfig.WIDTH + margin;
            if (gone) {
                asteroid.kill();
            }
        }
        for (EnemyShip enemy : enemies) {
            boolean gone = enemy.y() > GameConfig.HEIGHT;
            if (gone) {
                enemy.kill();
            }
        }
        for (PowerUp powerUp : powerUps) {
            boolean gone = powerUp.y() > GameConfig.HEIGHT;
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

    public void addEnemy(EnemyShip enemy) {
        enemies.add(enemy);
    }

    public void addAsteroid(Asteroid asteroid) {
        asteroids.add(asteroid);
    }

    public void addPowerUp(PowerUp powerUp) {
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

    public boolean bossPresent() {
        for (EnemyShip enemy : enemies) {
            if (enemy.isBoss()) {
                return true;
            }
        }
        return false;
    }

    public EnemyShip boss() {
        for (EnemyShip enemy : enemies) {
            if (enemy.isBoss()) {
                return enemy;
            }
        }
        return null;
    }

    public Sprite backgroundSprite() {
        return Sprite.BACKGROUND;
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

package com.hashimjacobs.spacecase.engine;

import java.util.Random;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.entity.Asteroid;
import com.hashimjacobs.spacecase.entity.EnemyShip;
import com.hashimjacobs.spacecase.entity.PowerUp;
import com.hashimjacobs.spacecase.mode.ModeRules;
import com.hashimjacobs.spacecase.prefs.Difficulty;

/**
 * Decides what appears and when: asteroids, enemy waves, the boss, and pickups.
 *
 * The Random is injected rather than taken from Math.random(), so spawn behaviour is reproducible
 * under test.
 */
public final class SpawnDirector {

    /** Ticks between waves; a boss arrives at the end of every fourth wave. */
    private static final int WAVE_LENGTH_TICKS = 1500;
    private static final int WAVES_PER_BOSS = 4;
    private static final int POWERUP_CHANCE_PER_THOUSAND = 3;

    private final Random random;
    private final Difficulty difficulty;
    private final ModeRules rules;

    private int wave = 1;
    private int ticksIntoWave;
    private boolean bossSpawnedThisWave;

    public SpawnDirector(Random random, Difficulty difficulty, ModeRules rules) {
        this.random = random;
        this.difficulty = difficulty;
        this.rules = rules;
    }

    public void update(World world) {
        ticksIntoWave++;
        if (ticksIntoWave >= WAVE_LENGTH_TICKS) {
            ticksIntoWave = 0;
            wave++;
            bossSpawnedThisWave = false;
        }

        if (rules.spawnAsteroids()) {
            maybeSpawnAsteroid(world);
        }
        if (rules.spawnEnemies()) {
            maybeSpawnBoss(world);
            maybeSpawnEnemy(world);
        }
        if (rules.spawnPowerUps()) {
            maybeSpawnPowerUp(world);
        }
    }

    private void maybeSpawnAsteroid(World world) {
        if (!rolls(difficulty.asteroidChance())) {
            return;
        }
        int size = random.nextInt(10);
        Asteroid asteroid = switch (size) {
            case 0, 1 -> new Asteroid(Sprite.ASTEROID_HUGE, randomX(88), -90, 70, 22, 30);
            case 2, 3, 4 -> new Asteroid(Sprite.ASTEROID_BIG, randomX(56), -60, 40, 15, 20);
            default -> new Asteroid(Sprite.ASTEROID_SMALL, randomX(34), -40, 20, 9, 10);
        };
        double drift = (random.nextDouble() - 0.5) * 1.6;
        double fall = 1.6 + random.nextDouble() * 1.8;
        asteroid.setVelocity(drift, fall);
        world.addAsteroid(asteroid);
    }

    private void maybeSpawnEnemy(World world) {
        if (world.enemies().size() >= difficulty.maxEnemies()) {
            return;
        }
        if (!rolls(difficulty.enemyChance())) {
            return;
        }
        EnemyShip.EnemyKind kind = pickEnemyKind();
        EnemyShip enemy = new EnemyShip(kind, randomX(kind.sprite().width()), -kind.sprite().height());
        world.addEnemy(enemy);
    }

    /** Tougher archetypes become available as the waves progress. */
    private EnemyShip.EnemyKind pickEnemyKind() {
        int roll = random.nextInt(100);
        if (wave >= 3 && roll < 25) {
            return EnemyShip.EnemyKind.CRUISER;
        }
        if (roll < 55) {
            return EnemyShip.EnemyKind.FIGHTER;
        }
        return EnemyShip.EnemyKind.SCOUT;
    }

    private void maybeSpawnBoss(World world) {
        boolean bossWave = wave % WAVES_PER_BOSS == 0;
        boolean lateInWave = ticksIntoWave > WAVE_LENGTH_TICKS / 2;
        if (!bossWave || !lateInWave || bossSpawnedThisWave || world.bossPresent()) {
            return;
        }
        bossSpawnedThisWave = true;
        double x = GameConfig.WIDTH / 2 - Sprite.BOSS.width() / 2;
        EnemyShip boss = new EnemyShip(EnemyShip.EnemyKind.BOSS, x, -Sprite.BOSS.height());
        world.addEnemy(boss);
    }

    private void maybeSpawnPowerUp(World world) {
        if (!rolls(POWERUP_CHANCE_PER_THOUSAND)) {
            return;
        }
        PowerUp.Kind[] kinds = PowerUp.Kind.values();
        PowerUp.Kind kind = kinds[random.nextInt(kinds.length)];
        double x = spawnColumnForPickup(kind);
        PowerUp powerUp = new PowerUp(kind, x, -40);
        world.addPowerUp(powerUp);
    }

    /**
     * In battle mode pickups drop down the middle so both players have an equal claim; otherwise
     * anywhere across the arena.
     */
    private double spawnColumnForPickup(PowerUp.Kind kind) {
        double spriteWidth = kind.sprite().width();
        if (!rules.lastPlayerStanding()) {
            return randomX(spriteWidth);
        }
        double centreBand = GameConfig.WIDTH / 3;
        double offset = random.nextDouble() * centreBand;
        double x = centreBand + offset;
        return x;
    }

    private double randomX(double spriteWidth) {
        double span = GameConfig.WIDTH - spriteWidth;
        double x = random.nextDouble() * span;
        return x;
    }

    private boolean rolls(int chancePerThousand) {
        boolean hit = random.nextInt(1000) < chancePerThousand;
        return hit;
    }

    public int wave() {
        return wave;
    }
}

package com.hashimjacobs.spacecase.entity;

import com.hashimjacobs.spacecase.asset.Sprite;

/**
 * What a flagship's primary weapon actually puts in the air.
 *
 * Every boss in the game used to fire the same green bolt. The pattern varied -- a spread, a ring,
 * a spiral -- and the projectile never did, so at any distance fifty fights looked like one fight
 * with a different backdrop. This is the other half of the difference.
 *
 * <strong>One round per galaxy, not one per boss.</strong> A navy shares a weapon, so what is coming
 * at you says which part of the campaign you are in before the sky does, and the ten flagships of a
 * galaxy are already told apart by their three phases and their special. Fifty rounds would be fifty
 * sprites to draw and no more legible than five.
 *
 * The factors are against {@code GameConfig.ENEMY_BULLET_SPEED} and {@code ENEMY_BULLET_DAMAGE}, so
 * the whole set still moves with those two constants. They trade off deliberately: nothing here is
 * simply better than {@link #SPORE}, and the fastest round is the weakest.
 *
 * Pure data -- no asset loading -- so it is walkable in tests that never start the toolkit.
 */
public enum Ordnance {

    /** Verdance. The bolt every boss in the game used to fire, and the reference the rest are read against. */
    SPORE(Sprite.ENEMY_BULLET, 1.00, 1.00),

    /** Ashfall. A thrown cinder: quicker and lighter, so a wall of them is answered by moving. */
    EMBER(Sprite.BOSS_EMBER, 1.20, 0.85),

    /** Cryonis. A splinter, slow and heavy. The round you have time to see and cannot afford to eat. */
    SHARD(Sprite.BOSS_SHARD, 0.85, 1.30),

    /** Tempest. The fastest thing a flagship fires, and the weakest per hit. */
    BOLT(Sprite.BOSS_BOLT, 1.40, 0.75),

    /** Null. Slowest and heaviest: a round you have to leave rather than out-fly. */
    VOID(Sprite.BOSS_VOID, 0.72, 1.55);

    /** Levels in a galaxy, which is the block of flagships that shares a round. */
    private static final int PER_GALAXY = 10;

    private final Sprite art;
    private final double speedFactor;
    private final double damageFactor;

    Ordnance(Sprite art, double speedFactor, double damageFactor) {
        this.art = art;
        this.speedFactor = speedFactor;
        this.damageFactor = damageFactor;
    }

    /**
     * The round the flagships of one galaxy fire.
     *
     * Derived from the enum's own order, the way {@code mode.Level.galaxy()} is: the fifty
     * {@link Boss} constants are in level order and {@code LevelTest} holds the boss-to-level
     * bijection in both directions, so the tenth boss is the tenth level's by construction. A table
     * would be a second place for that to fall out of step.
     *
     * Clamped rather than indexed blind: a fifty-first flagship added before its galaxy exists gets
     * the last round rather than an ArrayIndexOutOfBoundsException in the middle of a boss fight.
     */
    public static Ordnance forBoss(Boss boss) {
        int galaxy = Math.min(boss.ordinal() / PER_GALAXY, values().length - 1);
        return values()[galaxy];
    }

    public Sprite art() {
        return art;
    }

    public double speedFactor() {
        return speedFactor;
    }

    public double damageFactor() {
        return damageFactor;
    }
}

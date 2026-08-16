package com.hashimjacobs.spacecase.asset;

/** Short sound effects. Played through AudioClip so overlapping shots do not cut each other off. */
public enum SoundFx {

    /**
     * The player's default weapon, at the full length it was authored.
     *
     * Two seconds against an eleven-tick cooldown means voices overlap several deep during
     * sustained fire. That is the intended sound -- the trimmed version was inaudible.
     */
    LASER("punchy-laser.wav", 0.5),

    /** Asteroids and ordinary debris. Deliberately the short generated burst; see EXPLOSION_SHIP. */
    EXPLOSION("explosion.wav", 0.55),

    /**
     * Ships and flagships only.
     *
     * Nine seconds long, where {@link #EXPLOSION} is under one. Asteroids die several a second and
     * would stack this into a permanent roar, so the two are separate rather than one sound reused.
     */
    SHIP_EXPLOSION("spaceship-explosion.wav", 0.5),

    COLLISION("collision.wav", 0.6),

    /** Retriggered on a timer while a player is nearly dead, rather than looped; see GameLoop. */
    LOW_HEALTH("low-health.wav", 0.4),

    /**
     * The flagship's primary weapon, fired with its phase pattern.
     *
     * Quiet because it retriggers up to five times a second on AIMED_BURST.
     * ponytail: volume is the only throttle; add a lastGunTick to EnemyShip if it muds up.
     */
    BOSS_GUN("machine-gun-burst.mp3", 0.22),

    /** The flagship's rocket salvo, on its own much longer timer. */
    BOSS_ROCKET("mega-boss-cannon.wav", 0.5),

    GAME_OVER("game-over.wav", 0.8),
    LEVEL_CLEAR("level-clear.wav", 0.8);

    private final String fileName;
    private final double baseVolume;

    SoundFx(String fileName, double baseVolume) {
        this.fileName = fileName;
        this.baseVolume = baseVolume;
    }

    public String resourcePath() {
        String path = "/sounds/" + fileName;
        return path;
    }

    /** Per-clip level so the laser does not drown out the music; scaled by the user's SFX setting. */
    public double baseVolume() {
        return baseVolume;
    }
}

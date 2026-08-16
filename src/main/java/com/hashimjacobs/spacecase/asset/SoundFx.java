package com.hashimjacobs.spacecase.asset;

/**
 * Short sound effects. Played through AudioClip so overlapping shots do not cut each other off.
 *
 * Each constant carries how long its sample runs, because a voice holds a native media player open
 * for exactly that long and {@link VoiceLimiter} has to know when one frees up. Voices alive is
 * duration times fire rate, so length here is not a detail -- it is the thing that decides how
 * many native players the game keeps open at once. Keep the seconds honest: a test checks each one
 * against the file.
 */
public enum SoundFx {

    /**
     * The player's default weapon.
     *
     * Cut from two seconds to half of one, which cost nothing audible: everything after 0.45s was
     * digital silence, and a voice sits open through silence exactly as long as through sound. An
     * earlier attempt trimmed to 0.35s and was reverted as inaudible -- that cut landed on the
     * sample's loudest point, since it swells rather than striking. Measure before cutting.
     */
    LASER("punchy-laser.wav", 0.5, 0.5),

    /** Asteroids and ordinary debris. Deliberately the short generated burst; see SHIP_EXPLOSION. */
    EXPLOSION("explosion.wav", 0.55, 0.75),

    /**
     * Ships and flagships only.
     *
     * Three seconds where {@link #EXPLOSION} is under one, so the two stay separate rather than one
     * sound reused: asteroids die several a second and would stack this into a permanent roar. Was
     * nine seconds, of which the last six were a tail below -20dB that cost six seconds of open
     * native player each time anything died.
     */
    SHIP_EXPLOSION("spaceship-explosion.wav", 0.5, 3.0),

    COLLISION("collision.wav", 0.6, 0.3),

    /** Retriggered on a timer while a player is nearly dead, rather than looped; see GameLoop. */
    LOW_HEALTH("low-health.wav", 0.4, 0.5),

    /**
     * The flagship's primary weapon, fired with its phase pattern.
     *
     * Quiet because it retriggers up to five times a second on AIMED_BURST.
     */
    BOSS_GUN("machine-gun-burst.mp3", 0.22, 1.632),

    /** The flagship's rocket salvo, on its own much longer timer. */
    BOSS_ROCKET("mega-boss-cannon.wav", 0.5, 3.68),

    GAME_OVER("game-over.wav", 0.8, 1.9),
    LEVEL_CLEAR("level-clear.wav", 0.8, 1.6);

    private final String fileName;
    private final double baseVolume;
    private final double seconds;

    SoundFx(String fileName, double baseVolume, double seconds) {
        this.fileName = fileName;
        this.baseVolume = baseVolume;
        this.seconds = seconds;
    }

    public String resourcePath() {
        String path = "/sounds/" + fileName;
        return path;
    }

    /** Per-clip level so the laser does not drown out the music; scaled by the user's SFX setting. */
    public double baseVolume() {
        return baseVolume;
    }

    /** How long one voice of this effect sounds for. */
    public double seconds() {
        return seconds;
    }
}

package com.hashimjacobs.spacecase.asset;

/**
 * Short sound effects, played through {@code javax.sound.sampled} by {@code SoundBank}, which
 * keeps a few clips open per effect so overlapping shots do not cut each other off.
 *
 * The second value is a level, not a length: {@code SoundBank} applies it as decibels on every
 * play, on top of the player's own SFX setting. It is the mix. The files themselves sit nearly
 * twelve decibels apart, and these numbers are what pull them back into one bed -- so a change
 * here is a mixing decision, and the effect it has is best judged by ear rather than by reading
 * the file's level.
 *
 * Effects must stay in a format the JDK can decode: {@code AssetProvenanceTest} checks that,
 * because {@code javax.sound.sampled} cannot read MP3 and an MP3 effect would be silently silent.
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
    LASER("punchy-laser.wav", 0.5),

    /** Asteroids and ordinary debris. Deliberately the short generated burst; see SHIP_EXPLOSION. */
    EXPLOSION("explosion.wav", 0.55),

    /**
     * Ships and flagships only.
     *
     * Three seconds where {@link #EXPLOSION} is under one, so the two stay separate rather than one
     * sound reused: asteroids die several a second and would stack this into a permanent roar. Was
     * nine seconds, of which the last six were a tail below -20dB that cost six seconds of open
     * native player each time anything died.
     */
    SHIP_EXPLOSION("spaceship-explosion.wav", 0.5),

    COLLISION("collision.wav", 0.6),

    /**
     * Retriggered on a timer while a player is nearly dead, rather than looped; see GameLoop.
     *
     * Was 0.4, which put it at about -15dB effective against a combat bed sitting near -23dB: the
     * loudest thing in the game, and hotter than the music, for a sound that fires over and over
     * while you are trying to survive. 0.18 lands it a shade above the bed -- audible through
     * gunfire, which is the whole job, without taking the mix over.
     */
    LOW_HEALTH("low-health.wav", 0.18),

    /**
     * The flagship's primary weapon, fired with its phase pattern.
     *
     * Quiet because it retriggers up to five times a second on AIMED_BURST.
     */
    BOSS_GUN("machine-gun-burst.wav", 0.22),

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

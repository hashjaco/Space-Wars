package com.hashimjacobs.spacecase.asset;

/** Short sound effects. Played through AudioClip so overlapping shots do not cut each other off. */
public enum SoundFx {

    LASER("laser.wav", 0.35),
    EXPLOSION("explosion.wav", 0.55),
    COLLISION("collision.wav", 0.6),
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

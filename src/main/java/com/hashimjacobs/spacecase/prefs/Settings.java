package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** User settings, persisted across runs in the platform preference store. */
public final class Settings {

    private static final String KEY_MUSIC = "musicVolume";
    private static final String KEY_SFX = "sfxVolume";
    private static final String KEY_DIFFICULTY = "difficulty";

    private final Preferences store;
    private double musicVolume;
    private double sfxVolume;
    private Difficulty difficulty;

    private Settings(Preferences store, double musicVolume, double sfxVolume, Difficulty difficulty) {
        this.store = store;
        this.musicVolume = musicVolume;
        this.sfxVolume = sfxVolume;
        this.difficulty = difficulty;
    }

    public static Settings load() {
        Preferences store = Preferences.userNodeForPackage(Settings.class);
        Settings settings = load(store);
        return settings;
    }

    /** Package-private so tests can supply a throwaway node. */
    static Settings load(Preferences store) {
        double music = clampVolume(store.getDouble(KEY_MUSIC, 0.5));
        double sfx = clampVolume(store.getDouble(KEY_SFX, 0.7));
        Difficulty difficulty = readDifficulty(store);
        Settings settings = new Settings(store, music, sfx, difficulty);
        return settings;
    }

    private static Difficulty readDifficulty(Preferences store) {
        String stored = store.get(KEY_DIFFICULTY, Difficulty.NORMAL.name());
        try {
            Difficulty parsed = Difficulty.valueOf(stored);
            return parsed;
        } catch (IllegalArgumentException e) {
            // A store written by a different version can name a difficulty that no longer exists.
            return Difficulty.NORMAL;
        }
    }

    public void save() {
        store.putDouble(KEY_MUSIC, musicVolume);
        store.putDouble(KEY_SFX, sfxVolume);
        store.put(KEY_DIFFICULTY, difficulty.name());
        try {
            store.flush();
        } catch (BackingStoreException e) {
            // Losing settings is not worth interrupting play over.
        }
    }

    private static double clampVolume(double value) {
        double clamped = Math.max(0.0, Math.min(1.0, value));
        return clamped;
    }

    public double musicVolume() {
        return musicVolume;
    }

    public void setMusicVolume(double value) {
        this.musicVolume = clampVolume(value);
    }

    public double sfxVolume() {
        return sfxVolume;
    }

    public void setSfxVolume(double value) {
        this.sfxVolume = clampVolume(value);
    }

    public Difficulty difficulty() {
        return difficulty;
    }

    public void setDifficulty(Difficulty difficulty) {
        this.difficulty = difficulty;
    }
}

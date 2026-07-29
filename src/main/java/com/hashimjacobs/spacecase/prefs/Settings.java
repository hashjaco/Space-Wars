package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

/** User settings, persisted across runs in the platform preference store. */
public final class Settings {

    private static final String KEY_MUSIC = "musicVolume";
    private static final String KEY_SFX = "sfxVolume";
    private static final String KEY_DIFFICULTY = "difficulty";
    private static final String KEY_GAMEPAD = "gamepadEnabled";
    private static final String KEY_DEADZONE = "gamepadDeadzone";
    private static final String KEY_FIRE_BUTTON = "gamepadFireButton";
    private static final String KEY_PAUSE_BUTTON = "gamepadPauseButton";

    private static final double MIN_DEADZONE = 0.05;
    private static final double MAX_DEADZONE = 0.80;

    private final Preferences store;
    private double musicVolume;
    private double sfxVolume;
    private Difficulty difficulty;
    private boolean gamepadEnabled;
    private double gamepadDeadzone;
    private PadButton gamepadFireButton;
    private PadButton gamepadPauseButton;

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
        settings.gamepadEnabled = store.getBoolean(KEY_GAMEPAD, true);
        settings.gamepadDeadzone = clampDeadzone(store.getDouble(KEY_DEADZONE, 0.30));
        settings.gamepadFireButton = readPadButton(store, KEY_FIRE_BUTTON, PadButton.A);
        settings.gamepadPauseButton = readPadButton(store, KEY_PAUSE_BUTTON, PadButton.START);
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

    private static PadButton readPadButton(Preferences store, String key, PadButton fallback) {
        String stored = store.get(key, fallback.name());
        try {
            PadButton parsed = PadButton.valueOf(stored);
            return parsed;
        } catch (IllegalArgumentException e) {
            // As above: a store written by a different version can name a button that is now gone.
            return fallback;
        }
    }

    public void save() {
        store.putDouble(KEY_MUSIC, musicVolume);
        store.putDouble(KEY_SFX, sfxVolume);
        store.put(KEY_DIFFICULTY, difficulty.name());
        store.putBoolean(KEY_GAMEPAD, gamepadEnabled);
        store.putDouble(KEY_DEADZONE, gamepadDeadzone);
        store.put(KEY_FIRE_BUTTON, gamepadFireButton.name());
        store.put(KEY_PAUSE_BUTTON, gamepadPauseButton.name());
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

    /**
     * Below the floor a resting stick reads as a held direction; above the ceiling most of the
     * stick's travel is dead and the ship stops responding.
     */
    private static double clampDeadzone(double value) {
        double clamped = Math.max(MIN_DEADZONE, Math.min(MAX_DEADZONE, value));
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

    public boolean gamepadEnabled() {
        return gamepadEnabled;
    }

    public void setGamepadEnabled(boolean enabled) {
        this.gamepadEnabled = enabled;
    }

    public double gamepadDeadzone() {
        return gamepadDeadzone;
    }

    public void setGamepadDeadzone(double value) {
        this.gamepadDeadzone = clampDeadzone(value);
    }

    public PadButton gamepadFireButton() {
        return gamepadFireButton;
    }

    public void setGamepadFireButton(PadButton button) {
        this.gamepadFireButton = button;
    }

    public PadButton gamepadPauseButton() {
        return gamepadPauseButton;
    }

    public void setGamepadPauseButton(PadButton button) {
        this.gamepadPauseButton = button;
    }
}

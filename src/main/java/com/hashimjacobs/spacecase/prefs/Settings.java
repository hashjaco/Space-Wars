package com.hashimjacobs.spacecase.prefs;

import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javafx.scene.input.KeyCode;

/** User settings, persisted across runs in the platform preference store. */
public final class Settings {

    private static final String KEY_MUSIC = "musicVolume";
    private static final String KEY_SFX = "sfxVolume";
    private static final String KEY_DIFFICULTY = "difficulty";
    private static final String KEY_GAMEPAD = "gamepadEnabled";
    private static final String KEY_DEADZONE = "gamepadDeadzone";
    private static final String KEY_ANALOG = "gamepadAnalog";
    private static final String KEY_REDUCED_FLASH = "reducedFlash";
    /** Player one keeps the original keys, so a store written before per-player binding loads. */
    private static final String[] KEY_FIRE_BUTTON = {"gamepadFireButton", "gamepadFireButton2"};
    private static final String[] KEY_PAUSE_BUTTON = {"gamepadPauseButton", "gamepadPauseButton2"};
    private static final String[] KEY_SENSITIVITY = {"gamepadSensitivity", "gamepadSensitivity2"};

    /** Slots 0 and 1, addressed by player number one and two. */
    private static final int PLAYERS = 2;

    /** One stored key per action per player, named from the action so no table has to be kept. */
    private static String keyBindingName(int slot, ControlAction action) {
        return "key" + action.name() + (slot == 0 ? "" : "2");
    }

    private static final double MIN_DEADZONE = 0.05;
    private static final double MAX_DEADZONE = 0.80;
    private static final double MIN_SENSITIVITY = 0.50;
    private static final double MAX_SENSITIVITY = 2.00;

    private final Preferences store;
    private double musicVolume;
    private double sfxVolume;
    private Difficulty difficulty;
    private boolean gamepadEnabled;
    private double gamepadDeadzone;
    private boolean gamepadAnalog;
    private boolean reducedFlash;
    private final PadButton[] gamepadFireButton = new PadButton[PLAYERS];
    private final PadButton[] gamepadPauseButton = new PadButton[PLAYERS];
    private final double[] gamepadSensitivity = new double[PLAYERS];
    private final KeyCode[][] keyBindings = new KeyCode[PLAYERS][ControlAction.values().length];

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
        settings.gamepadAnalog = store.getBoolean(KEY_ANALOG, true);
        settings.reducedFlash = store.getBoolean(KEY_REDUCED_FLASH, false);
        for (int slot = 0; slot < PLAYERS; slot++) {
            settings.gamepadFireButton[slot] =
                    readPadButton(store, KEY_FIRE_BUTTON[slot], PadButton.A);
            settings.gamepadPauseButton[slot] =
                    readPadButton(store, KEY_PAUSE_BUTTON[slot], PadButton.START);
            settings.gamepadSensitivity[slot] =
                    clampSensitivity(store.getDouble(KEY_SENSITIVITY[slot], 1.0));
            for (ControlAction action : ControlAction.values()) {
                settings.keyBindings[slot][action.ordinal()] =
                        readKeyCode(store, keyBindingName(slot, action), action.defaultKey(slot + 1));
            }
        }
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

    /**
     * A stored key name, or the action's default where it does not name a key this JavaFX knows.
     *
     * Same tolerance as {@link #readPadButton}: a store written by another build, or by hand, must
     * not stop the game launching. It costs a pilot one rebind and nothing else.
     */
    private static KeyCode readKeyCode(Preferences store, String key, KeyCode fallback) {
        String stored = store.get(key, fallback.name());
        try {
            return KeyCode.valueOf(stored);
        } catch (IllegalArgumentException unknownKey) {
            return fallback;
        }
    }

    public void save() {
        store.putDouble(KEY_MUSIC, musicVolume);
        store.putDouble(KEY_SFX, sfxVolume);
        store.put(KEY_DIFFICULTY, difficulty.name());
        store.putBoolean(KEY_GAMEPAD, gamepadEnabled);
        store.putDouble(KEY_DEADZONE, gamepadDeadzone);
        store.putBoolean(KEY_ANALOG, gamepadAnalog);
        store.putBoolean(KEY_REDUCED_FLASH, reducedFlash);
        for (int slot = 0; slot < PLAYERS; slot++) {
            store.put(KEY_FIRE_BUTTON[slot], gamepadFireButton[slot].name());
            store.put(KEY_PAUSE_BUTTON[slot], gamepadPauseButton[slot].name());
            store.putDouble(KEY_SENSITIVITY[slot], gamepadSensitivity[slot]);
            for (ControlAction action : ControlAction.values()) {
                store.put(keyBindingName(slot, action), keyBindings[slot][action.ordinal()].name());
            }
        }
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

    /**
     * How sharply the stick answers, as a multiplier the settings screen shows as a percentage.
     *
     * Read as the reciprocal of a response exponent, so above 1 the ship reaches speed sooner and
     * below 1 it holds back for finer control. The floor and ceiling are what keep it a feel
     * control rather than a balance one: a full push always yields the ship's whole speed, because
     * one raised to any power is one.
     */
    private static double clampSensitivity(double value) {
        double clamped = Math.max(MIN_SENSITIVITY, Math.min(MAX_SENSITIVITY, value));
        return clamped;
    }

    public double gamepadDeadzone() {
        return gamepadDeadzone;
    }

    public void setGamepadDeadzone(double value) {
        this.gamepadDeadzone = clampDeadzone(value);
    }

    /** Whether the left stick drives the ship at the speed it is pushed, rather than all-or-nothing. */
    public boolean gamepadAnalog() {
        return gamepadAnalog;
    }

    public void setGamepadAnalog(boolean analog) {
        this.gamepadAnalog = analog;
    }

    /**
     * Whether to hold the strobing visuals still: the respawn blink, the impact halo, the low-lives
     * and low-health pulses, the flagship warning, and the screen shake.
     *
     * Off by default, since the flashes carry information and most players want them. What it must
     * not do is take that information away with them -- see the renderer, which fades the hull
     * steadily instead of blinking it, on the same reasoning as the lives counter in the HUD.
     */
    public boolean reducedFlash() {
        return reducedFlash;
    }

    public void setReducedFlash(boolean reduced) {
        this.reducedFlash = reduced;
    }

    /**
     * The key this player uses for an action.
     *
     * @param player one or two
     */
    public KeyCode key(int player, ControlAction action) {
        return keyBindings[player - 1][action.ordinal()];
    }

    public void setKey(int player, ControlAction action, KeyCode code) {
        keyBindings[player - 1][action.ordinal()] = code;
    }

    /** Puts one seat back on the keys it shipped with, for a pilot who has bound themselves out. */
    public void resetKeys(int player) {
        for (ControlAction action : ControlAction.values()) {
            setKey(player, action, action.defaultKey(player));
        }
    }

    /** Who else is already using a key, or null when nobody is. Rebinding must not create a tie. */
    public ControlAction keyClash(int player, ControlAction binding, KeyCode code) {
        for (int slot = 0; slot < PLAYERS; slot++) {
            for (ControlAction action : ControlAction.values()) {
                boolean itself = slot == player - 1 && action == binding;
                if (!itself && keyBindings[slot][action.ordinal()] == code) {
                    return action;
                }
            }
        }
        return null;
    }

    /** @param player one or two; each pad carries its own bindings. */
    public PadButton gamepadFireButton(int player) {
        return gamepadFireButton[player - 1];
    }

    public void setGamepadFireButton(int player, PadButton button) {
        this.gamepadFireButton[player - 1] = button;
    }

    /** @param player one or two; each pad carries its own feel. */
    public double gamepadSensitivity(int player) {
        return gamepadSensitivity[player - 1];
    }

    public void setGamepadSensitivity(int player, double value) {
        this.gamepadSensitivity[player - 1] = clampSensitivity(value);
    }

    public PadButton gamepadPauseButton(int player) {
        return gamepadPauseButton[player - 1];
    }

    public void setGamepadPauseButton(int player, PadButton button) {
        this.gamepadPauseButton[player - 1] = button;
    }
}

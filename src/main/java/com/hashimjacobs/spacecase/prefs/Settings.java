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
    private static final String KEY_CINEMATIC = "glowEffects";
    private static final String KEY_FILM_GRAIN = "filmGrain";
    private static final String KEY_MIRRORED_SIDES = "mirroredSides";
    private static final String KEY_WINDOW = "windowHeight";

    /** {@link #windowHeight} values that are not a pixel height. */
    public static final int FULLSCREEN = 0;
    public static final int FIT_SCREEN = -1;
    /** Player one keeps the original keys, so a store written before per-player binding loads. */
    private static final String[] KEY_PAD_LAYOUT = {"padLayout", "padLayout2"};
    private static final String[] KEY_SENSITIVITY = {"gamepadSensitivity", "gamepadSensitivity2"};

    /** Slots 0 and 1, addressed by player number one and two. */
    private static final int PLAYERS = 2;

    /** One stored key per action per player, named from the action so no table has to be kept. */
    private static String keyBindingName(int slot, ControlAction action) {
        return "key" + action.name() + (slot == 0 ? "" : "2");
    }

    /**
     * One stored button per pad action per player. Fire and pause keep the names they had when they
     * were the only two a pad could rebind, so a store from before the controller screen loads.
     */
    private static String padBindingName(int slot, PadAction action) {
        String name = switch (action) {
            case FIRE -> "gamepadFireButton";
            case PAUSE -> "gamepadPauseButton";
            default -> "pad" + action.name();
        };
        return name + (slot == 0 ? "" : "2");
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
    private boolean cinematic;
    private boolean filmGrain;
    private boolean mirroredSides;
    private int windowHeight;
    private final PadButton[][] padButtons = new PadButton[PLAYERS][PadAction.values().length];
    private final String[] padLayout = new String[PLAYERS];
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
        settings.cinematic = store.getBoolean(KEY_CINEMATIC, true);
        settings.filmGrain = store.getBoolean(KEY_FILM_GRAIN, false);
        settings.mirroredSides = store.getBoolean(KEY_MIRRORED_SIDES, true);
        settings.windowHeight = store.getInt(KEY_WINDOW, 864);
        for (int slot = 0; slot < PLAYERS; slot++) {
            for (PadAction action : PadAction.values()) {
                settings.padButtons[slot][action.ordinal()] =
                        readPadButton(store, padBindingName(slot, action), action.defaultButton());
            }
            untangle(settings.padButtons[slot]);
            settings.padLayout[slot] = store.get(KEY_PAD_LAYOUT[slot], "");
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
     * Gives every action a button of its own.
     *
     * {@link #setPadButton} never creates a tie, but a store can arrive with one: fire moved to X
     * back when punch was fixed there, or a node edited by hand. The later action gives way, to the
     * first button nothing is using.
     */
    private static void untangle(PadButton[] row) {
        for (int later = 1; later < row.length; later++) {
            for (int earlier = 0; earlier < later; earlier++) {
                if (row[later] == row[earlier]) {
                    row[later] = firstUnused(row);
                }
            }
        }
    }

    private static PadButton firstUnused(PadButton[] row) {
        for (PadButton candidate : PadButton.values()) {
            if (!java.util.Arrays.asList(row).contains(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("more pad actions than pad buttons");
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
        store.putBoolean(KEY_CINEMATIC, cinematic);
        store.putBoolean(KEY_FILM_GRAIN, filmGrain);
        store.putBoolean(KEY_MIRRORED_SIDES, mirroredSides);
        store.putInt(KEY_WINDOW, windowHeight);
        for (int slot = 0; slot < PLAYERS; slot++) {
            for (PadAction action : PadAction.values()) {
                store.put(padBindingName(slot, action), padButtons[slot][action.ordinal()].name());
            }
            store.put(KEY_PAD_LAYOUT[slot], padLayout[slot]);
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
     * Cinematic lighting: glow on the flagship and the hulls, cast shadows, the darkened arena and
     * the letterboxed arrival while a flagship is on the field. On by default. Off is the flat look,
     * for slower machines or players who want every pixel plain.
     */
    public boolean cinematic() {
        return cinematic;
    }

    public void setCinematic(boolean on) {
        this.cinematic = on;
    }

    /** A faint moving film grain over the arena. Off by default: taste, not information. */
    public boolean filmGrain() {
        return filmGrain;
    }

    public void setFilmGrain(boolean on) {
        this.filmGrain = on;
    }

    /**
     * Whether the bars either side of the arena, on a screen wider than it, carry the level's sky
     * mirrored about each edge. Off leaves them plain dark; the seat panels sit in them either way.
     * On by default.
     */
    public boolean mirroredSides() {
        return mirroredSides;
    }

    public void setMirroredSides(boolean on) {
        this.mirroredSides = on;
    }

    /**
     * The window's content height in pixels (its width follows the arena's aspect), or
     * {@link #FULLSCREEN}, or {@link #FIT_SCREEN} for a maximized window.
     */
    public int windowHeight() {
        return windowHeight;
    }

    public void setWindowHeight(int height) {
        this.windowHeight = height;
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

    /**
     * The button this player's pad uses for an action.
     *
     * @param player one or two; each pad carries its own bindings
     */
    public PadButton padButton(int player, PadAction action) {
        return padButtons[player - 1][action.ordinal()];
    }

    /**
     * Binds an action to a button. Whatever was on that button takes the one this action gives up,
     * so a rebind is a swap and no action is ever left without a button.
     */
    public void setPadButton(int player, PadAction action, PadButton button) {
        PadButton[] row = padButtons[player - 1];
        for (int other = 0; other < row.length; other++) {
            if (row[other] == button) {
                row[other] = row[action.ordinal()];
            }
        }
        row[action.ordinal()] = button;
    }

    /**
     * Which controller drawing this player chose on the controller screen, by name, or empty to go
     * by whatever the connected pad calls itself. A name rather than the enum because the drawings
     * belong to the UI, and a name this build does not know simply reads as "not chosen".
     */
    public String padLayout(int player) {
        return padLayout[player - 1];
    }

    public void setPadLayout(int player, String name) {
        padLayout[player - 1] = name;
    }

    /** @param player one or two; each pad carries its own feel. */
    public double gamepadSensitivity(int player) {
        return gamepadSensitivity[player - 1];
    }

    public void setGamepadSensitivity(int player, double value) {
        this.gamepadSensitivity[player - 1] = clampSensitivity(value);
    }
}

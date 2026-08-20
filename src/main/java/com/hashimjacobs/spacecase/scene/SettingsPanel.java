package com.hashimjacobs.spacecase.scene;

import javafx.geometry.Pos;
import javafx.scene.layout.VBox;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.prefs.PadButton;
import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * The settings controls, as a reusable node so the start menu and the pause overlay show the same
 * thing rather than each growing its own copy.
 */
final class SettingsPanel extends VBox {

    private static final double VOLUME_STEP = 0.1;
    private static final double DEADZONE_STEP = 0.05;
    private static final double SENSITIVITY_STEP = 0.25;
    private static final double MIN_SENSITIVITY = 0.50;
    private static final double MAX_SENSITIVITY = 2.00;
    private static final double MAX_DEADZONE = 0.80;
    private static final int PLAYERS = 2;

    private final Settings settings;
    private final SoundBank sounds;
    private final MenuButton musicRow;
    private final MenuButton sfxRow;
    private final MenuButton difficultyRow;
    private final MenuButton controllerRow;
    private final MenuButton analogRow;
    private final MenuButton reducedFlashRow;
    private final MenuButton controlsRow;
    private final MenuButton[] fireButtonRow = new MenuButton[PLAYERS];
    private final MenuButton[] pauseButtonRow = new MenuButton[PLAYERS];
    private final MenuButton[] sensitivityRow = new MenuButton[PLAYERS];
    private final MenuButton deadzoneRow;
    private final MenuButton padStatusRow;
    private final Supplier<String> padStatus;
    private final MenuPanel rows;

    /**
     * @param padStatus what the pad reader currently sees, or null where no reader exists. Supplied
     *                  rather than held so the row reads live state without this panel knowing
     *                  anything about SDL.
     */
    SettingsPanel(Settings settings, SoundBank sounds, Supplier<String> padStatus, Runnable onBack) {
        this(settings, sounds, padStatus, onBack, null);
    }

    /**
     * @param onControls opens the keyboard rebinding screen, or null where there is nowhere to go --
     *                   the pause overlay has no second screen to push, so it hides the row
     */
    SettingsPanel(Settings settings, SoundBank sounds, Supplier<String> padStatus, Runnable onBack,
                  Runnable onControls) {
        this.settings = settings;
        this.sounds = sounds;
        this.padStatus = padStatus;

        musicRow = new MenuButton("", this::cycleMusicVolume);
        sfxRow = new MenuButton("", this::cycleSfxVolume);
        difficultyRow = new MenuButton("", this::cycleDifficulty);
        controllerRow = new MenuButton("", this::toggleController);
        analogRow = new MenuButton("", this::toggleAnalog);
        reducedFlashRow = new MenuButton("", this::toggleReducedFlash);
        controlsRow = onControls == null ? null : new MenuButton("Keyboard   >", onControls);
        for (int slot = 0; slot < PLAYERS; slot++) {
            int player = slot + 1;
            fireButtonRow[slot] = new MenuButton("", () -> cycleFireButton(player));
            pauseButtonRow[slot] = new MenuButton("", () -> cyclePauseButton(player));
            sensitivityRow[slot] = new MenuButton("", () -> cycleSensitivity(player));
        }
        deadzoneRow = new MenuButton("", this::cycleDeadzone);
        // Nothing to activate: a readout, not a control. Left in the navigable list all the same so
        // it cannot be skipped past unseen on a pad, which is exactly who needs to read it.
        padStatusRow = new MenuButton("", () -> { });
        MenuButton backRow = new MenuButton("Back", () -> {
            settings.save();
            onBack.run();
        });

        refreshLabels();

        List<MenuButton> panelRows = new ArrayList<>(List.of(
                musicRow, sfxRow, difficultyRow, reducedFlashRow, controllerRow, analogRow,
                fireButtonRow[0], pauseButtonRow[0], sensitivityRow[0],
                fireButtonRow[1], pauseButtonRow[1], sensitivityRow[1],
                deadzoneRow, padStatusRow));
        if (controlsRow != null) {
            panelRows.add(controlsRow);
        }
        panelRows.add(backRow);
        rows = new MenuPanel(panelRows.toArray(new MenuButton[0]));
        setAlignment(Pos.CENTER);
        getChildren().add(rows);
    }

    /** Keyboard navigation over the settings rows, with Escape wired to the back action. */
    MenuNavigator navigator(Runnable onBack) {
        MenuNavigator navigator = rows.navigator();
        navigator.setOnBack(onBack);
        return navigator;
    }

    /**
     * Brings the rows up to date, the pad readout above all.
     *
     * The pause overlay builds its panel once and reveals it again and again, so a controller
     * switched on mid-session would otherwise still read as absent -- which is precisely the case
     * the readout exists to explain.
     */
    void refresh() {
        refreshLabels();
    }

    private void cycleMusicVolume() {
        double next = wrapVolume(settings.musicVolume());
        settings.setMusicVolume(next);
        sounds.applyVolumes();
        refreshLabels();
    }

    private void cycleSfxVolume() {
        double next = wrapVolume(settings.sfxVolume());
        settings.setSfxVolume(next);
        refreshLabels();
    }

    private void cycleDifficulty() {
        Difficulty next = settings.difficulty().next();
        settings.setDifficulty(next);
        refreshLabels();
    }

    private void toggleController() {
        boolean next = !settings.gamepadEnabled();
        settings.setGamepadEnabled(next);
        refreshLabels();
    }

    private void toggleAnalog() {
        boolean next = !settings.gamepadAnalog();
        settings.setGamepadAnalog(next);
        refreshLabels();
    }

    private void toggleReducedFlash() {
        boolean next = !settings.reducedFlash();
        settings.setReducedFlash(next);
        refreshLabels();
    }

    private void cycleFireButton(int player) {
        PadButton next = settings.gamepadFireButton(player).next();
        settings.setGamepadFireButton(player, next);
        refreshLabels();
    }

    private void cyclePauseButton(int player) {
        PadButton next = settings.gamepadPauseButton(player).next();
        settings.setGamepadPauseButton(player, next);
        refreshLabels();
    }

    /**
     * Steps up in quarters and wraps back to the floor past the top.
     *
     * Wraps to the minimum rather than to zero the way the deadzone does: sensitivity is read as an
     * exponent's reciprocal, and zero has no meaning there.
     */
    private void cycleSensitivity(int player) {
        double next = settings.gamepadSensitivity(player) + SENSITIVITY_STEP;
        if (next > MAX_SENSITIVITY + 0.0001) {
            next = MIN_SENSITIVITY;
        }
        settings.setGamepadSensitivity(player, next);
        refreshLabels();
    }

    /** Steps up in twentieths and wraps back to the smallest past the top. */
    private void cycleDeadzone() {
        double next = settings.gamepadDeadzone() + DEADZONE_STEP;
        if (next > MAX_DEADZONE + 0.0001) {
            next = 0;
        }
        settings.setGamepadDeadzone(next);
        refreshLabels();
    }

    /** Steps up in tenths and wraps back to silent past the top. */
    private static double wrapVolume(double current) {
        double next = current + VOLUME_STEP;
        if (next > 1.0001) {
            return 0;
        }
        return next;
    }

    private void refreshLabels() {
        musicRow.setText("Music        " + percent(settings.musicVolume()));
        sfxRow.setText("Sound FX     " + percent(settings.sfxVolume()));
        difficultyRow.setText("Difficulty   " + settings.difficulty().label());
        reducedFlashRow.setText("Flashing     " + (settings.reducedFlash() ? "Reduced" : "Full"));
        controllerRow.setText("Controller   " + (settings.gamepadEnabled() ? "On" : "Off"));
        analogRow.setText("Stick mode   " + (settings.gamepadAnalog() ? "Analog" : "Digital"));
        for (int slot = 0; slot < PLAYERS; slot++) {
            int player = slot + 1;
            fireButtonRow[slot].setText(
                    "P" + player + " fire      " + settings.gamepadFireButton(player).label());
            pauseButtonRow[slot].setText(
                    "P" + player + " pause     " + settings.gamepadPauseButton(player).label());
            sensitivityRow[slot].setText(
                    "P" + player + " stick     " + percent(settings.gamepadSensitivity(player)));
        }
        deadzoneRow.setText("Deadzone     " + percent(settings.gamepadDeadzone()));
        padStatusRow.setText(padStatus == null ? "Pads         --" : padStatus.get());
    }

    private static String percent(double value) {
        String text = Math.round(value * 100) + "%";
        return text;
    }
}

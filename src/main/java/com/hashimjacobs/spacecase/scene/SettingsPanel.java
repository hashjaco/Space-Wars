package com.hashimjacobs.spacecase.scene;

import javafx.geometry.Pos;
import javafx.scene.layout.VBox;

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
    private static final double MAX_DEADZONE = 0.80;

    private final Settings settings;
    private final SoundBank sounds;
    private final MenuButton musicRow;
    private final MenuButton sfxRow;
    private final MenuButton difficultyRow;
    private final MenuButton controllerRow;
    private final MenuButton fireButtonRow;
    private final MenuButton pauseButtonRow;
    private final MenuButton deadzoneRow;
    private final MenuPanel rows;

    SettingsPanel(Settings settings, SoundBank sounds, Runnable onBack) {
        this.settings = settings;
        this.sounds = sounds;

        musicRow = new MenuButton("", this::cycleMusicVolume);
        sfxRow = new MenuButton("", this::cycleSfxVolume);
        difficultyRow = new MenuButton("", this::cycleDifficulty);
        controllerRow = new MenuButton("", this::toggleController);
        fireButtonRow = new MenuButton("", this::cycleFireButton);
        pauseButtonRow = new MenuButton("", this::cyclePauseButton);
        deadzoneRow = new MenuButton("", this::cycleDeadzone);
        MenuButton backRow = new MenuButton("Back", () -> {
            settings.save();
            onBack.run();
        });

        refreshLabels();

        rows = new MenuPanel(musicRow, sfxRow, difficultyRow, controllerRow, fireButtonRow,
                pauseButtonRow, deadzoneRow, backRow);
        setAlignment(Pos.CENTER);
        getChildren().add(rows);
    }

    /** Keyboard navigation over the settings rows, with Escape wired to the back action. */
    MenuNavigator navigator(Runnable onBack) {
        MenuNavigator navigator = rows.navigator();
        navigator.setOnBack(onBack);
        return navigator;
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

    private void cycleFireButton() {
        PadButton next = settings.gamepadFireButton().next();
        settings.setGamepadFireButton(next);
        refreshLabels();
    }

    private void cyclePauseButton() {
        PadButton next = settings.gamepadPauseButton().next();
        settings.setGamepadPauseButton(next);
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
        controllerRow.setText("Controller   " + (settings.gamepadEnabled() ? "On" : "Off"));
        fireButtonRow.setText("Fire button  " + settings.gamepadFireButton().label());
        pauseButtonRow.setText("Pause button " + settings.gamepadPauseButton().label());
        deadzoneRow.setText("Deadzone     " + percent(settings.gamepadDeadzone()));
    }

    private static String percent(double value) {
        String text = Math.round(value * 100) + "%";
        return text;
    }
}

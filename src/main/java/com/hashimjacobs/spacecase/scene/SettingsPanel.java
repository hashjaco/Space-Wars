package com.hashimjacobs.spacecase.scene;

import javafx.geometry.Pos;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
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

    /** What one Left/Right press moves a slider row by. Drags snap finer; see each row. */
    private static final double KEY_STEP = 0.05;
    private static final int PLAYERS = 2;

    private final Settings settings;
    private final SoundBank sounds;
    private final MenuButton musicRow;
    private final MenuButton sfxRow;
    private final MenuButton difficultyRow;
    private final MenuButton controllerRow;
    private final MenuButton analogRow;
    private final MenuButton reducedFlashRow;
    private final MenuButton cinematicRow;
    private final MenuButton grainRow;
    private final MenuButton resolutionRow;
    private final MenuButton controlsRow;
    private final MenuButton[] fireButtonRow = new MenuButton[PLAYERS];
    private final MenuButton[] pauseButtonRow = new MenuButton[PLAYERS];
    private final MenuButton[] sensitivityRow = new MenuButton[PLAYERS];
    private final MenuButton deadzoneRow;
    private final MenuButton padStatusRow;
    private final Supplier<String> padStatus;
    private final MenuPanel rows;
    /** Pushes the stored values back into the bars, for {@link #refreshLabels}. */
    private final List<Runnable> barRefreshers = new ArrayList<>();

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

        // Range and drag snap per row; the setters on Settings own the real clamping.
        musicRow = sliderRow(0, 1, 0.01, settings::musicVolume, value -> {
            settings.setMusicVolume(value);
            sounds.applyVolumes();
        });
        sfxRow = sliderRow(0, 1, 0.01, settings::sfxVolume, settings::setSfxVolume);
        difficultyRow = new MenuButton("", this::cycleDifficulty);
        controllerRow = new MenuButton("", this::toggleController);
        analogRow = new MenuButton("", this::toggleAnalog);
        reducedFlashRow = new MenuButton("", this::toggleReducedFlash);
        cinematicRow = new MenuButton("", this::toggleCinematic);
        grainRow = new MenuButton("", this::toggleGrain);
        resolutionRow = new MenuButton("", this::cycleResolution);
        controlsRow = onControls == null ? null : new MenuButton("Keyboard", onControls);
        if (controlsRow != null) {
            controlsRow.setRow("Keyboard", ">");
        }
        for (int slot = 0; slot < PLAYERS; slot++) {
            int player = slot + 1;
            fireButtonRow[slot] = new MenuButton("", () -> cycleFireButton(player));
            pauseButtonRow[slot] = new MenuButton("", () -> cyclePauseButton(player));
            sensitivityRow[slot] = sliderRow(0.50, 2.00, 0.05,
                    () -> settings.gamepadSensitivity(player),
                    value -> settings.setGamepadSensitivity(player, value));
        }
        deadzoneRow = sliderRow(0.05, 0.80, 0.01,
                settings::gamepadDeadzone, settings::setGamepadDeadzone);
        // Nothing to activate: a readout, not a control. Left in the navigable list all the same so
        // it cannot be skipped past unseen on a pad, which is exactly who needs to read it.
        padStatusRow = new MenuButton("", () -> { });
        MenuButton backRow = new MenuButton("Back", () -> {
            settings.save();
            onBack.run();
        });

        refreshLabels();

        List<MenuButton> panelRows = new ArrayList<>(List.of(
                musicRow, sfxRow, difficultyRow, resolutionRow, reducedFlashRow, cinematicRow, grainRow,
                controllerRow, analogRow,
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

    private void toggleCinematic() {
        settings.setCinematic(!settings.cinematic());
        refreshLabels();
    }

    private void cycleResolution() {
        List<Integer> options = SceneRouter.resolutions();
        // A stored size this screen cannot fit is not in the list; indexOf's -1 starts the cycle over.
        int next = options.get((options.indexOf(settings.windowHeight()) + 1) % options.size());
        settings.setWindowHeight(next);
        SceneRouter.applyResolution((Stage) getScene().getWindow(), next);
        refreshLabels();
    }

    private void toggleGrain() {
        settings.setFilmGrain(!settings.filmGrain());
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
     * A row whose value is dragged on a bar or stepped with Left/Right.
     *
     * Enter does nothing on these rows: the old click-to-cycle wrapped from full straight to silent,
     * which is the thing the bar replaces.
     */
    private MenuButton sliderRow(double min, double max, double dragStep,
                                 DoubleSupplier get, DoubleConsumer set) {
        MenuButton row = new MenuButton("", () -> { });
        LevelBar bar = new LevelBar(min, max, dragStep, value -> {
            set.accept(value);
            refreshLabels();
        });
        row.setTrailing(bar, direction -> {
            // Hundredths, so repeated key steps do not drift off the grid in float dust.
            double next = Math.round((get.getAsDouble() + direction * KEY_STEP) * 100) / 100.0;
            set.accept(Math.max(min, Math.min(max, next)));
            refreshLabels();
        });
        barRefreshers.add(() -> bar.setValue(get.getAsDouble()));
        return row;
    }

    private void refreshLabels() {
        musicRow.setRow("Music", percent(settings.musicVolume()));
        sfxRow.setRow("Sound FX", percent(settings.sfxVolume()));
        difficultyRow.setRow("Difficulty", settings.difficulty().label());
        reducedFlashRow.setRow("Flashing", settings.reducedFlash() ? "Reduced" : "Full");
        cinematicRow.setRow("Lighting", settings.cinematic() ? "Cinematic" : "Flat");
        resolutionRow.setRow("Resolution", SceneRouter.resolutionLabel(settings.windowHeight()));
        grainRow.setRow("Film grain", settings.filmGrain() ? "On" : "Off");
        controllerRow.setRow("Controller", settings.gamepadEnabled() ? "On" : "Off");
        analogRow.setRow("Stick mode", settings.gamepadAnalog() ? "Analog" : "Digital");
        for (int slot = 0; slot < PLAYERS; slot++) {
            int player = slot + 1;
            fireButtonRow[slot].setRow(
                    "P" + player + " fire", settings.gamepadFireButton(player).label());
            pauseButtonRow[slot].setRow(
                    "P" + player + " pause", settings.gamepadPauseButton(player).label());
            sensitivityRow[slot].setRow(
                    "P" + player + " stick", percent(settings.gamepadSensitivity(player)));
        }
        deadzoneRow.setRow("Deadzone", percent(settings.gamepadDeadzone()));
        padStatusRow.setRow("Pads", padStatus == null ? "--" : padStatus.get());
        barRefreshers.forEach(Runnable::run);
    }

    private static String percent(double value) {
        String text = Math.round(value * 100) + "%";
        return text;
    }
}

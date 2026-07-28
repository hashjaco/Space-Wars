package com.hashimjacobs.spacecase.scene;

import javafx.geometry.Pos;
import javafx.scene.layout.VBox;

import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.prefs.Difficulty;
import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * The settings controls, as a reusable node so the start menu and the pause overlay show the same
 * thing rather than each growing its own copy.
 */
final class SettingsPanel extends VBox {

    private static final double VOLUME_STEP = 0.1;

    private final Settings settings;
    private final SoundBank sounds;
    private final MenuButton musicRow;
    private final MenuButton sfxRow;
    private final MenuButton difficultyRow;

    SettingsPanel(Settings settings, SoundBank sounds, Runnable onBack) {
        this.settings = settings;
        this.sounds = sounds;

        musicRow = new MenuButton("", this::cycleMusicVolume);
        sfxRow = new MenuButton("", this::cycleSfxVolume);
        difficultyRow = new MenuButton("", this::cycleDifficulty);
        MenuButton backRow = new MenuButton("Back", () -> {
            settings.save();
            onBack.run();
        });

        refreshLabels();

        setAlignment(Pos.CENTER);
        getChildren().add(new MenuPanel(musicRow, sfxRow, difficultyRow, backRow));
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
    }

    private static String percent(double value) {
        String text = Math.round(value * 100) + "%";
        return text;
    }
}

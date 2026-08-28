package com.hashimjacobs.spacecase.scene;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import com.hashimjacobs.spacecase.ui.Tokens;
import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.engine.GameLoop;
import com.hashimjacobs.spacecase.engine.InputState;
import com.hashimjacobs.spacecase.engine.PadState;
import com.hashimjacobs.spacecase.engine.Renderer;
import com.hashimjacobs.spacecase.engine.RoundResult;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.prefs.HighScores;
import com.hashimjacobs.spacecase.prefs.Pilots;
import com.hashimjacobs.spacecase.prefs.SaveGames;
import com.hashimjacobs.spacecase.prefs.SaveSlot;
import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * A round in progress: the canvas the world draws onto, plus the pause overlay stacked above it.
 */
final class GameScreen {

    private final StackPane root;
    private final VBox pauseLayer;
    private final VBox settingsLayer;
    private final VBox saveLayer;
    private final InputState input = new InputState();
    private final GameLoop loop;
    private final SaveGames saves;
    private final MenuButton[] slotButtons = new MenuButton[SaveGames.SLOTS];
    private MenuNavigator pauseNavigator;
    private MenuNavigator settingsNavigator;
    private SettingsPanel settingsPanel;
    private MenuNavigator saveNavigator;

    /**
     * The loop this screen drives, for the router to wire a networked game onto.
     *
     * Package-private: {@code SceneRouter} is the only caller and lives here too, so a networked
     * run can reach {@code setStepGate}, {@code setIntentSource}, {@code setLevelHandshake},
     * {@code setTickObserver} and {@code setLocalSeat} without any of that becoming public API.
     */
    GameLoop loop() {
        return loop;
    }

    GameScreen(GameMode mode, Settings settings, SoundBank sounds, Pilots pilots,
               HighScores highScores, SaveGames saves, Random random, SaveSlot resume,
               Runnable onQuitToMenu, Consumer<RoundResult> onRoundOver,
               Supplier<String> padStatus) {
        this(mode, settings, sounds, pilots, highScores, saves, random, resume,
                onQuitToMenu, onRoundOver, padStatus, false);
    }

    /** @param seats how many ships to field; an online room decides it, not the mode */
    GameScreen(GameMode mode, Settings settings, SoundBank sounds, Pilots pilots,
               HighScores highScores, SaveGames saves, Random random, SaveSlot resume,
               Runnable onQuitToMenu, Consumer<RoundResult> onRoundOver,
               Supplier<String> padStatus, int seats) {
        this(mode, settings, sounds, pilots, highScores, saves, random, resume,
                onQuitToMenu, onRoundOver, padStatus, false, seats);
    }

    /** @param endless true for the post-campaign run, which ignores galaxy borders */
    GameScreen(GameMode mode, Settings settings, SoundBank sounds, Pilots pilots,
               HighScores highScores, SaveGames saves, Random random, SaveSlot resume,
               Runnable onQuitToMenu, Consumer<RoundResult> onRoundOver,
               Supplier<String> padStatus, boolean endless) {
        this(mode, settings, sounds, pilots, highScores, saves, random, resume, onQuitToMenu,
                onRoundOver, padStatus, endless, mode.rules().playerCount());
    }

    GameScreen(GameMode mode, Settings settings, SoundBank sounds, Pilots pilots,
               HighScores highScores, SaveGames saves, Random random, SaveSlot resume,
               Runnable onQuitToMenu, Consumer<RoundResult> onRoundOver,
               Supplier<String> padStatus, boolean endless, int seats) {
        Canvas canvas = new Canvas(GameConfig.WIDTH, GameConfig.HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        Renderer renderer = new Renderer(gc, settings);

        this.saves = saves;
        this.loop = new GameLoop(mode, renderer, input, sounds, settings, pilots, random,
                onRoundOver, highScores, saves, resume, endless, seats);
        this.pauseLayer = buildPauseLayer(onQuitToMenu);
        this.settingsLayer = buildSettingsLayer(settings, sounds, padStatus);
        this.saveLayer = buildSaveLayer();

        root = new StackPane(canvas, pauseLayer, settingsLayer, saveLayer);
        root.setPrefSize(GameConfig.WIDTH, GameConfig.HEIGHT);
        root.setStyle("-fx-background-color: black;");

        input.setOnPausePressed(this::togglePause);
    }

    private VBox buildPauseLayer(Runnable onQuitToMenu) {
        MenuButton back = new MenuButton("Back", this::resume);
        MenuButton saveButton = new MenuButton("Save Game", this::showSaveMenu);
        MenuButton settingsButton = new MenuButton("Settings", this::showSettings);
        MenuButton quit = new MenuButton("Quit Game", () -> {
            loop.stop();
            onQuitToMenu.run();
        });
        MenuPanel panel = new MenuPanel(back, saveButton, settingsButton, quit);
        pauseNavigator = panel.navigator();
        pauseNavigator.setOnBack(this::resume);

        VBox layer = new VBox(16);
        layer.setAlignment(Pos.CENTER);
        layer.getChildren().addAll(new MenuTitle("Paused", 38, 380, 62), panel);
        layer.setBackground(veil());
        layer.setVisible(false);
        return layer;
    }

    private VBox buildSettingsLayer(Settings settings, SoundBank sounds,
                                    Supplier<String> padStatus) {
        VBox layer = new VBox(16);
        layer.setAlignment(Pos.CENTER);
        SettingsPanel panel = new SettingsPanel(settings, sounds, padStatus, this::showPauseMenu);
        this.settingsPanel = panel;
        settingsNavigator = panel.navigator(this::showPauseMenu);
        layer.getChildren().addAll(new MenuTitle("Settings", 38, 380, 62), panel);
        layer.setBackground(veil());
        layer.setVisible(false);
        return layer;
    }

    /**
     * The three manual slots.
     *
     * Rows are labelled from what is in them, so no naming screen is needed and the choice to
     * overwrite is already an informed one.
     */
    private VBox buildSaveLayer() {
        MenuButton[] rows = new MenuButton[SaveGames.SLOTS + 1];
        for (int i = 0; i < SaveGames.SLOTS; i++) {
            int number = i + 1;
            slotButtons[i] = new MenuButton("Slot " + (i + 1), "", () -> {
                saves.save(number, loop.checkpoint());
                showPauseMenu();
            });
            rows[i] = slotButtons[i];
        }
        rows[SaveGames.SLOTS] = new MenuButton("Back", this::showPauseMenu);

        MenuPanel panel = new MenuPanel(rows);
        saveNavigator = panel.navigator();
        saveNavigator.setOnBack(this::showPauseMenu);

        VBox layer = new VBox(16);
        layer.setAlignment(Pos.CENTER);
        layer.getChildren().addAll(new MenuTitle("Save Game", 38, 380, 62), panel,
                MenuScreen.caption("Saves the start of this level", 12, Tokens.TEXT_DIM));
        layer.setBackground(veil());
        layer.setVisible(false);
        return layer;
    }

    private void showSaveMenu() {
        // Refreshed on open rather than at build time: a slot may have been written since.
        for (int i = 0; i < SaveGames.SLOTS; i++) {
            String held = saves.slot(i + 1).map(SaveSlot::describe).orElse("empty");
            slotButtons[i].setDetail(held);
        }
        pauseLayer.setVisible(false);
        settingsLayer.setVisible(false);
        saveLayer.setVisible(true);
        input.setMenuRouter(saveNavigator::handleKey);
    }

    private static Background veil() {
        BackgroundFill fill = new BackgroundFill(Tokens.veil(0.72), null, null);
        Background background = new Background(fill);
        return background;
    }

    /** Must run after the scene is attached to the window, since it listens for focus loss. */
    void attachInput(Scene scene) {
        input.attachTo(scene);
    }

    void start() {
        loop.start();
    }

    void stop() {
        loop.stop();
        input.detach();
    }

    private void togglePause() {
        if (settingsLayer.isVisible() || saveLayer.isVisible()) {
            showPauseMenu();
            return;
        }
        // Between levels the loop is running its own sequence; pausing into it would strand the
        // players mid-flight with a menu over a screen they cannot act on.
        if (!loop.isPaused() && !loop.isPausable()) {
            return;
        }
        if (loop.isPaused()) {
            resume();
        } else {
            pause();
        }
    }

    private void pause() {
        loop.setPaused(true);
        showPauseMenu();
    }

    private void resume() {
        pauseLayer.setVisible(false);
        settingsLayer.setVisible(false);
        saveLayer.setVisible(false);
        input.setMenuRouter(null);
        loop.setPaused(false);
    }

    private void showSettings() {
        pauseLayer.setVisible(false);
        settingsPanel.refresh();
        settingsLayer.setVisible(true);
        input.setMenuRouter(settingsNavigator::handleKey);
    }

    private void showPauseMenu() {
        settingsLayer.setVisible(false);
        saveLayer.setVisible(false);
        pauseLayer.setVisible(true);
        input.setMenuRouter(pauseNavigator::handleKey);
    }

    StackPane root() {
        return root;
    }

    /** Hands the loop the pad reader, for analog stick movement. */
    void setSticks(IntFunction<PadState> sticks) {
        loop.setSticks(sticks);
    }
}

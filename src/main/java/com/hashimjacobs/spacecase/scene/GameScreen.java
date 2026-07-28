package com.hashimjacobs.spacecase.scene;

import java.util.Random;
import java.util.function.Consumer;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.engine.GameLoop;
import com.hashimjacobs.spacecase.engine.InputState;
import com.hashimjacobs.spacecase.engine.Renderer;
import com.hashimjacobs.spacecase.engine.RoundResult;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * A round in progress: the canvas the world draws onto, plus the pause overlay stacked above it.
 */
final class GameScreen {

    private final StackPane root;
    private final VBox pauseLayer;
    private final VBox settingsLayer;
    private final InputState input = new InputState();
    private final GameLoop loop;
    private MenuNavigator pauseNavigator;
    private MenuNavigator settingsNavigator;

    GameScreen(GameMode mode, Settings settings, SoundBank sounds, Random random,
               Runnable onQuitToMenu, Consumer<RoundResult> onRoundOver) {
        Canvas canvas = new Canvas(GameConfig.WIDTH, GameConfig.HEIGHT);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        Renderer renderer = new Renderer(gc);

        this.loop = new GameLoop(mode, renderer, input, sounds, settings, random, onRoundOver);
        this.pauseLayer = buildPauseLayer(onQuitToMenu);
        this.settingsLayer = buildSettingsLayer(settings, sounds);

        root = new StackPane(canvas, pauseLayer, settingsLayer);
        root.setPrefSize(GameConfig.WIDTH, GameConfig.HEIGHT);
        root.setStyle("-fx-background-color: black;");

        input.setOnPausePressed(this::togglePause);
    }

    private VBox buildPauseLayer(Runnable onQuitToMenu) {
        MenuButton back = new MenuButton("Back", this::resume);
        MenuButton settingsButton = new MenuButton("Settings", this::showSettings);
        MenuButton quit = new MenuButton("Quit Game", () -> {
            loop.stop();
            onQuitToMenu.run();
        });
        MenuPanel panel = new MenuPanel(back, settingsButton, quit);
        pauseNavigator = panel.navigator();
        pauseNavigator.setOnBack(this::resume);

        VBox layer = new VBox(16);
        layer.setAlignment(Pos.CENTER);
        layer.getChildren().addAll(new MenuTitle("Paused", 38, 380, 62), panel);
        layer.setBackground(veil());
        layer.setVisible(false);
        return layer;
    }

    private VBox buildSettingsLayer(Settings settings, SoundBank sounds) {
        VBox layer = new VBox(16);
        layer.setAlignment(Pos.CENTER);
        SettingsPanel panel = new SettingsPanel(settings, sounds, this::showPauseMenu);
        settingsNavigator = panel.navigator(this::showPauseMenu);
        layer.getChildren().addAll(new MenuTitle("Settings", 38, 380, 62), panel);
        layer.setBackground(veil());
        layer.setVisible(false);
        return layer;
    }

    private static Background veil() {
        BackgroundFill fill = new BackgroundFill(Color.color(0, 0, 0, 0.72), null, null);
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
        if (settingsLayer.isVisible()) {
            showPauseMenu();
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
        input.setMenuRouter(null);
        loop.setPaused(false);
    }

    private void showSettings() {
        pauseLayer.setVisible(false);
        settingsLayer.setVisible(true);
        input.setMenuRouter(settingsNavigator::handleKey);
    }

    private void showPauseMenu() {
        settingsLayer.setVisible(false);
        pauseLayer.setVisible(true);
        input.setMenuRouter(pauseNavigator::handleKey);
    }

    StackPane root() {
        return root;
    }
}

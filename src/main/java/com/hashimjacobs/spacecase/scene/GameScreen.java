package com.hashimjacobs.spacecase.scene;

import java.util.Random;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Supplier;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.transform.Scale;

import com.hashimjacobs.spacecase.ui.Tokens;
import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.OnAndroid;
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

        // Over the arena and transparent across it: the room either side of the arena on a screen
        // wider than it. Empty, and the renderer told there are none, at the arena's own shape.
        Canvas bars = new Canvas(0, 0);
        bars.setMouseTransparent(true);
        Scale barShrink = new Scale(1, 1);
        bars.getTransforms().add(barShrink);
        Group barGroup = new Group(bars);

        root = new StackPane(sharp(canvas), barGroup, pauseLayer, settingsLayer, saveLayer);
        root.setPrefSize(GameConfig.WIDTH, GameConfig.HEIGHT);
        root.setStyle("-fx-background-color: black;");

        // Deferred, never run inside the listener: both properties change in the middle of a layout
        // pass, and resizing a canvas there changes bounds the pass has already read. On the Shield
        // that left the whole game screen drawn at five eighths size until something else -- hiding
        // and showing a node was enough -- forced a clean layout.
        if (OnAndroid.ACTIVE) {
            probe(canvas, barGroup);
        }
        Runnable fitBars = () -> javafx.application.Platform.runLater(() -> {
            fitBars(bars, barShrink, barGroup, renderer);
            root.requestLayout();
        });
        root.widthProperty().addListener((observable, old, width) -> fitBars.run());
        barGroup.localToSceneTransformProperty().addListener((observable, old, transform) -> fitBars.run());

        input.setOnPausePressed(this::togglePause);
    }

    /**
     * Keeps the canvas rasterizing at the size it is shown rather than at 996x864.
     *
     * The router scales the whole screen to fit the window, and a canvas scaled as a node is a
     * stretched bitmap. So the canvas grows by that same factor, shrinks back by a transform the
     * Group hides from layout, and the renderer's base transform scales every draw call up to
     * match: it still draws in arena units and never knows. Safe because the renderer balances
     * every save with a restore and never calls setTransform itself.
     */
    private static Group sharp(Canvas canvas) {
        Scale shrink = new Scale(1, 1);
        canvas.getTransforms().add(shrink);
        Group group = new Group(canvas);
        if (OnAndroid.ACTIVE) {
            // Measured on a Shield with an on-device probe: with the arena canvas at its full
            // 996x864, which JavaFX backs with a 1992x1728 texture on that 2x-density screen, the
            // whole game screen was drawn at five eighths size in the top-left corner. With the
            // canvas at half that, drawn into at half scale and shown at double, everything was the
            // right size -- and nothing else tried changed it. So Android gets the half-size canvas:
            // 996x864 real pixels shown across 1245x1080 of a 1080p surface, a little soft.
            canvas.setWidth(GameConfig.WIDTH / 2);
            canvas.setHeight(GameConfig.HEIGHT / 2);
            shrink.setX(2);
            shrink.setY(2);
            canvas.getGraphicsContext2D().setTransform(0.5, 0, 0, 0.5, 0, 0);
            return group;
        }
        group.localToSceneTransformProperty().addListener((observable, old, transform) -> {
            double factor = transform.getMxx();     // the router scales uniformly and never rotates
            if (factor <= 0 || !Double.isFinite(factor) || factor == 1 / shrink.getX()) {
                return;
            }
            canvas.setWidth(GameConfig.WIDTH * factor);
            canvas.setHeight(GameConfig.HEIGHT * factor);
            shrink.setX(1 / factor);
            shrink.setY(1 / factor);
            canvas.getGraphicsContext2D().setTransform(factor, 0, 0, factor, 0, 0);
        });
        return group;
    }

    /**
     * Sizes the side-bar canvas to the room the router gave this screen beyond the arena, at the
     * scale the screen is shown at -- the same sharpness bargain {@link #sharp} makes. Its origin
     * is moved to the arena's top-left corner, so the renderer draws the bars in arena units with
     * the left one at negative x.
     */
    private void fitBars(Canvas bars, Scale shrink, Group group, Renderer renderer) {
        double factor = group.getLocalToSceneTransform().getMxx();
        if (factor <= 0 || !Double.isFinite(factor)) {
            factor = 1;
        }
        if (OnAndroid.ACTIVE) {
            // Half size, like the arena canvas: at full size this one is a 3072x1728 texture on a
            // 1920x1080 surface, redrawn every frame.
            factor = 0.5;
        }
        double side = Math.max(0, Math.floor((root.getWidth() - GameConfig.WIDTH) / 2));
        double width = side < 1 ? 0 : (GameConfig.WIDTH + 2 * side) * factor;
        double height = side < 1 ? 0 : GameConfig.HEIGHT * factor;
        if (bars.getWidth() == width && bars.getHeight() == height) {
            return;
        }
        bars.setWidth(width);
        bars.setHeight(height);
        shrink.setX(1 / factor);
        shrink.setY(1 / factor);
        GraphicsContext gc = bars.getGraphicsContext2D();
        gc.setTransform(factor, 0, 0, factor, side * factor, 0);
        renderer.setBars(side < 1 ? null : gc, side);
    }

    /** TEMPORARY: steps the level through {@link com.hashimjacobs.spacecase.Probe}'s switches. */
    private void probe(Canvas canvas, Group barGroup) {
        String[] names = {"level as played", "no drawing at all", "drawing, no HUD", "drawing, no side bars",
            "drawing, canvases hidden", "level as played again"};
        Runnable[] steps = {
            () -> { },
            () -> com.hashimjacobs.spacecase.Probe.noDraw = true,
            () -> { com.hashimjacobs.spacecase.Probe.noDraw = false; com.hashimjacobs.spacecase.Probe.noHud = true; },
            () -> { com.hashimjacobs.spacecase.Probe.noHud = false; com.hashimjacobs.spacecase.Probe.noBars = true; },
            () -> { com.hashimjacobs.spacecase.Probe.noBars = false; canvas.setVisible(false); barGroup.setVisible(false); },
            () -> { canvas.setVisible(true); barGroup.setVisible(true); },
        };
        javafx.animation.Timeline timeline = new javafx.animation.Timeline();
        for (int i = 0; i < steps.length; i++) {
            int step = i;
            timeline.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(6 + 7 * i), event -> {
                steps[step].run();
                com.hashimjacobs.spacecase.Probe.phase(names[step]);
            }));
        }
        timeline.getKeyFrames().add(new javafx.animation.KeyFrame(javafx.util.Duration.seconds(6 + 7 * steps.length),
                event -> com.hashimjacobs.spacecase.Probe.phase("after the probe")));
        timeline.play();
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
        layer.getChildren().addAll(MenuTitle.plate("PAUSED", Tokens.SIZE_OVERLAY_TITLE), panel);
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
        layer.getChildren().addAll(MenuTitle.plate("SETTINGS", Tokens.SIZE_OVERLAY_TITLE), panel);
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
        layer.getChildren().addAll(MenuTitle.plate("SAVE GAME", Tokens.SIZE_OVERLAY_TITLE), panel,
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

    /**
     * The app has been sent to the background: pause as if the player had, and stay paused. Coming
     * back finds the pause menu up, so nobody returns to a ship already under fire.
     *
     * @return false between levels, where the loop is running its own sequence and cannot be paused
     */
    boolean pauseForBackground() {
        if (loop.isPaused()) {
            return true;
        }
        if (!loop.isPausable()) {
            return false;
        }
        pause();
        return true;
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

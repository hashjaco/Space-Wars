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

        // Over the arena and transparent across it: the room around the arena on a screen that is
        // not its shape. Empty, and the renderer told there are none, where the arena fills it.
        Canvas bars = new Canvas(0, 0);
        bars.setMouseTransparent(true);
        Scale barShrink = new Scale(1, 1);
        bars.getTransforms().add(barShrink);
        Group barGroup = new Group(bars);
        Scale arenaShrink = new Scale(1, 1);
        canvas.getTransforms().add(arenaShrink);
        Group arenaGroup = new Group(canvas);

        root = new StackPane(arenaGroup, barGroup, pauseLayer, settingsLayer, saveLayer);
        root.setPrefSize(GameConfig.WIDTH, GameConfig.HEIGHT);
        root.setStyle("-fx-background-color: black;");

        // Deferred, never run inside the listener: both properties change in the middle of a layout
        // pass, and resizing a canvas there changes bounds the pass has already read. On an Android
        // TV that left the whole game screen drawn at five eighths size until something else forced
        // a clean layout.
        Runnable fit = () -> javafx.application.Platform.runLater(() -> {
            fit(canvas, arenaShrink, bars, barShrink, barGroup, renderer);
            root.requestLayout();
        });
        root.widthProperty().addListener((observable, old, width) -> fit.run());
        barGroup.localToSceneTransformProperty().addListener((observable, old, transform) -> fit.run());
        // A side-view level is flown in a wider arena than a top-down one, so the fit changes
        // between levels as well as with the window.
        renderer.setOnArenaResized(fit);

        input.setOnPausePressed(this::togglePause);
    }

    /**
     * Fits the arena to the room the router gave this screen, and the bars to what is left over.
     *
     * The arena is {@link Renderer#arenaWidth} by 864 in its own units: 996 wide for a top-down
     * level, 1536 for a side-view one. It is shown as large as fits. Wider room than that leaves
     * bars at the sides; a 1536 arena in narrower room is shrunk to the width and leaves bands
     * above and below. Never both.
     *
     * Both canvases rasterize at the size they are shown rather than being stretched: the router
     * scales the whole screen to fit the window, and a canvas scaled as a node is a stretched
     * bitmap. So each grows by the scale it is shown at, shrinks back by a transform its Group
     * hides from layout, and carries a base transform that scales every draw call to match -- the
     * renderer still draws in arena units and never knows. Safe because the renderer balances every
     * save with a restore and never calls setTransform itself. The bar canvas's origin is moved to
     * the arena's top-left corner, so the bars are at negative coordinates and past the far edges.
     */
    private void fit(Canvas arena, Scale arenaShrink, Canvas bars, Scale barShrink, Group group,
                     Renderer renderer) {
        double factor = group.getLocalToSceneTransform().getMxx();   // the router scales uniformly
        if (factor <= 0 || !Double.isFinite(factor)) {
            factor = 1;
        }
        double arenaWidth = renderer.arenaWidth();
        double room = Math.max(GameConfig.WIDTH, root.getWidth());
        double shown = Math.min(1, room / arenaWidth);      // stage units per arena unit
        double pixels = factor * shown;                     // canvas pixels per arena unit
        double side = shown < 1 ? 0 : Math.max(0, Math.floor((room - arenaWidth) / 2));
        double band = (GameConfig.HEIGHT / shown - GameConfig.HEIGHT) / 2;
        boolean barred = side >= 1 || band >= 1;

        double width = arenaWidth * pixels;
        double height = GameConfig.HEIGHT * pixels;
        double barWidth = barred ? (arenaWidth + 2 * side) * pixels : 0;
        double barHeight = barred ? (GameConfig.HEIGHT + 2 * band) * pixels : 0;
        if (arena.getWidth() == width && arena.getHeight() == height
                && bars.getWidth() == barWidth && bars.getHeight() == barHeight) {
            return;
        }
        arena.setWidth(width);
        arena.setHeight(height);
        arenaShrink.setX(1 / factor);
        arenaShrink.setY(1 / factor);
        arena.getGraphicsContext2D().setTransform(pixels, 0, 0, pixels, 0, 0);

        bars.setWidth(barWidth);
        bars.setHeight(barHeight);
        barShrink.setX(1 / factor);
        barShrink.setY(1 / factor);
        GraphicsContext gc = bars.getGraphicsContext2D();
        gc.setTransform(pixels, 0, 0, pixels, side * pixels, band * pixels);
        renderer.setBars(barred ? gc : null, side, band);
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
        input.setMenuRouter(settingsPanel::handleKey);
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

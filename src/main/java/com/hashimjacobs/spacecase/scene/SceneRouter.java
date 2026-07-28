package com.hashimjacobs.spacecase.scene;

import java.util.List;
import java.util.Random;

import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.MusicCue;
import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.asset.SoundFx;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.engine.RoundResult;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.prefs.HighScores;
import com.hashimjacobs.spacecase.prefs.Settings;

/**
 * Owns the window and swaps whole screens through it.
 *
 * This replaces the GameStateMachine / GameState / GameIntro / GameStartMenu / GamePlay / GameEnd
 * stubs, which declared a screen-flow abstraction that was never implemented or called.
 *
 * Every screen is laid out at a fixed 996x864 and then scaled to fit the window, so the arena keeps
 * its aspect ratio at any size and the simulation never has to know the window changed.
 */
public final class SceneRouter {

    private final Stage stage;
    private final Settings settings;
    private final SoundBank sounds;
    private final HighScores highScores;
    private final Random random = new Random();

    /** Black surround; whatever is left over when the window is not exactly 996x864. */
    private final StackPane shell = new StackPane();
    /** Holds the current screen at its natural size; scaled as a whole to fit the shell. */
    private final Group stage2d = new Group();

    private GameScreen activeGame;

    public SceneRouter(Stage stage, Settings settings, SoundBank sounds, HighScores highScores) {
        this.stage = stage;
        this.settings = settings;
        this.sounds = sounds;
        this.highScores = highScores;

        shell.setStyle("-fx-background-color: black;");
        shell.setAlignment(Pos.CENTER);
        shell.getChildren().add(stage2d);
    }

    public void showStartMenu() {
        stopActiveGame();
        sounds.playMusic(MusicCue.MENU);

        MenuButton single = new MenuButton("Single Player", () -> startGame(GameMode.SOLO));
        MenuButton multi = new MenuButton("Multiplayer", this::showMultiplayerMenu);
        MenuButton settingsButton = new MenuButton("Settings", () -> showSettings(this::showStartMenu));
        MenuButton help = new MenuButton("Help", this::showHelp);
        MenuButton exit = new MenuButton("Exit", this::exit);
        MenuPanel panel = new MenuPanel(single, multi, settingsButton, help, exit);

        HBox ships = new HBox(28,
                MenuScreen.decal(Sprite.P1_STRAIGHT, 54),
                MenuScreen.decal(Sprite.P2_STRAIGHT, 54));
        ships.setAlignment(Pos.CENTER);

        StackPane root = MenuScreen.build("SPACE CASE",
                ships,
                panel,
                MenuScreen.caption("Best solo run: " + highScores.best(GameMode.SOLO), 12,
                        Color.web("#6d7a90")),
                MenuScreen.caption("↑↓ move    Enter select    F11 fullscreen", 11,
                        Color.web("#4c586c")));
        show(root, panel.navigator());
    }

    public void showMultiplayerMenu() {
        MenuButton coop = new MenuButton("Co-op  (survive together)", () -> startGame(GameMode.COOP));
        MenuButton battle = new MenuButton("Battle  (face each other)", () -> startGame(GameMode.BATTLE));
        MenuButton back = new MenuButton("Back", this::showStartMenu);
        MenuPanel panel = new MenuPanel(coop, battle, back);

        MenuNavigator navigator = panel.navigator();
        navigator.setOnBack(this::showStartMenu);

        StackPane root = MenuScreen.build("MULTIPLAYER",
                panel,
                MenuScreen.caption("Two players, one keyboard", 12, Color.web("#6d7a90")));
        show(root, navigator);
    }

    public void showSettings(Runnable onBack) {
        SettingsPanel panel = new SettingsPanel(settings, sounds, onBack);
        StackPane root = MenuScreen.build("SETTINGS", panel);
        show(root, panel.navigator(onBack));
    }

    public void showHelp() {
        VBox lines = new VBox(7);
        lines.setAlignment(Pos.CENTER_LEFT);
        List<String> rows = List.of(
                "PLAYER 1        W  A  S  D          fire: SHIFT",
                "PLAYER 2        arrow keys          fire: COMMA",
                "",
                "Single player accepts either WASD or the arrow keys,",
                "and fires with SHIFT or SPACE.",
                "",
                "ESCAPE          pause / resume",
                "F11             fullscreen",
                "MENUS           arrows or W/S, Enter to choose",
                "",
                "Shoot asteroids and enemy ships for points. Enemies drop",
                "pickups: tri-shot, mega laser, shield, speed, health or",
                "an extra life. A boss arrives every fourth wave and",
                "changes attack pattern as you wear it down.",
                "",
                "In Battle, player two starts at the top facing down and",
                "friendly fire is on. Last player with lives wins.");
        for (String row : rows) {
            lines.getChildren().add(MenuScreen.caption(row, 13, Color.web("#a9b6c9")));
        }

        MenuButton back = new MenuButton("Back", this::showStartMenu);
        MenuPanel panel = new MenuPanel(back);
        MenuNavigator navigator = panel.navigator();
        navigator.setOnBack(this::showStartMenu);

        StackPane root = MenuScreen.build("HELP", lines, panel);
        show(root, navigator);
    }

    public void startGame(GameMode mode) {
        stopActiveGame();
        sounds.playMusic(mode.music());

        GameScreen screen = new GameScreen(mode, settings, sounds, random,
                this::showStartMenu, this::showGameOver);
        activeGame = screen;
        show(screen.root(), null);
        // The scene must already be on the stage before input attaches; it listens for focus loss.
        screen.attachInput(stage.getScene());
        screen.start();
    }

    private void showGameOver(RoundResult result) {
        stopActiveGame();
        sounds.playMusic(MusicCue.MENU);
        sounds.play(SoundFx.GAME_OVER);

        boolean record = highScores.submit(result.mode(), result.bestScore());
        String heading = result.winningPlayerNumber() > 0
                ? "PLAYER " + result.winningPlayerNumber() + " WINS"
                : "GAME OVER";

        VBox summary = new VBox(6);
        summary.setAlignment(Pos.CENTER);
        summary.getChildren().add(MenuScreen.caption(result.mode().label(), 14, Color.web("#8b98ad")));
        for (int i = 0; i < result.scores().size(); i++) {
            String row = "Player " + (i + 1) + " score   " + result.scores().get(i);
            summary.getChildren().add(MenuScreen.caption(row, 15, Color.WHITE));
        }
        summary.getChildren().add(MenuScreen.caption("Waves survived   " + result.wavesSurvived(),
                13, Color.web("#8b98ad")));
        if (record) {
            summary.getChildren().add(MenuScreen.caption("NEW BEST", 15, Color.web("#0ec417")));
        } else {
            summary.getChildren().add(MenuScreen.caption(
                    "Best   " + highScores.best(result.mode()), 13, Color.web("#6d7a90")));
        }

        MenuButton again = new MenuButton("Play Again", () -> startGame(result.mode()));
        MenuButton menu = new MenuButton("Main Menu", this::showStartMenu);
        MenuPanel panel = new MenuPanel(again, menu);
        MenuNavigator navigator = panel.navigator();
        navigator.setOnBack(this::showStartMenu);

        StackPane root = MenuScreen.build(heading, summary, panel);
        show(root, navigator);
    }

    public void exit() {
        stopActiveGame();
        settings.save();
        stage.close();
    }

    private void stopActiveGame() {
        if (activeGame != null) {
            activeGame.stop();
            activeGame = null;
        }
    }

    /**
     * Installs a screen. A navigator, when supplied, receives key presses; game screens pass null
     * because {@link com.hashimjacobs.spacecase.engine.InputState} takes the keyboard instead.
     */
    private void show(Parent root, MenuNavigator navigator) {
        stage2d.getChildren().setAll(root);

        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(shell, GameConfig.WIDTH, GameConfig.HEIGHT, Color.BLACK);
            stage.setScene(scene);
            installFullscreenShortcut(scene);
            trackWindowSize(scene);
        }
        scene.setOnKeyReleased(null);
        if (navigator == null) {
            scene.setOnKeyPressed(null);
            return;
        }
        MenuNavigator active = navigator;
        scene.setOnKeyPressed(event -> {
            if (active.handleKey(event.getCode())) {
                event.consume();
            }
        });
    }

    /**
     * A filter rather than a handler, so fullscreen works on every screen without each one having to
     * cooperate -- filters run before the scene's key handler consumes anything.
     */
    private void installFullscreenShortcut(Scene scene) {
        scene.addEventFilter(KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() != KeyCode.F11) {
                return;
            }
            stage.setFullScreen(!stage.isFullScreen());
            event.consume();
        });
    }

    /** Scales the whole screen uniformly so 996x864 fits the window without distortion. */
    private void trackWindowSize(Scene scene) {
        scene.widthProperty().addListener((observable, old, width) -> rescale(scene));
        scene.heightProperty().addListener((observable, old, height) -> rescale(scene));
        rescale(scene);
    }

    private void rescale(Scene scene) {
        double factor = Math.min(scene.getWidth() / GameConfig.WIDTH,
                scene.getHeight() / GameConfig.HEIGHT);
        if (factor <= 0 || !Double.isFinite(factor)) {
            return;
        }
        stage2d.setScaleX(factor);
        stage2d.setScaleY(factor);
    }
}

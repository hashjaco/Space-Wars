package com.hashimjacobs.spacecase.scene;

import java.util.List;
import java.util.Random;

import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Stage;

import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.MusicTrack;
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
 */
public final class SceneRouter {

    private final Stage stage;
    private final Settings settings;
    private final SoundBank sounds;
    private final HighScores highScores;
    private final Random random = new Random();

    private GameScreen activeGame;

    public SceneRouter(Stage stage, Settings settings, SoundBank sounds, HighScores highScores) {
        this.stage = stage;
        this.settings = settings;
        this.sounds = sounds;
        this.highScores = highScores;
    }

    public void showStartMenu() {
        stopActiveGame();
        sounds.playMusic(MusicTrack.MAIN);

        MenuButton single = new MenuButton("Single Player", () -> startGame(GameMode.SOLO));
        MenuButton multi = new MenuButton("Multiplayer", this::showMultiplayerMenu);
        MenuButton settingsButton = new MenuButton("Settings", () -> showSettings(this::showStartMenu));
        MenuButton help = new MenuButton("Help", this::showHelp);
        MenuButton exit = new MenuButton("Exit", this::exit);

        HBox ships = new HBox(28,
                MenuScreen.decal(Sprite.P1_STRAIGHT, 54),
                MenuScreen.decal(Sprite.P2_STRAIGHT, 54));
        ships.setAlignment(Pos.CENTER);

        StackPane root = MenuScreen.build("SPACE CASE",
                ships,
                new MenuPanel(single, multi, settingsButton, help, exit),
                MenuScreen.caption("Best solo run: " + highScores.best(GameMode.SOLO), 12,
                        Color.web("#6d7a90")));
        show(root);
    }

    public void showMultiplayerMenu() {
        MenuButton coop = new MenuButton("Co-op  (survive together)", () -> startGame(GameMode.COOP));
        MenuButton battle = new MenuButton("Battle  (face each other)", () -> startGame(GameMode.BATTLE));
        MenuButton back = new MenuButton("Back", this::showStartMenu);

        StackPane root = MenuScreen.build("MULTIPLAYER",
                new MenuPanel(coop, battle, back),
                MenuScreen.caption("Two players, one keyboard", 12, Color.web("#6d7a90")));
        show(root);
    }

    public void showSettings(Runnable onBack) {
        SettingsPanel panel = new SettingsPanel(settings, sounds, onBack);
        StackPane root = MenuScreen.build("SETTINGS", panel);
        show(root);
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
                "",
                "Shoot asteroids and enemy ships for points. Collect",
                "pickups for a tri-shot, a mega laser, a shield, speed,",
                "health or an extra life. A boss arrives every fourth wave.",
                "",
                "In Battle, player two starts at the top facing down and",
                "friendly fire is on. Last player with lives wins.");
        for (String row : rows) {
            lines.getChildren().add(MenuScreen.caption(row, 13, Color.web("#a9b6c9")));
        }

        MenuButton back = new MenuButton("Back", this::showStartMenu);
        StackPane root = MenuScreen.build("HELP", lines, new MenuPanel(back));
        show(root);
    }

    public void startGame(GameMode mode) {
        stopActiveGame();
        sounds.playMusic(mode.music());

        GameScreen screen = new GameScreen(mode, settings, sounds, random,
                this::showStartMenu, this::showGameOver);
        activeGame = screen;
        show(screen.root());
        // The scene must already be on the stage before input attaches; it listens for focus loss.
        screen.attachInput(stage.getScene());
        screen.start();
    }

    private void showGameOver(RoundResult result) {
        stopActiveGame();
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

        StackPane root = MenuScreen.build(heading, summary, new MenuPanel(again, menu));
        show(root);
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

    private void show(Parent root) {
        Scene existing = stage.getScene();
        if (existing == null) {
            Scene scene = new Scene(root, GameConfig.WIDTH, GameConfig.HEIGHT, Color.BLACK);
            stage.setScene(scene);
            return;
        }
        // Reusing the Scene keeps the window from flickering or resizing between screens.
        existing.setOnKeyPressed(null);
        existing.setOnKeyReleased(null);
        existing.setRoot(root);
    }
}

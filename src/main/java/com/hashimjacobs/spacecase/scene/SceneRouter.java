package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

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
import com.hashimjacobs.spacecase.engine.Gamepad;
import com.hashimjacobs.spacecase.engine.RoundResult;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.HighScores;
import com.hashimjacobs.spacecase.prefs.Pilots;
import com.hashimjacobs.spacecase.prefs.SaveGames;
import com.hashimjacobs.spacecase.prefs.SaveSlot;
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
    private final Pilots pilots;
    private final SaveGames saves = SaveGames.load();
    private final Random random = new Random();

    /** Black surround; whatever is left over when the window is not exactly 996x864. */
    private final StackPane shell = new StackPane();
    /** Holds the current screen at its natural size; scaled as a whole to fit the shell. */
    private final Group stage2d = new Group();

    private GameScreen activeGame;
    private Gamepad gamepad;

    public SceneRouter(Stage stage, Settings settings, SoundBank sounds, HighScores highScores,
                       Pilots pilots) {
        this.stage = stage;
        this.settings = settings;
        this.sounds = sounds;
        this.highScores = highScores;
        this.pilots = pilots;

        shell.setStyle("-fx-background-color: black;");
        shell.setAlignment(Pos.CENTER);
        shell.getChildren().add(stage2d);
    }

    public void showStartMenu() {
        stopActiveGame();
        sounds.playMusic(MusicCue.MENU);

        // Continue and Level Select only exist once there is a run to continue, so a first-time
        // player sees the menu they always saw.
        List<MenuButton> rows = new ArrayList<>();
        Optional<SaveSlot> held = saves.checkpoint();
        held.ifPresent(save -> {
            rows.add(new MenuButton("Continue  -  " + save.describe(),
                    () -> startGame(save.mode(), save)));
            rows.add(new MenuButton("Level Select", () -> showLevelSelect(save)));
        });
        rows.add(new MenuButton("Single Player", () -> startGame(GameMode.SOLO)));
        rows.add(new MenuButton("Multiplayer", this::showMultiplayerMenu));
        rows.add(new MenuButton("Load Game", this::showLoadMenu));
        rows.add(new MenuButton("Pilots", this::showPilots));
        rows.add(new MenuButton("Settings", () -> showSettings(this::showStartMenu)));
        rows.add(new MenuButton("Help", this::showHelp));
        rows.add(new MenuButton("Exit", this::exit));
        MenuPanel panel = new MenuPanel(rows.toArray(new MenuButton[0]));

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
        MenuNavigator navigator = panel.navigator();
        show(root, navigator::handleKey);
    }

    /**
     * Replay anywhere you have been, in the ship you have now.
     *
     * Unlocks come off the checkpoint alone rather than a separate furthest-reached key, so there
     * is nothing extra to keep in sync. Once the campaign has been looped, everything is open.
     *
     * ponytail: a plain MenuPanel column. Fits about fifteen levels at 42px a row; past that, wrap
     * the panel in a ScrollPane rather than inventing paging.
     */
    public void showLevelSelect(SaveSlot from) {
        List<MenuButton> rows = new ArrayList<>();
        for (Level level : Level.values()) {
            boolean unlocked = from.loop() > 1 || level.ordinal() <= from.level().ordinal();
            if (!unlocked) {
                continue;
            }
            int best = highScores.best(from.mode(), level);
            String label = level.number() + "   " + level.label()
                    + (best > 0 ? "   best " + best : "");
            rows.add(new MenuButton(label,
                    () -> startGame(from.mode(), SaveSlot.forReplay(from.mode(), level))));
        }
        rows.add(new MenuButton("Back", this::showStartMenu));

        MenuPanel panel = new MenuPanel(rows.toArray(new MenuButton[0]));
        MenuNavigator navigator = panel.navigator();
        navigator.setOnBack(this::showStartMenu);

        StackPane root = MenuScreen.build("LEVEL SELECT", panel,
                MenuScreen.caption("Fly a cleared level again in your current ship. "
                        + "Reach a level to unlock it.", 12, Color.web("#6d7a90")));
        show(root, navigator::handleKey);
    }

    /** The three manual slots, labelled by what is in them. */
    public void showLoadMenu() {
        List<MenuButton> rows = new ArrayList<>();
        for (int number = 1; number <= SaveGames.SLOTS; number++) {
            Optional<SaveSlot> held = saves.slot(number);
            String label = number + "   " + held.map(SaveSlot::describe).orElse("empty");
            // An empty slot does nothing when chosen, which is the right amount of feedback for
            // a row that says "empty".
            Runnable action = held.<Runnable>map(save -> () -> startGame(save.mode(), save))
                    .orElse(() -> {
                    });
            rows.add(new MenuButton(label, action));
        }
        rows.add(new MenuButton("Back", this::showStartMenu));

        MenuPanel panel = new MenuPanel(rows.toArray(new MenuButton[0]));
        MenuNavigator navigator = panel.navigator();
        navigator.setOnBack(this::showStartMenu);

        StackPane root = MenuScreen.build("LOAD GAME", panel,
                MenuScreen.caption("Save to a slot from the pause menu during a run", 12,
                        Color.web("#6d7a90")));
        show(root, navigator::handleKey);
    }

    public void showPilots() {
        NameEntryPanel panel = new NameEntryPanel(pilots, this::showStartMenu);
        StackPane root = MenuScreen.build("PILOTS",
                panel,
                MenuScreen.caption("Names show under your ship. Rank is earned across every run.", 12,
                        Color.web("#6d7a90")));
        show(root, panel::handleKey);
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
        show(root, navigator::handleKey);
    }

    public void showSettings(Runnable onBack) {
        SettingsPanel panel = new SettingsPanel(settings, sounds, onBack);
        StackPane root = MenuScreen.build("SETTINGS", panel);
        show(root, panel.navigator(onBack)::handleKey);
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
                "ESCAPE          pause / resume        F11   fullscreen",
                "MENUS           arrows or W/S, Enter to choose",
                "CONTROLLERS     stick or d-pad, A to fire, Start to pause.",
                "                First pad is player one. Buttons and",
                "                deadzone are in Settings.",
                "",
                "Shoot asteroids and enemy ships for points. Enemies drop",
                "pickups: tri-shot, mega laser, shield, speed, health or an",
                "extra life. Each of the ten levels fields its own defenders",
                "and ends with its own flagship, which changes attack pattern",
                "as you wear it down. Level nine is flown side-on. Kill the",
                "flagship for a debrief, bonuses, rank and the garage.",
                "",
                "SAVING          checkpoints each level. Continue resumes;",
                "                Level Select replays anywhere you reached,",
                "                in the ship you have now. Pause to save to",
                "                one of three slots.",
                "",
                "PILOTS          name both seats from the start menu. Level",
                "                bonuses build a career score and your rank,",
                "                and buy upgrades in the garage.",
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
        show(root, navigator::handleKey);
    }

    public void startGame(GameMode mode) {
        startGame(mode, null);
    }

    /** @param resume a checkpoint or level-select replay to start from, or null for level one */
    public void startGame(GameMode mode, SaveSlot resume) {
        stopActiveGame();
        sounds.playMusic(mode.music());

        GameScreen screen = new GameScreen(mode, settings, sounds, pilots, highScores, saves,
                random, resume, this::showStartMenu, this::showGameOver);
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
        String progress = "Level " + result.level().number() + "   " + result.level().label()
                + "   -   " + result.wavesSurvived() + " waves"
                + (result.loop() > 1 ? "   -   loop " + result.loop() : "");
        summary.getChildren().add(MenuScreen.caption(progress, 13, Color.web("#8b98ad")));
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
        show(root, navigator::handleKey);
    }

    public void exit() {
        stopActiveGame();
        settings.save();
        stage.close();
    }

    /** Shuts SDL and the audio thread down with the window. */
    public void shutdown() {
        if (gamepad != null) {
            gamepad.stop();
            gamepad = null;
        }
        sounds.shutdown();
    }

    private void stopActiveGame() {
        if (activeGame != null) {
            activeGame.stop();
            activeGame = null;
        }
    }

    /**
     * Installs a screen. The handler, when supplied, receives key presses and reports whether it used
     * them; game screens pass null because {@link com.hashimjacobs.spacecase.engine.InputState} takes
     * the keyboard instead.
     *
     * A predicate rather than a {@link MenuNavigator} so a screen can compose one with something else
     * -- the pilots screen puts letter capture in front of the navigator, since W and S are both
     * navigation keys and letters that belong in a name.
     */
    private void show(Parent root, Predicate<KeyCode> keys) {
        stage2d.getChildren().setAll(root);

        Scene scene = stage.getScene();
        if (scene == null) {
            scene = new Scene(shell, GameConfig.WIDTH, GameConfig.HEIGHT, Color.BLACK);
            stage.setScene(scene);
            installFullscreenShortcut(scene);
            trackWindowSize(scene);
            // Feeds this Scene rather than any one screen, so it needs no re-attaching between
            // rounds -- unlike InputState, whose handlers the screens swap.
            gamepad = Gamepad.start(scene, settings);
        }
        scene.setOnKeyReleased(null);
        if (keys == null) {
            scene.setOnKeyPressed(null);
            return;
        }
        scene.setOnKeyPressed(event -> {
            if (keys.test(event.getCode())) {
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

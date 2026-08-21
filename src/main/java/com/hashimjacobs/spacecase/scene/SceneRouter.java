package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.function.Predicate;

import javafx.animation.AnimationTimer;
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

import com.hashimjacobs.spacecase.ui.Tokens;
import com.hashimjacobs.spacecase.GameConfig;
import com.hashimjacobs.spacecase.asset.MusicCue;
import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.asset.SoundFx;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.engine.Gamepad;
import com.hashimjacobs.spacecase.engine.MenuRepeat;
import com.hashimjacobs.spacecase.engine.RoundResult;
import com.hashimjacobs.spacecase.mode.Galaxy;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.ControlAction;
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

    /**
     * The map's idle pulse, while a map is on screen.
     *
     * Stopped in {@link #show} rather than at each place that navigates away, so leaving the map by
     * any route -- Escape, launching a level, the Back row -- cannot leave a timer running against a
     * canvas that is no longer mounted.
     */
    private AnimationTimer mapPulse;

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

        // Continue only exists once there is a run to continue. The map is always offered: a
        // first-time player seeing Verdance with its first level ready is a better opening than a
        // menu entry that appears from nowhere after the first game.
        List<MenuButton> rows = new ArrayList<>();
        Optional<SaveSlot> held = saves.checkpoint();
        held.ifPresent(save -> rows.add(new MenuButton("Continue  -  " + save.describe(),
                () -> startGame(save.mode(), save))));
        rows.add(new MenuButton("Universe Map", () -> showGalaxySelect(GameMode.SOLO)));
        rows.add(new MenuButton("Single Player", () -> startGame(GameMode.SOLO)));
        if (campaignComplete(GameMode.SOLO)) {
            rows.add(new MenuButton("Endless Run", () -> startEndless(GameMode.SOLO)));
        }
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
                        Tokens.TEXT_FAINT),
                MenuScreen.caption("↑↓ move    Enter select    F11 fullscreen", 11,
                        Tokens.TEXT_GHOST));
        MenuNavigator navigator = panel.navigator();
        show(root, navigator::handleKey);
    }

    /**
     * Tier one of the map: which galaxy.
     *
     * A list, not a canvas. Five rows is what {@link MenuPanel} is for, and it brings keyboard,
     * gamepad, mouse, focus painting, the repeat gate and Escape with it. A spatial galaxy chooser
     * would be animation work with no navigational payoff.
     *
     * Locked galaxies are shown with the reason, which is the opposite of what the old level select
     * did -- it omitted anything unreached, so the campaign had no visible shape.
     */
    public void showGalaxySelect(GameMode mode) {
        List<MenuButton> rows = new ArrayList<>();
        for (Galaxy galaxy : Galaxy.values()) {
            boolean open = saves.isGalaxyUnlocked(mode, galaxy);
            int done = saves.clearedCount(mode, galaxy);
            String label = roman(galaxy.number()) + "   " + galaxy.label() + "   ";
            if (!open) {
                // Safe only because SaveGames.isGalaxyUnlocked returns true for ordinal zero
                // unconditionally, so the first galaxy never reaches this branch. That invariant
                // lives in another class; if it ever gains a condition, this reads values()[-1].
                Galaxy before = Galaxy.values()[galaxy.ordinal() - 1];
                label += "locked - clear " + before.label();
            } else if (done == Galaxy.LEVELS_PER_GALAXY) {
                label += "complete";
            } else {
                label += done + "/" + Galaxy.LEVELS_PER_GALAXY + " cleared";
            }
            MenuButton row = new MenuButton(label, () -> showSystemMap(mode, galaxy));
            row.setLocked(!open);
            rows.add(row);
        }
        // Offered here rather than on Settings because this is the screen that shows progress, and
        // because Settings is shared with the pause overlay -- mid-run is the last place to put an
        // erase. Hidden with nothing to erase: a first-time player has no use for it.
        if (saves.clearedMask(mode) != 0) {
            rows.add(new MenuButton("Reset progress", () -> showResetConfirm(mode)));
        }
        rows.add(new MenuButton("Back", this::showStartMenu));

        MenuPanel panel = new MenuPanel(rows.toArray(new MenuButton[0]));
        MenuNavigator navigator = panel.navigator();
        navigator.setOnBack(this::showStartMenu);

        StackPane root = MenuScreen.build("UNIVERSE", panel,
                MenuScreen.caption("Finish a galaxy to open the next one.", 12, Tokens.TEXT_FAINT));
        show(root, navigator::handleKey);
    }

    /**
     * Asks before erasing, with keeping the run focused first.
     *
     * A screen rather than a JavaFX dialog: the game is driven by a pad as much as a keyboard, and
     * MenuPanel already brings pad, mouse, focus painting and Escape with it. Cancel sits in row
     * one because MenuPanel focuses row one, so the fast reflex -- Enter, then Enter again -- keeps
     * the campaign rather than destroying it.
     */
    private void showResetConfirm(GameMode mode) {
        MenuPanel panel = new MenuPanel(
                new MenuButton("No, keep my progress", () -> showGalaxySelect(mode)),
                new MenuButton("Yes, erase every galaxy", () -> {
                    saves.resetProgress();
                    showGalaxySelect(mode);
                }));
        MenuNavigator navigator = panel.navigator();
        navigator.setOnBack(() -> showGalaxySelect(mode));

        StackPane root = MenuScreen.build("RESET PROGRESS", panel,
                MenuScreen.caption("Every galaxy goes back to locked, and the saved runs go with "
                        + "it. This cannot be undone.", 12, Tokens.TEXT_FAINT),
                MenuScreen.caption("High scores and your pilots are kept.", 11, Tokens.TEXT_GHOST));
        show(root, navigator::handleKey);
    }

    /**
     * Tier two: a galaxy's ten levels as a route.
     *
     * The canvas repaints on demand rather than on a timer, except for the one idle pulse on the
     * next-up node -- and that timer is not started at all when reduced flash is set, so the screen
     * is entirely still for a player who asked for stillness.
     */
    public void showSystemMap(GameMode mode, Galaxy galaxy) {
        SystemMapModel map = new SystemMapModel(mode, galaxy, saves, highScores);
        SystemMapView view = new SystemMapView(map, settings);

        map.setOnBack(() -> showGalaxySelect(mode));
        map.setOnLaunch(level -> startGame(mode, SaveSlot.forReplay(mode, level)));
        map.setOnChanged(view::draw);

        StackPane root = MenuScreen.build(galaxy.label().toUpperCase(java.util.Locale.ROOT),
                view.canvas());
        view.draw();
        show(root, map::handleKey);

        if (!settings.reducedFlash()) {
            AnimationTimer pulse = new AnimationTimer() {
                @Override
                public void handle(long now) {
                    view.tick();
                    view.draw();
                }
            };
            pulse.start();
            mapPulse = pulse;
        }
    }

    /** I, II, III ... for the galaxy rows. Five of them, so a table beats an algorithm. */
    private static String roman(int number) {
        String[] numerals = {"I", "II", "III", "IV", "V"};
        return number >= 1 && number <= numerals.length ? numerals[number - 1] : String.valueOf(number);
    }

    /** Whether every galaxy is finished, which is what unlocks the endless run. */
    private boolean campaignComplete(GameMode mode) {
        for (Galaxy galaxy : Galaxy.values()) {
            if (saves.clearedCount(mode, galaxy) < Galaxy.LEVELS_PER_GALAXY) {
                return false;
            }
        }
        return true;
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
                        Tokens.TEXT_FAINT));
        show(root, navigator::handleKey);
    }

    public void showPilots() {
        NameEntryPanel panel = new NameEntryPanel(pilots, this::showStartMenu);
        StackPane root = MenuScreen.build("PILOTS",
                panel,
                MenuScreen.caption("Names show under your ship. Rank is earned across every run.", 12,
                        Tokens.TEXT_FAINT));
        show(root, panel::handleKey);
    }

    public void showMultiplayerMenu() {
        MenuButton coop = new MenuButton("Co-op  (survive together)", () -> startGame(GameMode.COOP));
        // Co-op keeps its own cleared-levels record, so it gets its own way into the map rather
        // than inheriting whichever galaxy the solo campaign happens to be in.
        MenuButton coopMap = new MenuButton("Co-op Campaign", () -> showGalaxySelect(GameMode.COOP));
        MenuButton battle = new MenuButton("Battle  (face each other)", () -> startGame(GameMode.BATTLE));
        MenuButton back = new MenuButton("Back", this::showStartMenu);
        MenuPanel panel = new MenuPanel(coop, coopMap, battle, back);

        MenuNavigator navigator = panel.navigator();
        navigator.setOnBack(this::showStartMenu);

        StackPane root = MenuScreen.build("MULTIPLAYER",
                panel,
                MenuScreen.caption("Two players, one keyboard", 12, Tokens.TEXT_FAINT));
        show(root, navigator::handleKey);
    }

    public void showSettings(Runnable onBack) {
        SettingsPanel panel = new SettingsPanel(settings, sounds, this::padStatus, onBack,
                () -> showControls(() -> showSettings(onBack)));
        StackPane root = MenuScreen.build("SETTINGS", panel);
        show(root, panel.navigator(onBack)::handleKey);
    }

    /**
     * Rebinding the keyboard.
     *
     * A screen of its own rather than ten more rows on Settings, which is already the longest menu
     * in the game. The capture handler runs ahead of the navigator so a key being bound is not also
     * read as a menu move.
     */
    public void showControls(Runnable onBack) {
        ControlsPanel panel = new ControlsPanel(settings, onBack);
        MenuNavigator navigator = panel.navigator(onBack);
        StackPane root = MenuScreen.build("KEYBOARD", panel,
                MenuScreen.caption("Choose a row, then press the key you want. "
                        + "Escape backs out.", 12, Tokens.TEXT_FAINT));
        show(root, code -> panel.captureKey(code) || navigator.handleKey(code));
    }

    /** Read off the bindings rather than written out, so the page cannot go stale after a rebind. */
    private String movementKeys(int player) {
        return settings.key(player, ControlAction.UP).getName()
                + "  " + settings.key(player, ControlAction.LEFT).getName()
                + "  " + settings.key(player, ControlAction.DOWN).getName()
                + "  " + settings.key(player, ControlAction.RIGHT).getName();
    }

    private String fireKey(int player) {
        return settings.key(player, ControlAction.FIRE).getName();
    }

    public void showHelp() {
        VBox lines = new VBox(7);
        lines.setAlignment(Pos.CENTER_LEFT);
        List<String> rows = List.of(
                "PLAYER 1        " + movementKeys(1) + "          fire: " + fireKey(1),
                "PLAYER 2        " + movementKeys(2) + "          fire: " + fireKey(2),
                "",
                "Single player accepts either those keys or the arrows,",
                "and fires with SPACE as well. Rebind any of them under",
                "Settings, Keyboard.",
                "",
                "ESCAPE          pause / resume        F11   fullscreen",
                "MENUS           arrows or W/S, Enter to choose",
                "CONTROLLERS     stick or d-pad, A to fire, Start to pause.",
                "                First pad is player one. Buttons and",
                "                deadzone are in Settings.",
                "",
                "Shoot asteroids and enemy ships for points. Enemies drop",
                "pickups: tri-shot, rockets, shield, speed, health or an",
                "extra life. The mega laser is rarer and only heavy hulls",
                "and flagships carry one. Tri-shot stacks to five streams,",
                "the shield soaks a set amount of damage, and everything",
                "you are carrying is lost when you die or reach the next",
                "level. Every level fields its own defenders and ends with",
                "its own flagship, which changes attack pattern as you wear",
                "it down. Some are flown side-on, and some thread rock that",
                "closes in from both sides -- the walls hurt. Kill the",
                "flagship for a debrief, bonuses, rank and the garage.",
                "",
                "UNIVERSE        the campaign is galaxies of ten levels.",
                "                Clear a level to open the next; clear all",
                "                ten to open the next galaxy. A run is one",
                "                galaxy, so finishing one ends it.",
                "",
                "SAVING          checkpoints each level. Continue resumes;",
                "                the Universe Map replays any level you have",
                "                cleared, in the ship you have now. Pause to",
                "                save to one of three slots.",
                "",
                "PILOTS          name both seats from the start menu. Level",
                "                bonuses build a career score and your rank,",
                "                and buy upgrades in the garage.",
                "",
                "In Battle, player two starts at the top facing down and",
                "friendly fire is on. Last player with lives wins.");
        for (String row : rows) {
            lines.getChildren().add(MenuScreen.caption(row, 13, Tokens.TEXT_MUTED));
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
                random, resume, this::showStartMenu, this::showGameOver, this::padStatus);
        screen.setSticks(gamepad == null ? null : gamepad::latestForPlayer);
        activeGame = screen;
        show(screen.root(), null);
        // The scene must already be on the stage before input attaches; it listens for focus loss.
        screen.attachInput(stage.getScene());
        screen.start();
    }

    /**
     * The post-campaign run: no galaxy borders, and {@code Level.next} wrapping forever.
     *
     * Started fresh from the first level rather than resumed, and never checkpointed, because it is
     * not campaign progress -- it is the arcade ladder the game was before it had an ending, kept
     * because the loop escalation that drives it is already written and tuned.
     */
    private void startEndless(GameMode mode) {
        stopActiveGame();
        sounds.playMusic(mode.music());
        GameScreen screen = new GameScreen(mode, settings, sounds, pilots, highScores, saves,
                random, null, this::showStartMenu, this::showGameOver, this::padStatus, true);
        screen.setSticks(gamepad == null ? null : gamepad::latestForPlayer);
        activeGame = screen;
        show(screen.root(), null);
        screen.attachInput(stage.getScene());
        screen.start();
    }

    private void showGameOver(RoundResult result) {
        stopActiveGame();
        sounds.playMusic(MusicCue.MENU);
        sounds.play(SoundFx.GAME_OVER);

        boolean record = highScores.submit(result.mode(), result.bestScore());
        String heading = result.galaxyCleared()
                ? "GALAXY CLEARED"
                : result.winningPlayerNumber() > 0
                        ? "PLAYER " + result.winningPlayerNumber() + " WINS"
                        : "GAME OVER";

        VBox summary = new VBox(6);
        summary.setAlignment(Pos.CENTER);
        summary.getChildren().add(MenuScreen.caption(result.mode().label(), 14, Tokens.TEXT_DIM));
        for (int i = 0; i < result.scores().size(); i++) {
            String row = "Player " + (i + 1) + " score   " + result.scores().get(i);
            summary.getChildren().add(MenuScreen.caption(row, 15, Color.WHITE));
        }
        String progress = "Level " + result.level().number() + "   " + result.level().label()
                + "   -   " + result.wavesSurvived() + " waves"
                + (result.loop() > 1 ? "   -   loop " + result.loop() : "");
        summary.getChildren().add(MenuScreen.caption(progress, 13, Tokens.TEXT_DIM));
        if (record) {
            summary.getChildren().add(MenuScreen.caption("NEW BEST", 15, Tokens.BRAND));
        } else {
            summary.getChildren().add(MenuScreen.caption(
                    "Best   " + highScores.best(result.mode()), 13, Tokens.TEXT_FAINT));
        }

        // A cleared galaxy is a finish, not a defeat, so the first row moves the player onward
        // instead of offering the run they have just completed all over again.
        Galaxy done = result.level().galaxy();
        Galaxy next = done.next();
        MenuButton onward = result.galaxyCleared()
                ? (next != null && saves.isGalaxyUnlocked(result.mode(), next)
                        ? new MenuButton("On to " + next.label(),
                                () -> showSystemMap(result.mode(), next))
                        : new MenuButton("Universe Map", () -> showGalaxySelect(result.mode())))
                : new MenuButton("Play Again", () -> startGame(result.mode()));
        MenuButton menu = new MenuButton("Main Menu", this::showStartMenu);
        MenuPanel panel = new MenuPanel(onward, menu);
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

    /**
     * One line naming what SDL currently recognises, for the settings screen.
     *
     * SDL only opens a pad it has a mapping for, so an unrecognised controller is indistinguishable
     * here from no controller at all -- Jamepad binds no raw joystick count. Saying so plainly, and
     * pointing at the fix, is worth more than a bare "none": a player whose pad is plugged in and
     * lit up otherwise has nothing to go on.
     */
    private String padStatus() {
        if (gamepad == null) {
            return "Pads         unavailable";
        }
        if (gamepad.recognisedPads() == 0) {
            return "Pads         none recognised -- see README";
        }
        StringBuilder text = new StringBuilder("Pads        ");
        for (int slot = 0; slot < 2; slot++) {
            String name = gamepad.padName(slot);
            if (name != null) {
                text.append(" P").append(slot + 1).append(' ').append(name);
            }
        }
        return text.toString();
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
        if (mapPulse != null) {
            mapPulse.stop();
            mapPulse = null;
        }
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
        if (keys == null) {
            scene.setOnKeyPressed(null);
            scene.setOnKeyReleased(null);
            return;
        }
        // Gated, so a held stick or arrow walks the menu instead of racing through it. A fresh gate
        // per screen is what makes the first push after arriving here immediate. The release handler
        // is what keeps quick tapping responsive: without it the gate would have to guess at when a
        // key came up, and would eat the second of two fast presses.
        MenuRepeat gated = MenuRepeat.gate(keys);
        scene.setOnKeyPressed(event -> {
            if (gated.test(event.getCode())) {
                event.consume();
            }
        });
        scene.setOnKeyReleased(event -> gated.release(event.getCode()));
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

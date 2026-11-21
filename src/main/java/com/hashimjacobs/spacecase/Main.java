package com.hashimjacobs.spacecase;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;
import javafx.stage.Stage;

import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.engine.GameLoop;
import com.hashimjacobs.spacecase.mode.GameMode;
import com.hashimjacobs.spacecase.mode.Level;
import com.hashimjacobs.spacecase.prefs.SaveSlot;
import com.hashimjacobs.spacecase.prefs.HighScores;
import com.hashimjacobs.spacecase.prefs.Pilots;
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.scene.Cutscene;
import com.hashimjacobs.spacecase.scene.SceneRouter;

public final class Main extends Application {

    /**
     * Opens the window on a loading screen, then decodes the art behind it.
     *
     * Decoding every sprite used to happen here, on the JavaFX thread, before there was a window: on
     * a Raspberry Pi that was the better part of ten seconds of nothing on screen, long enough for
     * the freeze watchdog to call it a hang. Now the window is up at once and the JavaFX thread stays
     * free; {@link #begin} builds the game when the art is ready.
     *
     * Android keeps the old order -- the art first, then the game's own first screen -- because its
     * window is the whole display from the start and swapping its scene is untested on a TV.
     */
    @Override
    public void start(Stage stage) {
        // First, so a hang during asset loading is reported too.
        FreezeWatchdog.install();
        stage.setTitle("Space Case");
        Settings settings = Settings.load();
        if (OnAndroid.ACTIVE) {
            Assets.load();
            begin(stage, settings);
            return;
        }

        Text loading = new Text("Loading");
        loading.setFill(Color.gray(0.45));
        StackPane splash = new StackPane(loading);
        splash.setStyle("-fx-background-color: black;");
        stage.setScene(new Scene(splash, GameConfig.WIDTH, GameConfig.HEIGHT, Color.BLACK));
        stage.show();
        SceneRouter.applyResolution(stage, settings.windowHeight());

        Thread loader = new Thread(() -> {
            try {
                Assets.load();
                Platform.runLater(() -> begin(stage, settings));
            } catch (RuntimeException | Error failed) {
                // What a failure in start() did before: say why, and do not leave a window that
                // will never finish loading.
                failed.printStackTrace();
                Platform.runLater(Platform::exit);
            }
        }, "asset-loader");
        loader.setDaemon(true);
        loader.start();
    }

    /** Everything after the art: the stores, the sound, the first screen. On the JavaFX thread. */
    private void begin(Stage stage, Settings settings) {
        Cutscene.fetchClips();

        SoundBank sounds = new SoundBank(settings);
        HighScores highScores = HighScores.load();
        Pilots pilots = Pilots.load();

        stage.getIcons().add(Assets.image(Sprite.P1_STRAIGHT));
        stage.setMinWidth(560);
        stage.setMinHeight(500);
        stage.setFullScreenExitHint("Press F11 to leave fullscreen");
        // F11 is the way out; Escape belongs to the game's own back and pause.
        stage.setFullScreenExitKeyCombination(javafx.scene.input.KeyCombination.NO_MATCH);

        // The router builds its own scene on its first screen, and only when the stage has none.
        stage.setScene(null);
        SceneRouter router = new SceneRouter(stage, settings, sounds, highScores, pilots);
        // --onfoot[=<mission>][,coop], e.g. --onfoot=forgeworks,coop: straight onto that stage
        // (the Undercity by default), looping, for feel-tuning.
        String onFoot = getParameters().getRaw().stream().filter(a -> a.startsWith("--onfoot")).findFirst().orElse(null);
        // --level=N[,boss]: solo straight into level N, optionally with its flagship already coming.
        // --garage: solo, opening in the garage. Both for looking at art, like --onfoot.
        String level = getParameters().getRaw().stream().filter(a -> a.startsWith("--level=")).findFirst().orElse(null);
        boolean garage = getParameters().getRaw().contains("--garage");
        if (level != null || garage) {
            String[] spec = level == null ? new String[] {"1"} : level.substring("--level=".length()).split(",");
            com.hashimjacobs.spacecase.engine.SpawnDirector.devBossNow = spec.length > 1 && spec[1].equals("boss");
            GameLoop.devGarage = garage;
            router.startGame(GameMode.SOLO, SaveSlot.forReplay(GameMode.SOLO, Level.values()[Integer.parseInt(spec[0]) - 1]));
        } else if (onFoot != null) {
            GameLoop.devOnFoot = true;
            GameMode mode = onFoot.endsWith("coop") ? GameMode.COOP : GameMode.SOLO;
            String named = onFoot.contains("=") ? onFoot.substring(onFoot.indexOf('=') + 1).split(",")[0] : "";
            com.hashimjacobs.spacecase.onfoot.Mission mission = com.hashimjacobs.spacecase.onfoot.Mission.UNDERCITY;
            for (com.hashimjacobs.spacecase.onfoot.Mission m : com.hashimjacobs.spacecase.onfoot.Mission.values()) {
                if (m.name().equalsIgnoreCase(named.replace('-', '_'))) {
                    mission = m;
                }
            }
            router.startGame(mode, SaveSlot.forReplay(mode, mission.level()));
        } else if (getParameters().getRaw().contains("--update")) {
            // Straight onto the update screen, for trying an update against -Dspacecase.updateUrl.
            router.showUpdate(router::showStartMenu);
        } else {
            router.showStartMenu();
            router.checkForUpdatesAtLaunch();
        }

        stage.setOnHidden(event -> {
            settings.save();
            router.shutdown();
        });
        if (!stage.isShowing()) {
            stage.show();
        }
        // After show, so the title bar's size is known and the window can be sized around the game.
        SceneRouter.applyResolution(stage, settings.windowHeight());
    }

    public static void main(String[] args) {
        launch(args);
    }
}

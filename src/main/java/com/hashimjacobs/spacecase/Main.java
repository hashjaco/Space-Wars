package com.hashimjacobs.spacecase;

import javafx.application.Application;
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
import com.hashimjacobs.spacecase.scene.SceneRouter;

public final class Main extends Application {

    @Override
    public void start(Stage stage) {
        // First, so a hang during asset loading is reported too.
        FreezeWatchdog.install();
        Assets.load();

        Settings settings = Settings.load();
        SoundBank sounds = new SoundBank(settings);
        HighScores highScores = HighScores.load();
        Pilots pilots = Pilots.load();

        stage.setTitle("Space Case");
        stage.getIcons().add(Assets.image(Sprite.P1_STRAIGHT));
        stage.setMinWidth(560);
        stage.setMinHeight(500);
        stage.setFullScreenExitHint("Press F11 to leave fullscreen");

        SceneRouter router = new SceneRouter(stage, settings, sounds, highScores, pilots);
        // --onfoot or --onfoot=coop: straight onto the Undercity stage, looping, for feel-tuning.
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
            router.startGame(mode, SaveSlot.forReplay(mode, Level.UNDERCITY));
        } else {
            router.showStartMenu();
        }

        stage.setOnHidden(event -> {
            settings.save();
            router.shutdown();
        });
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}

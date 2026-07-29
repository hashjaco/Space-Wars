package com.hashimjacobs.spacecase;

import javafx.application.Application;
import javafx.stage.Stage;

import com.hashimjacobs.spacecase.asset.Assets;
import com.hashimjacobs.spacecase.asset.SoundBank;
import com.hashimjacobs.spacecase.asset.Sprite;
import com.hashimjacobs.spacecase.prefs.HighScores;
import com.hashimjacobs.spacecase.prefs.Pilots;
import com.hashimjacobs.spacecase.prefs.Settings;
import com.hashimjacobs.spacecase.scene.SceneRouter;

public final class Main extends Application {

    @Override
    public void start(Stage stage) {
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
        router.showStartMenu();

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

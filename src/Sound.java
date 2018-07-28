package sample;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;
import java.util.HashMap;

public class Sound {
    private final HashMap<String, MediaPlayer> sounds = new HashMap<>();
    private final MediaPlayer fire = new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Documents/CSC/CSC 413/csc413-secondgame-Team02-ScriptKiddie19/Invaders/src/sample/Sounds/lazer.wav").toURI().toString()));
    private final MediaPlayer explosion = new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Documents/CSC/CSC 413/csc413-secondgame-Team02-ScriptKiddie19/Invaders/src/sample/Sounds/explosion_x.wav").toURI().toString()));
    private final MediaPlayer backgroundMusic = new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Documents/CSC/CSC 413/csc413-secondgame-Team02-ScriptKiddie19/Invaders/src/sample/Sounds/Katdrop-Call-The-Cops.wav").toURI().toString()));
    private final MediaPlayer speed = new MediaPlayer(new Media(new File("/Users/hashimjacobs/IdeaProjects/TankGame/src/sample/sprites/muscle-car-daniel_simon.wav").toURI().toString()));
    private final MediaPlayer weapon = new MediaPlayer(new Media(new File("/Users/hashimjacobs/IdeaProjects/TankGame/src/sample/sprites/collectRocket.wav").toURI().toString()));
    private final MediaPlayer armor = new MediaPlayer(new Media(new File("/Users/hashimjacobs/IdeaProjects/TankGame/src/sample/sprites/collectShield.wav").toURI().toString()));
    private final MediaPlayer gameOver = new MediaPlayer(new Media(new File("/Users/hashimjacobs/IdeaProjects/TankGame/src/sample/sprites/TITANIC-FLUTE-FAIL-Sound-Effect-Best-Sound-Effects-TV.wav").toURI().toString()));


    public Sound(){
        sounds.put("fire", fire);
        sounds.put("explosion", explosion);
        sounds.put("background music", backgroundMusic);
        sounds.put("speed powerup", speed);
        sounds.put("weapon powerup", weapon);
        sounds.put("armor", armor);
        sounds.put("game over", gameOver);
    }

    public MediaPlayer getValue(String media){
        return sounds.get(media);
    }
}

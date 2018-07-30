package sources;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.io.File;
import java.util.HashMap;

public class Sound {
    protected static HashMap<String, MediaPlayer> sounds = new HashMap<>();
    private final MediaPlayer fire = new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Projects/SpaceWars/src/sources/Sounds/lazer.wav").toURI().toString()));
    private final MediaPlayer explosion = new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Projects/SpaceWars/src/sources/Sounds/explosion_x.wav").toURI().toString()));
    private final MediaPlayer backgroundMusic = new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Projects/SpaceWars/src/sources/Sounds/Katdrop-Call-The-Cops.wav").toURI().toString()));
    private final MediaPlayer gameOver = new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Projects/SpaceWars/src/sources/Sounds/TITANIC-FLUTE-FAIL-Sound-Effect-Best-Sound-Effects-TV.wav").toURI().toString()));
    private final MediaPlayer collision = new MediaPlayer(new Media(new File("/Users/hashimjacobs/OneDrive/Projects/SpaceWars/src/sources/Sounds/shottyToDaBody.wav").toURI().toString()));


    public Sound(){
        sounds.put("fire", fire);
        sounds.put("explosion", explosion);
        sounds.put("background music", backgroundMusic);
        sounds.put("collision", collision);
        sounds.put("game over", gameOver);
    }

    public MediaPlayer getValue(String media){
        return sounds.get(media);
    }
}

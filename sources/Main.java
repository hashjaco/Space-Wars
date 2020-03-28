package sources;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.scene.media.MediaPlayer;

public class Main extends Application {

    private Sound sounds = new Sound();
    private Sprites sprites = new Sprites();

    // Plays media
    private static void playMedia(MediaPlayer mp) {
        mp.setAutoPlay(true);
        mp.play();
    }

    @Override
    public synchronized void start(Stage primaryStage) {
        // Background Music
        GameEngine gameEngine = new GameEngine();

        primaryStage.setScene(gameEngine.getScene());
        primaryStage.setTitle("Space Case");
        primaryStage.getIcons().add(sprites.getValue("player1straight"));
        primaryStage.setResizable(false);
                primaryStage.show();
    }

    public static void Main(String[] args) {
        launch(args);
    }
}

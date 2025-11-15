package sources;
import javafx.application.Application;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import javafx.scene.media.MediaPlayer;

public class Main extends Application {

    private Sound sounds = new Sound();
    private Sprites sprites = new Sprites();
    private Stage primaryStage;

    // Plays media
    private static void playMedia(MediaPlayer mp) {
        mp.setAutoPlay(true);
        mp.play();
    }

    @Override
    public synchronized void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        // Start with the start menu
        GameStartMenu startMenu = new GameStartMenu(this);
        primaryStage.setScene(startMenu.getScene());
        primaryStage.setTitle("Space Wars");
        primaryStage.getIcons().add(sprites.getValue("player1straight"));
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    public void startGame() {
        // Background Music
        GameEngine gameEngine = new GameEngine();
        primaryStage.setScene(gameEngine.getScene());
        primaryStage.setTitle("Space Wars - Game");
    }
    
    public Stage getPrimaryStage() {
        return primaryStage;
    }

    public static void main(String[] args) {
        launch(args);
    }
}

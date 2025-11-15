package sources;

import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.layout.Background;
import javafx.scene.layout.BackgroundFill;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.Text;

public class GameStartMenu {
    private Scene scene;
    private Main main;
    private Sprites sprites;
    
    public GameStartMenu(Main main) {
        this.main = main;
        this.sprites = new Sprites();
        this.scene = createMenuScene();
    }
    
    private Scene createMenuScene() {
        StackPane root = new StackPane();
        root.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));
        
        // Add background image if available
        try {
            javafx.scene.image.ImageView bgView = new javafx.scene.image.ImageView(sprites.getValue("background"));
            bgView.setFitWidth(996);
            bgView.setFitHeight(864);
            root.getChildren().add(bgView);
        } catch (Exception e) {
            // Background image not critical
        }
        
        VBox menuBox = new VBox(15);
        menuBox.setAlignment(Pos.CENTER);
        menuBox.setTranslateY(-50);
        
        // Title
        Title title = new Title("SPACE WARS");
        menuBox.getChildren().add(title);
        
        // Menu Items
        MenuItem startItem = new MenuItem("START GAME");
        MenuItem instructionsItem = new MenuItem("INSTRUCTIONS");
        MenuItem exitItem = new MenuItem("EXIT");
        
        MenuBox menu = new MenuBox(startItem, instructionsItem, exitItem);
        menuBox.getChildren().add(menu);
        
        // Event handlers
        startItem.setOnMouseClicked(e -> {
            main.startGame();
        });
        
        instructionsItem.setOnMouseClicked(e -> {
            showInstructions();
        });
        
        exitItem.setOnMouseClicked(e -> {
            System.exit(0);
        });
        
        root.getChildren().add(menuBox);
        
        return new Scene(root, 996, 864);
    }
    
    private void showInstructions() {
        StackPane root = new StackPane();
        root.setBackground(new Background(new BackgroundFill(Color.BLACK, null, null)));
        
        VBox instructionsBox = new VBox(20);
        instructionsBox.setAlignment(Pos.CENTER);
        instructionsBox.setTranslateY(-50);
        
        Title title = new Title("INSTRUCTIONS");
        instructionsBox.getChildren().add(title);
        
        VBox textBox = new VBox(15);
        textBox.setAlignment(Pos.CENTER);
        
        Text controlsTitle = new Text("CONTROLS");
        controlsTitle.setFill(Color.WHITE);
        controlsTitle.setFont(Font.font("Times New Roman", 30));
        
        Text player1Text = new Text("Player 1:\nW - UP | A - LEFT | S - DOWN | D - RIGHT | SHIFT - FIRE");
        player1Text.setFill(Color.CYAN);
        player1Text.setFont(Font.font("Times New Roman", 18));
        
        Text player2Text = new Text("Player 2:\nUP - UP | LEFT - LEFT | DOWN - DOWN | RIGHT - RIGHT | COMMA - FIRE");
        player2Text.setFill(Color.YELLOW);
        player2Text.setFont(Font.font("Times New Roman", 18));
        
        Text pauseText = new Text("ESCAPE - PAUSE/RESUME");
        pauseText.setFill(Color.WHITE);
        pauseText.setFont(Font.font("Times New Roman", 18));
        
        Text objectiveText = new Text("\nOBJECTIVE:\nDestroy asteroids and enemy ships!\nSurvive as long as possible!");
        objectiveText.setFill(Color.LIME);
        objectiveText.setFont(Font.font("Times New Roman", 20));
        
        MenuItem backItem = new MenuItem("BACK TO MENU");
        backItem.setOnMouseClicked(e -> {
            main.getPrimaryStage().setScene(this.scene);
        });
        
        textBox.getChildren().addAll(controlsTitle, player1Text, player2Text, pauseText, objectiveText);
        instructionsBox.getChildren().addAll(textBox, backItem);
        
        root.getChildren().add(instructionsBox);
        
        Scene instructionsScene = new Scene(root, 996, 864);
        main.getPrimaryStage().setScene(instructionsScene);
    }
    
    public Scene getScene() {
        return scene;
    }
}

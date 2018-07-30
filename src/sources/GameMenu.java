package sources;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

public class GameMenu {
    private Canvas canvas;
    private GraphicsContext gc;

    GameMenu(){
        gc = canvas.getGraphicsContext2D();
    }

    public Canvas draw(){
        return canvas;
    }
}

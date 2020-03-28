package sources;

import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

public class Game {
    private boolean isPaused = false;

    public void setPaused(boolean paused) {
        isPaused = paused;
    }

    public void getState(){}

    public void setState(State gameState){}


}

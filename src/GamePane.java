package sample;

import javafx.scene.canvas.Canvas;
import javafx.scene.image.Image;
import javafx.scene.layout.Pane;

import java.awt.*;

public class GamePane extends QuadTree {
    private Image background;
    private double H, W;
    Canvas canvas;
    GameEngine gameEngine;

    /**
     * Constructor
     *
     * @param pLevel
     * @param pBounds
     */
    public GamePane(int pLevel, Rectangle pBounds) {
        super(pLevel, pBounds);
    }
}

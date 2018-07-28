package sample;

import javafx.scene.Node;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;

import java.awt.geom.Rectangle2D;

public class HealthBar extends Rectangle {
    private double width;
    private Node view;

    public HealthBar(){
        this.view = null;
    }

    public HealthBar(Node view) {
        this.view = view;
        width = 100;
    }

    public void setBar(double health){
        this.width = health;
    }

    public double getHealth(){
        return width;
    }

}

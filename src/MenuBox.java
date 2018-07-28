package sample;

import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

public class MenuBox extends VBox {

    public MenuBox(MenuItem...items) {
        getChildren().add(createSeperator());

        for(MenuItem item : items) {
            getChildren().addAll(item, createSeperator());
        }
    }

    private Line createSeperator() {
        Line sep = new Line();
        sep.setEndX(210);
        sep.setStroke(Color.DARKGREY);
        return sep;
    }
}

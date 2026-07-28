package com.hashimjacobs.spacecase.scene;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

/**
 * A vertical stack of menu entries separated by hairlines.
 *
 * Ported from the original MenuBox.
 */
public final class MenuPanel extends VBox {

    public MenuPanel(Node... items) {
        setAlignment(Pos.CENTER);
        setPadding(new Insets(8, 0, 8, 0));
        getChildren().add(separator());
        for (Node item : items) {
            getChildren().addAll(item, separator());
        }
    }

    private static Line separator() {
        Line line = new Line();
        line.setEndX(330);
        line.setStroke(Color.web("#232b3d"));
        return line;
    }
}

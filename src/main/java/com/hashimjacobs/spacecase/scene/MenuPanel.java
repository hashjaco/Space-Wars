package com.hashimjacobs.spacecase.scene;

import java.util.ArrayList;
import java.util.List;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Line;

/**
 * A vertical stack of menu entries separated by hairlines.
 *
 * Ported from the original MenuBox.
 */
public final class MenuPanel extends VBox {

    private final List<MenuNavigator.Item> items = new ArrayList<>();

    public MenuPanel(MenuButton... buttons) {
        setAlignment(Pos.CENTER);
        setPadding(new Insets(8, 0, 8, 0));
        getChildren().add(separator());
        for (MenuButton button : buttons) {
            getChildren().addAll(button, separator());
            items.add(button);
        }
    }

    /** The entries in display order, for {@link MenuNavigator} to walk. */
    public List<MenuNavigator.Item> items() {
        return List.copyOf(items);
    }

    /** A navigator over this panel's entries, with the first one focused. */
    public MenuNavigator navigator() {
        MenuNavigator navigator = new MenuNavigator(items());
        return navigator;
    }

    private static Line separator() {
        Line line = new Line();
        line.setEndX(330);
        line.setStroke(Color.web("#232b3d"));
        return line;
    }
}

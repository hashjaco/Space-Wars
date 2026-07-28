package com.hashimjacobs.spacecase.scene;

import javafx.geometry.Pos;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

/**
 * A menu entry that lights up on hover.
 *
 * Ported from the original MenuItem, which was fully written but never wired to anything because
 * the start menu was never built.
 */
public final class MenuButton extends StackPane {

    private static final double WIDTH = 320;
    private static final double HEIGHT = 42;

    private static final LinearGradient HOVER_FILL = new LinearGradient(
            0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#0a5d12")),
            new Stop(0.12, Color.web("#05070c")),
            new Stop(0.88, Color.web("#05070c")),
            new Stop(1, Color.web("#0a5d12")));

    private final Text label;

    public MenuButton(String text, Runnable action) {
        Rectangle background = new Rectangle(WIDTH, HEIGHT);
        background.setFill(Color.web("#05070c"));
        background.setOpacity(0.72);
        background.setStroke(Color.web("#1d2436"));

        label = new Text(text);
        label.setFill(Color.web("#8b98ad"));
        label.setFont(Font.font("Verdana", FontWeight.BOLD, 17));

        setAlignment(Pos.CENTER);
        getChildren().addAll(background, label);
        setMaxWidth(WIDTH);

        setOnMouseEntered(event -> {
            background.setFill(HOVER_FILL);
            label.setFill(Color.WHITE);
        });
        setOnMouseExited(event -> {
            background.setFill(Color.web("#05070c"));
            label.setFill(Color.web("#8b98ad"));
        });
        setOnMousePressed(event -> background.setFill(Color.web("#0ec417")));
        setOnMouseReleased(event -> {
            background.setFill(HOVER_FILL);
            action.run();
        });
    }

    /** Lets a caller update the text in place, which the settings rows use to show their value. */
    public void setText(String text) {
        label.setText(text);
    }
}

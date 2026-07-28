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
 * A menu entry that lights up on hover or keyboard focus.
 *
 * Ported from the original MenuItem, which was fully written but never wired to anything because
 * the start menu was never built.
 */
public final class MenuButton extends StackPane implements MenuNavigator.Item {

    private static final double WIDTH = 320;
    private static final double HEIGHT = 42;
    private static final Color IDLE_BACKGROUND = Color.web("#05070c");
    private static final Color IDLE_TEXT = Color.web("#8b98ad");
    private static final Color HIGHLIGHT = Color.web("#0ec417");

    private static final LinearGradient HIGHLIGHT_FILL = new LinearGradient(
            0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Color.web("#0a5d12")),
            new Stop(0.12, Color.web("#05070c")),
            new Stop(0.88, Color.web("#05070c")),
            new Stop(1, Color.web("#0a5d12")));

    private final Rectangle background;
    private final Text label;
    private final Runnable action;
    private boolean focused;

    public MenuButton(String text, Runnable action) {
        this.action = action;

        background = new Rectangle(WIDTH, HEIGHT);
        background.setFill(IDLE_BACKGROUND);
        background.setOpacity(0.72);
        background.setStroke(Color.web("#1d2436"));

        label = new Text(text);
        label.setFill(IDLE_TEXT);
        label.setFont(Font.font("Verdana", FontWeight.BOLD, 17));

        setAlignment(Pos.CENTER);
        getChildren().addAll(background, label);
        setMaxWidth(WIDTH);

        // Hovering moves keyboard focus too, so the two input methods never disagree about which
        // entry is selected.
        setOnMouseEntered(event -> paintHighlighted());
        setOnMouseExited(event -> paintForFocusState());
        setOnMousePressed(event -> background.setFill(HIGHLIGHT));
        setOnMouseReleased(event -> {
            paintHighlighted();
            action.run();
        });
    }

    @Override
    public void setHighlighted(boolean highlighted) {
        this.focused = highlighted;
        paintForFocusState();
    }

    @Override
    public void activate() {
        action.run();
    }

    private void paintHighlighted() {
        background.setFill(HIGHLIGHT_FILL);
        background.setStroke(HIGHLIGHT);
        label.setFill(Color.WHITE);
    }

    private void paintForFocusState() {
        if (focused) {
            paintHighlighted();
            return;
        }
        background.setFill(IDLE_BACKGROUND);
        background.setStroke(Color.web("#1d2436"));
        label.setFill(IDLE_TEXT);
    }

    /** Lets a caller update the text in place, which the settings rows use to show their value. */
    public void setText(String text) {
        label.setText(text);
    }
}

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

import com.hashimjacobs.spacecase.ui.Tokens;

/**
 * A menu entry that lights up on hover or keyboard focus.
 *
 * Ported from the original MenuItem, which was fully written but never wired to anything because
 * the start menu was never built.
 */
public final class MenuButton extends StackPane implements MenuNavigator.Item {

    private static final double WIDTH = 320;
    private static final double HEIGHT = 42;
    private static final Color IDLE_BACKGROUND = Tokens.SURFACE_0;
    private static final Color IDLE_TEXT = Tokens.TEXT_DIM;
    private static final Color HIGHLIGHT = Tokens.BRAND;

    private static final LinearGradient HIGHLIGHT_FILL = new LinearGradient(
            0, 0, 1, 0, true, CycleMethod.NO_CYCLE,
            new Stop(0, Tokens.BRAND_DEEP),
            new Stop(0.12, Tokens.SURFACE_0),
            new Stop(0.88, Tokens.SURFACE_0),
            new Stop(1, Tokens.BRAND_DEEP));

    private final Rectangle background;
    private final Text label;
    private final Runnable action;
    private boolean focused;
    private boolean locked;

    public MenuButton(String text, Runnable action) {
        this.action = action;

        background = new Rectangle(WIDTH, HEIGHT);
        background.setFill(IDLE_BACKGROUND);
        background.setOpacity(0.72);
        background.setStroke(Tokens.EDGE);

        label = new Text(text);
        label.setFill(IDLE_TEXT);
        label.setFont(Font.font(Tokens.BODY, FontWeight.BOLD, 17));

        setAlignment(Pos.CENTER);
        getChildren().addAll(background, label);
        setMaxWidth(WIDTH);

        // Hovering moves keyboard focus too, so the two input methods never disagree about which
        // entry is selected.
        setOnMouseEntered(event -> paintHighlighted());
        setOnMouseExited(event -> paintForFocusState());
        setOnMousePressed(event -> {
            if (!locked) {
                background.setFill(HIGHLIGHT);
            }
        });
        setOnMouseReleased(event -> {
            paintHighlighted();
            activate();
        });
    }

    @Override
    public void setHighlighted(boolean highlighted) {
        this.focused = highlighted;
        paintForFocusState();
    }

    @Override
    public void activate() {
        if (locked) {
            return;
        }
        action.run();
    }

    /**
     * Marks a row as visible but not yet available.
     *
     * A locked row is shown rather than omitted, which is the point of a map: a player should be
     * able to see that a fourth galaxy exists and what it will take to reach it. It stays navigable
     * for the same reason the settings screen leaves its read-only rows navigable -- a pad user
     * scrolling past something they cannot read has not been told anything.
     *
     * The palette change is the weaker half of the signal on purpose: callers put the reason in the
     * label itself ("locked -- clear Ashen Verge"), so the row says why it cannot be used rather
     * than relying on a shade of grey that a colour-blind player may not distinguish.
     */
    public void setLocked(boolean locked) {
        this.locked = locked;
        paintForFocusState();
    }

    public boolean isLocked() {
        return locked;
    }

    private void paintHighlighted() {
        if (locked) {
            // Focused but still unavailable: brightened enough to show the cursor is here, and
            // never given the brand colour, which everywhere else means "you may".
            background.setFill(IDLE_BACKGROUND);
            background.setStroke(Tokens.TEXT_FAINT);
            label.setFill(Tokens.TEXT_DIM);
            return;
        }
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
        background.setStroke(Tokens.EDGE);
        label.setFill(locked ? Tokens.TEXT_FAINT : IDLE_TEXT);
    }

    /** Lets a caller update the text in place, which the settings rows use to show their value. */
    public void setText(String text) {
        label.setText(text);
    }
}
